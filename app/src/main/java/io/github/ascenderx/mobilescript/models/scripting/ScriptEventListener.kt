package io.github.ascenderx.mobilescript.models.scripting

interface ScriptEventListener {
    fun onScriptEvent(eventType: Int, data: Any?)
}