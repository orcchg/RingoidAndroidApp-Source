package com.ringoid.domain.model.actions

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.ringoid.domain.action_storage.DelayFromLast
import com.ringoid.domain.action_storage.TriggerStrategy
import com.ringoid.domain.action_storage.VIEW_DELAY_ON_TRIGGER

class ViewChatActionObject(
    @Expose @SerializedName(COLUMN_VIEW_CHAT_TIME_MILLIS) val timeInMillis: Long = 1,
    actionTime: Long = System.currentTimeMillis(),
    sourceFeed: String, targetImageId: String, targetUserId: String,
    triggerStrategies: List<TriggerStrategy> = listOf(DelayFromLast(VIEW_DELAY_ON_TRIGGER)))
    : ActionObject(actionTime = actionTime, actionType = ACTION_TYPE_VIEW_CHAT, sourceFeed = sourceFeed,
                   targetImageId = targetImageId, targetUserId = targetUserId,
                   triggerStrategies = triggerStrategies) {

    companion object {
        const val COLUMN_VIEW_CHAT_TIME_MILLIS = "viewTimeMillis"
    }

    override fun propertyString(): String? = "timeInMillis=$timeInMillis"

    override fun toActionString(): String = "VIEW_CHAT($timeInMillis,${targetIdsStr()})"
}