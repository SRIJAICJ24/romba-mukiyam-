package com.rombamukiyam.vault.core.crypto

import kotlin.math.log2

enum class StrengthLevel { VERY_WEAK, WEAK, FAIR, STRONG, VERY_STRONG }

data class StrengthReport(
    val level: StrengthLevel,
    /** Approximate guessing entropy in bits after penalties. */
    val entropyBits: Double,
    /** Short, plain-language hints (resource keys are resolved in the UI layer). */
    val hints: List<StrengthHint>,
)

enum class StrengthHint { TOO_SHORT, ADD_WORDS, REPEATED_CHARS, SEQUENCE, COMMON_WORD, ONLY_ONE_CHARSET, KEYBOARD_PATTERN }

/**
 * Fully local strength estimator. Deliberately dependency-free (no zxcvbn) and conservative.
 * It is a hint for users, not a security control; the Argon2id cost is what protects a weak passphrase.
 */
object PasswordStrength {

    private val commonWords = setOf(
        "password", "passw0rd", "qwerty", "123456", "12345678", "111111", "abc123", "letmein", "welcome",
        "admin", "iloveyou", "monkey", "dragon", "football", "baseball", "sunshine", "princess", "master",
        "login", "india", "chennai", "tamil", "family", "vault", "romba", "mukiyam", "secret", "money", "bank",
    )
    private val keyboardRuns = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm", "1234567890", "0987654321")

    fun evaluate(password: CharSequence): StrengthReport {
        if (password.isEmpty()) return StrengthReport(StrengthLevel.VERY_WEAK, 0.0, listOf(StrengthHint.TOO_SHORT))
        val hints = mutableListOf<StrengthHint>()
        val lower = password.toString().lowercase()

        var charset = 0
        if (password.any { it.isLowerCase() }) charset += 26
        if (password.any { it.isUpperCase() }) charset += 26
        if (password.any { it.isDigit() }) charset += 10
        if (password.any { !it.isLetterOrDigit() }) charset += 33
        if (charset == 0) charset = 26
        var bits = password.length * log2(charset.toDouble())

        // Passphrase bonus: 3+ words separated by spaces/hyphens behave like dictionary picks (~11 bits/word floor).
        val words = lower.split(Regex("[\\s\\-_.]+")).filter { it.length >= 3 }
        if (words.size >= 3) bits = maxOf(bits, words.size * 11.0 + words.sumOf { it.length } * 0.5)

        val classesUsed = listOf(
            password.any { it.isLowerCase() }, password.any { it.isUpperCase() },
            password.any { it.isDigit() }, password.any { !it.isLetterOrDigit() },
        ).count { it }
        if (classesUsed == 1 && words.size < 3) hints += StrengthHint.ONLY_ONE_CHARSET

        if (password.length < 12) hints += StrengthHint.TOO_SHORT
        if (password.length < 16 && words.size < 3) hints += StrengthHint.ADD_WORDS

        // Repeats: "aaaa", "abab"
        if (Regex("(.)\\1{2,}").containsMatchIn(lower)) {
            hints += StrengthHint.REPEATED_CHARS; bits *= 0.7
        }
        // Sequences: abcd, 1234
        if (hasSequence(lower)) {
            hints += StrengthHint.SEQUENCE; bits *= 0.75
        }
        if (keyboardRuns.any { run -> (0..run.length - 4).any { lower.contains(run.substring(it, it + 4)) } }) {
            hints += StrengthHint.KEYBOARD_PATTERN; bits *= 0.7
        }
        if (commonWords.any { lower.contains(it) }) {
            hints += StrengthHint.COMMON_WORD; bits *= 0.6
        }
        // Years like 1990/2024 add little.
        if (Regex("(19|20)\\d{2}").containsMatchIn(lower)) bits -= 8
        bits = bits.coerceAtLeast(0.0)

        val level = when {
            bits < 28 -> StrengthLevel.VERY_WEAK
            bits < 40 -> StrengthLevel.WEAK
            bits < 60 -> StrengthLevel.FAIR
            bits < 80 -> StrengthLevel.STRONG
            else -> StrengthLevel.VERY_STRONG
        }
        return StrengthReport(level, bits, hints.distinct())
    }

    private fun hasSequence(s: String): Boolean {
        if (s.length < 4) return false
        var run = 1
        for (i in 1 until s.length) {
            val d = s[i] - s[i - 1]
            run = if (d == 1 || d == -1) run + 1 else 1
            if (run >= 4) return true
        }
        return false
    }

}
