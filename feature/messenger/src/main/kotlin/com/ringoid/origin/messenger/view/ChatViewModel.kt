package com.ringoid.origin.messenger.view

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.ringoid.analytics.Analytics
import com.ringoid.base.eventbus.BusEvent
import com.ringoid.base.view.ViewState
import com.ringoid.base.viewmodel.BaseViewModel
import com.ringoid.domain.DomainUtil
import com.ringoid.debug.DebugLogUtil
import com.ringoid.domain.interactor.base.Params
import com.ringoid.domain.interactor.messenger.GetChatNewMessagesUseCase
import com.ringoid.domain.interactor.messenger.GetMessagesForPeerUseCase
import com.ringoid.domain.interactor.messenger.PollChatNewMessagesUseCase
import com.ringoid.domain.interactor.messenger.SendMessageToPeerUseCase
import com.ringoid.domain.memory.ChatInMemoryCache
import com.ringoid.domain.model.essence.action.ActionObjectEssence
import com.ringoid.domain.model.essence.messenger.MessageEssence
import com.ringoid.domain.model.messenger.Chat
import com.ringoid.domain.model.messenger.Message
import com.ringoid.origin.AppInMemory
import com.ringoid.origin.AppRes
import com.ringoid.origin.model.OnlineStatus
import com.ringoid.origin.utils.ScreenHelper
import com.ringoid.origin.view.main.LcNavTab
import com.uber.autodispose.lifecycle.autoDisposable
import io.reactivex.Flowable
import io.reactivex.subjects.PublishSubject
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ChatViewModel @Inject constructor(
    private val getChatNewMessagesUseCase: GetChatNewMessagesUseCase,
    private val getMessagesForPeerUseCase: GetMessagesForPeerUseCase,
    private val pollChatNewMessagesUseCase: PollChatNewMessagesUseCase,
    private val sendMessageToPeerUseCase: SendMessageToPeerUseCase,
    app: Application) : BaseViewModel(app) {

    private data class ChatData(val chatId: String)

    private val messages by lazy { MutableLiveData<List<Message>>() }
    private val newMessages by lazy { MutableLiveData<List<Message>>() }
    private val sentMessage by lazy { MutableLiveData<Message>() }
    private val onlineStatus by lazy { MutableLiveData<OnlineStatus>() }
    private val peerName by lazy { MutableLiveData<String>() }
    internal fun messages(): LiveData<List<Message>> = messages
    internal fun newMessages(): LiveData<List<Message>> = newMessages
    internal fun sentMessage(): LiveData<Message> = sentMessage
    internal fun onlineStatus(): LiveData<OnlineStatus> = onlineStatus
    internal fun peerName(): LiveData<String> = peerName

    private var chatData: ChatData? = null
    private var currentMessageList: List<Message> = emptyList()

    private val incomingPushMessage = PublishSubject.create<BusEvent>()

    private fun subscribeOnPush() {
        if (incomingPushMessage.hasObservers()) {
            return
        }

        Timber.v("Subscribe on incoming messages push notifications")
        incomingPushMessage
            .map { it as BusEvent.PushNewMessage }
            .filter { it.peerId == chatData?.chatId }
            .debounce(DomainUtil.DEBOUNCE_PUSH, TimeUnit.MILLISECONDS)
            .flatMapSingle { getChatNewMessagesUseCase.source(prepareGetChatParams(profileId = it.peerId)) }
            .autoDisposable(this)
            .subscribe(::handleChatUpdate, DebugLogUtil::e)
    }

    private fun countPeerMessages(messages: List<Message>): Int =
        messages.count { it.peerId != DomainUtil.CURRENT_USER_ID }

    private fun countUserMessages(messages: List<Message>): Int =
        messages.count { it.peerId == DomainUtil.CURRENT_USER_ID }

    // --------------------------------------------------------------------------------------------
    /**
     * Get chat messages from the local storage. Those messages had been stored locally
     * when Lmm data fetching had completed.
     */
    fun getMessages(profileId: String) {
        chatData = ChatData(chatId = profileId)
        // The most recent message is the first one in list, positions ascending and message age is also ascending
        getMessagesForPeerUseCase.source(params = Params().put("chatId", profileId))
            .doOnSubscribe { viewState.value = ViewState.LOADING }  // get chat for peer progress
            .doOnSuccess { viewState.value = ViewState.IDLE }  // get chat for peer success
            .doOnError { viewState.value = ViewState.ERROR(it) }  // get chat for peer failed
            .autoDisposable(this)
            .subscribe({ msgs ->
                ChatInMemoryCache.setPeerMessagesCountIfChanged(profileId = profileId, count = countPeerMessages(msgs))
                ChatInMemoryCache.setUserMessagesCountIfChanged(chatId = profileId, count = countUserMessages(msgs))
                currentMessageList = msgs.toMutableList().apply { removeAll { it.isLocal() } }
                messages.value = msgs
                startPollingChat(profileId = profileId, delay = 50L)
            }, DebugLogUtil::e)
    }

    @Suppress("CheckResult")
    fun sendMessage(peerId: String, imageId: String = DomainUtil.BAD_ID, text: String?,
                    sourceFeed: LcNavTab = LcNavTab.MESSAGES) {
        if (text.isNullOrBlank()) {
            return  // don't send empty text
        }

        val essence = ActionObjectEssence(actionType = "MESSAGE", sourceFeed = sourceFeed.feedName, targetImageId = imageId, targetUserId = peerId)
        val message = MessageEssence(peerId = peerId, text = text.trim(), aObjEssence = essence)

        sendMessageToPeerUseCase.source(params = Params().put(message))
            .doOnSubscribe { viewState.value = ViewState.LOADING }  // send message to peer in chat progress
            .doOnSuccess { viewState.value = ViewState.IDLE }  // send message to peer in chat success
            .doOnError { viewState.value = ViewState.ERROR(it) }  // send message to peer in chat failed
            .autoDisposable(this)
            .subscribe({
                ChatInMemoryCache.addUserMessagesCount(chatId = peerId, count = 1)
                sentMessage.value = it

                // analytics
                with (analyticsManager) {
                    fire(Analytics.ACTION_USER_MESSAGE, "sourceFeed" to sourceFeed.feedName)
                    fireOnce(Analytics.AHA_FIRST_MESSAGE_SENT, "sourceFeed" to sourceFeed.feedName)
                    when (sourceFeed) {
                        LcNavTab.LIKES -> fire(Analytics.ACTION_USER_MESSAGE_FROM_LIKES)
                        LcNavTab.MESSAGES -> fire(Analytics.ACTION_USER_MESSAGE_FROM_MESSAGES)
                    }
                }
            }, DebugLogUtil::e)
    }

    // --------------------------------------------------------------------------------------------
    private fun startPollingChat(profileId: String, delay: Long) {
        Flowable.timer(delay, TimeUnit.MILLISECONDS)
            .flatMap {
                pollChatNewMessagesUseCase.source(params = prepareGetChatParams(profileId))
                    .takeUntil { isStopped }  // stop polling if Chat screen was hidden
            }
            .doOnNext { subscribeOnPush() }
            .autoDisposable(this)
            .subscribe(::handleChatUpdate, DebugLogUtil::e)  // on error - fail silently
    }

    // --------------------------------------------------------------------------------------------
    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    fun onEventPushNewMessage(event: BusEvent.PushNewMessage) {
        incomingPushMessage.onNext(event)
    }

    // --------------------------------------------------------------------------------------------
    private fun prepareGetChatParams(profileId: String): Params =
        Params().put(ScreenHelper.getLargestPossibleImageResolution(context))
                .put("chatId", profileId)

    private fun handleChatUpdate(chat: Chat) {
        ChatInMemoryCache.addPeerMessagesCount(profileId = chat.id, count = countPeerMessages(chat.messages))
        ChatInMemoryCache.addUserMessagesCount(chatId = chat.id, count = countUserMessages(chat.messages))

        val list = mutableListOf<Message>()
            .apply {
                addAll(chat.messages.reversed())
                addAll(currentMessageList)
            }
        currentMessageList = ArrayList(list)  // clone list to avoid further modifications
        messages.value = list.apply { addAll(0, chat.unconsumedSentLocalMessages.reversed()) }
        onlineStatus.value = OnlineStatus.from(chat.lastOnlineStatus, label = chat.lastOnlineText)
        peerName.value = chat.name() ?: AppInMemory.genderString(chat.gender, default = AppRes.SEX_MALE)
    }
}