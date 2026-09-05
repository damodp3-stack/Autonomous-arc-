package com.example.ui




import androidx.compose.foundation.clickable



import androidx.compose.foundation.layout.*



import androidx.compose.foundation.lazy.LazyColumn



import androidx.compose.foundation.lazy.items



import androidx.compose.material.icons.Icons



import androidx.compose.material.icons.filled.Add



import androidx.compose.material.icons.filled.Delete



import androidx.compose.material3.*



import androidx.compose.runtime.*



import androidx.compose.ui.Alignment



import androidx.compose.ui.Modifier



import androidx.compose.ui.text.font.FontWeight



import androidx.compose.ui.unit.dp



import androidx.lifecycle.compose.collectAsStateWithLifecycle


import com.example.data.ProjectEntity


import java.text.SimpleDateFormat


import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    viewModel: ProjectListViewModel,
    onProjectSelected: (String) -> Unit
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Projects", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Project")
            }
        },
        containerColor = com.example.ui.theme.HighDensityBackground
    ) { paddingValues ->
        if (projects.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("No projects yet. Create one to get started.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(projects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        onClick = { onProjectSelected(project.id) },
                        onDelete = { viewModel.deleteProject(project.id) }
                    )
                }
            }
        }

        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("New Project") },
                text = {
                    OutlinedTextField(
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        label = { Text("Project Name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newProjectName.isNotBlank()) {
                                viewModel.createProject(newProjectName) { newId ->
                                    onProjectSelected(newId)
                                }
                                showCreateDialog = false
                                newProjectName = ""
                            }
                        }
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun ProjectCard(project: ProjectEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val formatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    val dateString = formatter.format(Date(project.updatedAt))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Last updated: $dateString",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Project",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
