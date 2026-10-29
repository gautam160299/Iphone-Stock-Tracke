package com.example.applestockpulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NtfyNotifierTest {

    @Test
    fun `a bare topic uses ntfy sh`() {
        assertEquals(NtfyTarget("https://ntfy.sh", "applestock-x7k2"), NtfyNotifier.parseTarget(" applestock-x7k2 "))
    }

    @Test
    fun `a topic URL keeps its own server`() {
        assertEquals(
            NtfyTarget("https://ntfy.example.com", "my_topic"),
            NtfyNotifier.parseTarget("https://ntfy.example.com/my_topic/")
        )
        assertEquals(
            NtfyTarget("https://example.com/ntfy", "alerts"),
            NtfyNotifier.parseTarget("https://example.com/ntfy/alerts?x=1")
        )
    }

    @Test
    fun `blank, bad names and plain http are refused`() {
        assertNull(NtfyNotifier.parseTarget(""))
        assertNull(NtfyNotifier.parseTarget("has space"))
        assertNull(NtfyNotifier.parseTarget("emoji🚨"))
        assertNull(NtfyNotifier.parseTarget("http://ntfy.sh/topic"))
        assertNull(NtfyNotifier.parseTarget("https://ntfy.sh/"))
    }
}
