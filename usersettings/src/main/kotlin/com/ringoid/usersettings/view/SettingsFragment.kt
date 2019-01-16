package com.ringoid.usersettings.view

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import com.jakewharton.rxbinding3.view.clicks
import com.ringoid.base.BuildConfig
import com.ringoid.base.view.BaseFragment
import com.ringoid.base.view.ViewState
import com.ringoid.origin.AppRes
import com.ringoid.origin.navigation.ExternalNavigator
import com.ringoid.origin.navigation.logout
import com.ringoid.origin.navigation.navigate
import com.ringoid.origin.view.dialog.Dialogs
import com.ringoid.usersettings.R
import com.ringoid.utility.clickDebounce
import kotlinx.android.synthetic.main.fragment_settings.*

class SettingsFragment : BaseFragment<SettingsViewModel>() {

    companion object {
        internal const val TAG = "SettingsFragment_tag"

        fun newInstance(): SettingsFragment = SettingsFragment()
    }

    override fun getVmClass(): Class<SettingsViewModel> = SettingsViewModel::class.java

    override fun getLayoutId(): Int = R.layout.fragment_settings

    // --------------------------------------------------------------------------------------------
    override fun onViewStateChange(newState: ViewState) {
        super.onViewStateChange(newState)
        when (newState) {
            is ViewState.CLOSE -> logout(this)
            else -> { /* no-op */ }
        }
    }

    /* Lifecycle */
    // --------------------------------------------------------------------------------------------
    @Suppress("CheckResult", "AutoDispose")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (toolbar as Toolbar).apply {
            setNavigationOnClickListener { activity?.onBackPressed() }
            setTitle(R.string.settings_title)
        }

        item_delete_account.clicks().compose(clickDebounce()).subscribe {
            Dialogs.showTextDialog(activity, titleResId = R.string.settings_account_delete_dialog_title,
                descriptionResId = R.string.settings_account_delete_dialog_description,
                positiveBtnLabelResId = R.string.button_delete, negativeBtnLabelResId = R.string.button_cancel,
                positiveListener = { _, _ -> vm.deleteAccount() })
        }
        item_legal.clicks().compose(clickDebounce()).subscribe { navigate(this, path = "/settings_info") }
        item_support.clicks().compose(clickDebounce()).subscribe {
            val appInfo = "${BuildConfig.VERSION_NAME}, [${Build.MODEL}, ${Build.MANUFACTURER}, ${Build.PRODUCT}], " +
                          "[${Build.VERSION.RELEASE}, ${Build.VERSION.SDK_INT}]"
            val subject = String.format(AppRes.EMAIL_SUPPORT_MAIL_SUBJECT, appInfo)
            ExternalNavigator.openEmailComposer(this, email = "support@ringoid.com", subject = subject)
        }
    }
}
