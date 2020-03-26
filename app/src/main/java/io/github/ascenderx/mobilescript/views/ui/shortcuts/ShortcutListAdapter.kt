package io.github.ascenderx.mobilescript.views.ui.shortcuts

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import io.github.ascenderx.mobilescript.R

class ShortcutListAdapter(private val inflater: LayoutInflater) :
    BaseAdapter() {
    private var keys: Array<String>? = null
    var data: MutableMap<String, Uri>? = null
        set(value) {
            field = value
            keys = value?.keys?.toTypedArray()
            notifyDataSetChanged()
        }

    override fun getCount(): Int {
        return data?.size ?: 0
    }

    private fun isPositionValid(position: Int): Boolean {
        return position >= 0 && position < data!!.size
    }

    private fun getKey(position: Int): String? {
        return if (!isPositionValid(position)) {
            null
        } else {
            keys!![position]
        }
    }

    override fun getItem(position: Int): Uri? {
        return if (!isPositionValid(position)) {
            null
        } else {
            data!![keys!![position]]
        }
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View = convertView ?:
            inflater.inflate(R.layout.shortcut_cell, null) as View

        val key: String? = getKey(position)
        if (key != null) {
            val uri: Uri? = getItem(position)
            val txtShortcutCaption: TextView = view.findViewById(R.id.txt_shortcut_caption)
            txtShortcutCaption.text = key
            view.setOnClickListener {

            }
        }

        return view
    }
}