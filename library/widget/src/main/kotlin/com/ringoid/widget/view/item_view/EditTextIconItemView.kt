package com.ringoid.widget.view.item_view

import android.content.Context
import android.util.AttributeSet
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.LayoutRes
import com.jakewharton.rxbinding3.InitialValueObservable
import com.ringoid.utility.changeVisibility
import com.ringoid.widget.R
import kotlinx.android.synthetic.main.widget_edit_text_icon_item_view_layout.view.*

class EditTextIconItemView : TextIconItemView {

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attributes: AttributeSet?) : this(context, attributes, 0)

    constructor(context: Context, attributes: AttributeSet?, defStyleAttr: Int) : super(context, attributes, defStyleAttr) {
        with (context.obtainStyledAttributes(attributes, R.styleable.EditTextIconItemView, defStyleAttr, R.style.TextIconItemView)) {
            ll_count_container.changeVisibility(isVisible = getBoolean(R.styleable.EditTextIconItemView_edit_text_icon_item_with_counter, false))
            recycle()
        }
    }

    @LayoutRes
    override fun getLayoutId(): Int = R.layout.widget_edit_text_icon_item_view_layout

    override fun getInputTextView(): TextView = tv_input

    /* API */
    // --------------------------------------------------------------------------------------------
    override fun getText(): String = getInputTextView().text.toString()

    override fun setInputText(text: String?): Boolean =
        super.setInputText(text).also {
            with (getInputTextView() as EditText) {
                setCharsCount(text?.length ?: 0)
                setSelection(text?.length ?: 0)
            }
        }

    @Suppress("SetTextI18n")
    internal fun setCharsCount(count: Int) {
        tv_chars_count?.text = "$count/$MAX_LENGTH"
    }
}

fun EditTextIconItemView.textChanges(): InitialValueObservable<CharSequence> =
    TextIconItemViewTextChangesObservable(this)
        .doOnNext { setCharsCount(it.length) }
