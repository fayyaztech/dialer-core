package com.fayyaztech.dialer_core.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.telecom.Call
import android.telephony.SmsManager
import android.util.Log
import androidx.annotation.RequiresApi
import com.fayyaztech.dialer_core.services.DefaultInCallService
import org.json.JSONArray
import org.json.JSONObject

/**
 * SMS Messaging Service for Call-Related Messaging
 * 
 * Features:
 * - Reject incoming calls with SMS
 * - Send quick reply messages
 * - Manage SMS templates
 * - Send auto-reply after missed calls
 * - Track sent messages
 * 
 * Permissions Required:
 * - android.permission.SEND_SMS
 * - android.permission.READ_PHONE_STATE
 * 
 * @author Dialer Core Team
 * @version 1.0
 */
class SmsMessagingService(private val context: Context) {
    
    private val tag = "SmsMessagingService"
    private val prefs: SharedPreferences = context.getSharedPreferences("sms_messaging", Context.MODE_PRIVATE)
    
    companion object {
        // Default SMS templates
        val DEFAULT_TEMPLATES = listOf(
            "I'll call you back later.",
            "In a meeting. Will call you soon.",
            "Can't talk now. Text me?",
            "Driving. I'll call when safe.",
            "Busy right now. Call you back!",
            "At work. Will call during break.",
            "Sorry, can't answer. What's up?",
            "In class. Text me instead.",
            "With family. Call you later.",
            "Phone on silent. What do you need?"
        )
        
        // Template categories
        const val CATEGORY_BUSY = "busy"
        const val CATEGORY_MEETING = "meeting"
        const val CATEGORY_DRIVING = "driving"
        const val CATEGORY_PERSONAL = "personal"
        const val CATEGORY_CUSTOM = "custom"
        
        // Settings keys
        private const val KEY_CUSTOM_TEMPLATES = "custom_templates"
        private const val KEY_AUTO_REPLY_ENABLED = "auto_reply_enabled"
        private const val KEY_AUTO_REPLY_MESSAGE = "auto_reply_message"
        private const val KEY_SENT_MESSAGES_LOG = "sent_messages_log"
    }
    
    /**
     * Reject an incoming call and send SMS
     * 
     * @param call The Call to reject
     * @param message The SMS message to send
     * @param subscriptionId Optional SIM subscription ID for dual-SIM devices
     * @return Boolean indicating success
     */
    fun rejectCallWithMessage(
        call: Call, 
        message: String,
        subscriptionId: Int = -1
    ): Boolean {
        return try {
            // Extract phone number from call
            val phoneNumber = call.details.handle.schemeSpecificPart
            Log.d(tag, "Rejecting call from $phoneNumber with message: $message")
            
            // First reject the call
            DefaultInCallService.markUserInitiatedDisconnect(call, "sms_service_reject_with_message")
            call.reject(false, message)
            
            // Then send SMS
            val smsSent = sendSms(phoneNumber, message, subscriptionId)
            
            if (smsSent) {
                // Log the sent message
                logSentMessage(phoneNumber, message, System.currentTimeMillis())
            }
            
            smsSent
        } catch (e: Exception) {
            Log.e(tag, "Failed to reject call with message", e)
            false
        }
    }
    
    /**
     * Send SMS to a phone number
     * 
     * @param phoneNumber Recipient phone number
     * @param message SMS message content
     * @param subscriptionId Optional SIM subscription ID for dual-SIM
     * @return Boolean indicating success
     */
    fun sendSms(
        phoneNumber: String, 
        message: String,
        subscriptionId: Int = -1
    ): Boolean {
        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ (API 31)
                getSmsManagerForSubscription(subscriptionId)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                // Android 5.1+ (API 22) - Dual-SIM support
                getSmsManagerForSubscriptionLegacy(subscriptionId)
            } else {
                // Android < 5.1
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            
            // Split message if longer than 160 characters
            val parts = smsManager.divideMessage(message)
            
            if (parts.size == 1) {
                // Single part message
                smsManager.sendTextMessage(
                    phoneNumber,
                    null,
                    message,
                    null,
                    null
                )
            } else {
                // Multi-part message
                smsManager.sendMultipartTextMessage(
                    phoneNumber,
                    null,
                    parts,
                    null,
                    null
                )
            }
            
            Log.d(tag, "SMS sent successfully to $phoneNumber")
            true
        } catch (e: SecurityException) {
            Log.e(tag, "Permission denied: SEND_SMS permission not granted", e)
            false
        } catch (e: Exception) {
            Log.e(tag, "Failed to send SMS to $phoneNumber", e)
            false
        }
    }
    
    /**
     * Get SmsManager for specific subscription (Android 12+)
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun getSmsManagerForSubscription(subscriptionId: Int): SmsManager {
        return if (subscriptionId != -1) {
            context.getSystemService(SmsManager::class.java)
                .createForSubscriptionId(subscriptionId)
        } else {
            context.getSystemService(SmsManager::class.java)
        }
    }
    
    /**
     * Get SmsManager for specific subscription (Android 5.1 - 11)
     */
    @Suppress("DEPRECATION")
    private fun getSmsManagerForSubscriptionLegacy(subscriptionId: Int): SmsManager {
        return if (subscriptionId != -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        } else {
            SmsManager.getDefault()
        }
    }
    
    /**
     * Send auto-reply SMS after missed call
     * 
     * @param phoneNumber Number that called
     * @return Boolean indicating success
     */
    fun sendAutoReplyAfterMissedCall(phoneNumber: String): Boolean {
        if (!isAutoReplyEnabled()) {
            Log.d(tag, "Auto-reply is disabled")
            return false
        }
        
        val message = getAutoReplyMessage()
        Log.d(tag, "Sending auto-reply to $phoneNumber: $message")
        
        val success = sendSms(phoneNumber, message)
        
        if (success) {
            logSentMessage(phoneNumber, message, System.currentTimeMillis())
        }
        
        return success
    }
    
    /**
     * Get all SMS templates (default + custom)
     */
    fun getAllTemplates(): List<SmsTemplate> {
        val templates = mutableListOf<SmsTemplate>()
        
        // Add default templates
        DEFAULT_TEMPLATES.forEachIndexed { index, message ->
            templates.add(
                SmsTemplate(
                    id = "default_$index",
                    message = message,
                    category = categorizeMessage(message),
                    isCustom = false
                )
            )
        }
        
        // Add custom templates
        templates.addAll(getCustomTemplates())
        
        return templates
    }
    
    /**
     * Get custom user-defined templates
     */
    fun getCustomTemplates(): List<SmsTemplate> {
        val templates = mutableListOf<SmsTemplate>()
        val json = prefs.getString(KEY_CUSTOM_TEMPLATES, "[]") ?: "[]"
        
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                templates.add(
                    SmsTemplate(
                        id = obj.getString("id"),
                        message = obj.getString("message"),
                        category = obj.optString("category", CATEGORY_CUSTOM),
                        isCustom = true
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse custom templates", e)
        }
        
        return templates
    }
    
    /**
     * Add a custom SMS template
     */
    fun addCustomTemplate(message: String, category: String = CATEGORY_CUSTOM): Boolean {
        return try {
            val templates = getCustomTemplates().toMutableList()
            val newTemplate = SmsTemplate(
                id = "custom_${System.currentTimeMillis()}",
                message = message,
                category = category,
                isCustom = true
            )
            templates.add(newTemplate)
            
            saveCustomTemplates(templates)
            Log.d(tag, "Added custom template: $message")
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to add custom template", e)
            false
        }
    }
    
    /**
     * Remove a custom template
     */
    fun removeCustomTemplate(templateId: String): Boolean {
        return try {
            val templates = getCustomTemplates().toMutableList()
            templates.removeIf { it.id == templateId }
            saveCustomTemplates(templates)
            Log.d(tag, "Removed custom template: $templateId")
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to remove custom template", e)
            false
        }
    }
    
    /**
     * Update a custom template
     */
    fun updateCustomTemplate(templateId: String, newMessage: String, newCategory: String? = null): Boolean {
        return try {
            val templates = getCustomTemplates().toMutableList()
            val index = templates.indexOfFirst { it.id == templateId }
            
            if (index != -1) {
                templates[index] = templates[index].copy(
                    message = newMessage,
                    category = newCategory ?: templates[index].category
                )
                saveCustomTemplates(templates)
                Log.d(tag, "Updated custom template: $templateId")
                true
            } else {
                Log.w(tag, "Template not found: $templateId")
                false
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to update custom template", e)
            false
        }
    }
    
    /**
     * Save custom templates to SharedPreferences
     */
    private fun saveCustomTemplates(templates: List<SmsTemplate>) {
        val jsonArray = JSONArray()
        templates.forEach { template ->
            val obj = JSONObject().apply {
                put("id", template.id)
                put("message", template.message)
                put("category", template.category)
            }
            jsonArray.put(obj)
        }
        
        prefs.edit().putString(KEY_CUSTOM_TEMPLATES, jsonArray.toString()).apply()
    }
    
    /**
     * Enable/disable auto-reply after missed calls
     */
    fun setAutoReplyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_REPLY_ENABLED, enabled).apply()
        Log.d(tag, "Auto-reply ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Check if auto-reply is enabled
     */
    fun isAutoReplyEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_REPLY_ENABLED, false)
    }
    
    /**
     * Set auto-reply message
     */
    fun setAutoReplyMessage(message: String) {
        prefs.edit().putString(KEY_AUTO_REPLY_MESSAGE, message).apply()
        Log.d(tag, "Auto-reply message set to: $message")
    }
    
    /**
     * Get auto-reply message
     */
    fun getAutoReplyMessage(): String {
        return prefs.getString(KEY_AUTO_REPLY_MESSAGE, DEFAULT_TEMPLATES[0]) ?: DEFAULT_TEMPLATES[0]
    }
    
    /**
     * Log sent SMS messages
     */
    private fun logSentMessage(phoneNumber: String, message: String, timestamp: Long) {
        try {
            val log = getSentMessagesLog().toMutableList()
            log.add(
                SentMessage(
                    phoneNumber = phoneNumber,
                    message = message,
                    timestamp = timestamp
                )
            )
            
            // Keep only last 100 messages
            if (log.size > 100) {
                log.removeAt(0)
            }
            
            saveSentMessagesLog(log)
        } catch (e: Exception) {
            Log.e(tag, "Failed to log sent message", e)
        }
    }
    
    /**
     * Get log of sent messages
     */
    fun getSentMessagesLog(): List<SentMessage> {
        val messages = mutableListOf<SentMessage>()
        val json = prefs.getString(KEY_SENT_MESSAGES_LOG, "[]") ?: "[]"
        
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                messages.add(
                    SentMessage(
                        phoneNumber = obj.getString("phoneNumber"),
                        message = obj.getString("message"),
                        timestamp = obj.getLong("timestamp")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse sent messages log", e)
        }
        
        return messages
    }
    
    /**
     * Save sent messages log
     */
    private fun saveSentMessagesLog(messages: List<SentMessage>) {
        val jsonArray = JSONArray()
        messages.forEach { msg ->
            val obj = JSONObject().apply {
                put("phoneNumber", msg.phoneNumber)
                put("message", msg.message)
                put("timestamp", msg.timestamp)
            }
            jsonArray.put(obj)
        }
        
        prefs.edit().putString(KEY_SENT_MESSAGES_LOG, jsonArray.toString()).apply()
    }
    
    /**
     * Clear sent messages log
     */
    fun clearSentMessagesLog() {
        prefs.edit().putString(KEY_SENT_MESSAGES_LOG, "[]").apply()
        Log.d(tag, "Sent messages log cleared")
    }
    
    /**
     * Categorize message based on content
     */
    private fun categorizeMessage(message: String): String {
        val lowerMessage = message.lowercase()
        return when {
            lowerMessage.contains("meeting") -> CATEGORY_MEETING
            lowerMessage.contains("driving") || lowerMessage.contains("drive") -> CATEGORY_DRIVING
            lowerMessage.contains("busy") || lowerMessage.contains("work") -> CATEGORY_BUSY
            lowerMessage.contains("family") || lowerMessage.contains("personal") -> CATEGORY_PERSONAL
            else -> CATEGORY_CUSTOM
        }
    }
    
    /**
     * Data class for SMS template
     */
    data class SmsTemplate(
        val id: String,
        val message: String,
        val category: String,
        val isCustom: Boolean
    )
    
    /**
     * Data class for sent message log entry
     */
    data class SentMessage(
        val phoneNumber: String,
        val message: String,
        val timestamp: Long
    )
}
