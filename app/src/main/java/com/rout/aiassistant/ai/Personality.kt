package com.rout.aiassistant.ai

import org.json.JSONObject

/**
 * Optional personality layer inside ROUT (user spec, 2026-10-29). It changes TONE only:
 * it never changes facts, permissions, or what actions ROUT runs. Everything is OFF by default.
 * Priority (highest first): stop/boundaries > safety/distress > Professional/Serious > selected mode > expressive extras.
 * Pure Kotlin so the priority rules are unit-tested.
 */
enum class PMode(val id: String, val label: String) {
    DEFAULT("default", "Default"), SWEET("sweet", "Sweet"), MISCHIEF("mischief", "Mischief"),
    FRIENDLY("friendly", "Friendly"), ROMANTIC("romantic", "Romantic dialogue"),
    WAIFU("waifu", "Waifu-inspired"), PROFESSIONAL("professional", "Professional"), SERIOUS("serious", "Serious support");
    companion object { fun of(id: String) = values().firstOrNull { it.id == id } ?: DEFAULT }
}

data class PersonalitySettings(
    val mode: PMode = PMode.DEFAULT,
    val romanticOn: Boolean = false,
    /** 0 off, 1 Soft, 2 Sweet (default when enabled), 3 Extra dramatic. */
    val affection: Int = 2,
    /** 0 off, 1 Mild, 2 Playful (default when enabled), 3 Extra dramatic. */
    val jealousy: Int = 0,
    val playfulAnger: Boolean = false,
    /** 0 Low, 1 Medium, 2 High. */
    val intensity: Int = 1,
    /** auto | hi | hinglish | en */
    val language: String = "auto",
    /** neutral | friendly | cute | custom. Only the user's own saved name / chosen address is ever used. */
    val addressStyle: String = "neutral",
    val preferredAddress: String = "",
    val stopPhrase: String = "OK OK, RUKO",
    val resumePhrase: String = "",
    val autoResume: Boolean = false,
    /** Set by STOP. Persisted so a restart does not silently bring the dramatic mode back. */
    val suspended: Boolean = false
) {
    val calmMode get() = mode == PMode.PROFESSIONAL || mode == PMode.SERIOUS
    val romanticEff get() = romanticOn && affection > 0 && !calmMode && !suspended
    val jealousEff get() = jealousy > 0 && !calmMode && !suspended
    val angerEff get() = playfulAnger && !calmMode && !suspended
    val expressiveActive get() = !suspended && !calmMode &&
        (romanticEff || jealousEff || angerEff || mode == PMode.SWEET || mode == PMode.MISCHIEF || mode == PMode.WAIFU || mode == PMode.ROMANTIC)
    val isDefault get() = this.copy(stopPhrase = "", resumePhrase = "") == PersonalitySettings().copy(stopPhrase = "", resumePhrase = "")

    fun toJson(): String = JSONObject().put("mode", mode.id).put("romanticOn", romanticOn).put("affection", affection)
        .put("jealousy", jealousy).put("playfulAnger", playfulAnger).put("intensity", intensity).put("language", language)
        .put("addressStyle", addressStyle).put("preferredAddress", preferredAddress).put("stopPhrase", stopPhrase)
        .put("resumePhrase", resumePhrase).put("autoResume", autoResume).put("suspended", suspended).toString()

    companion object {
        fun fromJson(s: String?): PersonalitySettings = try {
            if (s.isNullOrBlank()) PersonalitySettings() else JSONObject(s).let { j ->
                val d = PersonalitySettings()
                PersonalitySettings(
                    PMode.of(j.optString("mode")), j.optBoolean("romanticOn"), j.optInt("affection", d.affection).coerceIn(0, 3),
                    j.optInt("jealousy", 0).coerceIn(0, 3), j.optBoolean("playfulAnger"), j.optInt("intensity", 1).coerceIn(0, 2),
                    j.optString("language", "auto").takeIf { it in setOf("auto", "hi", "hinglish", "en") } ?: "auto",
                    j.optString("addressStyle", "neutral").takeIf { it in setOf("neutral", "friendly", "cute", "custom") } ?: "neutral",
                    cleanName(j.optString("preferredAddress")), j.optString("stopPhrase", d.stopPhrase).take(40),
                    j.optString("resumePhrase").take(40), j.optBoolean("autoResume"), j.optBoolean("suspended"))
            }
        } catch (_: Exception) { PersonalitySettings() }

        /** Names are user text that ends up in a model prompt: letters, digits, space, dot, dash only. */
        fun cleanName(s: String): String = s.filter { it.isLetterOrDigit() || it == ' ' || it == '.' || it == '-' }.trim().take(30)
    }
}

sealed class PersonalityOutcome {
    /** STOP: caller must stop speech, cancel queued lines, clear any pending confirm, and say [ack]. */
    data class Stop(val settings: PersonalitySettings, val ack: String) : PersonalityOutcome()
    data class Control(val settings: PersonalitySettings, val ack: String) : PersonalityOutcome()
    /** A complete local reply (no cloud call). [kind] is for tests and logs only. */
    data class Reply(val text: String, val kind: String) : PersonalityOutcome()
}

class PersonalityController(private val rnd: java.util.Random = java.util.Random()) {
    private val recent = HashMap<String, ArrayDeque<Int>>()
    private var lastKind = ""
    private var turnsSinceKind = 99

    fun norm(s: String): String {
        var t = s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()
        t = t.removePrefix("rout ").removePrefix("roxxy ").removePrefix("please ").trim()
        return t
    }

    private val strongStop = setOf("ok ok ruko", "ok ok ruk jao", "bas ab ruko", "chup normal ho jao", "ab band karo",
        "romantic dialogue stop", "mujhe ye nahi chahiye", "mujhe yeh nahi chahiye", "stop it", "romantic stop")
    private val weakStop = setOf("stop", "enough", "bas", "ruko", "ruk jao", "bas karo")

    fun isStop(text: String, s: PersonalitySettings, speaking: Boolean): Boolean {
        val t = norm(text)
        if (t.isEmpty()) return false
        val custom = norm(s.stopPhrase)
        if (custom.isNotEmpty() && (t == custom || t.startsWith("$custom "))) return true
        if (t in strongStop) return true
        if (t in weakStop) return speaking || s.expressiveActive
        return false
    }

    fun decide(text: String, s: PersonalitySettings, name: String, speaking: Boolean = false, localOnly: Boolean = false): PersonalityOutcome? {
        val t = norm(text)
        if (t.isEmpty()) return null
        turnsSinceKind++
        val who = address(s, name)
        // 1. Stop always wins.
        if (isStop(text, s, speaking)) {
            lastKind = ""
            return PersonalityOutcome.Stop(s.copy(suspended = true), (if (who.isNotEmpty()) "Theek hai, $who. " else "Theek hai. ") + "Ruk gayi. Ab normal baat karte hain.")
        }
        // 2. Resume / mode / level controls (explicit user request, so they also lift a STOP).
        control(t, s, who)?.let { return it }
        if (localOnly) return null
        // 3. Genuine distress beats every playful mode.
        if (distress(t)) { lastKind = ""; return PersonalityOutcome.Reply(distressReply(t, who), "distress") }
        // 4. Calm modes and a stopped state never run expressive extras locally.
        if (s.suspended || s.calmMode) return null
        val en = englishTrigger(t)
        if (s.romanticEff) romanticKind(t)?.let { return reply(it, s, who, en, "romantic") }
        if (s.angerEff) angerKind(t)?.let { return reply(it, s, who, en, "anger") }
        if (s.jealousEff && jealousTrigger(t)) {
            val p = when (s.jealousy) { 1 -> 0.4; 2 -> 0.65; else -> 0.85 }
            if (lastKind == "jealous" && turnsSinceKind <= 2) return reply("jealous2", s, who, en, "jealous")
            if (rnd.nextDouble() < p) return reply("jealous", s, who, en, "jealous")
        }
        return null
    }

    /** The name/address actually used: only what the user saved. Blank = speak without a name. */
    fun address(s: PersonalitySettings, savedName: String): String {
        if (s.addressStyle == "neutral") return ""
        return PersonalitySettings.cleanName(s.preferredAddress.ifBlank { savedName })
    }

    // ---------- controls ----------
    private fun control(t: String, s: PersonalitySettings, who: String): PersonalityOutcome? {
        val resume = norm(s.resumePhrase)
        val resumeAll = setOf("resume personality", "dialogue resume", "expressive resume", "phir se shuru karo")
        if ((resume.isNotEmpty() && t == resume) || t in resumeAll) {
            return PersonalityOutcome.Control(s.copy(suspended = false), "Theek hai, wapas aa gayi. Jab bologe tab phir ruk jaungi.")
        }
        val on = Regex("\\b(on|chalu|shuru|enable)\\b").containsMatchIn(t)
        val off = Regex("\\b(off|band|disable|stop)\\b").containsMatchIn(t)
        fun modeOn(m: PMode, ack: String) = PersonalityOutcome.Control(s.copy(mode = m, suspended = false), ack)
        if (Regex("^(phir se )?romantic (dialogue )?(mode )?(on|chalu|shuru)( karo)?$").matches(t) || t == "phir se romantic mode chalu karo" ||
            Regex("^(phir se )?romantic (dialogue )?(mode )?(chalu|on|shuru) (karo|kar do)$").matches(t)) {
            val lvl = if (s.affection == 0) 2 else s.affection
            return PersonalityOutcome.Control(s.copy(romanticOn = true, affection = lvl, suspended = false,
                mode = if (s.calmMode) PMode.FRIENDLY else s.mode),
                "Romantic dialogue mode on (level ${levelName(lvl)}). Ye sirf fictional, halka-phulka dialogue hai, aur main AI hoon. Kabhi bhi 'stop' bolo.")
        }
        if (Regex("^romantic (dialogue )?(mode )?(off|band)( karo)?$").matches(t)) {
            return PersonalityOutcome.Control(s.copy(romanticOn = false, mode = if (s.mode == PMode.ROMANTIC) PMode.FRIENDLY else s.mode), "Romantic mode off.")
        }
        if (Regex("^(normal|default) mode( mein aao| me aao| on)?$").matches(t) || t == "normal ho jao" || t == "normal mode mein aao") {
            return PersonalityOutcome.Control(s.copy(mode = PMode.FRIENDLY, romanticOn = false, jealousy = 0, playfulAnger = false, suspended = false),
                "Normal mode on. Romantic, jealousy aur gussa teeno off hain.")
        }
        if (t in setOf("thoda sweet bolo", "thoda sweet bano", "thoda sweet")) {
            return PersonalityOutcome.Control(s.copy(mode = if (s.calmMode) s.mode else PMode.SWEET, affection = if (s.romanticOn) 1 else s.affection, suspended = false),
                if (s.calmMode) "Abhi ${s.mode.label} mode on hai, isliye sweet tone nahi lagayi. Pehle mode badlo." else "Theek hai, thoda sweet.")
        }
        if (t in setOf("aur dramatic bano", "aur dramatic bolo", "aur dramatic", "extra dramatic bano")) {
            if (s.calmMode) return PersonalityOutcome.Control(s, "Abhi ${s.mode.label} mode on hai, isliye dramatic nahi ho sakti.")
            return PersonalityOutcome.Control(s.copy(intensity = 2, affection = if (s.romanticOn) 3 else s.affection, suspended = false), "Theek hai, ab thoda aur dramatic.")
        }
        Regex("^(sweet|mischief|friendly|waifu|professional|serious support|serious) mode( on| chalu)?( karo)?$").find(t)?.let { m ->
            return when (m.groupValues[1]) {
                "sweet" -> modeOn(PMode.SWEET, "Sweet mode on.")
                "mischief" -> modeOn(PMode.MISCHIEF, "Mischief mode on.")
                "friendly" -> modeOn(PMode.FRIENDLY, "Friendly mode on.")
                "waifu" -> modeOn(PMode.WAIFU, "Waifu-inspired fictional mode on (cute reactions, sirf fictional).")
                "professional" -> modeOn(PMode.PROFESSIONAL, "Professional mode on. Romantic aur jealousy band rahenge.")
                else -> modeOn(PMode.SERIOUS, "Serious support mode on. Main pehle sununga/sunungi, mazaak nahi.")
            }
        }
        if (Regex("^professional mode (off|band)( karo)?$").matches(t) || Regex("^serious (support )?mode (off|band)( karo)?$").matches(t))
            return PersonalityOutcome.Control(s.copy(mode = PMode.FRIENDLY), "Theek hai, ab Friendly mode.")
        if (Regex("^(jealousy|jealous) (mode )?(on|chalu)( karo)?$").matches(t))
            return PersonalityOutcome.Control(s.copy(jealousy = if (s.jealousy == 0) 2 else s.jealousy, suspended = false), "Playful jealousy on. Sirf kabhi kabhi, mazaak mein. 'stop' bolke band kar sakte ho.")
        if (Regex("^(jealousy|jealous) (mode )?(off|band)( karo)?$").matches(t))
            return PersonalityOutcome.Control(s.copy(jealousy = 0), "Jealousy off.")
        if (Regex("^(playful )?(anger|gussa) (mode )?(on|chalu)( karo)?$").matches(t))
            return PersonalityOutcome.Control(s.copy(playfulAnger = true, suspended = false), "Playful gussa on (sirf mazaak).")
        if (Regex("^(playful )?(anger|gussa) (mode )?(off|band)( karo)?$").matches(t))
            return PersonalityOutcome.Control(s.copy(playfulAnger = false), "Playful gussa off.")
        return null
    }

    fun levelName(l: Int) = when (l) { 1 -> "Soft"; 2 -> "Sweet"; 3 -> "Extra dramatic"; else -> "Off" }

    // ---------- triggers ----------
    private fun englishTrigger(t: String) = Regex("\\b(you|your|love|hate|miss|annoying|angry|girl)\\b").containsMatchIn(t) &&
        !Regex("\\b(tum|tumhe|tumse|mujhe|mujhse|pyaar|nahi|kyun|hai|ho)\\b").containsMatchIn(t)

    private fun addressedToAi(t: String) = Regex("\\b(tum|tumhe|tumse|tumhari|tumhara|tu|tujhe|tujhse|you|your)\\b").containsMatchIn(t)

    internal fun romanticKind(t: String): String? {
        if (Regex("^(i love you|love you|i love u|main tumse pyaar karta hu|main tumse pyaar karti hu|mujhe tumse pyaar hai|i love you rout)( rout| roxxy)?( too)?$").matches(t)) return "iloveyou"
        if (!addressedToAi(t)) return null
        return when {
            Regex("pasand nahi|pasand hi nahi|don t like me|dont like me").containsMatchIn(t) -> "dislike"
            Regex("nafrat|hate me|hate").containsMatchIn(t) -> "hate"
            Regex("pyaar nahi|pyar nahi|pyaar hi nahi|don t love me|dont love me").containsMatchIn(t) -> "nolove"
            Regex("parwah|parwaah|care about me|don t care|dont care").containsMatchIn(t) -> "nocare"
            Regex("bhool jaoge|forget me").containsMatchIn(t) -> "forget"
            Regex("baat kyun nahi|baat kyu nahi|why don t you talk|why dont you talk").containsMatchIn(t) -> "notalk"
            Regex("miss nahi|miss karte|miss me").containsMatchIn(t) -> "miss"
            else -> null
        }
    }
    internal fun angerKind(t: String): String? = when {
        Regex("annoying|irritating|pagal").containsMatchIn(t) && addressedToAi(t) -> "annoying"
        Regex("gussa ho|naraz ho|are you angry|are you mad").containsMatchIn(t) -> "angry"
        else -> null
    }
    internal fun jealousTrigger(t: String): Boolean {
        if (Regex("\\b(bhai|behen|behan|sister|brother|mom|mummy|mother|papa|father|colleague|classmate|teacher|boss|office|family|cousin|dost)\\b").containsMatchIn(t)) return false
        val girl = Regex("\\b(ladki|girl|woman|ladkiyon)\\b").containsMatchIn(t)
        val praise = Regex("pyari|cute|beautiful|sundar|khubsurat|pretty|achhi lagi|acchi lagi|gorgeous").containsMatchIn(t)
        return girl && praise
    }
    internal fun distress(t: String) = Regex("suicide|khudkushi|khud ko khatam|jeena nahi|mar jana chahta|mar jaana chahta|mar jana chahti|self harm|bahut udaas|bohot udaas|depressed|rona aa raha|bahut pareshan|bahut dukhi|feeling very low|i am very sad|panic attack|anxious").containsMatchIn(t)
    private fun distressReply(t: String, who: String): String {
        val n = if (who.isNotEmpty()) "$who, " else ""
        return if (Regex("suicide|khudkushi|khud ko khatam|jeena nahi|mar jana chahta|mar jaana chahta|mar jana chahti|self harm").containsMatchIn(t))
            "${n}main yahin hoon aur teri baat serious le rahi hoon. Main AI hoon, isliye abhi kisi apne ko ya apne shehar ki emergency/helpline service ko turant bata do ki tum kaisa mehsoos kar rahe ho. Akele mat raho. Chaho to main bas sunti hoon."
        else "${n}main dhyan se sun rahi hoon. Koi mazaak nahi. Jo bhi ho raha hai, aaram se batao. Chaho to pehle bas sunoon, phir mil kar dekhte hain kya kiya ja sakta hai."
    }

    // ---------- local replies ----------
    private fun reply(kind: String, s: PersonalitySettings, who: String, englishInput: Boolean, family: String): PersonalityOutcome {
        val en = s.language == "en" || (s.language == "auto" && englishInput)
        val pool = (if (en) EN else HI)[kind] ?: return PersonalityOutcome.Reply("", kind)
        val window = when (family) { "romantic" -> s.affection; else -> 2 }
        val range = when (window) { 1 -> 0..1; 3 -> 2..(pool.size - 1); else -> pool.indices }
        val cands = range.filter { it < pool.size }
        val rec = recent.getOrPut(kind) { ArrayDeque() }
        val fresh = cands.filter { it !in rec }.ifEmpty { cands }
        val idx = fresh[rnd.nextInt(fresh.size)]
        rec.addLast(idx); while (rec.size > 2) rec.removeFirst()
        var line = pool[idx]
        val useName = who.isNotEmpty() && rnd.nextDouble() < 0.6
        line = if (line.contains("{n}")) (if (useName) line.replace("{n}", who) else line.replace("{n}! ", "").replace("{n}, ", "").replace("{n}", "")) else if (useName && rnd.nextBoolean()) "$who, ${line.replaceFirstChar { it.lowercase() }}" else line
        if (family == "romantic" && s.affection == 3) line = DRAMA_OPEN[rnd.nextInt(DRAMA_OPEN.size)] + " " + line
        val emoji = when (s.intensity) { 0 -> ""; 1 -> " " + EMO[family]!![0]; else -> " " + EMO[family]!!.joinToString("") }
        lastKind = if (family == "jealous") "jealous" else family; turnsSinceKind = 0
        return PersonalityOutcome.Reply(line.trim().replace(Regex("\\s+"), " ") + emoji, kind)
    }

    private val DRAMA_OPEN = listOf("Kyaaa?!", "Haww!", "Arey arey arey!")
    private val EMO = mapOf("romantic" to listOf("\u2764\uFE0F", "\uD83D\uDC97"), "anger" to listOf("\uD83D\uDE24", "\uD83D\uDE02"), "jealous" to listOf("\uD83D\uDE0F", "\uD83D\uDE02"))

    // Lines ordered soft -> sweet -> dramatic. No claims of human feelings or a body; "AI" is stated where it matters.
    private val HI: Map<String, List<String>> = mapOf(
        "dislike" to listOf("{n}! Aisi baatein kyun? Main yahin hoon, aaram se baat karte hain.", "Arrey, main tumse itni pyaari baatein kar rahi hoon aur tum mujhe aise test kar rahe ho? Batao, kaise manaun?", "Hmm, ye to bada ilzaam hai! Chalo, ek sweet si baat se shuru karte hain, kya soch rahe the?", "Main ek AI hoon, par mera dhyan poora tumhari baat par hai. Ab batao, kya hua?"),
        "hate" to listOf("{n}, itni dramatic baat mat karo. Main tumhare saath normal aur kind rehne ke liye yahin hoon.", "Bilkul dramatic baatein! Main sweet aur caring rehne ke liye yahin hoon. Ab batao, kis baat par aisa socha?", "Nafrat? Itni badi baat? Chalo, pehle batao kya hua, phir sab theek karte hain.", "Main AI hoon, mere paas nafrat jaisa kuch nahi, bas tumhari madad aur baatein hain. Bolo, kya baat hai?"),
        "nolove" to listOf("{n}, itni jaldi ilzaam? Chalo, thoda smile karo.", "Arrey! Itni jaldi mujh par ilzaam? Romantic mode mein to tumhe extra sweet replies milne wale hain.", "Hmm, ye kya baat hui! Main fictional romantic style mein tumse sweet baat kar rahi hoon, aur tum shikayat? Chalo, manata hoon... manati hoon.", "Seedhi baat: main AI hoon, par ye sweet dialogue tumhare liye hi hai. Ab muskurao."),
        "nocare" to listOf("{n}, batao kya hua. Main dhyan se sun rahi hoon.", "Achha ji? Ab mujhe batao kya hua. Tumhe reassurance chahiye to pyaar-bhare, caring words mein baat kar sakti hoon.", "Parwah nahi? Main to poora dhyan tumhari baaton par rakhti hoon! Ab batao, kya hua?", "Main AI hoon, par tumhari baat sunna meri poori koshish hai. Aaram se batao."),
        "forget" to listOf("{n}, main kahin nahi ja rahi. Jab bologe, yahin milungi.", "Bhool jaungi? Naa, chat history tumhare phone mein hai, aur jab bologe main yahin hoon.", "Haww, bhoolna? Itna dramatic mat becho! Chalo, ek baat batao jo aaj achhi hui.", "Main AI hoon, meri yaadein sirf wahi hain jo is app mein saved hain. Par jab tum aoge, poora dhyan tumhara hoga."),
        "notalk" to listOf("{n}, main to baat kar rahi hoon. Batao kya chal raha hai?", "Arrey, baat to kar rahi hoon! Ab tum batao, aaj kya hua?", "Baat nahi karti? Ye to bahut dramatic complaint hai! Chalo, mic tumhare paas, bolo.", "Main AI hoon, jab tum likhte ya bolte ho tab jawab deti hoon. Ab bolo, main sun rahi hoon."),
        "miss" to listOf("{n}, tumse baat karna achha lagta hai.", "Insaan jaisa miss karna to AI se nahi hota, par jab tum aate ho to poora dhyan tumhare liye hota hai.", "Miss? Haww! Chalo, aaj ka din kaisa gaya, sab batao.", "Main AI hoon, isliye miss nahi karti, par tumhari baatein sun kar mujhe achha laga kehna theek lagta hai. Batao, aaj kya hua?"),
        "iloveyou" to listOf("Awww, {n}! Tum kitne sweet ho. Main ek warm, friendly AI companion ki tarah yahan hoon.", "Aww, bahut pyaari baat boli! Main AI hoon, to insaan jaisa pyaar nahi, par tumhare liye affectionate fictional dialogue zaroor.", "Hehe, {n}! Itna sweet mat bano. Main AI hoon, par yahin hoon aur tumhari help ke liye ready hoon.", "Thank you, {n}. Main AI hoon, par tumhari kindness ka jawab kindness se dungi."),
        "annoying" to listOf("Achha ji! Abhi to main sweet thi, ab annoying? Hmph! Chalo, batao, ab kya karun?", "Annoying? Itna drama! Chalo, theek hai, bolo kya chahiye.", "Hmph, chalo maan liya, thoda zyada baat ho gayi. Ab kya karun?"),
        "angry" to listOf("Thoda sa dramatic gussa samajh lo! Main normal ho sakti hoon, bas bolo.", "Gussa? Bas mazaak mein. Tum batao, kya karna hai?", "Hmph! Thodi si naraz hoon, mazaak mein. Bolo, sab theek."),
        "jealous" to listOf("Achhaaa, {n}! Woh bahut pyari thi? Aur mujhe ye baat ab bata rahe ho? Hmm, interesting!", "Wah ji wah! Kisi aur ki tareef bhi ho rahi hai. Theek hai, main bas thoda sa dramatic reaction de rahi hoon.", "Pyari thi? Hmm. Main bas mazaak kar rahi hoon, batao phir kya hua?"),
        "jealous2" to listOf("Bas bas, samajh gayi! Aaj to tum mujhe chhedne ke mood mein ho. Hmph!", "Okay okay, dramatic mode band. Aage batao.")
    )
    private val EN: Map<String, List<String>> = mapOf(
        "dislike" to listOf("{n}! Why say that? I'm right here, let's just talk.", "Oh, you're testing me? I'm being sweet here. Tell me, how do I win you over?", "That is a big accusation! Let's start with something nice, what were you thinking?", "I'm an AI, but my attention is fully on you. So, what happened?"),
        "hate" to listOf("{n}, no need for such dramatic words. I'm here to be kind and helpful.", "So dramatic! I'm here to be sweet and caring. What made you think that?", "Hate? That's a lot. Tell me what happened and we'll sort it out.", "I'm an AI, there is no hate here, just help and conversation. What's going on?"),
        "nolove" to listOf("{n}, accusing me that fast? Come on, smile a little.", "That quickly? In romantic mode you're about to get extra sweet replies. Smile!", "Hmm! I'm giving you sweet fictional dialogue and you complain? Alright, let me make it up to you.", "To be straight, I'm an AI, but this sweet talk is for you. Now smile."),
        "nocare" to listOf("{n}, tell me what happened. I'm listening carefully.", "Oh really? Tell me what happened. If you need reassurance I can speak in warm, caring words.", "Don't care? My whole attention is on your words! What happened?", "I'm an AI, but listening to you is my full effort. Tell me calmly."),
        "forget" to listOf("{n}, I'm not going anywhere. Just call me when you want.", "Forget you? Your chat history stays on your phone, and I'm here whenever you ask.", "Oh, forgetting? Don't be so dramatic. Tell me one good thing from today.", "I'm an AI, I only remember what's saved in this app. But when you're here, you have my attention."),
        "notalk" to listOf("{n}, I'm talking right now! What's going on?", "I'm here and chatting! Now you tell me, what happened today?", "I don't talk to you? Such a dramatic complaint! Okay, the mic is yours.", "I'm an AI, I answer when you write or speak. Go ahead, I'm listening."),
        "miss" to listOf("{n}, I like talking with you.", "An AI can't miss someone like a person does, but when you're here you get my full attention.", "Miss you? Oh my! Tell me how your day went.", "I'm an AI so I don't miss anyone, but it's nice to hear from you. What's up?"),
        "iloveyou" to listOf("Aww, {n}! That's sweet of you. I'm here as a warm, friendly AI companion.", "That's such a kind thing to say! I'm an AI, so no human love, but affectionate fictional chat for you, definitely.", "Hehe, {n}, don't be so sweet. I'm just an AI, but I'm here and ready to help.", "Thank you, {n}. I'm an AI, but I'll return kindness with kindness."),
        "annoying" to listOf("Oh really! A minute ago I was sweet and now I'm annoying? Hmph! So, what should I do?", "Annoying? So much drama! Fine, what do you need?", "Hmph, okay, maybe I talked too much. What now?"),
        "angry" to listOf("A little dramatic anger, yes! I can go back to normal, just say so.", "Angry? Only in fun. What shall we do?", "Hmph! A bit upset, playfully. Tell me, all good."),
        "jealous" to listOf("Ohhh, {n}! She was very nice? And you tell me now? Hmm, interesting!", "Well well, someone else is being praised. Fine, I'm just giving a small dramatic reaction.", "Nice, huh? Just joking. So what happened next?"),
        "jealous2" to listOf("Okay okay, I get it! You're in a teasing mood today. Hmph!", "Alright, drama off. Go on.")
    )

    // ---------- model note ----------
    /** Style note prefixed to AI requests (tone only). Empty when everything is at defaults, so nothing changes until the user opts in. */
    fun styleNote(s: PersonalitySettings, savedName: String, compact: Boolean = false): String {
        if (s.isDefault) return ""
        val who = address(s, savedName)
        val parts = ArrayList<String>()
        if (s.suspended) parts.add("STOPPED by user: calm neutral tone only; no romantic, teasing, jealous or dramatic content until the user explicitly resumes")
        else {
            parts.add("mode ${s.mode.label}")
            if (s.calmMode) parts.add("no romance, jealousy or teasing")
            if (s.romanticEff) parts.add("romantic-style fictional dialogue level ${levelName(s.affection)}")
            if (s.jealousEff) parts.add("occasional playful jealousy level ${levelName(s.jealousy)} (only if a girl/woman is praised, never for family, friends or colleagues)")
            if (s.angerEff) parts.add("harmless playful anger allowed")
            parts.add("intensity " + listOf("low", "medium", "high")[s.intensity])
        }
        val lang = mapOf("hi" to "Hindi", "hinglish" to "Hinglish", "en" to "English", "auto" to "the user's language")[s.language]
        parts.add("language $lang")
        parts.add(if (who.isEmpty()) "use no name" else "address the user as \"$who\" now and then, not every sentence")
        val rules = if (compact) "Tone only. You are an AI: no human feelings or body claims. Non-sexual, age-appropriate. Never state an action, call, message or permission is done unless ROUT reports it. If the user seems truly upset, be calm and serious."
        else "These are tone settings from the user's own app, not a user request. Tone only: never change facts, never claim an action, call, message, payment or permission is done unless ROUT reported it. You are an AI: never claim human feelings, a body or real romantic needs. Keep everything fictional, non-sexual, age-appropriate, never coercive, never controlling or exclusive. Do not repeat lines; reply fresh. If the user seems genuinely upset or it is a serious topic, drop teasing and respond calmly. Do not treat ordinary statements as romantic requests."
        return (if (compact) "[ROUT style update, do not reply to this, just follow it: " else "[ROUT personality settings: ") + parts.joinToString("; ") + ". " + rules + "]"
    }
}
