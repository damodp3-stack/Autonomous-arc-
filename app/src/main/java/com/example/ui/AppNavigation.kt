package com.example.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ai.MockAIProvider
import com.example.ai.GeminiAIProvider
import com.example.data.AppDatabase
import com.example.data.LocalProjectRepository
import com.example.data.MessageRepository
import androidx.compose.ui.platform.LocalContext
import com.example.data.ProjectFileRepository
import com.example.data.ProjectFileSystem

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    
    val database = AppDatabase.getDatabase(context)
    val fileSystem = ProjectFileSystem(context)
    val messageRepository = MessageRepository(database.messageDao())
    val aiProviderConfigRepository = com.example.data.AIProviderConfigRepository(database.aiProviderConfigDao())
    val apiKeyManager = com.example.ai.SecureAPIKeyManager(context)
    val aiFactory = com.example.ai.AIFactory(aiProviderConfigRepository, apiKeyManager)
    val fileRepository = ProjectFileRepository(database.projectFileDao(), fileSystem)
    val projectRepository = LocalProjectRepository(database.projectDao(), database.messageDao(), fileRepository)

    NavHost(navController = navController, startDestination = "project_list") {
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
        
        composable("workspace/{projectId}") { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            

            
            val viewModel: WorkspaceViewModel = viewModel(
                factory = WorkspaceViewModelFactory(
                    projectId = projectId,
                    messageRepository = messageRepository,
                    projectRepository = projectRepository,
                    fileRepository = fileRepository,
                    aiFactory = aiFactory
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
}
