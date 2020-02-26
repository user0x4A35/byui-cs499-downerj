package io.github.ascenderx.mobilescript.ui.console

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

import io.github.ascenderx.mobilescript.R

// See: http://android.amberfog.com/?p=296.

class ConsoleListAdapter(context: Context) : BaseAdapter() {
    private val inflater: LayoutInflater =
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    private val data: MutableList<ConsoleOutputRow> = mutableListOf()

    fun addCommandLine(text: String) {
        val row: ConsoleOutputRow = newLine(ConsoleOutputType.COMMAND)
        row.appendAndComplete(text)
        notifyDataSetChanged()
    }

    fun addResultLine(text: String) {
        val row: ConsoleOutputRow = newLine(ConsoleOutputType.RESULT)
        row.appendAndComplete(text)
        notifyDataSetChanged()
    }

    fun addErrorLine(text: String) {
        val row: ConsoleOutputRow = newLine(ConsoleOutputType.ERROR)
        row.appendAndComplete(text)
        notifyDataSetChanged()
    }

    fun addOutput(text: String) {
        val row: ConsoleOutputRow = getMostRecentOutputLine()
        row.append(text)
        notifyDataSetChanged()
    }

    fun addOutputAndEndLine(text: String) {
        val row: ConsoleOutputRow = getMostRecentOutputLine()
        row.appendAndComplete(text)
        notifyDataSetChanged()
    }

    private fun getMostRecentOutputLine(): ConsoleOutputRow {
        var row: ConsoleOutputRow
        // Attempt to get the last row.
        if (data.size > 0) {
            row = data[data.size - 1]
            // If the row is unmodifiable, then make a new row.
            if (row.isComplete() || row.type != ConsoleOutputType.OUTPUT) {
                row = newLine(ConsoleOutputType.OUTPUT)
            }
        // If empty, then make a new row.
        } else {
            row = newLine(ConsoleOutputType.OUTPUT)
        }

        return row
    }

    private fun newLine(type: ConsoleOutputType): ConsoleOutputRow {
        // Make the most recent item unmodifiable.
        if (data.size > 0) {
            data[data.size - 1].complete()
        }

        val row = ConsoleOutputRow(type)
        data.add(row)
        return row
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
        view.text = item.getText()

        return view
    }
}