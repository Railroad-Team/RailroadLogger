package io.github.railroad.logger;

import java.util.concurrent.*;
import java.util.function.Supplier;

public class VariableRateScheduler {
    private final ScheduledExecutorService exec;

    public VariableRateScheduler(ScheduledExecutorService exec) {
        this.exec = exec;
    }

    /**
     * Kick off a task that will re-schedule itself at a variable rate.
     *
     * @param task          the unit of work to run
     * @param initialDelay  delay before the very first run
     * @param delaySupplier supplies the delay (in milliseconds) until the next run
     */
    public void scheduleAtVariableRate(
            Runnable task,
            long initialDelay,
            Supplier<Long> delaySupplier
    ) {
        // wrap the user’s task so that when it completes, we schedule the next one
        Runnable wrapper = new Runnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } finally {
                    // compute the next delay and re-submit ourselves
                    long nextDelay = delaySupplier.get();
                    exec.schedule(this, nextDelay, TimeUnit.MILLISECONDS);
                }
            }
        };

        // schedule the first execution
        exec.schedule(wrapper, initialDelay, TimeUnit.MILLISECONDS);
    }

    public void shutdown(){
        exec.shutdown();
    }
}
