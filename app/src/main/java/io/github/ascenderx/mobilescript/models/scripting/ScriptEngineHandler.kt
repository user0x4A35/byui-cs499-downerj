package io.github.ascenderx.mobilescript.models.scripting

import android.net.Uri

interface ScriptEngineHandler {
    val commandHistory: List<String>
    val isEngineBusy: Boolean
    val shortcuts: MutableMap<String, Uri>
    val currentFileUri: Uri?
    fun attachScriptEventListener(listener: ScriptEventListener)
    fun postData(data: String): Boolean
    fun restartScriptEngine(fileUri: Uri? = null)
    fun clearCommandHistory()
    fun interrupt()
}