package io.github.ascenderx.mobilescript.ui.console

import android.app.Activity
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
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import io.github.ascenderx.mobilescript.R
import io.github.ascenderx.mobilescript.models.scripting.ScriptEngine
import io.github.ascenderx.mobilescript.models.scripting.ScriptEngineHandler
import io.github.ascenderx.mobilescript.models.scripting.ScriptEventListener
import io.github.ascenderx.mobilescript.ui.dialog.ConfirmationDialog
import io.github.ascenderx.mobilescript.ui.dialog.TextInputDialog
import io.github.ascenderx.mobilescript.ui.menu.MenuEventListener
import io.github.ascenderx.mobilescript.ui.menu.MenuHandler

class ConsoleFragment : Fragment(),
    ScriptEventListener,
    MenuEventListener {
    companion object {
        private const val INPUT_MODE_COMMAND = 0
        private const val INPUT_MODE_PROMPT = 1
        const val EVENT_LISTENER_TAG = "MS.Console.onScript"
    }

    private val viewModel: ConsoleViewModel by activityViewModels()
    private lateinit var activity: Activity
    private lateinit var scriptEngineHandler: ScriptEngineHandler
    private lateinit var menuHandler: MenuHandler
    private lateinit var outputView: ListView
    private lateinit var txtInput: TextView
    private lateinit var btHistory: Button
    private lateinit var btRun: Button
    private var currentHistoryIndex: Int = -1
    private var inputStatus: Int = INPUT_MODE_COMMAND

    override fun onAttach(context: Context) {
        if (context is ScriptEngineHandler) {
            scriptEngineHandler = context
            scriptEngineHandler.attachScriptEventListener(EVENT_LISTENER_TAG, this)
        }
        if (context is Activity) {
            activity = context
        }
        if (context is MenuHandler) {
            menuHandler = context
            menuHandler.showOptionItem(R.id.action_clear_console)
            menuHandler.showOptionItem(R.id.action_reset_engine)
            menuHandler.hideOptionItem(R.id.action_create_shortcut)
            menuHandler.hideOptionItem(R.id.action_clear_history)
            menuHandler.hideOptionItem(R.id.action_stop_engine)
        }
        super.onAttach(context)
    }

    override fun onStop() {
        this.scriptEngineHandler.detachScriptEventListener(EVENT_LISTENER_TAG)
        super.onStop()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Render the fragment.
        val root: View? = inflater.inflate(
            R.layout.fragment_console,
            container, false
        )

        // Get components by ID.
        outputView = root?.findViewById(R.id.console_output) as ListView
        txtInput = root.findViewById(R.id.txt_input) as TextView
        btHistory = root.findViewById(R.id.bt_history) as Button
        btRun = root.findViewById(R.id.bt_run) as Button

        registerUIElements(inflater)

        return root
    }

    private fun registerUIElements(inflater: LayoutInflater) {
        // Register the output list.
        val listAdapter = ConsoleListAdapter(inflater)
        outputView.adapter = listAdapter
        viewModel.liveData.observe(viewLifecycleOwner, Observer {
            listAdapter.data = it
        })

        // Register the history button.
        currentHistoryIndex = scriptEngineHandler.commandHistory.size - 1
        determineHistoryButtonState()
        btHistory.setOnClickListener {
            val history: List<String> = scriptEngineHandler.commandHistory
            val command: String = history[currentHistoryIndex--]
            txtInput.text = command
            // Disable the button once we've reached the bottom of the history stack.
            determineHistoryButtonState()
        }

        // Register the run button.
        determineRunButtonState()
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
        txtInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(text: Editable?) {
                if (text != null) {
                    determineRunButtonState(text)
                }
            }
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    override fun onScriptEvent(eventType: Int, data: Any?) {
        val text: String = (data ?: "undefined").toString()
        when (eventType) {
            ScriptEngine.EVENT_INITIALIZED -> onInitialized()
            ScriptEngine.EVENT_PRINT -> onPrint(text)
            ScriptEngine.EVENT_PRINT_LINE -> onPrintLine(text)
            ScriptEngine.EVENT_PROMPT -> onPrompt(text)
            ScriptEngine.EVENT_CLEAR_CONSOLE -> onClearConsole()
            ScriptEngine.EVENT_EVALUATE_ERROR -> onError(text)
            ScriptEngine.EVENT_RESULT -> onResult(text)
            ScriptEngine.EVENT_SCRIPT_RUN -> onScriptRun()
            ScriptEngine.EVENT_SCRIPT_END -> onScriptEnd()
            ScriptEngine.EVENT_RESTART -> onRestartEngine()
            ScriptEngine.EVENT_INTERRUPTED -> onInterrupt()
            ScriptEngine.EVENT_SOURCE_LOAD_ERROR -> onSourceLoadError(text)
            ScriptEngine.EVENT_SHORTCUT_CREATED -> onCreateShortcut()
            ScriptEngine.EVENT_HISTORY_CLEAR -> onClearHistory()
        }
    }

    override fun onOptionItemEvent(id: Int) {
        when (id) {
            // TODO: Determine menu item states.
            R.id.action_stop_engine -> onMenuItemStopEngine()
            R.id.action_reset_engine -> onMenuItemResetEngine()
            R.id.action_clear_console -> onMenuItemClearConsole()
            R.id.action_clear_history -> onMenuItemClearHistory()
            R.id.action_create_shortcut -> onMenuItemCreateShortcut()
        }
    }

    private fun onMenuItemClearConsole() {
        val dialog = ConfirmationDialog()
        dialog.message = getString(R.string.dialog_message_clear_console)
        dialog.okListener = object : ConfirmationDialog.OnOKListener {
            override fun onOK() = onClearConsole()
        }
        dialog.show(activity, layoutInflater)
    }

    private fun onMenuItemResetEngine() {
        val dialog = ConfirmationDialog()
        dialog.message = getString(R.string.dialog_message_reset_engine)
        dialog.okListener = object : ConfirmationDialog.OnOKListener {
            override fun onOK() {
                onRestartEngine()
                scriptEngineHandler.restartScriptEngine()
                menuHandler.hideOptionItem(R.id.action_reset_engine)
                menuHandler.hideOptionItem(R.id.action_create_shortcut)
            }
        }
        dialog.show(activity, layoutInflater)
    }

    private fun onMenuItemClearHistory() {
        val dialog = ConfirmationDialog()
        dialog.message = getString(R.string.dialog_message_clear_history)
        dialog.okListener = object : ConfirmationDialog.OnOKListener {
            override fun onOK() {
                scriptEngineHandler.clearCommandHistory()
                menuHandler.hideOptionItem(R.id.action_clear_history)
            }
        }
        dialog.show(activity, layoutInflater)
    }

    private fun onMenuItemCreateShortcut() {
        val dialog = TextInputDialog()
        dialog.message = getString(R.string.dialog_message_create_shortcut)
        dialog.hint = getString(R.string.shortcut_name_hint)
        dialog.okListener = object : TextInputDialog.OnOKListener {
            override fun onOK(returnValue: String?) {
                // TODO: Use returnValue.
                menuHandler.navigateTo(R.id.nav_shortcut)
                // TODO: Hide the menu option.
            }
        }
        dialog.show(activity, layoutInflater)
    }

    private fun onMenuItemStopEngine() {
        val dialog = ConfirmationDialog()
        dialog.message = getString(R.string.dialog_message_stop_engine)
        dialog.okListener = object : ConfirmationDialog.OnOKListener {
            override fun onOK() {
                scriptEngineHandler.interrupt()
                menuHandler.hideOptionItem(R.id.action_stop_engine)
            }
        }
        dialog.show(activity, layoutInflater)
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

        if (textField.isNotEmpty() && !scriptEngineHandler.isEngineBusy) {
            enableRunButton()
        } else {
            disableRunButton()
        }
    }

    private fun determineHistoryButtonState() {
        if (
            scriptEngineHandler.commandHistory.isNotEmpty() &&
            currentHistoryIndex >= 0 &&
            !scriptEngineHandler.isEngineBusy
        ) {
            enableHistoryButton()
        } else {
            disableHistoryButton()
        }
    }

    private fun disableHistoryButton() {
        btHistory.isEnabled = false
    }

    private fun enableHistoryButton() {
        btHistory.isEnabled = true
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
        viewModel.addCommandLine(getString(R.string.console_ready))
        enableInputField()
        determineHistoryButtonState()
        determineRunButtonState()
    }

    private fun onCommand(command: String) {
        viewModel.addCommandLine("-> $command")
    }

    private fun onCommandRun() {
        clearInputField()
        disableInputField()
        disableHistoryButton()
        disableRunButton()
    }

    private fun onPrint(text: String) {
        viewModel.addOutput(text)
    }

    private fun onPrintLine(text: String) {
        viewModel.addOutputAndEndLine(text)
    }

    private fun onPrompt(prompt: String) {
        setInputMode(INPUT_MODE_PROMPT)
        viewModel.addOutput("?> $prompt")
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
        viewModel.addErrorLine(error)
        enableInputField()
        enableHistoryButton()
    }

    private fun onResult(result: String) {
        setInputMode(INPUT_MODE_COMMAND)
        viewModel.addResultLine("<= $result")
        enableInputField()
        enableHistoryButton()
    }

    private fun onRestartEngine() {
        setInputMode(INPUT_MODE_COMMAND)
        viewModel.addCommandLine(getString(R.string.restart_notification))
        enableInputField()
        determineRunButtonState()
        determineHistoryButtonState()
    }

    private fun onScriptRun() {
        viewModel.addCommandLine(getString(R.string.restart_notification))
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

    private fun onClearConsole() {
        viewModel.clear()
    }

    private fun onInterrupt() {
        viewModel.addCommandLine(getString(R.string.interrupt_notification))
        setInputMode(INPUT_MODE_COMMAND)
        enableInputField()
        determineRunButtonState()
        determineHistoryButtonState()
    }

    private fun onSourceLoadError(error: String) {
        viewModel.addErrorLine(error)
    }

    private fun onCreateShortcut() {
        viewModel.addCommandLine(getString(R.string.shortcut_notification))
    }

    private fun onClearHistory() {
        viewModel.addCommandLine(getString(R.string.history_clear_notification))
        currentHistoryIndex = -1
        disableHistoryButton()
    }
}
