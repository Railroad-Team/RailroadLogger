package dev.railroadide.logger;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Interface for a logging system that provides methods to log messages at different levels.
 * Implementations of this interface should handle the actual logging mechanism.
 */
public interface Logger {
    /**
     * Initializes the logger, setting up any necessary resources or configurations.
     */
    default void init() {
    }

    /**
     * Gets the name of the logger.
     *
     * @return The name of the logger.
     */
    String getName();

    /**
     * Logs an error message with the specified objects.
     *
     * @param logMessage The error message to log.
     * @param objects    Additional objects to include in the log message.
     */
    default void error(String logMessage, Object... objects) {
        log(logMessage, LoggingLevel.ERROR, objects);
    }

    /**
     * Logs a warning message with the specified objects.
     *
     * @param logMessage The warning message to log.
     * @param objects    Additional objects to include in the log message.
     */
    default void warn(String logMessage, Object... objects) {
        log(logMessage, LoggingLevel.WARN, objects);
    }

    /**
     * Logs an informational message with the specified objects.
     *
     * @param logMessage The informational message to log.
     * @param objects    Additional objects to include in the log message.
     */
    default void info(String logMessage, Object... objects) {
        log(logMessage, LoggingLevel.INFO, objects);
    }

    /**
     * Logs a debug message with the specified objects.
     *
     * @param logMessage The debug message to log.
     * @param objects    Additional objects to include in the log message.
     */
    default void debug(String logMessage, Object... objects) {
        log(logMessage, LoggingLevel.DEBUG, objects);
    }

    /**
     * Logs a message with the specified logging level and objects.
     *
     * @param message The message to log.
     * @param level   The logging level.
     * @param objects Additional objects to include in the log message.
     */
    void log(String message, LoggingLevel level, Object... objects);

    /**
     * Sets whether compression is enabled for the logger.
     *
     * @param compression true to enable compression, false to disable it.
     */
    void setCompressionEnabled(boolean compression);

    /**
     * Gets the frequency of logging in milliseconds.
     *
     * @return The frequency in milliseconds.
     */
    boolean isCompressionEnabled();

    /**
     * Gets the frequency of logging in milliseconds.
     *
     * @return The frequency in milliseconds.
     */
    long getLogFrequency();

    /**
     * Sets the frequency of logging in a specified time unit.
     *
     * @param frequency The frequency value.
     * @param timeUnit  The time unit for the frequency.
     */
    void setLogFrequency(long frequency, TimeUnit timeUnit);

    /**
     * Gets the frequency of log deletion in milliseconds.
     *
     * @return The frequency in milliseconds.
     */
    long getDeletionFrequency();

    /**
     * Sets the frequency of log deletion in a specified time unit.
     *
     * @param frequency The frequency value.
     * @param timeUnit  The time unit for the frequency.
     */
    void setDeletionFrequency(long frequency, TimeUnit timeUnit);

    /**
     * Gets the directory where logs are stored.
     *
     * @return The path to the log directory.
     */
    Path getLogDirectory();

    /**
     * Sets the directory where logs will be stored.
     *
     * @param logDirectory The path to the log directory.
     */
    void setLogDirectory(Path logDirectory);

    /**
     * Gets the current logging level.
     *
     * @return The current logging level.
     */
    LoggingLevel getLoggingLevel();

    /**
     * Sets the logging level for the logger.
     *
     * @param level The logging level to set.
     */
    void setLoggingLevel(LoggingLevel level);

    /**
     * Adds a file to which logs will be written.
     *
     * @param file The file to add to the log.
     */
    void addLogFile(Path file);

    /**
     * Adds a file to which logs will be written by its name (should be resolved using the log directory).
     *
     * @param name The name of the file to add to the log.
     */
    default void addLogFile(String name) {
        Path logDirectory = getLogDirectory();
        if (logDirectory == null)
            throw new IllegalStateException("Log directory must be set before adding files to log to.");

        Path file = logDirectory.resolve(name);
        addLogFile(file);
    }

    /**
     * Gets the list of files to which logs will be written.
     *
     * @return A list of paths to the log files.
     */
    List<Path> getFilesToLogTo();

    /**
     * Gets the date format used for the log files.
     *
     * @return The date format as a DateTimeFormatter.
     */
    DateTimeFormatter getLogDateFormat();

    /**
     * Formats a FileTime object into a human-readable string.
     *
     * @param fileTime The FileTime object to format.
     * @return A formatted string representing the FileTime.
     */
    String formatFileTime(FileTime fileTime);

    /**
     * Closes the logger and releases any resources it holds.
     */
    default void close() {
    }
}