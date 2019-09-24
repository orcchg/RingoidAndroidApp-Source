package com.ringoid.origin.rateus.view

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.ringoid.analytics.Analytics
import com.ringoid.base.view.ViewState
import com.ringoid.base.viewmodel.OneShot
import com.ringoid.domain.interactor.system.PostToSlackUseCase
import com.ringoid.origin.rateus.view.RateUsDialog.Companion.RATING_THRESHOLD
import com.ringoid.origin.view.base.settings.BaseSettingsViewModel
import javax.inject.Inject

class RateUsViewModel @Inject constructor(postToSlackUseCase: PostToSlackUseCase, app: Application)
    : BaseSettingsViewModel(postToSlackUseCase, app) {

    private val closeDialogOneShot by lazy { MutableLiveData<OneShot<Boolean>>() }
    private val openGooglePlayOneShot by lazy { MutableLiveData<OneShot<Boolean>>() }
    internal fun closeDialogOneShot(): LiveData<OneShot<Boolean>> = closeDialogOneShot
    internal fun openGooglePlayOneShot(): LiveData<OneShot<Boolean>> = openGooglePlayOneShot

    internal fun cancelRate() {
        analyticsManager.fire(Analytics.RATE_US_ALERT_CANCELED)
    }

    internal fun sendRating(rating: Int, feedBackText: String? = null, tag: String? = null) {
        when {
            rating < RATING_THRESHOLD -> {
                analyticsManager.fire(Analytics.RATE_US_ALERT_FEEDBACK, "rating" to "$rating")
                if (!feedBackText.isNullOrBlank()) {
                    viewState.value = ViewState.LOADING
                    suggestImprovements(text = feedBackText, tag = tag) {
                        viewState.value = ViewState.IDLE
                        closeDialogOneShot.value = OneShot(true)
                    }
                }
            }
            rating >= RATING_THRESHOLD -> {
                analyticsManager.fire(Analytics.RATE_US_ALERT_RATED, "rating" to "$rating")
                openGooglePlayOneShot.value = OneShot(true)
            }
        }
    }

    /* Lifecycle */
    // --------------------------------------------------------------------------------------------
    override fun onFreshStart() {
        super.onFreshStart()
        analyticsManager.fire(Analytics.RATE_US_ALERT_SHOWN)
    }
}
