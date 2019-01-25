package com.ringoid.origin.view.error

import android.os.Bundle
import androidx.annotation.LayoutRes
import com.jakewharton.rxbinding3.view.clicks
import com.ringoid.base.deeplink.AppNav
import com.ringoid.base.view.SimpleBaseActivity
import com.ringoid.origin.R
import com.ringoid.origin.navigation.ExternalNavigator
import com.ringoid.utility.clickDebounce
import kotlinx.android.synthetic.main.activity_old_app_version.*

@AppNav("old_version")
class OldAppVersionActivity : SimpleBaseActivity() {

    @LayoutRes override fun getLayoutId(): Int = R.layout.activity_old_app_version

    /* Lifecycle */
    // --------------------------------------------------------------------------------------------
    @Suppress("CheckResult", "AutoDispose")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        btn_google_play.clicks().compose(clickDebounce())
            .subscribe {
                ExternalNavigator.openGooglePlay(this)
                finish()
            }
    }
}