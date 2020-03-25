package io.github.ascenderx.mobilescript.ui.menu

interface MenuEventListener {
    fun onOptionItemEvent(id: Int)
    fun getVisibleOptionItems(): List<Int>?
}