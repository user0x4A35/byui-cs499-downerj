package io.github.ascenderx.mobilescript.ui.dialog

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import io.github.ascenderx.mobilescript.R

class TextInputDialog {
    var okListener: OnOKListener? = null
    var message: String = "OK to continue?"
    var hint: String = "Enter a value..."
    private var returnValue: String? = null

    fun show(context: Context, inflater: LayoutInflater) {
        val promptView: View = inflater.inflate(R.layout.prompt_input, null)
        val dialogBuilder = AlertDialog.Builder(context)
            .setView(promptView)
            .setCancelable(false)
            .setPositiveButton(R.string.dialog_button_ok) { _, _ ->
                okListener?.onOK(returnValue)
            }
            .setNegativeButton(R.string.dialog_button_cancel) { dialog, _ ->
                dialog?.cancel()
            }

        val lblMessage: TextView = promptView.findViewById(R.id.lbl_input_message)
        lblMessage.text = message
        val txtInput: EditText = promptView.findViewById(R.id.txt_input_prompt)
        txtInput.hint = hint
        txtInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(text: Editable?) {
                returnValue = text?.toString()
            }
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        val alertDialog: AlertDialog = dialogBuilder.create()
        alertDialog.show()
    }

    interface OnOKListener {
        fun onOK(returnValue: String?)
    }
}