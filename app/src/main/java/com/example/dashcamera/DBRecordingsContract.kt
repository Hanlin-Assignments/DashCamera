package com.example.dashcamera

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import android.util.Log
import com.example.dashcamera.model.Recording
import java.lang.String


/**
 * DB contract for recordings
 */
internal object DBRecordingsContract {
    fun onCreate(db: SQLiteDatabase) {
        db.execSQL(RecordingsTable.SQL_CREATE_TABLE)
        db.execSQL(StarredRecordingTable.SQL_CREATE_TABLE)
    }

    fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2 && newVersion > 2) { //create a new table for recordings list
            db.execSQL(RecordingsTable.SQL_CREATE_TABLE)
        } else {
            db.execSQL(RecordingsTable.SQL_DROP_TABLE)
            db.execSQL(StarredRecordingTable.SQL_DROP_TABLE)
            onCreate(db)
        }
    }

    /**
     * Query all recordings
     *
     * @param db SQLiteDatabase
     * @return Cursor
     */
    fun queryAllRecordings(db: SQLiteDatabase): Cursor {
        return db.query(
            RecordingsTable.TABLE_NAME,
            RecordingsTable.QUERY_PROJECTION,
            null,
            null,
            null,
            null,
            BaseColumns._ID + " DESC",
            null
        )
    }

    /**
     * Delete all recordings
     *
     * @param db SQLiteDatabase
     * @return rue - deleted successfully
     */
    fun deleteAllRecordings(db: SQLiteDatabase): Boolean { //delete recordings
        val result = db.delete(RecordingsTable.TABLE_NAME, null, null)
        //delete stars
        if (result > 0) {
            db.delete(StarredRecordingTable.TABLE_NAME, null, null)
        }
        return result > 0
    }

    /**
     * Delete all recordings
     *
     * @param db SQLiteDatabase
     * @return rue - deleted successfully
     */
    fun deleteRecording(
        db: SQLiteDatabase,
        recording: Recording
    ): Boolean { //delete recordings
        val result = db.delete(
            RecordingsTable.TABLE_NAME,
            RecordingsTable.COLUMN_FILE_NAME + " LIKE ?",
            arrayOf(recording.getFileName())
        )
        //delete stars if exist
        if (result > 0) {
            db.delete(
                StarredRecordingTable.TABLE_NAME,
                StarredRecordingTable.COLUMN_NAME_FILE + " LIKE ?",
                arrayOf(recording.getFileName())
            )
        }
        return result > 0
    }

    /**
     * Insert new entry
     *
     * @param db        SQLiteDatabase
     * @param recording Recording
     * @return True -  inserted successfully
     */
    fun insertRecording(db: SQLiteDatabase, recording: Recording?): Boolean {
        if (recording == null) return false
        var insertedRowId: Long = -1
        val cv = ContentValues()
        cv.put(RecordingsTable.COLUMN_FILE_PATH, recording.getFilePath())
        cv.put(RecordingsTable.COLUMN_FILE_NAME, recording.getFileName())
        db.beginTransaction()
        try {
            insertedRowId = db.insert(
                RecordingsTable.TABLE_NAME,
                null,
                cv
            )
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(
                DBRecordingsContract::class.java.simpleName,
                "insertNewRecording: EXCEPTION - " + e.localizedMessage,
                e
            )
        } finally {
            db.endTransaction()
        }
        return insertedRowId > -1
    }

    /**
     * Star recording
     *
     * @param db        SQLiteDatabase
     * @param recording Recording
     * @return True - starred successfully
     */
    fun insertStar(db: SQLiteDatabase, recording: Recording?): Boolean {
        if (recording == null) return false
        var insertedRowId: Long = -1
        val cv = ContentValues()
        cv.put(StarredRecordingTable.COLUMN_NAME_FILE, recording.getFileName())
        db.beginTransaction()
        try {
            insertedRowId = db.insert(
                StarredRecordingTable.TABLE_NAME,
                null,
                cv
            )
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(
                DBRecordingsContract::class.java.simpleName,
                "insertStar: EXCEPTION - " + e.localizedMessage,
                e
            )
        } finally {
            db.endTransaction()
        }
        return insertedRowId > -1
    }

    /**
     * Delete star
     *
     * @param db        SQLiteDatabase
     * @param recording Recording
     * @return True - deleted successfully
     */
    fun deleteStar(db: SQLiteDatabase, recording: Recording): Boolean {
        var result: Long = 0
        db.beginTransaction()
        try {
            result = db.delete(
                StarredRecordingTable.TABLE_NAME,
                StarredRecordingTable.COLUMN_NAME_FILE + " LIKE ?",
                arrayOf(recording.getFileName())
            ).toLong()
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(
                DBRecordingsContract::class.java.simpleName,
                "deleteStar: exception - " + e.message,
                e
            )
        } finally {
            db.endTransaction()
        }
        return result > 0
    }

    /**
     * Check if recording exists
     *
     * @param db        SQLiteDatabase
     * @param recording Recording
     * @return True - exists
     */
    fun isRecordingExists(db: SQLiteDatabase, recording: Recording?): Boolean {
        if (recording == null) return false
        val cursor: Cursor?
        var rowCount = 0
        cursor = db.query(
            RecordingsTable.TABLE_NAME,
            arrayOf(BaseColumns._ID),
            "CAST (" + BaseColumns._ID + " AS TEXT) = ?",
            arrayOf(String.valueOf(recording.getId())),
            null,
            null,
            null,
            null
        )
        try {
            rowCount = cursor.count
        } catch (e: Exception) {
            Log.e(
                DBRecordingsContract::class.java.simpleName,
                "isRecordingExists: EXCEPTION - " + e.message,
                e
            )
        } finally {
            if (cursor != null && !cursor.isClosed) {
                cursor.close()
            }
        }
        return rowCount > 0
    }

    /**
     * Check is recording is starred
     *
     * @param db        SQLiteDatabase
     * @param recording Recording
     * @return True - starred
     */
    fun isRecordingStarred(db: SQLiteDatabase, recording: Recording?): Boolean {
        if (recording == null) return false
        val cursor: Cursor?
        var rowCount = 0
        cursor = db.query(
            StarredRecordingTable.TABLE_NAME,
            arrayOf(BaseColumns._ID),
            StarredRecordingTable.COLUMN_NAME_FILE + " LIKE ?",
            arrayOf(recording.getFileName()),
            null,
            null,
            null,
            null
        )
        try {
            rowCount = cursor.count
        } catch (e: Exception) {
            Log.e(
                DBRecordingsContract::class.java.simpleName,
                "isRecordingStarred: EXCEPTION - " + e.message,
                e
            )
        } finally {
            if (cursor != null && !cursor.isClosed) {
                cursor.close()
            }
        }
        return rowCount > 0
    }

    fun getRecordingFromCursor(cursor: Cursor?): Recording? {
        return if (cursor == null) null else Recording(
            cursor.getInt(cursor.getColumnIndex(BaseColumns._ID)),
            cursor.getString(cursor.getColumnIndex(RecordingsTable.COLUMN_FILE_PATH))
        )
    }

    /**
     * Table for starred recordings
     */
    private object StarredRecordingTable : BaseColumns {
        private const val TABLE_NAME = "starred_recording"
        private const val COLUMN_NAME_FILE = "file"
        const val SQL_CREATE_TABLE = ("create table IF NOT EXISTS "
                + TABLE_NAME
                + "("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_NAME_FILE + " TEXT"
                + ");")
        const val SQL_DROP_TABLE =
            "DROP TABLE IF EXISTS $TABLE_NAME"
    }

    /**
     * Table for recordings list
     */
    private object RecordingsTable : BaseColumns {
        private const val TABLE_NAME = "recording"
        private const val COLUMN_FILE_PATH = "file_path"
        private const val COLUMN_FILE_NAME = "file_name"
        const val SQL_CREATE_TABLE = ("create table IF NOT EXISTS "
                + TABLE_NAME
                + "("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_FILE_PATH + " TEXT, "
                + COLUMN_FILE_NAME + " TEXT"
                + ");")
        const val SQL_DROP_TABLE = "DROP TABLE IF EXISTS $TABLE_NAME"
        private val QUERY_PROJECTION = arrayOf(
            BaseColumns._ID,
            COLUMN_FILE_PATH,
            COLUMN_FILE_NAME
        )
    }
}