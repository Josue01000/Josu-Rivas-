package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NexoRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndObserveAgent_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = NexoRepository(firestore)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createAgent(
                name = "Agente WhatsApp VIP",
                channel = "whatsapp",
                description = "Responde mensajes urgentes",
                triggerCondition = "urgente, cotización",
                actionTemplate = "Gracias por escribirnos, revisaremos tu solicitud.",
                autoExecute = true
            )
        }
        assertTrue(createResult.isSuccess)
        val agentId = createResult.getOrThrow()

        val agents = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeAgents(uid).first { list -> list.any { it.id == agentId } }
        }
        assertTrue(agents.any { it.id == agentId && it.name == "Agente WhatsApp VIP" })
    }

    @Test
    fun createDataRecord_andArchive_enforcesTerminalLock() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = NexoRepository(firestore)

        val recordId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createDataRecord(
                sourceApp = "gmail",
                category = "Facturas",
                intent = "archivar_factura",
                title = "Factura #104",
                content = "Total $120 USD",
                extractedEntities = "Monto: $120 USD",
                suggestedReply = "Recibido",
                status = "pending",
                eventTitle = "",
                eventIsoDateTime = ""
            ).getOrThrow()
        }

        val archiveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateRecordStatus(recordId, "archived", "Archivado")
        }
        assertTrue(archiveResult.isSuccess)

        val mutateAfterArchive = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateRecordStatus(recordId, "pending", "Prioritario")
        }
        assertTrue(mutateAfterArchive.isFailure)
        val firestoreEx = mutateAfterArchive.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)

        val records = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeDataRecords(uid).first { list -> list.any { it.id == recordId } }
        }
        assertEquals("archived", records.first { it.id == recordId }.status)
    }

    @Test
    fun crossUserAccess_aliceAgents_rejectedForBob() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = NexoRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createAgent(
                name = "Privado Alice",
                channel = "gmail",
                description = "Solo Alice",
                triggerCondition = "vip",
                actionTemplate = "OK",
                autoExecute = false
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        var caughtException: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeAgents(aliceUid).first()
            }
        } catch (e: Exception) {
            caughtException = e
        }
        val firestoreEx = caughtException?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun unauthenticatedObserveAgents_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = NexoRepository(firestore)
        var caughtException: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeAgents("alice_uid").first()
            }
        } catch (e: Exception) {
            caughtException = e
        }
        val firestoreEx = caughtException?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@nexo.test"
        const val BOB_EMAIL = "bob@nexo.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
