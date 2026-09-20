package com.rombamukiyam.vault.core.crypto

import java.security.SecureRandom

enum class GeneratorMode { RANDOM, MEMORABLE, PASSPHRASE }

data class GeneratorOptions(
    val mode: GeneratorMode = GeneratorMode.RANDOM,
    val length: Int = 16,
    val uppercase: Boolean = true,
    val lowercase: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val avoidAmbiguous: Boolean = true,
    /** Custom symbol set; when null the default set is used. Some banks restrict symbols. */
    val customSymbols: String? = null,
    /** Passphrase mode. */
    val wordCount: Int = 5,
    val separator: Char = '-',
    val capitaliseWords: Boolean = true,
    val appendDigit: Boolean = true,
) {
    companion object {
        const val MIN_LENGTH = 8
        const val MAX_LENGTH = 64
        const val DEFAULT_SYMBOLS = "!@#\$%^&*()-_=+[]{};:,.?/"
        const val AMBIGUOUS = "Il1O0o|`'\";:.,"
    }
}

/**
 * SecureRandom-based generator. Uses rejection-free uniform selection (SecureRandom.nextInt(bound)) and, for
 * RANDOM mode, guarantees at least one character from every enabled class.
 */
class PasswordGenerator(
    private val wordlist: List<String>,
    private val random: SecureRandom = SecureRandomProvider.secureRandom(),
) {

    fun generate(options: GeneratorOptions): String = when (options.mode) {
        GeneratorMode.RANDOM -> random(options)
        GeneratorMode.MEMORABLE -> memorable(options)
        GeneratorMode.PASSPHRASE -> passphrase(options)
    }

    private fun random(o: GeneratorOptions): String {
        val length = o.length.coerceIn(GeneratorOptions.MIN_LENGTH, GeneratorOptions.MAX_LENGTH)
        val classes = buildList {
            if (o.lowercase) add(filter("abcdefghijklmnopqrstuvwxyz", o))
            if (o.uppercase) add(filter("ABCDEFGHIJKLMNOPQRSTUVWXYZ", o))
            if (o.digits) add(filter("0123456789", o))
            if (o.symbols) add(filter(o.customSymbols?.ifBlank { null } ?: GeneratorOptions.DEFAULT_SYMBOLS, o))
        }.filter { it.isNotEmpty() }
        require(classes.isNotEmpty()) { "no character classes enabled" }
        val all = classes.joinToString("")
        val chars = CharArray(length)
        // One from each class first, then fill, then shuffle (Fisher-Yates with SecureRandom).
        var i = 0
        classes.forEach { cls -> if (i < length) chars[i++] = cls[random.nextInt(cls.length)] }
        while (i < length) chars[i++] = all[random.nextInt(all.length)]
        for (j in chars.lastIndex downTo 1) {
            val k = random.nextInt(j + 1)
            val t = chars[j]; chars[j] = chars[k]; chars[k] = t
        }
        return String(chars)
    }

    /** Word-word-Word12 style: easier to type into a bank site on a phone but still long. */
    private fun memorable(o: GeneratorOptions): String {
        val words = pickWords(3)
        val sb = StringBuilder()
        words.forEachIndexed { idx, w ->
            sb.append(if (idx == words.lastIndex || idx == 0) w.replaceFirstChar { it.uppercase() } else w)
            if (idx != words.lastIndex) sb.append(if (o.symbols) o.separator else "")
        }
        sb.append(random.nextInt(10)).append(random.nextInt(10))
        if (o.symbols) sb.append(GeneratorOptions.DEFAULT_SYMBOLS.let { s -> s[random.nextInt(minOf(6, s.length))] })
        return sb.toString()
    }

    private fun passphrase(o: GeneratorOptions): String {
        val words = pickWords(o.wordCount.coerceIn(3, 10))
        val body = words.joinToString(o.separator.toString()) { if (o.capitaliseWords) it.replaceFirstChar { c -> c.uppercase() } else it }
        return if (o.appendDigit) body + o.separator + random.nextInt(10) + random.nextInt(10) else body
    }

    private fun pickWords(n: Int): List<String> {
        require(wordlist.size >= 1000) { "wordlist too small" }
        return List(n) { wordlist[random.nextInt(wordlist.size)] }
    }

    private fun filter(chars: String, o: GeneratorOptions): String =
        if (o.avoidAmbiguous) chars.filterNot { it in GeneratorOptions.AMBIGUOUS } else chars

    companion object {
        /** Entropy estimate in bits for the UI meter. */
        fun estimateBits(options: GeneratorOptions, wordlistSize: Int): Double = when (options.mode) {
            GeneratorMode.RANDOM -> {
                var pool = 0
                if (options.lowercase) pool += 26
                if (options.uppercase) pool += 26
                if (options.digits) pool += 10
                if (options.symbols) pool += (options.customSymbols?.ifBlank { null } ?: GeneratorOptions.DEFAULT_SYMBOLS).length
                if (options.avoidAmbiguous) pool -= 6
                options.length * kotlin.math.log2(pool.coerceAtLeast(2).toDouble())
            }
            GeneratorMode.MEMORABLE -> 3 * kotlin.math.log2(wordlistSize.toDouble()) + 6.6
            GeneratorMode.PASSPHRASE -> options.wordCount * kotlin.math.log2(wordlistSize.toDouble()) + (if (options.appendDigit) 6.6 else 0.0)
        }
    }
}
