package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.util.AttributeSet
import android.widget.TextClock
import androidx.core.widget.TextViewCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.ClockType

class ClockOverlay : TextClock {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        TextViewCompat.setTextAppearance(this, R.style.OverlayText)
    }

    /** Large, condensed, thin, one uniform translucent white: it takes whatever the video behind it gives, no painted gradient. */
    fun applyGlassStyle() {
        typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 72f)
        setTextColor(android.graphics.Color.argb(105, 255, 255, 255))
        setShadowLayer(0f, 0f, 0f, 0)
        includeFontPadding = false
        letterSpacing = 0.04f
    }

    fun updateFormat(format: ClockType?) {
        when (format) {
            ClockType.HOUR_24 -> {
                this.format12Hour = "HH:mm"
                this.format24Hour = "HH:mm"
            }

            ClockType.HOUR_12 -> {
                this.format12Hour = "h:mm a"
                this.format24Hour = "h:mm a"
            }

            else -> {
                // this.text.toString().lowercase() // maybe add pref? 1:30pm vs 1:30PM
            }
        }
    }

    override fun onDetachedFromWindow() {
        // Fixes a commonly reported crash with this control ?!
        try {
            super.onDetachedFromWindow()
        } catch (e: Exception) {
            // Doesn't matter
        }
    }
}
