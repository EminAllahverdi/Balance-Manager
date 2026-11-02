package com.eminallahverdi.balance

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.data.FirstRun
import com.eminallahverdi.balance.core.data.RecurringRepository
import com.eminallahverdi.balance.core.datastore.InstallPreferences
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.needsNotificationAsk
import com.eminallahverdi.balance.feature.onboarding.NotificationsAskRoute
import com.eminallahverdi.balance.feature.onboarding.OnboardingRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class StartScreen { LOADING, ONBOARDING, NOTIFICATIONS, APP }

/** Held here so it's decided once: a rotation can't send someone back to onboarding while it's finishing. */
@HiltViewModel
class StartViewModel @Inject constructor(
    firstRun: FirstRun,
    private val install: InstallPreferences,
    private val prefs: UserPreferencesRepository,
    private val recurring: RecurringRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _screen = MutableStateFlow(StartScreen.LOADING)
    val screen: StateFlow<StartScreen> = _screen.asStateFlow()

    init {
        viewModelScope.launch {
            _screen.value = when {
                firstRun.isNeeded() -> StartScreen.ONBOARDING
                asksNotifications() -> StartScreen.NOTIFICATIONS
                else -> StartScreen.APP
            }
        }
    }

    private suspend fun asksNotifications() = needsNotificationAsk(
        sdk = Build.VERSION.SDK_INT,
        remindersOn = prefs.current().eveningReminderEnabled || recurring.payments.first().any { !it.isFinished },
        allowed = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        asked = install.notificationsAsked.first(),
    )

    fun onboardingDone() {
        _screen.value = StartScreen.APP
    }

    fun notificationsAnswered() {
        viewModelScope.launch {
            install.setNotificationsAsked()
            _screen.value = StartScreen.APP
        }
    }
}

/** What opens before [content]: onboarding on a fresh install, or the notification question a restore leaves behind. */
@Composable
fun StartGate(viewModel: StartViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    when (screen) {
        StartScreen.LOADING -> Box(Modifier.fillMaxSize().background(BtTheme.colors.bgCanvas))
        StartScreen.ONBOARDING -> OnboardingRoute(onDone = viewModel::onboardingDone)
        StartScreen.NOTIFICATIONS -> NotificationsAskRoute(onAnswered = viewModel::notificationsAnswered)
        StartScreen.APP -> content()
    }
}
