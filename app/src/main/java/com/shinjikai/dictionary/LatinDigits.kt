package com.shinjikai.dictionary

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * Forces Western ("Arabic") digits 0-9 everywhere in the app, even on devices set to
 * Arabic, which would otherwise render ٠١٢٣. Adds the Unicode `nu=latn` keyword to every
 * configured locale, so String.format, stringResource(%d), DateFormat and NumberFormat
 * all produce Latin digits while keeping the language itself unchanged.
 */
internal object LatinDigits {
    fun withLatinDigits(locale: Locale): Locale =
        Locale.Builder()
            .setLocale(locale)
            .setUnicodeLocaleKeyword("nu", "latn")
            .build()

    fun wrap(base: Context): Context {
        val current = base.resources.configuration.locales
        val patched = Array(current.size()) { index -> withLatinDigits(current[index]) }
        val localeList = LocaleList(*patched)
        Locale.setDefault(localeList[0])
        val config = Configuration(base.resources.configuration).apply { setLocales(localeList) }
        return base.createConfigurationContext(config)
    }
}
