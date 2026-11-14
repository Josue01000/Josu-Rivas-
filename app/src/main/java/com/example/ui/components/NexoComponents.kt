package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AutomationAgent
import com.example.data.model.DataRecord
import com.example.data.model.NlpAnalysisResult

fun channelLabel(channel: String): String = when (channel) {
    "whatsapp" -> "WhatsApp"
    "gmail" -> "Gmail"
    "calendar" -> "Calendario"
    "gemini_app" -> "Gemini App"
    "omnichannel" -> "Omnicanal"
    "data_vault" -> "Bóveda Datos"
    else -> "General"
}

fun channelIcon(channel: String): ImageVector = when (channel) {
    "whatsapp" -> Icons.AutoMirrored.Filled.Chat
    "gmail" -> Icons.Filled.Email
    "calendar" -> Icons.Filled.CalendarMonth
    "gemini_app" -> Icons.Filled.AutoAwesome
    else -> Icons.Filled.Hub
}

@Composable
fun ChannelBadge(channel: String, modifier: Modifier = Modifier) {
    val (bg, fg) = when (channel) {
        "whatsapp" -> Color(0xFF064E3B) to Color(0xFF6EE7B7)
        "gmail" -> Color(0xFF7F1D1D) to Color(0xFFFCA5A5)
        "calendar" -> Color(0xFF164E63) to Color(0xFF67E8F9)
        "gemini_app" -> Color(0xFF4C1D95) to Color(0xFFDDD6FE)
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = channelIcon(channel),
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = channelLabel(channel),
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NlpResultCard(
    result: NlpAnalysisResult,
    onSendWhatsApp: (String) -> Unit,
    onSendGmail: (String, String) -> Unit,
    onAddCalendar: (String, String, String, String) -> Unit,
    onSpeakSummary: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Psychology,
                        contentDescription = "Resultado PLN",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Intención: ${result.intent}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = { onSpeakSummary("${result.summary}. Respuesta sugerida: ${result.suggestedReply}") },
                    modifier = Modifier.testTag("speak_nlp_summary_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Escuchar resumen con voz",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text("Categoría: ${result.category}", style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                AssistChip(
                    onClick = {},
                    label = { Text("Urgencia: ${result.urgency}", style = MaterialTheme.typography.labelSmall) }
                )
                AssistChip(
                    onClick = {},
                    label = { Text("Confianza: ${result.confidencePercent}%", style = MaterialTheme.typography.labelSmall) }
                )
                if (result.shouldAutoArchive) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Auto-Archivado", style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    )
                }
            }

            if (result.summary.isNotBlank()) {
                Text(
                    text = result.summary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (result.extractedEntities.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Entidades extraídas (PLN)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = result.extractedEntities,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (result.hasCalendarEvent && result.eventTitle.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = "Evento detectado",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = result.eventTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = listOf(result.eventIsoDateTime, result.eventLocation)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" • ")
                                    .ifBlank { "Fecha sugerida: mañana 09:00" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            if (result.suggestedReply.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Respuesta / Acción (${result.matchedRuleName})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = result.suggestedReply,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (result.suggestedReply.isNotBlank()) {
                    FilledTonalButton(
                        onClick = { onSendWhatsApp(result.suggestedReply) },
                        modifier = Modifier.testTag("dispatch_whatsapp_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Enviar WhatsApp")
                    }
                    OutlinedButton(
                        onClick = {
                            onSendGmail(
                                "Respuesta: ${result.summary.take(40)}",
                                result.suggestedReply
                            )
                        },
                        modifier = Modifier.testTag("dispatch_gmail_button")
                    ) {
                        Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Abrir Gmail")
                    }
                }
                if (result.hasCalendarEvent || result.eventTitle.isNotBlank()) {
                    FilledTonalButton(
                        onClick = {
                            onAddCalendar(
                                result.eventTitle.ifBlank { result.summary.take(50) },
                                result.summary,
                                result.eventLocation,
                                result.eventIsoDateTime
                            )
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.testTag("dispatch_calendar_button")
                    ) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Agendar Evento")
                    }
                }
            }
        }
    }
}

@Composable
fun AgentRuleCard(
    agent: AutomationAgent,
    onToggle: (Boolean, Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (agent.enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChannelBadge(agent.channel)
                        Text(
                            text = "${agent.runsCount} ejec.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = agent.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Switch(
                    checked = agent.enabled,
                    onCheckedChange = { onToggle(it, agent.autoExecute) },
                    modifier = Modifier.testTag("agent_switch_${agent.id}")
                )
            }

            if (agent.description.isNotBlank()) {
                Text(
                    text = agent.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Disparador: ${agent.triggerCondition}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Acción: ${agent.actionTemplate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = if (agent.autoExecute) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (agent.autoExecute) "Ejecución autónoma" else "Requiere confirmación",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_agent_${agent.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Eliminar agente",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DataRecordCard(
    record: DataRecord,
    onSendWhatsApp: () -> Unit,
    onSendGmail: () -> Unit,
    onAddCalendar: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ChannelBadge(record.sourceApp)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = record.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_record_${record.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Eliminar registro",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = record.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = record.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            if (record.extractedEntities.isNotBlank()) {
                Text(
                    text = "Datos: ${record.extractedEntities}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (record.eventTitle.isNotBlank() || record.eventIsoDateTime.isNotBlank()) {
                    FilledTonalButton(
                        onClick = onAddCalendar,
                        modifier = Modifier.testTag("record_calendar_${record.id}")
                    ) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Agendar")
                    }
                }
                if (record.suggestedReply.isNotBlank()) {
                    OutlinedButton(onClick = onSendWhatsApp) {
                        Text("WhatsApp")
                    }
                    OutlinedButton(onClick = onSendGmail) {
                        Text("Gmail")
                    }
                }
                if (record.status != "archived") {
                    OutlinedButton(
                        onClick = onArchive,
                        modifier = Modifier.testTag("archive_record_${record.id}")
                    ) {
                        Icon(Icons.Filled.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Archivar")
                    }
                }
            }
        }
    }
}
