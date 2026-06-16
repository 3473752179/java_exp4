import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

public class MultiThreadDownloadDemo {

    private static final int THREAD_COUNT = 4;
    private static final int BUFFER_SIZE = 1024;

    public static void main(String[] args) {
        Path sourcePath = Path.of("source_demo.txt");
        Path targetPath = Path.of("downloaded_demo.txt");

        try {
            createDemoSourceFile(sourcePath);
            long fileSize = Files.size(sourcePath);
            prepareTargetFile(targetPath, fileSize);

            System.out.println("Source file: " + sourcePath.toAbsolutePath());
            System.out.println("Target file: " + targetPath.toAbsolutePath());
            System.out.println("File size: " + fileSize + " bytes");
            System.out.println("Thread count: " + THREAD_COUNT);
            System.out.println("--------------------------------");

            long partSize = (fileSize + THREAD_COUNT - 1) / THREAD_COUNT;
            CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
            Object printLock = new Object();

            for (int i = 0; i < THREAD_COUNT; i++) {
                long start = i * partSize;
                long end = Math.min(fileSize - 1, start + partSize - 1);

                if (start >= fileSize) {
                    latch.countDown();
                    continue;
                }

                DownloadWorker worker = new DownloadWorker(
                        i + 1,
                        sourcePath,
                        targetPath,
                        start,
                        end,
                        latch,
                        printLock
                );
                worker.start();
            }

            latch.await();
            System.out.println("--------------------------------");
            System.out.println("All download threads finished.");

            if (Files.mismatch(sourcePath, targetPath) == -1) {
                System.out.println("Verification passed: files are identical.");
            } else {
                System.out.println("Verification failed: files are different.");
            }
        } catch (Exception e) {
            System.out.println("Execution failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createDemoSourceFile(Path sourcePath) throws IOException {
        if (Files.exists(sourcePath)) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 1; i <= 2000; i++) {
            builder.append("This is demo line ").append(i)
                    .append(" for the multithreaded segmented download example.")
                    .append(System.lineSeparator());
        }
        Files.writeString(sourcePath, builder.toString(), StandardCharsets.UTF_8);
    }

    private static void prepareTargetFile(Path targetPath, long fileSize) throws IOException {
        try (RandomAccessFile targetFile = new RandomAccessFile(targetPath.toFile(), "rw")) {
            targetFile.setLength(fileSize);
        }
    }

    private static class DownloadWorker extends Thread {
        private final int workerId;
        private final Path sourcePath;
        private final Path targetPath;
        private final long start;
        private final long end;
        private final CountDownLatch latch;
        private final Object printLock;

        DownloadWorker(int workerId,
                       Path sourcePath,
                       Path targetPath,
                       long start,
                       long end,
                       CountDownLatch latch,
                       Object printLock) {
            this.workerId = workerId;
            this.sourcePath = sourcePath;
            this.targetPath = targetPath;
            this.start = start;
            this.end = end;
            this.latch = latch;
            this.printLock = printLock;
        }

        @Override
        public void run() {
            try (RandomAccessFile sourceFile = new RandomAccessFile(sourcePath.toFile(), "r");
                 RandomAccessFile targetFile = new RandomAccessFile(targetPath.toFile(), "rw")) {

                sourceFile.seek(start);
                targetFile.seek(start);

                byte[] buffer = new byte[BUFFER_SIZE];
                long remaining = end - start + 1;
                long downloaded = 0;

                log("Started, range = [" + start + ", " + end + "]");

                while (remaining > 0) {
                    int bytesToRead = (int) Math.min(buffer.length, remaining);
                    int len = sourceFile.read(buffer, 0, bytesToRead);

                    if (len == -1) {
                        break;
                    }

                    targetFile.write(buffer, 0, len);
                    remaining -= len;
                    downloaded += len;

                    log("Progress: " + downloaded + " / " + (end - start + 1) + " bytes");

                    // Sleep briefly to simulate network latency.
                    Thread.sleep(20);
                }

                log("Finished.");
            } catch (Exception e) {
                log("Failed: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        }

        private void log(String message) {
            synchronized (printLock) {
                System.out.println("Thread-" + workerId + ": " + message);
            }
        }
    }
}
