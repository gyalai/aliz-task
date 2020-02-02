package ai.aliz.backup.remover
import io.reactivex.BackpressureStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

private val logger = KotlinLogging.logger { }

@ExperimentalCoroutinesApi
@FlowPreview
class BackupRemover(
    private val folder: Path,
    private val concurrency: Int,
    private val retries: Long
) {

    fun cleanUp() = runBlocking {
        val fileScanner = FileScanner()

        val fileDeleteJob = async(Dispatchers.IO) {
            fileScanner.fileRemoveChannel
                    .toFlowable(BackpressureStrategy.BUFFER)
                    .asFlow()
                    .flatMapMerge(concurrency) { file ->
                        deleteFlow(file)
                    }.toList()
        }

        fileScanner.cleanUp(folder)

        val filesDeleted = fileDeleteJob.await()

        // Folders remove is sync operation after the files has been cleaned up to avoid concurrency issues
        val foldersDeleted = fileScanner.folderRemoveChannel
            .toFlowable(BackpressureStrategy.BUFFER)
            .asFlow()
            .flatMapConcat { file ->
                deleteFlow(file)
            }.toList()

        logResult(filesDeleted, foldersDeleted)
    }

    private fun logResult(filesDeleted: List<FileStateDetail>, foldersDeleted: List<FileStateDetail>) {
        val failedFiles = filesDeleted.filter { !it.deleted }
        val failedFolders = foldersDeleted.filter { !it.deleted }

        logger.info { "Files deleted: ${filesDeleted.filter { it.deleted }.count()}" }
        logger.info { "Folders deleted: ${foldersDeleted.filter { it.deleted }.count()}" }
        logger.info { "Failed files (${failedFiles.count()}): " }
        failedFiles.forEach {
            logger.info { "\t${it.path} - ${it.error?.message}" }
        }

        logger.info { "Failed folders (${failedFolders.count()}): " }
        failedFolders.forEach {
            logger.info { "\t${it.path} - ${it.error?.message}" }
        }
    }

    private fun deleteFlow(file: File): Flow<FileStateDetail> {
        return flow {
            emit(delete(file))
        }
        .retry(retries)
        .catch { error -> FileStateDetail(file.toPath(), false, error) }
    }

    private fun delete(file: File): FileStateDetail {
        val path = file.toPath()
        Files.delete(path)

        // Uncomment for checking it is running on different threads
//        logger.info { "Deleted: $file" }

        return FileStateDetail(path)
    }
}

data class FileStateDetail(val path: Path, val deleted: Boolean = true, val error: Throwable? = null)
