package ani.himitsu.util

import android.content.Context
import android.content.DialogInterface
import android.view.View
import androidx.appcompat.app.AlertDialog
import ani.himitsu.R

@Suppress("unused")
class AlertDialogBuilder(private val context: Context) {
    private var title: String? = null
    private var customTitle: View? = null
    private var message: String? = null
    private var cancellable = true
    private var positiveButtonTitle: String? = null
    private var negativeButtonTitle: String? = null
    private var neutralButtonTitle: String? = null
    private var onPositiveButtonClick: (() -> Unit)? = null
    private var onNegativeButtonClick: (() -> Unit)? = null
    private var onNeutralButtonClick: (() -> Unit)? = null
    private var items: Array<String>? = null
    private var checkedItems: BooleanArray? = null
    private var onItemChecked: ((Int, Boolean) -> Unit)? = null
    private var onItemsSelected: ((BooleanArray) -> Unit)? = null
    private var dismissOnSelect: Boolean = true
    private var selectedItemIndex: Int = -1
    private var onItemSelected: ((Int) -> Unit)? = null
    private var customView: View? = null
    private var attach: ((dialog: AlertDialog) -> Unit)? = null
    private var dismissListener: DialogInterface.OnDismissListener? = null
    private var cancelListener: DialogInterface.OnCancelListener? = null
    fun setTitle(title: String?): AlertDialogBuilder {
        this.title = title
        return this
    }

    fun setTitle(int: Int, formatArgs: Int? = null): AlertDialogBuilder {
        this.title = context.getString(int, formatArgs)
        return this
    }

    fun setCustomTitle(view: View): AlertDialogBuilder {
        this.customTitle = view
        return this
    }

    fun setMessage(message: String?): AlertDialogBuilder {
        this.message = message
        return this
    }

    fun setMessage(stringInt: Int, vararg formatArgs: Any): AlertDialogBuilder {
        this.message = context.getString(stringInt, *formatArgs)
        return this
    }

    fun setCustomView(view: View): AlertDialogBuilder {
        this.customView = view
        return this
    }

    fun setPositiveButton(title: String?, onClick: (() -> Unit)? = null): AlertDialogBuilder {
        this.positiveButtonTitle = title
        this.onPositiveButtonClick = onClick
        return this
    }

    fun setPositiveButton(
        int: Int,
        formatArgs: Int? = null,
        onClick: (() -> Unit)? = null
    ): AlertDialogBuilder {
        this.positiveButtonTitle = context.getString(int, formatArgs)
        this.onPositiveButtonClick = onClick
        return this
    }

    fun setNegativeButton(title: String?, onClick: (() -> Unit)? = null): AlertDialogBuilder {
        this.negativeButtonTitle = title
        this.onNegativeButtonClick = onClick
        return this
    }

    fun setNegativeButton(
        int: Int,
        formatArgs: Int? = null,
        onClick: (() -> Unit)? = null
    ): AlertDialogBuilder {
        this.negativeButtonTitle = context.getString(int, formatArgs)
        this.onNegativeButtonClick = onClick
        return this
    }

    fun setNeutralButton(title: String?, onClick: (() -> Unit)? = null): AlertDialogBuilder {
        this.neutralButtonTitle = title
        this.onNeutralButtonClick = onClick
        return this
    }

    fun setNeutralButton(
        int: Int,
        formatArgs: Int? = null,
        onClick: (() -> Unit)? = null
    ): AlertDialogBuilder {
        this.neutralButtonTitle = context.getString(int, formatArgs)
        this.onNeutralButtonClick = onClick
        return this
    }

    fun attach(attach: ((dialog: AlertDialog) -> Unit)?): AlertDialogBuilder {
        this.attach = attach
        return this
    }

    fun setSingleChoiceItems(
        items: Array<String>,
        selectedItemIndex: Int = -1,
        dismissOnSelect: Boolean = true,
        onItemSelected: (Int) -> Unit
    ): AlertDialogBuilder {
        this.items = items
        this.selectedItemIndex = selectedItemIndex
        this.dismissOnSelect = dismissOnSelect
        this.onItemSelected = onItemSelected
        return this
    }

    fun setMultiChoiceItems(
        items: Array<String>,
        checkedItems: BooleanArray? = null,
        onItemChecked: ((Int, Boolean) -> Unit)?,
        onItemsSelected: ((BooleanArray) -> Unit)?,
    ): AlertDialogBuilder {
        this.items = items
        this.checkedItems = checkedItems ?: BooleanArray(items.size) { false }
        this.onItemChecked = onItemChecked
        this.onItemsSelected = onItemsSelected
        return this
    }

    fun setCancelable(cancellable: Boolean) {
        this.cancellable = cancellable
    }

    fun setOnCancelListener(cancelListener: DialogInterface.OnCancelListener) {
        this.cancelListener = cancelListener
    }

    fun setOnDismissListener(dismissListener: DialogInterface.OnDismissListener) {
        this.dismissListener = dismissListener
    }

    fun show(): AlertDialog {
        val builder = AlertDialog.Builder(context, R.style.MyDialog)
        if (title != null) builder.setTitle(title)
        if (customTitle != null) builder.setCustomTitle(customTitle)
        if (message != null) builder.setMessage(message)
        if (customView != null) builder.setView(customView)
        if (items != null) {
            if (onItemSelected != null) {
                builder.setSingleChoiceItems(items, selectedItemIndex) { dialog, which ->
                    selectedItemIndex = which
                    onItemSelected?.invoke(which)
                    if (dismissOnSelect) dialog.dismiss()
                }
            } else if (checkedItems != null && (onItemChecked != null || onItemsSelected != null)) {
                builder.setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
                    onItemChecked?.invoke(which, isChecked) ?: checkedItems?.set(which, isChecked)
                }
            }
        }
        if (positiveButtonTitle != null) {
            builder.setPositiveButton(positiveButtonTitle) { dialog, _ ->
                onPositiveButtonClick?.invoke()
                dialog.dismiss()
            }
        }
        if (negativeButtonTitle != null) {
            builder.setNegativeButton(negativeButtonTitle) { dialog, _ ->
                onNegativeButtonClick?.invoke()
                dialog.dismiss()
            }
        }
        if (neutralButtonTitle != null) {
            builder.setNeutralButton(neutralButtonTitle) { dialog, _ ->
                onNeutralButtonClick?.invoke()
                dialog.dismiss()
            }
        }
        dismissListener?.let { builder.setOnDismissListener(it) }
        builder.setCancelable(cancellable)
        cancelListener?.let { builder.setOnCancelListener(it) }
        return builder.create().apply {
            attach?.invoke(this)
            // window?.attributes?.windowAnimations = android.R.style.Animation_Dialog
            window?.setDimAmount(0.8f)
            show()
        }
    }
}

fun Context.customAlertDialog(): AlertDialogBuilder {
    return AlertDialogBuilder(this)
}