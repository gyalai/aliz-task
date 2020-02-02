import ai.aliz.backup.remover.BackupRemover
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import java.nio.file.Path

class Main : CliktCommand() {
    private val rootFolder: String by option(help = "Starting folder").default(".", "currentFolder")
    private val deleteThreadCount: Int by option(help = "Number of threads deleting the bak files")
        .int().default(5)
    private val maxNofRetries: Long by option(help = "Maximum number of retries for deleting a bak file or folder")
        .long().default(2)

    @FlowPreview
    @ExperimentalCoroutinesApi
    override fun run() {
        val folder = Path.of(rootFolder).toAbsolutePath()

        BackupRemover(folder, deleteThreadCount, maxNofRetries).cleanUp()
    }
}

fun main(args: Array<String>) = Main().main(args)
