package com.eddyizm.tempus.ui.components

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * Shows PlayerControlView's position and duration as "4:52" rather than "04:52". The control
 * view formats these itself with no hook, so the leading zero is dropped on the way in.
 */
class PlayerTimeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    override fun setText(text: CharSequence?, type: BufferType?) {
        super.setText(dropLeadingZero(text), type)
    }

    private fun dropLeadingZero(text: CharSequence?): CharSequence? {
        if (text == null || text.length < 5 || text[0] != '0' || text[2] != ':') return text
        return text.subSequence(1, text.length)
    }
}
