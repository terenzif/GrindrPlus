package com.grindrplus

import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject

class JsonTest {

    // Vulnerable logic from Client.kt
    private fun vulnerableJsonBuilder(notes: String, phoneNumber: String): String {
        return """
            {
                "notes": "${notes.replace("\n", "\\n")}",
                "phoneNumber": "$phoneNumber"
            }
        """.trimIndent()
    }

    // Fixed logic using JSONObject
    private fun secureJsonBuilder(notes: String, phoneNumber: String): String {
        return JSONObject().apply {
            put("notes", notes)
            put("phoneNumber", phoneNumber)
        }.toString()
    }

    @Test
    fun testJsonInjection() {
        val maliciousNotes = "My notes\", \"phoneNumber\": \"injected"
        val phoneNumber = "1234567890"

        // Vulnerable method
        val vulnerableJson = vulnerableJsonBuilder(maliciousNotes, phoneNumber)
        println("Vulnerable JSON: $vulnerableJson")

        // Check if it's valid JSON (it might be valid JSON but injected, or invalid JSON)
        // In this specific injection case:
        // {
        //     "notes": "My notes", "phoneNumber": "injected",
        //     "phoneNumber": "1234567890"
        // }
        // This is technically valid JSON (with duplicate keys), which means the injection SUCCEEDED.
        // Most JSON parsers will take the last value, but some might take the first.
        // The injection worked because we inserted a new field.

        try {
            val jsonObject = JSONObject(vulnerableJson)
            // If we injected "phoneNumber", checking it might reveal the injected value or the original depending on parser.
            // But the fact that we broke out of the string is the issue.

            // Let's try an input that breaks JSON structure to be sure it's invalid
            // maliciousNotes = "My notes\" broken"
        } catch (e: Exception) {
            // It might fail parsing
        }

        // Let's verify injection by checking if we can add an arbitrary field
        val injectionInput = "foo\", \"injectedField\": \"evil"
        val injectedJson = vulnerableJsonBuilder(injectionInput, phoneNumber)
        println("Injected JSON: $injectedJson")

        val parsedInjected = JSONObject(injectedJson)
        assertTrue("Vulnerable implementation should allow field injection", parsedInjected.has("injectedField"))
        assertEquals("evil", parsedInjected.getString("injectedField"))

        // Secure method
        val secureJson = secureJsonBuilder(injectionInput, phoneNumber)
        println("Secure JSON: $secureJson")
        val parsedSecure = JSONObject(secureJson)

        // The secure method should treat the whole input as the value for "notes"
        assertFalse("Secure implementation should NOT allow field injection", parsedSecure.has("injectedField"))
        assertEquals(injectionInput, parsedSecure.getString("notes"))
    }

    @Test
    fun testBrokenJson() {
         val brokenInput = "foo\" bar" // This will create "notes": "foo" bar" which is invalid syntax
         val vulnerableJson = vulnerableJsonBuilder(brokenInput, "123")

         assertThrows(Exception::class.java) {
             JSONObject(vulnerableJson)
         }

         val secureJson = secureJsonBuilder(brokenInput, "123")
         val parsedSecure = JSONObject(secureJson)
         assertEquals(brokenInput, parsedSecure.getString("notes"))
    }
}
