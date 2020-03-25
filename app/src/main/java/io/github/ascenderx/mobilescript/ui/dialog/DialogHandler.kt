package io.github.ascenderx.mobilescript.ui.dialog

import android.content.DialogInterface

interface DialogHandler {
    fun showConfirmationDialog(
        message: String,
        okCallback: DialogInterface.OnClickListener
    )
    fun showTextInputDialog(
        message: String,
        hint: String,
        inputString: StringReference,
        okCallback: DialogInterface.OnClickListener
    )
}