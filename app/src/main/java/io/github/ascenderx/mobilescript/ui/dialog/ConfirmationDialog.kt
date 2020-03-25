package io.github.ascenderx.mobilescript.ui.dialog

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import io.github.ascenderx.mobilescript.R

class ConfirmationDialog {
    var okListener: OnOKListener? = null
    var message: String = "OK to continue?"

    fun show(context: Context, inflater: LayoutInflater) {
        val promptView: View = inflater.inflate(R.layout.prompt_confirm, null)
        val dialogBuilder = AlertDialog.Builder(context)
            .setView(promptView)
            .setCancelable(false)
            .setPositiveButton(R.string.dialog_button_ok) { _, _ ->
                okListener?.onOK()
            }
            .setNegativeButton(R.string.dialog_button_cancel) { dialog, _ ->
                dialog?.cancel()
            }

        val lblMessage: TextView = promptView.findViewById(R.id.lbl_confirm_message)
        lblMessage.text = message
        val alertDialog: AlertDialog = dialogBuilder.create()
        alertDialog.show()
    }

    interface OnOKListener {
        fun onOK()
    }
}