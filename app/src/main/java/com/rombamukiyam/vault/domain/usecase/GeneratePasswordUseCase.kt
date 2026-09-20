package com.rombamukiyam.vault.domain.usecase

import android.content.Context
import com.rombamukiyam.vault.core.crypto.GeneratorOptions
import com.rombamukiyam.vault.core.crypto.PasswordGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Loads the bundled EFF short wordlist once. */
@Singleton
class WordlistProvider @Inject constructor(@ApplicationContext private val context: Context) {
    val words: List<String> by lazy {
        context.assets.open("wordlist_eff_short.txt").bufferedReader().useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
        }
    }
}

class GeneratePasswordUseCase @Inject constructor(private val wordlist: WordlistProvider) {
    private val generator by lazy { PasswordGenerator(wordlist.words) }

    operator fun invoke(options: GeneratorOptions): String = generator.generate(options)

    fun estimateBits(options: GeneratorOptions): Double = PasswordGenerator.estimateBits(options, wordlist.words.size)
}
