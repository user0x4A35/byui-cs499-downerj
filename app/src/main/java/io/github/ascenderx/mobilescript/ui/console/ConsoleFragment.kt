package io.github.ascenderx.mobilescript.ui.console

import android.content.Context
import android.os.Bundle
import android.os.Message
import android.renderscript.Script
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

class ConsoleFragment : Fragment() {
    companion object {
        private const val INPUT_MODE_COMMAND: Int = 0
        private const val INPUT_MODE_PROMPT: Int = 1
    }

    private lateinit var consoleViewModel: ConsoleViewModel
    private lateinit var consoleAdapter: ConsoleListAdapter
    private lateinit var consoleOutputView: ListView
    private lateinit var txtInput: TextView
    private lateinit var btHistory: Button
    private lateinit var btRun: Button
    private var scriptEngine: ScriptEngine? = null
    private var currentHistoryIndex: Int = -1
    private var inputStatus: Int = INPUT_MODE_COMMAND

    override fun onAttach(context: Context) {
        super.onAttach(context)

        if (context is ScriptEventEmitter) {
            context.attachScriptEventListener(object : ScriptEventListener {
                override fun onMessage(msg: Message) {
                    val data: String = (msg.obj ?: "undefined").toString()
                    when (msg.what) {
                        ScriptEngine.STATUS_PRINT -> onPrint(data)
                        ScriptEngine.STATUS_PRINT_LINE -> onPrintLine(data)
                        ScriptEngine.STATUS_PROMPT -> onPrompt(data)
                        ScriptEngine.STATUS_CLEAR -> onClear()
                        ScriptEngine.STATUS_ERROR -> onError(data)
                        ScriptEngine.STATUS_RESULT -> onResult(data)
                        ScriptEngine.STATUS_SCRIPT_RUN -> onScriptRun()
                        ScriptEngine.STATUS_SCRIPT_END -> onScriptEnd()
                        ScriptEngine.STATUS_RESTART -> onRestart()
                        ScriptEngine.STATUS_INTERRUPT -> onInterrupt()
                        ScriptEngine.STATUS_SOURCE_LOAD_ERROR -> onSourceLoadError(data)
                        ScriptEngine.STATUS_SHORTCUT_CREATED -> onShortcutCreated(data)
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
        disableHistoryButton()
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
                determineHistoryButtonState()
            }
        })

        // Register the run button.
        disableRunButton()
        btRun.setOnClickListener(object : View.OnClickListener {
            override fun onClick(view: View) {
                if (scriptEngine == null) {
                    return
                }
                val engine: ScriptEngine = scriptEngine as ScriptEngine
                when (inputStatus) {
                    INPUT_MODE_COMMAND -> {
                        val command = "${txtInput.text}"
                        onCommand(command)
                        val historyIndex: Int = engine.evaluate(command)
                        currentHistoryIndex = historyIndex

                        // Immediately clear and disable the input field (until
                        // execution completes).
                        onCommandRun()
                    }
                    INPUT_MODE_PROMPT -> {
                        val value = "${txtInput.text}"
                        onPrintLine(value)
                        engine.returnPrompt(value)

                        // Immediately clear and disable the input field (until
                        // execution completes).
                        onPromptSend()
                    }
                }
            }
        })

        // Register the input field.
        enableInputField()
        txtInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(text: Editable?) {
                if (text != null) {
                    determineRunButtonState(text)
                }
            }
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        return root
    }

    private fun enableInputField() {
        txtInput.isEnabled = true
    }

    private fun disableInputField() {
        txtInput.isEnabled = false
    }

    private fun clearInputField() {
        txtInput.text = ""
    }

    private fun disableRunButton() {
        btRun.isEnabled = false
    }

    private fun enableRunButton() {
        btRun.isEnabled = true
    }

    private fun determineRunButtonState(text: Editable?) {
        if (text?.isNotEmpty() == true) {
            enableRunButton()
        } else {
            disableRunButton()
        }
    }

    private fun disableHistoryButton() {
        btHistory.isEnabled = false
    }

    private fun enableHistoryButton() {
        btHistory.isEnabled = true
    }

    private fun determineHistoryButtonState() {
        if (currentHistoryIndex >= 0) {
            enableHistoryButton()
        } else {
            disableHistoryButton()
        }
    }

    private fun setInputMode(mode: Int) {
        inputStatus = mode
        when (inputStatus) {
            INPUT_MODE_COMMAND -> {
                txtInput.hint = getText(R.string.input_hint)
                btRun.text = getString(R.string.run_button)
            }
            INPUT_MODE_PROMPT -> {
                txtInput.hint = getString(R.string.prompt_hint)
                btRun.text = getString(R.string.prompt_return_button)
            }
        }
    }

    private fun onCommand(command: String) {
        consoleAdapter.addCommandLine("-> $command")
    }

    private fun onCommandRun() {
        clearInputField()
        disableInputField()
        disableHistoryButton()
        disableRunButton()
    }

    private fun onPrint(text: String) {
        consoleAdapter.addOutput(text)
    }

    private fun onPrintLine(text: String) {
        consoleAdapter.addOutputAndEndLine(text)
    }

    private fun onPrompt(prompt: String) {
        setInputMode(INPUT_MODE_PROMPT)
        consoleAdapter.addOutput("?> $prompt")
        enableInputField()
        enableRunButton()
    }

    private fun onPromptSend() {
        clearInputField()
        disableInputField()
        disableHistoryButton()
        disableRunButton()
    }

    private fun onError(error: String) {
        setInputMode(INPUT_MODE_COMMAND)
        consoleAdapter.addErrorLine(error)
        enableInputField()
        enableHistoryButton()
    }

    private fun onResult(result: String) {
        setInputMode(INPUT_MODE_COMMAND)
        consoleAdapter.addResultLine("<= $result")
        enableInputField()
        enableHistoryButton()
    }

    private fun onRestart() {
        setInputMode(INPUT_MODE_COMMAND)
        consoleAdapter.addErrorLine(getString(R.string.restart_notification))
        enableInputField()
        disableRunButton()
        disableHistoryButton()
    }

    private fun onScriptRun() {
        consoleAdapter.addErrorLine(getString(R.string.restart_notification))
        disableInputField()
        disableRunButton()
        disableHistoryButton()
    }

    private fun onScriptEnd() {
        setInputMode(INPUT_MODE_COMMAND)
        enableInputField()
        disableRunButton()
        disableHistoryButton()
    }

    private fun onClear() {
        consoleAdapter.clear()
    }

    private fun onInterrupt() {
        consoleAdapter.addErrorLine(getString(R.string.interrupt_notification))
        setInputMode(INPUT_MODE_COMMAND)
        enableInputField()
    }

    private fun onSourceLoadError(error: String) {
        consoleAdapter.addErrorLine(error)
    }

    private fun onShortcutCreated(uriPath: String) {
        consoleAdapter.addErrorLine("Shortcut created for $uriPath")
    }
}
