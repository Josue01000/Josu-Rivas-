package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AutomationAgent
import com.example.data.model.CapturedNotificationEvent
import com.example.data.model.DataRecord
import com.example.service.AgentEventBus
import com.example.service.BackgroundAgentService
import com.example.service.SharedAudioPayload
import com.example.ui.components.NlpResultCard
import com.example.ui.components.channelLabel
import com.example.ui.viewmodel.AiTaskState
import com.example.util.AppConnectors
import com.example.util.VoiceRecorderHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HubScreen(
    agents: List<AutomationAgent>,
    records: List<DataRecord>,
    aiState: AiTaskState,
    capturedNotifications: List<CapturedNotificationEvent>,
    sharedIncomingText: String?,
    sharedIncomingAudio: SharedAudioPayload?,
    isBackgroundRunning: Boolean,
    lastBackgroundStatus: String?,
    onConsumeSharedText: () -> Unit,
    onConsumeSharedAudio: () -> Unit,
    onAnalyzeNlp: (sourceChannel: String, text: String, bitmap: Bitmap?) -> Unit,
    onAnalyzeAudio: (sourceChannel: String, audioBytes: ByteArray, mimeType: String, extraText: String) -> Unit,
    onAskOrchestrator: (question: String) -> Unit,
    onSpeakText: (String) -> Unit,
    onResetAiState: () -> Unit,
    onLogExternalAction: (String, String, String, String, String) -> Unit
) {
    val context = LocalContext.current
    var selectedChannel by rememberSaveable { mutableStateOf("whatsapp") }
    var inputText by rememberSaveable { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var isRecordingAudio by remember { mutableStateOf(false) }
    var audioPermissionNote by remember { mutableStateOf<String?>(null) }
    val voiceRecorder = remember(context) { VoiceRecorderHelper(context) }

    DisposableEffect(Unit) {
        onDispose {
            voiceRecorder.stopSilently()
        }
    }

    val listenerEnabled = remember(capturedNotifications.size, isBackgroundRunning) {
        AgentEventBus.isNotificationAccessEnabled(context)
    }

    LaunchedEffect(sharedIncomingText) {
        if (!sharedIncomingText.isNullOrBlank()) {
            inputText = sharedIncomingText
            onConsumeSharedText()
        }
    }

    LaunchedEffect(sharedIncomingAudio) {
        if (sharedIncomingAudio != null) {
            val loaded = VoiceRecorderHelper.readAudioUriBytes(
                context = context,
                uri = sharedIncomingAudio.uri,
                fallbackMime = sharedIncomingAudio.mimeType
            )
            onConsumeSharedAudio()
            if (loaded != null) {
                onAnalyzeAudio(selectedChannel, loaded.first, loaded.second, inputText)
            } else {
                audioPermissionNote = "No se pudo leer el audio compartido (o supera 18 MB)."
            }
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            audioPermissionNote = null
            isRecordingAudio = voiceRecorder.startRecording()
            if (!isRecordingAudio) {
                audioPermissionNote = "No se pudo inicializar el micrófono del dispositivo."
            }
        } else {
            audioPermissionNote = "Permiso de micrófono denegado. Actívalo en Ajustes para enviar pedidos de voz."
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            BackgroundAgentService.start(context)
        } else {
            audioPermissionNote = "Se requiere permiso de notificaciones para mantener el agente activo en segundo plano."
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        selectedImageUri = uri
        selectedBitmap = uri?.let { loadScaledBitmap(context, it) }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(152.dp)
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_orchestrator),
                        contentDescription = "Centro de comando Nexo AI",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.35f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Centro de Comando PLN",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${agents.count { it.enabled }} agentes activos  •  ${records.size} registros en bóveda",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Psychology,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Voz, Audio y Procesador PLN",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("whatsapp", "gmail", "calendar", "gemini_app").forEach { channel ->
                                FilterChip(
                                    selected = selectedChannel == channel,
                                    onClick = { selectedChannel = channel },
                                    label = { Text(channelLabel(channel)) },
                                    modifier = Modifier.testTag("channel_chip_$channel")
                                )
                            }
                        }

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            label = { Text("Pega texto o dicta un pedido con el botón de voz") },
                            placeholder = {
                                Text("Ej: 'Haz una invitación por WhatsApp para la junta de lanzamiento este viernes a las 6pm'")
                            },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("nlp_input_field")
                        )

                        if (isRecordingAudio) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.GraphicEq,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "Escuchando tu pedido... Toca 'Detener y Enviar' para que Gemini lo procese.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        if (audioPermissionNote != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = audioPermissionNote.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        if (selectedImageUri != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AsyncImage(
                                    model = selectedImageUri,
                                    contentDescription = "Captura adjunta para análisis multimodal",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                Text(
                                    text = "Imagen lista para OCR y PLN multimodal",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        selectedImageUri = null
                                        selectedBitmap = null
                                    }
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Quitar imagen")
                                }
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    if (isRecordingAudio) {
                                        val recorded = voiceRecorder.stopAndReadBytes()
                                        isRecordingAudio = false
                                        if (recorded != null) {
                                            onAnalyzeAudio(selectedChannel, recorded.first, recorded.second, inputText)
                                        } else {
                                            audioPermissionNote = "El audio grabado fue demasiado corto o vacío."
                                        }
                                    } else {
                                        val hasMicPermission = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasMicPermission) {
                                            audioPermissionNote = null
                                            isRecordingAudio = voiceRecorder.startRecording()
                                        } else {
                                            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                                colors = if (isRecordingAudio) {
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                } else {
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    )
                                },
                                enabled = aiState !is AiTaskState.Loading,
                                modifier = Modifier.testTag("voice_command_button")
                            ) {
                                Icon(
                                    imageVector = if (isRecordingAudio) Icons.Filled.Stop else Icons.Filled.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (isRecordingAudio) "Detener y Enviar" else "Pedido por Voz")
                            }

                            Button(
                                onClick = {
                                    onAnalyzeNlp(selectedChannel, inputText, selectedBitmap)
                                },
                                enabled = aiState !is AiTaskState.Loading && !isRecordingAudio,
                                modifier = Modifier.testTag("analyze_nlp_button")
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Analizar Texto")
                            }

                            FilledTonalButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.testTag("pick_screenshot_button")
                            ) {
                                Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Captura")
                            }

                            OutlinedButton(
                                onClick = {
                                    onAskOrchestrator(
                                        inputText.ifBlank {
                                            "Dame un plan de acción con mis pendientes y sugiere qué automatizar hoy."
                                        }
                                    )
                                },
                                enabled = aiState !is AiTaskState.Loading && !isRecordingAudio,
                                modifier = Modifier.testTag("ask_orchestrator_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Orquestador IA")
                            }
                        }
                    }
                }
            }

            item {
                when (aiState) {
                    is AiTaskState.Idle -> {}
                    is AiTaskState.Loading -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Text(
                                    text = "Procesando audio/texto e intenciones con Gemini 3.8 Flash...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    is AiTaskState.Streaming -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Respuesta en tiempo real (Gemini)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(text = aiState.partialText, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    is AiTaskState.CompletedText -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Síntesis del Orquestador",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    OutlinedButton(onClick = onResetAiState) { Text("Limpiar") }
                                }
                                Text(text = aiState.text, style = MaterialTheme.typography.bodyMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(onClick = { AppConnectors.openWhatsAppWithText(context, aiState.text) }) {
                                        Text("Enviar por WhatsApp")
                                    }
                                    OutlinedButton(onClick = { onSpeakText(aiState.text) }) {
                                        Text("Escuchar")
                                    }
                                }
                            }
                        }
                    }

                    is AiTaskState.Analyzed -> {
                        NlpResultCard(
                            result = aiState.result,
                            onSendWhatsApp = { reply ->
                                AppConnectors.openWhatsAppWithText(context, reply)
                                onLogExternalAction(
                                    aiState.result.matchedRuleName,
                                    "whatsapp",
                                    aiState.result.intent,
                                    aiState.result.summary,
                                    "Respuesta/invitación enviada a WhatsApp"
                                )
                            },
                            onSendGmail = { subject, body ->
                                AppConnectors.openGmailCompose(context, subject = subject, body = body)
                                onLogExternalAction(
                                    aiState.result.matchedRuleName,
                                    "gmail",
                                    aiState.result.intent,
                                    subject,
                                    "Borrador abierto en Gmail"
                                )
                            },
                            onAddCalendar = { title, desc, loc, iso ->
                                AppConnectors.insertCalendarEvent(context, title, desc, loc, iso)
                                onLogExternalAction(
                                    "Extractor Calendario",
                                    "calendar",
                                    "agendar_evento",
                                    title,
                                    "Evento enviado a Google Calendar ($iso)"
                                )
                            },
                            onSpeakSummary = onSpeakText
                        )
                    }

                    is AiTaskState.Error -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Error en procesamiento IA",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = aiState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        1.dp,
                        if (isBackgroundRunning) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PowerSettingsNew,
                                    contentDescription = null,
                                    tint = if (isBackgroundRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column {
                                    Text(
                                        text = "Agente en Segundo Plano (Pantalla Apagada)",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = lastBackgroundStatus
                                            ?: if (isBackgroundRunning) "Activo: responde pedidos de WhatsApp sin encender pantalla"
                                            else "Inactivo: enciéndelo para automatizar con pantalla apagada",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = isBackgroundRunning,
                                onCheckedChange = { enable ->
                                    if (enable) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            val granted = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (granted) {
                                                BackgroundAgentService.start(context)
                                            } else {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        } else {
                                            BackgroundAgentService.start(context)
                                        }
                                    } else {
                                        BackgroundAgentService.stop(context)
                                    }
                                },
                                modifier = Modifier.testTag("background_agent_switch")
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Cómo funciona con pantalla apagada:\n" +
                                    "• Si llega un mensaje de WhatsApp con palabras como 'Nexo', 'Gemini', 'invitación' o tus disparadores activos, Nexo AI lo procesa en segundo plano y responde automáticamente por la notificación (RemoteInput) sin encender la pantalla.\n" +
                                    "• Notas de voz: Android no permite grabar audio pasivo con pantalla bloqueada ni expone los audios cifrados de WhatsApp en notificaciones; para audios, usa 'Pedido por Voz' arriba o 'Compartir -> Nexo AI' desde una nota de voz.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (listenerEnabled) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                                    contentDescription = null,
                                    tint = if (listenerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                                )
                                Column {
                                    Text(
                                        text = "Monitor en Vivo (WhatsApp / Gmail)",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = if (listenerEnabled) "Escuchando notificaciones entrantes"
                                        else "Permiso de notificaciones requerido para pantalla apagada",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (!listenerEnabled) {
                                FilledTonalButton(
                                    onClick = { AppConnectors.openNotificationListenerSettings(context) },
                                    modifier = Modifier.testTag("enable_notification_listener_button")
                                ) {
                                    Text("Activar")
                                }
                            }
                        }

                        if (capturedNotifications.isEmpty()) {
                            Text(
                                text = "Aún no hay notificaciones capturadas en esta sesión. También puedes usar 'Compartir -> Nexo AI' con texto o notas de voz desde WhatsApp, Gmail o Gemini.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(capturedNotifications, key = { it.id }) { event ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${event.appLabel} • ${event.sender}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(event.timestampMillis)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(text = event.message, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    val channel = when (event.appLabel) {
                                        "WhatsApp" -> "whatsapp"
                                        "Gmail" -> "gmail"
                                        else -> "gemini_app"
                                    }
                                    selectedChannel = channel
                                    inputText = "${event.sender}: ${event.message}"
                                    onAnalyzeNlp(channel, inputText, null)
                                }
                            ) {
                                Text("Procesar con IA")
                            }
                            if (event.hasInlineReply) {
                                OutlinedButton(
                                    onClick = {
                                        val sent = AgentEventBus.sendInlineReply(
                                            context = context,
                                            eventId = event.id,
                                            replyText = "Recibido por Nexo AI, en un momento te respondo."
                                        )
                                        if (sent) {
                                            onLogExternalAction(
                                                "Monitor WhatsApp",
                                                "whatsapp",
                                                "respuesta_rapida",
                                                event.message,
                                                "Respuesta automática enviada vía notificación"
                                            )
                                        }
                                    }
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Auto-Responder")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun loadScaledBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        val maxDim = 1280
        val ratio = minOf(maxDim.toFloat() / raw.width, maxDim.toFloat() / raw.height, 1f)
        if (ratio < 1f) {
            Bitmap.createScaledBitmap(raw, (raw.width * ratio).toInt(), (raw.height * ratio).toInt(), true)
        } else {
            raw
        }
    } catch (_: Exception) {
        null
    }
}
