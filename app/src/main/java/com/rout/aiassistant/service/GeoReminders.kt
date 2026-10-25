package com.rout.aiassistant.service

/** Pure parsing for location-based reminders ("geofencing") - unit-testable, no Android deps. */
object GeoReminders {

    data class Request(val task: String, val place: String, val onExit: Boolean, val useCurrentLocation: Boolean)

    private val CURRENT_MARKERS = setOf(
        "yahan", "yahaan", "yahin", "here", "this place", "current location", "is jagah", "iss jagah", "is jagah par"
    )

    /**
     * English: "remind me to buy milk when I reach the market" / "...when I leave office"
     * Hinglish: "jab main ghar pahunchun to doodh yaad dilana" / "office se nikalte hi yaad dila dena ki ..."
     */
    fun parse(t0: String): Request? {
        val t = t0.trim().lowercase()

        Regex("remind\\s*me\\s*(?:to)?\\s*(.+?)\\s+when\\s*(?:i|i'm|i am)\\s*(reach|arrive\\s*at|arrive|get\\s*to|leave|exit)\\s*(.+)$")
            .find(t)?.let { m ->
                val exit = m.groupValues[2].startsWith("leave") || m.groupValues[2].startsWith("exit")
                return build(m.groupValues[1], m.groupValues[3], exit)
            }

        // "jab main <place> pahunchun/niklun ... yaad dila ..."
        Regex("(?:yaad\\s*dila\\w*)?\\s*jab\\s*(?:main\\s*)?(.+?)\\s*(pahunch\\w*|pohanch\\w*|nikal\\w*|nikl\\w*|chhod\\w*|chod\\w*)[,\\s]+(?:to\\s*)?(?:mujhe\\s*)?(?:ye\\s*)?(.+?)\\s*yaad\\s*dila\\w*(?:\\s+\\w+)?\\s*$")
            .find(t)?.let { m ->
                val exit = m.groupValues[2].startsWith("nik") || m.groupValues[2].startsWith("chh") || m.groupValues[2].startsWith("cho")
                return build(m.groupValues[3], m.groupValues[1], exit)
            }

        // "<place> pahunchte/nikalte hi <task> yaad dilana"
        Regex("(.+?)\\s*(?:par|pe|se|mein)?\\s*(pahunchte|pohanchte|nikalte|chhodte|chodte)\\s*hi[,\\s]+(.+?)\\s*yaad\\s*dila\\w*(?:\\s+\\w+)?\\s*$")
            .find(t)?.let { m ->
                val exit = m.groupValues[2].startsWith("nik") || m.groupValues[2].startsWith("chh") || m.groupValues[2].startsWith("cho")
                return build(m.groupValues[3], m.groupValues[1], exit)
            }

        // "yaad dilana <task> jab main <place> pahunchun"
        Regex("yaad\\s*dila\\w*\\s+(?:mujhe\\s*)?(.+?)\\s+jab\\s*(?:main\\s*)?(.+?)\\s*(pahunch\\w*|pohanch\\w*|nikal\\w*|nikl\\w*)$")
            .find(t)?.let { m ->
                val exit = m.groupValues[3].startsWith("nik")
                return build(m.groupValues[1], m.groupValues[2], exit)
            }
        // "<place> nikalte/pahunchte hi yaad dila dena (ki) <task>"
        Regex("(.+?)\\s*(?:se|par|pe)?\\s*(pahunchte|pohanchte|nikalte|chhodte|chodte)\\s*hi[,\\s]+yaad\\s*dila\\w*\\s+(?:ki\\s+)?(.+)$")
            .find(t)?.let { m ->
                val exit = m.groupValues[2].startsWith("nik") || m.groupValues[2].startsWith("chh") || m.groupValues[2].startsWith("cho")
                return build(m.groupValues[3], m.groupValues[1], exit)
            }
        return null
    }

    private fun build(task0: String, place0: String, exit: Boolean): Request? {
        val task = task0.trim().trim(',', ' ', ':').removePrefix("ki ").removePrefix("to ").trim()
        var place = place0.trim().trim('.', ' ', ',').removePrefix("the ").removePrefix("mera ").removePrefix("mere ").trim()
        if (task.isBlank() || place.isBlank()) return null
        val current = place in CURRENT_MARKERS || place.endsWith("yahan") || place.endsWith("here")
        if (current) place = "yahan"
        return Request(task, place, exit, current)
    }
}
