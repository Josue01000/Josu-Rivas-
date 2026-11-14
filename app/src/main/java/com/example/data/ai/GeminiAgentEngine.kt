package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.AutomationAgent
import com.example.data.model.NlpAnalysisResult
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class GeminiAgentEngine {

    private val nlpSchema = Schema.obj(
        properties = mapOf(
            "intent" to Schema.string(
                description = "Detected NLP user intent slug in Spanish, e.g. agendar_cita, responder_cliente, archivar_factura, resumen_gemini, soporte_urgente, consulta_general"
            ),
            "category" to Schema.enumeration(
                values = listOf(
                    "Prioritario",
                    "Facturas",
                    "Reuniones",
                    "Clientes",
                    "Resumen IA",
                    "Archivado",
                    "General"
                ),
                description = "Primary classification category"
            ),
            "confidencePercent" to Schema.integer(description = "Confidence score 0 to 100"),
            "urgency" to Schema.enumeration(
                values = listOf("Alta", "Media", "Baja"),
                description = "Urgency level"
            ),
            "summary" to Schema.string(description = "Concise 1-2 sentence executive summary in Spanish"),
            "extractedEntities" to Schema.string(
                description = "Comma-separated extracted entities: contacts, dates, monetary amounts, action items"
            ),
            "suggestedReply" to Schema.string(
                description = "Polite, ready-to-send reply in Spanish tailored to the message or email"
            ),
            "shouldAutoArchive" to Schema.boolean(
                description = "True if this item is an automated receipt, newsletter, or resolved notification safe to archive"
            ),
            "hasCalendarEvent" to Schema.boolean(
                description = "True if the text mentions a meeting, appointment, deadline, or scheduled event"
            ),
            "eventTitle" to Schema.string(
                description = "Short title for Calendar event if hasCalendarEvent is true, else empty string"
            ),
            "eventIsoDateTime" to Schema.string(
                description = "ISO-8601 local date-time (yyyy-MM-dd'T'HH:mm) if a date/time is found or inferred relative to current date, else empty string"
            ),
            "eventLocation" to Schema.string(
                description = "Meeting link or physical location if present, else empty string"
            ),
            "recommendedChannel" to Schema.enumeration(
                values = listOf("whatsapp", "gmail", "calendar", "gemini_app", "omnichannel"),
                description = "Best downstream channel to execute the next action"
            ),
            "matchedRuleName" to Schema.string(
                description = "Name of the matching automation rule provided in context, or 'Agente Orquestador Nexo'"
            )
        )
    )

    private val structuredNlpModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = nlpSchema
                temperature = 0.2f
            }
        )
    }

    private val generalStreamModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "Eres Nexo AI, un orquestador experto de agentes autónomos en Android. " +
                        "Respondes siempre en español claro, directo y accionable. Ayudas al usuario " +
                        "a conectar WhatsApp, Gmail, Google Calendar, sus mini-apps creadas en el chat de Gemini " +
                        "y su bóveda de datos estructurados."
                )
            }
        )
    }

    suspend fun analyzeInputWithNlp(
        sourceChannel: String,
        inputText: String,
        activeAgents: List<AutomationAgent>,
        imageBitmap: Bitmap? = null,
        currentDateIso: String
    ): NlpAnalysisResult {
        val rulesContext = if (activeAgents.isEmpty()) {
            "No hay reglas personalizadas activas aún."
        } else {
            activeAgents.filter { it.enabled }.joinToString("\n") { agent ->
                "- Regla '${agent.name}' (canal=${agent.channel}, disparador='${agent.triggerCondition}', plantilla='${agent.actionTemplate}')"
            }
        }

        val promptText = """
            Fecha actual de referencia: $currentDateIso
            Canal de origen: $sourceChannel
            
            Reglas autónomas activas del usuario:
            $rulesContext
            
            Contenido a analizar mediante PLN y reconocimiento de intenciones:
            $inputText
            
            Instrucciones:
            1. Detecta la intención principal (intent) y clasifica en una categoría válida.
            2. Extrae entidades clave (fechas, horas, montos, remitentes, tareas).
            3. Si existe cualquier mención de reunión, cita, entrega o llamada, activa hasCalendarEvent=true y genera eventTitle + eventIsoDateTime en formato yyyy-MM-dd'T'HH:mm usando la fecha actual de referencia si dicen "mañana", "lunes", etc.
            4. Si alguna regla activa coincide con el mensaje o intención, usa su nombre en matchedRuleName y adapta su plantilla en suggestedReply.
            5. Determina si un correo/notificación informativa o factura pagada debe auto-archivarse (shouldAutoArchive=true).
        """.trimIndent()

        try {
            val response = withTimeout(60_000L) {
                if (imageBitmap != null) {
                    structuredNlpModel.generateContent(
                        content {
                            image(imageBitmap)
                            text(promptText)
                        }
                    )
                } else {
                    structuredNlpModel.generateContent(promptText)
                }
            }

            val rawJson = response.text
                ?: throw IllegalStateException("El modelo Gemini devolvió una respuesta vacía.")

            val json = JSONObject(rawJson)
            return NlpAnalysisResult(
                intent = json.optString("intent", "consulta_general"),
                category = json.optString("category", "General"),
                confidencePercent = json.optInt("confidencePercent", 85),
                urgency = json.optString("urgency", "Media"),
                summary = json.optString("summary", ""),
                extractedEntities = json.optString("extractedEntities", ""),
                suggestedReply = json.optString("suggestedReply", ""),
                shouldAutoArchive = json.optBoolean("shouldAutoArchive", false),
                hasCalendarEvent = json.optBoolean("hasCalendarEvent", false),
                eventTitle = json.optString("eventTitle", ""),
                eventIsoDateTime = json.optString("eventIsoDateTime", ""),
                eventLocation = json.optString("eventLocation", ""),
                recommendedChannel = json.optString("recommendedChannel", sourceChannel),
                matchedRuleName = json.optString("matchedRuleName", "Agente Orquestador Nexo")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error en análisis PLN con Gemini", e)
            throw e
        }
    }

    suspend fun analyzeAudioWithNlp(
        sourceChannel: String,
        audioBytes: ByteArray,
        mimeType: String,
        extraInstruction: String,
        activeAgents: List<AutomationAgent>,
        currentDateIso: String
    ): NlpAnalysisResult {
        val rulesContext = if (activeAgents.isEmpty()) {
            "No hay reglas personalizadas activas aún."
        } else {
            activeAgents.filter { it.enabled }.joinToString("\n") { agent ->
                "- Regla '${agent.name}' (canal=${agent.channel}, disparador='${agent.triggerCondition}', plantilla='${agent.actionTemplate}')"
            }
        }

        val promptText = """
            Fecha actual de referencia: $currentDateIso
            Canal de origen: $sourceChannel
            
            Reglas autónomas activas del usuario:
            $rulesContext
            
            Instrucción adicional del usuario (si existe):
            ${extraInstruction.ifBlank { "Ninguna" }}
            
            Escucha cuidadosamente el audio adjunto en español (nota de voz o pedido por micrófono) y realiza:
            1. Comprende exactamente qué solicita el usuario (ej. crear una invitación sobre un tema, agendar una cita, responder por WhatsApp, clasificar un correo o generar un resumen).
            2. En 'summary', incluye una transcripción breve + resumen del pedido.
            3. En 'suggestedReply', redacta el texto final completo y listo para enviar (por ejemplo, la invitación completa bien redactada, lista para enviar por WhatsApp o Gmail).
            4. Si menciona fecha/hora, activa hasCalendarEvent=true y calcula eventIsoDateTime en formato yyyy-MM-dd'T'HH:mm.
            5. Extrae entidades clave y asigna la categoría e intención correspondientes.
        """.trimIndent()

        try {
            val response = withTimeout(60_000L) {
                structuredNlpModel.generateContent(
                    content {
                        inlineData(audioBytes, mimeType)
                        text(promptText)
                    }
                )
            }

            val rawJson = response.text
                ?: throw IllegalStateException("El modelo Gemini devolvió una respuesta vacía para el audio.")

            val json = JSONObject(rawJson)
            return NlpAnalysisResult(
                intent = json.optString("intent", "comando_voz"),
                category = json.optString("category", "Resumen IA"),
                confidencePercent = json.optInt("confidencePercent", 85),
                urgency = json.optString("urgency", "Media"),
                summary = json.optString("summary", ""),
                extractedEntities = json.optString("extractedEntities", ""),
                suggestedReply = json.optString("suggestedReply", ""),
                shouldAutoArchive = json.optBoolean("shouldAutoArchive", false),
                hasCalendarEvent = json.optBoolean("hasCalendarEvent", false),
                eventTitle = json.optString("eventTitle", ""),
                eventIsoDateTime = json.optString("eventIsoDateTime", ""),
                eventLocation = json.optString("eventLocation", ""),
                recommendedChannel = json.optString("recommendedChannel", sourceChannel),
                matchedRuleName = json.optString("matchedRuleName", "Agente de Voz Nexo")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error en análisis de audio PLN con Gemini", e)
            throw e
        }
    }

    fun runCustomGeminiAppStream(
        appName: String,
        appSystemPrompt: String,
        userPayload: String,
        targetChannel: String,
        imageBitmap: Bitmap? = null
    ): Flow<String> = flow {
        val fullPrompt = """
            Estás ejecutando la mini-app personalizada del usuario llamada "$appName".
            Instrucciones originales de su app en el chat de Gemini:
            $appSystemPrompt
            
            Canal destino preferido para la acción posterior: $targetChannel
            
            Entrada / Resultado del chat a procesar:
            $userPayload
            
            Genera:
            1) Un resumen ejecutivo claro o la salida exacta solicitada por las instrucciones de la app.
            2) Una sección breve "Acciones sugeridas para $targetChannel" lista para copiar o enviar.
        """.trimIndent()

        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            val stream = if (imageBitmap != null) {
                generalStreamModel.generateContentStream(
                    content {
                        image(imageBitmap)
                        text(fullPrompt)
                    }
                )
            } else {
                generalStreamModel.generateContentStream(fullPrompt)
            }
            stream.collect { chunk ->
                val chunkText = chunk.text.orEmpty()
                if (chunkText.isNotEmpty()) {
                    accumulated.append(chunkText)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("Gemini no devolvió texto para esta ejecución.")
        }
    }

    fun askOrchestratorStream(
        userQuestion: String,
        contextSummary: String
    ): Flow<String> = flow {
        val prompt = """
            Contexto actual del espacio de trabajo del usuario en Nexo AI:
            $contextSummary
            
            Solicitud del usuario:
            $userQuestion
        """.trimIndent()

        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            generalStreamModel.generateContentStream(prompt).collect { chunk ->
                val chunkText = chunk.text.orEmpty()
                if (chunkText.isNotEmpty()) {
                    accumulated.append(chunkText)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("El asistente no generó contenido.")
        }
    }

    private companion object {
        const val TAG = "GeminiAgentEngine"
    }
}
