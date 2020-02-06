package ai.aliz.backup.remover

import io.reactivex.observers.TestObserver
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class FileScannerJavaTest {

    @TempDir
    lateinit var tmpFolder: Path

    lateinit var fileScanner: FileScannerJava

    lateinit var fileObserver: TestObserver<Path>

    lateinit var folderObserver: TestObserver<Path>

    @BeforeEach
    fun before() {
        this.folderObserver = TestObserver()
        this.fileObserver = TestObserver()
        this.fileScanner = FileScannerJava()
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
            it.assertValue { folder -> folder == tmpFolder }
        }
        fileObserver.assertComplete()
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
        fileObserver.assertValue { file -> file == tmpFolder.resolve("test/test2.bak") }
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
        folderObserver.assertValueAt(0) { file -> file == tmpFolder.resolve("test") }
        folderObserver.assertValueAt(1) { file -> file == tmpFolder }
        fileObserver.assertComplete()
        fileObserver.assertValueAt(0) { file -> file == tmpFolder.resolve("test/test.bak") }
        fileObserver.assertValueAt(1) { file -> file == tmpFolder.resolve("test/test2.bak") }
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
        folderObserver.assertValueAt(0) { file -> file == tmpFolder.resolve("test") }
        folderObserver.assertValueAt(1) { file -> file == tmpFolder.resolve("test2") }
        folderObserver.assertValueAt(2) { file -> file == tmpFolder }
        fileObserver.assertComplete()
        fileObserver.assertValueAt(0) { file -> file == tmpFolder.resolve("test/test.bak") }
        fileObserver.assertValueAt(1) { file -> file == tmpFolder.resolve("test2/test.bak") }
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
        folderObserver.assertValue { file -> file == tmpFolder.resolve("test") }
        fileObserver.assertComplete()
        fileObserver.assertValue { file -> file == tmpFolder.resolve("test/test.bak") }
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
        folderObserver.assertValueAt(0) { file -> file == tmpFolder.resolve("test2/testInner2") }
        folderObserver.assertValueAt(1) { file -> file == tmpFolder.resolve("test2/testInner4/testInner5") }
        folderObserver.assertValueAt(2) { file -> file == tmpFolder.resolve("test2/testInner4") }
        fileObserver.assertComplete()
        fileObserver.assertValueAt(0) { file -> file == tmpFolder.resolve("test/test.bak") }
        fileObserver.assertValueAt(1) { file -> file == tmpFolder.resolve("test2/testInner2/test.bak") }
        fileObserver.assertValueAt(2) { file -> file == tmpFolder.resolve("test2/testInner4/testInner5/test2.bak") }
        fileObserver.assertValueAt(3) { file -> file == tmpFolder.resolve("test2/testInner4/test2.bak") }
        fileObserver.assertValueAt(4) { file -> file == tmpFolder.resolve("test2/testInner4/testInner6.bak") }
    }
}
