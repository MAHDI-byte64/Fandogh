package com.v2ray.ang.ui.fandogh

/**
 * Splits the subscription's servers into the tabs the picker shows.
 *
 * Grouping is derived from the server's own name — the same resolution [CountryFlags]
 * uses for the flag on each row, so a row's flag and the tab it appears under can never
 * disagree. Nothing is stored: rename a server in the panel and it moves tab on the next
 * subscription update.
 */
object ServerTabs {

    /** Metro emoji, the mark operators put on a tunnelled entry. */
    private const val TUNNEL_EMOJI = "🚇"

    enum class Continent(val code: String) {
        Europe("EU"),
        Asia("AS"),
        NorthAmerica("NA"),
        SouthAmerica("SA"),
        Africa("AF"),
        Oceania("OC")
    }

    /**
     * One tab in the picker.
     *
     * [All] and [Tunnel] are cross-cutting: a tunnelled German server appears under All,
     * under Tunnel, and under Europe. Hiding it from Europe would be surprising — it is
     * still a German server — and All exists precisely so nothing can be lost between
     * the narrower tabs.
     */
    sealed interface Tab {
        data object All : Tab
        data object Tunnel : Tab
        data class Region(val continent: Continent) : Tab
    }

    /** Whether a server is one of the operator's tunnelled entries. */
    fun isTunnel(name: String): Boolean =
        name.contains(TUNNEL_EMOJI) || name.contains("tunnel", ignoreCase = true)

    fun continentOf(name: String): Continent? =
        CountryFlags.codeForName(name)?.let { CONTINENTS[it] }

    /**
     * The tabs worth showing for this list, in a stable order.
     *
     * A tab with nothing in it is not offered: an empty Africa tab is a dead end, and
     * which continents a subscription covers is the operator's business, not a fixed
     * list to pad out.
     */
    fun tabsFor(servers: List<PickableServer>): List<Tab> {
        if (servers.isEmpty()) return listOf(Tab.All)

        val tabs = mutableListOf<Tab>(Tab.All)
        if (servers.any { isTunnel(it.name) }) tabs.add(Tab.Tunnel)

        val present = servers.mapNotNullTo(HashSet()) { continentOf(it.name) }
        // Continent.entries order is the display order, so tabs do not reshuffle when
        // the subscription's mix of countries changes.
        Continent.entries.filter { it in present }.mapTo(tabs) { Tab.Region(it) }
        return tabs
    }

    fun filter(servers: List<PickableServer>, tab: Tab): List<PickableServer> = when (tab) {
        Tab.All -> servers
        Tab.Tunnel -> servers.filter { isTunnel(it.name) }
        is Tab.Region -> servers.filter { continentOf(it.name) == tab.continent }
    }

    private val CONTINENTS: Map<String, Continent> = buildMap {
        listOf(
            "AL", "AD", "AT", "BY", "BE", "BA", "BG", "HR", "CY", "CZ", "DK", "EE", "FI",
            "FR", "DE", "GR", "HU", "IS", "IE", "IT", "XK", "LV", "LI", "LT", "LU", "MT",
            "MD", "MC", "ME", "NL", "MK", "NO", "PL", "PT", "RO", "RU", "SM", "RS", "SK",
            "SI", "ES", "SE", "CH", "UA", "GB", "JE", "VA"
        ).forEach { put(it, Continent.Europe) }

        listOf(
            "AF", "AM", "AZ", "BH", "BD", "BT", "BN", "KH", "CN", "GE", "HK", "IN", "ID",
            "IR", "IQ", "IL", "JP", "JO", "KZ", "KW", "KG", "LA", "LB", "MO", "MY", "MV",
            "MN", "MM", "NP", "KP", "OM", "PK", "PS", "PH", "QA", "SA", "SG", "KR", "LK",
            "SY", "TW", "TJ", "TH", "TL", "TR", "TM", "AE", "UZ", "VN", "YE"
        ).forEach { put(it, Continent.Asia) }

        listOf(
            "AG", "BS", "BB", "BZ", "CA", "CR", "CU", "DM", "DO", "SV", "GD", "GT", "HT",
            "HN", "JM", "MX", "NI", "PA", "KN", "LC", "VC", "TT", "US"
        ).forEach { put(it, Continent.NorthAmerica) }

        listOf(
            "AR", "BO", "BR", "CL", "CO", "EC", "GY", "PY", "PE", "SR", "UY", "VE"
        ).forEach { put(it, Continent.SouthAmerica) }

        listOf(
            "DZ", "AO", "BJ", "BW", "BF", "BI", "CM", "CV", "CF", "TD", "KM", "CD", "CG",
            "CI", "DJ", "EG", "GQ", "ER", "ET", "GA", "GM", "GH", "GN", "GW", "KE", "LS",
            "LR", "LY", "MG", "MW", "ML", "MR", "MU", "MA", "MZ", "NA", "NE", "NG", "RW",
            "SN", "SC", "SL", "SO", "ZA", "SS", "SD", "TZ", "TG", "TN", "UG", "ZM", "ZW"
        ).forEach { put(it, Continent.Africa) }

        listOf(
            "AU", "FJ", "KI", "MH", "FM", "NR", "NZ", "PW", "PG", "WS", "SB", "TO", "TV", "VU"
        ).forEach { put(it, Continent.Oceania) }
    }
}
