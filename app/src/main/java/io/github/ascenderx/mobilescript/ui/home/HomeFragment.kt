package io.github.ascenderx.mobilescript.ui.home

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

import com.eclipsesource.v8.*

import io.github.ascenderx.mobilescript.R
import io.github.ascenderx.mobilescript.controllers.ConsoleOutputRow
import io.github.ascenderx.mobilescript.ui.ConsoleListAdapter

class HomeFragment : Fragment(), OnResultListener {
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var evaluator: Evaluator
    private var output: StringBuffer = StringBuffer()
    private lateinit var consoleAdapter: ConsoleListAdapter

    override fun onAttach(context: Context) {
        super.onAttach(context)

        if (context is Evaluator) {
            evaluator = context
            evaluator.callback = this
            evaluator.bootRuntime(this)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Provided.
        homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_home, container, false)

        // Get components by ID.
        val consoleOutputView: ListView = root.findViewById(R.id.consoleOutput)
        val editText: TextView = root.findViewById(R.id.editText)
        val btRun: Button = root.findViewById(R.id.btRun)

        // Register the output list.
        consoleAdapter = ConsoleListAdapter(context as Context)
        consoleOutputView.adapter = consoleAdapter

        // Register the run button.
        btRun.isEnabled = false
        btRun.setOnClickListener(object : View.OnClickListener {
            override fun onClick(view: View) {
                val command = "${editText.text}"
                evaluator.evaluate(command)

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

    override fun onResult(command: String?, result: Any?, error: String?) {
        if (command == null) {
            return
        }

        if (error == null) {
            val fullResult = "${result ?: "undefined"}"
            consoleAdapter.addItem(
                ConsoleOutputRow.ConsoleOutputType.VALID,
                "-> $command\n$output<= $fullResult\n"
            )
        } else {
            consoleAdapter.addItem(
                ConsoleOutputRow.ConsoleOutputType.INVALID,
                "-> $command\n[JavaScript] ${error}\n"
            )
        }
        clearOutputBuffer()
    }

    fun print(text: String) {
        output.append(text)
    }

    private fun clearOutputBuffer() {
        output.delete(0, output.length)
    }

    fun clearOutputList() {
        clearOutputBuffer()
        consoleAdapter.clear()
    }

    interface Evaluator {
        var callback: OnResultListener?
        fun bootRuntime(fragment: HomeFragment)
        fun evaluate(command: String)
    }

    class PrintCallback(private val fragment: HomeFragment) : JavaVoidCallback {
        override fun invoke(receiver: V8Object?, parameters: V8Array?) {
            if ((parameters == null) || (parameters.length() == 0)) {
                fragment.print("\n")
                return
            }

            val output = "${parameters[0]}"
            fragment.print(output)
        }
    }

    class PrintLineCallback(private val fragment: HomeFragment) : JavaVoidCallback {
        override fun invoke(receiver: V8Object?, parameters: V8Array?) {
            if ((parameters == null) || (parameters.length() == 0)) {
                fragment.print("\n")
                return
            }

            val output = "${parameters[0]}\n"
            fragment.print(output)
        }
    }

    class ClearCallback(private val fragment: HomeFragment) : JavaVoidCallback {
        override fun invoke(receiver: V8Object?, parameters: V8Array?) {
            fragment.clearOutputList()
        }
    }
}

interface OnResultListener {
    fun onResult(command: String?, result: Any?, error: String?)
}