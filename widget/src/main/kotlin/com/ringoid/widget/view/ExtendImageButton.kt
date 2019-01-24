package com.ringoid.widget.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import com.jakewharton.rxbinding3.view.clicks
import com.ringoid.utility.clickDebounce
import com.ringoid.utility.getSelectableItemBg
import com.ringoid.widget.R
import kotlinx.android.synthetic.main.widget_extend_image_button.view.*

class ExtendImageButton : FrameLayout {

    constructor(context: Context): this(context, null)

    constructor(context: Context, attributes: AttributeSet?): this(context, attributes, 0)

    constructor(context: Context, attributes: AttributeSet?, defStyleAttr: Int): super(context, attributes, defStyleAttr) {
        init(context, attributes, defStyleAttr)
    }

    // --------------------------------------------------------------------------------------------
    private fun init(context: Context, attributes: AttributeSet?, defStyleAttr: Int) {
        background = context.getSelectableItemBg()
        foreground = context.getSelectableItemBg()
        isClickable = true
        isFocusable = true

        LayoutInflater.from(context).inflate(R.layout.widget_extend_image_button, this, true)

        context.obtainStyledAttributes(attributes, R.styleable.ExtendImageButton, defStyleAttr, 0)
            .apply {
                setImageResource(resId = getResourceId(R.styleable.ExtendImageButton_xbtnSrc, 0))
                recycle()
            }
    }

    /* API */
    // --------------------------------------------------------------------------------------------
    fun setImageResource(@DrawableRes resId: Int) {
        resId.takeIf { it != 0 }?.let { ibtn.setImageResource(it) }
    }

    @Suppress("CheckResult")
    override fun setOnClickListener(l: OnClickListener?) {
        super.setOnClickListener(l)
        ibtn.clicks().compose(clickDebounce()).subscribe { l?.onClick(ibtn) }
    }
}