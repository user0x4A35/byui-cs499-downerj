package io.github.ascenderx.mobilescript.ui.console

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import io.github.ascenderx.mobilescript.models.ConsoleOutputRow
import io.github.ascenderx.mobilescript.models.ConsoleOutputType
import io.github.ascenderx.mobilescript.models.ScriptEngine
import io.github.ascenderx.mobilescript.models.ScriptMessageStatus

class ConsoleFragment : Fragment() {
    private lateinit var consoleViewModel: ConsoleViewModel
    private lateinit var consoleAdapter: ConsoleListAdapter
    private lateinit var handler: Handler
    private lateinit var scriptEngine: ScriptEngine

    override fun onAttach(context: Context) {
        super.onAttach(context)

        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    ScriptMessageStatus.PRINT.value -> printOutput(msg.obj as String)
                    ScriptMessageStatus.ERROR.value -> printError(msg.obj as String)
                    ScriptMessageStatus.CLEAR.value -> clearOutput()
                    ScriptMessageStatus.RESULT.value -> printResult(msg.obj as String)
                }
            }
        }

        scriptEngine = ScriptEngine.getInstance(handler)
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
        val consoleOutputView: ListView = root.findViewById(R.id.consoleOutput) as ListView
        val editText: TextView = root.findViewById(R.id.editText)
        val btRun: Button = root.findViewById(R.id.btRun)

        // Register the output list.
        consoleAdapter =
            ConsoleListAdapter(
                context as Context
            )
        consoleOutputView.adapter = consoleAdapter

        // Register the run button.
        btRun.isEnabled = false
        btRun.setOnClickListener(object : View.OnClickListener {
            override fun onClick(view: View) {
                val command = "${editText.text}"
                printCommand(command)
                scriptEngine.evaluate(command)

                // Immediately clear the input field.
                editText.text = ""
            }
        })

        // Register the input field.
        editText.addTextChangedListener(object : TextWatcher {
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

    fun printCommand(command: String) {
        consoleAdapter.addItem(
            ConsoleOutputType.COMMAND,
            "-> $command"
        )
    }

    fun printOutput(text: String) {
        consoleAdapter.addItem(
            ConsoleOutputType.VALID,
            text
        )
    }

    fun printError(message: String) {
        consoleAdapter.addItem(
            ConsoleOutputType.INVALID,
            message
        )
    }

    fun printResult(result: String) {
        consoleAdapter.addItem(
            ConsoleOutputType.RESULT,
            "<= $result"
        )
    }

    fun clearOutput() {
        consoleAdapter.clear()
    }
}
