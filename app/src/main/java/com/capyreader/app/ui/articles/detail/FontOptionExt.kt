package com.capyreader.app.ui.articles.detail

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.toFontFamily
import com.capyreader.app.R
import com.jocmp.capy.articles.FontOption

/** Reader font options mapped to bundled font resources; the system default
 *  falls back to null so consumers can use the platform family. */
fun FontOption.toFontFamily(): FontFamily? = when (this) {
    FontOption.SYSTEM_DEFAULT -> null
    FontOption.ATKINSON_HYPERLEGIBLE -> Font(resId = R.font.atkinson_hyperlegible)
    FontOption.INTER -> Font(resId = R.font.inter)
    FontOption.JOST -> Font(resId = R.font.jost)
    FontOption.LITERATA -> Font(resId = R.font.literata)
    FontOption.POPPINS -> Font(resId = R.font.poppins)
    FontOption.VOLLKORN -> Font(resId = R.font.vollkorn)
}?.toFontFamily()
