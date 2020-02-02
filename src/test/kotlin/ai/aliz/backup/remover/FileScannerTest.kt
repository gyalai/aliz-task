package ai.aliz.backup.remover
import io.reactivex.observers.TestObserver
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class FileScannerTest {

    @TempDir
    lateinit var tmpFolder: Path

    lateinit var fileScanner: FileScanner

    lateinit var fileObserver: TestObserver<File>

    lateinit var folderObserver: TestObserver<File>

    @BeforeEach
    fun before() {
        this.folderObserver = TestObserver<File>()
        this.fileObserver = TestObserver<File>()
        this.fileScanner = FileScanner()
        this.fileScanner.folderRemoveChannel.subscribe(this.folderObserver)
        this.fileScanner.fileRemoveChannel.subscribe(this.fileObserver)
    }

    @Test
    fun `should mark empty folder`() {
        // when
        fileScanner.cleanUp(tmpFolder)

        // then
        folderObserver.assertComplete()
        folderObserver.assertOf {
            it.assertValue { folder -> folder.toPath() == tmpFolder }
        }
        fileObserver.assertComplete();
    }

    @Test
    fun `should mark bak file`() {
        // given
        createFolders(tmpFolder) {
            folder("test") {
                file("test.txt")
                file("test2.bak")
            }
        }

        // when
        fileScanner.cleanUp(tmpFolder)

        // then
        folderObserver.assertComplete()
        folderObserver.assertNoValues()
        fileObserver.assertComplete()
        fileObserver.assertValue { file -> file.toPath() == tmpFolder.resolve("test/test2.bak") }
    }

    @Test
    fun `should not mark bak file even if it found first`() {
        // given
        createFolders(tmpFolder) {
            folder("test") {
                file("test2.bak")
                file("test2.txt")
            }
        }

        // when
        fileScanner.cleanUp(tmpFolder)

        // then
        folderObserver.assertComplete()
        folderObserver.assertNoValues()
        fileObserver.assertComplete()
        fileObserver.assertNoValues()
    }

    @Test
    fun `should not mark bak file even if it found second`() {
        // given
        createFolders(tmpFolder) {
            folder("test") {
                file("test2.txt")
                file("test2.bak")
            }
        }
        // when
        fileScanner.cleanUp(tmpFolder)

        // then
        folderObserver.assertComplete()
        folderObserver.assertNoValues()
        fileObserver.assertComplete()
        fileObserver.assertNoValues()
    }

    @Test
    fun `should remove multiple bak files `() {
        // given
        createFolders(tmpFolder) {
            folder("test") {
                file("test.bak")
                file("test2.bak")
            }
        }

        // when
        fileScanner.cleanUp(tmpFolder)

        // then
        folderObserver.assertComplete()
        folderObserver.assertValueAt(0) { file -> file.toPath() == tmpFolder.resolve("test") }
        folderObserver.assertValueAt(1) { file -> file.toPath() == tmpFolder }
        fileObserver.assertComplete()
        fileObserver.assertValueAt(0) { file -> file.toPath() == tmpFolder.resolve("test/test.bak") }
        fileObserver.assertValueAt(1) { file -> file.toPath() == tmpFolder.resolve("test/test2.bak") }
    }

    @Test
    fun `should remove multiple bak files even after folder switch`() {
        // given
        createFolders(tmpFolder) {
            folder("test") {
                file("test.bak")
            }
            folder("test2") {
                file("test.bak")
            }
        }
        // when
        fileScanner.cleanUp(tmpFolder)

        // then
        folderObserver.assertComplete()
        folderObserver.assertValueAt(0) { file -> file.toPath() == tmpFolder.resolve("test") }
        folderObserver.assertValueAt(1) { file -> file.toPath() == tmpFolder.resolve("test2") }
        folderObserver.assertValueAt(2) { file -> file.toPath() == tmpFolder }
        fileObserver.assertComplete()
        fileObserver.assertValueAt(0) { file -> file.toPath() == tmpFolder.resolve("test/test.bak") }
        fileObserver.assertValueAt(1) { file -> file.toPath() == tmpFolder.resolve("test2/test.bak") }
    }

    @Test
    fun `should remove one folder`() {
        // given
        createFolders(tmpFolder) {
            folder("test") {
                file("test.bak")
            }
            folder("test2") {
                file("test.txt")
            }
        }
        // when
        fileScanner.cleanUp(tmpFolder)

        // then
        folderObserver.assertComplete()
        folderObserver.assertValue { file -> file.toPath() == tmpFolder.resolve("test") }
        fileObserver.assertComplete()
        fileObserver.assertValue { file -> file.toPath() == tmpFolder.resolve("test/test.bak") }
    }

    @Test
    fun `should remove folders in a complex tree`() {
        // given
        createFolders(tmpFolder) {
            folder("test") {
                folder("testInner") {
                    file("test.txt")
                }
                file("test.bak")
            }
            folder("test2") {
                file("test.txt")
                folder("testInner2") {
                    file("test.bak")
                }
                folder("testInner3") {
                    file("test2.txt")
                }
                folder("testInner4") {
                    file("test2.bak")
                    folder("testInner5") {
                        file("test2.bak")
                    }
                    file("testInner6.bak")
                }
            }
        }
        // when
        fileScanner.cleanUp(tmpFolder)

        // then
        folderObserver.assertComplete()
        folderObserver.assertValueAt(0) { file -> file.toPath() == tmpFolder.resolve("test2/testInner2") }
        folderObserver.assertValueAt(1) { file -> file.toPath() == tmpFolder.resolve("test2/testInner4/testInner5") }
        folderObserver.assertValueAt(2) { file -> file.toPath() == tmpFolder.resolve("test2/testInner4") }
        fileObserver.assertComplete()
        fileObserver.assertValueAt(0) { file -> file.toPath() == tmpFolder.resolve("test/test.bak") }
        fileObserver.assertValueAt(1) { file -> file.toPath() == tmpFolder.resolve("test2/testInner2/test.bak") }
        fileObserver.assertValueAt(2) { file -> file.toPath() == tmpFolder.resolve("test2/testInner4/testInner5/test2.bak") }
        fileObserver.assertValueAt(3) { file -> file.toPath() == tmpFolder.resolve("test2/testInner4/test2.bak") }
        fileObserver.assertValueAt(4) { file -> file.toPath() == tmpFolder.resolve("test2/testInner4/testInner6.bak") }
    }
}

fun createFolders(rootFolder: Path, body: FolderBuilder.() -> Unit) = FolderBuilder().apply(body).build(rootFolder)

class FolderBuilder(private val folderName: String? = null) {

    private val folders = mutableListOf<FolderBuilder>()

    private val files = mutableListOf<String>()

    fun folder(folderName: String, body: FolderBuilder.() -> Unit) =
            FolderBuilder(folderName).apply(body).also { folders.add(it) }

    fun file(fileName: String) = files.add(fileName)

    fun build(rootFolder: Path) {
        var folder = rootFolder
        if (folderName != null) {
            folder = folder.resolve(folderName)
            assert(!rootFolder.relativize(folder).startsWith(".."))
                { "Child path is outside of the parent path, you probably mistyped the folder name." }

            Files.createDirectory(folder)
        }
        folders.forEach { it.build(folder) }

        files.forEach {
            Files.createFile(folder.resolve(it))
        }
    }
}
