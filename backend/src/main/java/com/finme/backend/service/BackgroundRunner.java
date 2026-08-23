package com.finme.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs a task on the statement executor.
 * <p>
 * This exists as its own bean for a specific reason: {@code @Async} is applied by a proxy, and a
 * proxy only intercepts calls that arrive from outside the object. Had StatementIngestionService
 * annotated one of its own methods and called it from {@code ingest()}, that self-invocation
 * would have bypassed the proxy entirely and run the extraction inline - the upload would still
 * block for the full ~100 seconds, with nothing in the code obviously wrong. Handing the task to
 * a separate bean makes the call an external one, so the annotation actually applies.
 * <p>
 * Taking a Runnable rather than depending on the ingestion service also keeps the dependency
 * pointing one way, avoiding the circular reference a dedicated processor bean would create.
 */
@Component
public class BackgroundRunner {

    private static final Logger log = LoggerFactory.getLogger(BackgroundRunner.class);

    @Async("statementExecutor")
    public void run(String description, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException ex) {
            // No caller is waiting on this thread, so an escaping exception would otherwise be
            // swallowed by the executor. The task itself is responsible for recording failure
            // where the user can see it; this is the operator's copy.
            log.error("Background task failed: {}", description, ex);
        }
    }
}
