package com.ringoid.base.viewmodel

import android.app.Application
import androidx.lifecycle.MutableLiveData
import com.ringoid.base.view.ViewState
import com.ringoid.domain.interactor.base.Params
import com.ringoid.domain.interactor.user.GetUserAccessTokenUseCase
import com.ringoid.domain.model.user.AccessToken
import com.ringoid.domain.repository.ISharedPrefsManager
import com.uber.autodispose.AutoDispose.autoDisposable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import javax.inject.Inject
import kotlin.reflect.KFunction

abstract class BaseViewModel(app: Application) : AutoDisposeViewModel(app) {

    @Inject lateinit var getUserAccessTokenUseCase: GetUserAccessTokenUseCase
    @Inject lateinit var spm: ISharedPrefsManager

    protected var subs: Disposable? = null  // for single subscription
    protected val cs: CompositeDisposable = CompositeDisposable()  // for multiple subscriptions

    val accessToken: MutableLiveData<AccessToken?> by lazy { MutableLiveData<AccessToken?>() }
    val navigation: MutableLiveData<KFunction<*>> by lazy { MutableLiveData<KFunction<*>>() }
    val viewState: MutableLiveData<ViewState> by lazy { MutableLiveData<ViewState>() }

    /* Lifecycle */
    // --------------------------------------------------------------------------------------------
    override fun onCleared() {
        super.onCleared()
        subs?.dispose()
        subs = null
        cs.clear()
    }

    // --------------------------------------------------------------------------------------------
    fun obtainAccessToken() {
        getUserAccessTokenUseCase.source(Params.EMPTY)
            .`as`(autoDisposable(this))
            .subscribe({ accessToken.value = it }, { accessToken.value = null })
    }
}
