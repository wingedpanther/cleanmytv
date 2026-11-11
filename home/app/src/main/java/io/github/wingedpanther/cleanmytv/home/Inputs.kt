package io.github.wingedpanther.cleanmytv.home

/** What the input menu needs from a TvInputInfo, so the choice can be tested without a TV. */
data class InputRow(val id: String, val parentId: String?, val hdmi: Boolean, val hidden: Boolean, val custom: String?, val label: String?, val tuner: Boolean = false)

/**
 * One entry per port, as id to name. HDMI-CEC adds a child input under a port for each device that names
 * itself, like SHIELD (#50): those only lend the port their name. The name set in TV settings comes first,
 * and inputs hidden there stay out. The TV's own tuner comes first (#63), then HDMI ports in port order.
 * Some TVs register a tuner per broadcast type, all called Tuner (#50): only the first is listed, and
 * liveTv off leaves tuners out.
 */
fun pickInputs(rows: List<InputRow>, liveTv: Boolean = true): List<Pair<String, String>> {
    val ids = rows.map { it.id }.toSet()
    val devices = rows.filter { it.parentId != null }.groupBy { it.parentId }
    fun text(s: String?): String? = s?.trim()?.takeIf { it.isNotEmpty() }
    // A child whose port isn't listed stands in for it.
    val shown = rows.filter { (it.parentId == null || it.parentId !in ids) && !it.hidden && (liveTv || !it.tuner) }
        .sortedWith(compareBy({ !it.tuner }, { !it.hdmi }, { portNumber(it.id) }, { it.id }))
    return (shown.filter { it.tuner }.take(1) + shown.filterNot { it.tuner })
        .map { port -> port.id to (text(port.custom) ?: devices[port.id]?.firstNotNullOfOrNull { text(it.label) } ?: text(port.label) ?: if (port.tuner) "Live TV" else "Input") }
}

/** Port ids end in their number on most TVs (…/HW2); others sort after them. */
private fun portNumber(id: String): Int = Regex("(\\d+)$").find(id)?.value?.toIntOrNull() ?: Int.MAX_VALUE
