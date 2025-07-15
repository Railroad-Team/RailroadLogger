package io.github.railroad.logger;

import io.github.railroad.logger.util.LoggerUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * LoggerManager is responsible for managing multiple loggers, initializing them, archiving logs, and cleaning up old logs.
 * It should be initialized once at the start of the application.
 */
public final class LoggerManager {
    private static final List<Logger> LOGGERS = new ArrayList<>();

    private static boolean initialized = false;

    /**
     * Initializes the LoggerManager, setting up all registered loggers and preparing log files.
     * This method should be called once at the start of the application.
     */
    public static void init() {
        if (initialized)
            return;

        Set<Path> logFiles = new HashSet<>();
        for (Logger logger : LOGGERS) {
            if (logger == null)
                continue;

            logFiles.addAll(logger.getFilesToLogTo());
        }

        for (Logger logger : LOGGERS) {
            if (logger == null)
                continue;

            logger.init();
        }

        Map<Path, List<Logger>> logFilesToLoggers = groupLoggersByFile();
        try {
            archiveLogs(logFilesToLoggers);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to archive logs", exception);
        }

        for (Path logFile : logFiles) {
            if (logFile == null)
                continue;

            try {
                if (Files.notExists(logFile)) {
                    Files.createDirectories(logFile.getParent());
                    Files.createFile(logFile);
                }
            } catch (IOException exception) {
                throw new RuntimeException("Failed to create log file: " + logFile, exception);
            }
        }

        initialized = true;
        Runtime.getRuntime().addShutdownHook(new Thread(LoggerManager::shutdown));
    }

    /**
     * Shuts down the LoggerManager, closing all registered loggers and cleaning up resources.
     * This method is automatically called when the application exits.
     */
    public static void shutdown() {
        if (!initialized)
            return;

        for (Logger logger : LOGGERS) {
            if (logger == null)
                continue;

            logger.close();
        }

        initialized = false;
    }

    /**
     * Creates a new LoggerImpl.Builder instance for a logger with the specified name.
     *
     * @param name The name of the logger.
     * @return A new LoggerImpl.Builder instance.
     */
    public static LoggerImpl.Builder create(String name) {
        return new LoggerImpl.Builder(name);
    }

    /**
     * Creates a new LoggerImpl.Builder instance for a logger associated with the specified class.
     *
     * @param clazz The class for which the logger is being created.
     * @return A new LoggerImpl.Builder instance.
     */
    public static LoggerImpl.Builder create(Class<?> clazz) {
        if (clazz == null)
            throw new IllegalArgumentException("Class cannot be null");

        return new LoggerImpl.Builder(clazz);
    }

    /**
     * Registers a new logger with the LoggerManager.
     * If the logger is already registered, it will return the existing instance.
     *
     * @param logger The logger to register.
     * @return The registered logger instance.
     */
    public static <T extends Logger> T registerLogger(T logger) {
        if (logger == null)
            throw new IllegalArgumentException("Logger cannot be null");

        if (LOGGERS.contains(logger))
            return logger;

        LOGGERS.add(logger);

        if (initialized) {
            try {
                logger.init();
            } catch (Exception exception) {
                throw new RuntimeException("Failed to initialize logger: " + logger.getName(), exception);
            }
        }

        return logger;
    }

    /**
     * Unregisters a logger from the LoggerManager.
     * If the logger is not registered, it will throw an exception.
     *
     * @param logger The logger to unregister.
     */
    public static void unregisterLogger(Logger logger) {
        if (logger == null)
            throw new IllegalArgumentException("Logger cannot be null");

        if (!LOGGERS.contains(logger))
            throw new IllegalArgumentException("Logger is not registered: " + logger.getName());

        if (initialized) {
            try {
                logger.close();
            } catch (Exception exception) {
                throw new RuntimeException("Failed to close logger: " + logger.getName(), exception);
            }
        }

        LOGGERS.remove(logger);
    }

    private static Map<Path, List<Logger>> groupLoggersByFile() {
        Map<Path, List<Logger>> logFilesToLoggers = new HashMap<>();

        for (Logger logger : LOGGERS) {
            if (logger == null)
                continue;

            for (Path logFile : logger.getFilesToLogTo()) {
                if (logFile == null)
                    continue;

                logFilesToLoggers.computeIfAbsent(logFile, $ -> new ArrayList<>()).add(logger);
            }
        }

        return logFilesToLoggers;
    }

    private static void archiveLogs(Map<Path, List<Logger>> logFiles) throws IOException {
        for (Path logPath : logFiles.keySet()) {
            if (Files.notExists(logPath) || !Files.isRegularFile(logPath) || Files.isDirectory(logPath))
                continue;

            FileTime dateCreated = Files.readAttributes(logPath, BasicFileAttributes.class).creationTime();
            List<Logger> loggers = logFiles.get(logPath);
            for (Logger logger : loggers) {
                Path logDirectory = logger.getLogDirectory();
                String timestamp = logger.formatFileTime(dateCreated);
                String logNameWithoutExtension = logPath.getFileName().toString().substring(0, logPath.getFileName().toString().lastIndexOf('.'));
                String candidate = String.format("%s_%s.log", timestamp, logNameWithoutExtension);
                Path archivedLogPath = logDirectory.resolve(candidate);

                int count = 1;
                while (Files.exists(archivedLogPath)) {
                    archivedLogPath = logDirectory.resolve(String.format("%s_%s(%d).log", timestamp, logNameWithoutExtension, count));
                    count++;
                }

                copyAndRecreate(logPath, archivedLogPath);
                if (logger.isCompressionEnabled()) {
                    LoggerUtils.compress(archivedLogPath);
                    Files.deleteIfExists(archivedLogPath);
                }
            }
        }

        for (Logger logger : LOGGERS) {
            deleteOldLogs(logger);
        }
    }

    private static void copyAndRecreate(Path filePath, Path newPath) throws IOException {
        // We copy the file instead of moving, and then set the creation time manually so that we can bypass
        // windows' file-system tunneling
        Files.copy(filePath, newPath, StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.setAttribute(filePath, "creationTime", FileTime.from(Instant.now()));
        } catch (Exception ignored) {}
        Files.write(filePath, new byte[0], StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void deleteOldLogs(Logger logger) throws IOException {
        long frequency = logger.getDeletionFrequency();
        if (frequency <= 0)
            return;

        Instant cutoff = Instant.now().minusMillis(frequency);
        Path dir = logger.getLogDirectory();
        if (Files.notExists(dir) || !Files.isDirectory(dir))
            return;

        try (Stream<Path> files = Files.list(dir)) {
            List<Path> filesList = files.filter(Files::isRegularFile).toList();
            for (Path file : filesList) {
                DateTimeFormatter logDateFormat = logger.getLogDateFormat();
                try {
                    String fileName = file.getFileName().toString()
                            .replace(".tar.gz", "")
                            .replace(".log", "");
                    int underscoreIndex = fileName.lastIndexOf('_');
                    if (underscoreIndex != -1) {
                        fileName = fileName.substring(0, underscoreIndex);
                    }

                    logDateFormat.parse(fileName);
                } catch (DateTimeException ignored) {
                    continue;
                }

                try {
                    if (!Files.getLastModifiedTime(file).toInstant().isBefore(cutoff))
                        continue;
                } catch (IOException exception) {
                    logger.error("Failed to get last modified time for file: {}", file, exception);
                    continue;
                }

                try {
                    Files.deleteIfExists(file);
                } catch (IOException exception) {
                    logger.error("Failed to delete old log file: {}", file, exception);
                }
            }
        }
    }
}
