package com.example.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ai.MockAIProvider
import com.example.data.AppDatabase
import com.example.data.LocalProjectRepository
import com.example.data.MessageRepository
import androidx.compose.ui.platform.LocalContext

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    
    val database = AppDatabase.getDatabase(context)
    val messageRepository = MessageRepository(database.messageDao())
    val projectRepository = LocalProjectRepository(database.projectDao(), database.messageDao())
    val aiProvider = MockAIProvider()

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
                    aiProvider = aiProvider
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
