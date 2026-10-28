package com.rout.aiassistant.commands

/** One reply-capable notification thread, as seen by the listener at this moment. Never stored. */
data class ReplyTarget(
    val key: String,
    val pkg: String,
    val app: String,
    val sender: String,
    val isGroup: Boolean
)

/**
 * Pure rules for replying to a notification. Ships OFF (Prefs.notifReplyAllowed).
 * No thread is guessed: exactly one 1:1 thread whose sender matches, else nothing happens.
 */
object NotificationReply {
    sealed class Pick {
        data class One(val t: ReplyTarget) : Pick()
        data class Many(val ts: List<ReplyTarget>) : Pick()
        object None : Pick()
        object GroupOnly : Pick()
    }

    private val SEP = Regex("^(?:notification\\s+)?(?:reply|jawab|jawaab)\\s+(?:to\\s+)?([^:]{1,40}):\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val KO = Regex("^([^:]{1,40}?)\\s+(?:ko|ke message ka|ki notification ka|ki notification par|ki notification pe)\\s+(?:notification\\s+)?(?:reply|jawab|jawaab)\\s+(?:karo|bhejo|do)\\s*[:,]?\\s*(.+)$", RegexOption.IGNORE_CASE)

    /** (name, words) or null when either part is missing. */
    fun parse(text: String): Pair<String, String>? {
        val t = text.trim()
        val m = SEP.find(t) ?: KO.find(t) ?: return null
        val name = m.groupValues[1].trim(); val words = m.groupValues[2].trim()
        return if (name.isBlank() || words.isBlank()) null else name to words
    }

    fun pick(name: String, targets: List<ReplyTarget>): Pick {
        val n = name.trim().lowercase()
        val hits = targets.filter { it.sender.lowercase().let { s -> s == n || s.split(" ").firstOrNull() == n } }
        if (hits.isEmpty()) return Pick.None
        val solo = hits.filter { !it.isGroup }
        return when {
            solo.isEmpty() -> Pick.GroupOnly
            solo.size == 1 -> Pick.One(solo[0])
            else -> Pick.Many(solo)
        }
    }

    /** Short stable label of the exact thread key, shown so two chats with one name cannot look identical. */
    fun shortId(key: String): String = Integer.toHexString(key.hashCode()).takeLast(6)

    /** Same thread, same sender, still 1:1, still reply-capable. */
    fun stillSame(old: ReplyTarget, now: List<ReplyTarget>): ReplyTarget? =
        now.firstOrNull { it.key == old.key && it.pkg == old.pkg && it.sender == old.sender && !it.isGroup }
}
