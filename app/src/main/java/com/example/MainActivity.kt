package com.example

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.core.configureAppCheck
import com.example.data.ai.GeminiAgentEngine
import com.example.data.repository.NexoRepository
import com.example.service.AgentEventBus
import com.example.ui.screens.AgentsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.GeminiBridgeScreen
import com.example.ui.screens.HubScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.NexoTheme
import com.example.ui.viewmodel.NexoViewModel
import com.example.ui.viewmodel.StreamUiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

private const val ROUTE_HUB = "hub"
private const val ROUTE_AGENTS = "agents"
private const val ROUTE_GEMINI_BRIDGE = "gemini_bridge"
private const val ROUTE_VAULT = "vault"

private data class NexoDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

class MainActivity : ComponentActivity() {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // App Check MUST run before setContent, ViewModels, or any Firebase call.
        configureAppCheck(this, intent)
        handleIncomingShareIntent(intent)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "ES")
                isTtsReady = true
            }
        }

        enableEdgeToEdge()
        setContent {
            NexoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        onSpeakText = { textToSpeak ->
                            if (isTtsReady && textToSpeak.isNotBlank()) {
                                tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "nexo_tts")
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val mime = intent.type.orEmpty()
            if (mime.startsWith("text/")) {
                val shared = intent.getStringExtra(Intent.EXTRA_TEXT)
                AgentEventBus.publishSharedText(shared)
            } else if (mime.startsWith("audio/")) {
                val audioUri = androidx.core.content.IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_STREAM,
                    android.net.Uri::class.java
                )
                AgentEventBus.publishSharedAudio(audioUri, mime)
            }
        }
    }
}

@Composable
fun AppNavigation(
    onSpeakText: (String) -> Unit
) {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            NexoShellScreen(
                currentUserId = user.uid,
                userDisplayName = user.displayName ?: user.email ?: "Usuario",
                onSpeakText = onSpeakText
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexoShellScreen(
    currentUserId: String,
    userDisplayName: String,
    onSpeakText: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: NexoViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                NexoViewModel(
                    repository = NexoRepository(db),
                    aiEngine = GeminiAgentEngine(),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val agentsState by viewModel.agentsState.collectAsStateWithLifecycle()
    val recordsState by viewModel.recordsState.collectAsStateWithLifecycle()
    val geminiAppsState by viewModel.geminiAppsState.collectAsStateWithLifecycle()
    val logsState by viewModel.logsState.collectAsStateWithLifecycle()
    val aiState by viewModel.aiTaskState.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()

    val capturedNotifications by AgentEventBus.capturedNotifications.collectAsStateWithLifecycle()
    val sharedIncomingText by AgentEventBus.sharedIncomingText.collectAsStateWithLifecycle()
    val sharedIncomingAudio by AgentEventBus.sharedIncomingAudio.collectAsStateWithLifecycle()
    val isBackgroundRunning by AgentEventBus.isBackgroundServiceRunning.collectAsStateWithLifecycle()
    val lastBackgroundStatus by AgentEventBus.lastBackgroundStatus.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(bannerMessage) {
        val msg = bannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_HUB

    val destinations = remember {
        listOf(
            NexoDestination(ROUTE_HUB, "Centro PLN", Icons.Filled.Psychology),
            NexoDestination(ROUTE_AGENTS, "Agentes", Icons.Filled.Hub),
            NexoDestination(ROUTE_GEMINI_BRIDGE, "Gemini App", Icons.Filled.AutoAwesome),
            NexoDestination(ROUTE_VAULT, "Datos", Icons.Filled.FolderShared)
        )
    }

    val agents = (agentsState as? StreamUiState.Success)?.data.orEmpty()
    val records = (recordsState as? StreamUiState.Success)?.data.orEmpty()
    val geminiApps = (geminiAppsState as? StreamUiState.Success)?.data.orEmpty()
    val logs = (logsState as? StreamUiState.Success)?.data.orEmpty()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Nexo AI • $userDisplayName",
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    actions = {
                        IconButton(
                            onClick = {
                                signOutUser(
                                    credentialManager = credentialManager,
                                    scope = coroutineScope
                                )
                            },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Cerrar sesión"
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        destinations.forEach { dest ->
                            NavigationBarItem(
                                selected = currentRoute == dest.route,
                                onClick = {
                                    if (currentRoute != dest.route) {
                                        navController.navigate(dest.route) {
                                            popUpTo(ROUTE_HUB) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        destinations.forEach { dest ->
                            NavigationRailItem(
                                selected = currentRoute == dest.route,
                                onClick = {
                                    if (currentRoute != dest.route) {
                                        navController.navigate(dest.route) {
                                            popUpTo(ROUTE_HUB) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) }
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_HUB,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_HUB) {
                        HubScreen(
                            agents = agents,
                            records = records,
                            aiState = aiState,
                            capturedNotifications = capturedNotifications,
                            sharedIncomingText = sharedIncomingText,
                            sharedIncomingAudio = sharedIncomingAudio,
                            isBackgroundRunning = isBackgroundRunning,
                            lastBackgroundStatus = lastBackgroundStatus,
                            onConsumeSharedText = { AgentEventBus.consumeSharedText() },
                            onConsumeSharedAudio = { AgentEventBus.consumeSharedAudio() },
                            onAnalyzeNlp = { channel, text, bitmap ->
                                viewModel.analyzeWithNlpAndSave(channel, text, bitmap)
                            },
                            onAnalyzeAudio = { channel, bytes, mime, extra ->
                                viewModel.analyzeAudioWithNlp(channel, bytes, mime, extra)
                            },
                            onAskOrchestrator = { question ->
                                viewModel.askOrchestrator(question)
                            },
                            onSpeakText = onSpeakText,
                            onResetAiState = { viewModel.resetAiState() },
                            onLogExternalAction = { agentName, channel, intent, preview, actionDesc ->
                                viewModel.logDirectExternalAction(agentName, channel, intent, preview, actionDesc)
                            }
                        )
                    }

                    composable(ROUTE_AGENTS) {
                        AgentsScreen(
                            agents = agents,
                            ideas = viewModel.agentIdeas,
                            onActivateIdea = { idea -> viewModel.activateTemplateIdea(idea) },
                            onCreateAgent = { name, channel, desc, trigger, template, autoExec ->
                                viewModel.createCustomAgent(name, channel, desc, trigger, template, autoExec)
                            },
                            onToggleAgent = { agent, enabled, autoExec ->
                                viewModel.toggleAgent(agent, enabled, autoExec)
                            },
                            onDeleteAgent = { id -> viewModel.deleteAgent(id) }
                        )
                    }

                    composable(ROUTE_GEMINI_BRIDGE) {
                        GeminiBridgeScreen(
                            geminiApps = geminiApps,
                            aiState = aiState,
                            onSaveGeminiApp = { name, purpose, prompt, target ->
                                viewModel.saveCustomGeminiApp(name, purpose, prompt, target)
                            },
                            onRunGeminiApp = { app, payload, bitmap ->
                                viewModel.runCustomGeminiApp(app, payload, bitmap)
                            },
                            onDeleteGeminiApp = { id -> viewModel.deleteGeminiApp(id) },
                            onSpeakText = onSpeakText,
                            onLogExternalAction = { agentName, channel, intent, preview, actionDesc ->
                                viewModel.logDirectExternalAction(agentName, channel, intent, preview, actionDesc)
                            }
                        )
                    }

                    composable(ROUTE_VAULT) {
                        VaultScreen(
                            records = records,
                            logs = logs,
                            onArchiveRecord = { rec -> viewModel.archiveRecord(rec) },
                            onMarkExecuted = { rec -> viewModel.markRecordExecuted(rec) },
                            onDeleteRecord = { id -> viewModel.deleteRecord(id) }
                        )
                    }
                }
            }
        }
    }
}
