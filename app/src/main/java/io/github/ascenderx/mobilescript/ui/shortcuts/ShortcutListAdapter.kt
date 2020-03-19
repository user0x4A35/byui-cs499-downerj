package io.github.ascenderx.mobilescript.ui.shortcuts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import io.github.ascenderx.mobilescript.R

class ShortcutListAdapter(private val inflater: LayoutInflater) :
    BaseAdapter() {
    var data: MutableList<ShortcutCell>? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getCount(): Int {
        return data?.size ?: 0
    }

    override fun getItem(position: Int): ShortcutCell? {
        return data?.get(position)
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View = convertView ?:
            inflater.inflate(R.layout.shortcut_cell, null) as View

        val item: ShortcutCell? = getItem(position)
        if (item != null) {
            val txtShortcutCaption: TextView = view.findViewById(R.id.txt_shortcut_caption)
            txtShortcutCaption.text = getItem(position)?.title
        }

        return view
    }
}