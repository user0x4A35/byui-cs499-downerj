package io.github.ascenderx.mobilescript.ui.menu

interface MenuHandler {
    fun attachMenuEventListener()
    fun showOptionItem(id: Int)
    fun hideOptionItem(id: Int)
    fun navigateTo(destination: Int)
}