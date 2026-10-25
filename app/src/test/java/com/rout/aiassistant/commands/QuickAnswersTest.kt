package com.rout.aiassistant.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class QuickAnswersTest {

    // --- Math ---
    @Test fun mathDigits() { assertEquals("= 57", QuickAnswers.evalMath("45 plus 12")) }
    @Test fun mathSymbols() { assertEquals("= 57", QuickAnswers.evalMath("45 + 12")) }
    @Test fun mathGuna() { assertEquals("= 540", QuickAnswers.evalMath("45 guna 12")) }
    @Test fun mathHindiQuestion() { assertEquals("= 24", QuickAnswers.evalMath("12 guna 2 kitna hota hai")) }
    @Test fun mathDivide() { assertEquals("= 5", QuickAnswers.evalMath("100 divide 20")) }
    @Test fun mathPercent() { assertEquals("= 43.2", QuickAnswers.evalMath("18 percent of 240")) }
    @Test fun mathPercentSymbol() { assertEquals("= 43.2", QuickAnswers.evalMath("18% of 240")) }
    @Test fun mathPrecedence() { assertEquals("= 19", QuickAnswers.evalMath("3 plus 4 times 4")) }
    @Test fun mathSqrt() { assertEquals("= 12", QuickAnswers.evalMath("square root of 144")) }
    @Test fun mathWords() { assertEquals("= 7", QuickAnswers.evalMath("twelve minus five")) }
    @Test fun mathPower() { assertEquals("= 8", QuickAnswers.evalMath("2 to the power of 3")) }
    @Test fun mathNotMathIsNull() { assertNull(QuickAnswers.evalMath("weather kaisa hai")) }
    @Test fun mathSingleNumberIsNull() { assertNull(QuickAnswers.evalMath("45")) }
    @Test fun mathDivZeroIsNull() { assertNull(QuickAnswers.evalMath("5 divide 0")) }

    // --- Random ---
    @Test fun coinToss() { assertTrue(QuickAnswers.randomAnswer("coin toss karo")!!.contains("Coin toss")) }
    @Test fun dice() { assertTrue(QuickAnswers.randomAnswer("dice roll karo")!!.contains("Dice roll")) }
    @Test fun randomRange() {
        val r = QuickAnswers.randomAnswer("random number between 5 and 10")!!
        val n = r.substringAfterLast(": ").toInt()
        assertTrue(n in 5..10)
    }
    @Test fun randomNull() { assertNull(QuickAnswers.randomAnswer("random thought batao")) }

    // --- Units ---
    @Test fun kmToMiles() { assertEquals("5 kilometers = 3.11 miles", QuickAnswers.convertLocalUnits("5 km in miles")) }
    @Test fun kgToPounds() { assertEquals("10 kg = 22.05 pounds", QuickAnswers.convertLocalUnits("10 kilo kitne pounds")) }
    @Test fun temp() { assertEquals("100°C = 212°F", QuickAnswers.convertLocalUnits("100 celsius in fahrenheit")) }
    @Test fun unitsNull() { assertNull(QuickAnswers.convertLocalUnits("open settings")) }

    // --- Currency parse ---
    @Test fun currDollar() { assertEquals(Triple(50.0, "USD", "INR"), QuickAnswers.parseCurrency("50 dollars in rupees")) }
    @Test fun currCodes() { assertEquals(Triple(100.0, "USD", "EUR"), QuickAnswers.parseCurrency("100 usd to euro")) }
    @Test fun currNull() { assertNull(QuickAnswers.parseCurrency("50 kg in pounds")) }

    // --- Dates ---
    private fun now(): Calendar = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 25, 9, 0, 0); set(Calendar.MILLISECOND, 0)
    }
    @Test fun daysUntil() {
        val r = QuickAnswers.dateFacts("kitne din bache 26 october mein", now())!!
        assertTrue(r, r.contains("1 din"))
    }
    @Test fun daysUntilNextYear() {
        val r = QuickAnswers.dateFacts("days until 15 august", now())!!
        assertTrue(r, r.contains("2027") && r.contains("294 din"))
    }
    @Test fun weekday() {
        val r = QuickAnswers.dateFacts("26 october 2026 ko kaun sa din hai", now())!!
        assertTrue(r, r.contains("Monday"))
    }
    @Test fun weekNumber() {
        val exp = now().get(Calendar.WEEK_OF_YEAR)
        assertTrue(QuickAnswers.dateFacts("week number kya hai", now())!!.contains("week number $exp"))
    }
    @Test fun dateNull() { assertNull(QuickAnswers.dateFacts("aaj ka mausam", now())) }

    // --- Moon ---
    @Test fun moon() {
        val r = QuickAnswers.moonPhaseAnswer("moon phase kya hai", now())!!
        assertTrue(r, r.contains("Chaand") && r.contains("%"))
    }
    @Test fun moonNull() { assertNull(QuickAnswers.moonPhaseAnswer("weather kaisa hai")) }

    // --- Email / ETA / weather city / meaning ---
    @Test fun emailTo() {
        assertEquals("a@b.com" to "meeting kal hai", QuickAnswers.parseEmail("email to a@b.com saying meeting kal hai"))
    }
    @Test fun emailKo() {
        assertEquals("rahul" to "project done hai", QuickAnswers.parseEmail("rahul ko email karo ki project done hai"))
    }
    @Test fun emailNull() { assertNull(QuickAnswers.parseEmail("email app kholo")) }

    @Test fun etaTo() { assertEquals("airport", QuickAnswers.parseEta("eta to airport")) }
    @Test fun etaHindi() { assertEquals("office", QuickAnswers.parseEta("office jaane mein kitna time lagega")) }
    @Test fun etaTak() { assertEquals("delhi", QuickAnswers.parseEta("delhi tak kitna time lagega")) }
    @Test fun etaNull() { assertNull(QuickAnswers.parseEta("kitne baje hain")) }

    @Test fun cityIn() { assertEquals("delhi", QuickAnswers.parseWeatherCity("weather in delhi")) }
    @Test fun cityKa() { assertEquals("mumbai", QuickAnswers.parseWeatherCity("mumbai ka mausam")) }
    @Test fun cityAqi() { assertEquals("delhi", QuickAnswers.parseWeatherCity("aqi in delhi")) }
    @Test fun cityNull() { assertNull(QuickAnswers.parseWeatherCity("mausam kaisa hai")) }

    @Test fun meaningOf() { assertEquals("meaning" to "serendipity", QuickAnswers.parseMeaning("meaning of serendipity")) }
    @Test fun matlab() { assertEquals("meaning" to "serendipity", QuickAnswers.parseMeaning("serendipity ka matlab")) }
    @Test fun spell() { assertEquals("spell" to "necessary", QuickAnswers.parseMeaning("spell necessary")) }

    @Test fun emergency() { assertTrue(QuickAnswers.isEmergencyCall("emergency call karo")) }
    @Test fun emergencyNull() { assertTrue(!QuickAnswers.isEmergencyCall("rahul ko call karo")) }

    // --- Send location / named timer / song id ---
    @Test fun sendLocHindi() { assertEquals("rahul", QuickAnswers.parseSendLocation("meri location rahul ko bhejo")) }
    @Test fun sendLocEnglish() { assertEquals("rahul", QuickAnswers.parseSendLocation("send my location to rahul")) }
    @Test fun locSelf() { assertEquals("", QuickAnswers.parseSendLocation("meri location batao")) }
    @Test fun sendLocNull() { assertNull(QuickAnswers.parseSendLocation("location settings kholo")) }

    @Test fun namedTimerChai() { assertEquals("chai" to 300, QuickAnswers.parseNamedTimer("5 minute ka chai timer lagao")) }
    @Test fun namedTimerEnglish() { assertEquals("study" to 1500, QuickAnswers.parseNamedTimer("set a study timer for 25 minutes")) }
    @Test fun namedTimerSeconds() { assertEquals("pasta" to 90, QuickAnswers.parseNamedTimer("pasta timer 90 seconds")) }
    @Test fun namedTimerPlainIsNull() { assertNull(QuickAnswers.parseNamedTimer("timer 5 minutes")) }
    @Test fun namedTimerNoneIsNull() { assertNull(QuickAnswers.parseNamedTimer("timer lagao")) }

    @Test fun songId() { assertTrue(QuickAnswers.isSongIdQ("ye gaana kaunsa hai")) }
    @Test fun songIdEnglish() { assertTrue(QuickAnswers.isSongIdQ("what song is this")) }
    @Test fun songIdNull() { assertTrue(!QuickAnswers.isSongIdQ("gaana chalao")) }
}