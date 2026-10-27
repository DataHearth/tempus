package com.eddyizm.tempus.navigation

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable

/**
 * Paints the band behind the status bar with the colour of whatever sits directly under it, so
 * tinted toolbars, page headers and the expanded player run up to the top edge of the screen.
 *
 * The content root keeps its status-bar padding (toolbars never slide under the bar); this only
 * colours that padding and picks status bar icons that stay legible on it.
 */
class StatusBarBackdrop(
    private val window: Window,
    private val contentRoot: View,
    private val pageRoot: View,
) : ViewTreeObserver.OnPreDrawListener {

    private var painted: Int? = null

    fun attach() {
        contentRoot.viewTreeObserver.addOnPreDrawListener(this)
    }

    @Suppress("DEPRECATION")
    override fun onPreDraw(): Boolean {
        val color = colorUnderStatusBar()
        // ThemeHelper repaints statusBarColor on start, which only takes effect below API 35.
        val barRepainted = Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM && window.statusBarColor != color
        if (color != painted || barRepainted) {
            painted = color
            contentRoot.setBackgroundColor(color)
            window.statusBarColor = color
            WindowInsetsControllerCompat(window, contentRoot).isAppearanceLightStatusBars =
                ColorUtils.calculateLuminance(color) > 0.5
        }
        return true
    }

    private fun colorUnderStatusBar(): Int {
        val fallback = MaterialColors.getColor(pageRoot, MaterialR.attr.colorSurface)
        if (pageRoot.width == 0) return fallback
        return opaqueColorAt(pageRoot, pageRoot.width / 2f, 0f) ?: fallback
    }

    /** Returns the opaque colour drawn at (x, y): the topmost child that paints there wins, else this view's own background. */
    private fun opaqueColorAt(view: View, x: Float, y: Float): Int? {
        if (view is ViewGroup) {
            for (i in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(i)
                if (child.visibility != View.VISIBLE || child.alpha == 0f) continue
                val cx = x + view.scrollX - child.left - child.translationX
                val cy = y + view.scrollY - child.top - child.translationY
                if (cx >= 0 && cy >= 0 && cx < child.width && cy < child.height) {
                    opaqueColorAt(child, cx, cy)?.let { return it }
                }
            }
        }
        return backgroundColor(view.background)
    }

    private fun backgroundColor(drawable: Drawable?): Int? {
        val color = when (drawable) {
            is ColorDrawable -> drawable.color
            is MaterialShapeDrawable -> drawable.fillColor?.defaultColor
            is GradientDrawable -> drawable.colors?.firstOrNull() ?: drawable.color?.defaultColor
            is RippleDrawable -> null
            is LayerDrawable -> (0 until drawable.numberOfLayers).firstNotNullOfOrNull { backgroundColor(drawable.getDrawable(it)) }
            else -> null
        } ?: return null
        return if (Color.alpha(color) == 0xFF) color else null
    }
}
