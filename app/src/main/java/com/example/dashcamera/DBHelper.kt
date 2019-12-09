package com.example.dashcamera

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.example.dashcamera.model.Recording
import java.util.*


class DBHelper private constructor(context: Context) :
    SQLiteOpenHelper(
        context,
        DATABASE_NAME,
        null,
        DATABASE_VERSION
    ),
    IDBHelper {
    override fun onCreate(db: SQLiteDatabase) {
        DBRecordingsContract.onCreate(db)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) { // Since we have only one version, upgrade policy is to simply to discard the data
        // and start over
        DBRecordingsContract.onUpgrade(db, oldVersion, newVersion)
    }

    override fun onDowngrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        onUpgrade(db, oldVersion, newVersion)
    }

    /**
     * Select all recordings for videos list
     *
     * @return List of recordings
     */
    override fun selectAllRecordingsList(): ArrayList<Recording?> {
        val recordingsList: ArrayList<Recording?> = ArrayList<Recording?>()
        var recording: Recording?
        val cursor: Cursor = DBRecordingsContract.queryAllRecordings(
            readableDatabase
        )
        try {
            if (cursor.moveToFirst()) {
                do {
                    recording = DBRecordingsContract.getRecordingFromCursor(cursor)
                    recordingsList.add(recording)
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            Log.e(
                DBHelper::class.java.simpleName,
                "selectAllRecordingsList: EXCEPTION - " + e.localizedMessage,
                e
            )
        } finally {
            if (cursor != null && !cursor.isClosed) {
                cursor.close()
            }
        }
        return recordingsList
    }

    /**
     * Insert new recording
     *
     * @param recording Recording
     * @return True - inserted successfully
     */
    override fun insertNewRecording(recording: Recording?): Boolean {
        return if (DBRecordingsContract.isRecordingExists(
                readableDatabase,
                recording
            )
        ) false else DBRecordingsContract.insertRecording(writableDatabase, recording)
    }

    /**
     * Delete single recording
     *
     * @param recording Recording
     * @return True - deleted successfully
     */
    override fun deleteRecording(recording: Recording): Boolean {
        return DBRecordingsContract.deleteRecording(writableDatabase, recording)
    }

    /**
     * Delete all recordings
     *
     *
     * Note: Uses for "Delete all recordings" from settings
     *
     * @return True - deleted successfully
     */
    override fun deleteAllRecordings(): Boolean {
        return DBRecordingsContract.deleteAllRecordings(writableDatabase)
    }

    /**
     * Check recording starred or not
     *
     * @param recording Recording
     * @return True - starred
     */
    override fun isRecordingStarred(recording: Recording?): Boolean {
        return DBRecordingsContract.isRecordingStarred(readableDatabase, recording)
    }

    /**
     * Insert or delete star for recording
     *
     * @param recording Recording
     * @return True - star updated successfully
     */
    override fun updateStar(recording: Recording): Boolean {
        val isStarred: Boolean = recording.isStarred()
        return if (!isStarred) { //insert start for recording
            DBRecordingsContract.insertStar(writableDatabase, recording)
        } else { //remove star
            DBRecordingsContract.deleteStar(writableDatabase, recording)
        }
    }

    companion object {
        // Make it a singleton
        private var sHelper: DBHelper? = null
        private const val DATABASE_NAME = "OpenDashCam.db"
        // If you change the database schema, you must increment the database version.
        private const val DATABASE_VERSION = 2

        @Synchronized
        fun getInstance(context: Context): DBHelper? {
            if (sHelper == null) { // Use the application context, which will ensure that you
            // don't accidentally leak an Activity's context.
            // See this article for more information: http://bit.ly/6LRzfx
                sHelper = DBHelper(context.applicationContext)
            }
            return sHelper
        }
    }
}