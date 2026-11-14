package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class AutomationAgent(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val channel: String = "whatsapp",
    val description: String = "",
    val triggerCondition: String = "",
    val actionTemplate: String = "",
    val autoExecute: Boolean = true,
    val enabled: Boolean = true,
    val runsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DataRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sourceApp: String = "manual",
    val category: String = "General",
    val intent: String = "analizar_datos",
    val title: String = "",
    val content: String = "",
    val extractedEntities: String = "",
    val suggestedReply: String = "",
    val status: String = "pending",
    val eventTitle: String = "",
    val eventIsoDateTime: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class GeminiCustomApp(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val purpose: String = "",
    val systemPrompt: String = "",
    val targetChannel: String = "data_vault",
    val lastResult: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AgentExecutionLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val agentName: String = "",
    val channel: String = "omnichannel",
    val detectedIntent: String = "",
    val inputPreview: String = "",
    val outputAction: String = "",
    val status: String = "success",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class NlpAnalysisResult(
    val intent: String,
    val category: String,
    val confidencePercent: Int,
    val urgency: String,
    val summary: String,
    val extractedEntities: String,
    val suggestedReply: String,
    val shouldAutoArchive: Boolean,
    val hasCalendarEvent: Boolean,
    val eventTitle: String,
    val eventIsoDateTime: String,
    val eventLocation: String,
    val recommendedChannel: String,
    val matchedRuleName: String
)

data class CapturedNotificationEvent(
    val id: String,
    val packageName: String,
    val appLabel: String,
    val sender: String,
    val message: String,
    val timestampMillis: Long,
    val hasInlineReply: Boolean
)
