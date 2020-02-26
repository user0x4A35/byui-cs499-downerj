package io.github.ascenderx.mobilescript.ui.console

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

import io.github.ascenderx.mobilescript.R
import io.github.ascenderx.mobilescript.models.ConsoleOutputRow
import io.github.ascenderx.mobilescript.models.ConsoleOutputType

// See: http://android.amberfog.com/?p=296.

class ConsoleListAdapter(context: Context) : BaseAdapter() {
    private val inflater: LayoutInflater =
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    private val data: MutableList<ConsoleOutputRow> = mutableListOf()

    fun addItem(type: ConsoleOutputType, text: String) {
        data.add(ConsoleOutputRow(type, text))
        notifyDataSetChanged()
    }

    fun clear() {
        data.clear()
        notifyDataSetChanged()
    }

    override fun getCount(): Int {
        return data.size
    }

    override fun getItem(position: Int): ConsoleOutputRow? {
        return data[position]
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
        val color: Int = item?.type?.color as Int

        view.setTextColor(color)
        view.text = item.text

        return view
    }
}