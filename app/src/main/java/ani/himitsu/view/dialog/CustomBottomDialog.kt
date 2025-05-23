package ani.himitsu.view.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import ani.himitsu.databinding.BottomSheetCustomBinding
import bit.himitsu.setStatusTransparent

open class CustomBottomDialog : BottomSheetDialogFragment() {
    private var _binding: BottomSheetCustomBinding? = null
    private val binding by lazy { _binding!! }

    private val viewList = mutableListOf<View>()
    fun addView(view: View) {
        viewList.add(view)
    }

    var title: String? = null
    fun setTitleText(string: String) {
        title = string
    }

    private var checkText: String? = null
    private var checkChecked: Boolean = false
    private var checkCallback: ((Boolean) -> Unit)? = null

    fun setCheck(text: String, checked: Boolean, callback: ((Boolean) -> Unit)) {
        checkText = text
        checkChecked = checked
        checkCallback = callback
    }

    private var negativeText: String? = null
    private var negativeCallback: (() -> Unit)? = null
    fun setNegativeButton(text: String, callback: (() -> Unit)) {
        negativeText = text
        negativeCallback = callback
    }

    private var positiveText: String? = null
    private var positiveCallback: (() -> Unit)? = null
    fun setPositiveButton(text: String, callback: (() -> Unit)) {
        positiveText = text
        positiveCallback = callback
    }

    private var neutralText: String? = null
    private var neutralCallback: (() -> Unit)? = null
    fun setNeutralButton(text: String, callback: (() -> Unit)) {
        neutralText = text
        neutralCallback = callback
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCustomBinding.inflate(inflater, container, false)
        dialog?.window?.setStatusTransparent()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.bottomSheetCustomTitle.text = title
        viewList.forEach {
            binding.bottomDialogCustomContainer.addView(it)
        }
        if (checkText != null) binding.bottomDialogCustomCheckBox.apply {
            visibility = View.VISIBLE
            text = checkText
            isChecked = checkChecked
            setOnCheckedChangeListener { _, checked -> checkCallback?.invoke(checked) }
        }

        if (negativeText != null) binding.bottomDialogCustomNegative.apply {
            visibility = View.VISIBLE
            text = negativeText
            setOnClickListener { negativeCallback?.invoke() }
        }

        if (positiveText != null) binding.bottomDialogCustomPositive.apply {
            visibility = View.VISIBLE
            text = positiveText
            setOnClickListener { positiveCallback?.invoke() }
        }

        if (neutralText != null) binding.bottomDialogCustomNeutral.apply {
            visibility = View.VISIBLE
            text = neutralText
            setOnClickListener { neutralCallback?.invoke() }
        }

    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }

    companion object {
        fun newInstance() = CustomBottomDialog()
    }

}