package io.github.ascenderx.mobilescript.ui.shortcuts

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import io.github.ascenderx.mobilescript.R

class ShortcutListAdapter(private val inflater: LayoutInflater, private val icon: Drawable?) :
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
        var view: ImageView? = convertView as ImageView?

        if (view == null) {
            view = inflater.inflate(R.layout.shortcut_cell, null) as ImageView
        }

        val item: ShortcutCell? = getItem(position)
        if (item != null) {
            view.setImageDrawable(icon)
        }

        return view
    }
}