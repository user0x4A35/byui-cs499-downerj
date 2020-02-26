package io.github.ascenderx.mobilescript.models.scripting

import android.os.Message

interface ScriptEventListener {
    fun onMessage(msg: Message)
}