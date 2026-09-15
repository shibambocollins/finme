package com.finme.backend.service;

/**
 * Runs the task on the calling thread instead of handing it to the async executor.
 * <p>
 * Ingestion is deliberately asynchronous in production, which makes the interesting part -
 * did extraction persist the right transactions, did a failure get recorded on the row -
 * happen after the method under test has already returned. Mocking BackgroundRunner would
 * skip that work entirely and leave those assertions testing nothing; a real executor would
 * make them race. Running inline keeps the assertions meaningful and deterministic.
 */
public class InlineBackgroundRunner extends BackgroundRunner {

    @Override
    public void run(String description, Runnable task) {
        task.run();
    }
}
