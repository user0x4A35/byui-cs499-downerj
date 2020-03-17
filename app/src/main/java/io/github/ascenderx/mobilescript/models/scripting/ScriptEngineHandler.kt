package io.github.ascenderx.mobilescript.models.scripting

interface ScriptEngineHandler {
    val commandHistory: List<String>
    val isEngineBusy: Boolean
    fun attachScriptEventListener(id: String, listener: ScriptEventListener)
    fun detachScriptEventListener(id: String)
    fun postData(data: String): Boolean
}