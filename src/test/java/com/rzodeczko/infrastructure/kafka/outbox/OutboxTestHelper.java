package com.rzodeczko.infrastructure.kafka.outbox;

import org.springframework.test.util.AopTestUtils;

/**
 * Same-package accessor for the {@code protected processOutbox()} method,
 * allowing integration tests to trigger outbox processing deterministically
 * instead of relying on the scheduled poll.
 * <p>
 * Uses {@link AopTestUtils#getTargetObject} to bypass the {@code @SchedulerLock}
 * AOP proxy — ShedLock's {@code lockAtLeastFor} can silently skip execution
 * when tests run close together.
 */
public final class OutboxTestHelper {

    private OutboxTestHelper() {
    }

    public static void triggerOutbox(AbstractOutboxScheduler scheduler) {
        AbstractOutboxScheduler target = AopTestUtils.getTargetObject(scheduler);
        target.processOutbox();
    }
}
