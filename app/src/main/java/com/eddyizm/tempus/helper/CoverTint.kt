package com.eddyizm.tempus.helper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.scale
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.eddyizm.tempus.glide.CustomGlideRequest
import com.google.android.material.R as MaterialR
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.color.MaterialColors

/**
 * Colours taken from a cover: [tint] fills the player and mini player, [band] sits behind
 * page headers and the playing queue row, [accent] marks the current song and progress.
 */
data class CoverTint(val tint: Int, val band: Int, val accent: Int) {
    fun interface Listener {
        fun onTint(tint: CoverTint)
    }

    companion object {
        // The quantizer walks every pixel; a thumbnail gives the same seed in a fraction of the time.
        private const val SAMPLE_PX = 64

        @JvmStatic
        fun from(context: Context, bitmap: Bitmap): CoverTint {
            val sample = if (bitmap.width > SAMPLE_PX || bitmap.height > SAMPLE_PX) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false).scale(SAMPLE_PX, SAMPLE_PX)
            } else {
                bitmap
            }
            val seed = DynamicColorsOptions.Builder().setContentBasedSource(sample).build().contentBasedSeedColor
                ?: return neutral(context)
            val roles = MaterialColors.getColorRoles(context, seed)
            val surface = MaterialColors.getColor(context, MaterialR.attr.colorSurface, 0)
            return CoverTint(
                tint = roles.accentContainer,
                band = ColorUtils.blendARGB(surface, roles.accentContainer, 0.55f),
                accent = roles.accent,
            )
        }

        @JvmStatic
        fun neutral(context: Context): CoverTint {
            val container = MaterialColors.getColor(context, MaterialR.attr.colorSurfaceContainerHigh, 0)
            val band = MaterialColors.getColor(context, MaterialR.attr.colorSurfaceContainer, 0)
            val accent = MaterialColors.getColor(context, MaterialR.attr.colorOnSurface, 0)
            return CoverTint(container, band, accent)
        }

        /** Loads [coverId] and reports its tint; the neutral tint is reported first so views never keep a stale colour. */
        @JvmStatic
        fun load(context: Context, coverId: String?, onTint: Listener) {
            onTint.onTint(neutral(context))
            if (coverId.isNullOrEmpty()) return
            CustomGlideRequest.loadAlbumArtBitmap(context, coverId, SAMPLE_PX * 2, object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    onTint.onTint(from(context, resource))
                }

                override fun onLoadCleared(placeholder: Drawable?) = Unit
            })
        }
    }
}
