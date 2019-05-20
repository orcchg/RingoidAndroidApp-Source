package com.ringoid.data.manager

import com.ringoid.domain.BuildConfig
import com.ringoid.domain.manager.IRuntimeConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuntimeConfig @Inject constructor() : IRuntimeConfig {

    private var isDeveloper: Boolean = BuildConfig.IS_STAGING

    override fun isDeveloper(): Boolean = isDeveloper

    internal fun setDeveloperMode(flag: Boolean) {
        isDeveloper = flag
    }
}
