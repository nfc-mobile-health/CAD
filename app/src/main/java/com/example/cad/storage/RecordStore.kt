package com.example.cad.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.cad.protocol.MedicalRecord
import com.example.cad.protocol.RecordCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SavedNote(
    val id: Long = 0,
    val patientId: String,
    val timestampMinutes: Long,
    val hpId: String,
    val rawBlob: ByteArray,
    val isPendingUpload: Boolean = true,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
) {
    val decodedRecord: MedicalRecord by lazy { RecordCodec.decode(rawBlob) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SavedNote
        return id == other.id && rawBlob.contentEquals(other.rawBlob)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + rawBlob.contentHashCode()
        return result
    }
}

interface RecordStore {
    suspend fun saveNote(patientId: String, blob: ByteArray): Long
    suspend fun getPendingNotes(): List<SavedNote>
    suspend fun getNotesForPatient(patientId: String): List<SavedNote>
    suspend fun markUploaded(id: Long)
    suspend fun countPending(): Int
}

class SQLiteRecordStore(context: Context) : RecordStore {
    private val dbHelper = DatabaseHelper(context.applicationContext)

    override suspend fun saveNote(patientId: String, blob: ByteArray): Long = withContext(Dispatchers.IO) {
        val decoded = RecordCodec.decode(blob)
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_PATIENT_ID, patientId)
            put(COLUMN_TIMESTAMP_MINUTES, decoded.timestampMinutes)
            put(COLUMN_HP_ID, decoded.hpId)
            put(COLUMN_RAW_BLOB, blob)
            put(COLUMN_PENDING_UPLOAD, 1)
            put(COLUMN_CREATED_AT, System.currentTimeMillis())
        }
        db.insert(TABLE_NOTES, null, values)
    }

    override suspend fun getPendingNotes(): List<SavedNote> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TABLE_NOTES, null, "$COLUMN_PENDING_UPLOAD = 1", null, null, null, "$COLUMN_CREATED_AT DESC"
        )
        val list = mutableListOf<SavedNote>()
        cursor.use {
            while (it.moveToNext()) {
                list.add(it.toSavedNote())
            }
        }
        list
    }

    override suspend fun getNotesForPatient(patientId: String): List<SavedNote> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TABLE_NOTES, null, "$COLUMN_PATIENT_ID = ?", arrayOf(patientId), null, null, "$COLUMN_CREATED_AT DESC"
        )
        val list = mutableListOf<SavedNote>()
        cursor.use {
            while (it.moveToNext()) {
                list.add(it.toSavedNote())
            }
        }
        list
    }

    override suspend fun markUploaded(id: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply { put(COLUMN_PENDING_UPLOAD, 0) }
        db.update(TABLE_NOTES, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
        Unit
    }

    override suspend fun countPending(): Int = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_NOTES WHERE $COLUMN_PENDING_UPLOAD = 1", null)
        cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    private fun android.database.Cursor.toSavedNote(): SavedNote = SavedNote(
        id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
        patientId = getString(getColumnIndexOrThrow(COLUMN_PATIENT_ID)),
        timestampMinutes = getLong(getColumnIndexOrThrow(COLUMN_TIMESTAMP_MINUTES)),
        hpId = getString(getColumnIndexOrThrow(COLUMN_HP_ID)),
        rawBlob = getBlob(getColumnIndexOrThrow(COLUMN_RAW_BLOB)),
        isPendingUpload = getInt(getColumnIndexOrThrow(COLUMN_PENDING_UPLOAD)) == 1,
        createdAtEpochMs = getLong(getColumnIndexOrThrow(COLUMN_CREATED_AT)),
    )

    private class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE_NOTES (
                    $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_PATIENT_ID TEXT NOT NULL,
                    $COLUMN_TIMESTAMP_MINUTES INTEGER NOT NULL,
                    $COLUMN_HP_ID TEXT NOT NULL,
                    $COLUMN_RAW_BLOB BLOB NOT NULL,
                    $COLUMN_PENDING_UPLOAD INTEGER NOT NULL DEFAULT 1,
                    $COLUMN_CREATED_AT INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_NOTES")
            onCreate(db)
        }
    }

    companion object {
        const val DATABASE_NAME = "cad_records.db"
        const val DATABASE_VERSION = 1
        const val TABLE_NOTES = "notes"
        const val COLUMN_ID = "id"
        const val COLUMN_PATIENT_ID = "patient_id"
        const val COLUMN_TIMESTAMP_MINUTES = "timestamp_minutes"
        const val COLUMN_HP_ID = "hp_id"
        const val COLUMN_RAW_BLOB = "raw_blob"
        const val COLUMN_PENDING_UPLOAD = "pending_upload"
        const val COLUMN_CREATED_AT = "created_at"
    }
}
