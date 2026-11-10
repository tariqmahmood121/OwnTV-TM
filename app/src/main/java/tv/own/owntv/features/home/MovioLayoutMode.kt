/* MovioTV — universal APK layout selection. GPL-3.0; Created by ZIGGS. */
package tv.own.owntv.features.home

import androidx.compose.runtime.compositionLocalOf

enum class MovioLayoutChoice {
    AUTO, MOBILE, TV;
    companion object {
        fun fromSaved(value: String?): MovioLayoutChoice = entries.firstOrNull { it.name == value } ?: AUTO
    }
}

/** Shared by Home, Movies, Series and Live TV; supplied by OwnTVShell. */
val LocalMovioTouchMode = compositionLocalOf { false }
