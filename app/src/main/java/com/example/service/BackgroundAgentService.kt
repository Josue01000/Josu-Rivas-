package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.ai.GeminiAgentEngine
import com.example.data.model.AutomationAgent
import com.example.data.model.CapturedNotificationEvent
import com.example.data.repository.NexoRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackgroundAgentService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val aiEngine = GeminiAgentEngine()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildStatusNotification("Escuchando pedidos y reglas con pantalla apagada"))
        AgentEventBus.setBackgroundServiceRunning(true, "Agente en segundo plano activo")

        serviceScope.launch {
            AgentEventBus.liveNotificationStream.collect { event ->
                processIncomingEventAutonomously(event)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        AgentEventBus.setBackgroundServiceRunning(false, "Agente en segundo plano detenido")
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun processIncomingEventAutonomously(event: CapturedNotificationEvent) {
        val textLower = event.message.lowercase(Locale.getDefault())
        if (event.message.startsWith("[Nexo AI]")) return

        val channel = when (event.appLabel) {
            "WhatsApp" -> "whatsapp"
            "Gmail" -> "gmail"
            else -> "gemini_app"
        }

        val activeAgents = AgentEventBus.activeAgentsSnapshot.filter { it.enabled && it.autoExecute }
        val matchingRule: AutomationAgent? = activeAgents.firstOrNull { agent ->
            (agent.channel == channel || agent.channel == "omnichannel") &&
                agent.triggerCondition.split(",")
                    .map { it.trim().lowercase(Locale.getDefault()) }
                    .any { kw -> kw.isNotBlank() && textLower.contains(kw) }
        }

        val isDirectAiCommand = DIRECT_COMMAND_KEYWORDS.any { textLower.contains(it) }
        if (matchingRule == null && !isDirectAiCommand) {
            return
        }

        try {
            updateNotification("Procesando pedido de ${event.sender} con Gemini...")
            val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).format(Date())
            val analysis = aiEngine.analyzeInputWithNlp(
                sourceChannel = channel,
                inputText = "Remitente: ${event.sender}\nMensaje: ${event.message}",
                activeAgents = activeAgents,
                currentDateIso = nowIso
            )

            var autoReplied = false
            if (channel == "whatsapp" && event.hasInlineReply && analysis.suggestedReply.isNotBlank()) {
                val replyPayload = "[Nexo AI] ${analysis.suggestedReply}"
                autoReplied = AgentEventBus.sendInlineReply(applicationContext, event.id, replyPayload)
            }

            if (Firebase.auth.currentUser != null) {
                val repository = NexoRepository(applicationContext)
                repository.createDataRecord(
                    sourceApp = channel,
                    category = if (analysis.shouldAutoArchive && channel == "gmail") "Archivado" else analysis.category,
                    intent = analysis.intent,
                    title = analysis.eventTitle.ifBlank { "Auto (${event.sender}): ${analysis.summary.take(60)}" },
                    content = "${event.message}\n\nRespuesta/Resultado IA:\n${analysis.suggestedReply}",
                    extractedEntities = analysis.extractedEntities,
                    suggestedReply = analysis.suggestedReply,
                    status = if (autoReplied) "executed" else if (analysis.shouldAutoArchive) "archived" else "pending",
                    eventTitle = analysis.eventTitle,
                    eventIsoDateTime = analysis.eventIsoDateTime
                )
                if (matchingRule != null) {
                    repository.incrementAgentRuns(matchingRule)
                }
                repository.addExecutionLog(
                    agentName = matchingRule?.name ?: "Agente Segundo Plano",
                    channel = channel,
                    detectedIntent = analysis.intent,
                    inputPreview = event.message.take(120),
                    outputAction = if (autoReplied) {
                        "Respondido automáticamente por WhatsApp en segundo plano"
                    } else {
                        "Procesado en segundo plano y guardado en Bóveda"
                    },
                    status = "success"
                )
            }

            val summaryMsg = if (autoReplied) {
                "Auto-respondido a ${event.sender}: ${analysis.intent}"
            } else {
                "Procesado (${event.sender}): ${analysis.category}"
            }
            updateNotification(summaryMsg)
            AgentEventBus.updateBackgroundStatus(summaryMsg)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error ejecutando agente en segundo plano", e)
            val errMsg = "Error en segundo plano: ${e.localizedMessage ?: "revisa conexión"}"
            updateNotification(errMsg)
            AgentEventBus.updateBackgroundStatus(errMsg)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Agente Autónomo Nexo AI",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene activos los agentes de WhatsApp y Gmail con la pantalla apagada."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildStatusNotification(contentText: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Nexo AI • Agente Autónomo Activo")
            .setContentText(contentText)
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildStatusNotification(contentText))
    }

    companion object {
        private const val TAG = "BackgroundAgentService"
        private const val CHANNEL_ID = "nexo_background_agent"
        private const val NOTIFICATION_ID = 4041
        const val ACTION_STOP = "com.example.action.STOP_BACKGROUND_AGENT"

        private val DIRECT_COMMAND_KEYWORDS = listOf(
            "nexo",
            "gemini",
            "invitación",
            "invitacion",
            "resume",
            "resumen",
            "agenda"
        )

        fun start(context: Context) {
            val intent = Intent(context, BackgroundAgentService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BackgroundAgentService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
