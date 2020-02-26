package io.github.ascenderx.mobilescript.ui.console

import android.graphics.Color

enum class ConsoleOutputType(val color: Int) {
    OUTPUT(Color.BLUE),
    ERROR(Color.RED),
    COMMAND(Color.GRAY),
    RESULT(Color.BLACK)
}