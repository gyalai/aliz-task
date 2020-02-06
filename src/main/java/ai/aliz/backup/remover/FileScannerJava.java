package ai.aliz.backup.remover;

import io.reactivex.subjects.ReplaySubject;
import io.reactivex.subjects.Subject;
import kotlin.io.FileTreeWalk;
import kotlin.io.FileWalkDirection;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Deque;
import java.util.LinkedList;

public class FileScannerJava implements FileVisitor<Path> {

    public static final String EXTENSION_BAK = "bak";
    private final Deque<Path> folderStack = new LinkedList<>();
    private final Deque<Path> suspiciousBackFileStack = new LinkedList<>();
    private final Deque<Boolean> folderToKeepStack = new LinkedList<>();

    private String lastFileNameWithoutExtension;

    private String lastBackFileNameWithoutExtension;

    private Path suspiciousBackFile;

    private boolean folderToKeep;

    private Subject<Path> fileRemoveChannel = ReplaySubject.create();
    private Subject<Path> folderRemoveChannel = ReplaySubject.create();

    public void cleanUp(Path folder) {
        try {
            Files.walkFileTree(folder, this);
        } catch (Exception e) {
            fileRemoveChannel.onError(e);
            folderRemoveChannel.onError(e);
        } finally {
            fileRemoveChannel.onComplete();
            folderRemoveChannel.onComplete();
        }
    }

    @Override
    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
        if (attrs.isRegularFile()) {
            var file = path.toFile();

            final String extension = getExtension(file);
            final String nameWithoutExtension = getNameWithoutExtension(file);

            if (EXTENSION_BAK.equals(extension)) {

                if (!nameWithoutExtension.equals(lastFileNameWithoutExtension)) {
                    newSuspiciousBackFile(path, nameWithoutExtension);
                } else {
                    cleanUp();
                }
            } else if (nameWithoutExtension.equals(lastBackFileNameWithoutExtension)) {
                cleanUp();
            } else if (!nameWithoutExtension.equals(lastFileNameWithoutExtension)) {
                newFilename();
            }

            lastFileNameWithoutExtension = getNameWithoutExtension(file);

            folderToKeep = folderToKeep || !EXTENSION_BAK.equals(extension);
        }

        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path folder, BasicFileAttributes attrs) {
        stashParentFolderState(folder);

        cleanUp();

        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path folder, IOException exc) {
        catchSuspicious();

        cleanUp();

        var folderOnStack = folderStack.pop();

        if (!folder.equals(folderOnStack)) {
            throw new IllegalStateException("We left from a folder we were not been!");
        }

        restoreParentFolderState(folder);

        return FileVisitResult.CONTINUE;
    }

    public io.reactivex.subjects.Subject<Path> getFileRemoveChannel() {
        return fileRemoveChannel;
    }

    public Subject<Path> getFolderRemoveChannel() {
        return folderRemoveChannel;
    }

    private void newFilename() {
        catchSuspicious();
        cleanUp();
    }

    private void newSuspiciousBackFile(Path path, String nameWithoutExtension) {
        catchSuspicious();

        cleanUp();

        suspiciousBackFile = path;
        lastBackFileNameWithoutExtension = nameWithoutExtension;
    }

    private void cleanUp() {
        suspiciousBackFile = null;
        lastBackFileNameWithoutExtension = null;
        lastFileNameWithoutExtension = null;
    }

    private void catchSuspicious() {
        if (suspiciousBackFile != null) {
            fileRemoveChannel.onNext(suspiciousBackFile);
        }
    }

    private void stashParentFolderState(Path folder) {
        folderStack.push(folder);
        folderToKeepStack.push(folderToKeep);
        folderToKeep = false;
        suspiciousBackFileStack.push(suspiciousBackFile);
    }

    private void restoreParentFolderState(Path folder) {
        suspiciousBackFile = suspiciousBackFileStack.pop();
        lastBackFileNameWithoutExtension = suspiciousBackFile != null ? getNameWithoutExtension(suspiciousBackFile.toFile()) : null;
        var parentMark = folderToKeepStack.pop();

        if (!folderToKeep) {
            folderRemoveChannel.onNext(folder);
        } else {
            // parent folder has to remain as well
            parentMark = true;
        }

        folderToKeep = parentMark;
    }

    private String getNameWithoutExtension(File file) {
        return StringUtils.substringBeforeLast(file.getName(), ".");
    }

    private String getExtension(File file) {
        return StringUtils.substringAfterLast(file.getName(), ".");
    }
}
