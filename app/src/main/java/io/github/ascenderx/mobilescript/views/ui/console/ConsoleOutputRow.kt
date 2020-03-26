package io.github.ascenderx.mobilescript.views.ui.console

class ConsoleOutputRow(val type: ConsoleOutputType) {
    private var buffer: StringBuffer? = StringBuffer()
    private var text: String? = null

    fun append(text: String) {
        buffer?.append(text)
    }

    fun appendAndComplete(text: String) {
        append(text)
        complete()
    }

    fun getText(): String {
        return when {
            buffer != null -> buffer.toString()
            text != null -> text.toString()
            else -> ""
        }
    }

    fun complete() {
        if (buffer == null) {
            return
        }
        text = buffer.toString()
        buffer = null
    }

    fun isComplete(): Boolean {
        return buffer == null
    }
}