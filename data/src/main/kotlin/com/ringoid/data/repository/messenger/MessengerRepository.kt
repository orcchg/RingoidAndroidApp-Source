package com.ringoid.data.repository.messenger

import com.ringoid.data.di.PerUser
import com.ringoid.data.local.database.dao.messenger.MessageDao
import com.ringoid.data.local.database.model.messenger.MessageDbo
import com.ringoid.data.local.shared_prefs.accessSingle
import com.ringoid.data.remote.RingoidCloud
import com.ringoid.data.repository.BaseRepository
import com.ringoid.data.repository.handleError
import com.ringoid.domain.DomainUtil
import com.ringoid.domain.action_storage.IActionObjectPool
import com.ringoid.domain.debug.DebugLogUtil
import com.ringoid.domain.manager.ISharedPrefsManager
import com.ringoid.domain.misc.ImageResolution
import com.ringoid.domain.model.actions.MessageActionObject
import com.ringoid.domain.model.essence.messenger.MessageEssence
import com.ringoid.domain.model.mapList
import com.ringoid.domain.model.messenger.Chat
import com.ringoid.domain.model.messenger.Message
import com.ringoid.domain.repository.messenger.IMessengerRepository
import com.ringoid.utility.randomString
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessengerRepository @Inject constructor(
    private val local: MessageDao, @PerUser private val sentMessagesLocal: MessageDao,
    cloud: RingoidCloud, spm: ISharedPrefsManager, aObjPool: IActionObjectPool)
    : BaseRepository(cloud, spm, aObjPool), IMessengerRepository {

    override fun getChat(chatId: String, resolution: ImageResolution, sourceFeed: String): Single<Chat> =
        aObjPool.triggerSource().flatMap { getChatOnly(chatId, resolution, lastActionTime = it, sourceFeed = sourceFeed) }

    private fun getChatOnly(chatId: String, resolution: ImageResolution, lastActionTime: Long, sourceFeed: String): Single<Chat> =
        spm.accessSingle {
            getChatImpl(it.accessToken, chatId, resolution, lastActionTime)
                .cacheMessagesFromChat(sourceFeed)
        }

    override fun getChatNew(chatId: String, resolution: ImageResolution, sourceFeed: String): Single<Chat> =
        aObjPool.triggerSource().flatMap { getChatNewOnly(chatId, resolution, lastActionTime = it, sourceFeed = sourceFeed) }

    private fun getChatNewOnly(chatId: String, resolution: ImageResolution, lastActionTime: Long, sourceFeed: String): Single<Chat> =
        spm.accessSingle {
            getChatImpl(it.accessToken, chatId, resolution, lastActionTime)
                .filterOutChatOldMessages(chatId, sourceFeed)
                .cacheMessagesFromChat(sourceFeed)  // cache only new chat messages, including sent by current user (if any), because they've been uploaded
//                .filterOutChatSentMessages(chatId, sourceFeed)  // if there are sent messages by current user, filter them out to avoid duplicates on subscriber's side
//                .concatWithUnconsumedSentLocalMessages(chatId, sourceFeed)
//                .clearCachedSentMessages()
        }

    private fun getChatImpl(accessToken: String, chatId: String, resolution: ImageResolution, lastActionTime: Long) =
        cloud.getChat(accessToken, resolution, chatId, lastActionTime)
            .handleError(tag = "getChat(peerId=$chatId,$resolution,lat=$lastActionTime)", traceTag = "feeds/chat")
            .doOnSuccess { DebugLogUtil.v("# Chat messages: [${it.chat.messages.size}] originally") }
            .map { it.chat.mapToChat() }

    // ------------------------------------------
    private fun Single<Chat>.cacheMessagesFromChat(sourceFeed: String): Single<Chat> =
        flatMap { chat ->
            val messages = mutableListOf<MessageDbo>()
                .apply { addAll(chat.messages.map { MessageDbo.from(it, sourceFeed) }) }
            Completable.fromCallable { local.insertMessages(messages) }
                .toSingleDefault(chat)
        }

    private fun Single<Chat>.clearCachedSentMessages(): Single<Chat> =
        doOnSuccess { sentMessagesLocal.deleteMessages() }

    /**
     * Compare old messages list to incoming messages list for chat given by [chatId] and retain only
     * new messages, that have appeared in chat data. These messages can also include messages sent
     * by the current user.
     */
    private fun Single<Chat>.filterOutChatOldMessages(chatId: String, sourceFeed: String): Single<Chat> =
        toObservable()
        .withLatestFrom(local.messages(chatId = chatId, sourceFeed = sourceFeed).toObservable(),
            BiFunction { chat: Chat, localMessages: List<MessageDbo> ->
                Timber.v("Old messages [${localMessages.size}]: ${localMessages.joinToString(", ", "{", "}", transform = { it.text })}")
                if (chat.messages.size > localMessages.size) {
                    val newMessages = chat.messages.subList(localMessages.size, chat.messages.size)
                    chat.copyWith(newMessages)  // retain only new messages
                } else chat.copyWith(messages = emptyList())  // no new messages
            })
        .singleOrError()
        .doOnSuccess {
            Timber.v("New messages [${it.messages.size}]: ${it.messagesToString()}, ${it.messagesDetailsToString()}")
            DebugLogUtil.v("# Chat messages: [${it.messages.size}] after filtering out cached (old) messages")
        }

    /**
     * Compare chat's messages list to locally stored sent messages for chat given by [chatId] and retain only
     * those messages, that are completely new, discarding any messages that have been potentially sent already
     * by the current user, to avoid duplicates, because subscriber is likely to show sent message at the moment
     * of sending, so it should be excluded further on refresh chat's data.
     */
//    private fun Single<Chat>.filterOutChatSentMessages(chatId: String, sourceFeed: String): Single<Chat> =
//        toObservable()
//        .withLatestFrom(sentMessagesLocal.messages(chatId = chatId, sourceFeed = sourceFeed).toObservable(),
//            BiFunction { chat: Chat, sentLocalMessages: List<MessageDbo> ->
//                val consumedSentMessages = mutableListOf<MessageDbo>()
//                val iter = chat.messages.iterator()
//
////                Timber.v("Sent local messages [${sentLocalMessages.size}]: ${sentLocalMessages.joinToString(", ", "{", "}", transform = { it.text })}, ${sentLocalMessages.joinToString(",", "[", "]", transform = { "{${it.peerId.substring(0..3)}:${it.clientId.substring(0..5)}:${it.text}" })}")
//                while (iter.hasNext()) {
//                    val message = iter.next()
//                    if (message.isUserMessage()) {
//                        sentLocalMessages.find { it.clientId == message.clientId }
//                            ?.let { sentMessage ->
//                                consumedSentMessages.add(sentMessage)
//                                iter.remove()
//                            }
//                    }
//                }
//                chat to consumedSentMessages
//            })
//        .singleOrError()
//        .flatMap { (chat, consumedSentMessages) ->
//            Completable.fromCallable { sentMessagesLocal.deleteMessages(consumedSentMessages) }
//                       .toSingleDefault(chat)
//        }
//        .doOnSuccess {
//            Timber.v("Final messages [${it.messages.size}]: ${it.messagesToString()}, ${it.messagesDetailsToString()}")
//            DebugLogUtil.v("# Chat messages: [${it.messages.size}] after filtering out sent messages")
//        }
//
//    private fun Single<Chat>.concatWithUnconsumedSentLocalMessages(chatId: String, sourceFeed: String): Single<Chat> =
//        toObservable()
//        .withLatestFrom(sentMessagesLocal.messages(chatId = chatId, sourceFeed = sourceFeed).toObservable(),
//            BiFunction { chat: Chat, sentLocalMessages: List<MessageDbo> ->
//                val unconsumedSentMessages = mutableListOf<MessageDbo>().apply { addAll(sentLocalMessages) }
//                chat.messages.forEach { message ->
//                    if (message.isUserMessage()) {
//                        unconsumedSentMessages.removeAll { it.id == message.clientId || it.clientId == message.clientId }
//                    }
//                }
////                Timber.v("Concat sent messages [${unconsumedSentMessages.size}]: ${unconsumedSentMessages.joinToString(", ", "{", "}", transform = { it.text })}, ${unconsumedSentMessages.joinToString(",", "[", "]", transform = { "{${it.peerId.substring(0..3)}:${it.clientId.substring(0..5)}:${it.text}" })}")
//                chat.messages.addAll(unconsumedSentMessages.mapList())
//                chat
//            })
//        .singleOrError()

    // --------------------------------------------------------------------------------------------
    override fun clearMessages(): Completable =
        Completable.fromCallable {
            local.deleteMessages()
            sentMessagesLocal.deleteMessages()
        }

    override fun clearMessages(chatId: String): Completable =
        Completable.fromCallable {
            local.deleteMessages(chatId)
            sentMessagesLocal.deleteMessages(chatId)
        }

    // messages cached since last network request + sent user messages (cache locally)
    override fun getMessages(chatId: String, sourceFeed: String): Single<List<Message>> =
        Maybe.fromCallable { local.markMessagesAsRead(chatId = chatId, sourceFeed = sourceFeed) }
            .flatMap { local.messages(chatId = chatId, sourceFeed = sourceFeed) }
            .concatWith(sentMessagesLocal.messages(chatId))
            .collect({ mutableListOf<MessageDbo>() }, { out, localMessages -> out.addAll(localMessages) })
            .map { it.mapList().reversed() }

    override fun sendMessage(essence: MessageEssence): Single<Message> {
        val sentMessage = Message(
            id = "_${randomString()}_${essence.peerId}",  // client-side id
            chatId = essence.peerId,
            /** 'clientId' equals to 'id' */
            peerId = DomainUtil.CURRENT_USER_ID,
            text = essence.text)

        val aobj = MessageActionObject(
            clientId = sentMessage.clientId, text = essence.text,
            sourceFeed = essence.aObjEssence?.sourceFeed ?: "",
            targetImageId = essence.aObjEssence?.targetImageId ?: DomainUtil.BAD_ID,
            targetUserId = essence.aObjEssence?.targetUserId ?: DomainUtil.BAD_ID)

        aObjPool.put(aobj)
        return Single.just(sentMessage).cacheSentMessage()
    }

    // ------------------------------------------
    private fun Single<Message>.cacheSentMessage(): Single<Message> =
        flatMap { message ->
            Completable.fromCallable { sentMessagesLocal.addMessage(MessageDbo.from(message)) }
                       .toSingleDefault(message)
        }
}
