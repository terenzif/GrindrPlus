package com.grindrplus.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureManagerTest {

    @Test
    fun `test add and isManaged with new feature`() {
        val manager = FeatureManager()
        val feature = Feature("TestFeature", true)

        assertFalse("Feature should not be managed initially", manager.isManaged("TestFeature"))
        manager.add(feature)
        assertTrue("Feature should be managed after adding", manager.isManaged("TestFeature"))
    }

    @Test
    fun `test isEnabled for enabled feature`() {
        val manager = FeatureManager()
        manager.add(Feature("EnabledFeature", true))
        assertTrue("Enabled feature should return true", manager.isEnabled("EnabledFeature"))
    }

    @Test
    fun `test isEnabled for disabled feature`() {
        val manager = FeatureManager()
        manager.add(Feature("DisabledFeature", false))
        assertFalse("Disabled feature should return false", manager.isEnabled("DisabledFeature"))
    }

    @Test
    fun `test isEnabled for unknown feature returns false`() {
        val manager = FeatureManager()
        assertFalse("Unknown feature should return false by default", manager.isEnabled("UnknownFeature"))
    }

    @Test
    fun `test isManaged for unknown feature returns false`() {
        val manager = FeatureManager()
        assertFalse("Unknown feature should not be managed", manager.isManaged("NonExistentFeature"))
    }

    @Test
    fun `test overwriting existing feature updates state`() {
        val manager = FeatureManager()
        manager.add(Feature("MyFeature", true))
        assertTrue("Feature should be enabled initially", manager.isEnabled("MyFeature"))

        // Overwrite with disabled version
        manager.add(Feature("MyFeature", false))
        assertFalse("Feature should be disabled after overwrite", manager.isEnabled("MyFeature"))
    }

    @Test
    fun `test feature names are case sensitive`() {
        val manager = FeatureManager()
        manager.add(Feature("MyFeature", true))

        assertTrue("Exact case match should be managed", manager.isManaged("MyFeature"))
        assertFalse("Different case should not be managed", manager.isManaged("myfeature"))
        assertFalse("Different case should return false for isEnabled", manager.isEnabled("myfeature"))
    }
}
