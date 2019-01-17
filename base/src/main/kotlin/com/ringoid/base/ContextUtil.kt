package com.ringoid.base

import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.ringoid.base.view.BaseActivity
import com.ringoid.base.view.BaseFragment
import java.util.*

fun Activity.isActivityDestroyed(): Boolean = ContextUtil.isActivityDestroyed(this)

object ContextUtil {

    fun deviceInfo(): String =
        "Android ${Build.VERSION.SDK_INT}, device [id: ${Build.ID}] = ${Build.DEVICE}, " +
        "brand = ${Build.BRAND}, vendor = ${Build.MANUFACTURER}, tz = ${TimeZone.getDefault().displayName}, " +
        "time = ${Calendar.getInstance().time}, locale = ${Locale.getDefault().language}"

    fun isActivityDestroyed(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return activity.isDestroyed
        }
        if (BaseActivity::class.java.isInstance(activity)) {
            return (activity as BaseActivity<*>).isDestroying
        }
        return activity.isFinishing
    }

    fun isAfterOnSaveInstanceStateFragment(fragment: Fragment): Boolean {
        if (BaseFragment::class.java.isInstance(fragment)) {
            return (fragment as BaseFragment<*>).isOnSaveInstanceState
        }
        return fragment.isStateSaved
    }

    fun isSafeToShowDialog(dialog: DialogFragment) = !isAfterOnSaveInstanceStateFragment(dialog)

    // --------------------------------------------------------------------------------------------
    fun getShareEventIntent(content: String, subject: String): Intent =
        Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, content)
        }
}