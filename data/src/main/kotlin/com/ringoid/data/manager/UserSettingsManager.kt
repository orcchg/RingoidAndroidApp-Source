package com.ringoid.data.manager

import com.ringoid.data.local.shared_prefs.SharedPrefsManager
import com.ringoid.domain.manager.IUserSettingsManager
import com.ringoid.domain.model.user.UserSettings
import com.ringoid.utility.manager.LocaleManager
import com.ringoid.utility.manager.TimezoneManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserSettingsManager @Inject constructor(
    private val localeManager: LocaleManager, private val spm: SharedPrefsManager,
    private val timezoneManager: TimezoneManager) : IUserSettingsManager {

    override fun getUserSettings(): UserSettings {
        val locale = localeManager.getLang()
        val pushEnabled = spm.getUserSettingPushEnabled()
        val timeZone = timezoneManager.getTimeZone()
        return UserSettings(locale = locale, push = pushEnabled, timeZone = timeZone)
    }
}