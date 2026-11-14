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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AutomationAgent
import com.example.ui.components.AgentRuleCard
import com.example.ui.components.ChannelBadge
import com.example.ui.components.channelLabel
import com.example.ui.viewmodel.AgentTemplateIdea

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentsScreen(
    agents: List<AutomationAgent>,
    ideas: List<AgentTemplateIdea>,
    onActivateIdea: (AgentTemplateIdea) -> Unit,
    onCreateAgent: (name: String, channel: String, description: String, trigger: String, template: String, autoExecute: Boolean) -> Unit,
    onToggleAgent: (AutomationAgent, Boolean, Boolean) -> Unit,
    onDeleteAgent: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Agentes Autónomos",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Reglas inteligentes para WhatsApp, Gmail y Calendario",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.testTag("open_create_agent_dialog")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Crear")
                    }
                }
            }

            if (agents.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Aún no tienes agentes activos",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Activa una de las ideas listas abajo en 1 toque o crea tu propia regla de automatización.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(agents, key = { it.id }) { agent ->
                    AgentRuleCard(
                        agent = agent,
                        onToggle = { enabled, autoExec -> onToggleAgent(agent, enabled, autoExec) },
                        onDelete = { onDeleteAgent(agent.id) }
                    )
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "Ideas de Agentes y Herramientas IA",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            items(ideas, key = { it.id }) { idea ->
                val alreadyActive = agents.any { it.name.equals(idea.name, ignoreCase = true) }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                            ChannelBadge(idea.channel)
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = idea.aiToolBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Text(text = idea.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = idea.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Disparador: ${idea.triggerCondition}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        FilledTonalButton(
                            onClick = { onActivateIdea(idea) },
                            enabled = !alreadyActive,
                            modifier = Modifier.testTag("activate_idea_${idea.id}")
                        ) {
                            Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (alreadyActive) "Ya activado" else "Activar agente")
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Text(
                                text = "Stack de Herramientas IA Activas",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            text = "• PLN + Reconocimiento de Intenciones (Gemini 3.8 Flash con esquema JSON estricto)\n" +
                                "• Análisis Multimodal de Capturas (Photo Picker sin permisos intrusivos)\n" +
                                "• Parser Temporal Natural -> Google Calendar (ISO-8601 automático)\n" +
                                "• Streaming de Baja Latencia para tu App de Gemini\n" +
                                "• Síntesis de Voz (Android TTS) para escuchar resúmenes sin manos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateAgentDialog(
            onDismiss = { showCreateDialog = false },
            onSave = { name, channel, desc, trigger, template, autoExec ->
                onCreateAgent(name, channel, desc, trigger, template, autoExec)
                showCreateDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateAgentDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf("whatsapp") }
    var description by remember { mutableStateOf("") }
    var trigger by remember { mutableStateOf("") }
    var template by remember { mutableStateOf("") }
    var autoExecute by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo Agente Autónomo", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("whatsapp", "gmail", "calendar", "gemini_app", "omnichannel").forEach { item ->
                        FilterChip(
                            selected = channel == item,
                            onClick = { channel = item },
                            label = { Text(channelLabel(item), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del agente") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_agent_name_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Objetivo (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = trigger,
                    onValueChange = { trigger = it },
                    label = { Text("Palabras clave o intención disparadora") },
                    placeholder = { Text("Ej: factura, urgente, reunión") },
                    modifier = Modifier.fillMaxWidth().testTag("new_agent_trigger_input")
                )
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it },
                    label = { Text("Respuesta o acción automática") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("new_agent_template_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-ejecutar al detectar", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = autoExecute, onCheckedChange = { autoExecute = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, channel, description, trigger, template, autoExecute) },
                modifier = Modifier.testTag("confirm_create_agent_button")
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
