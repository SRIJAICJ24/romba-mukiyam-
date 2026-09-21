package com.rombamukiyam.vault.core.crypto

import com.google.common.truth.Truth.assertThat
import com.rombamukiyam.vault.domain.model.PasswordPolicy
import org.junit.Test

class PasswordGeneratorTest {

    private val words = List(1296) { "word$it" }
    private val gen = PasswordGenerator(words)

    @Test
    fun `random mode honours length and includes every enabled class`() {
        repeat(200) {
            val pw = gen.generate(GeneratorOptions(mode = GeneratorMode.RANDOM, length = 16))
            assertThat(pw).hasLength(16)
            assertThat(pw.any { it.isUpperCase() }).isTrue()
            assertThat(pw.any { it.isLowerCase() }).isTrue()
            assertThat(pw.any { it.isDigit() }).isTrue()
            assertThat(pw.any { !it.isLetterOrDigit() }).isTrue()
        }
    }

    @Test
    fun `avoid ambiguous excludes look-alike characters`() {
        repeat(200) {
            val pw = gen.generate(GeneratorOptions(length = 40, avoidAmbiguous = true))
            assertThat(pw.none { it in GeneratorOptions.AMBIGUOUS }).isTrue()
        }
    }

    @Test
    fun `custom symbols restrict the symbol set`() {
        repeat(200) {
            val pw = gen.generate(GeneratorOptions(length = 24, customSymbols = "@#"))
            pw.filter { !it.isLetterOrDigit() }.forEach { assertThat(it in "@#").isTrue() }
        }
    }

    @Test
    fun `digits-only when other classes disabled`() {
        val pw = gen.generate(GeneratorOptions(length = 12, uppercase = false, lowercase = false, symbols = true, digits = true, customSymbols = "-"))
        assertThat(pw).hasLength(12)
        assertThat(pw.all { it.isDigit() || it == '-' }).isTrue()
    }

    @Test
    fun `passphrase mode uses wordlist words and separator`() {
        val pw = gen.generate(GeneratorOptions(mode = GeneratorMode.PASSPHRASE, wordCount = 5, separator = '-', capitaliseWords = false, appendDigit = false))
        val parts = pw.split('-')
        assertThat(parts).hasSize(5)
        parts.forEach { assertThat(words).contains(it) }
    }

    @Test
    fun `outputs are not repeated`() {
        val seen = HashSet<String>()
        repeat(2000) { assertThat(seen.add(gen.generate(GeneratorOptions(length = 12)))).isTrue() }
    }

    @Test
    fun `policy violations are detected`() {
        val policy = PasswordPolicy(itemId = "i", minLength = 8, maxLength = 12, requireDigit = true, requireSymbol = true, allowedSymbols = "@#", forbiddenCharacters = " ")
        assertThat(policy.violations("Ab1@efgh")).isEmpty()
        assertThat(policy.violations("Ab1@")).contains("min_length")
        assertThat(policy.violations("Ab1@efghijklmn")).contains("max_length")
        assertThat(policy.violations("Abcdefg@")).contains("digit")
        assertThat(policy.violations("Abcdefg1")).contains("symbol")
        assertThat(policy.violations("Abcdef1!")).contains("disallowed_symbol")
        assertThat(policy.violations("Abcde 1@")).contains("forbidden_char")
    }

    @Test
    fun `entropy estimate grows with length and words`() {
        assertThat(PasswordGenerator.estimateBits(GeneratorOptions(length = 20), 1296)).isGreaterThan(PasswordGenerator.estimateBits(GeneratorOptions(length = 10), 1296))
        assertThat(PasswordGenerator.estimateBits(GeneratorOptions(mode = GeneratorMode.PASSPHRASE, wordCount = 6), 1296))
            .isGreaterThan(PasswordGenerator.estimateBits(GeneratorOptions(mode = GeneratorMode.PASSPHRASE, wordCount = 4), 1296))
    }
}
