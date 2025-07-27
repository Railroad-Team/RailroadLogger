package dev.railroadide.logger.impl;

import com.google.gson.*;
import dev.railroadide.logger.Logger;
import dev.railroadide.logger.LoggerManager;
import dev.railroadide.logger.LoggingLevel;
import dev.railroadide.logger.util.VariableRateScheduler;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.fusesource.jansi.Ansi.Color.*;
import static org.fusesource.jansi.Ansi.ansi;

// TODO: Add support for logging to a remote server (?)
// TODO: Add support for uploading a log file to a remote server (e.g., for bug reports)
// TODO: Add everything else to the config file
public class DefaultLogger implements Logger {
    private static final String BRACE_REGEX = "(?<!\\\\)\\{}";
    private final Queue<String> loggingMessages = new ConcurrentLinkedQueue<>();
    private final VariableRateScheduler scheduler = new VariableRateScheduler(Executors.newSingleThreadScheduledExecutor(new BasicThreadFactory.Builder().daemon(true).build()));

    @Getter
    private final String name;

    @Getter
    private final List<Path> filesToLogTo = new ArrayList<>();

    @Getter
    private final DateTimeFormatter logDateFormat;

    @Getter
    @Setter
    private boolean isCompressionEnabled = true;

    @Getter
    @Setter
    private long logFrequency;

    @Getter
    @Setter
    private long deletionFrequency;

    @Getter
    @Setter
    private Path logDirectory;

    @Getter
    @Setter
    private LoggingLevel loggingLevel;

    @Getter
    @Setter
    private Path configFile;

    @Getter
    @Setter
    private String loggingLayout;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    DefaultLogger(String name, DateTimeFormatter logDateFormat) {
        this.name = name;
        this.logDateFormat = logDateFormat;
    }

    @Override
    public void init() {
        System.setProperty("jansi.passthrough", "true");
        AnsiConsole.systemInstall();

        beginWriteScheduling();
        try {
            if (Files.notExists(this.configFile)) {
                Files.writeString(this.configFile, GSON.toJson(toJson()), StandardOpenOption.CREATE_NEW);
            }

            JsonObject json = GSON.fromJson(Files.readString(this.configFile), JsonObject.class);
            fromJson(json);
        } catch (IOException exception) {
            throw new RuntimeException("An error has occurred loading the config file", exception);
        }
    }

    @Override
    public void log(String message, LoggingLevel level, Object... objects) {
        Ansi.Color logColor = switch (level) {
            case ERROR -> RED;
            case WARN -> YELLOW;
            case INFO -> GREEN;
            case DEBUG -> BLUE;
            default -> WHITE;
        };

        if (message == null || message.isEmpty())
            return;

        long bracesCount = Pattern.compile(BRACE_REGEX).matcher(message).results().count();

        List<Throwable> throwables = new ArrayList<>();
        for (int i = 0; i < objects.length; i++) {
            Object object = objects[i];
            // We check if the last object is a throwable and skip replacement if so.
            // This is to allow for cases such as: LOGGER.error("Failed to compress log file {}", exception, exception);
            if (object instanceof Throwable throwable && i >= bracesCount) {
                throwables.add(throwable);
                continue;
            }

            message = message.replaceFirst(BRACE_REGEX, Matcher.quoteReplacement(Objects.toString(object)));
        }

        String messaage = arrangeMessage(level, message);

        StringBuilder messageBuilder = new StringBuilder(messaage);

        for (Throwable throwable : throwables) {
            var stringWriter = new StringWriter();
            try (var printWriter = new PrintWriter(stringWriter)) {
                throwable.printStackTrace(printWriter);
            }

            String fullTrace = stringWriter.toString();
            messageBuilder.append("\n").append(fullTrace);
        }

        message = messageBuilder.toString();

        int logLevel = this.loggingLevel.ordinal();
        if (level.ordinal() <= logLevel) {
            System.out.println(ansi().eraseLine().fg(logColor).a(message).reset());
        }

        loggingMessages.offer(message);
    }

    private JsonObject toJson() {
        var jsonObject = new JsonObject();
        var jsonArray = new JsonArray();

        for(Path path : filesToLogTo){
            jsonArray.add(path.toString());
        }

        jsonObject.add("FilesToLogTo", jsonArray);
        jsonObject.addProperty("IsCompressionEnabled", isCompressionEnabled);
        jsonObject.addProperty("LogFrequency", logFrequency);
        jsonObject.addProperty("DeletionFrequency", deletionFrequency);
        jsonObject.addProperty("LogDirectory", logDirectory.toString());
        jsonObject.addProperty("LoggingLevel", loggingLevel.name());
        jsonObject.addProperty("LoggingLayout", loggingLayout);
        return jsonObject;
    }

    private void fromJson(JsonObject json) {
        if (json == null)
            return;

        if (json.has("FilesToLogTo")) {
            JsonElement filesToLogToElement = json.get("FilesToLogTo");
            if (filesToLogToElement.isJsonArray()) {
                JsonArray filesArray = filesToLogToElement.getAsJsonArray();
                for (JsonElement fileElement : filesArray) {
                    if (fileElement.isJsonPrimitive() && fileElement.getAsJsonPrimitive().isString()) {
                        this.filesToLogTo.add(Path.of(fileElement.getAsString()));
                    }
                }
            }
        }

        if(json.has("IsCompressionEnabled")){
            JsonElement isCompressionEnabledElement = json.get("IsCompressionEnabled");
            if (isCompressionEnabledElement.isJsonPrimitive()) {
                JsonPrimitive isCompressionEnabledPrimitive = isCompressionEnabledElement.getAsJsonPrimitive();
                if (isCompressionEnabledPrimitive.isBoolean()) {
                    this.isCompressionEnabled = isCompressionEnabledElement.getAsBoolean();
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
                        this.loggingLevel = LoggingLevel.DEBUG; // Default to DEBUG if invalid
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

    private String arrangeMessage(LoggingLevel level, String message) {
        LocalTime localTime = LocalTime.now();

        Map<String, Object> hashMap = new HashMap<>();
        hashMap.put("{hours}", StringUtils.leftPad(String.valueOf(localTime.getHour()), 2, '0'));
        hashMap.put("{minutes}", StringUtils.leftPad(String.valueOf(localTime.getMinute()), 2, '0'));
        hashMap.put("{seconds}", StringUtils.leftPad(String.valueOf(localTime.getSecond()), 2, '0'));
        hashMap.put("{milliseconds}", StringUtils.leftPad(String.valueOf(localTime.getNano() / 1_000_000), 4, '0'));
        hashMap.put("{nanoseconds}", StringUtils.leftPad(String.valueOf(localTime.getNano()), 7, '0'));

        hashMap.put("{threadName}", Thread.currentThread().getName());
        hashMap.put("{loggingLevelName}", level.name());
        hashMap.put("{loggerName}", this.name);
        hashMap.put("{message}", message);

        String logMessage = loggingLayout;

        for (Map.Entry<String, Object> entry : hashMap.entrySet()) {
            logMessage = logMessage.replace(entry.getKey(), Objects.toString(entry.getValue()));
        }

        return logMessage;
    }

    @Override
    public void close() {
        try {
            AnsiConsole.systemUninstall();
            scheduler.shutdown();

            String logText = String.join("\n", loggingMessages);
            loggingMessages.clear();
            for (Path logFile : this.filesToLogTo) {
                Files.writeString(logFile, logText + "\n", StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            }
        } catch (IOException exception) {
            System.err.println("Failed to close logger: " + exception.getMessage());
        }
    }

    @Override
    public String formatFileTime(FileTime fileTime) {
        ZonedDateTime zonedDateTime = Instant.ofEpochMilli(fileTime.toMillis()).atZone(ZoneId.systemDefault());
        return getLogDateFormat().format(zonedDateTime);
    }

    @Override
    public void addLogFile(Path file) {
        if (file == null)
            throw new IllegalArgumentException("File to log to must not be null and must exist.");

        this.filesToLogTo.add(file);
    }

    @Override
    public void setLogFrequency(long frequency, TimeUnit timeUnit) {
        if (frequency <= 0)
            throw new IllegalArgumentException("Log frequency must be greater than 0.");

        this.logFrequency = timeUnit.toMillis(frequency);
    }

    @Override
    public void setDeletionFrequency(long frequency, TimeUnit timeUnit) {
        if (frequency <= 0)
            throw new IllegalArgumentException("Deletion frequency must be greater than 0.");

        this.deletionFrequency = timeUnit.toMillis(frequency);
    }

    

    private void beginWriteScheduling() {
        scheduler.scheduleAtVariableRate(() -> {
            if (loggingMessages.isEmpty())
                return;
            try {
                List<String> messageCache = new ArrayList<>(this.loggingMessages);
                var logText = String.join("\n", messageCache);
                this.loggingMessages.removeAll(messageCache);
                for (Path logFile : this.filesToLogTo) {
                    Files.writeString(logFile, logText + "\n", StandardOpenOption.APPEND, StandardOpenOption.CREATE);
                }
            } catch (IOException exception) {
                System.err.println("Failed to write log messages: " + exception.getMessage());
                exception.printStackTrace();
            }
        }, 0, () -> logFrequency);
    }

    /**
     * Builder for creating a DefaultLogger instance.
     */
    public static class Builder {
        private final String name;
        private DateTimeFormatter logDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
        private Path logDirectory = Path.of("logs");
        private final List<Path> filesToLogTo = new ArrayList<>();
        private boolean isCompressionEnabled = true;
        private long logFrequency = TimeUnit.SECONDS.toMillis(1); // Default to 1 second
        private long deletionFrequency = TimeUnit.DAYS.toMillis(1); // Default to 1 day
        private LoggingLevel loggingLevel = LoggingLevel.DEBUG;
        private boolean logToLatest = true;
        private Path configFile = Path.of("config.json");
        private String loggingLayout = "{hours}:{minutes}:{seconds} [{threadName}] {loggingLevelName} {loggerName} - {message}";

        /**
         * Creates a new Builder instance with the specified name.
         *
         * @param name The name of the logger.
         */
        public Builder(String name) {
            this.name = name;
        }

        /**
         * Creates a new Builder instance with the specified class name.
         *
         * @param clazz The class for which the logger is being created.
         */
        public Builder(Class<?> clazz) {
            this.name = clazz.getSimpleName();
        }

        /**
         * Sets the directory where log files will be stored.
         *
         * @param logDirectory The directory to store log files.
         * @return This Builder instance for method chaining.
         */
        public Builder logDirectory(Path logDirectory) {
            this.logDirectory = logDirectory;
            return this;
        }

        /**
         * Adds a file to log to.
         *
         * @param file The file to log to.
         * @return This Builder instance for method chaining.
         */
        public Builder addLogFile(Path file) {
            this.filesToLogTo.add(file);
            return this;
        }

        /**
         * Adds a file to log to by name, using the log directory set in this builder.
         *
         * @param name The name of the file to log to.
         * @return This Builder instance for method chaining.
         */
        public Builder addLogFile(String name) {
            if (logDirectory == null)
                throw new IllegalStateException("Log directory must be set before adding files to log to.");

            this.filesToLogTo.add(logDirectory.resolve(name));
            return this;
        }

        /**
         * Enables or disables compression for log files.
         *
         * @param compression true to enable compression, false to disable.
         * @return This Builder instance for method chaining.
         */
        public Builder isCompressionEnabled(boolean compression) {
            this.isCompressionEnabled = compression;
            return this;
        }

        /**
         * Sets the frequency at which logs are written to files.
         *
         * @param duration The duration of the frequency.
         * @param unit     The time unit of the duration.
         * @return This Builder instance for method chaining.
         */
        public Builder logFrequency(long duration, TimeUnit unit) {
            this.logFrequency = unit.toMillis(duration);
            return this;
        }

        /**
         * Sets the frequency at which old logs are deleted.
         *
         * @param duration The duration of the deletion frequency.
         * @param unit     The time unit of the duration.
         * @return This Builder instance for method chaining.
         */
        public Builder deletionFrequency(long duration, TimeUnit unit) {
            this.deletionFrequency = unit.toMillis(duration);
            return this;
        }

        /**
         * Disables logging to the latest.log file.
         *
         * @return This Builder instance for method chaining.
         */
        public Builder dontLogToLatest() {
            this.logToLatest = false;
            return this;
        }

        /**
         * Sets the date format used for log file names.
         *
         * @param logDateFormat The date format for log file names.
         * @return This Builder instance for method chaining.
         */
        public Builder logDateFormat(DateTimeFormatter logDateFormat) {
            this.logDateFormat = logDateFormat;
            return this;
        }

        /**
         * Sets the logging level for the logger.
         *
         * @param loggingLevel The logging level to set.
         * @return This Builder instance for method chaining.
         */
        public Builder loggingLevel(LoggingLevel loggingLevel) {
            this.loggingLevel = loggingLevel;
            return this;
        }

        /**
         * Sets the config file location for the logger
         *
         * @param configFile The path of the config file
         * @return This Builder instance for method chaining.
         */
        public Builder configFile(Path configFile) {
            this.configFile = configFile;
            return this;
        }

        /**
         * Sets the logging layout for the logger
         *
         * @param loggingLayout The logging layout to set
         * @return This Builder instance for method chaining.
         */
        public Builder loggingLayout(String loggingLayout) {
            this.loggingLayout = loggingLayout;
            return this;
        }

        /**
         * Builds the DefaultLogger instance with the specified configuration.
         *
         * @return A new DefaultLogger instance.
         * @throws IllegalStateException if the log directory is not set.
         */
        public DefaultLogger build() {
            if (name == null || name.isBlank())
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

            if(loggingLayout == null){
                throw new IllegalStateException("Logging layout must be set before building the logger.");
            }

            var logger = new DefaultLogger(name, logDateFormat);
            logger.setLogDirectory(logDirectory);
            logger.setCompressionEnabled(isCompressionEnabled);
            logger.setLogFrequency(logFrequency);
            logger.setDeletionFrequency(deletionFrequency);
            logger.setLoggingLevel(loggingLevel);
            logger.setConfigFile(configFile);
            logger.setLoggingLayout(loggingLayout);

            if (logToLatest) {
                Path latestLog = logDirectory.resolve("latest.log");
                logger.addLogFile(latestLog);
            }

            for (Path file : filesToLogTo) {
                if (file == null)
                    continue;

                logger.addLogFile(file);
            }

            LoggerManager.registerLogger(logger);
            return logger;
        }
    }
}