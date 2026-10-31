package com.eminallahverdi.balance

import com.eminallahverdi.balance.core.common.ApplicationScope
import com.eminallahverdi.balance.core.data.CatalogRepository
import com.eminallahverdi.balance.core.data.StarterTemplate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Until onboarding exists, a new install starts from the household template. */
class FirstRunSeeder @Inject constructor(
    private val catalog: CatalogRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun seedIfEmpty() {
        scope.launch {
            if (catalog.isEmpty()) catalog.applyTemplate(StarterTemplate.household())
        }
    }
}
