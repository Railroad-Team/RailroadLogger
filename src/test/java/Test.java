import dev.railroadide.logger.Logger;
import dev.railroadide.logger.LoggerManager;
import dev.railroadide.logger.LoggerService;
import dev.railroadide.logger.LoggingLevel;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class Test {
    public static void main(String[] args) throws InterruptedException {
        LoggerService service = LoggerService.builder()
                .logDirectory(Path.of("logs"))
                .deletionFrequency(5, TimeUnit.MINUTES)
                .logFrequency(1, TimeUnit.SECONDS)
                .addLogFile("test.log")
                .addLogFile("beans.log")
                .addLogFile("yes/no.log")
                .compressionEnabled(true)
                .loggingLevel(LoggingLevel.INFO)
                .build();

        Logger logger = LoggerManager.create(Test.class)
                .service(service)
                .build();

        LoggerManager.init();

        for (int i = 0; i < 1_000; i++) {
            LoggingLevel level = LoggingLevel.values()[i % LoggingLevel.values().length];
            logger.log("This is a test log message number {}", level, i);
            Thread.sleep(10); // Sleep for 1/100 second to simulate time between log messages
        }
    }
}
