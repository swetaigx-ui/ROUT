package com.rout.aiassistant.commands

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Siri-parity quick answers: math, random, unit/currency conversion, date facts, moon phase.
 * All parsers are pure functions (unit-tested); only fx() touches the network.
 */
object QuickAnswers {

    // ---------- Math ----------

    private val NUMWORD = mapOf(
        "zero" to 0.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0,
        "six" to 6.0, "seven" to 7.0, "eight" to 8.0, "nine" to 9.0, "ten" to 10.0,
        "eleven" to 11.0, "twelve" to 12.0, "thirteen" to 13.0, "fourteen" to 14.0, "fifteen" to 15.0,
        "sixteen" to 16.0, "seventeen" to 17.0, "eighteen" to 18.0, "nineteen" to 19.0, "twenty" to 20.0,
        "thirty" to 30.0, "forty" to 40.0, "fifty" to 50.0, "sixty" to 60.0, "seventy" to 70.0,
        "eighty" to 80.0, "ninety" to 90.0, "hundred" to 100.0, "thousand" to 1000.0
    )

    private sealed class Tok {
        data class Num(val v: Double) : Tok()
        data class Op(val s: String) : Tok() // + - * / ^
    }

    /** Tokenize spoken math into numbers and operators, or null when it is not math. */
    private fun tokenize(raw: String): List<Tok>? {
        var t = raw.lowercase().trim()
        // percent-of first: "18 percent of 240" / "18% of 240"
        Regex("(\\d+(?:\\.\\d+)?)\\s*(?:%|percent)\\s*(?:of|ka)\\s*(\\d+(?:\\.\\d+)?)").find(t)?.let { m ->
            val p = m.groupValues[1].toDouble(); val base = m.groupValues[2].toDouble()
            return listOf(Tok.Num(p), Tok.Op("*"), Tok.Num(base / 100.0))
        }
        t = t.replace("kitna hota hai", " ").replace("kitna hoga", " ").replace("kitna hai", " ")
            .replace("what is", " ").replace("what's", " ").replace("whats", " ").replace("calculate", " ")
            .replace("batao", " ").replace("bata do", " ").replace("karo", " ")
            .replace("to the power of", " power ").replace("ki power", " power ")
            .replace("multiplied by", " times ").replace("multiply by", " times ")
            .replace("divided by", " divide ")
            .replace("aur jod do", " plus ").replace("jod do", " plus ")
            .replace("\u0917\u0941\u0928\u093e", " guna ").replace("\u092d\u093e\u0917", " bhag ")
        // sqrt / square root of N
        Regex("(?:square root of|sqrt(?:\\s+of)?)\\s*(\\d+(?:\\.\\d+)?)").find(t)?.let { m ->
            return listOf(Tok.Num(m.groupValues[1].toDouble()), Tok.Op("sqrt"))
        }
        val opWords = listOf(
            "to the power of" to "^", "ki power" to "^", "power" to "^",
            "multiplied by" to "*", "multiply" to "*", "times" to "*", "into" to "*",
            "guna" to "*", "gu\\u0928\\u093e" to "*", "x" to "*",
            "divided by" to "/", "divide" to "/", "bhag" to "/", "\\u092d\\u093e\\u0917" to "/",
            "plus" to "+", "add" to "+", "jodo" to "+", "aur jod do" to "+",
            "minus" to "-", "subtract" to "-", "ghatao" to "-", "kam" to "-"
        ).sortedByDescending { it.first.length }
        val toks = mutableListOf<Tok>()
        var i = 0
        var sawOp = false
        var lastWasNum = false
        while (i < t.length) {
            val c = t[i]
            when {
                c.isDigit() || (c == '.' && i + 1 < t.length && t[i + 1].isDigit()) -> {
                    var j = i
                    while (j < t.length && (t[j].isDigit() || t[j] == '.')) j++
                    toks.add(Tok.Num(t.substring(i, j).toDouble()))
                    lastWasNum = true
                    i = j
                }
                c == '+' || c == '-' || c == '*' || c == '/' || c == '^' || c == '\u00d7' || c == '\u00f7' -> {
                    val op = when (c) { '\u00d7' -> "*"; '\u00f7' -> "/"; else -> c.toString() }
                    if (op == "-" && !lastWasNum) { i++; continue } // ignore leading minus words
                    toks.add(Tok.Op(op)); sawOp = true; lastWasNum = false; i++
                }
                c == '%' -> { toks.add(Tok.Op("/")); toks.add(Tok.Num(100.0)); sawOp = true; lastWasNum = false; i++ }
                c.isLetter() -> {
                    var j = i
                    while (j < t.length && (t[j].isLetter() || t[j] == '\'')) j++
                    val word = t.substring(i, j)
                    val matchedOp = opWords.firstOrNull { it.first == word }
                    when {
                        NUMWORD.containsKey(word) -> { toks.add(Tok.Num(NUMWORD[word]!!)); lastWasNum = true }
                        matchedOp != null -> { toks.add(Tok.Op(matchedOp.second)); sawOp = true; lastWasNum = false }
                        word == "of" || word == "ka" || word == "se" || word == "ko" || word == "mein" -> { /* filler */ }
                        else -> return null // unknown word - not math
                    }
                    i = j
                }
                else -> i++
            }
        }
        if (toks.count { it is Tok.Num } < 2 || !sawOp) return null
        return toks
    }

    /** Evaluate with operator precedence (^, then * /, then + -). */
    private fun evalToks(toks: List<Tok>): Double? {
        if (toks.size == 2 && toks[1] is Tok.Op && (toks[1] as Tok.Op).s == "sqrt") {
            val n = (toks[0] as Tok.Num).v
            return if (n >= 0) sqrt(n) else null
        }
        val nums = mutableListOf<Double>()
        val ops = mutableListOf<String>()
        for (tk in toks) when (tk) {
            is Tok.Num -> nums.add(tk.v)
            is Tok.Op -> {
                while (ops.isNotEmpty() && prec(ops.last()) >= prec(tk.s)) {
                    if (!applyOp(nums, ops.removeAt(ops.lastIndex))) return null
                }
                ops.add(tk.s)
            }
        }
        while (ops.isNotEmpty()) if (!applyOp(nums, ops.removeAt(ops.lastIndex))) return null
        return nums.singleOrNull()
    }

    private fun prec(op: String) = when (op) { "^" -> 3; "*", "/" -> 2; else -> 1 }

    private fun applyOp(nums: MutableList<Double>, op: String): Boolean {
        if (nums.size < 2) return false
        val b = nums.removeAt(nums.lastIndex); val a = nums.removeAt(nums.lastIndex)
        val r = when (op) {
            "+" -> a + b
            "-" -> a - b
            "*" -> a * b
            "/" -> if (b == 0.0) return false else a / b
            "^" -> a.pow(b)
            else -> return false
        }
        if (r.isNaN() || r.isInfinite()) return false
        nums.add(r)
        return true
    }

    private fun fmt(v: Double): String {
        val r = (v * 100).roundToInt() / 100.0
        return if (r == floor(r)) r.toLong().toString() else r.toString()
    }

    /** Spoken/typed math -> answer line, or null when the input is not a math question. */
    fun evalMath(text: String): String? {
        val toks = tokenize(text) ?: return null
        val v = evalToks(toks) ?: return null
        return "= ${fmt(v)}"
    }

    // ---------- Random ----------

    private val rng = kotlin.random.Random

    /** Coin flip / dice / random number -> answer, or null. */
    fun randomAnswer(text: String): String? {
        val t = text.trim().lowercase()
        if (Regex("(?:coin|sikka).*(?:toss|flip|uchal|uchhal)|(?:toss|flip)\\s*(?:a\\s*)?coin|sikka uchal").containsMatchIn(t)) {
            return "Coin toss: " + if (rng.nextBoolean()) "HEADS" else "TAILS"
        }
        if (Regex("(?:dice|dice roll|paasa|ludo).*(?:roll|ghuma|fe[ck])|roll\\s*(?:a\\s*)?(?:dice|die)|paasa fek").containsMatchIn(t)) {
            return "Dice roll: " + (rng.nextInt(6) + 1)
        }
        Regex("(?:random number|koi bhi number)(?:\\s+(?:between|ke beech)\\s+(\\d+)\\s+(?:and|aur)\\s+(\\d+))?").find(t)?.let { m ->
            val a = m.groupValues[1].ifEmpty { "1" }.toInt()
            val b = m.groupValues[2].ifEmpty { "100" }.toInt()
            if (a >= b) return null
            return "Random number ($a-$b): " + rng.nextInt(a, b + 1)
        }
        return null
    }

    // ---------- Unit conversion (local) ----------

    private data class UnitDef(val names: List<String>, val toBase: (Double) -> Double, val fromBase: (Double) -> Double, val kind: String, val outName: String)

    private val UNITS = listOf(
        UnitDef(listOf("km", "kilometer", "kilometers", "kilometre"), { it }, { it }, "km", "kilometers"),
        UnitDef(listOf("miles", "mile"), { it * 1.609344 }, { it / 1.609344 }, "km", "miles"),
        UnitDef(listOf("kg", "kilo", "kilos", "kilogram", "kilograms"), { it }, { it }, "kg", "kg"),
        UnitDef(listOf("pounds", "pound", "lbs", "lb"), { it * 0.45359237 }, { it / 0.45359237 }, "kg", "pounds"),
        UnitDef(listOf("feet", "foot", "ft"), { it * 0.3048 }, { it / 0.3048 }, "m", "feet"),
        UnitDef(listOf("meter", "meters", "metre", "metres", "m"), { it }, { it }, "m", "meters"),
        UnitDef(listOf("inch", "inches"), { it * 2.54 }, { it / 2.54 }, "cm", "inches"),
        UnitDef(listOf("cm", "centimeter", "centimeters"), { it }, { it }, "cm", "cm"),
        UnitDef(listOf("liters", "litres", "liter", "litre"), { it }, { it }, "L", "liters"),
        UnitDef(listOf("gallons", "gallon"), { it * 3.785411784 }, { it / 3.785411784 }, "L", "gallons")
    )

    private fun findUnit(word: String): UnitDef? = UNITS.firstOrNull { u -> u.names.any { it == word } }

    /** "5 km in miles", "10 kilo kitne pounds" -> answer, or null. */
    fun convertLocalUnits(text: String): String? {
        val t = text.trim().lowercase()
        // temperature
        Regex("(\\d+(?:\\.\\d+)?)\\s*(?:degrees?\\s*)?(celsius|centigrade|fahrenheit|f|c)\\s*(?:in|to|into|mein)\\s*(celsius|fahrenheit|f|c)\\b").find(t)?.let { m ->
            val v = m.groupValues[1].toDouble()
            val from = m.groupValues[2]; val to = m.groupValues[3]
            val fromC = from.startsWith("c")
            val toC = to.startsWith("c")
            if (fromC == toC) return null
            val out = if (fromC) v * 9 / 5 + 32 else (v - 32) * 5 / 9
            return "${fmt(v)}\u00b0${if (fromC) "C" else "F"} = ${fmt(out)}\u00b0${if (toC) "C" else "F"}"
        }
        val m = Regex("(\\d+(?:\\.\\d+)?)\\s*([a-z.]+)\\s*(?:in|to|into|mein|kitne|kitni)\\s*([a-z.]+)\\b").find(t) ?: return null
        val v = m.groupValues[1].toDouble()
        val from = findUnit(m.groupValues[2].trim()) ?: return null
        val to = findUnit(m.groupValues[3].trim()) ?: return null
        if (from.kind != to.kind) return null
        return "${fmt(v)} ${from.outName} = ${fmt(to.fromBase(from.toBase(v)))} ${to.outName}"
    }

    // ---------- Currency conversion (Frankfurter, free, no key) ----------

    private val CURR = mapOf(
        "dollar" to "USD", "dollars" to "USD", "usd" to "USD", "bucks" to "USD",
        "rupee" to "INR", "rupees" to "INR", "inr" to "INR", "rupaye" to "INR", "rupaya" to "INR",
        "euro" to "EUR", "euros" to "EUR", "eur" to "EUR",
        "pound sterling" to "GBP", "gbp" to "GBP",
        "yen" to "JPY", "jpy" to "JPY",
        "dirham" to "AED", "dirhams" to "AED", "aed" to "AED",
        "riyal" to "SAR", "sar" to "SAR",
        "ruble" to "RUB", "rub" to "RUB",
        "canadian dollar" to "CAD", "cad" to "CAD",
        "australian dollar" to "AUD", "aud" to "AUD",
        "yuan" to "CNY", "cny" to "CNY",
        "won" to "KRW", "krw" to "KRW",
        "taka" to "BDT", "bdt" to "BDT",
        "nepali rupee" to "NPR", "npr" to "NPR",
        "singapore dollar" to "SGD", "sgd" to "SGD"
    )

    /** Parse "50 dollars in rupees" / "100 usd to inr" -> (amount, FROM, TO), or null. */
    fun parseCurrency(text: String): Triple<Double, String, String>? {
        val t = text.trim().lowercase()
        val keys = CURR.keys.sortedByDescending { it.length }
        val keyPat = keys.joinToString("|") { Regex.escape(it) }
        val m = Regex("(\\d+(?:\\.\\d+)?)\\s*($keyPat)\\s*(?:in|to|into|mein|kitne|kitni|barabar)\\s*($keyPat)\\b").find(t) ?: return null
        val from = CURR[m.groupValues[2]] ?: return null
        val to = CURR[m.groupValues[3]] ?: return null
        if (from == to) return null
        return Triple(m.groupValues[1].toDouble(), from, to)
    }

    suspend fun fx(amount: Double, from: String, to: String): Double? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL("https://api.frankfurter.app/latest?amount=$amount&from=$from&to=$to").openConnection() as HttpURLConnection)
                .apply { connectTimeout = 10000; readTimeout = 10000 }
            val raw = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            conn.disconnect()
            JSONObject(raw).getJSONObject("rates").optDouble(to).takeIf { it > 0 }
        } catch (e: Exception) { null }
    }

    // ---------- Date facts ----------

    private val MONTHS = mapOf(
        "jan" to 1, "january" to 1, "janviri" to 1,
        "feb" to 2, "february" to 2,
        "mar" to 3, "march" to 3,
        "apr" to 4, "april" to 4,
        "may" to 5, "mai" to 5,
        "jun" to 6, "june" to 6,
        "jul" to 7, "july" to 7,
        "aug" to 8, "august" to 8,
        "sep" to 9, "sept" to 9, "september" to 9,
        "oct" to 10, "october" to 10,
        "nov" to 11, "november" to 11,
        "dec" to 12, "december" to 12
    )

    /** Parse a "d Month [yyyy]" date anywhere in the text. */
    private fun findDate(t: String, now: Calendar): Calendar? {
        val monthPat = MONTHS.keys.sortedByDescending { it.length }.joinToString("|")
        val m = Regex("(\\d{1,2})\\s*(?:st|nd|rd|th)?\\s*($monthPat)(?:\\s*(\\d{4}))?").find(t) ?: return null
        val day = m.groupValues[1].toInt()
        val month = MONTHS[m.groupValues[2]] ?: return null
        if (day < 1 || day > 31) return null
        val year = m.groupValues[3].ifEmpty { now.get(Calendar.YEAR).toString() }.toInt()
        val cal = (now.clone() as Calendar).apply {
            set(Calendar.YEAR, year); set(Calendar.MONTH, month - 1); set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        // No explicit year and date already passed this year -> next year.
        if (m.groupValues[3].isEmpty() && cal.before(startOfDay(now))) cal.add(Calendar.YEAR, 1)
        return cal
    }

    private fun startOfDay(c: Calendar): Calendar = (c.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    /** Days-until / day-of-week / week-number questions -> answer, or null. */
    fun dateFacts(text: String, now: Calendar = Calendar.getInstance()): String? {
        val t = text.trim().lowercase()
        val daysUntilCue = Regex("(kitne din|how many days|days until|days left|countdown)").containsMatchIn(t)
        val weekdayCue = Regex("(kaun sa din|what day|which day|kis din)").containsMatchIn(t)
        if (daysUntilCue) {
            val target = findDate(t, now) ?: return null
            val diff = ((target.timeInMillis - startOfDay(now).timeInMillis) / 86400000L).toInt()
            val label = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.getDefault()).format(target.time)
            if (diff < 0) return null
            return "$label mein $diff din bache hain."
        }
        if (weekdayCue) {
            val target = findDate(t, now) ?: return null
            val dow = java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(target.time)
            val label = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.getDefault()).format(target.time)
            return "$label ko $dow hai."
        }
        if (Regex("(week number|week of year|kaun sa week|kis week)").containsMatchIn(t)) {
            return "Abhi week number ${now.get(Calendar.WEEK_OF_YEAR)} chal raha hai (day ${now.get(Calendar.DAY_OF_YEAR)} of the year)."
        }
        return null
    }

    // ---------- Moon phase ----------

    /** Conway-style moon phase age (days since new moon). */
    internal fun moonAge(now: Calendar): Double {
        val y = now.get(Calendar.YEAR)
        var m = now.get(Calendar.MONTH) + 1
        val d = now.get(Calendar.DAY_OF_MONTH)
        var yy = y
        var mm = m
        if (mm < 3) { yy -= 1; mm += 12 }
        mm += 1
        val jd = (365.25 * yy).toLong() + (30.6 * mm).toLong() + d - 694039.09
        return (jd / 29.5305882 % 1.0 * 29.5305882).let { if (it < 0) it + 29.5305882 else it }
    }

    fun moonPhaseAnswer(text: String, now: Calendar = Calendar.getInstance()): String? {
        val t = text.trim().lowercase()
        if (!Regex("(moon phase|chaand|chand ka|poornima|amavasya|purnima)").containsMatchIn(t)) return null
        val age = moonAge(now)
        val phase = when {
            age < 1.85 -> "New moon (amavasya ke aas-paas)"
            age < 5.54 -> "Waxing crescent"
            age < 9.23 -> "First quarter"
            age < 12.92 -> "Waxing gibbous"
            age < 16.61 -> "Full moon (poornima ke aas-paas)"
            age < 20.30 -> "Waning gibbous"
            age < 23.99 -> "Last quarter"
            age < 27.68 -> "Waning crescent"
            else -> "New moon (amavasya ke aas-paas)"
        }
        val illum = ((1 - kotlin.math.cos(2 * Math.PI * age / 29.5305882)) / 2 * 100).roundToInt()
        return "Chaand abhi $phase hai, lagbhag $illum% roshni ke saath (day ${age.roundToInt()} of the cycle)."
    }

    // ---------- Voice email / ETA / weather city / meaning ----------

    /** "email to x@gmail.com saying ..." / "rahul ko email karo ki ..." -> (to, body), or null. */
    fun parseEmail(text: String): Pair<String, String>? {
        val t = text.trim()
        Regex("^(?:send\\s+)?(?:an\\s+)?email\\s+(?:to\\s+)?(\\S+)\\s*(?:saying|ki|that|subject\\s*:\\s*)\\s*(.+)$", RegexOption.IGNORE_CASE).find(t)?.let { m ->
            val to = m.groupValues[1].trim(); val body = m.groupValues[2].trim()
            if (to.isNotEmpty() && body.isNotEmpty()) return to to body
        }
        Regex("^(.+?)\\s+ko\\s+email\\s+(?:karo|kar do|bhejo|likho)\\s*(?:ki|that)?\\s*(.+)$", RegexOption.IGNORE_CASE).find(t)?.let { m ->
            val to = m.groupValues[1].trim(); val body = m.groupValues[2].trim()
            if (to.isNotEmpty() && body.isNotEmpty()) return to to body
        }
        return null
    }

    /** "eta to X" / "X jaane mein kitna time lagega" / "time to reach X" -> destination, or null. */
    fun parseEta(text: String): String? {
        val t = text.trim().lowercase()
        Regex("^eta\\s+(?:to|for|of)\\s+(.+)$").find(t)?.let { return it.groupValues[1].trim() }
        Regex("^(.+?)\\s+(?:jaane mein|jaane main|pahunchne mein)\\s+kitna\\s+(?:time|samay|waqt)(?:\\s+lagega| lagta hai|)?\\s*$").find(t)?.let { return it.groupValues[1].trim() }
        Regex("^(.+?)\\s+tak\\s+kitna\\s+(?:time|samay|waqt)(?:\\s+lagega)?\\s*$").find(t)?.let { return it.groupValues[1].trim() }
        Regex("^(?:how long to reach|time to reach)\\s+(.+)$").find(t)?.let { return it.groupValues[1].trim() }
        return null
    }

    /** "weather in X" / "X ka mausam" / "X mein temperature" -> city, or null. Generic "weather" returns null. */
    fun parseWeatherCity(text: String): String? {
        val t = text.trim().lowercase()
        Regex("(?:weather|mausam|temperature|aqi|air quality|sunrise|sunset)\\s+(?:in|of|at)\\s+([a-z][a-z .'-]{1,40})$").find(t)?.let { return it.groupValues[1].trim() }
        Regex("^([a-z][a-z .'-]{1,40})\\s+(?:ka|ki|mein)\\s+(?:weather|mausam|temperature|aqi|sunrise|sunset)\\b").find(t)?.let { return it.groupValues[1].trim() }
        return null
    }

    /** Sunrise/sunset question? */
    fun isSunQ(text: String): Boolean = Regex("(sunrise|sunset|suryoday|suryast)").containsMatchIn(text.trim().lowercase())

    /** AQI question? */
    fun isAqiQ(text: String): Boolean = Regex("(aqi|air quality)").containsMatchIn(text.trim().lowercase())

    /** "meaning of X" / "X ka matlab" / "spell X" -> (mode, word), mode = "meaning" | "spell". */
    fun parseMeaning(text: String): Pair<String, String>? {
        val t = text.trim()
        Regex("^(?:what is the |what's the )?meaning of\\s+(.+?)\\s*[?.]?$", RegexOption.IGNORE_CASE).find(t)?.let { return "meaning" to it.groupValues[1].trim() }
        Regex("^(.+?)\\s+ka\\s+(?:matlab|meaning|arth)(?:\\s+kya\\s+hai)?\\s*[?.]?$", RegexOption.IGNORE_CASE).find(t)?.let {
            val w = it.groupValues[1].trim()
            if (w.isNotEmpty() && w.length < 60) return "meaning" to w
        }
        Regex("^(?:spell|spelling of|spelling batao)\\s+(.+?)\\s*[?.]?$", RegexOption.IGNORE_CASE).find(t)?.let { return "spell" to it.groupValues[1].trim() }
        return null
    }

    /** Emergency call cue? */
    fun isEmergencyCall(text: String): Boolean {
        val t = text.trim().lowercase()
        return Regex("emergency").containsMatchIn(t) && Regex("(call|phone|dial|lagao|karo)").containsMatchIn(t)
    }

    /** "meri location X ko bhejo" / "send my location to X" -> contact name, or null. "meri location batao" -> "" (share self). */
    fun parseSendLocation(text: String): String? {
        val t = text.trim().lowercase()
        Regex("(?:meri|my)\\s+location\\s+(.+?)\\s+ko\\s+(?:bhejo|bhej do|send\\s*karo|share\\s*karo)").find(t)?.let { return it.groupValues[1].trim() }
        Regex("(?:send|share|bhejo)\\s+(?:my|meri)\\s+location\\s+(?:to\\s+)?(.+)$").find(t)?.let { return it.groupValues[1].trim() }
        if (Regex("^(?:meri|my)\\s+location\\s+(?:batao|kya hai|bata do)$").matches(t)) return ""
        return null
    }

    /** "5 minute ka chai timer" / "chai timer 5 minute" / "timer 5 minute chai" -> (label, seconds), or null. */
    fun parseNamedTimer(text: String): Pair<String, Int>? {
        val t = text.trim().lowercase()
        if (!t.contains("timer")) return null
        val timeM = Regex("(\\d+)\\s*(minutes?|mins?|seconds?|secs?|ghante?|hours?)") .find(t) ?: return null
        val n = timeM.groupValues[1].toInt()
        if (n <= 0) return null
        val secs = when {
            timeM.groupValues[2].startsWith("min") -> n * 60
            timeM.groupValues[2].startsWith("sec") -> n
            else -> n * 3600
        }
        var label = t.replace(timeM.value, " ")
        listOf("timer", "lagao", "laga do", "set karo", "set", "karo", "ka", "ki", "ke", "mujhe", "ek", "a", "for", "chahiye").forEach { w -> label = label.replace(Regex("\\b$w\\b"), " ") }
        label = label.replace(Regex("\\s+"), " ").trim()
        if (label.isEmpty()) return null
        return label to secs
    }

    /** Song recognition cue: "ye gaana kaunsa hai" / "what song is this". */
    fun isSongIdQ(text: String): Boolean {
        val t = text.trim().lowercase()
        return (Regex("(gaana|song|gana)").containsMatchIn(t) && Regex("(kaunsa|kaun sa|which|what|pehchan|baj raha)").containsMatchIn(t))
    }

    /** "bedtime 11 baje" / "raat 10:30 baje bedtime" -> (hour24, minute), or null. Returns (-1,-1) for "bedtime off". */
    fun parseBedtime(text: String): Pair<Int, Int>? {
        val t = text.trim().lowercase()
        if (!t.contains("bedtime") && !t.contains("bed time")) return null
        if (Regex("(off|band|hatao|cancel)").containsMatchIn(t)) return -1 to -1
        val timeM = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|baje)").find(t) ?: return null
        var hour = timeM.groupValues[1].toInt()
        val minute = timeM.groupValues[2].ifEmpty { "0" }.toInt()
        if (hour > 24 || minute > 59) return null
        val marker = timeM.groupValues[3]
        val pm = marker == "pm" || (marker == "baje" && (Regex("(raat|rat|shaam|sham|night)").containsMatchIn(t) || hour < 7))
        val am = marker == "am" || (marker == "baje" && Regex("(subah|morning)").containsMatchIn(t))
        if (pm && hour < 12) hour += 12
        if (am && hour == 12) hour = 0
        if (marker == "baje" && !pm && !am && hour < 12) hour += 12 // bedtime defaults to night
        if (hour >= 24) return null
        return hour to minute
    }

    /** News cue: "news batao" / "headlines sunao" / "khabar". */
    fun isNewsQ(text: String): Boolean {
        val t = text.trim().lowercase()
        return Regex("(news|headlines|khabar|khabre|samachar)").containsMatchIn(t) &&
            Regex("(batao|sunao|dikhao|kya hai|latest|aaj|tell|read)").containsMatchIn(t)
    }
}