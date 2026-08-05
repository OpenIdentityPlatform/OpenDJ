/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems LLC.
 */

package org.openidentityplatform.opendj.embedded;

import org.apache.commons.io.FileUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests the deletion of the temporary directory of an embedded instance, which has to survive
 * the files that the server threads are still holding when {@code stop()} returns.
 * <p>
 * The deletion itself is injected, so that the retries can be driven without depending on a
 * genuinely undeletable file, which no platform offers in a portable way.
 */
public class EmbeddedOpenDJCleanupTest {

    private final List<File> temporaryDirectories = new ArrayList<>();

    @AfterMethod
    public void deleteTemporaryDirectories() {
        for (File directory : temporaryDirectories) {
            FileUtils.deleteQuietly(directory);
        }
        temporaryDirectories.clear();
    }

    @Test
    public void deletesTheWholeTree() throws Exception {
        final File directory = createTree(3);

        EmbeddedOpenDJ.deleteInstanceDirectory(directory, 0);

        assertFalse(directory.exists(), directory + " has not been deleted");
    }

    @Test
    public void ignoresADirectoryThatDoesNotExist() throws Exception {
        final File directory = createTree(0);
        assertTrue(FileUtils.deleteQuietly(directory));

        //neither of these must throw
        EmbeddedOpenDJ.deleteInstanceDirectory(directory, 10_000);
        EmbeddedOpenDJ.deleteInstanceDirectory(null, 10_000);
    }

    @Test
    public void stopsRetryingAsSoonAsTheDeletionSucceeds() throws Exception {
        final File directory = createTree(1);
        final AtomicInteger attempts = new AtomicInteger();

        final long elapsedMs = timed(() ->
                EmbeddedOpenDJ.deleteInstanceDirectory(directory, 10_000, file -> attempts.incrementAndGet() >= 3));

        assertEquals(attempts.get(), 3, "the deletion should have been retried until it succeeded");
        assertTrue(elapsedMs < 10_000, "it waited " + elapsedMs + " ms instead of returning on success");
    }

    @Test
    public void triesOnlyOnceWithoutATimeout() throws Exception {
        final File directory = createTree(1);
        final AtomicInteger attempts = new AtomicInteger();

        EmbeddedOpenDJ.deleteInstanceDirectory(directory, 0, file -> {
            attempts.incrementAndGet();
            return false;
        });

        assertEquals(attempts.get(), 1, "a zero timeout must not retry");
        assertTrue(directory.exists(), "the injected deletion did not delete anything");
    }

    @Test
    public void givesUpWhenTheTimeoutExpires() throws Exception {
        final File directory = createTree(1);
        final AtomicInteger attempts = new AtomicInteger();
        final long timeoutMs = 300;

        final long elapsedMs = timed(() -> EmbeddedOpenDJ.deleteInstanceDirectory(directory, timeoutMs, file -> {
            attempts.incrementAndGet();
            return false;
        }));

        assertTrue(attempts.get() > 1, "the deletion should have been retried, attempts: " + attempts.get());
        assertTrue(elapsedMs >= timeoutMs, "it gave up after " + elapsedMs + " ms, before the timeout");
        assertTrue(elapsedMs < TimeUnit.SECONDS.toMillis(30), "it did not give up, " + elapsedMs + " ms elapsed");
        //the leftovers are registered for deletion on JVM exit, so this must return normally
        assertTrue(directory.exists());
    }

    @Test
    public void givesUpWhenTheThreadIsInterrupted() throws Exception {
        final File directory = createTree(1);
        final AtomicInteger attempts = new AtomicInteger();

        final long elapsedMs;
        Thread.currentThread().interrupt();
        try {
            elapsedMs = timed(() -> EmbeddedOpenDJ.deleteInstanceDirectory(directory, 60_000, file -> {
                attempts.incrementAndGet();
                return false;
            }));
            assertTrue(Thread.currentThread().isInterrupted(), "the interrupted status has been swallowed");
        } finally {
            Thread.interrupted();
        }

        assertEquals(attempts.get(), 1, "an interrupted deletion must not be retried");
        assertTrue(elapsedMs < TimeUnit.SECONDS.toMillis(30), "it kept retrying for " + elapsedMs + " ms");
    }

    @Test
    public void reportsTheRemainingPathsAndHowManyAreLeftOut() throws Exception {
        final File directory = createTree(25);

        final String remaining = EmbeddedOpenDJ.remainingPaths(directory);

        final String[] lines = remaining.split("\n");
        //one empty leading token before the first line separator, 20 paths and the summary
        assertEquals(lines.length, 22, "unexpected report: " + remaining);
        for (int i = 1; i < lines.length - 1; i++) {
            assertTrue(lines[i].startsWith("  " + directory.getPath()), "unexpected line: " + lines[i]);
        }
        assertTrue(remaining.endsWith("... and 5 more"), remaining);
    }

    @Test
    public void reportsAllTheRemainingPathsWhenThereAreFewOfThem() throws Exception {
        final File directory = createTree(2);

        final String remaining = EmbeddedOpenDJ.remainingPaths(directory);

        assertEquals(remaining.split("\n").length, 3, "unexpected report: " + remaining);
        assertFalse(remaining.contains("more"), remaining);
    }

    /** Creates a temporary directory holding the given number of files, one of them nested. */
    private File createTree(int fileCount) throws IOException {
        final File directory = Files.createTempDirectory("opendj-cleanup-test").toFile();
        temporaryDirectories.add(directory);
        final File subDirectory = new File(directory, "nested");
        assertTrue(subDirectory.mkdir());
        for (int i = 0; i < fileCount; i++) {
            final File parent = i == 0 ? subDirectory : directory;
            Files.write(new File(parent, "file" + i + ".txt").toPath(), new byte[] { 'x' });
        }
        return directory;
    }

    private static long timed(Runnable runnable) {
        final long start = System.nanoTime();
        runnable.run();
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }
}
