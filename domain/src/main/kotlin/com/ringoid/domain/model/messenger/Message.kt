package com.ringoid.domain.model.messenger

import android.os.Parcel
import android.os.Parcelable
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.ringoid.domain.DomainUtil
import com.ringoid.domain.model.IEssence
import com.ringoid.domain.model.IListModel
import com.ringoid.utility.randomString

data class Message(
    @Expose @SerializedName(COLUMN_ID) val id: String,
    @Expose @SerializedName(COLUMN_CHAT_ID) val chatId: String,
    @Expose @SerializedName(COLUMN_PEER_ID) val peerId: String,
    @Expose @SerializedName(COLUMN_TEXT) val text: String)
    : IEssence, IListModel, Parcelable {

    private constructor(source: Parcel): this(id = source.readString() ?: DomainUtil.BAD_ID,
        chatId = source.readString() ?: DomainUtil.BAD_ID, peerId = source.readString() ?: DomainUtil.BAD_ID,
        text = source.readString() ?: "")

    override fun getModelId(): Long = id.hashCode().toLong()

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.apply {
            writeString(id)
            writeString(chatId)
            writeString(peerId)
            writeString(text)
        }
    }

    companion object {
        const val COLUMN_ID = "id"
        const val COLUMN_CHAT_ID = "chatId"
        const val COLUMN_PEER_ID = "peerId"
        const val COLUMN_TEXT = "text"

        @JvmField
        val CREATOR = object : Parcelable.Creator<Message> {
            override fun createFromParcel(source: Parcel): Message = Message(source)
            override fun newArray(size: Int): Array<Message?>  = arrayOfNulls(size)
        }
    }
}

val EmptyMessage = Message(id = randomString(), chatId = DomainUtil.BAD_ID, peerId = DomainUtil.BAD_ID, text = "")
