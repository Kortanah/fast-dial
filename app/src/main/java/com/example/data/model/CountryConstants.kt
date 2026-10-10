package com.example.data.model

import android.content.Context

data class CountryItem(
    val name: String,
    val flagEmoji: String
) {
    val displayName: String get() = "$flagEmoji $name"
}

object CountryConstants {
    val CENTRAL_COUNTRIES = listOf(
        CountryItem("Ghana", "🇬🇭"),
        CountryItem("Kenya", "🇰🇪"),
        CountryItem("Togo", "🇹🇬"),
        CountryItem("Nigeria", "🇳🇬"),
        CountryItem("Global", "🌐")
    )

    val FRANCOPHONE_ISO_CODES = setOf(
        "fr", "ci", "tg", "sn", "bf", "ml", "ne", "bj", "cm", "cd",
        "cg", "ga", "gn", "ht", "mg", "td", "dj", "km", "sc", "vu", "cf", "bi", "rw"
    )

    fun getFlagEmoji(countryName: String): String {
        return CENTRAL_COUNTRIES.find { it.name.equals(countryName, ignoreCase = true) }?.flagEmoji ?: "🌐"
    }

    fun getDisplayName(countryName: String): String {
        if (countryName == "All" || countryName.isBlank()) return "🌐 All"
        val flag = getFlagEmoji(countryName)
        return "$flag $countryName"
    }

    fun isFrancophoneCountry(countryName: String): Boolean {
        val lower = countryName.lowercase()
        return lower.contains("togo") || lower.contains("ivoire") || lower.contains("côte") ||
                lower.contains("cote") || lower.contains("france") || lower.contains("senegal") ||
                lower.contains("benin") || lower.contains("burkina") || lower.contains("mali") ||
                lower.contains("niger") || lower.contains("cameroon") || lower.contains("cameroun") ||
                lower.contains("congo") || lower.contains("gabon") || lower.contains("guinea") || lower.contains("guinée")
    }

    fun detectDeviceLanguage(appContext: Context? = null): String {
        // Only inspect the device's system language preference
        val sysLocale = java.util.Locale.getDefault()
        val lang = sysLocale.language.lowercase()
        if (lang.startsWith("fr")) {
            return "French"
        }
        return "English"
    }

    fun detectDeviceCountry(appContext: Context? = null): String {
        val sysLocale = java.util.Locale.getDefault()
        val countryIso = sysLocale.country.uppercase()

        var telephonyIso = ""
        if (appContext != null) {
            try {
                val tm = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
                telephonyIso = (tm?.simCountryIso ?: tm?.networkCountryIso ?: "").uppercase()
            } catch (_: Exception) {}
        }
        val iso = if (telephonyIso.isNotBlank()) telephonyIso else countryIso

        return when (iso) {
            "GH" -> "Ghana"
            "KE" -> "Kenya"
            "TG" -> "Togo"
            "NG" -> "Nigeria"
            "FR", "SN", "BF", "ML", "NE", "BJ", "CM", "CD", "CG", "GA", "GN" -> "Togo"
            else -> "Ghana"
        }
    }
}

