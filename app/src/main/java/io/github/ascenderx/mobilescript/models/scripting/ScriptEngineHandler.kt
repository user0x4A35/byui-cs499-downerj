package io.github.ascenderx.mobilescript.models.scripting

import android.net.Uri

interface ScriptEngineHandler {
    val commandHistory: List<String>
    val isEngineBusy: Boolean
    val shortcuts: MutableMap<String, Uri>
    fun attachScriptEventListener(listener: ScriptEventListener)
    fun detachScriptEventListener()
    fun postData(data: String): Boolean
    fun restartScriptEngine()
    fun clearCommandHistory()
    fun interrupt()
}