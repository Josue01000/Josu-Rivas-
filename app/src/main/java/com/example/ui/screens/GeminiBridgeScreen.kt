package com.example.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.GeminiCustomApp
import com.example.ui.components.ChannelBadge
import com.example.ui.components.channelLabel
import com.example.ui.viewmodel.AiTaskState
import com.example.util.AppConnectors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GeminiBridgeScreen(
    geminiApps: List<GeminiCustomApp>,
    aiState: AiTaskState,
    onSaveGeminiApp: (name: String, purpose: String, systemPrompt: String, targetChannel: String) -> Unit,
    onRunGeminiApp: (GeminiCustomApp, payload: String, bitmap: Bitmap?) -> Unit,
    onDeleteGeminiApp: (String) -> Unit,
    onSpeakText: (String) -> Unit,
    onLogExternalAction: (String, String, String, String, String) -> Unit
) {
    val context = LocalContext.current
    var showConnectDialog by remember { mutableStateOf(false) }
    var appToRun by remember { mutableStateOf<GeminiCustomApp?>(null) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(155.dp)
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_gemini_bridge),
                        contentDescription = "Puente con tu app del chat de Gemini",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Conector App de Gemini",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Conecta tu app del chat de Gemini para resumir y ejecutar acciones en WhatsApp, Gmail o Calendar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { showConnectDialog = true },
                        modifier = Modifier.testTag("connect_gemini_app_button")
                    ) {
                        Icon(Icons.Filled.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Importar App del Chat")
                    }

                    FilledTonalButton(
                        onClick = { AppConnectors.openGeminiApp(context) },
                        modifier = Modifier.testTag("open_external_gemini_app_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Abrir App Gemini")
                    }
                }
            }

            if (aiState is AiTaskState.Loading || aiState is AiTaskState.Streaming || aiState is AiTaskState.CompletedText) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Resultado de tu App Gemini", style = MaterialTheme.typography.titleMedium)
                            }

                            when (aiState) {
                                is AiTaskState.Loading -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                        Text("Ejecutando flujo con Gemini 3.8 Flash...", style = MaterialTheme.typography.bodySmall)
                                    }
                                }

                                is AiTaskState.Streaming -> {
                                    Text(text = aiState.partialText, style = MaterialTheme.typography.bodyMedium)
                                }

                                is AiTaskState.CompletedText -> {
                                    Text(text = aiState.text, style = MaterialTheme.typography.bodyMedium)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilledTonalButton(onClick = {
                                            AppConnectors.openWhatsAppWithText(context, aiState.text)
                                            onLogExternalAction("Gemini Bridge", "whatsapp", "compartir_resumen", aiState.text.take(80), "Enviado a WhatsApp")
                                        }) {
                                            Text("Enviar WhatsApp")
                                        }
                                        OutlinedButton(onClick = {
                                            AppConnectors.openGmailCompose(
                                                context = context,
                                                subject = "Resumen generado con Nexo + Gemini",
                                                body = aiState.text
                                            )
                                            onLogExternalAction("Gemini Bridge", "gmail", "enviar_resumen", aiState.text.take(80), "Abierto en Gmail")
                                        }) {
                                            Text("Enviar Gmail")
                                        }
                                        OutlinedButton(onClick = {
                                            AppConnectors.insertCalendarEvent(
                                                context = context,
                                                title = "Seguimiento Gemini App",
                                                description = aiState.text
                                            )
                                        }) {
                                            Text("Agendar")
                                        }
                                        OutlinedButton(onClick = { onSpeakText(aiState.text) }) {
                                            Text("Escuchar")
                                        }
                                    }
                                }

                                else -> {}
                            }
                        }
                    }
                }
            }

            if (geminiApps.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Conecta la app que creaste en el chat de Gemini",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "1. Copia el prompt o propósito de la app que tienes en tu chat de Gemini (o compártelo desde Gemini hacia Nexo AI).\n" +
                                    "2. Regístrala con 'Importar App del Chat'.\n" +
                                    "3. Ejecútala aquí con nuevos textos o capturas y encadena el resultado a WhatsApp, Gmail o Calendario.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(geminiApps, key = { it.id }) { app ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ChannelBadge(app.targetChannel)
                                IconButton(
                                    onClick = { onDeleteGeminiApp(app.id) },
                                    modifier = Modifier.testTag("delete_gemini_app_${app.id}")
                                ) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Eliminar app")
                                }
                            }
                            Text(text = app.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = app.purpose,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (app.lastResult.isNotBlank()) {
                                Text(
                                    text = "Último resultado: ${app.lastResult}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = { appToRun = app },
                                modifier = Modifier.testTag("run_gemini_app_${app.id}")
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Ejecutar y Encadenar")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConnectDialog) {
        ConnectGeminiAppDialog(
            onDismiss = { showConnectDialog = false },
            onSave = { name, purpose, prompt, target ->
                onSaveGeminiApp(name, purpose, prompt, target)
                showConnectDialog = false
            }
        )
    }

    val runningApp = appToRun
    if (runningApp != null) {
        RunGeminiAppDialog(
            app = runningApp,
            onDismiss = { appToRun = null },
            onExecute = { payload, bitmap ->
                onRunGeminiApp(runningApp, payload, bitmap)
                appToRun = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectGeminiAppDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var targetChannel by remember { mutableStateOf("whatsapp") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importar App del Chat de Gemini") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre de tu app") },
                    placeholder = { Text("Ej: Analizador de Ventas Chat") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("gemini_app_name_input")
                )
                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it },
                    label = { Text("Qué hace (resumen)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Instrucciones / Prompt del chat de Gemini") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth().testTag("gemini_app_prompt_input")
                )
                Text("Acción destino preferida:", style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("whatsapp", "gmail", "calendar", "data_vault").forEach { target ->
                        FilterChip(
                            selected = targetChannel == target,
                            onClick = { targetChannel = target },
                            label = { Text(channelLabel(target), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, purpose, prompt, targetChannel) },
                modifier = Modifier.testTag("confirm_connect_gemini_app_button")
            ) {
                Text("Conectar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun RunGeminiAppDialog(
    app: GeminiCustomApp,
    onDismiss: () -> Unit,
    onExecute: (String, Bitmap?) -> Unit
) {
    val context = LocalContext.current
    var payload by remember { mutableStateOf("") }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        bitmap = uri?.let { loadBitmapForGemini(context, it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ejecutar: ${app.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = payload,
                    onValueChange = { payload = it },
                    label = { Text("Pega texto o resultado del chat") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().testTag("run_gemini_payload_input")
                )
                OutlinedButton(
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (bitmap != null) "Captura adjunta ✓" else "Adjuntar captura")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onExecute(payload, bitmap) },
                modifier = Modifier.testTag("confirm_run_gemini_app_button")
            ) {
                Text("Ejecutar IA")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Suppress("DEPRECATION")
private fun loadBitmapForGemini(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
        } else {
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        val ratio = minOf(1280f / raw.width, 1280f / raw.height, 1f)
        if (ratio < 1f) Bitmap.createScaledBitmap(raw, (raw.width * ratio).toInt(), (raw.height * ratio).toInt(), true)
        else raw
    } catch (_: Exception) {
        null
    }
}
