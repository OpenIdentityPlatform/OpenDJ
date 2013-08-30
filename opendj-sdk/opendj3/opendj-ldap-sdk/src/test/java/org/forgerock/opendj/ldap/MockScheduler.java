/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2013 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A mock scheduled executor which allows unit tests to directly invoke
 * scheduled tasks without needing to wait.
 */
final class MockScheduler implements ScheduledExecutorService {

    // Saved scheduled task.
    private Runnable command;
    private long delay;
    private boolean isScheduled = false;
    private TimeUnit unit;

    MockScheduler() {
        // Nothing to do.
    }

    @Override
    public boolean awaitTermination(final long timeout, final TimeUnit unit)
            throws InterruptedException {
        // Unused.
        return false;
    }

    @Override
    public void execute(final Runnable command) {
        // Unused.

    }

    @Override
    public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        // Unused.
        return null;
    }

    @Override
    public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks,
            final long timeout, final TimeUnit unit) throws InterruptedException {
        // Unused.
        return null;
    }

    @Override
    public <T> T invokeAny(final Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        // Unused.
        return null;
    }

    @Override
    public <T> T invokeAny(final Collection<? extends Callable<T>> tasks, final long timeout,
            final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        // Unused.
        return null;
    }

    @Override
    public boolean isShutdown() {
        // Unused.
        return false;
    }

    @Override
    public boolean isTerminated() {
        // Unused.
        return false;
    }

    @Override
    public <V> ScheduledFuture<V> schedule(final Callable<V> callable, final long delay,
            final TimeUnit unit) {
        // Unused.
        return null;
    }

    @Override
    public ScheduledFuture<?> schedule(final Runnable command, final long delay, final TimeUnit unit) {
        // Unused.
        return null;
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(final Runnable command, final long initialDelay,
            final long period, final TimeUnit unit) {
        // Unused.
        return null;
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(final Runnable command,
            final long initialDelay, final long delay, final TimeUnit unit) {
        this.command = command;
        this.delay = delay;
        this.unit = unit;
        this.isScheduled = true;
        return new ScheduledFuture<Object>() {
            @Override
            public boolean cancel(final boolean mayInterruptIfRunning) {
                isScheduled = false;
                return true;
            }

            @Override
            public int compareTo(final Delayed o) {
                // Unused.
                return 0;
            }

            @Override
            public Object get() throws InterruptedException, ExecutionException {
                // Unused.
                return null;
            }

            @Override
            public Object get(final long timeout, final TimeUnit unit) throws InterruptedException,
                    ExecutionException, TimeoutException {
                // Unused.
                return null;
            }

            @Override
            public long getDelay(final TimeUnit unit) {
                // Unused.
                return 0;
            }

            @Override
            public boolean isCancelled() {
                return !isScheduled;
            }

            @Override
            public boolean isDone() {
                // Unused.
                return false;
            }

        };
    }

    @Override
    public void shutdown() {
        // Unused.
    }

    @Override
    public List<Runnable> shutdownNow() {
        // Unused.
        return Collections.emptyList();
    }

    @Override
    public <T> Future<T> submit(final Callable<T> task) {
        // Unused.
        return null;
    }

    @Override
    public Future<?> submit(final Runnable task) {
        // Unused.
        return null;
    }

    @Override
    public <T> Future<T> submit(final Runnable task, final T result) {
        // Unused.
        return null;
    }

    Runnable getCommand() {
        return command;
    }

    long getDelay() {
        return delay;
    }

    TimeUnit getUnit() {
        return unit;
    }

    boolean isScheduled() {
        return isScheduled;
    }
}
