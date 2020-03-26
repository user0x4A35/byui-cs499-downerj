package io.github.ascenderx.mobilescript.views.ui.menu

interface MenuEventListener {
    fun onOptionItemEvent(id: Int)
    fun getVisibleOptionItems(): List<Int>?
}