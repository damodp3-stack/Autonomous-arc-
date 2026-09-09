package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubIntegrationDialog(
    viewModel: GitHubViewModel,
    onDismiss: () -> Unit
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val projectState by viewModel.projectState.collectAsStateWithLifecycle()
    val discoveryState by viewModel.discoveryState.collectAsStateWithLifecycle()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("GitHub Integration") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    when (val currentAuth = authState) {
                        is GitHubAuthState.Checking -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        is GitHubAuthState.Unauthenticated -> {
                            AuthenticationView(
                                onAuthenticate = { token -> viewModel.authenticate(token) }
                            )
                        }
                        is GitHubAuthState.Error -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center)) {
                                Text("Authentication Error", color = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(currentAuth.message)
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { viewModel.authenticate("") }) { // Try again with existing or reset
                                    Text("Retry")
                                }
                            }
                        }
                        is GitHubAuthState.Authenticated -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Logged in as ${currentAuth.user.login}", fontWeight = FontWeight.Bold)
                                    TextButton(onClick = { viewModel.logout() }) {
                                        Text("Logout")
                                    }
                                }
                                Divider(modifier = Modifier.padding(vertical = 16.dp))

                                when (val projState = projectState) {
                                    is GitHubProjectState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                                    is GitHubProjectState.Disconnected -> {
                                        RepositoryDiscoveryView(
                                            discoveryState = discoveryState,
                                            onFetch = { viewModel.fetchRepositories() },
                                            onSelectRepository = { repo -> viewModel.selectRepository(repo) },
                                            onSelectBranch = { branch -> viewModel.selectBranch(branch) },
                                            onConnect = { viewModel.connectRepository() }
                                        )
                                    }
                                    is GitHubProjectState.Connected -> {
                                        ConnectedProjectView(
                                            config = projState.config,
                                            onDisconnect = { viewModel.disconnectRepository() },
                                            onSync = { /* Sync Foundation */ },
                                            onChangeRepository = { viewModel.disconnectRepository() }
                                        )
                                    }
                                    is GitHubProjectState.Error -> {
                                        Text("Project Error: ${projState.message}", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuthenticationView(onAuthenticate: (String) -> Unit) {
    var token by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Connect to GitHub", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Please provide a Personal Access Token (classic) with repo scope.", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Personal Access Token") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onAuthenticate(token) },
            enabled = token.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Authenticate")
        }
    }
}


@Composable
fun RepositoryDiscoveryView(
    discoveryState: RepositoryDiscoveryState,
    onFetch: () -> Unit,
    onSelectRepository: (com.example.github.GitHubRepository) -> Unit,
    onSelectBranch: (com.example.github.GitHubBranch) -> Unit,
    onConnect: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Connect a Repository", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))

        when (discoveryState) {
            is RepositoryDiscoveryState.Idle -> {
                Button(onClick = onFetch, modifier = Modifier.fillMaxWidth()) {
                    Text("Load Repositories")
                }
            }
            is RepositoryDiscoveryState.LoadingRepositories, is RepositoryDiscoveryState.LoadingBranches, is RepositoryDiscoveryState.Connecting -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is RepositoryDiscoveryState.Error -> {
                Text(discoveryState.message, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onFetch, modifier = Modifier.fillMaxWidth()) {
                    Text("Retry")
                }
            }
            is RepositoryDiscoveryState.RepositoriesLoaded -> {
                LazyColumn {
                    items(discoveryState.repositories) { repo ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onSelectRepository(repo) }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(repo.fullName, fontWeight = FontWeight.Bold)
                                if (repo.description != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(repo.description, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            is RepositoryDiscoveryState.BranchesLoaded -> {
                Text("Repository: ${discoveryState.repository.fullName}", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Select Branch", style = MaterialTheme.typography.titleSmall)
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(discoveryState.branches) { branch ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectBranch(branch) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = branch == discoveryState.selectedBranch,
                                onClick = { onSelectBranch(branch) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(branch.name)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onConnect,
                    enabled = discoveryState.selectedBranch != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect")
                }
            }
        }
    }
}
@Composable
fun ConnectedProjectView(
    config: com.example.data.GitHubConfigEntity,
    onDisconnect: () -> Unit,
    onSync: () -> Unit,
    onChangeRepository: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("● Connected", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Repository:", fontWeight = FontWeight.Bold)
                Text("${config.owner}/${config.repository}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Branch:", fontWeight = FontWeight.Bold)
                Text(config.branch)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(onClick = onSync) {
                Text("Refresh")
            }
            OutlinedButton(onClick = onChangeRepository) {
                Text("Change Repository")
            }
            OutlinedButton(onClick = onDisconnect) {
                Text("Disconnect")
            }
        }
    }
}
