package com.ringoid.origin.view.main

import android.app.Application
import com.ringoid.base.viewmodel.BaseViewModel
import com.ringoid.domain.debug.DebugLogUtil
import com.ringoid.domain.interactor.base.Params
import com.ringoid.domain.interactor.push.UpdatePushTokenUseCase
import com.ringoid.domain.log.SentryUtil
import com.ringoid.domain.model.essence.push.PushTokenEssenceUnauthorized
import timber.log.Timber

abstract class BaseMainViewModel(private val updatePushTokenUseCase: UpdatePushTokenUseCase,
                                 app: Application) : BaseViewModel(app) {

    /* Lifecycle */
    // --------------------------------------------------------------------------------------------
    override fun onFreshStart() {
        super.onFreshStart()
        SentryUtil.setUser(spm)
    }

    // --------------------------------------------------------------------------------------------
    fun updatePushToken(token: String) {
        val params = Params().put(PushTokenEssenceUnauthorized(token))
        updatePushTokenUseCase.source(params = params)
            .subscribe({ DebugLogUtil.i("Successfully uploaded Firebase push token: $token") }, Timber::e)
    }
}
