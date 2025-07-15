import io.github.railroad.logger.Logger;
import io.github.railroad.logger.LoggerManager;
import io.github.railroad.logger.LoggingLevel;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class Test {
    public static void main(String[] args) throws InterruptedException {
        Logger logger = LoggerManager.create(Test.class)
                .logDirectory(Path.of("logs"))
                .deletionFrequency(5, TimeUnit.MINUTES)
                .logFrequency(1, TimeUnit.SECONDS)
                .dontLogToLatest()
                .addFileToLogTo("test.log")
                .addFileToLogTo("beans.log")
                .addFileToLogTo("yes/no.log")
                .isCompressionEnabled(true)
                .build();

        LoggerManager.init();

        for (int i = 0; i < 1_000; i++) {
            LoggingLevel level = LoggingLevel.values()[i % LoggingLevel.values().length];
            logger.log("This is a test log message number {}", level, i);
            Thread.sleep(10); // Sleep for 1/100 second to simulate time between log messages
        }
    }
}
