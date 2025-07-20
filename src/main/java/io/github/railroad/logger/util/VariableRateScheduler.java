package io.github.railroad.logger.util;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A scheduler that allows scheduling tasks at a variable rate, where the delay between executions can change dynamically.
 * This is useful for tasks that need to adapt their execution frequency based on runtime conditions.
 */
public class VariableRateScheduler {
    private final ScheduledExecutorService exec;

    /**
     * Constructs a VariableRateScheduler with the given ScheduledExecutorService.
     *
     * @param exec The ScheduledExecutorService to use for scheduling tasks.
     */
    public VariableRateScheduler(ScheduledExecutorService exec) {
        this.exec = exec;
    }

    /**
     * Schedules a task to run at a variable rate, where the delay between executions is determined by the provided
     * delay supplier.
     *
     * @param task           The task to run.
     * @param initialDelay   The initial delay before the first execution.
     * @param delaySupplier  A supplier that provides the delay for subsequent executions.
     */
    public void scheduleAtVariableRate(Runnable task, long initialDelay, Supplier<Long> delaySupplier) {
        // Wrap the user’s task so that when it completes, we schedule the next one
        Runnable wrapper = new RunTask(task, initialDelay, delaySupplier);

        // Schedule the first execution
        exec.schedule(wrapper, initialDelay, TimeUnit.MILLISECONDS);
    }

    /**
     * Shuts down the scheduler, stopping any further task execution.
     */
    public void shutdown() {
        exec.shutdown();
    }

    private class RunTask implements Runnable {
        private final Runnable task;
        private final long initialDelay;
        private final Supplier<Long> delaySupplier;

        public RunTask(Runnable task, long initialDelay, Supplier<Long> delaySupplier) {
            this.task = task;
            this.initialDelay = initialDelay;
            this.delaySupplier = delaySupplier;
        }

        @Override
        public void run() {
            try {
                task.run();
            } finally {
                // Compute the next delay and re-submit ourselves
                long nextDelay = delaySupplier.get();
                if (nextDelay > 0) {
                    // Reschedule the task with the next delay
                    new VariableRateScheduler(exec).scheduleAtVariableRate(task, initialDelay, delaySupplier);
                }
            }
        }
    }
}
