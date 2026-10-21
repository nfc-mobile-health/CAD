package com.example.cad

import com.example.cad.nfc.InMemoryCardTransport
import com.example.cad.nfc.MCardReader
import com.example.cad.protocol.BloodPressure
import com.example.cad.protocol.MedicalRecord
import com.example.cad.protocol.RecordCodec
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase2ProtocolTest {

    private val v1Hex = "3D0E3F6C044E313031503C46500C466576657220322064617973"
    private val v2Hex = "240E3F49044E31303148"
    private val v3Hex = "3F0E3B25044E3130325840454C1650617261636574616D6F6C20353030206D673B4F525325436F7567682C206164766973656420726573742E2052657669657720696E20332064617973"

    @Test
    fun testVectorV1DecodesAndEncodesByteExact() {
        val bytes = hexToBytes(v1Hex)
        val record = RecordCodec.decode(bytes)
        assertEquals(933740L, record.timestampMinutes)
        assertEquals("N101", record.hpId)
        assertEquals(BloodPressure(120, 80), record.bloodPressure)
        assertEquals(37.0, record.temperatureCelsius!!, 0.001)
        assertEquals(80, record.heartRateBpm)
        assertEquals("Fever 2 days", record.description)

        val encoded = RecordCodec.encode(record)
        assertEquals(v1Hex, bytesToHex(encoded))
    }

    @Test
    fun testVectorV2DecodesAndEncodesByteExact() {
        val bytes = hexToBytes(v2Hex)
        val record = RecordCodec.decode(bytes)
        assertEquals(933705L, record.timestampMinutes)
        assertEquals("N101", record.hpId)
        assertNull(record.bloodPressure)
        assertNull(record.temperatureCelsius)
        assertEquals(72, record.heartRateBpm)
        assertNull(record.description)

        val encoded = RecordCodec.encode(record)
        assertEquals(v2Hex, bytesToHex(encoded))
    }

    @Test
    fun testVectorV3DecodesAndEncodesByteExact() {
        val bytes = hexToBytes(v3Hex)
        val record = RecordCodec.decode(bytes)
        assertEquals(932645L, record.timestampMinutes)
        assertEquals("N102", record.hpId)
        assertEquals(BloodPressure(128, 84), record.bloodPressure)
        assertEquals(36.9, record.temperatureCelsius!!, 0.001)
        assertEquals(76, record.heartRateBpm)
        assertEquals(listOf("Paracetamol 500 mg", "ORS"), record.medications)
        assertEquals("Cough, advised rest. Review in 3 days", record.description)

        val encoded = RecordCodec.encode(record)
        assertEquals(v3Hex, bytesToHex(encoded))
    }

    @Test
    fun testVectorV4Maximum179Bytes() {
        val record = MedicalRecord(
            timestampMinutes = 525600L,
            hpId = "N1010101",
            bloodPressure = BloodPressure(295, 275),
            temperatureCelsius = 55.5,
            heartRateBpm = 255,
            medications = listOf("m".repeat(60)),
            description = "d".repeat(100),
        )
        val encoded = RecordCodec.encode(record)
        assertEquals(179, encoded.size)
        assertEquals("3F080520084E313031303130", bytesToHex(encoded.copyOfRange(0, 12)))

        val decoded = RecordCodec.decode(encoded)
        assertEquals(record.hpId, decoded.hpId)
        assertEquals(record.bloodPressure, decoded.bloodPressure)
        assertEquals(record.temperatureCelsius!!, decoded.temperatureCelsius!!, 0.001)
        assertEquals(record.heartRateBpm, decoded.heartRateBpm)
        assertEquals(record.medications, decoded.medications)
        assertEquals(record.description, decoded.description)
    }

    @Test
    fun testAddRecordAndDuplicateDetection() = runTest {
        val transport = InMemoryCardTransport()
        val reader = MCardReader(transport)
        val v1 = hexToBytes(v1Hex)

        val res1 = reader.addRecord(v1)
        assertFalse(res1.alreadyStored)
        assertEquals(1, res1.recordCount)

        val res2 = reader.addRecord(v1)
        assertTrue(res2.alreadyStored)
        assertEquals(1, res2.recordCount)
    }

    @Test
    fun testWriteAndVerify() = runTest {
        val transport = InMemoryCardTransport()
        val reader = MCardReader(transport)
        val v1 = hexToBytes(v1Hex)

        val ok = reader.writeAndVerify(v1)
        assertTrue(ok)
    }

    @Test
    fun testEvictionTwelveRecordsLeavesTen() = runTest {
        val transport = InMemoryCardTransport()
        val reader = MCardReader(transport)

        val records = (1..12).map { i ->
            RecordCodec.encode(
                MedicalRecord(
                    timestampMinutes = 1000L + i,
                    hpId = "N101",
                    heartRateBpm = 60 + i,
                )
            )
        }

        for (blob in records) {
            reader.addRecord(blob)
        }

        val all = reader.readAllRecords()
        assertEquals(10, all.size)
        // First is newest (R12)
        assertEquals(bytesToHex(records[11]), bytesToHex(all[0]))
        // Last is R3 (records[2])
        assertEquals(bytesToHex(records[2]), bytesToHex(all[9]))
    }

    @Test
    fun testBoundaryRangeViolationsAreRefused() {
        assertThrows(IllegalArgumentException::class.java) { BloodPressure(39, 80) }
        assertThrows(IllegalArgumentException::class.java) { BloodPressure(296, 80) }
        assertThrows(IllegalArgumentException::class.java) { BloodPressure(120, 19) }
        assertThrows(IllegalArgumentException::class.java) { BloodPressure(120, 276) }
        assertThrows(IllegalArgumentException::class.java) {
            MedicalRecord(100L, "N1", temperatureCelsius = 29.9)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MedicalRecord(100L, "N1", temperatureCelsius = 55.6)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MedicalRecord(100L, "N1", heartRateBpm = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MedicalRecord(100L, "N1", heartRateBpm = 256)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MedicalRecord(100L, "N1", medications = listOf("a;b"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            MedicalRecord(100L, "N1", medications = listOf("m".repeat(61)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            MedicalRecord(100L, "N1", description = "d".repeat(101))
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "")
        val result = ByteArray(clean.length / 2)
        for (i in result.indices) {
            result[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return result
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02X", b.toInt() and 0xFF))
        }
        return sb.toString()
    }
}
