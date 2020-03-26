package io.github.ascenderx.mobilescript.views.ui.console

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ConsoleViewModel : ViewModel() {
    val liveData = MutableLiveData<MutableList<ConsoleOutputRow>>()
    val isEmpty: Boolean
        get() = data.isEmpty()
    val isNotEmpty: Boolean
        get() = data.isNotEmpty()
    private val data: MutableList<ConsoleOutputRow> = mutableListOf()

    private fun update() {
        liveData.value = data
    }

    fun addCommandLine(text: String) {
        val row: ConsoleOutputRow = newLine(ConsoleOutputType.COMMAND)
        row.appendAndComplete(text)
        update()
    }

    fun addResultLine(text: String) {
        val row: ConsoleOutputRow = newLine(ConsoleOutputType.RESULT)
        row.appendAndComplete(text)
        update()
    }

    fun addErrorLine(text: String) {
        val row: ConsoleOutputRow = newLine(ConsoleOutputType.ERROR)
        row.appendAndComplete(text)
        update()
    }

    fun addOutput(text: String) {
        val row: ConsoleOutputRow = getMostRecentOutputLine()
        row.append(text)
        update()
    }

    fun addOutputAndEndLine(text: String) {
        val row: ConsoleOutputRow = getMostRecentOutputLine()
        row.appendAndComplete(text)
        update()
    }

    fun clear() {
        this.data.clear()
        update()
    }

    private fun getMostRecentOutputLine(): ConsoleOutputRow {
        var row: ConsoleOutputRow
        // Attempt to get the last row.
        if (this.data.isNotEmpty()) {
            row = this.data[this.data.size - 1]
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
        if (this.data.size > 0) {
            this.data[this.data.size - 1].complete()
        }

        val row = ConsoleOutputRow(type)
        this.data.add(row)
        return row
    }
}