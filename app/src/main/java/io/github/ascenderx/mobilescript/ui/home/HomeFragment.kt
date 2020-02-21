package io.github.ascenderx.mobilescript.ui.home

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.eclipsesource.v8.*
import io.github.ascenderx.mobilescript.R

class HomeFragment : Fragment() {
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var textView: TextView
    private lateinit var v8: V8
    private lateinit var evaluator: Evaluator
    private var output: StringBuffer = StringBuffer()

    override fun onAttach(context: Context) {
        super.onAttach(context)

        if (context is Evaluator) {
            evaluator = context
            v8 = evaluator.getRuntime()
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

        // Register components.
        textView = root.findViewById(R.id.text_home)
        val editText: TextView = root.findViewById(R.id.editText)
        val btRun: Button = root.findViewById(R.id.bt_run)
        btRun.isEnabled = false
        val evaluator = this.evaluator
        btRun.setOnClickListener(object : View.OnClickListener {
            override fun onClick(view: View) {
                val command = editText.text.toString()
                output.append("> $command\n")

                try {
                    output.append(evaluator.evaluate(command) ?: "undefined")
                    output.append("\n")
                } catch (v8ex: V8RuntimeException) {
                    output.append("[JavaScript] ${v8ex.message.toString()}\n")
                }
                // Immediately clear the input field.
                editText.text = ""
                textView.text = output.toString()
            }
        })
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

    fun printLine(text: String) {
        output.append(text)
    }

    fun clear() {
        output.delete(0, output.length)
    }

    interface Evaluator {
        fun getRuntime(): V8
        fun evaluate(text: String): Any?
    }

    class PrintLineCallback(private val fragment: HomeFragment) : JavaVoidCallback {
        override fun invoke(receiver: V8Object, parameters: V8Array) {
            if (parameters.length() == 0) {
                fragment.printLine("\n")
                return
            }

            val output: String = parameters[0].toString()
            fragment.printLine(output)
        }
    }

    class ClearCallback(private val fragment: HomeFragment) : JavaVoidCallback {
        override fun invoke(receiver: V8Object, parameters: V8Array) {
            fragment.clear()
        }
    }
}
