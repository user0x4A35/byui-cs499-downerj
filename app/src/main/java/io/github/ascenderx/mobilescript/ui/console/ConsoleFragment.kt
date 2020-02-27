package io.github.ascenderx.mobilescript.ui.console

import android.content.Context
import android.os.Bundle
import android.os.Message
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider

import io.github.ascenderx.mobilescript.R
import io.github.ascenderx.mobilescript.models.scripting.ScriptEngine
import io.github.ascenderx.mobilescript.models.scripting.ScriptEventEmitter
import io.github.ascenderx.mobilescript.models.scripting.ScriptEventListener
import io.github.ascenderx.mobilescript.models.scripting.ScriptMessageStatus

class ConsoleFragment : Fragment() {
    private lateinit var consoleViewModel: ConsoleViewModel
    private lateinit var consoleAdapter: ConsoleListAdapter
    private var scriptEngine: ScriptEngine? = null
    private var currentHistoryIndex: Int = -1
    private lateinit var consoleOutputView: ListView
    private lateinit var txtInput: TextView
    private lateinit var btHistory: Button
    private lateinit var btRun: Button

    override fun onAttach(context: Context) {
        super.onAttach(context)

        if (context is ScriptEventEmitter) {
            context.attachScriptEventListener(object : ScriptEventListener {
                override fun onMessage(msg: Message) {
                    val data: String = (msg.obj ?: "undefined").toString()
                    when (msg.what) {
                        ScriptMessageStatus.PRINT.value -> printOutput(data)
                        ScriptMessageStatus.PRINT_LINE.value -> printOutputAndEndLine(data)
                        ScriptMessageStatus.CLEAR.value -> clearOutput()
                        ScriptMessageStatus.ERROR.value -> {
                            printError(data)
                            enableInputField()
                            btHistory.isEnabled = true
                        }
                        ScriptMessageStatus.RESULT.value -> {
                            printResult(data)
                            enableInputField()
                            btHistory.isEnabled = true
                        }

                    }
                }
            })
            scriptEngine = context.engine
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Provided.
        consoleViewModel =
            ViewModelProvider(this).get(ConsoleViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_console, container, false)

        // Get components by ID.
        consoleOutputView = root.findViewById(R.id.consoleOutput) as ListView
        txtInput = root.findViewById(R.id.txtInput)
        btHistory = root.findViewById(R.id.btHistory)
        btRun = root.findViewById(R.id.btRun)

        // Register the output list.
        consoleAdapter = ConsoleListAdapter(context as Context)
        consoleOutputView.adapter = consoleAdapter

        // Register the history button.
        btHistory.isEnabled = false
        btHistory.setOnClickListener(object : View.OnClickListener {
            override fun onClick(view: View) {
                if (scriptEngine == null) {
                    return
                }
                val engine: ScriptEngine = scriptEngine as ScriptEngine
                val history: List<String> = engine.commandHistory
                val command: String = history[currentHistoryIndex--]
                txtInput.text = command
                // Disable the button once we've reached the bottom of the history stack.
                btHistory.isEnabled = currentHistoryIndex >= 0
            }
        })

        // Register the run button.
        btRun.isEnabled = false
        btRun.setOnClickListener(object : View.OnClickListener {
            override fun onClick(view: View) {
                if (scriptEngine == null) {
                    return
                }
                val engine: ScriptEngine = scriptEngine as ScriptEngine
                val command = "${txtInput.text}"
                printCommand(command)
                val historyIndex: Int = engine.evaluate(command)
                currentHistoryIndex = historyIndex

                // Immediately clear and disable the input field.
                clearInputField()
                disableInputField()
                // Disable the history button (until execution completes).
                btHistory.isEnabled = false
            }
        })

        // Register the input field.
        txtInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(text: Editable?) {
                if (text != null) {
                    btRun.isEnabled = text.isNotEmpty()
                }
            }
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        return root
    }

    private fun enableInputField() {
        txtInput.isEnabled = true
        txtInput.hint = getText(R.string.input_hint)
    }

    private fun disableInputField() {
        txtInput.isEnabled = false
        txtInput.hint = getText(R.string.running_hint)
    }

    private fun clearInputField() {
        txtInput.text = ""
    }

    private fun printCommand(command: String) {
        consoleAdapter.addCommandLine("-> $command")
    }

    private fun printOutput(text: String) {
        consoleAdapter.addOutput(text)
    }

    private fun printOutputAndEndLine(text: String) {
        consoleAdapter.addOutputAndEndLine(text)
    }

    private fun printError(error: String) {
        consoleAdapter.addErrorLine(error)
    }

    private fun printResult(result: String) {
        consoleAdapter.addResultLine("<= $result")
    }

    private fun clearOutput() {
        consoleAdapter.clear()
    }
}
