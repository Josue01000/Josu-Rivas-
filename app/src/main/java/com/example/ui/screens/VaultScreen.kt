package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AgentExecutionLog
import com.example.data.model.DataRecord
import com.example.ui.components.ChannelBadge
import com.example.ui.components.DataRecordCard
import com.example.ui.components.channelLabel
import com.example.util.AppConnectors
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VaultScreen(
    records: List<DataRecord>,
    logs: List<AgentExecutionLog>,
    onArchiveRecord: (DataRecord) -> Unit,
    onMarkExecuted: (DataRecord) -> Unit,
    onDeleteRecord: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedSegment by rememberSaveable { mutableIntStateOf(0) } // 0 = Datos, 1 = Historial
    var sourceFilter by rememberSaveable { mutableStateOf("all") }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredRecords = remember(records, sourceFilter, searchQuery) {
        records.filter { rec ->
            val matchesFilter = when (sourceFilter) {
                "all" -> true
                "archived" -> rec.status == "archived"
                else -> rec.sourceApp == sourceFilter
            }
            val matchesQuery = searchQuery.isBlank() ||
                rec.title.contains(searchQuery, ignoreCase = true) ||
                rec.content.contains(searchQuery, ignoreCase = true) ||
                rec.extractedEntities.contains(searchQuery, ignoreCase = true) ||
                rec.category.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Bóveda de Datos", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = "Clasificación de Gmail, mensajes, eventos y logs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (filteredRecords.isNotEmpty() && selectedSegment == 0) {
                        FilledTonalButton(
                            onClick = {
                                val report = filteredRecords.joinToString("\n\n") { rec ->
                                    "[${rec.category} | ${channelLabel(rec.sourceApp)}] ${rec.title}\nDatos: ${rec.extractedEntities}\nResumen: ${rec.content.take(200)}"
                                }
                                AppConnectors.shareText(context, "Reporte Bóveda Nexo AI", report)
                            },
                            modifier = Modifier.testTag("export_vault_button")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Exportar")
                        }
                    }
                }
            }

            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedSegment == 0,
                        onClick = { selectedSegment = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Registros (${records.size})")
                    }
                    SegmentedButton(
                        selected = selectedSegment == 1,
                        onClick = { selectedSegment = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("Historial (${logs.size})")
                    }
                }
            }

            if (selectedSegment == 0) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        label = { Text("Buscar por cliente, factura, fecha o categoría") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("vault_search_input")
                    )
                }

                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val filters = listOf(
                            "all" to "Todos",
                            "whatsapp" to "WhatsApp",
                            "gmail" to "Gmail",
                            "calendar" to "Calendario",
                            "gemini_app" to "Gemini App",
                            "archived" to "Archivados"
                        )
                        filters.forEach { (key, label) ->
                            FilterChip(
                                selected = sourceFilter == key,
                                onClick = { sourceFilter = key },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                if (filteredRecords.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Sin registros en esta vista", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Procesa un mensaje, correo de Gmail o salida de Gemini desde el Centro PLN para verlo clasificado aquí.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filteredRecords, key = { it.id }) { record ->
                        DataRecordCard(
                            record = record,
                            onSendWhatsApp = {
                                AppConnectors.openWhatsAppWithText(context, record.suggestedReply)
                                onMarkExecuted(record)
                            },
                            onSendGmail = {
                                AppConnectors.openGmailCompose(
                                    context = context,
                                    subject = "Re: ${record.title}",
                                    body = record.suggestedReply
                                )
                                onMarkExecuted(record)
                            },
                            onAddCalendar = {
                                AppConnectors.insertCalendarEvent(
                                    context = context,
                                    title = record.eventTitle.ifBlank { record.title },
                                    description = record.content,
                                    isoDateTime = record.eventIsoDateTime
                                )
                                onMarkExecuted(record)
                            },
                            onArchive = { onArchiveRecord(record) },
                            onDelete = { onDeleteRecord(record.id) }
                        )
                    }
                }
            } else {
                if (logs.isEmpty()) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Text(
                                text = "El historial de ejecuciones autónomas aparecerá aquí.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    }
                } else {
                    items(logs, key = { it.id }) { log ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ChannelBadge(log.channel)
                                    val dateText = log.createdAt?.toDate()?.let {
                                        SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(it)
                                    } ?: "Ahora"
                                    Text(
                                        text = dateText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(text = log.agentName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Intención: ${log.detectedIntent}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = log.outputAction,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
