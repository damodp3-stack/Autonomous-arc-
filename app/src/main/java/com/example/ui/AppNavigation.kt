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

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    
    val database = AppDatabase.getDatabase(context)
    val messageRepository = MessageRepository(database.messageDao())
    val fileRepository = ProjectFileRepository(database.projectFileDao())
    val projectRepository = LocalProjectRepository(database.projectDao(), database.messageDao(), database.projectFileDao())

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
            
            // We retrieve the project name synchronously if possible, or just pass the ID to GeminiAIProvider. 
            // In a real app we'd pass the actual project context, but for now we'll pass projectId.
            val providers = mapOf(
                "Gemini" to GeminiAIProvider(projectName = projectId),
                "Mock" to MockAIProvider()
            )
            
            val viewModel: WorkspaceViewModel = viewModel(
                factory = WorkspaceViewModelFactory(
                    projectId = projectId,
                    messageRepository = messageRepository,
                    projectRepository = projectRepository,
                    fileRepository = fileRepository,
                    providers = providers
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
