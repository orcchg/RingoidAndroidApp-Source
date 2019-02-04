package com.ringoid.data.repository.debug

import com.ringoid.data.action_storage.ActionObjectPool
import com.ringoid.data.local.database.dao.feed.UserFeedDao
import com.ringoid.data.local.database.dao.messenger.MessageDao
import com.ringoid.data.remote.RingoidCloud
import com.ringoid.data.remote.model.feed.FeedResponse
import com.ringoid.data.remote.model.feed.ProfileEntity
import com.ringoid.data.remote.model.image.ImageEntity
import com.ringoid.data.repository.feed.FeedRepository
import com.ringoid.data.repository.handleError
import com.ringoid.domain.DomainUtil
import com.ringoid.domain.model.feed.Feed
import com.ringoid.domain.model.messenger.Message
import com.ringoid.domain.repository.ISharedPrefsManager
import com.ringoid.domain.repository.debug.IDebugFeedRepository
import io.reactivex.Completable
import io.reactivex.Single
import javax.inject.Inject
import javax.inject.Named

class DebugFeedRepository @Inject constructor(messengerLocal: MessageDao,
    @Named("alreadySeen") alreadySeenProfilesCache: UserFeedDao,
    @Named("block") blockedProfilesCache: UserFeedDao,
    cloud: RingoidCloud, spm: ISharedPrefsManager, aObjPool: ActionObjectPool)
    : FeedRepository(messengerLocal, alreadySeenProfilesCache, blockedProfilesCache, cloud, spm, aObjPool),
      IDebugFeedRepository {

    /* Debug */
    // --------------------------------------------------------------------------------------------
    private var requestAttempt: Int = 0
    private var requestRepeatAfterDelayAttempt: Int = 0

    private fun getAndIncrementRequestAttempt(): Int = requestAttempt++
    private fun getAndIncrementRequestRepeatAfterDelayAttempt(): Int = requestRepeatAfterDelayAttempt++

    override fun debugGetNewFacesWithFailNTimesBeforeSuccessForPage(page: Int, failPage: Int, count: Int): Single<Feed> =
        if (page == failPage) {
            Single.just(DebugRepository.getFeed(page))
                .flatMap {
                    val i = getAndIncrementRequestAttempt()
                    if (i < count) Single.just(it.convertToFeedResponse(errorCode = "DebugError", errorMessage = "Debug error"))
                    else Single.just(it.convertToFeedResponse())
                }
        } else {
            Single.just(DebugRepository.getFeed(page)).map { it.convertToFeedResponse() }
        }
        .handleError(count = count * 2, delay = 250L)
        .filterAlreadySeenProfilesFeed()
        .filterBlockedProfilesFeed()
        .cacheNewFacesAsAlreadySeen()
        .map { it.map() }

    override fun debugGetNewFacesWithRepeatForPageAfterDelay(page: Int, repeatPage: Int, delay: Long): Single<Feed> =
        if (page == repeatPage) {
            Single.just(DebugRepository.getFeed(page))
                .flatMap {
                    val i = getAndIncrementRequestRepeatAfterDelayAttempt()
                    Single.just(it.convertToFeedResponse(repeatAfterSec = if (i < 1) delay else 0))
                }
        } else {
            Single.just(DebugRepository.getFeed(page)).map { it.convertToFeedResponse() }
        }
        .handleError()
        .filterAlreadySeenProfilesFeed()
        .filterBlockedProfilesFeed()
        .cacheNewFacesAsAlreadySeen()
        .map { it.map() }

    override fun dropFlags(): Completable =
        Single.just(0L)
            .doOnSubscribe {
                requestAttempt = 0
                requestRepeatAfterDelayAttempt = 0
            }
            .ignoreElement()  // convert to Completable

    // ------------------------------------------
    private fun Feed.convertToFeedResponse(errorCode: String = "", errorMessage: String = "", repeatAfterSec: Long = 0L): FeedResponse =
        FeedResponse(profiles = this.profiles.map { ProfileEntity(id = it.id, sortPosition = 0, images = it.images.map { ImageEntity(id = it.id, uri = it.uri ?: "") }) }, errorCode = errorCode, errorMessage = errorMessage, repeatAfterSec = repeatAfterSec)
}

fun getDebugChat(): List<Message> = listOf(
        Message(chatId = "peer1", peerId = "peer1", text = "1"),  // NEW
        Message(chatId = "peer1", peerId = DomainUtil.CURRENT_USER_ID, text = "2 my?"),
        Message(chatId = "peer1", peerId = DomainUtil.CURRENT_USER_ID, text = "3 my"),
        Message(chatId = "peer1", peerId = "peer1", text = "4"),
        Message(chatId = "peer1", peerId = "peer1", text = "5"),
        Message(chatId = "peer1", peerId = "peer1", text = "6"),
        Message(chatId = "peer1", peerId = DomainUtil.CURRENT_USER_ID, text = "7 my"),
        Message(chatId = "peer1", peerId = DomainUtil.CURRENT_USER_ID, text = "8 my"),
        Message(chatId = "peer1", peerId = "peer1", text = "9"),
        Message(chatId = "peer1", peerId = "peer1", text = "10"),
        Message(chatId = "peer1", peerId = "peer1", text = "11"),
        Message(chatId = "peer1", peerId = DomainUtil.CURRENT_USER_ID, text = "12 my"),
        Message(chatId = "peer1", peerId = DomainUtil.CURRENT_USER_ID, text = "13 my"),
        Message(chatId = "peer1", peerId = "peer1", text = "14"),
        Message(chatId = "peer1", peerId = "peer1", text = "15"),
        Message(chatId = "peer1", peerId = "peer1", text = "16"),
        Message(chatId = "peer1", peerId = DomainUtil.CURRENT_USER_ID, text = "17 my"),
        Message(chatId = "peer1", peerId = DomainUtil.CURRENT_USER_ID, text = "18 my"),
        Message(chatId = "peer1", peerId = "peer1", text = "19"),
        Message(chatId = "peer1", peerId = "peer1", text = "20"))  // OLD