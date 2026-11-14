package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.GeminiAgentEngine
import com.example.data.model.AgentExecutionLog
import com.example.data.model.AutomationAgent
import com.example.data.model.DataRecord
import com.example.data.model.GeminiCustomApp
import com.example.data.model.NlpAnalysisResult
import com.example.data.repository.NexoRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface StreamUiState<out T> {
    data object Loading : StreamUiState<Nothing>
    data class Success<T>(val data: T) : StreamUiState<T>
    data class Error(val message: String) : StreamUiState<Nothing>
}

sealed interface AiTaskState {
    data object Idle : AiTaskState
    data object Loading : AiTaskState
    data class Streaming(val partialText: String) : AiTaskState
    data class Analyzed(val result: NlpAnalysisResult, val savedRecordId: String?) : AiTaskState
    data class CompletedText(val text: String) : AiTaskState
    data class Error(val message: String) : AiTaskState
}

data class AgentTemplateIdea(
    val id: String,
    val name: String,
    val channel: String,
    val description: String,
    val triggerCondition: String,
    val actionTemplate: String,
    val aiToolBadge: String
)

class NexoViewModel(
    private val repository: NexoRepository,
    private val aiEngine: GeminiAgentEngine,
    private val currentUserId: String
) : ViewModel() {

    val agentsState: StateFlow<StreamUiState<List<AutomationAgent>>> =
        repository.observeAgents(currentUserId)
            .map<List<AutomationAgent>, StreamUiState<List<AutomationAgent>>> { list ->
                com.example.service.AgentEventBus.updateActiveAgentsSnapshot(list)
                StreamUiState.Success(list)
            }
            .catch { error -> emit(StreamUiState.Error(unwrapFirestoreMessage(error, "Error cargando agentes"))) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), StreamUiState.Loading)

    val recordsState: StateFlow<StreamUiState<List<DataRecord>>> =
        repository.observeDataRecords(currentUserId)
            .map<List<DataRecord>, StreamUiState<List<DataRecord>>> { StreamUiState.Success(it) }
            .catch { error -> emit(StreamUiState.Error(unwrapFirestoreMessage(error, "Error cargando datos"))) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), StreamUiState.Loading)

    val geminiAppsState: StateFlow<StreamUiState<List<GeminiCustomApp>>> =
        repository.observeGeminiApps(currentUserId)
            .map<List<GeminiCustomApp>, StreamUiState<List<GeminiCustomApp>>> { StreamUiState.Success(it) }
            .catch { error -> emit(StreamUiState.Error(unwrapFirestoreMessage(error, "Error cargando apps de Gemini"))) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), StreamUiState.Loading)

    val logsState: StateFlow<StreamUiState<List<AgentExecutionLog>>> =
        repository.observeExecutionLogs(currentUserId)
            .map<List<AgentExecutionLog>, StreamUiState<List<AgentExecutionLog>>> { StreamUiState.Success(it) }
            .catch { error -> emit(StreamUiState.Error(unwrapFirestoreMessage(error, "Error cargando historial"))) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), StreamUiState.Loading)

    private val _aiTaskState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val aiTaskState: StateFlow<AiTaskState> = _aiTaskState.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private var activeAiJob: Job? = null

    val agentIdeas: List<AgentTemplateIdea> = listOf(
        AgentTemplateIdea(
            id = "wa_vip",
            name = "Guardia WhatsApp VIP",
            channel = "whatsapp",
            description = "Detecta mensajes urgentes, cotizaciones o clientes y redacta respuesta inmediata.",
            triggerCondition = "urgente, precio, cotización, disponibilidad, cita",
            actionTemplate = "Hola, recibimos tu mensaje. Un asesor ya revisa tu solicitud y te confirma a la brevedad.",
            aiToolBadge = "PLN + Sentimiento"
        ),
        AgentTemplateIdea(
            id = "gmail_triage",
            name = "Clasificador Gmail Facturas",
            channel = "gmail",
            description = "Clasifica comprobantes, facturas y recibos en Datos y los marca para auto-archivo.",
            triggerCondition = "factura, recibo, pago, comprobante, estado de cuenta",
            actionTemplate = "Correo clasificado en Facturas y respaldado en tu Bóveda de Datos.",
            aiToolBadge = "Extracción JSON"
        ),
        AgentTemplateIdea(
            id = "cal_sync",
            name = "Extractor Inteligente de Citas",
            channel = "calendar",
            description = "Convierte fechas naturales ('mañana 4pm') de WhatsApp o Gmail en eventos de Google Calendar.",
            triggerCondition = "reunión, junta, mañana, lunes, zoom, meet, agendar",
            actionTemplate = "Evento extraído con fecha ISO listo para insertar en Google Calendar.",
            aiToolBadge = "Parser Temporal"
        ),
        AgentTemplateIdea(
            id = "gemini_bridge",
            name = "Puente Gemini Chat -> Equipo",
            channel = "gemini_app",
            description = "Toma el resultado de tu app en el chat de Gemini, lo resume y lo envía por WhatsApp o Gmail.",
            triggerCondition = "resumen, reporte, minuta, análisis, chat gemini",
            actionTemplate = "Resumen ejecutivo formateado para compartir con tu equipo.",
            aiToolBadge = "Streaming Gemini 3.8"
        ),
        AgentTemplateIdea(
            id = "omnichannel_brief",
            name = "Resumen Matutino Omnicanal",
            channel = "omnichannel",
            description = "Consolida pendientes de WhatsApp, correos prioritarios y eventos en un briefing diario.",
            triggerCondition = "resumen diario, pendientes, briefing, hoy",
            actionTemplate = "Prioridades del día ordenadas por urgencia e impacto.",
            aiToolBadge = "Síntesis Multimodelo"
        )
    )

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun resetAiState() {
        activeAiJob?.cancel()
        _aiTaskState.value = AiTaskState.Idle
    }

    fun activateTemplateIdea(idea: AgentTemplateIdea) {
        viewModelScope.launch {
            val result = repository.createAgent(
                name = idea.name,
                channel = idea.channel,
                description = idea.description,
                triggerCondition = idea.triggerCondition,
                actionTemplate = idea.actionTemplate,
                autoExecute = true,
                enabled = true
            )
            result.onSuccess {
                _bannerMessage.value = "Agente '${idea.name}' activado en tu flujo."
                repository.addExecutionLog(
                    agentName = idea.name,
                    channel = idea.channel,
                    detectedIntent = "activacion_plantilla",
                    inputPreview = idea.triggerCondition,
                    outputAction = "Agente autónomo configurado y activo",
                    status = "success"
                )
            }.onFailure { e ->
                _bannerMessage.value = e.localizedMessage ?: "No se pudo activar el agente."
            }
        }
    }

    fun createCustomAgent(
        name: String,
        channel: String,
        description: String,
        triggerCondition: String,
        actionTemplate: String,
        autoExecute: Boolean
    ) {
        if (name.isBlank() || triggerCondition.isBlank() || actionTemplate.isBlank()) {
            _bannerMessage.value = "Completa nombre, disparador y plantilla de acción."
            return
        }
        viewModelScope.launch {
            repository.createAgent(
                name = name.trim(),
                channel = channel,
                description = description.trim().ifBlank { "Agente autónomo personalizado" },
                triggerCondition = triggerCondition.trim(),
                actionTemplate = actionTemplate.trim(),
                autoExecute = autoExecute
            ).onSuccess {
                _bannerMessage.value = "Agente '$name' guardado en la nube."
            }.onFailure { e ->
                _bannerMessage.value = e.localizedMessage ?: "Error guardando agente."
            }
        }
    }

    fun toggleAgent(agent: AutomationAgent, enabled: Boolean, autoExecute: Boolean) {
        viewModelScope.launch {
            repository.toggleAgent(agent, enabled, autoExecute).onFailure { e ->
                _bannerMessage.value = e.localizedMessage ?: "Error actualizando agente."
            }
        }
    }

    fun deleteAgent(agentId: String) {
        viewModelScope.launch {
            repository.deleteAgent(agentId).onFailure { e ->
                _bannerMessage.value = e.localizedMessage ?: "Error eliminando agente."
            }
        }
    }

    fun analyzeWithNlpAndSave(
        sourceChannel: String,
        rawText: String,
        imageBitmap: Bitmap? = null,
        saveToVault: Boolean = true
    ) {
        if (rawText.isBlank() && imageBitmap == null) {
            _aiTaskState.value = AiTaskState.Error("Ingresa un mensaje, correo o captura para analizar.")
            return
        }
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _aiTaskState.value = AiTaskState.Loading
            try {
                val activeAgents = (agentsState.value as? StreamUiState.Success)?.data.orEmpty()
                val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).format(Date())
                val analysis = aiEngine.analyzeInputWithNlp(
                    sourceChannel = sourceChannel,
                    inputText = rawText.ifBlank { "Analiza la imagen adjunta y extrae intenciones, tareas o fechas." },
                    activeAgents = activeAgents,
                    imageBitmap = imageBitmap,
                    currentDateIso = nowIso
                )

                var savedId: String? = null
                if (saveToVault) {
                    val status = if (analysis.shouldAutoArchive) "archived" else "pending"
                    val category = if (analysis.shouldAutoArchive && sourceChannel == "gmail") {
                        "Archivado"
                    } else {
                        analysis.category
                    }
                    savedId = repository.createDataRecord(
                        sourceApp = sourceChannel,
                        category = category,
                        intent = analysis.intent,
                        title = analysis.eventTitle.ifBlank { analysis.summary.take(80).ifBlank { "Análisis $sourceChannel" } },
                        content = rawText.ifBlank { analysis.summary },
                        extractedEntities = analysis.extractedEntities,
                        suggestedReply = analysis.suggestedReply,
                        status = status,
                        eventTitle = analysis.eventTitle,
                        eventIsoDateTime = analysis.eventIsoDateTime
                    ).getOrNull()

                    val matchedAgent = activeAgents.firstOrNull {
                        it.enabled && (it.name.equals(analysis.matchedRuleName, ignoreCase = true) || it.channel == sourceChannel)
                    }
                    if (matchedAgent != null) {
                        repository.incrementAgentRuns(matchedAgent)
                    }

                    repository.addExecutionLog(
                        agentName = analysis.matchedRuleName,
                        channel = sourceChannel,
                        detectedIntent = analysis.intent,
                        inputPreview = rawText.take(120).ifBlank { "Captura multimodal" },
                        outputAction = if (analysis.hasCalendarEvent) {
                            "Evento detectado: ${analysis.eventTitle} (${analysis.eventIsoDateTime})"
                        } else if (analysis.shouldAutoArchive) {
                            "Clasificado en $category y auto-archivado"
                        } else {
                            "Respuesta sugerida lista (${analysis.category})"
                        },
                        status = if (analysis.shouldAutoArchive) "archived" else "success"
                    )
                }

                _aiTaskState.value = AiTaskState.Analyzed(analysis, savedId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Fallo en análisis PLN", e)
                _aiTaskState.value = AiTaskState.Error(
                    e.localizedMessage ?: "No se pudo completar el análisis con Gemini."
                )
            }
        }
    }

    fun analyzeAudioWithNlp(
        sourceChannel: String,
        audioBytes: ByteArray,
        mimeType: String,
        extraInstruction: String = "",
        saveToVault: Boolean = true
    ) {
        if (audioBytes.isEmpty()) {
            _aiTaskState.value = AiTaskState.Error("El audio está vacío o excede el tamaño permitido.")
            return
        }
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _aiTaskState.value = AiTaskState.Loading
            try {
                val activeAgents = (agentsState.value as? StreamUiState.Success)?.data.orEmpty()
                val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).format(Date())
                val analysis = aiEngine.analyzeAudioWithNlp(
                    sourceChannel = sourceChannel,
                    audioBytes = audioBytes,
                    mimeType = mimeType,
                    extraInstruction = extraInstruction,
                    activeAgents = activeAgents,
                    currentDateIso = nowIso
                )

                var savedId: String? = null
                if (saveToVault) {
                    val status = if (analysis.shouldAutoArchive) "archived" else "pending"
                    savedId = repository.createDataRecord(
                        sourceApp = sourceChannel,
                        category = analysis.category,
                        intent = analysis.intent,
                        title = analysis.eventTitle.ifBlank { analysis.summary.take(80).ifBlank { "Pedido de Voz ($sourceChannel)" } },
                        content = "${analysis.summary}\n\n${analysis.suggestedReply}".trim(),
                        extractedEntities = analysis.extractedEntities,
                        suggestedReply = analysis.suggestedReply,
                        status = status,
                        eventTitle = analysis.eventTitle,
                        eventIsoDateTime = analysis.eventIsoDateTime
                    ).getOrNull()

                    repository.addExecutionLog(
                        agentName = analysis.matchedRuleName,
                        channel = sourceChannel,
                        detectedIntent = analysis.intent,
                        inputPreview = "Audio ($mimeType, ${audioBytes.size / 1024} KB)",
                        outputAction = if (analysis.hasCalendarEvent) {
                            "Audio procesado -> Evento: ${analysis.eventTitle}"
                        } else {
                            "Audio procesado -> Respuesta/invitación lista"
                        },
                        status = "success"
                    )
                }

                _aiTaskState.value = AiTaskState.Analyzed(analysis, savedId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Fallo en análisis de audio PLN", e)
                _aiTaskState.value = AiTaskState.Error(
                    e.localizedMessage ?: "No se pudo procesar el audio con Gemini."
                )
            }
        }
    }

    fun saveCustomGeminiApp(
        name: String,
        purpose: String,
        systemPrompt: String,
        targetChannel: String
    ) {
        if (name.isBlank() || systemPrompt.isBlank()) {
            _bannerMessage.value = "Indica el nombre y las instrucciones de tu app de Gemini."
            return
        }
        viewModelScope.launch {
            repository.createGeminiCustomApp(
                name = name.trim(),
                purpose = purpose.trim().ifBlank { "App importada del chat de Gemini" },
                systemPrompt = systemPrompt.trim(),
                targetChannel = targetChannel
            ).onSuccess {
                _bannerMessage.value = "App '$name' conectada a Nexo AI."
            }.onFailure { e ->
                _bannerMessage.value = e.localizedMessage ?: "Error guardando app de Gemini."
            }
        }
    }

    fun runCustomGeminiApp(
        app: GeminiCustomApp,
        userPayload: String,
        imageBitmap: Bitmap? = null
    ) {
        if (userPayload.isBlank() && imageBitmap == null) {
            _aiTaskState.value = AiTaskState.Error("Escribe o pega los datos/conversación para procesar.")
            return
        }
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _aiTaskState.value = AiTaskState.Loading
            var finalOutput = ""
            try {
                aiEngine.runCustomGeminiAppStream(
                    appName = app.name,
                    appSystemPrompt = app.systemPrompt,
                    userPayload = userPayload.ifBlank { "Procesa esta captura según tus instrucciones." },
                    targetChannel = app.targetChannel,
                    imageBitmap = imageBitmap
                ).collect { partial ->
                    finalOutput = partial
                    _aiTaskState.value = AiTaskState.Streaming(partial)
                }

                if (finalOutput.isNotBlank()) {
                    _aiTaskState.value = AiTaskState.CompletedText(finalOutput)
                    if (app.id.isNotBlank()) {
                        repository.updateGeminiAppResult(app.id, finalOutput)
                    }
                    repository.createDataRecord(
                        sourceApp = "gemini_app",
                        category = "Resumen IA",
                        intent = "ejecucion_${app.name.lowercase().replace(" ", "_").take(30)}",
                        title = "Resultado: ${app.name}",
                        content = finalOutput,
                        extractedEntities = "Destino: ${app.targetChannel}",
                        suggestedReply = finalOutput.take(500),
                        status = "executed"
                    )
                    repository.addExecutionLog(
                        agentName = app.name,
                        channel = "gemini_app",
                        detectedIntent = "resumen_y_accion",
                        inputPreview = userPayload.take(120).ifBlank { "Captura" },
                        outputAction = "Resumen generado y listo para ${app.targetChannel}",
                        status = "success"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error ejecutando app personalizada de Gemini", e)
                _aiTaskState.value = AiTaskState.Error(
                    e.localizedMessage ?: "Error ejecutando tu app de Gemini."
                )
            }
        }
    }

    fun askOrchestrator(question: String) {
        if (question.isBlank()) return
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _aiTaskState.value = AiTaskState.Loading
            var latest = ""
            try {
                val agents = (agentsState.value as? StreamUiState.Success)?.data.orEmpty()
                val records = (recordsState.value as? StreamUiState.Success)?.data.orEmpty()
                val apps = (geminiAppsState.value as? StreamUiState.Success)?.data.orEmpty()
                val contextSummary = buildString {
                    appendLine("Agentes activos: ${agents.count { it.enabled }} / ${agents.size}")
                    appendLine("Registros en bóveda: ${records.size} (Pendientes: ${records.count { it.status == "pending" }})")
                    appendLine("Apps de Gemini conectadas: ${apps.joinToString { it.name }.ifBlank { "ninguna" }}")
                    if (records.isNotEmpty()) {
                        appendLine("Últimos registros: ${records.take(5).joinToString(" | ") { "${it.sourceApp}:${it.title}(${it.category})" }}")
                    }
                }
                aiEngine.askOrchestratorStream(question, contextSummary).collect { partial ->
                    latest = partial
                    _aiTaskState.value = AiTaskState.Streaming(partial)
                }
                if (latest.isNotBlank()) {
                    _aiTaskState.value = AiTaskState.CompletedText(latest)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error en orquestador IA", e)
                _aiTaskState.value = AiTaskState.Error(
                    e.localizedMessage ?: "Error consultando al orquestador Gemini."
                )
            }
        }
    }

    fun archiveRecord(record: DataRecord) {
        if (record.id.isBlank() || record.status == "archived") return
        viewModelScope.launch {
            repository.updateRecordStatus(record.id, "archived", "Archivado")
                .onSuccess {
                    _bannerMessage.value = "'${record.title}' archivado."
                    repository.addExecutionLog(
                        agentName = "Gestor Gmail / Datos",
                        channel = record.sourceApp.takeIf { it in listOf("whatsapp", "gmail", "calendar", "gemini_app") } ?: "omnichannel",
                        detectedIntent = "archivar_registro",
                        inputPreview = record.title,
                        outputAction = "Movido a Archivado",
                        status = "archived"
                    )
                }
                .onFailure { e ->
                    _bannerMessage.value = e.localizedMessage ?: "Error archivando registro."
                }
        }
    }

    fun markRecordExecuted(record: DataRecord) {
        if (record.id.isBlank() || record.status == "archived") return
        viewModelScope.launch {
            repository.updateRecordStatus(record.id, "executed", record.category)
                .onSuccess { _bannerMessage.value = "Acción marcada como ejecutada." }
                .onFailure { e -> _bannerMessage.value = e.localizedMessage ?: "Error actualizando estado." }
        }
    }

    fun deleteRecord(recordId: String) {
        viewModelScope.launch {
            repository.deleteDataRecord(recordId).onFailure { e ->
                _bannerMessage.value = e.localizedMessage ?: "Error eliminando registro."
            }
        }
    }

    fun deleteGeminiApp(appId: String) {
        viewModelScope.launch {
            repository.deleteGeminiApp(appId).onFailure { e ->
                _bannerMessage.value = e.localizedMessage ?: "Error eliminando app de Gemini."
            }
        }
    }

    fun logDirectExternalAction(agentName: String, channel: String, intent: String, preview: String, actionDesc: String) {
        viewModelScope.launch {
            repository.addExecutionLog(
                agentName = agentName,
                channel = channel,
                detectedIntent = intent,
                inputPreview = preview,
                outputAction = actionDesc,
                status = "success"
            )
        }
    }

    private fun unwrapFirestoreMessage(error: Throwable, fallback: String): String {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        return firestoreError?.message ?: error.message ?: fallback
    }

    private companion object {
        const val TAG = "NexoViewModel"
    }
}
