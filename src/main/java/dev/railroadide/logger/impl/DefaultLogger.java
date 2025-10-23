package dev.railroadide.logger.impl;

import dev.railroadide.logger.Logger;
import dev.railroadide.logger.LoggerManager;
import dev.railroadide.logger.LoggerService;
import dev.railroadide.logger.LoggingLevel;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new BasicThreadFactory.Builder().daemon(true).build());

    @Getter
    private final String name;
    private final LoggerService service;

    DefaultLogger(String name, LoggerService service) {
        this.name = Objects.requireNonNull(name, "Logger name must not be null.");
        this.service = Objects.requireNonNull(service, "Logger service must not be null.");
    }

    @Override
    public void init() {
        System.setProperty("jansi.passthrough", "true");
        AnsiConsole.systemInstall();

        beginWriteScheduling();
        service.loadConfiguration();
    }

    @Override
    public LoggerService getService() {
        return service;
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

        String arrangedMessage = arrangeMessage(level, message);

        StringBuilder messageBuilder = new StringBuilder(arrangedMessage);

        for (Throwable throwable : throwables) {
            var stringWriter = new StringWriter();
            try (var printWriter = new PrintWriter(stringWriter)) {
                throwable.printStackTrace(printWriter);
            }

            String fullTrace = stringWriter.toString();
            messageBuilder.append("\n").append(fullTrace);
        }

        String resolvedMessage = messageBuilder.toString();

        int configuredLogLevel = service.getLoggingLevel().ordinal();
        if (level.ordinal() <= configuredLogLevel) {
            System.out.println(ansi().eraseLine().fg(logColor).a(resolvedMessage).reset());
        }

        loggingMessages.offer(resolvedMessage);
    }

    private String arrangeMessage(LoggingLevel level, String message) {
        LocalTime localTime = LocalTime.now();

        Map<String, Object> hashMap = Map.of(
                "{hours}", StringUtils.leftPad(String.valueOf(localTime.getHour()), 2, '0'),
                "{minutes}", StringUtils.leftPad(String.valueOf(localTime.getMinute()), 2, '0'),
                "{seconds}", StringUtils.leftPad(String.valueOf(localTime.getSecond()), 2, '0'),
                "{milliseconds}", StringUtils.leftPad(String.valueOf(localTime.getNano() / 1_000_000), 4, '0'),
                "{nanoseconds}", StringUtils.leftPad(String.valueOf(localTime.getNano()), 7, '0'),
                "{threadName}", Thread.currentThread().getName(),
                "{loggingLevelName}", level.name(),
                "{loggerName}", this.name,
                "{message}", message
        );

        String logMessage = service.getLoggingLayout();

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
            for (Path logFile : service.getFilesToLogTo()) {
                Files.writeString(logFile, logText + "\n", StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            }
        } catch (Exception exception) {
            System.err.println("Failed to close logger: " + exception.getMessage());
        }
    }

    private void beginWriteScheduling() {
        scheduler.scheduleAtFixedRate(() -> {
            if (loggingMessages.isEmpty())
                return;

            try {
                List<String> messageCache = new ArrayList<>(this.loggingMessages);
                if(messageCache.isEmpty())
                    return;

                var logText = String.join("\n", messageCache);
                this.loggingMessages.removeAll(messageCache);
                if(logText.isEmpty())
                    return;

                for (Path logFile : service.getFilesToLogTo()) {
                    Files.writeString(logFile, logText + "\n", StandardOpenOption.APPEND, StandardOpenOption.CREATE);
                }
            } catch (Exception exception) {
                System.err.println("Failed to write log messages: " + exception.getMessage());
                exception.printStackTrace();
            }
        }, 0, service.getLogFrequency(), TimeUnit.MILLISECONDS);
    }

    /**
     * Builder for creating a DefaultLogger instance.
     */
    public static class Builder {
        private final String name;
        private LoggerService service;

        /**
         * Creates a new Builder instance with the specified name.
         *
         * @param name The name of the logger.
         */
        public Builder(String name) {
            this.name = name;
            this.service = new LoggerService();
        }

        /**
         * Creates a new Builder instance with the specified class name.
         *
         * @param clazz The class for which the logger is being created.
         */
        public Builder(Class<?> clazz) {
            this(Objects.requireNonNull(clazz, "Class cannot be null").getSimpleName());
        }

        /**
         * Supplies a custom LoggerService implementation.
         *
         * @param service The service to use for configuration.
         * @return This Builder instance for method chaining.
         */
        public Builder service(LoggerService service) {
            this.service = Objects.requireNonNull(service, "Logger service must not be null.");
            return this;
        }

        /**
         * Returns the service currently associated with this builder so callers can adjust it directly.
         */
        public LoggerService service() {
            return service;
        }

        /**
         * Builds the DefaultLogger instance with the specified configuration.
         *
         * @return A new DefaultLogger instance.
         */
        public DefaultLogger build() {
            LoggerService configuredService = Objects.requireNonNull(service, "Logger service must not be null.");
            configuredService.validate(name);

            DefaultLogger logger = new DefaultLogger(name, configuredService);
            LoggerManager.track(logger);
            return logger;
        }
    }
}
