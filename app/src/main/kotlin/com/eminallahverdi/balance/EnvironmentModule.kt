package com.eminallahverdi.balance

import com.eminallahverdi.balance.core.model.AccountantLock
import com.eminallahverdi.balance.core.model.AppEnvironment
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Only the app module has flavors, so this is how the rest of the code learns which app it's in. */
@Module
@InstallIn(SingletonComponent::class)
object EnvironmentModule {
    @Provides
    fun environment(): AppEnvironment = if (BuildConfig.FLAVOR == "dev") AppEnvironment.DEV else AppEnvironment.PROD

    /** The accountant copy's PIN, as CI built its hash in: empty without the secret, which keeps the copy locked. */
    @Provides
    fun accountantLock(): AccountantLock = AccountantLock(BuildConfig.ACCOUNTANT_PIN_HASH)
}
