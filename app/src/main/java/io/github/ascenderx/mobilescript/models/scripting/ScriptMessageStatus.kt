package io.github.ascenderx.mobilescript.models.scripting

enum class ScriptMessageStatus(val value: Int) {
    ERROR(-1),
    RESULT(0),
    PRINT(1),
    PRINT_LINE(2),
    CLEAR(3)
}