package com.ringoid.origin.feed.view.lmm.messenger

import android.app.Application
import com.ringoid.domain.DomainUtil
import com.ringoid.domain.interactor.feed.CacheBlockedProfileIdUseCase
import com.ringoid.domain.interactor.feed.ClearCachedAlreadySeenProfileIdsUseCase
import com.ringoid.domain.interactor.feed.DropLmmChangedStatusUseCase
import com.ringoid.domain.interactor.feed.GetLmmUseCase
import com.ringoid.domain.interactor.image.CountUserImagesUseCase
import com.ringoid.domain.memory.IUserInMemoryCache
import com.ringoid.domain.model.feed.FeedItem
import com.ringoid.domain.model.feed.Lmm
import com.ringoid.origin.feed.view.lmm.base.BaseLmmFeedViewModel
import io.reactivex.Observable
import javax.inject.Inject

class MessengerViewModel @Inject constructor(
    getLmmUseCase: GetLmmUseCase, clearCachedAlreadySeenProfileIdsUseCase: ClearCachedAlreadySeenProfileIdsUseCase,
    cacheBlockedProfileIdUseCase: CacheBlockedProfileIdUseCase,
    countUserImagesUseCase: CountUserImagesUseCase, dropLmmChangedStatusUseCase: DropLmmChangedStatusUseCase,
    userInMemoryCache: IUserInMemoryCache, app: Application)
    : BaseLmmFeedViewModel(getLmmUseCase, clearCachedAlreadySeenProfileIdsUseCase, cacheBlockedProfileIdUseCase,
                           countUserImagesUseCase, dropLmmChangedStatusUseCase, userInMemoryCache, app) {

    override fun getFeedFromLmm(lmm: Lmm): List<FeedItem> = lmm.messages

    override fun sourceFeed(): Observable<List<FeedItem>> = getLmmUseCase.repository.feedMessages

    override fun getFeedName(): String = DomainUtil.SOURCE_FEED_MESSAGES
}
