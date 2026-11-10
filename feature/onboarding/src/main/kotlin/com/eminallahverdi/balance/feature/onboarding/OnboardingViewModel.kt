package com.eminallahverdi.balance.feature.onboarding

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.backup.DriveBackups
import com.eminallahverdi.balance.core.common.ApplicationScope
import com.eminallahverdi.balance.core.common.IoDispatcher
import com.eminallahverdi.balance.core.common.Language
import com.eminallahverdi.balance.core.common.UiText
import com.eminallahverdi.balance.core.common.uiText
import com.eminallahverdi.balance.core.data.FirstRun
import com.eminallahverdi.balance.core.data.StarterTemplate
import com.eminallahverdi.balance.core.datastore.InstallPreferences
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.designsystem.theme.PersonPalette
import com.eminallahverdi.balance.core.model.CategoryType
import com.eminallahverdi.balance.core.model.CsvImport
import com.eminallahverdi.balance.core.model.CsvPreview
import com.eminallahverdi.balance.core.model.parseTransactionCsv
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FAMILY_COLOUR = 0xFF8A94A3
private const val OWNER_COLOUR = 0xFF5B9DFF
private const val FAMILY = "family"

/** The shared person's name in each of the starter template's languages, keyed by whether it's Azerbaijani. */
private val FAMILY_NAMES = mapOf(false to "Family", true to "Ailə")

enum class OnboardingStep { WELCOME, PEOPLE, CATEGORIES, BACKUP, REMINDERS, IMPORT }

enum class CategoryTemplate(@StringRes val label: Int, @StringRes val caption: Int) {
    HOUSEHOLD(R.string.onboarding_template_household, R.string.onboarding_template_household_caption),
    SIMPLE(R.string.onboarding_template_simple, R.string.onboarding_template_simple_caption);

    fun build(ownerName: String, azerbaijani: Boolean = false): StarterTemplate = when (this) {
        HOUSEHOLD -> StarterTemplate.household(ownerName, azerbaijani)
        SIMPLE -> StarterTemplate.simple(ownerName, azerbaijani)
    }
}

data class PersonDraft(val key: String, val name: String, val color: Long)

data class PersonEditor(val draft: PersonDraft, val isNew: Boolean)

/** Nothing is written until the last tap, so leaving halfway leaves a fresh install, and the next start begins again. */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    /** Chosen on Welcome, which makes the import the last step. */
    val importing: Boolean = false,
    /** Android 13 and up, with notifications not allowed yet. */
    val asksNotifications: Boolean = false,
    val reminderMinute: Int = 21 * 60,
    val ownerName: String = "",
    /** The starter template's language: the one showing, which can change on Welcome. */
    val azerbaijani: Boolean = false,
    val others: List<PersonDraft> = listOf(PersonDraft(FAMILY, FAMILY_NAMES.getValue(false), FAMILY_COLOUR)),
    val editor: PersonEditor? = null,
    val template: CategoryTemplate = CategoryTemplate.HOUSEHOLD,
    val dropped: Set<String> = emptySet(),
    val showingIncome: Boolean = false,
    /** Google already said yes on the backup page. Backups themselves only turn on at the last tap. */
    val backUp: Boolean = false,
    val import: CsvImport? = null,
    val preview: CsvPreview? = null,
    val message: UiText? = null,
    val saving: Boolean = false,
    val finished: Boolean = false,
) {
    /** The numbered steps. Welcome isn't one of them. */
    val steps: List<OnboardingStep>
        get() = listOfNotNull(
            OnboardingStep.PEOPLE,
            OnboardingStep.CATEGORIES,
            OnboardingStep.BACKUP,
            OnboardingStep.REMINDERS.takeIf { asksNotifications },
            OnboardingStep.IMPORT.takeIf { importing },
        )

    /** Which numbered step this is, and how many there are. */
    val progress: Pair<Int, Int> get() = steps.indexOf(step) + 1 to steps.size
    val next: OnboardingStep? get() = steps.getOrNull(steps.indexOf(step) + 1)
    val previous: OnboardingStep get() = steps.getOrNull(steps.indexOf(step) - 1) ?: OnboardingStep.WELCOME

    val shownCategories: List<StarterTemplate.TemplateCategory>
        get() = template.build(ownerName, azerbaijani).categories.filter { it.type == if (showingIncome) CategoryType.INCOME else CategoryType.EXPENSE }

    /** A household with Family gone hands its categories to the owner, and what was left out stays out. */
    fun finalTemplate(): StarterTemplate =
        template.build(ownerName.trim(), azerbaijani)
            .withPeople(others.mapIndexed { i, person -> StarterTemplate.TemplatePerson(person.key, person.name.trim(), person.color, i + 1) })
            .without(dropped)

    /** Quick add needs a category to save to, so the last one of each kind stays. */
    fun toggled(category: StarterTemplate.TemplateCategory): OnboardingUiState {
        val keptOfKind = template.build(ownerName, azerbaijani).categories.count { it.type == category.type && it.key !in dropped }
        return when {
            category.key in dropped -> copy(dropped = dropped - category.key)
            keptOfKind > 1 -> copy(dropped = dropped + category.key)
            else -> this
        }
    }

    /** In [azerbaijani], with Family renamed to match unless the user has named it themselves. */
    fun inLanguage(azerbaijani: Boolean): OnboardingUiState = copy(
        azerbaijani = azerbaijani,
        others = others.map { if (it.key == FAMILY && it.name in FAMILY_NAMES.values) it.copy(name = FAMILY_NAMES.getValue(azerbaijani)) else it },
    )

    /** The first colour nobody has yet, so people can be told apart without choosing. */
    fun nextColour(): Long = PersonPalette.firstOrNull { c -> c != OWNER_COLOUR && others.none { it.color == c } } ?: PersonPalette.first()
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val firstRun: FirstRun,
    private val install: InstallPreferences,
    private val backups: DriveBackups,
    prefs: UserPreferencesRepository,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()
    private var addedPeople = 0

    init {
        // The template follows the language showing, which Welcome can change before anything is chosen.
        viewModelScope.launch { Language.flow.collect { _state.update { it.inLanguage(Language.isAzerbaijani) } } }
        viewModelScope.launch {
            val allowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
            _state.update {
                it.copy(asksNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !allowed, reminderMinute = prefs.current().eveningReminderMinute)
            }
        }
    }

    fun start(importing: Boolean) = _state.update { it.copy(importing = importing, step = OnboardingStep.PEOPLE) }
    fun back() = _state.update { it.copy(step = it.previous, message = null) }

    fun next() {
        val following = _state.value.next ?: return finish()
        _state.update { it.copy(step = following) }
    }

    fun setOwnerName(name: String) = _state.update { it.copy(ownerName = name) }

    fun addPerson() = _state.update { s ->
        s.copy(editor = PersonEditor(PersonDraft("person${++addedPeople}", "", s.nextColour()), isNew = true))
    }

    fun editPerson(person: PersonDraft) = _state.update { it.copy(editor = PersonEditor(person, isNew = false)) }
    fun updateEditor(draft: PersonDraft) = _state.update { it.copy(editor = it.editor?.copy(draft = draft)) }
    fun closeEditor() = _state.update { it.copy(editor = null) }

    fun saveEditor() = _state.update { s ->
        val editor = s.editor ?: return@update s
        val others = if (editor.isNew) s.others + editor.draft else s.others.map { if (it.key == editor.draft.key) editor.draft else it }
        s.copy(others = others, editor = null)
    }

    fun removeEditing() = _state.update { s ->
        s.copy(others = s.others.filterNot { it.key == s.editor?.draft?.key }, editor = null)
    }

    fun chooseTemplate(template: CategoryTemplate) = _state.update { it.copy(template = template, dropped = emptySet()) }
    fun showIncome(income: Boolean) = _state.update { it.copy(showingIncome = income) }
    fun toggle(category: StarterTemplate.TemplateCategory) = _state.update { it.toggled(category) }

    fun chooseBackup(on: Boolean) {
        _state.update { it.copy(backUp = on) }
        next()
    }

    /** Answered either way counts: it asks once, and Settings can still turn the reminder off or on. */
    fun notificationsAnswered() {
        viewModelScope.launch {
            install.setNotificationsAsked()
            next()
        }
    }

    fun chooseFile(file: Uri) {
        viewModelScope.launch {
            val text = withContext(io) { runCatching { context.contentResolver.openInputStream(file)?.use { it.readBytes().decodeToString() } }.getOrNull() }
            val import = text?.let(::parseTransactionCsv)
            _state.update { s ->
                val template = s.finalTemplate()
                when {
                    text == null -> s.copy(message = uiText(R.string.onboarding_couldnt_read_file))
                    import == null -> s.copy(message = uiText(R.string.onboarding_needs_date_amount))
                    import.entries.isEmpty() -> s.copy(message = uiText(R.string.onboarding_no_entries_in_file))
                    else -> s.copy(message = null, import = import, preview = import.preview(template.accounts.map { it.name }, template.categoryNames))
                }
            }
        }
    }

    fun closePreview() = _state.update { it.copy(import = null, preview = null) }
    fun runImport() = finish(_state.value.import)
    fun skipImport() = finish()

    private fun finish(import: CsvImport? = null) {
        val s = _state.value
        if (s.saving) return
        _state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            runCatching { firstRun.finish(s.finalTemplate(), s.ownerName.trim(), import, backUp = s.backUp) }
                .onSuccess {
                    // The first one goes now, not at 03:00, or Home would say it's never been backed up. It outlives this screen.
                    if (s.backUp) appScope.launch { backups.backUp() }
                    _state.update { it.copy(finished = true) }
                }
                .onFailure { _state.update { it.copy(saving = false, message = uiText(R.string.onboarding_couldnt_finish)) } }
        }
    }
}
