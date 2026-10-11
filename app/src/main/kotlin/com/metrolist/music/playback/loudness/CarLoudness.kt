package com.metrolist.music.playback.loudness

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class CarLoudnessMode(
    val correctionStrength: Double,
) {
    OFF(0.0),
    LOW(0.50),
    MEDIUM(0.75),
    HIGH(1.0),
}

enum class CarLoudnessState {
    DONE,
    FAILED,
}

data class CarLoudnessMeasurement(
    val songId: String,
    val fingerprint: String,
    val integratedLufs: Double,
    val truePeakDbtp: Double,
)
data class CarLoudnessRecord(
    val songId: String,
    val fingerprint: String,
    val analysisVersion: Int,
    val state: CarLoudnessState,
    val integratedLufs: Double?,
    val truePeakDbtp: Double?,
    val updatedAtMs: Long,
    val error: String?,
) {
    fun measurementOrNull(): CarLoudnessMeasurement? =
        if (
            state == CarLoudnessState.DONE &&
            integratedLufs != null &&
            truePeakDbtp != null
        ) {
            CarLoudnessMeasurement(
                songId = songId,
                fingerprint = fingerprint,
                integratedLufs = integratedLufs,
                truePeakDbtp = truePeakDbtp,
            )
        } else {
            null
        }
}

class CarLoudnessStore(
    context: Context,
) : SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE track_loudness (
                song_id TEXT PRIMARY KEY NOT NULL,
                fingerprint TEXT NOT NULL,
                analysis_version INTEGER NOT NULL,
                state TEXT NOT NULL,
                integrated_lufs REAL,
                true_peak_dbtp REAL,
                updated_at_ms INTEGER NOT NULL,
                error TEXT
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        db.execSQL("DROP TABLE IF EXISTS track_loudness")
        onCreate(db)
    }

    fun record(
        songId: String,
        fingerprint: String,
    ): CarLoudnessRecord? {
        val cursor =
            readableDatabase.query(
                "track_loudness",
                COLUMNS,
                "song_id = ? AND fingerprint = ? AND analysis_version = ?",
                arrayOf(songId, fingerprint, ANALYSIS_VERSION.toString()),
                null,
                null,
                null,
                "1",
            )
        cursor.use {
            if (!it.moveToFirst()) return null
            return CarLoudnessRecord(
                songId = it.getString(0),
                fingerprint = it.getString(1),
                analysisVersion = it.getInt(2),
                state = CarLoudnessState.valueOf(it.getString(3)),
                integratedLufs = if (it.isNull(4)) null else it.getDouble(4),
                truePeakDbtp = if (it.isNull(5)) null else it.getDouble(5),
                updatedAtMs = it.getLong(6),
                error = if (it.isNull(7)) null else it.getString(7),
            )
        }
    }

    fun measurement(
        songId: String,
        fingerprint: String,
    ): CarLoudnessMeasurement? =
        record(songId, fingerprint)?.measurementOrNull()
    fun putMeasurement(measurement: CarLoudnessMeasurement) {
        upsert(
            songId = measurement.songId,
            fingerprint = measurement.fingerprint,
            state = CarLoudnessState.DONE,
            integratedLufs = measurement.integratedLufs,
            truePeakDbtp = measurement.truePeakDbtp,
            error = null,
        )
    }

    fun putFailure(
        songId: String,
        fingerprint: String,
        error: String,
    ) {
        upsert(
            songId = songId,
            fingerprint = fingerprint,
            state = CarLoudnessState.FAILED,
            integratedLufs = null,
            truePeakDbtp = null,
            error = error.take(500),
        )
    }

    fun clear() {
        writableDatabase.delete("track_loudness", null, null)
    }

    private fun upsert(
        songId: String,
        fingerprint: String,
        state: CarLoudnessState,
        integratedLufs: Double?,
        truePeakDbtp: Double?,
        error: String?,
    ) {
        val values =
            ContentValues().apply {
                put("song_id", songId)
                put("fingerprint", fingerprint)
                put("analysis_version", ANALYSIS_VERSION)
                put("state", state.name)
                integratedLufs?.let { put("integrated_lufs", it) } ?: putNull("integrated_lufs")
                truePeakDbtp?.let { put("true_peak_dbtp", it) } ?: putNull("true_peak_dbtp")
                put("updated_at_ms", System.currentTimeMillis())
                error?.let { put("error", it) } ?: putNull("error")
            }
        writableDatabase.insertWithOnConflict(
            "track_loudness",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    companion object {
        const val ANALYSIS_VERSION = 1

        private const val DB_NAME = "car-loudness.db"
        private const val DB_VERSION = 1
        private val COLUMNS =
            arrayOf(
                "song_id",
                "fingerprint",
                "analysis_version",
                "state",
                "integrated_lufs",
                "true_peak_dbtp",
                "updated_at_ms",
                "error",
            )
    }
}

fun carNormalizationGainMb(
    measurement: CarLoudnessMeasurement,
    mode: CarLoudnessMode,
): Int {
    if (mode == CarLoudnessMode.OFF) return 0

    val requestedDb =
        (TARGET_LUFS - measurement.integratedLufs) *
            mode.correctionStrength
    val safeDb =
        if (requestedDb > 0.0) {
            val positiveHeadroomDb =
                max(0.0, TRUE_PEAK_CEILING_DBTP - measurement.truePeakDbtp)
            min(requestedDb, positiveHeadroomDb)
        } else {
            requestedDb
        }

    return (safeDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB) * 100.0).roundToInt()
}

const val CAR_LOUDNESS_FINGERPRINT_EXTRA = "metrolist.car.loudness.fingerprint"
const val TARGET_LUFS = -16.0
const val TRUE_PEAK_CEILING_DBTP = -1.0
private const val MIN_GAIN_DB = -15.0
private const val MAX_GAIN_DB = 12.0
