package com.example.data.model

data class PackageItem(
    val id: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val currency: String = "USD",
    val durationValue: Long = 0L,
    val durationUnit: String = "months", // "minutes" or "months"
    val active: Boolean = true,
    val description: String = ""
) {
    /**
     * Formatted string representation of the duration (e.g., "15 Minutes", "1 Month", "6 Months").
     */
    val formattedDuration: String
        get() = when (durationUnit.lowercase()) {
            "minutes", "minute", "mins", "min" -> "$durationValue Minute${if (durationValue != 1L) "s" else ""}"
            "months", "month", "m" -> "$durationValue Month${if (durationValue != 1L) "s" else ""}"
            else -> "$durationValue $durationUnit"
        }

    /**
     * Formatted string representation of the price with currency symbol or code.
     */
    val formattedPrice: String
        get() = if (currency.length == 1 || currency.startsWith("$") || currency.startsWith("€") || currency.startsWith("£") || currency.startsWith("₵")) {
            "$currency$price"
        } else {
            "$price $currency"
        }
}

data class PackageConfig(
    val currency: String = "USD",
    val activePackageId: String = "",
    val packages: List<PackageItem> = emptyList()
)
