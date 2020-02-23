package io.github.ascenderx.mobilescript.controllers

class ConsoleOutputRow(val type: ConsoleOutputType, val text: String) {
    enum class ConsoleOutputType {
        VALID, INVALID
    }
}