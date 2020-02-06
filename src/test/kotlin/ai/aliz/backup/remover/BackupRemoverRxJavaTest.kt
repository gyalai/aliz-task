package ai.aliz.backup.remover

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class BackupRemoverRxJavaTest {
    @TempDir
    lateinit var tmpFolder: Path

    @Test
    fun should() {
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

        BackupRemoverRxJava(tmpFolder, 5, 2).cleanUp()

        assertThat(tmpFolder.resolve("test").toFile().exists()).isTrue()
        assertThat(tmpFolder.resolve("test/testInner").toFile().exists()).isTrue()
        assertThat(tmpFolder.resolve("test2/testInner2").toFile().exists()).isFalse()
        assertThat(tmpFolder.resolve("test2/testInner3").toFile().exists()).isTrue()
        assertThat(tmpFolder.resolve("test2/testInner4").toFile().exists()).isFalse()
    }
}
