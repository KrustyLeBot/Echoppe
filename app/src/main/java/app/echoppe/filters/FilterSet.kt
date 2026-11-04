package app.echoppe.filters

/** A group of lists (ads, trackers or cookie banners), parsed once and shared by every tab. */
class FilterSet(val network: NetworkFilter, val cosmetics: Cosmetics) {
    companion object {
        fun parse(texts: Iterable<String>): FilterSet {
            val rules = ArrayList<NetworkRule>()
            val hosts = HashSet<String>()
            val cosmetics = Cosmetics()
            for (text in texts) {
                text.lineSequence().forEach { line ->
                    if (cosmetics.add(line)) return@forEach
                    val host = NetworkFilter.plainHost(line)
                    if (host != null) hosts += host else NetworkRule.parse(line)?.let { rules += it }
                }
            }
            return FilterSet(NetworkFilter(rules, hosts), cosmetics)
        }
    }
}
