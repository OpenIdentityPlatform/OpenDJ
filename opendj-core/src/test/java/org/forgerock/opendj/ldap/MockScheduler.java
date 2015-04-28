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
 *      Copyright 2013-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import static java.util.concurrent.Executors.callable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
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

    private final class ScheduledCallableFuture<T> implements ScheduledFuture<T>, Callable<T> {
        private final Callable<T> callable;
        private final CountDownLatch isDone = new CountDownLatch(1);
        private final boolean removeAfterCall;
        private T result;

        private ScheduledCallableFuture(final Callable<T> callable, final boolean removeAfterCall) {
            this.callable = callable;
            this.removeAfterCall = removeAfterCall;
        }

        @Override
        public T call() throws Exception {
            result = callable.call();
            isDone.countDown();
            if (removeAfterCall) {
                tasks.remove(this);
            }
            return result;
        }

        @Override
        public boolean cancel(final boolean mayInterruptIfRunning) {
            return tasks.remove(this);
        }

        @Override
        public int compareTo(final Delayed o) {
            // Unused.
            return 0;
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            isDone.await();
            return result;
        }

        @Override
        public T get(final long timeout, final TimeUnit unit) throws InterruptedException,
                ExecutionException, TimeoutException {
            if (isDone.await(timeout, unit)) {
                return result;
            } else {
                throw new TimeoutException();
            }
        }

        @Override
        public long getDelay(final TimeUnit unit) {
            // Unused.
            return 0;
        }

        @Override
        public boolean isCancelled() {
            return tasks.contains(this);
        }

        @Override
        public boolean isDone() {
            return isDone.getCount() == 0;
        }

    }

    /** Saved scheduled tasks. */
    private final List<Callable<?>> tasks = new CopyOnWriteArrayList<>();

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
        return onceOnly(callable);
    }

    @Override
    public ScheduledFuture<?> schedule(final Runnable command, final long delay, final TimeUnit unit) {
        return onceOnly(callable(command));
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(final Runnable command, final long initialDelay,
            final long period, final TimeUnit unit) {
        return repeated(callable(command));
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(final Runnable command,
            final long initialDelay, final long delay, final TimeUnit unit) {
        return repeated(callable(command));
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
        return onceOnly(task);
    }

    @Override
    public Future<?> submit(final Runnable task) {
        return onceOnly(callable(task));
    }

    @Override
    public <T> Future<T> submit(final Runnable task, final T result) {
        return onceOnly(callable(task, result));
    }

    List<Callable<?>> getAllTasks() {
        return tasks;
    }

    Callable<?> getFirstTask() {
        return tasks.get(0);
    }

    boolean isScheduled() {
        return !tasks.isEmpty();
    }

    void runAllTasks() {
        for (final Callable<?> task : tasks) {
            runTask0(task);
        }
    }

    void runFirstTask() {
        runTask(0);
    }

    void runTask(final int i) {
        runTask0(tasks.get(i));
    }

    private <T> ScheduledCallableFuture<T> onceOnly(final Callable<T> callable) {
        final ScheduledCallableFuture<T> wrapped = new ScheduledCallableFuture<>(callable, true);
        tasks.add(wrapped);
        return wrapped;
    }

    private <T> ScheduledCallableFuture<T> repeated(final Callable<T> callable) {
        final ScheduledCallableFuture<T> wrapped = new ScheduledCallableFuture<>(callable, false);
        tasks.add(wrapped);
        return wrapped;
    }

    private void runTask0(final Callable<?> task) {
        try {
            task.call();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }
}
