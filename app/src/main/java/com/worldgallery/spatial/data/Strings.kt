package com.worldgallery.spatial.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Bilingual copy (zh-CN / en-US). Every user-facing string is written as `tr(zh, en)` at the call
 * site so the Figma copy stays visible next to the layout it belongs to. The locale is Compose
 * state, so switching language re-renders every window immediately.
 */
object Lang {
    const val ZH = "zh-CN"
    const val EN = "en-US"

    var locale: String by mutableStateOf(ZH)
        private set

    val isZh: Boolean get() = locale == ZH

    fun set(value: String) {
        locale = if (value == EN) EN else ZH
    }

    fun defaultForSystem(): String =
        if (java.util.Locale.getDefault().language.lowercase().startsWith("zh")) ZH else EN
}

fun tr(zh: String, en: String): String = if (Lang.isZh) zh else en
