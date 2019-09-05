package com.ringoid.repository.messenger

import com.ringoid.data.handleError
import com.ringoid.data.local.shared_prefs.accessSingle
import com.ringoid.datainterface.di.PerUser
import com.ringoid.datainterface.local.messenger.IMessageDbFacade
import com.ringoid.datainterface.remote.IRingoidCloudFacade
import com.ringoid.domain.DomainUtil
import com.ringoid.domain.action_storage.IActionObjectPool
import com.ringoid.domain.manager.ISharedPrefsManager
import com.ringoid.domain.misc.ImageResolution
import com.ringoid.domain.model.actions.MessageActionObject
import com.ringoid.domain.model.essence.messenger.MessageEssence
import com.ringoid.domain.model.messenger.Chat
import com.ringoid.domain.model.messenger.Message
import com.ringoid.domain.repository.messenger.IMessengerRepository
import com.ringoid.report.exception.SkipThisTryException
import com.ringoid.repository.BaseRepository
import com.ringoid.utility.randomString
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessengerRepository @Inject constructor(
    private val local: IMessageDbFacade,
    @PerUser private val sentMessagesLocal: IMessageDbFacade,
    cloud: IRingoidCloudFacade, spm: ISharedPrefsManager, aObjPool: IActionObjectPool)
    : BaseRepository(cloud, spm, aObjPool), IMessengerRepository {

    private val sentMessages = ConcurrentHashMap<String, MutableSet<Message>>()
    private val semaphores = mutableMapOf<String, Semaphore>()
    private var pollingDelay = 5000L  // in ms

    init {
        restoreCachedSentMessagesLocal()
    }

    /* Concurrency */
    // --------------------------------------------------------------------------------------------
    @Synchronized
    private fun tryAcquireLock(chatId: String): Boolean {
        if (!semaphores.contains(chatId)) {
            semaphores[chatId] = Semaphore(1)  // mutex
        }
        return semaphores[chatId]!!.tryAcquire()
    }

    @Synchronized
    private fun releaseLock(chatId: String) {
        semaphores[chatId]?.release()
    }

    // --------------------------------------------------------------------------------------------
    override fun getChat(chatId: String, resolution: ImageResolution): Single<Chat> =
        aObjPool.triggerSource().flatMap { getChatOnly(chatId, resolution, lastActionTime = it) }

    override fun getChatOnly(chatId: String, resolution: ImageResolution): Single<Chat> =
        getChatOnly(chatId, resolution, aObjPool.lastActionTime())

    override fun getChatNew(chatId: String, resolution: ImageResolution): Single<Chat> =
        aObjPool.triggerSource().flatMap { getChatNewOnly(chatId, resolution, lastActionTime = it) }

    override fun pollChatNew(chatId: String, resolution: ImageResolution): Flowable<Chat> =
        getChatNew(chatId, resolution)
            .repeatWhen { it.flatMap { Flowable.timer(pollingDelay, TimeUnit.MILLISECONDS, Schedulers.io()) } }
            .retryWhen { errorSource ->
                errorSource.flatMap { error ->
                    if (error is SkipThisTryException) {
                        // delay resubscription by 'pollingDelay' and continue polling
                        Timber.w("Skip current iteration and continue polling later on in $pollingDelay ms")
                        Flowable.timer(pollingDelay, TimeUnit.MILLISECONDS, Schedulers.io())
                    } else {
                        Flowable.error(error)
                    }
                }
            }

    // ------------------------------------------
    private fun getChatOnly(chatId: String, resolution: ImageResolution, lastActionTime: Long): Single<Chat> =
        spm.accessSingle { accessToken ->
            Single.just(0L)
                .flatMap {
                    if (tryAcquireLock(chatId)) {
                        getChatImpl(accessToken.accessToken, chatId, resolution, lastActionTime)
                            .concatWithUnconsumedSentLocalMessages(chatId)
                            .cacheUnconsumedSentLocalMessages(chatId)
                            .cacheMessagesFromChat()
                            .doOnSuccess { Timber.v("New chat full: ${it.print()}") }
                            .doFinally { releaseLock(chatId) }
                    } else {
                        Timber.w("Skip current iteration")
                        Single.error(SkipThisTryException())
                    }
                }
        }

    private fun getChatNewOnly(chatId: String, resolution: ImageResolution, lastActionTime: Long): Single<Chat> =
        spm.accessSingle { accessToken ->
            Single.just(0L)
                .flatMap {
                    if (tryAcquireLock(chatId)) {
                        getChatImpl(accessToken.accessToken, chatId, resolution, lastActionTime)
                            .filterOutChatOldMessages(chatId)
                            .concatWithUnconsumedSentLocalMessages(chatId)
                            .cacheUnconsumedSentLocalMessages(chatId)
                            .cacheMessagesFromChat()  // cache only new chat messages, including sent by current user (if any), because they've been uploaded
                            .doOnSuccess { Timber.v("New chat delta: ${it.print()}") }
                            .doFinally { releaseLock(chatId) }
                    } else {
                        Timber.w("Skip current iteration")
                        Single.error(SkipThisTryException())
                    }
                }
        }

    private fun getChatImpl(accessToken: String, chatId: String, resolution: ImageResolution, lastActionTime: Long) =
        cloud.getChat(accessToken, resolution, chatId, lastActionTime)
            .handleError(tag = "getChat(peerId=$chatId,$resolution,lat=$lastActionTime)", traceTag = "feeds/chat")
            .doOnSuccess { chat ->
                if (chat.pullAgainAfter >= 500L) {
                    pollingDelay = chat.pullAgainAfter  // update polling delay from response data
                }
            }
            .map { it.chat.mapToChat() }

    // ------------------------------------------
    private fun Single<Chat>.cacheMessagesFromChat(): Single<Chat> =
        flatMap { chat ->
            Completable.fromCallable { local.insertMessages(chat.messages, unread = 0) }
                       .toSingleDefault(chat)
        }

    /**
     * Compare old messages list to incoming messages list for chat given by [chatId] and retain only
     * new messages, that have appeared in chat data. These messages can also include messages sent
     * by the current user.
     */
    private fun Single<Chat>.filterOutChatOldMessages(chatId: String): Single<Chat> =
        toObservable()
        .withLatestFrom(local.countChatMessages(chatId = chatId).toObservable(),
            BiFunction { chat: Chat, localMessagesCount: Int ->
                if (chat.messages.size > localMessagesCount) {
                    val newMessages = chat.messages.subList(localMessagesCount, chat.messages.size)
                    chat.copyWith(newMessages)  // retain only new messages
                } else chat.copyWith(messages = emptyList())  // no new messages
            })
        .singleOrError()

    /**
     * Given the most recent sublist of chat's messages, analyze locally stored sent messages and
     * retain only unconsumed ones, i.e. those sent messages that don't present in chat.
     *
     * @note N-Readers-1-Writer pattern is used to concurrently access locally stored sent messages.
     */
    private fun Single<Chat>.concatWithUnconsumedSentLocalMessages(chatId: String): Single<Chat> =
        map { chat ->
            if (sentMessages.containsKey(chatId)) {
                val unconsumedSentMessages = mutableListOf<Message>().apply { addAll(sentMessages[chatId]!!) }  // order can change here
                chat.messages.forEach { message ->
                    if (message.isUserMessage()) {
                        unconsumedSentMessages.removeAll { it.id == message.clientId || it.clientId == message.clientId }
                    }
                }
                chat.unconsumedSentLocalMessages.addAll(unconsumedSentMessages.sortedBy { it.ts })
                sentMessages[chatId]!!.retainAll(unconsumedSentMessages)
            }
            chat  // result value
        }

    private fun Single<Chat>.cacheUnconsumedSentLocalMessages(chatId: String): Single<Chat> =
        flatMap { chat ->
            sentMessagesLocal.countChatMessages(chatId)
                .map { count -> count to chat }
        }
        .flatMap { (count, chat) ->
            if (count > 0) {
                Completable.fromCallable { sentMessagesLocal.deleteMessages(chatId) }
                           .toSingleDefault(chat)
            } else Single.just(chat)
        }
        .flatMap { chat ->
            if (sentMessages.containsKey(chatId) && sentMessages[chatId]!!.isNotEmpty()) {
                Completable.fromCallable { sentMessagesLocal.addMessages(sentMessages[chatId]!!, unread = 0) }
                .toSingleDefault(chat)
            } else Single.just(chat)
        }

    // --------------------------------------------------------------------------------------------
    override fun clearMessages(): Completable =
        Completable.fromCallable { local.deleteMessages() }
                   .andThen(clearSentMessages())

    override fun clearMessages(chatId: String): Completable =
        Completable.fromCallable { local.deleteMessages(chatId) }
                   .andThen(clearSentMessages(chatId))

    override fun clearSentMessages(): Completable =
        Completable.fromCallable {
            sentMessagesLocal.deleteMessages()
            sentMessages.clear()
        }

    override fun clearSentMessages(chatId: String): Completable =
        Completable.fromCallable {
            sentMessagesLocal.deleteMessages(chatId)
            sentMessages[chatId]?.clear()
        }

    // ------------------------------------------
    // messages cached since last network request + sent user messages (cache locally)
    override fun getMessages(chatId: String): Single<List<Message>> =
        getMessagesOnly(chatId)
            .retryWhen { errorSource ->
                errorSource.flatMap { error ->
                    if (error is SkipThisTryException) {
                        Flowable.timer(200, TimeUnit.MILLISECONDS, Schedulers.io())
                    } else {
                        Flowable.error(error)
                    }
                }
            }

    private fun getMessagesOnly(chatId: String): Single<List<Message>> =
        Single.just(0L)
            .flatMap {
                if (tryAcquireLock(chatId)) {
                    getMessagesImpl(chatId).doFinally { releaseLock(chatId) }
                } else {
                    Timber.w("Cache is busy, retry get local messages")
                    Single.error(SkipThisTryException())
                }
            }

    private fun getMessagesImpl(chatId: String): Single<List<Message>> =
        Maybe.fromCallable { local.markMessagesAsRead(chatId = chatId) }
            .flatMap { local.messages(chatId = chatId) }
            .concatWith(sentMessagesLocal.messages(chatId))
            .collect({ mutableListOf<Message>() }, { out, localMessages -> out.addAll(localMessages) })
            .map { it.reversed() }

    // ------------------------------------------
    override fun sendMessage(essence: MessageEssence): Single<Message> {
        val sentMessage = Message(
            id = "_${randomString()}_${essence.peerId}",  // client-side id
            chatId = essence.peerId,
            /** 'clientId' equals to 'id' */
            peerId = DomainUtil.CURRENT_USER_ID,
            text = essence.text,
            ts = System.currentTimeMillis())  // ts at sending message

        val sourceFeed = essence.aObjEssence?.sourceFeed ?: ""
        val aobj = MessageActionObject(
            clientId = sentMessage.clientId,
            text = essence.text,
            sourceFeed = sourceFeed,
            targetImageId = essence.aObjEssence?.targetImageId ?: DomainUtil.BAD_ID,
            targetUserId = essence.aObjEssence?.targetUserId ?: DomainUtil.BAD_ID)

        return Completable.fromCallable { sentMessagesLocal.addMessage(sentMessage) }
            .doOnSubscribe {
                keepSentMessage(sentMessage)
                aObjPool.put(aobj)  // immediate action object will be committed right now
            }
            .toSingleDefault(sentMessage)
    }

    // --------------------------------------------------------------------------------------------
    @Suppress("CheckResult")
    private fun restoreCachedSentMessagesLocal() {
        sentMessagesLocal.messages()
            .subscribeOn(Schedulers.io())
            .subscribe({ it.forEach { message -> keepSentMessage(message) } }, Timber::e)
    }

    @Synchronized
    private fun keepSentMessage(sentMessage: Message) {
        if (!sentMessages.containsKey(sentMessage.chatId)) {
            sentMessages[sentMessage.chatId] = Collections.newSetFromMap(ConcurrentHashMap())
        }
        sentMessages[sentMessage.chatId]!!.add(sentMessage)  // will be sorted by ts
    }
}