package com.rombamukiyam.vault.core.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PasswordStrengthTest {

    @Test
    fun `empty and trivial inputs are very weak`() {
        assertThat(PasswordStrength.evaluate("").level).isEqualTo(StrengthLevel.VERY_WEAK)
        assertThat(PasswordStrength.evaluate("1234").level).isEqualTo(StrengthLevel.VERY_WEAK)
        assertThat(PasswordStrength.evaluate("password").level).isEqualTo(StrengthLevel.VERY_WEAK)
        assertThat(PasswordStrength.evaluate("qwerty123").level).isEqualTo(StrengthLevel.VERY_WEAK)
    }

    @Test
    fun `long random passphrases are strong`() {
        val r = PasswordStrength.evaluate("mango kettle violin thunder 42")
        assertThat(r.level).isAnyOf(StrengthLevel.STRONG, StrengthLevel.VERY_STRONG)
        val r2 = PasswordStrength.evaluate("Kx9#pLm2\$vQz7!rTb4&")
        assertThat(r2.level).isAnyOf(StrengthLevel.STRONG, StrengthLevel.VERY_STRONG)
    }

    @Test
    fun `common words and years are penalised`() {
        val a = PasswordStrength.evaluate("Chennai1990!!")
        val b = PasswordStrength.evaluate("Xqzvtr7hj4!!")
        assertThat(a.entropyBits).isLessThan(b.entropyBits)
        assertThat(a.hints).contains(StrengthHint.COMMON_WORD)
    }

    @Test
    fun `hints are reported`() {
        val r = PasswordStrength.evaluate("aaaa")
        assertThat(r.hints).contains(StrengthHint.REPEATED_CHARS)
        assertThat(r.hints).contains(StrengthHint.TOO_SHORT)
    }
}
