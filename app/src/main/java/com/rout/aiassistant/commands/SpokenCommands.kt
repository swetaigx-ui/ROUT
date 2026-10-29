package com.rout.aiassistant.commands

/**
 * Pure parsing for spoken (LIVE) commands, run on already-latinized lowercase text.
 * Nothing here touches the phone. It only decides what a phrase means so the router can act.
 */
object SpokenCommands {
    private const val SEARCH_V = "(?:search karo|search kar do|search kar|search|dhundho|dhoondo|dhundh do|dekho|dikhao|chalao|play karo)"
    private const val OPEN_V = "(?:open karo|open kar do|open|kholo|khol do|kholiye)"
    private const val PLACE = "(?:mein|me|par|pe|on|in)"

    private fun clean(q: String): String? {
        var x = q.trim().trim(',', '.', ':', '-', '?', '!').trim()
        x = x.replace(Regex("^(?:karo|for|ke liye|ke baare mein|ka)\\s+"), "")
        x = x.replace(Regex("\\s+(?:search karo|search kar do|search kar|search|dhundho|dhoondo|dekho|dikhao|chalao|play karo|karo|kar do)$"), "")
        x = x.trim().trim(',', '.', ':', '-').trim()
        return x.ifEmpty { null }
    }

    /** The YouTube search words only (never the command words), or null when this is not a YouTube search. */
    fun youtubeQuery(t0: String, youtubeContext: Boolean): String? {
        val t = t0.trim().trimEnd('.', '!', '?')
        Regex("^(?:youtube|yt)\\s+$OPEN_V\\s*(?:aur|and|phir|fir|,)?\\s*(?:$PLACE\\s+)?$SEARCH_V\\s*[:,]?\\s+(.+)$").find(t)?.let { return clean(it.groupValues[1]) }
        Regex("^(?:youtube|yt)\\s+$OPEN_V\\s*(?:aur|and|phir|fir|,)\\s*(.+?)\\s+$SEARCH_V$").find(t)?.let { return clean(it.groupValues[1]) }
        Regex("^open youtube\\s*(?:and|aur)\\s*search\\s+(?:for\\s+)?(.+)$").find(t)?.let { return clean(it.groupValues[1]) }
        Regex("^(?:youtube|yt)\\s*$PLACE\\s+(.+?)\\s+$SEARCH_V$").find(t)?.let { return clean(it.groupValues[1]) }
        Regex("^(?:youtube|yt)\\s*(?:$PLACE\\s+)?$SEARCH_V\\s*[:,]?\\s+(.+)$").find(t)?.let { return clean(it.groupValues[1]) }
        Regex("^search\\s+(?:for\\s+)?(.+?)\\s+(?:on|in)\\s+youtube$").find(t)?.let { return clean(it.groupValues[1]) }
        Regex("^(.+?)\\s+(?:youtube)\\s*$PLACE\\s+$SEARCH_V$").find(t)?.let { return clean(it.groupValues[1]) }
        if (youtubeContext) {
            Regex("^(?:search karo|search kar do|search|dhundho|dhoondo)\\s*[:,]?\\s+(.+)$").find(t)?.let { return clean(it.groupValues[1]) }
            Regex("^(.+?)\\s+(?:search karo|search kar do|search|dhundho|dhoondo)$").find(t)?.let { return clean(it.groupValues[1]) }
        }
        return null
    }

    private val NOT_A_MESSAGE = setOf("message", "msg", "sms", "mail", "email", "photo", "pic", "image", "video", "location", "file", "document", "link", "kuch", "voice", "audio", "whatsapp", "wa", "call")

    /** "<name> ko hi bhejo" style: (name, exact words). The user spoke both the recipient and the words in this one sentence. */
    fun whatsappDirect(t0: String): Pair<String, String>? {
        val t = t0.trim().trimEnd('.', '!', '?')
        val verbs = "(?:likh kar bhejo|likh ke bhejo|likh kar send karo|likh ke send karo|likh kar send kar do|likh ke send kar do|likh kar bhej do|likh ke bhej do|bhejo|bhej do|send karo|send kar do|likh do|likho)"
        val m = Regex("^(?:whatsapp\\s+(?:par|pe|mein)\\s+)?(.+?)\\s+ko\\s+(?:whatsapp\\s+(?:par\\s+|pe\\s+|mein\\s+)?)?(.+?)\\s+$verbs$").find(t) ?: return null
        val name = m.groupValues[1].trim()
        val body = m.groupValues[2].trim().trim(',', ':', '"')
        if (name.isEmpty() || name.split(Regex("\\s+")).size > 4 || body.isEmpty() || body.length > 300) return null
        if (body in NOT_A_MESSAGE || body.split(Regex("\\s+")).all { it in NOT_A_MESSAGE }) return null
        if (Regex("\\b(?:call|sms|mail|email|reel|story|status)\\b").containsMatchIn(name)) return null
        return name to body
    }

    fun isStatusView(t: String) = Regex("^(?:mera |meri |my )?status\\s+(?:play karo|play kar do|play|dekho|dikhao|chalao|kholo|khol do|dekhna hai|open karo)$").matches(t.trim().trimEnd('.', '!'))
    fun isStatusPost(t: String) = Regex("^(?:mera |meri |my )?status\\s+(?:lagao|laga do|post karo|post kar do|daalo|dalo|upload karo|lagana hai)$").matches(t.trim().trimEnd('.', '!'))
    fun isLike(t: String) = Regex("^(?:(?:is |isse |is reel ko |ye |reels? |reel |video )*like\\s*(?:karo|kar do|kardo|kar de)?|like it)$").matches(t.trim().trimEnd('.', '!'))

    /** Rewrite loose spoken phrasing into the canonical forms the screen-control parser already understands. Null = leave as is. */
    fun rewrite(t0: String): String? {
        val t = t0.trim().trimEnd('.', '!', '?')
        Regex("^(?:(.+?)\\s+(?:par|pe|ko)\\s+(?:click|tap)(?:\\s+karo|\\s+kar do)?|(?:click|tap)\\s+karo\\s+(.+))$").find(t)?.let {
            val x = (it.groupValues[1].ifEmpty { it.groupValues[2] }).trim()
            if (x.isNotEmpty()) return "click $x"
        }
        if (Regex("^(?:upar scroll karo|scroll upar(?: karo)?|upar karo|pichli reel|previous reel|pichla video|previous video)$").matches(t)) return "scroll up"
        if (Regex("^(?:scroll karo|scroll kar do|scroll|neeche scroll karo|scroll neeche(?: karo)?|next reel|agli reel|next video|agla video|aage badho)$").matches(t)) return "scroll down"
        return null
    }

    /** True when a phrase plainly asks the phone to do something, so a silent drop by the name gate can be explained. */
    fun looksLikeCommand(latinized: String): Boolean {
        val t = latinized.lowercase()
        return Regex("\\b(?:open|kholo|khol do|search karo|search|click|scroll|like karo|like|bhejo|bhej do|send karo|flashlight|torch|wifi|bluetooth|volume|brightness|status|reels|youtube|instagram|whatsapp|call karo|screenshot|home jao|back)\\b").containsMatchIn(t)
    }
}
