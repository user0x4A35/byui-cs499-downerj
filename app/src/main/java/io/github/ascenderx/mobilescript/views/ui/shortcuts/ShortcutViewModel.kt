package io.github.ascenderx.mobilescript.views.ui.shortcuts

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ShortcutViewModel : ViewModel() {
    val liveData = MutableLiveData<MutableMap<String, Uri>>()
    private var data: MutableMap<String, Uri>? = null

    private fun update() {
        liveData.value = data
    }

    fun initializeData(data: MutableMap<String, Uri>? = null) {
        if (this.data != null) {
            return
        }

        this.data = data ?: mutableMapOf()
    }

    fun addShortcut(title: String, uri: Uri) {
        if (data == null) {
            initializeData()
        } else if (title in data!!) {
            return
        }
        data!![title] = uri
        update()
    }

    fun removeShortcut(title: String) {
        data?.remove(title)
    }
}
