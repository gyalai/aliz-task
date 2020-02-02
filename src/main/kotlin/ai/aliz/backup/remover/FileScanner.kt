package ai.aliz.backup.remover
import io.reactivex.subjects.ReplaySubject
import java.io.File
import java.nio.file.Path
import java.util.LinkedList

class FileScanner {

    private var folderStack: LinkedList<File> = LinkedList()

    private var lastFileNameWithoutExtension: String? = null

    private var lastBackFileNameWithoutExtension: String? = null

    private var suspiciousBackFileStack = LinkedList<File?>()
    private var suspiciousBackFile: File? = null

    private var folderToKeepStack = LinkedList<Boolean>()
    private var folderToKeep: Boolean = false

    val fileRemoveChannel = ReplaySubject.create<File>()
    val folderRemoveChannel = ReplaySubject.create<File>()

    fun cleanUp(folder: Path) {
        kotlin.runCatching {
            folder.toFile().walkTopDown()
                    .onEnter(::stepIn)
                    .onLeave(::stepOut)
                    .forEach(::checkFile)
        }.onFailure {
            fileRemoveChannel.onError(it)
            folderRemoveChannel.onError(it)
        }
        fileRemoveChannel.onComplete()
        folderRemoveChannel.onComplete()
    }

    private fun checkFile(file: File) {
        if (file.isFile) {

            when {
                file.extension == "bak" && file.nameWithoutExtension != lastFileNameWithoutExtension -> {
                    newSuspiciousBackFile(file)
                }
                file.extension == "bak" -> {
                    cleanUp()
                }
                file.nameWithoutExtension == lastBackFileNameWithoutExtension -> {
                    // false positive
                    cleanUp()
                }
                file.nameWithoutExtension != lastFileNameWithoutExtension -> {
                    newFilename()
                }
            }

            lastFileNameWithoutExtension = file.nameWithoutExtension

            folderToKeep = folderToKeep || file.extension != "bak"
        }
    }

    private fun newFilename() {
        suspiciousBackFile?.let { fileRemoveChannel.onNext(it) }
        cleanUp()
    }

    private fun newSuspiciousBackFile(file: File) {
        suspiciousBackFile?.let { fileRemoveChannel.onNext(it) }
        cleanUp()

        suspiciousBackFile = file
        lastBackFileNameWithoutExtension = file.nameWithoutExtension
    }

    private fun cleanUp() {
        suspiciousBackFile = null
        lastBackFileNameWithoutExtension = null
        lastFileNameWithoutExtension = null
    }

    private fun stepIn(folder: File): Boolean {
        stashParentFolderState(folder)

        cleanUp()

        return true
    }

    private fun stepOut(folder: File) {
        suspiciousBackFile?.let { fileRemoveChannel.onNext(it) }
        cleanUp()

        val folderOnStack = folderStack.pop()

        assert(folder == folderOnStack) { "We left from a folder we were not been!" }

        restoreParentFolderState(folder)
    }

    private fun stashParentFolderState(folder: File) {
        folderStack.push(folder)
        folderToKeepStack.push(folderToKeep)
        folderToKeep = false
        suspiciousBackFileStack.push(suspiciousBackFile)
    }

    private fun restoreParentFolderState(folder: File) {
        suspiciousBackFile = suspiciousBackFileStack.pop()
        lastBackFileNameWithoutExtension = suspiciousBackFile?.nameWithoutExtension
        var parentMark = folderToKeepStack.pop()

        if (!folderToKeep) {
            folderRemoveChannel.onNext(folder)
        } else {
            // parent folder has to remain as well
            parentMark = true
        }

        folderToKeep = parentMark
    }
}


