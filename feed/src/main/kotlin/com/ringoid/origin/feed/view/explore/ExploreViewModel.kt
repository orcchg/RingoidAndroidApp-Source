package com.ringoid.origin.feed.view.explore

import android.app.Application
import androidx.lifecycle.MutableLiveData
import com.ringoid.base.view.IListScrollCallback
import com.ringoid.base.view.ViewState
import com.ringoid.domain.DomainUtil
import com.ringoid.domain.interactor.base.Params
import com.ringoid.domain.interactor.feed.CacheBlockedProfileIdUseCase
import com.ringoid.domain.interactor.feed.DebugGetNewFacesUseCase
import com.ringoid.domain.interactor.feed.GetNewFacesUseCase
import com.ringoid.domain.interactor.image.CountUserImagesUseCase
import com.ringoid.domain.model.feed.Feed
import com.ringoid.origin.ScreenHelper
import com.ringoid.origin.feed.view.FeedViewModel
import com.uber.autodispose.lifecycle.autoDisposable
import timber.log.Timber
import javax.inject.Inject

class ExploreViewModel @Inject constructor(
    private val getNewFacesUseCase: GetNewFacesUseCase,
    private val debugGetNewFacesUseCase: DebugGetNewFacesUseCase,
    cacheBlockedProfileIdUseCase: CacheBlockedProfileIdUseCase,
    countUserImagesUseCase: CountUserImagesUseCase, app: Application)
    : FeedViewModel(cacheBlockedProfileIdUseCase, countUserImagesUseCase, app),
    IListScrollCallback {

    val feed by lazy { MutableLiveData<Feed>() }
    private var isLoadingMore: Boolean = false
    private var nextPage: Int = 0

    override fun getFeedName(): String = "new_faces"

    // ------------------------------------------
    override fun getFeed() {
//        debugGetNewFacesUseCase.source(params = prepareDebugFeedParams())
        getNewFacesUseCase.source(params = prepareFeedParams())
            .doOnSubscribe { viewState.value = ViewState.LOADING }
            .doOnSuccess {
                viewState.value = if (it.isEmpty()) ViewState.CLEAR(mode = ViewState.CLEAR.MODE_EMPTY_DATA)
                                  else ViewState.IDLE
            }
            .doOnError { viewState.value = ViewState.ERROR(it) }
            .autoDisposable(this)
            .subscribe({ feed.value = it }, Timber::e)
    }

    private fun getMoreFeed() {
//        debugGetNewFacesUseCase.source(params = prepareDebugFeedParams())
        getNewFacesUseCase.source(params = prepareFeedParams())
            .doOnSubscribe { viewState.value = ViewState.PAGING }
            .doOnSuccess { viewState.value = ViewState.IDLE }
            .doOnError { viewState.value = ViewState.ERROR(it) }
            .doFinally { isLoadingMore = false }
            .autoDisposable(this)
            .subscribe({ feed.value = it }, Timber::e)
    }

    private fun prepareFeedParams(): Params =
        Params().put(ScreenHelper.getLargestPossibleImageResolution(context))
                .put("limit", DomainUtil.LIMIT_PER_PAGE)

    private fun prepareDebugFeedParams(): Params = Params().put("page", nextPage++)

    // ------------------------------------------
    override fun onScroll(itemsLeftToEnd: Int) {
        if (isLoadingMore) {
            Timber.v("Skip onScroll event in error state or during loading more")
            return
        }
        if (itemsLeftToEnd <= DomainUtil.LOAD_MORE_THRESHOLD) {
            isLoadingMore = true
            getMoreFeed()
        }
    }
}
