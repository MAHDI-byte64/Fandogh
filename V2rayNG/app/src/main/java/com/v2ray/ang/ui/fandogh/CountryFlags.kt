package com.v2ray.ang.ui.fandogh

/**
 * Works out which flag belongs to a server, from its name alone.
 *
 * Panels name servers however their operator likes — "Germany 🇩🇪", "آلمان", "DE-01",
 * "tunnel [irancell]". Three sources are tried in order of how much they can be trusted:
 * a flag the operator typed themselves, a country name spelled out, then a bare country
 * code. Anything left over has no country to show and the caller falls back to a globe,
 * which is honest rather than guessing wrong.
 */
object CountryFlags {

    /** Turns "DE" into 🇩🇪 by mapping letters onto regional indicator symbols. */
    private fun codeToFlag(code: String): String? {
        if (code.length != 2) return null
        val a = code[0].uppercaseChar()
        val b = code[1].uppercaseChar()
        if (a !in 'A'..'Z' || b !in 'A'..'Z') return null
        val base = 0x1F1E6
        return String(Character.toChars(base + (a - 'A'))) +
                String(Character.toChars(base + (b - 'A')))
    }

    /**
     * A flag already present in the name.
     *
     * Regional indicators only carry meaning in pairs, so a single stray one is ignored
     * rather than rendered as a lone letter tile.
     */
    private fun existingFlag(name: String): String? {
        val points = name.codePoints().toArray()
        for (i in 0 until points.size - 1) {
            val first = points[i]
            val second = points[i + 1]
            if (first in 0x1F1E6..0x1F1FF && second in 0x1F1E6..0x1F1FF) {
                return String(Character.toChars(first)) + String(Character.toChars(second))
            }
        }
        return null
    }

    // Spelled-out names, English and Persian. Longer names are matched first so
    // "South Korea" is not swallowed by a shorter entry.
    private val NAMES: List<Pair<String, String>> = listOf(
        "united states" to "US", "united kingdom" to "GB", "south korea" to "KR",
        "south africa" to "ZA", "hong kong" to "HK", "new zealand" to "NZ",
        "netherlands" to "NL", "switzerland" to "CH", "kazakhstan" to "KZ",
        "azerbaijan" to "AZ", "afghanistan" to "AF", "luxembourg" to "LU",
        "singapore" to "SG", "australia" to "AU", "argentina" to "AR",
        "indonesia" to "ID", "lithuania" to "LT", "armenia" to "AM",
        "bulgaria" to "BG", "cambodia" to "KH", "denmark" to "DK",
        "estonia" to "EE", "ethiopia" to "ET", "germany" to "DE",
        "hungary" to "HU", "malaysia" to "MY", "moldova" to "MD",
        "pakistan" to "PK", "portugal" to "PT", "romania" to "RO",
        "slovakia" to "SK", "slovenia" to "SI", "thailand" to "TH",
        "ukraine" to "UA", "vietnam" to "VN", "belgium" to "BE",
        "bahrain" to "BH", "belarus" to "BY", "croatia" to "HR",
        "czechia" to "CZ", "czech" to "CZ", "england" to "GB",
        "finland" to "FI", "georgia" to "GE", "iceland" to "IS",
        "ireland" to "IE", "morocco" to "MA", "nigeria" to "NG",
        "albania" to "AL", "algeria" to "DZ", "austria" to "AT",
        "bahamas" to "BS", "canada" to "CA", "cyprus" to "CY",
        "france" to "FR", "greece" to "GR", "iceland" to "IS",
        "jordan" to "JO", "kuwait" to "KW", "latvia" to "LV",
        "mexico" to "MX", "norway" to "NO", "poland" to "PL",
        "russia" to "RU", "serbia" to "RS", "sweden" to "SE",
        "taiwan" to "TW", "turkey" to "TR", "türkiye" to "TR",
        "brazil" to "BR", "cyprus" to "CY", "israel" to "IL",
        "jersey" to "JE", "kosovo" to "XK", "panama" to "PA",
        "brunei" to "BN", "chile" to "CL", "china" to "CN",
        "egypt" to "EG", "india" to "IN", "italy" to "IT",
        "japan" to "JP", "qatar" to "QA", "spain" to "ES",
        "sudan" to "SD", "korea" to "KR", "malta" to "MT",
        "nepal" to "NP", "chile" to "CL", "emirates" to "AE",
        "dubai" to "AE", "iran" to "IR", "iraq" to "IQ",
        "oman" to "OM", "peru" to "PE", "saudi" to "SA",
        "uae" to "AE", "usa" to "US", "america" to "US",

        // Persian
        "آلمان" to "DE", "فرانسه" to "FR", "ترکیه" to "TR", "رومانی" to "RO",
        "هلند" to "NL", "آمریکا" to "US", "انگلیس" to "GB", "فنلاند" to "FI",
        "سوئد" to "SE", "لهستان" to "PL", "اتریش" to "AT", "سوئیس" to "CH",
        "کانادا" to "CA", "ژاپن" to "JP", "سنگاپور" to "SG", "امارات" to "AE",
        "ایران" to "IR", "روسیه" to "RU", "هند" to "IN", "ارمنستان" to "AM",
        "گرجستان" to "GE", "آذربایجان" to "AZ", "قزاقستان" to "KZ",
        "قبرس" to "CY", "ایتالیا" to "IT", "اسپانیا" to "ES", "بلژیک" to "BE",
        "دانمارک" to "DK", "نروژ" to "NO", "چک" to "CZ", "مجارستان" to "HU",
        "بلغارستان" to "BG", "اوکراین" to "UA", "استرالیا" to "AU",
        "کره" to "KR", "تایوان" to "TW", "مالزی" to "MY", "اندونزی" to "ID",
        "تایلند" to "TH", "ویتنام" to "VN", "برزیل" to "BR", "مکزیک" to "MX",
        "اسرائیل" to "IL", "قطر" to "QA", "کویت" to "KW", "عربستان" to "SA",
        "عمان" to "OM", "بحرین" to "BH", "عراق" to "IQ", "پاکستان" to "PK",
        "چین" to "CN", "پرتغال" to "PT", "یونان" to "GR", "ایرلند" to "IE",
        "صربستان" to "RS", "کرواسی" to "HR", "استونی" to "EE", "لتونی" to "LV",
        "لیتوانی" to "LT", "مولداوی" to "MD", "بلاروس" to "BY"
    ).sortedByDescending { it.first.length }

    // Codes are matched only as standalone tokens: "IN" inside "INDIA" or "tunnel" must
    // not read as India, and a two-letter word is a weak enough signal already.
    private val CODES: Set<String> = NAMES.map { it.second }.toSet()

    private val TOKEN = Regex("[A-Za-z]{2,}")

    /**
     * The flag emoji for a server name, or null when the name names no country.
     */
    fun forName(name: String): String? {
        existingFlag(name)?.let { return it }

        val lower = name.lowercase()
        NAMES.firstOrNull { lower.contains(it.first) }?.let { return codeToFlag(it.second) }

        TOKEN.findAll(name)
            .map { it.value.uppercase() }
            .firstOrNull { it.length == 2 && it in CODES }
            ?.let { return codeToFlag(it) }

        return null
    }

    /**
     * The name with any flag emoji removed, so a row showing the flag separately does
     * not print it twice.
     */
    fun stripFlag(name: String): String {
        val builder = StringBuilder()
        var i = 0
        val points = name.codePoints().toArray()
        while (i < points.size) {
            val point = points[i]
            if (point in 0x1F1E6..0x1F1FF) {
                i++
                continue
            }
            builder.appendCodePoint(point)
            i++
        }
        return builder.toString().trim().trim('-', '_', '|').trim()
    }
}
