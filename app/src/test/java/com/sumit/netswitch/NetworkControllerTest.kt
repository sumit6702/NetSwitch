package com.sumit.netswitch

import com.sumit.netswitch.controller.NetworkController
import com.sumit.netswitch.model.CommandResult
import com.sumit.netswitch.model.NetworkMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkControllerTest {

    @Test
    fun parseNetworkMode_realMotorolaEdge70Output_returnsFiveG() {
        val realOutput = "GPRS|EDGE|UMTS|HSDPA|HSUPA|HSPA|LTE|HSPA+|GSM|LTE_CA|NR"
        val mode = NetworkController.parseNetworkMode(realOutput)
        assertEquals(NetworkMode.FIVE_G, mode)
    }

    @Test
    fun parseNetworkMode_fourGOutputWithoutNr_returnsFourG() {
        val outputWithoutNr = "GPRS|EDGE|UMTS|HSDPA|HSUPA|HSPA|LTE|HSPA+|GSM|LTE_CA"
        val mode = NetworkController.parseNetworkMode(outputWithoutNr)
        assertEquals(NetworkMode.FOUR_G, mode)
    }

    @Test
    fun parseNetworkMode_minimalLteAndNr_returnsFiveG() {
        val output = "LTE|NR"
        val mode = NetworkController.parseNetworkMode(output)
        assertEquals(NetworkMode.FIVE_G, mode)
    }

    @Test
    fun parseNetworkMode_minimalLteOnly_returnsFourG() {
        val output = "LTE"
        val mode = NetworkController.parseNetworkMode(output)
        assertEquals(NetworkMode.FOUR_G, mode)
    }

    @Test
    fun parseNetworkMode_withWhitespaceAndNewlines_parsesCorrectly() {
        val output = "  LTE | NR \n"
        val mode = NetworkController.parseNetworkMode(output)
        assertEquals(NetworkMode.FIVE_G, mode)
    }

    @Test
    fun parseNetworkMode_nullOrEmpty_returnsUnknown() {
        assertEquals(NetworkMode.UNKNOWN, NetworkController.parseNetworkMode(null))
        assertEquals(NetworkMode.UNKNOWN, NetworkController.parseNetworkMode(""))
        assertEquals(NetworkMode.UNKNOWN, NetworkController.parseNetworkMode("   "))
    }

    @Test
    fun parseNetworkMode_errorResponses_returnsUnknown() {
        assertEquals(NetworkMode.UNKNOWN, NetworkController.parseNetworkMode("Error: phone process died"))
        assertEquals(NetworkMode.UNKNOWN, NetworkController.parseNetworkMode("java.lang.SecurityException: Not allowed"))
        assertEquals(NetworkMode.UNKNOWN, NetworkController.parseNetworkMode("Invalid slot 2"))
        assertEquals(NetworkMode.UNKNOWN, NetworkController.parseNetworkMode("Command failed"))
    }

    @Test
    fun parseNetworkMode_unrecognizedRandomText_returnsUnknown() {
        assertEquals(NetworkMode.UNKNOWN, NetworkController.parseNetworkMode("hello world foo bar"))
        assertEquals(NetworkMode.UNKNOWN, NetworkController.parseNetworkMode("123456789"))
    }

    @Test
    fun bitmasks_verifyNrBitDifference() {
        val mask5g = NetworkController.DEFAULT_5G_MASK
        val mask4g = NetworkController.DEFAULT_4G_MASK

        assertEquals(20, mask5g.length)
        assertEquals(20, mask4g.length)

        // 5G mask starts with '1' (NR enabled at bit 19)
        assertEquals('1', mask5g[0])
        // 4G mask starts with '0' (NR disabled at bit 19)
        assertEquals('0', mask4g[0])

        // The remaining 19 bits are identical
        assertEquals(mask5g.substring(1), mask4g.substring(1))
    }

    @Test
    fun commandResult_serialization_roundTrip() {
        val original = CommandResult(
            exitCode = 0,
            stdout = "set-allowed-network-types-for-users completed",
            stderr = ""
        )
        val json = original.toJson()
        val parsed = CommandResult.fromJson(json)

        assertEquals(original.exitCode, parsed.exitCode)
        assertEquals(original.stdout, parsed.stdout)
        assertEquals(original.stderr, parsed.stderr)
        assertTrue(parsed.isSuccess)
    }

    @Test
    fun commandResult_failureExitCode_notSuccess() {
        val result = CommandResult(
            exitCode = 1,
            stdout = "",
            stderr = "Permission denied"
        )
        assertFalse(result.isSuccess)
    }

    @Test
    fun networkMode_oppositeToggle() {
        assertEquals(NetworkMode.FOUR_G, NetworkMode.FIVE_G.getOpposite())
        assertEquals(NetworkMode.FIVE_G, NetworkMode.FOUR_G.getOpposite())
        assertEquals(NetworkMode.FOUR_G, NetworkMode.UNKNOWN.getOpposite())
    }
}
