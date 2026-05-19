package com.skyframe.notifications

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class NotificationIdsTest {

    @Test
    fun `same alert id produces the same Int`() {
        val a = NotificationIds.forAlertId("urn:oid:2.49.0.1.840.0.test-tornado-1")
        val b = NotificationIds.forAlertId("urn:oid:2.49.0.1.840.0.test-tornado-1")
        assertEquals(a, b)
    }

    @Test
    fun `different alert ids produce different Ints`() {
        val a = NotificationIds.forAlertId("urn:oid:2.49.0.1.840.0.test-tornado-1")
        val b = NotificationIds.forAlertId("urn:oid:2.49.0.1.840.0.test-tornado-2")
        assertNotEquals(a, b)
    }
}
