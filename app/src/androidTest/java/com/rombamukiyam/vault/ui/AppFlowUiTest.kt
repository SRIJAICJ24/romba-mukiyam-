package com.rombamukiyam.vault.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rombamukiyam.vault.MainActivity
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.core.security.KeyringStore
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end Compose test through the real Activity and Hilt graph: fresh install shows onboarding,
 * passphrase strength gates progress, and typed secrets never appear in the semantics tree as plain text.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppFlowUiTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hilt.inject()
    }

    private fun str(id: Int) = compose.activity.getString(id)

    @Test
    fun freshInstallShowsOnboardingAndBlocksWeakPassphrase() {
        // A vault created by another test could exist; this test only asserts behaviour on a fresh profile.
        val hasVault = File(compose.activity.filesDir, KeyringStore.DIR_NAME).let { File(it, KeyringStore.FILE_NAME).exists() }
        if (hasVault) {
            compose.onNodeWithText(str(R.string.lock_subtitle)).assertIsDisplayed()
            return
        }
        compose.onNodeWithText(str(R.string.ob_welcome_title)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.action_get_started)).performClick()
        compose.onNodeWithText(str(R.string.ob_local_title)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.action_continue)).performClick()
        compose.onNodeWithText(str(R.string.ob_pass_title)).assertIsDisplayed()

        compose.onNodeWithText(str(R.string.label_master_passphrase)).performTextInput("password")
        compose.onNodeWithText(str(R.string.strength_very_weak)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.action_continue)).assertIsNotEnabled()
        // Typed passphrase must not be exposed as visible text in the tree (password transformation).
        compose.onAllNodes(hasText("password")).assertCountEquals(0)
    }
}
