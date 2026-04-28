package com.fayyaztech.dialer_core.utils

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File

/**
 * Helper class to access the shared student database from the dialer-core module.
 * This accesses the same database created by Flutter's DatabaseHelper.
 */
class StudentDatabaseHelper(private val context: Context) {

    companion object {
        private const val DATABASE_NAME = "dailathon_students.db"
        private const val TAG = "StudentDatabaseHelper"
        
        // Singleton instance
        @Volatile
        private var instance: StudentDatabaseHelper? = null
        
        fun getInstance(context: Context): StudentDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: StudentDatabaseHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Get the database file path (same location as Flutter's DatabaseHelper)
     */
    private fun getDatabasePath(): String {
        val dbDir = File(context.applicationInfo.dataDir, "databases")
        if (!dbDir.exists()) {
            dbDir.mkdirs()
        }
        return File(dbDir, DATABASE_NAME).absolutePath
    }

    /**
     * Normalize phone number by removing country code, +, spaces, and special characters.
     * Returns last 10 digits for consistent comparison (matches Flutter implementation).
     */
    private fun normalizePhoneNumber(phone: String): String {
        // Remove all non-digit characters
        val cleaned = phone.replace(Regex("[^0-9]"), "")
        
        // If number is longer than 10 digits, take the last 10
        // This handles country codes like +91, +1, etc.
        return if (cleaned.length > 10) {
            cleaned.substring(cleaned.length - 10)
        } else {
            cleaned
        }
    }

    /**
     * Check if a contact exists in the students table.
     * Returns true if the normalized phone number matches any student contact.
     */
    fun contactExistsInStudents(contact: String): Boolean {
        if (contact.isBlank()) return false
        
        val dbPath = getDatabasePath()
        val dbFile = File(dbPath)
        
        if (!dbFile.exists()) {
            Log.d(TAG, "Database file does not exist: $dbPath")
            return false
        }

        var db: SQLiteDatabase? = null
        var cursor: Cursor? = null
        
        try {
            db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)
            val normalizedContact = normalizePhoneNumber(contact)
            
            // Get all contacts and normalize them for comparison
            cursor = db.query(
                "students",
                arrayOf("contact"),
                "contact IS NOT NULL AND contact != ''",
                null,
                null,
                null,
                null
            )
            
            while (cursor.moveToNext()) {
                val dbContact = cursor.getString(0)
                if (normalizePhoneNumber(dbContact) == normalizedContact) {
                    return true
                }
            }
            
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if contact exists in students: ${e.message}", e)
            return false
        } finally {
            cursor?.close()
            db?.close()
        }
    }

    /**
     * Get student name by contact number (normalized lookup).
     * Returns the student name if found, null otherwise.
     */
    fun getStudentNameByContact(contact: String): String? {
        if (contact.isBlank()) return null
        
        val dbPath = getDatabasePath()
        val dbFile = File(dbPath)
        
        if (!dbFile.exists()) {
            Log.d(TAG, "Database file does not exist: $dbPath")
            return null
        }

        var db: SQLiteDatabase? = null
        var cursor: Cursor? = null
        
        try {
            db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)
            val normalizedContact = normalizePhoneNumber(contact)
            
            cursor = db.query(
                "students",
                arrayOf("name", "contact"),
                "contact IS NOT NULL AND contact != ''",
                null,
                null,
                null,
                null
            )
            
            while (cursor.moveToNext()) {
                val dbContact = cursor.getString(1)
                if (normalizePhoneNumber(dbContact) == normalizedContact) {
                    return cursor.getString(0) // Return student name
                }
            }
            
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting student name by contact: ${e.message}", e)
            return null
        } finally {
            cursor?.close()
            db?.close()
        }
    }
}
