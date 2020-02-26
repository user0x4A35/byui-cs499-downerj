package io.github.ascenderx.mobilescript.ui.console

import android.graphics.Color

class ConsoleOutputRow(val type: ConsoleOutputType, val text: String)

enum class ConsoleOutputType(val color: Int) {
    VALID(Color.BLUE),
    INVALID(Color.RED),
    COMMAND(Color.GRAY),
    RESULT(Color.BLACK)
}