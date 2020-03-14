package io.github.ascenderx.mobilescript.ui.console

import android.content.Context
import android.os.Bundle
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
import io.github.ascenderx.mobilescript.models.scripting.ScriptEngineHandler
import io.github.ascenderx.mobilescript.models.scripting.ScriptEventListener

class ConsoleFragment : Fragment() {
    companion object {
        private const val INPUT_MODE_COMMAND = 0
        private const val INPUT_MODE_PROMPT = 1
    }

    private lateinit var consoleViewModel: ConsoleViewModel
    private lateinit var consoleAdapter: ConsoleListAdapter
    private lateinit var consoleOutputView: ListView
    private lateinit var txtInput: TextView
    private lateinit var btHistory: Button
    private lateinit var btRun: Button
    private lateinit var scriptEngineHandler: ScriptEngineHandler
    private var currentHistoryIndex: Int = -1
    private var inputStatus: Int = INPUT_MODE_COMMAND

    override fun onAttach(context: Context) {
        super.onAttach(context)

        if (context is ScriptEngineHandler) {
            this.scriptEngineHandler = context
            context.attachScriptEventListener(object : ScriptEventListener {
                override fun onScriptEvent(eventType: Int, data: Any?) {
                    val text: String = (data ?: "undefined").toString()
                    when (eventType) {
                        ScriptEngine.EVENT_INITIALIZED -> onInitialized()
                        ScriptEngine.EVENT_PRINT -> onPrint(text)
                        ScriptEngine.EVENT_PRINT_LINE -> onPrintLine(text)
                        ScriptEngine.EVENT_PROMPT -> onPrompt(text)
                        ScriptEngine.EVENT_CLEAR_CONSOLE -> onClear()
                        ScriptEngine.EVENT_EVALUATE_ERROR -> onError(text)
                        ScriptEngine.EVENT_RESULT -> onResult(text)
                        ScriptEngine.EVENT_SCRIPT_RUN -> onScriptRun()
                        ScriptEngine.EVENT_SCRIPT_END -> onScriptEnd()
                        ScriptEngine.EVENT_RESTART -> onRestart()
                        ScriptEngine.EVENT_INTERRUPTED -> onInterrupt()
                        ScriptEngine.EVENT_SOURCE_LOAD_ERROR -> onSourceLoadError(text)
                        ScriptEngine.EVENT_SHORTCUT_CREATED -> onShortcutCreated()
                        ScriptEngine.EVENT_HISTORY_CLEAR -> onHistoryClear()
                    }
                }
            })
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
        consoleOutputView = root.findViewById(R.id.console_output) as ListView
        txtInput = root.findViewById(R.id.txt_input)
        btHistory = root.findViewById(R.id.bt_history)
        btRun = root.findViewById(R.id.bt_run)

        // Register the output list.
        consoleAdapter = ConsoleListAdapter(context as Context)
        consoleOutputView.adapter = consoleAdapter

        // Register the history button.
        disableHistoryButton()
        btHistory.setOnClickListener {
            val history: List<String> = scriptEngineHandler.commandHistory
            val command: String = history[currentHistoryIndex--]
            txtInput.text = command
            // Disable the button once we've reached the bottom of the history stack.
            determineHistoryButtonState()
        }

        // Register the run button.
        disableRunButton()
        btRun.setOnClickListener {
            when (inputStatus) {
                INPUT_MODE_COMMAND -> {
                    val command = "${txtInput.text}"
                    onCommand(command)
                    if (scriptEngineHandler.postData(command)) {
                        currentHistoryIndex = scriptEngineHandler.commandHistory.size - 1
                    }

                    // Immediately clear and disable the input field (until
                    // execution completes).
                    onCommandRun()
                }
                INPUT_MODE_PROMPT -> {
                    val value = "${txtInput.text}"
                    onPrintLine(value)
                    scriptEngineHandler.postData(value)

                    // Immediately clear and disable the input field (until
                    // execution completes).
                    onPromptSend()
                }
            }
        }

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

    private fun determineRunButtonState(text: Editable? = null) {
        val textField: String = (text ?: txtInput.text).toString()

        if (textField.isNotEmpty()) {
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
        if (scriptEngineHandler.commandHistory.isNotEmpty() && currentHistoryIndex >= 0) {
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

    private fun onInitialized() {
        consoleAdapter.addCommandLine(getString(R.string.console_ready))
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
        consoleAdapter.addCommandLine(getString(R.string.restart_notification))
        enableInputField()
        determineRunButtonState()
        determineHistoryButtonState()
    }

    private fun onScriptRun() {
        consoleAdapter.addCommandLine(getString(R.string.restart_notification))
        disableInputField()
        disableRunButton()
        disableHistoryButton()
    }

    private fun onScriptEnd() {
        setInputMode(INPUT_MODE_COMMAND)
        enableInputField()
        determineRunButtonState()
        determineHistoryButtonState()
    }

    private fun onClear() {
        consoleAdapter.clear()
    }

    private fun onInterrupt() {
        consoleAdapter.addCommandLine(getString(R.string.interrupt_notification))
        setInputMode(INPUT_MODE_COMMAND)
        enableInputField()
        determineRunButtonState()
        determineHistoryButtonState()
    }

    private fun onSourceLoadError(error: String) {
        consoleAdapter.addErrorLine(error)
    }

    private fun onShortcutCreated() {
        consoleAdapter.addCommandLine(getString(R.string.shortcut_notification))
    }

    private fun onHistoryClear() {
        currentHistoryIndex = -1
        disableHistoryButton()
        consoleAdapter.addCommandLine(getString(R.string.history_clear_notification))
    }
}
