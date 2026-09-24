package com.grindrplus.bridge

import android.content.Intent
import com.grindrplus.core.Constants
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

/**
 * Unit tests for ForceStartActivity security fix.
 * Tests the validation logic that prevents open redirect attacks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ForceStartActivityTest {

    private val testLogs = mutableListOf<TestLog>()

    data class TestLog(val priority: Int, val tag: String?, val message: String, val throwable: Throwable?)

    private val testTree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            testLogs.add(TestLog(priority, tag, message, t))
        }
    }

    @Before
    fun setUp() {
        testLogs.clear()
        Timber.plant(testTree)
    }

    @After
    fun tearDown() {
        Timber.uproot(testTree)
    }

    @Test
    fun `test only GRINDR_PACKAGE_NAME is allowed to launch`() {
        // Create an intent with the authorized package
        val intent = Intent().apply {
            putExtra("pkg", Constants.GRINDR_PACKAGE_NAME)
        }

        val controller = Robolectric.buildActivity(ForceStartActivity::class.java, intent)
        controller.create()

        // Verify the activity was created and finished
        assertTrue("Activity should finish after onCreate", controller.get().isFinishing)

        // Note: In test environment, actual launch might not succeed due to missing package,
        // but we verify the validation passed and no warning was logged
        val warningLog = testLogs.find { it.message.contains("Unauthorized package launch attempt") }
        assertEquals("No unauthorized package warning should be logged", null, warningLog)
    }

    @Test
    fun `test other package names are rejected and logged as warnings`() {
        val unauthorizedPackage = "com.example.malicious"
        val intent = Intent().apply {
            putExtra("pkg", unauthorizedPackage)
        }

        val controller = Robolectric.buildActivity(ForceStartActivity::class.java, intent)
        controller.create()

        // Check that warning was logged
        val warningLog = testLogs.find { 
            it.message.contains("Unauthorized package launch attempt: $unauthorizedPackage") 
        }
        assertNotNull("Warning should be logged for unauthorized package", warningLog)
        assertEquals("Log should be a warning", android.util.Log.WARN, warningLog?.priority)

        // Verify no launch message was logged
        val launchLog = testLogs.find { it.message.contains("Launched package:") }
        assertEquals("No package should be launched", null, launchLog)
    }

    @Test
    fun `test null package name is handled correctly`() {
        val intent = Intent()
        // No "pkg" extra added, so it will be null

        val controller = Robolectric.buildActivity(ForceStartActivity::class.java, intent)
        controller.create()

        // Verify activity completes without error
        assertTrue("Activity should finish successfully", controller.get().isFinishing)

        // Check logs - should not contain any package-related warnings or launches
        val warningLog = testLogs.find { it.message.contains("Unauthorized package launch attempt") }
        val launchLog = testLogs.find { it.message.contains("Launched package:") }
        
        assertEquals("No warning should be logged for null package", null, warningLog)
        assertEquals("No launch should be attempted for null package", null, launchLog)
    }

    @Test
    fun `test empty package name is rejected`() {
        val intent = Intent().apply {
            putExtra("pkg", "")
        }

        val controller = Robolectric.buildActivity(ForceStartActivity::class.java, intent)
        controller.create()

        // Empty string is not equal to GRINDR_PACKAGE_NAME, so it should be rejected
        val warningLog = testLogs.find { 
            it.message.contains("Unauthorized package launch attempt: ") 
        }
        assertNotNull("Warning should be logged for empty package name", warningLog)

        // Verify no launch message was logged
        val launchLog = testLogs.find { it.message.contains("Launched package:") }
        assertEquals("No package should be launched", null, launchLog)
    }

    @Test
    fun `test case-sensitive package name validation`() {
        // Try with wrong case
        val wrongCasePackage = Constants.GRINDR_PACKAGE_NAME.uppercase()
        val intent = Intent().apply {
            putExtra("pkg", wrongCasePackage)
        }

        val controller = Robolectric.buildActivity(ForceStartActivity::class.java, intent)
        controller.create()

        // Should be rejected because package names are case-sensitive
        val warningLog = testLogs.find { 
            it.message.contains("Unauthorized package launch attempt: $wrongCasePackage") 
        }
        assertNotNull("Warning should be logged for wrong case package", warningLog)
    }

    @Test
    fun `test bridge service is started regardless of package validation`() {
        // Test with unauthorized package
        val intent = Intent().apply {
            putExtra("pkg", "com.example.test")
        }

        val controller = Robolectric.buildActivity(ForceStartActivity::class.java, intent)
        controller.create()

        // Verify service was started (check for log message)
        val serviceLog = testLogs.find { 
            it.message.contains("Bridge service started successfully") ||
            it.message.contains("Failed to start bridge service")
        }
        assertNotNull("Bridge service should be started", serviceLog)
    }
}
