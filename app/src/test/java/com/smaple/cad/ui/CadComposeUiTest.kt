package com.smaple.cad.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.smaple.core.fakes.FakeCredentialStore
import com.smaple.core.fakes.FakeBackendApi
import com.smaple.cad.sync.SyncManager
import androidx.test.core.app.ApplicationProvider

@RunWith(RobolectricTestRunner::class)
@Config(instrumentedPackages = ["androidx.loader.content"])
class CadComposeUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testLoginScreen_Renders() {
        val authViewModel = AuthViewModel(FakeCredentialStore(), FakeBackendApi(), SyncManager(ApplicationProvider.getApplicationContext()))
        composeTestRule.setContent {
            LoginScreen(viewModel = authViewModel, onLoginSuccess = {})
        }

        // Happy path check
        composeTestRule.onNodeWithText("Login").assertIsDisplayed()
    }
}
