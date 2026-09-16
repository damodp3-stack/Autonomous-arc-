package com.example.github

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.GitHubConfigRepository
import com.example.data.ProjectFileEntity
import com.example.data.ProjectFileRepository
import com.example.data.ProjectFileSystem
import com.example.ui.GitHubProjectState
import com.example.ui.GitHubPushState
import com.example.ui.GitHubViewModel
import com.example.ui.RepositoryDiscoveryState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class GitHubPushTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var fileSystem: ProjectFileSystem
    private lateinit var fileRepo: ProjectFileRepository
    private lateinit var configRepo: GitHubConfigRepository
    private lateinit var mockService: MockGitHubService

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        fileSystem = ProjectFileSystem(context)
        fileRepo = ProjectFileRepository(db.projectFileDao(), fileSystem)
        configRepo = GitHubConfigRepository(db.githubConfigDao())
        mockService = MockGitHubService()
        runBlocking { mockService.authenticate("test-token") }
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun createViewModel(projectId: String): GitHubViewModel {
        val syncService = com.example.github.RealGitHubSyncService(mockService, fileRepo, configRepo)
        return GitHubViewModel(projectId, mockService, mockService, configRepo, syncService)
    }

    private suspend fun setupClonedProject(viewModel: GitHubViewModel, repoName: String) {
        viewModel.fetchRepositories()
        ShadowLooper.idleMainLooper()
        val state1 = viewModel.discoveryState.value as? RepositoryDiscoveryState.RepositoriesLoaded
        if (state1 == null) {
            println("DISCOVERY STATE WAS " + viewModel.discoveryState.value)
            throw Exception("Expected RepositoriesLoaded but was " + viewModel.discoveryState.value)
        }
        val repo = state1.repositories.find { it.name == repoName }!!
        viewModel.selectRepository(repo)
        ShadowLooper.idleMainLooper()
        viewModel.connectRepository(force = true)
        for (i in 0..500) {
            ShadowLooper.idleMainLooper()
            val state = viewModel.discoveryState.value
            if (state is RepositoryDiscoveryState.Idle || state is RepositoryDiscoveryState.Error) break
            Thread.sleep(10)
        }
    }

    @Test
    fun `test no local changes`() = runBlocking {
        val projId = "proj-push-no-changes"
        val vm = createViewModel(projId)
        setupClonedProject(vm, "normal-repo")
        
        vm.detectChanges()
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm.pushState.value; if (s is com.example.ui.GitHubPushState.ChangesReady || s is com.example.ui.GitHubPushState.NoChanges || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        
        assert(vm.pushState.value is GitHubPushState.NoChanges)
    }

    @Test
    fun `test new text file and successful push`() = runBlocking {
        val projId = "proj-new-file"
        val vm = createViewModel(projId)
        setupClonedProject(vm, "normal-repo")
        
        fileSystem.writeFileBytes(projId, "new_file.txt", "Hello World".toByteArray())
        fileRepo.createFile(projId, "new_file.txt", "Hello World")
        
        vm.detectChanges()
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm.pushState.value; if (s is com.example.ui.GitHubPushState.ChangesReady || s is com.example.ui.GitHubPushState.NoChanges || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        
        val ready = vm.pushState.value as GitHubPushState.ChangesReady
        assert(ready.summary.additions == 1)
        
        vm.commitAndPush("Add new file")
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm.pushState.value; if (s is com.example.ui.GitHubPushState.Success || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        
        if (vm.pushState.value !is GitHubPushState.Success) {
            java.io.File("/tmp/MULTI_END_STATE").writeText("END STATE: " + vm.pushState.value)
        }
        assert(vm.pushState.value is GitHubPushState.Success)
    }

    @Test
    fun `test modified text file`() = runBlocking {
        val projId = "proj-mod-file"
        val vm = createViewModel(projId)
        setupClonedProject(vm, "normal-repo")
        
        fileSystem.writeFileBytes(projId, "src/main.kt", "Modified".toByteArray())
        
        vm.detectChanges()
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm.pushState.value; if (s is com.example.ui.GitHubPushState.ChangesReady || s is com.example.ui.GitHubPushState.NoChanges || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        
        val ready = vm.pushState.value as GitHubPushState.ChangesReady
        assert(ready.summary.modifications == 1)
        
        vm.commitAndPush("Modify file")
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm.pushState.value; if (s is com.example.ui.GitHubPushState.Success || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        
        if (vm.pushState.value !is GitHubPushState.Success) {
            java.io.File("/tmp/MULTI_END_STATE").writeText("END STATE: " + vm.pushState.value)
        }
        assert(vm.pushState.value is GitHubPushState.Success)
    }

    @Test
    fun `test deleted file`() = runBlocking {
        val projId = "proj-del-file"
        val vm = createViewModel(projId)
        setupClonedProject(vm, "normal-repo")
        
        fileSystem.deleteFile(projId, "README.md")
        fileRepo.getFileByPath(projId, "README.md")?.let { fileRepo.deleteFile(it.id) }
        
        vm.detectChanges()
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm.pushState.value; if (s is com.example.ui.GitHubPushState.ChangesReady || s is com.example.ui.GitHubPushState.NoChanges || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        
        val ready = vm.pushState.value as GitHubPushState.ChangesReady
        assert(ready.summary.deletions == 1)
        
        vm.commitAndPush("Delete file")
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm.pushState.value; if (s is com.example.ui.GitHubPushState.Success || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        
        if (vm.pushState.value !is GitHubPushState.Success) {
            java.io.File("/tmp/MULTI_END_STATE").writeText("END STATE: " + vm.pushState.value)
        }
        assert(vm.pushState.value is GitHubPushState.Success)
    }

    @Test
    fun `test multiple simultaneous changes including binary`() = runBlocking {
        val projId = "proj-multi"
        val vm = createViewModel(projId)
        setupClonedProject(vm, "normal-repo")
        
        fileSystem.writeFileBytes(projId, "src/main.kt", "Modified".toByteArray())
        fileSystem.writeFileBytes(projId, "new.bin", byteArrayOf(0x00, 0x01))
        fileRepo.createFileWithBytes(projId, "new.bin", byteArrayOf(0x00, 0x01))
        fileSystem.deleteFile(projId, "README.md")
        fileRepo.getFileByPath(projId, "README.md")?.let { fileRepo.deleteFile(it.id) }
        
        vm.detectChanges()
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm.pushState.value; if (s is com.example.ui.GitHubPushState.ChangesReady || s is com.example.ui.GitHubPushState.NoChanges || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        
        java.io.File("/tmp/MULTI_STATE").writeText("MULTI STATE IS " + vm.pushState.value)
        val ready = vm.pushState.value as GitHubPushState.ChangesReady
        assert(ready.summary.modifications == 1)
        assert(ready.summary.additions == 1)
        assert(ready.summary.deletions == 1)
        
        vm.commitAndPush("Multi changes")
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm.pushState.value; if (s is com.example.ui.GitHubPushState.Success || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        
        if (vm.pushState.value !is GitHubPushState.Success) {
            java.io.File("/tmp/MULTI_END_STATE").writeText("END STATE: " + vm.pushState.value)
        }
        assert(vm.pushState.value is GitHubPushState.Success)
    }

    @Test
    fun `test binary modification`() = runBlocking {
        val projId = "proj-bin-mod"
        val vm = createViewModel(projId)
        setupClonedProject(vm, "normal-repo")
        
        fileSystem.writeFileBytes(projId, "assets/icon.png", byteArrayOf(0x01, 0x02, 0x03))
        
        vm.detectChanges()
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm.pushState.value; if (s is com.example.ui.GitHubPushState.ChangesReady || s is com.example.ui.GitHubPushState.NoChanges || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        
        val ready = vm.pushState.value as GitHubPushState.ChangesReady
        assert(ready.summary.modifications == 1)
        
        vm.commitAndPush("Mod binary")
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm.pushState.value; if (s is com.example.ui.GitHubPushState.Success || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        
        if (vm.pushState.value !is GitHubPushState.Success) {
            java.io.File("/tmp/MULTI_END_STATE").writeText("END STATE: " + vm.pushState.value)
        }
        assert(vm.pushState.value is GitHubPushState.Success)
    }

    @Test
    fun `test blank commit message`() = runBlocking {
        val projId = "proj-blank"
        val vm = createViewModel(projId)
        setupClonedProject(vm, "normal-repo")
        
        fileSystem.writeFileBytes(projId, "src/main.kt", "Modified".toByteArray())
        
        vm.detectChanges()
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm.pushState.value; if (s is com.example.ui.GitHubPushState.ChangesReady || s is com.example.ui.GitHubPushState.NoChanges || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        
        vm.commitAndPush("   \n  ")
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm.pushState.value; if (s is com.example.ui.GitHubPushState.Success || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        
        if (vm.pushState.value !is GitHubPushState.Error) { println("STATE WAS " + vm.pushState.value); assert(false) }
    }

    @Test
    fun `test blob creation failure preserves local`() = runBlocking {
        val projId = "proj-blob-fail"
        val vm = createViewModel(projId)
        setupClonedProject(vm, "normal-repo")
        
        fileSystem.writeFileBytes(projId, "fail.txt", "fail-blob".toByteArray())
        fileRepo.createFile(projId, "fail.txt", "fail-blob")
        
        vm.detectChanges()
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm.pushState.value; if (s is com.example.ui.GitHubPushState.ChangesReady || s is com.example.ui.GitHubPushState.NoChanges || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        
        vm.commitAndPush("Fail blob")
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm.pushState.value; if (s is com.example.ui.GitHubPushState.Success || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        
        if (vm.pushState.value !is GitHubPushState.Error) { println("STATE WAS " + vm.pushState.value); assert(false) }
    }

    @Test
    fun `test tree creation failure preserves local`() = runBlocking {
        val projId = "proj-tree-fail"
        val vm = createViewModel(projId)
        setupClonedProject(vm, "normal-repo")
        
        fileSystem.writeFileBytes(projId, "fail-tree", "content".toByteArray())
        fileRepo.createFile(projId, "fail-tree", "content")
        
        vm.detectChanges()
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm.pushState.value; if (s is com.example.ui.GitHubPushState.ChangesReady || s is com.example.ui.GitHubPushState.NoChanges || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        
        vm.commitAndPush("Fail tree")
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm.pushState.value; if (s is com.example.ui.GitHubPushState.Success || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        
        if (vm.pushState.value !is GitHubPushState.Error) { println("STATE WAS " + vm.pushState.value); assert(false) }
    }

    @Test
    fun `test commit creation failure`() = runBlocking {
        val projId = "proj-commit-fail"
        val vm = createViewModel(projId)
        setupClonedProject(vm, "normal-repo")
        
        fileSystem.writeFileBytes(projId, "src/main.kt", "Modified".toByteArray())
        
        vm.detectChanges()
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm.pushState.value; if (s is com.example.ui.GitHubPushState.ChangesReady || s is com.example.ui.GitHubPushState.NoChanges || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        
        vm.commitAndPush("fail-commit")
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm.pushState.value; if (s is com.example.ui.GitHubPushState.Success || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        
        if (vm.pushState.value !is GitHubPushState.Error) { println("STATE WAS " + vm.pushState.value); assert(false) }
    }

    @Test
    fun `test ref update failure`() = runBlocking {
        val projId = "proj-ref-fail"
        val vm = createViewModel(projId)
        setupClonedProject(vm, "normal-repo")
        
        fileSystem.writeFileBytes(projId, "src/main.kt", "Modified".toByteArray())
        
        vm.detectChanges()
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm.pushState.value; if (s is com.example.ui.GitHubPushState.ChangesReady || s is com.example.ui.GitHubPushState.NoChanges || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        
        mockService.failNextRefUpdate = true
        vm.commitAndPush("fail ref")
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm.pushState.value; if (s is com.example.ui.GitHubPushState.Success || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        
        if (vm.pushState.value !is GitHubPushState.Error) { println("STATE WAS " + vm.pushState.value); assert(false) }
    }

    @Test
    fun `test remote branch changed conflict`() = runBlocking {
        val projId = "proj-conflict"
        val vm = createViewModel(projId)
        setupClonedProject(vm, "normal-repo")
        
        fileSystem.writeFileBytes(projId, "src/main.kt", "Modified".toByteArray())
        
        vm.detectChanges()
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm.pushState.value; if (s is com.example.ui.GitHubPushState.ChangesReady || s is com.example.ui.GitHubPushState.NoChanges || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        
        // simulate remote branch moving
        mockService.currentRefSha = "different_sha_from_someone_else"
        
        vm.commitAndPush("conflict")
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm.pushState.value; if (s is com.example.ui.GitHubPushState.Success || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        
        assert(vm.pushState.value is GitHubPushState.Conflict)
    }

    @Test
    fun `test invalid path`() = runBlocking {
        val projId = "proj-invalid"
        val vm = createViewModel(projId)
        setupClonedProject(vm, "normal-repo")
        
        // Force insert into DB to bypass filesystem safety checks
        println("INSERTING INVALID FILE"); db.projectFileDao().insertFile(ProjectFileEntity(projectId = projId, path = "../hacked.txt", name = "hacked.txt", extension = "txt", content = "content", isDirectory = false))
        
        vm.detectChanges()
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm.pushState.value; if (s is com.example.ui.GitHubPushState.ChangesReady || s is com.example.ui.GitHubPushState.NoChanges || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        
        if (vm.pushState.value !is GitHubPushState.Error) { println("STATE WAS " + vm.pushState.value); assert(false) }
    }

    @Test
    fun `test oversized file`() = runBlocking {
        val projId = "proj-oversize"
        val vm = createViewModel(projId)
        setupClonedProject(vm, "normal-repo")
        
        val big = ByteArray(11 * 1024 * 1024) { 0 }
        fileSystem.writeFileBytes(projId, "big.bin", big)
                fileSystem.writeFileBytes(projId, "big.bin", big)
        db.projectFileDao().insertFile(ProjectFileEntity(projectId = projId, path = "big.bin", name = "big.bin", extension = "bin", content = "[BINARY FILE]", isDirectory = false))
        
        vm.detectChanges()
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm.pushState.value; if (s is com.example.ui.GitHubPushState.ChangesReady || s is com.example.ui.GitHubPushState.NoChanges || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        
        if (vm.pushState.value !is GitHubPushState.Error) { println("STATE WAS " + vm.pushState.value); assert(false) }
    }

    @Test
    fun `test authentication failure`() = runBlocking {
        val projId = "proj-auth"
        val vm = createViewModel(projId)
        setupClonedProject(vm, "normal-repo")
        
        fileSystem.writeFileBytes(projId, "src/main.kt", "Modified".toByteArray())
        
        vm.detectChanges()
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm.pushState.value; if (s is com.example.ui.GitHubPushState.ChangesReady || s is com.example.ui.GitHubPushState.NoChanges || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        
        mockService.logout()
        vm.commitAndPush("auth fail")
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm.pushState.value; if (s is com.example.ui.GitHubPushState.Success || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        
        if (vm.pushState.value !is GitHubPushState.Error) { println("STATE WAS " + vm.pushState.value); assert(false) }
    }

    @Test
    fun `test project isolation`() = runBlocking {
        val projId1 = "proj-iso-1"
        val vm1 = createViewModel(projId1)
        setupClonedProject(vm1, "normal-repo")
        
        val projId2 = "proj-iso-2"
        val vm2 = createViewModel(projId2)
        setupClonedProject(vm2, "normal-repo")
        
        fileSystem.writeFileBytes(projId2, "src/main.kt", "Modified".toByteArray())
        
        vm1.detectChanges()
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm1.pushState.value; if (s is com.example.ui.GitHubPushState.ChangesReady || s is com.example.ui.GitHubPushState.NoChanges || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        assert(vm1.pushState.value is GitHubPushState.NoChanges)
        
        vm2.detectChanges()
        for(i in 0..500) { ShadowLooper.idleMainLooper(); val s = vm2.pushState.value; if (s is com.example.ui.GitHubPushState.ChangesReady || s is com.example.ui.GitHubPushState.NoChanges || s is com.example.ui.GitHubPushState.Error || s is com.example.ui.GitHubPushState.Conflict) break; Thread.sleep(10) }
        assert(vm2.pushState.value is GitHubPushState.ChangesReady)
    }
}
