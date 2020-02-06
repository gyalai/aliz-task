package ai.aliz.backup.remover

import java.nio.file.Files
import java.nio.file.Path

fun createFolders(rootFolder: Path, body: TestFolderBuilder.() -> Unit) = TestFolderBuilder().apply(body).build(rootFolder)

class TestFolderBuilder(private val folderName: String? = null) {

    private val folders = mutableListOf<TestFolderBuilder>()

    private val files = mutableListOf<String>()

    fun folder(folderName: String, body: TestFolderBuilder.() -> Unit) =
            TestFolderBuilder(folderName).apply(body).also { folders.add(it) }

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
