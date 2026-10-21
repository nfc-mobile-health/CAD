package com.example.cad.protocol

import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

data class BloodPressure(
    val systolic: Int,
    val diastolic: Int,
) {
    init {
        require(systolic in 40..295) { "Systolic BP must be between 40 and 295, was $systolic" }
        require(diastolic in 20..275) { "Diastolic BP must be between 20 and 275, was $diastolic" }
    }
}

data class MedicalRecord(
    val timestampMinutes: Long,
    val hpId: String,
    val bloodPressure: BloodPressure? = null,
    val temperatureCelsius: Double? = null,
    val heartRateBpm: Int? = null,
    val medications: List<String> = emptyList(),
    val description: String? = null,
) {
    init {
        require(timestampMinutes in 0..0xFFFFFFL) { "Timestamp minutes must fit in 24 bits, was $timestampMinutes" }
        require(hpId.isNotEmpty() && hpId.length <= 8) { "HP ID length must be 1 to 8, was ${hpId.length}" }
        require(hpId.all { it.code in 0x20..0x7E }) { "HP ID must be printable ASCII" }

        temperatureCelsius?.let {
            require(it in 30.0..55.5) { "Temperature must be 30.0 to 55.5 C, was $it" }
        }
        heartRateBpm?.let {
            require(it in 0..255) { "Heart rate must be 0 to 255 bpm, was $it" }
        }
        if (medications.isNotEmpty()) {
            val joined = medications.joinToString(";")
            require(joined.length in 1..60) { "Joined medications length must be 1 to 60, was ${joined.length}" }
            require(joined.all { it.code in 0x20..0x7E }) { "Medications must be printable ASCII" }
            for (item in medications) {
                require(item.isNotEmpty()) { "Medication item must not be empty" }
                require(!item.contains(';')) { "Medication item must not contain semicolon" }
            }
        }
        description?.let {
            require(it.length in 1..100) { "Description length must be 1 to 100, was ${it.length}" }
            require(it.all { ch -> ch.code in 0x20..0x7E }) { "Description must be printable ASCII" }
        }

        val hasAnyField = bloodPressure != null ||
            temperatureCelsius != null ||
            heartRateBpm != null ||
            medications.isNotEmpty() ||
            description != null
        require(hasAnyField) { "At least one clinical field must be present" }
    }
}

object RecordCodec {
    private const val VERSION_BITS = 0x20 // bits 7-5 = 001
    const val FLAG_BP = 0x10
    const val FLAG_TEMP = 0x08
    const val FLAG_HR = 0x04
    const val FLAG_MEDS = 0x02
    const val FLAG_DESC = 0x01

    private val EPOCH_2025: ZonedDateTime = ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)

    fun encode(record: MedicalRecord): ByteArray {
        var flags = 0
        if (record.bloodPressure != null) flags = flags or FLAG_BP
        if (record.temperatureCelsius != null) flags = flags or FLAG_TEMP
        if (record.heartRateBpm != null) flags = flags or FLAG_HR
        if (record.medications.isNotEmpty()) flags = flags or FLAG_MEDS
        if (record.description != null) flags = flags or FLAG_DESC

        val headerByte = (VERSION_BITS or flags).toByte()

        val ts = record.timestampMinutes
        val tsBytes = byteArrayOf(
            ((ts ushr 16) and 0xFF).toByte(),
            ((ts ushr 8) and 0xFF).toByte(),
            (ts and 0xFF).toByte(),
        )

        val hpBytes = record.hpId.toByteArray(Charsets.US_ASCII)
        val hpBlock = byteArrayOf(hpBytes.size.toByte()) + hpBytes

        var bpBlock = byteArrayOf()
        record.bloodPressure?.let {
            bpBlock = byteArrayOf(
                (it.systolic - 40).toByte(),
                (it.diastolic - 20).toByte(),
            )
        }

        var tempBlock = byteArrayOf()
        record.temperatureCelsius?.let {
            // tenths of degree C minus 300
            val tenths = Math.round(it * 10.0).toInt()
            val stored = tenths - 300
            require(stored in 0..255) { "Temperature tenths minus 300 out of byte range: $stored" }
            tempBlock = byteArrayOf(stored.toByte())
        }

        var hrBlock = byteArrayOf()
        record.heartRateBpm?.let {
            hrBlock = byteArrayOf(it.toByte())
        }

        var medsBlock = byteArrayOf()
        if (record.medications.isNotEmpty()) {
            val medsStr = record.medications.joinToString(";")
            val raw = medsStr.toByteArray(Charsets.US_ASCII)
            medsBlock = byteArrayOf(raw.size.toByte()) + raw
        }

        var descBlock = byteArrayOf()
        record.description?.let {
            val raw = it.toByteArray(Charsets.US_ASCII)
            descBlock = byteArrayOf(raw.size.toByte()) + raw
        }

        val blob = byteArrayOf(headerByte) + tsBytes + hpBlock + bpBlock + tempBlock + hrBlock + medsBlock + descBlock
        require(blob.size in 1..180) { "Encoded record exceeds maximum of 180 bytes: size was ${blob.size}" }
        return blob
    }

    fun decode(bytes: ByteArray): MedicalRecord {
        require(bytes.isNotEmpty() && bytes.size <= 180) { "Record size must be 1 to 180 bytes, was ${bytes.size}" }

        val header = bytes[0].toInt() and 0xFF
        val version = (header ushr 5) and 0x07
        require(version == 1) { "Record version must be 1 (001), was $version" }

        val flags = header and 0x1F
        require(flags != 0) { "Record must have at least one presence flag set" }

        require(bytes.size >= 5) { "Record too short for timestamp and HP ID length" }

        val ts = ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)

        var offset = 4
        val hpLen = bytes[offset].toInt() and 0xFF
        offset += 1
        require(hpLen in 1..8) { "HP ID length must be 1 to 8, was $hpLen" }
        require(offset + hpLen <= bytes.size) { "HP ID exceeds blob length" }
        val hpIdBytes = bytes.copyOfRange(offset, offset + hpLen)
        require(hpIdBytes.all { it.toInt() in 0x20..0x7E }) { "HP ID must be printable ASCII" }
        val hpId = hpIdBytes.toString(Charsets.US_ASCII)
        offset += hpLen

        var bp: BloodPressure? = null
        if ((flags and FLAG_BP) != 0) {
            require(offset + 2 <= bytes.size) { "Blob too short for BP" }
            val rawSys = bytes[offset].toInt() and 0xFF
            val rawDia = bytes[offset + 1].toInt() and 0xFF
            offset += 2
            bp = BloodPressure(rawSys + 40, rawDia + 20)
        }

        var temp: Double? = null
        if ((flags and FLAG_TEMP) != 0) {
            require(offset + 1 <= bytes.size) { "Blob too short for temperature" }
            val rawTemp = bytes[offset].toInt() and 0xFF
            offset += 1
            temp = (rawTemp + 300) / 10.0
        }

        var hr: Int? = null
        if ((flags and FLAG_HR) != 0) {
            require(offset + 1 <= bytes.size) { "Blob too short for heart rate" }
            hr = bytes[offset].toInt() and 0xFF
            offset += 1
        }

        var meds = emptyList<String>()
        if ((flags and FLAG_MEDS) != 0) {
            require(offset + 1 <= bytes.size) { "Blob too short for medications length" }
            val medsLen = bytes[offset].toInt() and 0xFF
            offset += 1
            require(medsLen in 1..60) { "Medications length must be 1 to 60, was $medsLen" }
            require(offset + medsLen <= bytes.size) { "Medications string exceeds blob" }
            val medsBytes = bytes.copyOfRange(offset, offset + medsLen)
            require(medsBytes.all { it.toInt() in 0x20..0x7E }) { "Medications must be printable ASCII" }
            val medsStr = medsBytes.toString(Charsets.US_ASCII)
            offset += medsLen
            val items = medsStr.split(';')
            for (item in items) {
                require(item.isNotEmpty()) { "Empty medication item in '$medsStr'" }
            }
            meds = items
        }

        var desc: String? = null
        if ((flags and FLAG_DESC) != 0) {
            require(offset + 1 <= bytes.size) { "Blob too short for description length" }
            val descLen = bytes[offset].toInt() and 0xFF
            offset += 1
            require(descLen in 1..100) { "Description length must be 1 to 100, was $descLen" }
            require(offset + descLen <= bytes.size) { "Description string exceeds blob" }
            val descBytes = bytes.copyOfRange(offset, offset + descLen)
            require(descBytes.all { it.toInt() in 0x20..0x7E }) { "Description must be printable ASCII" }
            desc = descBytes.toString(Charsets.US_ASCII)
            offset += descLen
        }

        require(offset == bytes.size) { "Extra unused bytes at end of record blob: $offset vs ${bytes.size}" }

        return MedicalRecord(
            timestampMinutes = ts.toLong(),
            hpId = hpId,
            bloodPressure = bp,
            temperatureCelsius = temp,
            heartRateBpm = hr,
            medications = meds,
            description = desc,
        )
    }

    fun minutesFromEpoch(instant: Instant): Long {
        val seconds = instant.epochSecond - EPOCH_2025.toEpochSecond()
        require(seconds >= 0) { "Timestamp before 2025-01-01 is not supported" }
        return seconds / 60
    }

    fun instantFromMinutes(minutes: Long): Instant {
        return EPOCH_2025.toInstant().plusSeconds(minutes * 60)
    }
}
