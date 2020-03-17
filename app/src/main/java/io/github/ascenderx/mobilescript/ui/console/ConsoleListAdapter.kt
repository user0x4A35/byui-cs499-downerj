package io.github.ascenderx.mobilescript.ui.console

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.lifecycle.MutableLiveData
import io.github.ascenderx.mobilescript.R

// See: http://android.amberfog.com/?p=296.

class ConsoleListAdapter(
    private val inflater: LayoutInflater
) : BaseAdapter() {
    var data: MutableList<ConsoleOutputRow>? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getCount(): Int {
        return data?.size ?: 0
    }

    override fun getItem(position: Int): ConsoleOutputRow? {
        return data?.get(position)
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        var view: TextView? = convertView as TextView?

        if (view == null) {
            view = inflater.inflate(R.layout.output_row, null) as TextView
        }

        val item: ConsoleOutputRow? = getItem(position)
        if (item != null) {
            val color: Int = item.type.color
            view.setTextColor(color)
            view.text = item.getText()
        }

        return view
    }
}