package com.example.service

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import com.example.data.model.AutomationAgent
import com.example.data.model.CapturedNotificationEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

data class ReplyActionHandle(
    val actionIntent: PendingIntent,
    val remoteInputs: Array<RemoteInput>
)

data class SharedAudioPayload(
    val uri: Uri,
    val mimeType: String
)

object AgentEventBus {
    private const val TAG = "AgentEventBus"
    private const val MAX_CAPTURED_EVENTS = 40

    private val _capturedNotifications = MutableStateFlow<List<CapturedNotificationEvent>>(emptyList())
    val capturedNotifications: StateFlow<List<CapturedNotificationEvent>> = _capturedNotifications.asStateFlow()

    private val _liveNotificationStream = MutableSharedFlow<CapturedNotificationEvent>(extraBufferCapacity = 16)
    val liveNotificationStream: SharedFlow<CapturedNotificationEvent> = _liveNotificationStream.asSharedFlow()

    private val _sharedIncomingText = MutableStateFlow<String?>(null)
    val sharedIncomingText: StateFlow<String?> = _sharedIncomingText.asStateFlow()

    private val _sharedIncomingAudio = MutableStateFlow<SharedAudioPayload?>(null)
    val sharedIncomingAudio: StateFlow<SharedAudioPayload?> = _sharedIncomingAudio.asStateFlow()

    private val _isBackgroundServiceRunning = MutableStateFlow(false)
    val isBackgroundServiceRunning: StateFlow<Boolean> = _isBackgroundServiceRunning.asStateFlow()

    private val _lastBackgroundStatus = MutableStateFlow<String?>(null)
    val lastBackgroundStatus: StateFlow<String?> = _lastBackgroundStatus.asStateFlow()

    @Volatile
    var activeAgentsSnapshot: List<AutomationAgent> = emptyList()
        private set

    private val replyHandles = ConcurrentHashMap<String, ReplyActionHandle>()

    fun updateActiveAgentsSnapshot(agents: List<AutomationAgent>) {
        activeAgentsSnapshot = agents
    }

    fun setBackgroundServiceRunning(running: Boolean, statusNote: String? = null) {
        _isBackgroundServiceRunning.value = running
        if (statusNote != null) {
            _lastBackgroundStatus.value = statusNote
        }
    }

    fun updateBackgroundStatus(statusNote: String) {
        _lastBackgroundStatus.value = statusNote
    }

    fun publishCapturedNotification(
        event: CapturedNotificationEvent,
        replyHandle: ReplyActionHandle?
    ) {
        if (replyHandle != null) {
            replyHandles[event.id] = replyHandle
        }
        val updated = (listOf(event) + _capturedNotifications.value).take(MAX_CAPTURED_EVENTS)
        _capturedNotifications.value = updated
        _liveNotificationStream.tryEmit(event)
    }

    fun publishSharedText(text: String?) {
        if (!text.isNullOrBlank()) {
            _sharedIncomingText.value = text
        }
    }

    fun consumeSharedText() {
        _sharedIncomingText.value = null
    }

    fun publishSharedAudio(uri: Uri?, mimeType: String?) {
        if (uri != null) {
            _sharedIncomingAudio.value = SharedAudioPayload(
                uri = uri,
                mimeType = mimeType ?: "audio/*"
            )
        }
    }

    fun consumeSharedAudio() {
        _sharedIncomingAudio.value = null
    }

    fun sendInlineReply(context: Context, eventId: String, replyText: String): Boolean {
        val handle = replyHandles[eventId] ?: return false
        return try {
            val intent = Intent()
            val bundle = Bundle()
            handle.remoteInputs.forEach { input ->
                bundle.putCharSequence(input.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(handle.remoteInputs, intent, bundle)
            handle.actionIntent.send(context, 0, intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando respuesta rápida por RemoteInput", e)
            false
        }
    }

    fun isNotificationAccessEnabled(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.contains(context.packageName)
    }
}
