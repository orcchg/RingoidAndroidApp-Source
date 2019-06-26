package com.ringoid.domain.model

import com.ringoid.domain.BuildConfig
import com.ringoid.domain.model.messenger.IMessage

fun Collection<IMessage>.print(): String =
        "[$size]${if (BuildConfig.IS_STAGING) ": ${joinToString("\n\t\t", "{\n\t\t", "}", transform = { it.text })}" else ""}"
