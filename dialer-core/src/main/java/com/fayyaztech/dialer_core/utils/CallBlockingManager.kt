package com.fayyaztech.dialer_core.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Call blocking and screening database manager.
 * 
 * Features:
 * - Blacklist (blocked numbers)
 * - Whitelist (always allow)
 * - Spam detection patterns
 * - Blocking rules (regex, prefix matching)
 * - Statistics tracking
 * 
 * Storage: SharedPreferences for lightweight, fast access during call screening
 */
class CallBlockingManager(private val context: Context) {

    companion object {
        private const val TAG = "CallBlockingManager"
        private const val PREFS_NAME = "call_blocking_prefs"
        
        // Preference keys
        private const val KEY_BLACKLIST = "blacklist"
        private const val KEY_WHITELIST = "whitelist"
        private const val KEY_BLOCK_UNKNOWN = "block_unknown"
        private const val KEY_BLOCK_PRIVATE = "block_private"
        private const val KEY_BLOCK_FOREIGN = "block_foreign"
        private const val KEY_SPAM_PATTERNS = "spam_patterns"
        private const val KEY_BLOCKED_COUNT = "blocked_count"
        private const val KEY_SPAM_SCORE_THRESHOLD = "spam_score_threshold"
        
        // Default spam score threshold (0-100)
        private const val DEFAULT_SPAM_THRESHOLD = 70
    }

    private val prefs: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Blocking decision result
     */
    data class ScreeningResult(
        val shouldBlock: Boolean,
        val reason: BlockReason,
        val confidence: Int = 100, // 0-100
        val displayMessage: String = ""
    )

    /**
     * Reasons for blocking a call
     */
    enum class BlockReason {
        NOT_BLOCKED,
        BLACKLISTED,
        SPAM_PATTERN,
        UNKNOWN_NUMBER,
        PRIVATE_NUMBER,
        FOREIGN_NUMBER,
        SPAM_SCORE_HIGH,
        MANUAL_BLOCK
    }

    /**
     * Blocked number entry
     */
    data class BlockedNumber(
        val number: String,
        val displayName: String = "",
        val reason: String = "",
        val timestamp: Long = System.currentTimeMillis(),
        val autoBlocked: Boolean = false
    )

    /**
     * Screen an incoming call
     * @param phoneNumber The incoming caller number
     * @param callerName The caller name (if available)
     * @return ScreeningResult with blocking decision
     */
    fun screenCall(phoneNumber: String?, callerName: String? = null): ScreeningResult {
        try {
            // Handle null or empty numbers
            if (phoneNumber.isNullOrBlank()) {
                return if (shouldBlockPrivateNumbers()) {
                    ScreeningResult(
                        shouldBlock = true,
                        reason = BlockReason.PRIVATE_NUMBER,
                        displayMessage = "Private number blocked"
                    )
                } else {
                    ScreeningResult(
                        shouldBlock = false,
                        reason = BlockReason.NOT_BLOCKED
                    )
                }
            }

            val normalizedNumber = normalizePhoneNumber(phoneNumber)

            // 1. Check whitelist first (highest priority)
            if (isWhitelisted(normalizedNumber)) {
                Log.d(TAG, "Call allowed: Whitelisted number $normalizedNumber")
                return ScreeningResult(
                    shouldBlock = false,
                    reason = BlockReason.NOT_BLOCKED,
                    displayMessage = "Whitelisted contact"
                )
            }

            // 2. Check blacklist
            if (isBlacklisted(normalizedNumber)) {
                Log.i(TAG, "Call blocked: Blacklisted number $normalizedNumber")
                incrementBlockedCount()
                return ScreeningResult(
                    shouldBlock = true,
                    reason = BlockReason.BLACKLISTED,
                    displayMessage = "Blocked number"
                )
            }

            // 3. Check spam patterns
            val spamScore = calculateSpamScore(normalizedNumber, callerName)
            if (spamScore >= getSpamScoreThreshold()) {
                Log.i(TAG, "Call blocked: Spam score $spamScore for $normalizedNumber")
                incrementBlockedCount()
                return ScreeningResult(
                    shouldBlock = true,
                    reason = BlockReason.SPAM_SCORE_HIGH,
                    confidence = spamScore,
                    displayMessage = "Likely spam (${spamScore}% confidence)"
                )
            }

            // 4. Check if unknown numbers should be blocked
            if (shouldBlockUnknownNumbers() && callerName.isNullOrBlank()) {
                Log.i(TAG, "Call blocked: Unknown number $normalizedNumber")
                incrementBlockedCount()
                return ScreeningResult(
                    shouldBlock = true,
                    reason = BlockReason.UNKNOWN_NUMBER,
                    displayMessage = "Unknown caller blocked"
                )
            }

            // 5. Check foreign numbers
            if (shouldBlockForeignNumbers() && isForeignNumber(normalizedNumber)) {
                Log.i(TAG, "Call blocked: Foreign number $normalizedNumber")
                incrementBlockedCount()
                return ScreeningResult(
                    shouldBlock = true,
                    reason = BlockReason.FOREIGN_NUMBER,
                    displayMessage = "International call blocked"
                )
            }

            // Allow the call
            return ScreeningResult(
                shouldBlock = false,
                reason = BlockReason.NOT_BLOCKED
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error screening call: ${e.message}", e)
            // On error, allow the call (fail-safe)
            return ScreeningResult(
                shouldBlock = false,
                reason = BlockReason.NOT_BLOCKED
            )
        }
    }

    /**
     * Add a number to blacklist
     */
    fun addToBlacklist(number: String, reason: String = ""): Boolean {
        return try {
            val normalized = normalizePhoneNumber(number)
            val blacklist = getBlacklist().toMutableSet()
            blacklist.add(normalized)
            saveBlacklist(blacklist)
            
            Log.i(TAG, "Added $normalized to blacklist: $reason")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add to blacklist: ${e.message}", e)
            false
        }
    }

    /**
     * Remove a number from blacklist
     */
    fun removeFromBlacklist(number: String): Boolean {
        return try {
            val normalized = normalizePhoneNumber(number)
            val blacklist = getBlacklist().toMutableSet()
            val removed = blacklist.remove(normalized)
            if (removed) {
                saveBlacklist(blacklist)
                Log.i(TAG, "Removed $normalized from blacklist")
            }
            removed
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove from blacklist: ${e.message}", e)
            false
        }
    }

    /**
     * Add a number to whitelist
     */
    fun addToWhitelist(number: String): Boolean {
        return try {
            val normalized = normalizePhoneNumber(number)
            val whitelist = getWhitelist().toMutableSet()
            whitelist.add(normalized)
            saveWhitelist(whitelist)
            
            Log.i(TAG, "Added $normalized to whitelist")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add to whitelist: ${e.message}", e)
            false
        }
    }

    /**
     * Remove a number from whitelist
     */
    fun removeFromWhitelist(number: String): Boolean {
        return try {
            val normalized = normalizePhoneNumber(number)
            val whitelist = getWhitelist().toMutableSet()
            val removed = whitelist.remove(normalized)
            if (removed) {
                saveWhitelist(whitelist)
                Log.i(TAG, "Removed $normalized from whitelist")
            }
            removed
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove from whitelist: ${e.message}", e)
            false
        }
    }

    /**
     * Check if number is blacklisted
     */
    fun isBlacklisted(number: String): Boolean {
        val normalized = normalizePhoneNumber(number)
        return getBlacklist().contains(normalized) || 
               matchesPattern(normalized, getBlacklist())
    }

    /**
     * Check if number is whitelisted
     */
    fun isWhitelisted(number: String): Boolean {
        val normalized = normalizePhoneNumber(number)
        return getWhitelist().contains(normalized) ||
               matchesPattern(normalized, getWhitelist())
    }

    /**
     * Get all blacklisted numbers
     */
    fun getBlacklist(): Set<String> {
        return try {
            val json = prefs.getString(KEY_BLACKLIST, "[]") ?: "[]"
            val array = JSONArray(json)
            (0 until array.length()).map { array.getString(it) }.toSet()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load blacklist: ${e.message}", e)
            emptySet()
        }
    }

    /**
     * Get all whitelisted numbers
     */
    fun getWhitelist(): Set<String> {
        return try {
            val json = prefs.getString(KEY_WHITELIST, "[]") ?: "[]"
            val array = JSONArray(json)
            (0 until array.length()).map { array.getString(it) }.toSet()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load whitelist: ${e.message}", e)
            emptySet()
        }
    }

    /**
     * Calculate spam score (0-100)
     * Higher score = more likely spam
     */
    private fun calculateSpamScore(number: String, callerName: String?): Int {
        var score = 0

        // Check common spam patterns
        val patterns = getSpamPatterns()
        
        // Pattern: Sequential digits (e.g., 1234567890)
        if (isSequentialDigits(number)) {
            score += 20
        }

        // Pattern: All same digits (e.g., 9999999999)
        if (number.replace(Regex("[^0-9]"), "").let { it.toSet().size == 1 }) {
            score += 30
        }

        // Pattern: Starts with common spam prefixes
        val spamPrefixes = listOf("1800", "1888", "0000", "9999")
        if (spamPrefixes.any { number.startsWith(it) }) {
            score += 15
        }

        // Pattern: Too short or too long
        val digits = number.replace(Regex("[^0-9]"), "")
        if (digits.length < 7 || digits.length > 15) {
            score += 10
        }

        // Pattern: No caller name but long number
        if (callerName.isNullOrBlank() && digits.length >= 10) {
            score += 5
        }

        // Check custom spam patterns from preferences
        patterns.forEach { pattern ->
            if (number.contains(pattern, ignoreCase = true)) {
                score += 25
            }
        }

        return score.coerceIn(0, 100)
    }

    /**
     * Check if number has sequential digits
     */
    private fun isSequentialDigits(number: String): Boolean {
        val digits = number.replace(Regex("[^0-9]"), "")
        if (digits.length < 4) return false
        
        var sequential = 0
        for (i in 0 until digits.length - 1) {
            if (digits[i].code + 1 == digits[i + 1].code ||
                digits[i].code - 1 == digits[i + 1].code) {
                sequential++
                if (sequential >= 4) return true
            } else {
                sequential = 0
            }
        }
        return false
    }

    /**
     * Check if pattern matches (supports wildcards)
     */
    private fun matchesPattern(number: String, patterns: Set<String>): Boolean {
        return patterns.any { pattern ->
            if (pattern.contains("*")) {
                val regex = pattern.replace("*", ".*").toRegex()
                number.matches(regex)
            } else {
                number.endsWith(pattern) || pattern.endsWith(number)
            }
        }
    }

    /**
     * Check if number is foreign/international
     */
    private fun isForeignNumber(number: String): Boolean {
        // Check if starts with + and country code not matching local
        // This is a basic implementation
        return number.startsWith("+") && !number.startsWith("+91") // Adjust for your country
    }

    /**
     * Settings
     */
    fun shouldBlockUnknownNumbers(): Boolean {
        return prefs.getBoolean(KEY_BLOCK_UNKNOWN, false)
    }

    fun setBlockUnknownNumbers(block: Boolean) {
        prefs.edit().putBoolean(KEY_BLOCK_UNKNOWN, block).apply()
    }

    fun shouldBlockPrivateNumbers(): Boolean {
        return prefs.getBoolean(KEY_BLOCK_PRIVATE, false)
    }

    fun setBlockPrivateNumbers(block: Boolean) {
        prefs.edit().putBoolean(KEY_BLOCK_PRIVATE, block).apply()
    }

    fun shouldBlockForeignNumbers(): Boolean {
        return prefs.getBoolean(KEY_BLOCK_FOREIGN, false)
    }

    fun setBlockForeignNumbers(block: Boolean) {
        prefs.edit().putBoolean(KEY_BLOCK_FOREIGN, block).apply()
    }

    fun getSpamScoreThreshold(): Int {
        return prefs.getInt(KEY_SPAM_SCORE_THRESHOLD, DEFAULT_SPAM_THRESHOLD)
    }

    fun setSpamScoreThreshold(threshold: Int) {
        prefs.edit().putInt(KEY_SPAM_SCORE_THRESHOLD, threshold.coerceIn(0, 100)).apply()
    }

    /**
     * Get spam patterns
     */
    private fun getSpamPatterns(): Set<String> {
        return try {
            val json = prefs.getString(KEY_SPAM_PATTERNS, "[]") ?: "[]"
            val array = JSONArray(json)
            (0 until array.length()).map { array.getString(it) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    /**
     * Statistics
     */
    fun getBlockedCallCount(): Int {
        return prefs.getInt(KEY_BLOCKED_COUNT, 0)
    }

    private fun incrementBlockedCount() {
        val count = getBlockedCallCount() + 1
        prefs.edit().putInt(KEY_BLOCKED_COUNT, count).apply()
    }

    fun resetBlockedCallCount() {
        prefs.edit().putInt(KEY_BLOCKED_COUNT, 0).apply()
    }

    /**
     * Helper functions
     */
    private fun saveBlacklist(list: Set<String>) {
        val array = JSONArray(list.toList())
        prefs.edit().putString(KEY_BLACKLIST, array.toString()).apply()
    }

    private fun saveWhitelist(list: Set<String>) {
        val array = JSONArray(list.toList())
        prefs.edit().putString(KEY_WHITELIST, array.toString()).apply()
    }

    private fun normalizePhoneNumber(number: String): String {
        // Remove all non-digit characters except +
        var normalized = number.replace(Regex("[^0-9+]"), "")
        
        // Remove leading + for comparison
        if (normalized.startsWith("+")) {
            normalized = normalized.substring(1)
        }
        
        // Take last 10 digits for Indian numbers (adjust for your region)
        if (normalized.length > 10) {
            normalized = normalized.takeLast(10)
        }
        
        return normalized
    }

    /**
     * Convert to map for Flutter
     */
    fun getSettingsMap(): Map<String, Any> = mapOf(
        "blockUnknown" to shouldBlockUnknownNumbers(),
        "blockPrivate" to shouldBlockPrivateNumbers(),
        "blockForeign" to shouldBlockForeignNumbers(),
        "spamThreshold" to getSpamScoreThreshold(),
        "blockedCount" to getBlockedCallCount(),
        "blacklistSize" to getBlacklist().size,
        "whitelistSize" to getWhitelist().size
    )
}
