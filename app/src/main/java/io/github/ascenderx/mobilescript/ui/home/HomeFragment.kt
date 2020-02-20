package io.github.ascenderx.mobilescript.ui.home

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.eclipsesource.v8.JavaVoidCallback
import com.eclipsesource.v8.V8
import com.eclipsesource.v8.V8Array
import com.eclipsesource.v8.V8Object
import io.github.ascenderx.mobilescript.R
import java.lang.Exception

class HomeFragment : Fragment() {
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var textView: TextView
    private lateinit var v8: V8
    private lateinit var evaluator: Evaluator

    override fun onAttach(context: Context) {
        super.onAttach(context)

        if (context is Evaluator) {
            evaluator = context
            v8 = evaluator.getRuntime()
            v8.registerJavaMethod(PrintLineCallback(this), "println")
            Log.d("MS.Home.attach", context.toString())
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_home, container, false)
        textView = root.findViewById(R.id.text_home)
//        homeViewModel.text.observe(this, Observer {
//            textView.text = it
//        })
        val editText: TextView = root.findViewById(R.id.editText)
        val btRun: Button = root.findViewById(R.id.bt_run)

        btRun.setOnClickListener { view ->
            try {
                this.evaluator.evaluate(editText.text.toString())
            } catch (ex: Exception) {
                write(ex.message.toString())
            }
        }
        return root
    }

    fun write(text: String) {
        textView.text = text
    }

    interface Evaluator {
        fun getRuntime(): V8
        fun evaluate(text: String)
    }
}

class PrintLineCallback(private val fragment: HomeFragment) : JavaVoidCallback {

    override fun invoke(receiver: V8Object, parameters: V8Array) {
        val argument: String = parameters[0] as String
        fragment.write(argument)
    }
}