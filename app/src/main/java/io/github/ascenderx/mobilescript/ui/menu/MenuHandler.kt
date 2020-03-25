package io.github.ascenderx.mobilescript.ui.menu

interface MenuHandler {
    fun attachMenuEventListener(listener: MenuEventListener)
    fun detachMenuEventListener()
    fun showOptionItem(id: Int)
    fun hideOptionItem(id: Int)
    fun navigateTo(destination: Int)
}