package io.github.ascenderx.mobilescript.ui.shortcuts

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ShortcutViewModel : ViewModel() {
    val liveData = MutableLiveData<MutableList<ShortcutCell>>()
    private val data: MutableList<ShortcutCell> = mutableListOf()

    private fun update() {
        liveData.value = data
    }

    fun addShortcut(title: String, uri: Uri?) {
        data.add(ShortcutCell(title, uri))
        update()
    }
}
