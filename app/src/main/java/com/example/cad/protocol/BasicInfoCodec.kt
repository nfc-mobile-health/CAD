package com.example.cad.protocol

import java.time.LocalDate
import java.time.Period

data class PatientBasicInfo(
    val patientId: String,
    val name: String,
    val dateOfBirth: LocalDate,
    val gender: Gender,
    val bloodGroup: BloodGroup,
)

enum class Gender(val displayName: String) {
    MALE("Male"), FEMALE("Female"), NON_BINARY("Non-binary"), NOT_STATED("Not stated");

    companion object {
        fun fromCode(value: Int): Gender? = entries.getOrNull(value)
    }
}

enum class BloodGroup(val displayName: String) {
    UNKNOWN("Unknown"), A_POSITIVE("A+"), A_NEGATIVE("A-"), B_POSITIVE("B+"),
    B_NEGATIVE("B-"), AB_POSITIVE("AB+"), AB_NEGATIVE("AB-"), O_POSITIVE("O+"), O_NEGATIVE("O-");

    companion object {
        fun fromCode(value: Int): BloodGroup? = entries.getOrNull(value)
    }
}

object BasicInfoCodec {
    private const val FORMAT = 0x10
    private val EPOCH: LocalDate = LocalDate.of(1900, 1, 1)
    private val LAST_DATE: LocalDate = LocalDate.of(2079, 6, 5)

    fun decode(bytes: ByteArray): PatientBasicInfo {
        require(bytes.size in 7..58) { "Basic information length is invalid" }
        require((bytes[0].toInt() and 0xFF) == FORMAT) { "Basic information format is invalid" }
        val flags = bytes[1].toInt() and 0xFF
        require(flags and 0x03 == 0) { "Reserved bits must be zero" }
        val gender = Gender.fromCode(flags ushr 6) ?: error("Gender is invalid")
        val bloodGroup = BloodGroup.fromCode((flags ushr 2) and 0x0F) ?: error("Blood group is invalid")
        val dobDays = ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
        val dateOfBirth = EPOCH.plusDays(dobDays.toLong())
        require(!dateOfBirth.isAfter(LAST_DATE)) { "Date of birth is invalid" }
        val patientLength = bytes[4].toInt() and 0xFF
        require(patientLength in 1..12) { "Patient ID length is invalid" }
        val patientStart = 5
        val nameLengthIndex = patientStart + patientLength
        require(nameLengthIndex < bytes.size) { "Patient ID exceeds data" }
        val nameLength = bytes[nameLengthIndex].toInt() and 0xFF
        require(nameLength in 1..40 && nameLengthIndex + 1 + nameLength == bytes.size) { "Name length is invalid" }
        return PatientBasicInfo(
            patientId = ascii(bytes.copyOfRange(patientStart, nameLengthIndex)),
            name = ascii(bytes.copyOfRange(nameLengthIndex + 1, bytes.size)),
            dateOfBirth = dateOfBirth,
            gender = gender,
            bloodGroup = bloodGroup,
        )
    }

    fun ageOn(info: PatientBasicInfo, today: LocalDate): Int =
        Period.between(info.dateOfBirth, today).years

    private fun ascii(value: ByteArray): String {
        require(value.all { it.toInt() in 0x20..0x7E }) { "Text is not printable ASCII" }
        return value.decodeToString()
    }
}
