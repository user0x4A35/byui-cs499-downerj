package io.github.ascenderx.mobilescript.models.scripting

interface ScriptEngineHandler {
    val commandHistory: List<String>
    fun attachScriptEventListener(listener: ScriptEventListener)
    fun postData(data: String): Boolean
}