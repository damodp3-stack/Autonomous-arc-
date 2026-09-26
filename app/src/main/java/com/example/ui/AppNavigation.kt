package com.example.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ai.AIFactory
import com.example.ai.SecureAPIKeyManager
import com.example.data.*
import com.example.github.SimpleTokenManager
import com.example.github.TokenManager
import com.example.sync.RealSyncRepository

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Projects : Screen("projects", "Projects", Icons.Default.Folder)
    object Ideas : Screen("ideas", "Ideas", Icons.Default.Lightbulb)
    object Media : Screen("media", "Media", Icons.Default.PhotoLibrary)
    object Analytics : Screen("analytics", "Analytics", Icons.Default.Analytics)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current

    val database = remember { AppDatabase.getDatabase(context) }
    val fileSystem = remember { ProjectFileSystem(context) }
    val messageRepository = remember { MessageRepository(database.messageDao()) }
    val aiProviderConfigRepository = remember { AIProviderConfigRepository(database.aiProviderConfigDao()) }
    val apiKeyManager = remember { SecureAPIKeyManager(context) }
    val aiFactory = remember { AIFactory(aiProviderConfigRepository, apiKeyManager) }
    val fileRepository = remember { ProjectFileRepository(database.projectFileDao(), fileSystem) }
    val projectRepository = remember { LocalProjectRepository(database.projectDao(), database.messageDao(), fileRepository) }
    val usageRepository = remember { LocalUsageRepository(database.usageDao()) }
    val mediaRepository = remember { LocalMediaRepository(database.mediaDao(), context) }
    val mediaGenerationProvider = remember { com.example.ai.GeminiMediaGenerationProvider(mediaRepository, apiKeyManager) }
    val ideaRepository = remember { LocalIdeaRepository(database.ideaDao()) }
    val syncRepository = remember {
        RealSyncRepository(
            syncMetadataDao = database.syncMetadataDao(),
            projectDao = database.projectDao(),
            ideaDao = database.ideaDao(),
            mediaDao = database.mediaDao()
        )
    }

    val tokenManager = remember { SimpleTokenManager(context) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val topLevelScreens = remember {
        listOf(
            Screen.Projects,
            Screen.Ideas,
            Screen.Media,
            Screen.Analytics,
            Screen.Settings
        )
    }

    val isTopLevelRoute = topLevelScreens.any { it.route == currentRoute } || currentRoute == "project_list"

    var showAISettingsDialog by remember { mutableStateOf(false) }
    var showGitHubSettingsDialog by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            if (isTopLevelRoute) {
                NavigationBar(
                    windowInsets = WindowInsets.navigationBars
                ) {
                    val activeRoute = if (currentRoute == "project_list") "projects" else currentRoute
                    topLevelScreens.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = activeRoute == screen.route,
                            onClick = {
                                if (activeRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "projects",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Legacy route alias for full backward compatibility
            composable("project_list") {
                val viewModel: ProjectListViewModel = viewModel(
                    factory = ProjectListViewModelFactory(projectRepository)
                )
                ProjectListScreen(
                    viewModel = viewModel,
                    onProjectSelected = { projectId ->
                        navController.navigate("workspace/$projectId")
                    }
                )
            }

            // 1. Projects tab
            composable("projects") {
                val viewModel: ProjectListViewModel = viewModel(
                    factory = ProjectListViewModelFactory(projectRepository)
                )
                ProjectListScreen(
                    viewModel = viewModel,
                    onProjectSelected = { projectId ->
                        navController.navigate("workspace/$projectId")
                    }
                )
            }

            // 2. Ideas Vault tab
            composable("ideas") {
                val viewModel: IdeasVaultViewModel = viewModel(
                    factory = IdeasVaultViewModelFactory(ideaRepository, projectRepository)
                )
                IdeasVaultScreen(
                    viewModel = viewModel,
                    onNavigateToProject = { projectId ->
                        navController.navigate("workspace/$projectId")
                    }
                )
            }

            // 3. Media Vault tab
            composable("media") {
                val viewModel: MediaVaultViewModel = viewModel(
                    factory = MediaVaultViewModelFactory(mediaRepository, mediaGenerationProvider)
                )
                MediaVaultScreen(
                    viewModel = viewModel,
                    onNavigateToProject = { projectId ->
                        navController.navigate("workspace/$projectId")
                    }
                )
            }

            // 4. Usage Analytics tab
            composable("analytics") {
                val viewModel: UsageAnalyticsViewModel = viewModel(
                    factory = UsageAnalyticsViewModelFactory(usageRepository)
                )
                UsageAnalyticsScreen(
                    viewModel = viewModel
                )
            }

            // 5. Settings tab
            composable("settings") {
                SettingsScreen(
                    syncRepository = syncRepository,
                    onOpenAISettings = { showAISettingsDialog = true },
                    onOpenGitHubSettings = { showGitHubSettingsDialog = true }
                )
            }

            // Workspace sub-screen
            composable("workspace/{projectId}") { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable

                val viewModel: WorkspaceViewModel = viewModel(
                    factory = WorkspaceViewModelFactory(
                        projectId = projectId,
                        messageRepository = messageRepository,
                        projectRepository = projectRepository,
                        fileRepository = fileRepository,
                        aiFactory = aiFactory,
                        apiKeyManager = apiKeyManager,
                        configRepository = aiProviderConfigRepository,
                        usageRepository = usageRepository
                    )
                )

                WorkspaceScreen(
                    viewModel = viewModel,
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }

        // Global Dialogs from Settings
        if (showAISettingsDialog) {
            StandaloneAIProviderSettingsDialog(
                apiKeyManager = apiKeyManager,
                aiFactory = aiFactory,
                configRepository = aiProviderConfigRepository,
                onDismiss = { showAISettingsDialog = false }
            )
        }

        if (showGitHubSettingsDialog) {
            GitHubSettingsDialog(
                tokenManager = tokenManager,
                onDismiss = { showGitHubSettingsDialog = false }
            )
        }
    }
}
