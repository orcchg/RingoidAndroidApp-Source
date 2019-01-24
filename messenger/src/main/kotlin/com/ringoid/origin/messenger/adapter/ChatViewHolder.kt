package com.ringoid.origin.messenger.adapter

import android.view.View
import com.ringoid.base.adapter.BaseViewHolder
import com.ringoid.domain.model.messenger.Message
import kotlinx.android.synthetic.main.rv_item_chat_item.view.*

interface IChatViewHolder

abstract class BaseChatViewHolder(view: View) : BaseViewHolder<Message>(view)

open class ChatViewHolder(view: View) : BaseChatViewHolder(view) {

    override fun bind(model: Message, payloads: List<Any>) {
        itemView.tv_chat_message.text = model.text
    }
}

class MyChatViewHolder(view: View) : ChatViewHolder(view)

class PeerChatViewHolder(view: View) : ChatViewHolder(view)

class HeaderChatViewHolder(view: View) : BaseChatViewHolder(view) {

    override fun bind(model: Message, payloads: List<Any>) {
        // no-op
    }
}