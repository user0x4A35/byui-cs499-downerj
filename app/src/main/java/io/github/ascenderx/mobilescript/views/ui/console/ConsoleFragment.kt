package io.github.ascenderx.mobilescript.views.ui.console

import android.app.Activity
import android.content.Context
import android.net.Uri
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
import io.github.ascenderx.mobilescript.views.ui.dialog.ConfirmationDialog
import io.github.ascenderx.mobilescript.views.ui.dialog.TextInputDialog
import io.github.ascenderx.mobilescript.views.ui.menu.MenuEventListener
import io.github.ascenderx.mobilescript.views.ui.menu.MenuHandler
import io.github.ascenderx.mobilescript.views.ui.shortcuts.ShortcutViewModel

class ConsoleFragment : Fragment(),
    ScriptEventListener,
    MenuEventListener {
    companion object {
        private const val INPUT_MODE_COMMAND = 0
        private const val INPUT_MODE_PROMPT = 1
    }

    private val viewModel: ConsoleViewModel by activityViewModels()
    private val shortcutViewModel: ShortcutViewModel by activityViewModels()
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
            scriptEngineHandler.attachScriptEventListener(this)
        }
        if (context is Activity) {
            activity = context
        }
        if (context is MenuHandler) {
            menuHandler = context
            menuHandler.attachMenuEventListener(this)
        }
        super.onAttach(context)
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

    override fun getVisibleOptionItems(): List<Int>? {
        val optionItems: MutableList<Int> = mutableListOf()
        if (viewModel.isNotEmpty) {
            optionItems.add(R.id.action_clear_console)
        }
        if (scriptEngineHandler.commandHistory.isNotEmpty()) {
            optionItems.add(R.id.action_clear_history)
        }
        if (scriptEngineHandler.isEngineBusy) {
            optionItems.add(R.id.action_stop_engine)
        }
        // TODO: Check if engine can be restarted.
        // TODO: Check if user script is loaded and has already been saved.
        if (scriptEngineHandler.currentFileUri != null) {
            optionItems.add(R.id.action_create_shortcut)
        }
        return optionItems
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
        determineHistoryButtonAndOptionState()
        btHistory.setOnClickListener {
            val history: List<String> = scriptEngineHandler.commandHistory
            val command: String = history[currentHistoryIndex--]
            txtInput.text = command
            // Disable the button once we've reached the bottom of the history stack.
            determineHistoryButtonAndOptionState()
        }

        // Register the run button.
        determineRunButtonState()
        btRun.setOnClickListener {
            when (inputStatus) {
                INPUT_MODE_COMMAND -> {
                    val command = "${txtInput.text}"
                    onCommandRun(command)
                }
                INPUT_MODE_PROMPT -> {
                    val value = "${txtInput.text}"
                    onPromptSend(value)
                }
            }
        }

        // Register the input field.
        txtInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(text: Editable?) = determineRunButtonState(text)
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
            ScriptEngine.EVENT_EVALUATE_ERROR -> onError(text)
            ScriptEngine.EVENT_RESULT -> onResult(text)
            ScriptEngine.EVENT_SCRIPT_RUN -> onScriptRun()
            ScriptEngine.EVENT_SCRIPT_END -> onScriptEnd()
            ScriptEngine.EVENT_SOURCE_LOAD_ERROR -> onSourceLoadError(text)
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
            override fun onOK() = onRestartEngine()
        }
        dialog.show(activity, layoutInflater)
    }

    private fun onMenuItemClearHistory() {
        val dialog = ConfirmationDialog()
        dialog.message = getString(R.string.dialog_message_clear_history)
        dialog.okListener = object : ConfirmationDialog.OnOKListener {
            override fun onOK() = onClearHistory()
        }
        dialog.show(activity, layoutInflater)
    }

    private fun onMenuItemCreateShortcut() {
        if (scriptEngineHandler.currentFileUri == null) {
            return
        }
        val dialog = TextInputDialog()
        dialog.message = getString(R.string.dialog_message_create_shortcut)
        dialog.hint = getString(R.string.shortcut_name_hint)
        dialog.okListener = object : TextInputDialog.OnOKListener {
            override fun onOK(returnValue: String?) = onCreateShortcut(
                returnValue,
                scriptEngineHandler.currentFileUri
            )
        }
        dialog.show(activity, layoutInflater)
    }

    private fun onMenuItemStopEngine() {
        val dialog = ConfirmationDialog()
        dialog.message = getString(R.string.dialog_message_stop_engine)
        dialog.okListener = object : ConfirmationDialog.OnOKListener {
            override fun onOK() = onInterrupt()
        }
        dialog.show(activity, layoutInflater)
    }

    private fun clearInputField() {
        txtInput.text = ""
    }

    private fun determineRunButtonState(text: Editable? = null) {
        if (text == null) {
            return
        }
        val textField: String = text.toString()
        btRun.isEnabled = textField.isNotEmpty() && !scriptEngineHandler.isEngineBusy
    }

    private fun determineHistoryButtonAndOptionState() {
        val isEmpty: Boolean = scriptEngineHandler.commandHistory.isEmpty()
        if (isEmpty) {
            menuHandler.hideOptionItem(R.id.action_clear_history)
        } else {
            menuHandler.showOptionItem(R.id.action_clear_history)
        }

        val shouldEnable: Boolean = !isEmpty &&
                currentHistoryIndex >= 0 &&
                !scriptEngineHandler.isEngineBusy
        btHistory.isEnabled = shouldEnable
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
        txtInput.isEnabled = true
        determineHistoryButtonAndOptionState()
        determineRunButtonState()
    }

    private fun onCommandRun(command: String) {
        viewModel.addCommandLine("-> $command")
        if (scriptEngineHandler.postData(command)) {
            currentHistoryIndex = scriptEngineHandler.commandHistory.size - 1
        }
        menuHandler.showOptionItem(R.id.action_clear_console)
        menuHandler.showOptionItem(R.id.action_stop_engine)
        menuHandler.showOptionItem(R.id.action_reset_engine)
        clearInputField()
        txtInput.isEnabled = false
        determineHistoryButtonAndOptionState()
        btRun.isEnabled = false
    }

    private fun onPrint(text: String) {
        viewModel.addOutput(text)
        menuHandler.showOptionItem(R.id.action_clear_console)
    }

    private fun onPrintLine(text: String) {
        viewModel.addOutputAndEndLine(text)
        menuHandler.showOptionItem(R.id.action_clear_console)
    }

    private fun onPrompt(prompt: String) {
        setInputMode(INPUT_MODE_PROMPT)
        viewModel.addOutput("?> $prompt")
        menuHandler.hideOptionItem(R.id.action_clear_console)
        txtInput.isEnabled = true
        determineHistoryButtonAndOptionState()
        btRun.isEnabled = true
    }

    private fun onPromptSend(value: String) {
        clearInputField()
        onPrintLine(value)
        scriptEngineHandler.postData(value)
        menuHandler.showOptionItem(R.id.action_clear_console)
        txtInput.isEnabled = false
        determineHistoryButtonAndOptionState()
        btRun.isEnabled = false
    }

    private fun onError(error: String) {
        menuHandler.showOptionItem(R.id.action_clear_console)
        menuHandler.hideOptionItem(R.id.action_stop_engine)
        setInputMode(INPUT_MODE_COMMAND)
        determineHistoryButtonAndOptionState()
        viewModel.addErrorLine(error)
        txtInput.isEnabled = true
        determineHistoryButtonAndOptionState()
    }

    private fun onResult(result: String) {
        menuHandler.showOptionItem(R.id.action_clear_console)
        menuHandler.hideOptionItem(R.id.action_stop_engine)
        setInputMode(INPUT_MODE_COMMAND)
        viewModel.addResultLine("<= $result")
        txtInput.isEnabled = true
        determineHistoryButtonAndOptionState()
    }

    private fun onRestartEngine() {
        viewModel.addCommandLine(getString(R.string.restart_notification))
        scriptEngineHandler.restartScriptEngine()
        menuHandler.showOptionItem(R.id.action_clear_console)
        menuHandler.hideOptionItem(R.id.action_stop_engine)
        menuHandler.hideOptionItem(R.id.action_reset_engine)
        setInputMode(INPUT_MODE_COMMAND)
        txtInput.isEnabled = true
        determineRunButtonState()
        determineHistoryButtonAndOptionState()
    }

    private fun onScriptRun() {
        viewModel.addCommandLine(getString(R.string.restart_notification))
        menuHandler.showOptionItem(R.id.action_clear_console)
        menuHandler.showOptionItem(R.id.action_stop_engine)
        menuHandler.showOptionItem(R.id.action_reset_engine)
        menuHandler.showOptionItem(R.id.action_create_shortcut)
        txtInput.isEnabled = false
        btRun.isEnabled = false
        determineHistoryButtonAndOptionState()
    }

    private fun onScriptEnd() {
        setInputMode(INPUT_MODE_COMMAND)
        menuHandler.hideOptionItem(R.id.action_stop_engine)
        txtInput.isEnabled = true
        determineRunButtonState()
        determineHistoryButtonAndOptionState()
    }

    private fun onClearConsole() {
        viewModel.clear()
        menuHandler.hideOptionItem(R.id.action_clear_console)
    }

    private fun onInterrupt() {
        viewModel.addCommandLine(getString(R.string.interrupt_notification))
        scriptEngineHandler.interrupt()
        menuHandler.showOptionItem(R.id.action_clear_console)
        menuHandler.hideOptionItem(R.id.action_stop_engine)
        setInputMode(INPUT_MODE_COMMAND)
        txtInput.isEnabled = true
        determineRunButtonState()
        determineHistoryButtonAndOptionState()
    }

    private fun onSourceLoadError(error: String) {
        viewModel.addErrorLine(error)
        menuHandler.showOptionItem(R.id.action_clear_console)
    }

    private fun onCreateShortcut(shortcutName: String?, uri: Uri?) {
        if (shortcutName == null || uri == null) {
            return
        }
        shortcutViewModel.addShortcut(shortcutName, uri)
        menuHandler.navigateTo(R.id.nav_shortcut)
        // TODO: Hide the menu option.
    }

    private fun onClearHistory() {
        viewModel.addCommandLine(getString(R.string.history_clear_notification))
        scriptEngineHandler.clearCommandHistory()
        menuHandler.showOptionItem(R.id.action_clear_console)
        menuHandler.hideOptionItem(R.id.action_clear_history)
        currentHistoryIndex = -1
        determineHistoryButtonAndOptionState()
    }
}
