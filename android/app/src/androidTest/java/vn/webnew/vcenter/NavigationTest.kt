package vn.webnew.vcenter

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasTestTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.performTextReplacement
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()



    @Test fun connectionFormValidatesAndRetainsNavigationOnRecreation() {
        compose.onNodeWithTag("profile-host").assertExists()
        val npm = compose.onNodeWithTag("npm-toggle").performScrollTo()
        val isOff = runCatching { npm.assertIsOff() }.isSuccess
        if (isOff) npm.performClick()
        compose.onNodeWithTag("npm-user").assertExists()
        compose.onNodeWithTag("npm-password").assertExists()
        compose.onNodeWithTag("profile-name").performTextInput("Lab")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("screen-title").assertDoesNotExist()
        compose.onNodeWithTag("new-profile").assertDoesNotExist()
        compose.onNodeWithTag("profile-name").assertExists()
        compose.onNodeWithTag("profile-host").performScrollTo().performTextReplacement("")
        compose.activityRule.scenario.onActivity { activity ->
            val keyboard = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            keyboard.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("connect").performScrollTo().performClick()
        compose.onNodeWithTag("connection-error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("connected-server").assertDoesNotExist()
    }

    @Test fun soapParserWorksOnAndroidAndRejectsDoctype() {
        val xml = """<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/"><Body><RetrieveServiceContentResponse xmlns="urn:vim25"><returnval><sessionManager>SessionManager</sessionManager><propertyCollector>propertyCollector</propertyCollector><viewManager>viewManager</viewManager><rootFolder>group-d1</rootFolder><about><fullName>VMware vCenter Server 8</fullName><version>8.0.3</version><instanceUuid>test-instance</instanceUuid></about></returnval></RetrieveServiceContentResponse></Body></Envelope>"""
        org.junit.Assert.assertEquals("8.0.3", VsphereSoap.serviceContent(xml).info.version)
        org.junit.Assert.assertThrows(SoapFailure::class.java) {
            VsphereSoap.serviceContent("<!DOCTYPE Envelope SYSTEM 'file:///not-allowed'>" + xml)
        }
    }




    @Test fun unauthenticatedLaunchHidesDashboardAndLogout() {
        compose.onNodeWithTag("profile-host").assertExists()
        compose.onNodeWithTag("more-menu").assertDoesNotExist()
        compose.onNodeWithTag("tab-Overview").assertDoesNotExist()
        compose.onNodeWithTag("menu-Logout").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("profile-host").assertExists()
        compose.onNodeWithTag("tab-Overview").assertDoesNotExist()
    }
}
