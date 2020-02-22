package io.github.ascenderx.mobilescript.models

class ConsoleOutputRow(val type: ConsoleOutputType, val text: String) {
    enum class ConsoleOutputType {
        VALID, INVALID
    }
}