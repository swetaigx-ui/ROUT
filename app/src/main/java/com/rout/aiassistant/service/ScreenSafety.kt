package com.rout.aiassistant.service

/**
 * Sensitive-screen heuristics and redaction for screen control.
 *
 * These are heuristics, not a guarantee: they scan visible screen text for
 * password/OTP/payment markers (English, Hinglish, Devanagari) and fail
 * closed. A screen with none of these markers could still hold sensitive
 * content - ROUT never claims universal coverage. The safe direction is
 * always "block too much", never "read too much".
 */
object ScreenSafety {

    private val SENSITIVE_MARKERS = listOf(
        // English
        "otp", "one-time", "one time password", "verification code", "verify your",
        "card number", "cvv", "cvc", "expiry date", "valid thru",
        "enter pin", "upi pin", "mpin", "netbanking password",
        "payment", "checkout", "pay now", "password",
        // Hinglish (romanized)
        "otp daalo", "otp dalo", "pin daalo", "pin dalo", "paisa bhejo", "payment karo",
        // Devanagari
        "ओटीपी", "पासवर्ड", "पिन दर्ज", "भुगतान", "पेमेंट", "कार्ड नंबर"
    )

    fun isSensitive(screenTextLower: String): Boolean =
        SENSITIVE_MARKERS.any { screenTextLower.contains(it) }

    private val OTP_RUN = Regex("(?<![0-9])[0-9]{4,8}(?![0-9])")
    private val LONG_NUMBER = Regex("[0-9][0-9 -]{10,24}[0-9]")           // cards, account numbers
    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val PHONE = Regex("(?:\\+91[ -]?)?[6-9][0-9]{9}")

    /** Redact numbers/codes/contact details before screen text leaves the device. */
    fun redact(text: String): String =
        text.replace(EMAIL, "[email]")
            .replace(LONG_NUMBER, "[number]")
            .replace(PHONE, "[phone]")
            .replace(OTP_RUN, "[code]")
}
