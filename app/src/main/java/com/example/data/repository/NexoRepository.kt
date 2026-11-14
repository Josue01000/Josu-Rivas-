package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AgentExecutionLog
import com.example.data.model.AutomationAgent
import com.example.data.model.DataRecord
import com.example.data.model.GeminiCustomApp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class NexoRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("El usuario debe iniciar sesión con Google antes de acceder a Firestore.")
    }

    private fun agentsCollection(userId: String) =
        db.collection("users").document(userId).collection("agents")

    private fun recordsCollection(userId: String) =
        db.collection("users").document(userId).collection("data_records")

    private fun geminiAppsCollection(userId: String) =
        db.collection("users").document(userId).collection("gemini_apps")

    private fun logsCollection(userId: String) =
        db.collection("users").document(userId).collection("execution_logs")

    fun observeAgents(userId: String): Flow<List<AutomationAgent>> = flow {
        val path = "users/$userId/agents"
        emitAll(
            agentsCollection(userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            AutomationAgent::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                }
                .catch { error ->
                    logStreamError(error, path, userId)
                    throw error
                }
        )
    }

    fun observeDataRecords(userId: String): Flow<List<DataRecord>> = flow {
        val path = "users/$userId/data_records"
        emitAll(
            recordsCollection(userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            DataRecord::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                }
                .catch { error ->
                    logStreamError(error, path, userId)
                    throw error
                }
        )
    }

    fun observeGeminiApps(userId: String): Flow<List<GeminiCustomApp>> = flow {
        val path = "users/$userId/gemini_apps"
        emitAll(
            geminiAppsCollection(userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            GeminiCustomApp::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                }
                .catch { error ->
                    logStreamError(error, path, userId)
                    throw error
                }
        )
    }

    fun observeExecutionLogs(userId: String): Flow<List<AgentExecutionLog>> = flow {
        val path = "users/$userId/execution_logs"
        emitAll(
            logsCollection(userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            AgentExecutionLog::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                }
                .catch { error ->
                    logStreamError(error, path, userId)
                    throw error
                }
        )
    }

    suspend fun createAgent(
        name: String,
        channel: String,
        description: String,
        triggerCondition: String,
        actionTemplate: String,
        autoExecute: Boolean,
        enabled: Boolean = true
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = agentsCollection(uid).document()
        val payload = mapOf(
            "userId" to uid,
            "name" to name.take(100),
            "channel" to channel,
            "description" to description.take(400),
            "triggerCondition" to triggerCondition.take(300),
            "actionTemplate" to actionTemplate.take(1000),
            "autoExecute" to autoExecute,
            "enabled" to enabled,
            "runsCount" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun toggleAgent(agent: AutomationAgent, enabled: Boolean, autoExecute: Boolean): Result<Unit> =
        runCatching {
            val uid = requireUserId()
            require(agent.id.isNotBlank()) { "Agent ID requerido" }
            val docRef = agentsCollection(uid).document(agent.id)
            try {
                docRef.update(
                    mapOf(
                        "enabled" to enabled,
                        "autoExecute" to autoExecute,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
        }

    suspend fun incrementAgentRuns(agent: AutomationAgent): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(agent.id.isNotBlank()) { "Agent ID requerido" }
        val docRef = agentsCollection(uid).document(agent.id)
        try {
            docRef.update(
                mapOf(
                    "runsCount" to agent.runsCount + 1L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteAgent(agentId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = agentsCollection(uid).document(agentId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun createDataRecord(
        sourceApp: String,
        category: String,
        intent: String,
        title: String,
        content: String,
        extractedEntities: String,
        suggestedReply: String,
        status: String = "pending",
        eventTitle: String = "",
        eventIsoDateTime: String = ""
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = recordsCollection(uid).document()
        val payload = mapOf(
            "userId" to uid,
            "sourceApp" to sourceApp,
            "category" to category,
            "intent" to intent.take(120).ifBlank { "analizar" },
            "title" to title.take(200).ifBlank { "Registro Nexo AI" },
            "content" to content.take(5000),
            "extractedEntities" to extractedEntities.take(1500),
            "suggestedReply" to suggestedReply.take(2000),
            "status" to status,
            "eventTitle" to eventTitle.take(200),
            "eventIsoDateTime" to eventIsoDateTime.take(64),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateRecordStatus(
        recordId: String,
        newStatus: String,
        newCategory: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = recordsCollection(uid).document(recordId)
        try {
            docRef.update(
                mapOf(
                    "status" to newStatus,
                    "category" to newCategory,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteDataRecord(recordId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = recordsCollection(uid).document(recordId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun createGeminiCustomApp(
        name: String,
        purpose: String,
        systemPrompt: String,
        targetChannel: String,
        lastResult: String = ""
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = geminiAppsCollection(uid).document()
        val payload = mapOf(
            "userId" to uid,
            "name" to name.take(100),
            "purpose" to purpose.take(300),
            "systemPrompt" to systemPrompt.take(4000),
            "targetChannel" to targetChannel,
            "lastResult" to lastResult.take(5000),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateGeminiAppResult(appId: String, lastResult: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = geminiAppsCollection(uid).document(appId)
        try {
            docRef.update(
                mapOf(
                    "lastResult" to lastResult.take(5000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteGeminiApp(appId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = geminiAppsCollection(uid).document(appId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun addExecutionLog(
        agentName: String,
        channel: String,
        detectedIntent: String,
        inputPreview: String,
        outputAction: String,
        status: String = "success"
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = logsCollection(uid).document()
        val payload = mapOf(
            "userId" to uid,
            "agentName" to agentName.take(120).ifBlank { "Nexo Orquestador" },
            "channel" to channel,
            "detectedIntent" to detectedIntent.take(120).ifBlank { "procesado" },
            "inputPreview" to inputPreview.take(400),
            "outputAction" to outputAction.take(600).ifBlank { "Completado" },
            "status" to status,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteExecutionLog(logId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = logsCollection(uid).document(logId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    private fun logStreamError(error: Throwable, path: String, uid: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
        }
    }
}
