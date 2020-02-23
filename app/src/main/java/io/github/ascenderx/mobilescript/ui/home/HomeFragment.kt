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
import io.github.ascenderx.mobilescript.models.ConsoleOutputRow
import io.github.ascenderx.mobilescript.ui.ConsoleListAdapter

class HomeFragment : Fragment() {
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var v8: V8
    private lateinit var evaluator: Evaluator
    private var output: StringBuffer = StringBuffer()
    private lateinit var consoleAdapter: ConsoleListAdapter

    override fun onAttach(context: Context) {
        super.onAttach(context)

        if (context is Evaluator) {
            evaluator = context
            v8 = evaluator.getRuntime()
            v8.registerJavaMethod(PrintCallback(this), "print")
            v8.registerJavaMethod(PrintLineCallback(this), "println")
            v8.registerJavaMethod(ClearCallback(this), "clear")
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
        val consoleOutputView: ListView = root.findViewById(R.id.consoleOutput) as ListView
        val editText: TextView = root.findViewById(R.id.editText)
        val btRun: Button = root.findViewById(R.id.btRun)

        // Register the output list.
        consoleAdapter = ConsoleListAdapter(context as Context)
        consoleOutputView.adapter = consoleAdapter

        // Register the run button.
        btRun.isEnabled = false
        val evaluator = this.evaluator
        btRun.setOnClickListener(object : View.OnClickListener {
            override fun onClick(view: View) {
                val command = "${editText.text}"
                val result: String

                try {
                    result = "${evaluator.evaluate(command) ?: "undefined"}"
                    consoleAdapter.addItem(
                        ConsoleOutputRow.ConsoleOutputType.VALID,
                        "-> $command\n$output<= $result\n"
                    )
                } catch (v8ex: V8RuntimeException) {
                    consoleAdapter.addItem(
                        ConsoleOutputRow.ConsoleOutputType.INVALID,
                        "-> $command\n[JavaScript] ${v8ex.message.toString()}\n"
                    )
                }

                // Immediately clear the input field.
                editText.text = ""
                clearOutputBuffer()
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
        fun getRuntime(): V8
        fun evaluate(text: String): Any?
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
