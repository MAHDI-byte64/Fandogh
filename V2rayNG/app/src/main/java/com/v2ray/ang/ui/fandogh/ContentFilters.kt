package com.v2ray.ang.ui.fandogh

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.handler.MmkvManager

/**
 * Blocking and bypass rules, expressed as Xray routing rules.
 *
 * These deliberately live in the core's routing table rather than in the app: a rule
 * here applies to every app on the device whose traffic enters the tunnel, not just to
 * a browser, and it costs nothing at runtime because the core is already matching every
 * connection against this table.
 *
 * Each filter owns a ruleset identified by a stable id, so toggling one off removes
 * exactly its own rule and leaves any rules the user added by hand untouched.
 */
object ContentFilters {

    /** Domain lists shipped with the core's geosite database. */
    enum class Filter(
        val id: String,
        val remarks: String,
        val domains: List<String>,
        val outboundTag: String,
        val requiresIranRules: Boolean = false
    ) {
        Ads(
            id = "fandogh-block-ads",
            remarks = "Fandogh · Ads",
            domains = listOf("geosite:category-ads-all"),
            outboundTag = AppConfig.TAG_BLOCKED
        ),
        Trackers(
            id = "fandogh-block-trackers",
            remarks = "Fandogh · Trackers",
            // category-ads-all already covers most trackers; these add the analytics
            // endpoints that are not strictly advertising.
            domains = listOf("domain:google-analytics.com", "domain:doubleclick.net", "domain:scorecardresearch.com", "domain:hotjar.com", "domain:mixpanel.com", "domain:segment.io", "domain:branch.io", "domain:appsflyer.com", "domain:adjust.com"),
            outboundTag = AppConfig.TAG_BLOCKED
        ),
        Malware(
            id = "fandogh-block-malware",
            remarks = "Fandogh · Malware and phishing",
            // These categories exist only in the Iran rules database. Referencing a
            // category the loaded geosite file does not contain makes the core reject
            // the whole config, so the rule is only written when that database is the
            // selected source — see [securityCategoriesAvailable].
            domains = listOf("geosite:malware", "geosite:phishing", "geosite:cryptominers"),
            outboundTag = AppConfig.TAG_BLOCKED,
            requiresIranRules = true
        ),
        DirectIran(
            id = "fandogh-direct-ir",
            remarks = "Fandogh · Iranian sites direct",
            // domain:ir needs no database at all, so this still does something useful
            // when the Iran rules are not installed; the category is added on top when
            // it is available.
            domains = listOf("domain:ir"),
            outboundTag = AppConfig.TAG_DIRECT
        )
    }

    data class State(
        val blockAds: Boolean = false,
        val blockTrackers: Boolean = false,
        val blockMalware: Boolean = false,
        val directIran: Boolean = false
    )

    private const val IRAN_RULES_SOURCE = "Chocolate4U/Iran-v2ray-rules"

    /**
     * Whether the selected geosite database carries the security and Iran categories.
     *
     * Xray fails to start on a rule naming a category its database does not define, so
     * this gates every rule that depends on one.
     */
    fun securityCategoriesAvailable(): Boolean =
        MmkvManager.decodeSettingsString(AppConfig.PREF_GEO_FILES_SOURCES) == IRAN_RULES_SOURCE

    /** Points the asset downloader at the database that defines those categories. */
    fun selectIranRulesSource() {
        MmkvManager.encodeSettings(AppConfig.PREF_GEO_FILES_SOURCES, IRAN_RULES_SOURCE)
    }

    fun read(): State {
        val ids = MmkvManager.decodeRoutingRulesets()
            .orEmpty()
            .filter { it.enabled }
            .mapTo(HashSet()) { it.id }
        return State(
            blockAds = Filter.Ads.id in ids,
            blockTrackers = Filter.Trackers.id in ids,
            blockMalware = Filter.Malware.id in ids,
            directIran = Filter.DirectIran.id in ids
        )
    }

    /**
     * Adds or removes one filter's rule.
     *
     * Blocking rules are inserted at the front: routing takes the first match, so a
     * block placed after a broad proxy rule would never be reached. The direct-traffic
     * rule goes in front for the same reason.
     */
    fun set(filter: Filter, enabled: Boolean): Boolean {
        if (enabled && filter.requiresIranRules && !securityCategoriesAvailable()) return false

        val rules = MmkvManager.decodeRoutingRulesets() ?: mutableListOf()
        rules.removeAll { it.id == filter.id }
        if (enabled) {
            val domains = filter.domains + when {
                filter == Filter.DirectIran && securityCategoriesAvailable() ->
                    listOf("geosite:category-ir")

                else -> emptyList()
            }
            rules.add(
                0,
                RulesetItem(
                    id = filter.id,
                    remarks = filter.remarks,
                    domain = domains,
                    outboundTag = filter.outboundTag,
                    enabled = true
                )
            )
        }
        MmkvManager.encodeRoutingRulesets(rules)
        return true
    }
}
