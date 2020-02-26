package io.github.ascenderx.mobilescript.models.scripting

interface ScriptEventEmitter {
    val engine: ScriptEngine
    fun attachScriptEventListener(listener: ScriptEventListener)
}