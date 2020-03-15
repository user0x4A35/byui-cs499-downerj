package io.github.ascenderx.mobilescript.models.scripting

import io.github.ascenderx.mobilescript.ui.console.ConsoleListAdapter

interface ScriptEngineHandler {
    val commandHistory: List<String>
    var consoleListAdapter: ConsoleListAdapter?
    val isEngineBusy: Boolean
    fun attachScriptEventListener(id: String, listener: ScriptEventListener)
    fun postData(data: String): Boolean
}