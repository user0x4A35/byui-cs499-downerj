package io.github.ascenderx.mobilescript.views.ui.menu

interface MenuHandler {
    fun attachMenuEventListener(listener: MenuEventListener)
    fun showOptionItem(id: Int)
    fun hideOptionItem(id: Int)
    fun navigateTo(destination: Int)
}