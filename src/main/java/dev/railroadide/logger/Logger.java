package dev.railroadide.logger;

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
     * Provides the service used to configure this logger.
     *
     * @return The service instance holding the logger configuration.
     */
    LoggerService getService();

    /**
     * Closes the logger and releases any resources it holds.
     */
    default void close() {
    }
}
