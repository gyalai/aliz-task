package ai.aliz.backup.remover;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static java.util.stream.Collectors.toList;

public class BackupRemoverRxJava {
    private static final Logger logger = LoggerFactory.getLogger(BackupRemoverRxJava.class);
    private final Path path;
    private final int concurrency;
    private final long retries;

    public BackupRemoverRxJava(Path path, int concurrency, long retries) {
        this.path = path;
        this.concurrency = concurrency;
        this.retries = retries;
    }

    public void cleanUp() throws ExecutionException, InterruptedException {
        var folderScanner = new FileScannerJava();

        var folderObservable = folderScanner.getFolderRemoveChannel()
                .map(FileStateDetail::new)
                .concatMap(this::deleteFile)
                .toList();

        var future = folderScanner.getFileRemoveChannel()
                .map(FileStateDetail::new)
                .flatMap(this::deleteFile, concurrency)
                .toList()
                .concatWith(folderObservable)
                .toList()
                .doOnSuccess(deletes -> {
                    logResult(deletes.get(0), deletes.get(1));
                })
                .toFuture();

        folderScanner.cleanUp(path);

        future.get();
    }

    private void logResult(List<FileStateDetail> filesDeleted, List<FileStateDetail> foldersDeleted) {
        var failedFiles = filesDeleted.stream().filter ( file -> !file.isDeleted() ).collect(toList());
        var failedFolders = foldersDeleted.stream().filter ( file -> !file.isDeleted() ).collect(toList());

        logger.info("Files deleted: {}", filesDeleted.stream().filter ( file -> file.isDeleted() ).count());
        logger.info("Folders deleted: {}", foldersDeleted.stream().filter ( file -> file.isDeleted() ).count());
        logger.info("Failed files {}", failedFiles.size());
        failedFiles.forEach ( fileDetails ->
            logger.info("\t{} - {}",
                    fileDetails.getPath(), fileDetails.getError() != null ? fileDetails.getError().getMessage(): "" )
        );

        logger.info("Failed folders {}", failedFolders.size());
        failedFolders.forEach ( fileDetails ->
                logger.info("\t{} - {}",
                        fileDetails.getPath(), fileDetails.getError() != null ? fileDetails.getError().getMessage(): "" )
        );
    }

    private ObservableSource<FileStateDetail> deleteFile(FileStateDetail details) {
        return Observable.just(details)
                .subscribeOn(Schedulers.io())
            .map(file -> {
                Files.delete(file.path);

                file.deleted = true;
//                logger.info("Deleted {}", file.path);

                return file;
            })
            .retry(retries)
            .onErrorReturn(error -> {
                details.setError(error);

                return details;
            });

    }

    private static class FileStateDetail {
        private final Path path;

        private boolean deleted;

        private Throwable error;


        private FileStateDetail(Path path) {
            this.path = path;
        }

        public Path getPath() {
            return path;
        }

        public boolean isDeleted() {
            return deleted;
        }

        public void setDeleted(boolean deleted) {
            this.deleted = deleted;
        }

        public Throwable getError() {
            return error;
        }

        public void setError(Throwable error) {
            this.error = error;
        }
    }
}
