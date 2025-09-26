package dev.railroadide.logger;

import com.google.gson.*;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Central service that stores configuration for loggers, including output locations and runtime parameters.
 */
public class LoggerService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<Path> filesToLogTo = new ArrayList<>();
    @Setter
    @Getter
    private boolean compressionEnabled = true;
    @Getter
    private long logFrequency = TimeUnit.SECONDS.toMillis(1);
    @Getter
    private long deletionFrequency = TimeUnit.DAYS.toMillis(1);
    @Getter
    private Path logDirectory = Path.of("logs");
    @Getter
    private LoggingLevel loggingLevel = LoggingLevel.DEBUG;
    @Getter
    private Path configFile = Path.of("config.json");
    @Getter
    private String loggingLayout = "{hours}:{minutes}:{seconds} [{threadName}] {loggingLevelName} {loggerName} - {message}";
    @Getter
    private DateTimeFormatter logDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");

    /**
     * Creates a new {@link Builder} populated with the default configuration values.
     *
     * @return a builder that can be used to configure a {@link LoggerService}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new {@link Builder} that copies configuration from an existing service instance.
     *
     * @param template the service whose configuration should be used as the starting point
     * @return a builder pre-populated with the supplied template's configuration
     * @throws NullPointerException if {@code template} is {@code null}
     */
    public static Builder builder(LoggerService template) {
        return new Builder(template);
    }

    /**
     * Sets the interval, in milliseconds, at which log files are written to disk.
     *
     * @param logFrequency the frequency in milliseconds
     * @throws IllegalArgumentException if {@code logFrequency} is negative
     */
    public void setLogFrequency(long logFrequency) {
        if (logFrequency < 0)
            throw new IllegalArgumentException("Log frequency must be greater than or equal to 0.");

        this.logFrequency = logFrequency;
    }

    /**
     * Sets the interval for writing log files using the supplied time unit.
     *
     * @param frequency the numeric value of the frequency
     * @param unit      the unit the {@code frequency} value is expressed in
     * @throws NullPointerException     if {@code unit} is {@code null}
     * @throws IllegalArgumentException if the computed frequency is negative
     */
    public void setLogFrequency(long frequency, TimeUnit unit) {
        Objects.requireNonNull(unit, "Time unit must not be null.");
        setLogFrequency(unit.toMillis(frequency));
    }

    /**
     * Sets how often old log files are deleted, in milliseconds.
     *
     * @param deletionFrequency the deletion frequency in milliseconds
     * @throws IllegalArgumentException if {@code deletionFrequency} is negative
     */
    public void setDeletionFrequency(long deletionFrequency) {
        if (deletionFrequency < 0)
            throw new IllegalArgumentException("Deletion frequency must be greater than or equal to 0.");

        this.deletionFrequency = deletionFrequency;
    }

    /**
     * Sets how often old log files are deleted using a specific time unit.
     *
     * @param frequency the numeric value of the frequency
     * @param unit      the unit the {@code frequency} value is expressed in
     * @throws NullPointerException     if {@code unit} is {@code null}
     * @throws IllegalArgumentException if the computed frequency is negative
     */
    public void setDeletionFrequency(long frequency, TimeUnit unit) {
        Objects.requireNonNull(unit, "Time unit must not be null.");
        setDeletionFrequency(unit.toMillis(frequency));
    }

    /**
     * Configures the directory where log files should be written.
     *
     * @param logDirectory the target directory for log files
     * @throws NullPointerException if {@code logDirectory} is {@code null}
     */
    public void setLogDirectory(Path logDirectory) {
        this.logDirectory = Objects.requireNonNull(logDirectory, "Log directory must not be null.");
    }

    /**
     * Sets the minimum {@link LoggingLevel} that should be recorded.
     *
     * @param loggingLevel the logging level threshold
     * @throws NullPointerException if {@code loggingLevel} is {@code null}
     */
    public void setLoggingLevel(LoggingLevel loggingLevel) {
        this.loggingLevel = Objects.requireNonNull(loggingLevel, "Logging level must not be null.");
    }

    /**
     * Sets the configuration file that should be used to persist and load settings.
     *
     * @param configFile the configuration file path
     * @throws NullPointerException if {@code configFile} is {@code null}
     */
    public void setConfigFile(Path configFile) {
        this.configFile = Objects.requireNonNull(configFile, "Config file must not be null.");
    }

    /**
     * Sets the pattern used when formatting log messages.
     *
     * @param loggingLayout the layout pattern string
     * @throws IllegalArgumentException if {@code loggingLayout} is {@code null} or blank
     */
    public void setLoggingLayout(String loggingLayout) {
        if (loggingLayout == null || loggingLayout.isBlank())
            throw new IllegalArgumentException("Logging layout must not be null or blank.");

        this.loggingLayout = loggingLayout;
    }

    /**
     * Defines the {@link DateTimeFormatter} used when naming log files.
     *
     * @param logDateFormat the formatter to use for log file timestamps
     * @throws NullPointerException if {@code logDateFormat} is {@code null}
     */
    public void setLogDateFormat(DateTimeFormatter logDateFormat) {
        this.logDateFormat = Objects.requireNonNull(logDateFormat, "Log date format must not be null.");
    }

    /**
     * Returns an immutable view of the files the service writes logs to.
     *
     * @return an unmodifiable list of log file paths
     */
    public List<Path> getFilesToLogTo() {
        return Collections.unmodifiableList(filesToLogTo);
    }

    /**
     * Adds a log file target if it has not already been configured.
     *
     * @param file the file to add to the list of log destinations
     * @throws IllegalArgumentException if {@code file} is {@code null}
     */
    public void addLogFile(Path file) {
        if (file == null)
            throw new IllegalArgumentException("File to log to must not be null.");

        if (!filesToLogTo.contains(file)) {
            filesToLogTo.add(file);
        }
    }

    /**
     * Adds a log file target within the configured log directory.
     *
     * @param name the file name to resolve against the log directory
     * @throws IllegalStateException if the log directory has not been configured
     */
    public void addLogFile(String name) {
        Path directory = getLogDirectory();
        if (directory == null)
            throw new IllegalStateException("Log directory must be set before adding files to log to.");

        addLogFile(directory.resolve(name));
    }

    /**
     * Formats a {@link FileTime} using the configured timestamp formatter.
     *
     * @param fileTime the file time to format
     * @return a string representation of the supplied {@link FileTime}
     * @throws NullPointerException if {@code fileTime} is {@code null}
     */
    public String formatFileTime(FileTime fileTime) {
        Objects.requireNonNull(fileTime, "File time must not be null.");
        ZonedDateTime zonedDateTime = Instant.ofEpochMilli(fileTime.toMillis()).atZone(ZoneId.systemDefault());
        return logDateFormat.format(zonedDateTime);
    }

    /**
     * Ensures the service has a valid configuration before the logger is constructed.
     *
     * @param loggerName the name that will be used when instantiating the logger
     * @throws IllegalArgumentException if required configuration values are missing or invalid
     * @throws IllegalStateException    if lazily configured paths have not been set
     */
    public void validate(String loggerName) {
        if (loggerName == null || loggerName.isBlank())
            throw new IllegalArgumentException("Logger name must not be null or empty.");
        if (logDateFormat == null)
            throw new IllegalArgumentException("Log date format must not be null.");
        if (logFrequency < 0)
            throw new IllegalArgumentException("Log frequency must be greater than or equal to 0.");
        if (deletionFrequency < 0)
            throw new IllegalArgumentException("Deletion frequency must be greater than or equal to 0.");
        if (logDirectory == null)
            throw new IllegalStateException("Log directory must be set before building the logger.");
        if (loggingLevel == null)
            throw new IllegalArgumentException("Logging level must not be null.");
        if (configFile == null)
            throw new IllegalStateException("Config file location must be set before building the logger.");
        if (loggingLayout == null)
            throw new IllegalStateException("Logging layout must be set before building the logger.");
    }

    /**
     * Loads configuration from the configured config file, creating it with defaults if it does not exist.
     *
     * @throws IllegalStateException if the config file location has not been set
     * @throws RuntimeException      if an I/O error occurs while loading or creating the file
     */
    public synchronized void loadConfiguration() {
        if (configFile == null)
            throw new IllegalStateException("Config file must be set before loading configuration.");

        try {
            if (Files.notExists(configFile)) {
                Path parent = configFile.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }

                Files.writeString(configFile, GSON.toJson(toJson()), StandardOpenOption.CREATE_NEW);
            }

            String content = Files.readString(configFile);
            JsonObject json = GSON.fromJson(content, JsonObject.class);
            applyFromJson(json);
        } catch (IOException exception) {
            throw new RuntimeException("An error has occurred loading the config file", exception);
        }
    }

    private JsonObject toJson() {
        var jsonObject = new JsonObject();
        var filesArray = new JsonArray();

        for (Path path : filesToLogTo) {
            filesArray.add(path.toString());
        }

        jsonObject.add("FilesToLogTo", filesArray);
        jsonObject.addProperty("IsCompressionEnabled", compressionEnabled);
        jsonObject.addProperty("LogFrequency", logFrequency);
        jsonObject.addProperty("DeletionFrequency", deletionFrequency);
        jsonObject.addProperty("LogDirectory", logDirectory.toString());
        jsonObject.addProperty("LoggingLevel", loggingLevel.name());
        jsonObject.addProperty("LoggingLayout", loggingLayout);
        return jsonObject;
    }

    private void applyFromJson(JsonObject json) {
        if (json == null)
            return;

        if (json.has("FilesToLogTo")) {
            JsonElement filesToLogToElement = json.get("FilesToLogTo");
            if (filesToLogToElement.isJsonArray()) {
                JsonArray filesArray = filesToLogToElement.getAsJsonArray();
                for (JsonElement fileElement : filesArray) {
                    if (fileElement.isJsonPrimitive() && fileElement.getAsJsonPrimitive().isString()) {
                        Path path = Path.of(fileElement.getAsString());
                        if (!filesToLogTo.contains(path)) {
                            filesToLogTo.add(path);
                        }
                    }
                }
            }
        }

        if (json.has("IsCompressionEnabled")) {
            JsonElement isCompressionEnabledElement = json.get("IsCompressionEnabled");
            if (isCompressionEnabledElement.isJsonPrimitive()) {
                JsonPrimitive isCompressionEnabledPrimitive = isCompressionEnabledElement.getAsJsonPrimitive();
                if (isCompressionEnabledPrimitive.isBoolean()) {
                    this.compressionEnabled = isCompressionEnabledElement.getAsBoolean();
                }
            }
        }

        if (json.has("LogFrequency")) {
            JsonElement logFrequencyElement = json.get("LogFrequency");
            if (logFrequencyElement.isJsonPrimitive()) {
                JsonPrimitive logFrequencyPrimitive = logFrequencyElement.getAsJsonPrimitive();
                if (logFrequencyPrimitive.isNumber()) {
                    this.logFrequency = logFrequencyElement.getAsLong();
                }
            }
        }

        if (json.has("DeletionFrequency")) {
            JsonElement deletionFrequencyElement = json.get("DeletionFrequency");
            if (deletionFrequencyElement.isJsonPrimitive()) {
                JsonPrimitive deletionFrequencyPrimitive = deletionFrequencyElement.getAsJsonPrimitive();
                if (deletionFrequencyPrimitive.isNumber()) {
                    this.deletionFrequency = deletionFrequencyElement.getAsLong();
                }
            }
        }

        if (json.has("LogDirectory")) {
            JsonElement logDirectoryElement = json.get("LogDirectory");
            if (logDirectoryElement.isJsonPrimitive()) {
                JsonPrimitive logDirectoryPrimitive = logDirectoryElement.getAsJsonPrimitive();
                if (logDirectoryPrimitive.isString()) {
                    this.logDirectory = Path.of(logDirectoryElement.getAsString());
                }
            }
        }

        if (json.has("LoggingLevel")) {
            JsonElement loggingLevelElement = json.get("LoggingLevel");
            if (loggingLevelElement.isJsonPrimitive()) {
                JsonPrimitive loggingLevelPrimitive = loggingLevelElement.getAsJsonPrimitive();
                if (loggingLevelPrimitive.isString()) {
                    try {
                        this.loggingLevel = LoggingLevel.valueOf(loggingLevelElement.getAsString().toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        System.err.println("Invalid logging level in config file: " + loggingLevelElement.getAsString());
                        this.loggingLevel = LoggingLevel.DEBUG;
                    }
                }
            }
        }

        if (json.has("LoggingLayout")) {
            JsonElement loggingLayoutElement = json.get("LoggingLayout");
            if (loggingLayoutElement.isJsonPrimitive()) {
                JsonPrimitive loggingLayoutPrimitive = loggingLayoutElement.getAsJsonPrimitive();
                if (loggingLayoutPrimitive.isString()) {
                    this.loggingLayout = loggingLayoutElement.getAsString();
                }
            }
        }
    }

    /**
     * Fluent builder that allows callers to customise a {@link LoggerService} instance before creation.
     */
    public static class Builder {
        private final LoggerService service;

        private Builder() {
            this.service = new LoggerService();
        }

        private Builder(LoggerService template) {
            this();
            LoggerService source = Objects.requireNonNull(template, "Template service must not be null.");
            this.service.filesToLogTo.addAll(source.filesToLogTo);
            this.service.compressionEnabled = source.compressionEnabled;
            this.service.logFrequency = source.logFrequency;
            this.service.deletionFrequency = source.deletionFrequency;
            this.service.logDirectory = source.logDirectory;
            this.service.loggingLevel = source.loggingLevel;
            this.service.configFile = source.configFile;
            this.service.loggingLayout = source.loggingLayout;
            this.service.logDateFormat = source.logDateFormat;
        }

        /**
         * Enables or disables compression of archived log files.
         *
         * @param enabled {@code true} to enable compression; {@code false} otherwise
         * @return this builder for chaining
         */
        public Builder compressionEnabled(boolean enabled) {
            this.service.setCompressionEnabled(enabled);
            return this;
        }

        /**
         * Sets how often log data is flushed, expressed in milliseconds.
         *
         * @param frequencyMillis the flush interval in milliseconds
         * @return this builder for chaining
         * @throws IllegalArgumentException if {@code frequencyMillis} is negative
         */
        public Builder logFrequency(long frequencyMillis) {
            this.service.setLogFrequency(frequencyMillis);
            return this;
        }

        /**
         * Sets how often log data is flushed using the supplied {@link TimeUnit}.
         *
         * @param frequency the numeric frequency value
         * @param unit      the unit the {@code frequency} value is expressed in
         * @return this builder for chaining
         * @throws NullPointerException     if {@code unit} is {@code null}
         * @throws IllegalArgumentException if the computed frequency is negative
         */
        public Builder logFrequency(long frequency, TimeUnit unit) {
            this.service.setLogFrequency(frequency, unit);
            return this;
        }

        /**
         * Sets how often old log files should be purged, in milliseconds.
         *
         * @param frequencyMillis the purge interval in milliseconds
         * @return this builder for chaining
         * @throws IllegalArgumentException if {@code frequencyMillis} is negative
         */
        public Builder deletionFrequency(long frequencyMillis) {
            this.service.setDeletionFrequency(frequencyMillis);
            return this;
        }

        /**
         * Sets how often old log files should be purged using the supplied {@link TimeUnit}.
         *
         * @param frequency the numeric frequency value
         * @param unit      the unit the {@code frequency} value is expressed in
         * @return this builder for chaining
         * @throws NullPointerException     if {@code unit} is {@code null}
         * @throws IllegalArgumentException if the computed frequency is negative
         */
        public Builder deletionFrequency(long frequency, TimeUnit unit) {
            this.service.setDeletionFrequency(frequency, unit);
            return this;
        }

        /**
         * Specifies the directory that should contain all log files.
         *
         * @param directory the directory that log files should be written to
         * @return this builder for chaining
         * @throws NullPointerException if {@code directory} is {@code null}
         */
        public Builder logDirectory(Path directory) {
            this.service.setLogDirectory(directory);
            return this;
        }

        /**
         * Sets the minimum {@link LoggingLevel} that will be emitted.
         *
         * @param level the threshold level
         * @return this builder for chaining
         * @throws NullPointerException if {@code level} is {@code null}
         */
        public Builder loggingLevel(LoggingLevel level) {
            this.service.setLoggingLevel(level);
            return this;
        }

        /**
         * Sets the configuration file that backs this service.
         *
         * @param file the configuration file path
         * @return this builder for chaining
         * @throws NullPointerException if {@code file} is {@code null}
         */
        public Builder configFile(Path file) {
            this.service.setConfigFile(file);
            return this;
        }

        /**
         * Sets the layout pattern used for log messages.
         *
         * @param layout the pattern string
         * @return this builder for chaining
         * @throws IllegalArgumentException if {@code layout} is {@code null} or blank
         */
        public Builder loggingLayout(String layout) {
            this.service.setLoggingLayout(layout);
            return this;
        }

        /**
         * Sets the formatter used when naming or displaying log files.
         *
         * @param formatter the {@link DateTimeFormatter} to use
         * @return this builder for chaining
         * @throws NullPointerException if {@code formatter} is {@code null}
         */
        public Builder logDateFormat(DateTimeFormatter formatter) {
            this.service.setLogDateFormat(formatter);
            return this;
        }

        /**
         * Adds an explicit log file target.
         *
         * @param file the log file path to add
         * @return this builder for chaining
         * @throws IllegalArgumentException if {@code file} is {@code null}
         */
        public Builder addLogFile(Path file) {
            this.service.addLogFile(file);
            return this;
        }

        /**
         * Adds a log file path relative to the configured log directory.
         *
         * @param fileName the file name to resolve within the log directory
         * @return this builder for chaining
         * @throws IllegalStateException if the log directory has not been configured
         */
        public Builder addLogFile(String fileName) {
            this.service.addLogFile(fileName);
            return this;
        }

        /**
         * Removes all log file targets that have been configured on the builder.
         *
         * @return this builder for chaining
         */
        public Builder clearLogFiles() {
            this.service.filesToLogTo.clear();
            return this;
        }

        /**
         * Creates a new {@link LoggerService} instance populated with the builder's configuration.
         *
         * @return a fully configured {@link LoggerService}
         */
        public LoggerService build() {
            LoggerService result = new LoggerService();
            result.filesToLogTo.addAll(this.service.filesToLogTo);
            result.compressionEnabled = this.service.compressionEnabled;
            result.logFrequency = this.service.logFrequency;
            result.deletionFrequency = this.service.deletionFrequency;
            result.logDirectory = this.service.logDirectory;
            result.loggingLevel = this.service.loggingLevel;
            result.configFile = this.service.configFile;
            result.loggingLayout = this.service.loggingLayout;
            result.logDateFormat = this.service.logDateFormat;
            return result;
        }
    }
}
