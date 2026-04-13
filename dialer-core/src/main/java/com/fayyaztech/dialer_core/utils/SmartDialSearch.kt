package com.fayyaztech.dialer_core.utils

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log
import java.text.Normalizer

/**
 * Smart Dial / T9 Search utility for contact and phone number search.
 * 
 * Features:
 * - T9 numeric keypad search (2=ABC, 3=DEF, etc.)
 * - Predictive name search
 * - Phone number partial matching
 * - Multi-language support (normalization)
 * - Fast indexed search
 * - Search result ranking
 * 
 * T9 Keypad Mapping:
 * 2: ABC, 3: DEF, 4: GHI, 5: JKL, 6: MNO, 7: PQRS, 8: TUV, 9: WXYZ
 * 0: Space, 1: Special characters
 */
class SmartDialSearch(private val context: Context) {

    companion object {
        private const val TAG = "SmartDialSearch"
        
        // T9 keypad mapping
        private val T9_MAP = mapOf(
            '2' to "abc",
            '3' to "def",
            '4' to "ghi",
            '5' to "jkl",
            '6' to "mno",
            '7' to "pqrs",
            '8' to "tuv",
            '9' to "wxyz",
            '0' to " "
        )
        
        // Reverse mapping for fast lookup
        private val CHAR_TO_T9 = mutableMapOf<Char, Char>().apply {
            T9_MAP.forEach { (key, chars) ->
                chars.forEach { char ->
                    put(char, key)
                    put(char.uppercaseChar(), key)
                }
            }
        }
    }

    /**
     * Search result item
     */
    data class SearchResult(
        val contactId: Long,
        val name: String,
        val phoneNumber: String,
        val photoUri: String?,
        val matchScore: Int, // Higher is better
        val matchType: MatchType,
        val highlightStart: Int = -1,
        val highlightEnd: Int = -1
    ) {
        fun toMap(): Map<String, Any?> = mapOf(
            "id" to contactId,
            "name" to name,
            "number" to phoneNumber,
            "photo" to photoUri,
            "matchScore" to matchScore,
            "matchType" to matchType.name,
            "highlightStart" to highlightStart,
            "highlightEnd" to highlightEnd
        )
    }

    /**
     * Match types for ranking
     */
    enum class MatchType(val priority: Int) {
        EXACT_NUMBER(100),      // Exact phone number match
        START_NUMBER(90),       // Phone number starts with query
        CONTAINS_NUMBER(80),    // Phone number contains query
        T9_NAME_START(70),      // T9 name match from start
        T9_NAME_WORD(60),       // T9 match at word boundary
        T9_NAME_CONTAINS(50),   // T9 match anywhere
        NAME_START(40),         // Name starts with query
        NAME_WORD(30),          // Match at word boundary
        NAME_CONTAINS(20)       // Name contains query
    }

    /**
     * Search contacts using T9 or direct text
     * @param query The search query (numeric for T9, text for regular search)
     * @param limit Maximum number of results
     * @return List of search results, sorted by relevance
     */
    fun search(query: String, limit: Int = 20): List<SearchResult> {
        if (query.isBlank()) return emptyList()

        val results = mutableListOf<SearchResult>()
        val normalizedQuery = query.trim().lowercase()
        val isNumericQuery = normalizedQuery.all { it.isDigit() }

        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
            )

            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameColumn = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberColumn = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoColumn = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)

                while (it.moveToNext()) {
                    val contactId = it.getLong(idColumn)
                    val name = it.getString(nameColumn) ?: continue
                    val phoneNumber = it.getString(numberColumn) ?: continue
                    val photoUri = it.getString(photoColumn)

                    // Try different matching strategies
                    val matchResult = if (isNumericQuery) {
                        // Numeric query: try T9 matching and phone number matching
                        matchT9AndNumber(normalizedQuery, name, phoneNumber)
                    } else {
                        // Text query: direct name and phone matching
                        matchTextAndNumber(normalizedQuery, name, phoneNumber)
                    }

                    matchResult?.let { (matchType, score, highlightStart, highlightEnd) ->
                        results.add(
                            SearchResult(
                                contactId = contactId,
                                name = name,
                                phoneNumber = phoneNumber,
                                photoUri = photoUri,
                                matchScore = score,
                                matchType = matchType,
                                highlightStart = highlightStart,
                                highlightEnd = highlightEnd
                            )
                        )
                    }
                }
            }

            // Sort by match score (descending) and return top results
            return results
                .sortedByDescending { it.matchScore }
                .take(limit)

        } catch (e: Exception) {
            Log.e(TAG, "Search failed: ${e.message}", e)
            return emptyList()
        }
    }

    /**
     * Match using T9 and phone number for numeric queries
     */
    private fun matchT9AndNumber(
        query: String,
        name: String,
        phoneNumber: String
    ): MatchResult? {
        // First, try phone number matching (higher priority)
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        val cleanQuery = query.replace(Regex("[^0-9]"), "")

        // Exact number match
        if (cleanNumber == cleanQuery) {
            return MatchResult(MatchType.EXACT_NUMBER, 1000, 0, cleanNumber.length)
        }

        // Number starts with query
        if (cleanNumber.startsWith(cleanQuery)) {
            return MatchResult(MatchType.START_NUMBER, 900, 0, cleanQuery.length)
        }

        // Number ends with query (last digits)
        if (cleanNumber.endsWith(cleanQuery)) {
            val start = cleanNumber.length - cleanQuery.length
            return MatchResult(MatchType.CONTAINS_NUMBER, 850, start, cleanNumber.length)
        }

        // Number contains query
        val numberIndex = cleanNumber.indexOf(cleanQuery)
        if (numberIndex >= 0) {
            return MatchResult(
                MatchType.CONTAINS_NUMBER, 
                800, 
                numberIndex, 
                numberIndex + cleanQuery.length
            )
        }

        // T9 name matching
        val t9Name = convertToT9(name)
        val t9Result = matchT9(query, t9Name, name)
        return t9Result
    }

    /**
     * Match using text search
     */
    private fun matchTextAndNumber(
        query: String,
        name: String,
        phoneNumber: String
    ): MatchResult? {
        val normalizedName = normalizeName(name)
        
        // Phone number matching (if query could be a number)
        if (query.all { it.isDigit() }) {
            val cleanNumber = phoneNumber.replace(Regex("[^0-9]"), "")
            val numberIndex = cleanNumber.indexOf(query)
            if (numberIndex >= 0) {
                return MatchResult(
                    MatchType.CONTAINS_NUMBER,
                    850,
                    numberIndex,
                    numberIndex + query.length
                )
            }
        }

        // Name starts with query (highest priority for text search)
        if (normalizedName.startsWith(query)) {
            return MatchResult(MatchType.NAME_START, 700, 0, query.length)
        }

        // Word boundary match (e.g., "john smith" matches "smith")
        normalizedName.split(" ").forEachIndexed { index, word ->
            if (word.startsWith(query)) {
                val start = normalizedName.indexOf(word)
                return MatchResult(
                    MatchType.NAME_WORD,
                    650 - (index * 10), // Prioritize earlier words
                    start,
                    start + query.length
                )
            }
        }

        // Name contains query
        val nameIndex = normalizedName.indexOf(query)
        if (nameIndex >= 0) {
            return MatchResult(
                MatchType.NAME_CONTAINS,
                600,
                nameIndex,
                nameIndex + query.length
            )
        }

        return null
    }

    /**
     * Match T9 query against T9-converted name
     */
    private fun matchT9(query: String, t9Name: String, originalName: String): MatchResult? {
        // T9 starts with query (best match)
        if (t9Name.startsWith(query)) {
            return MatchResult(MatchType.T9_NAME_START, 750, 0, query.length)
        }

        // T9 word boundary match
        val words = t9Name.split("0") // 0 represents space in T9
        words.forEachIndexed { index, word ->
            if (word.startsWith(query)) {
                // Find position in original name
                val wordStart = findWordStart(originalName, index)
                return MatchResult(
                    MatchType.T9_NAME_WORD,
                    700 - (index * 10),
                    wordStart,
                    wordStart + query.length
                )
            }
        }

        // T9 contains query
        val t9Index = t9Name.indexOf(query)
        if (t9Index >= 0) {
            return MatchResult(MatchType.T9_NAME_CONTAINS, 650, t9Index, t9Index + query.length)
        }

        return null
    }

    /**
     * Convert text to T9 numeric representation
     * Example: "John" -> "5646"
     */
    private fun convertToT9(text: String): String {
        val normalized = normalizeName(text)
        return buildString {
            normalized.forEach { char ->
                if (char.isLetter()) {
                    append(CHAR_TO_T9[char.lowercaseChar()] ?: '1')
                } else if (char == ' ') {
                    append('0')
                }
            }
        }
    }

    /**
     * Normalize name for consistent matching
     * - Remove accents/diacritics
     * - Convert to lowercase
     * - Trim whitespace
     */
    private fun normalizeName(name: String): String {
        // Remove accents using Normalizer
        val normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
        val withoutAccents = normalized.replace(Regex("\\p{M}"), "")
        return withoutAccents.lowercase().trim()
    }

    /**
     * Find the start position of the nth word in original text
     */
    private fun findWordStart(text: String, wordIndex: Int): Int {
        var currentWord = 0
        var position = 0

        text.forEachIndexed { index, char ->
            if (char == ' ') {
                currentWord++
                position = index + 1
            }
            if (currentWord == wordIndex) {
                return position
            }
        }

        return 0
    }

    /**
     * Get T9 representation of a query for display
     * Example: "5646" -> "JMN/KMO/JNO/KNO..."
     */
    fun getT9Candidates(query: String): List<String> {
        if (!query.all { it.isDigit() }) return emptyList()
        
        // For simplicity, return the query itself
        // A full implementation would generate all possible letter combinations
        return listOf(query)
    }

    /**
     * Internal match result
     */
    private data class MatchResult(
        val type: MatchType,
        val score: Int,
        val highlightStart: Int,
        val highlightEnd: Int
    )
}
