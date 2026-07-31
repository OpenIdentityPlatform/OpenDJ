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
 * Copyright 2024-2026 3A Systems LLC.
 */

package org.openidentityplatform.opendj.embedded;

import org.apache.commons.io.FileUtils;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.forgerock.opendj.ldif.EntryReader;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.opendj.ldif.LDIFEntryWriter;
import org.forgerock.opendj.server.config.client.BackendCfgClient;
import org.forgerock.opendj.server.embedded.ConfigParameters;
import org.forgerock.opendj.server.embedded.ConnectionParameters;
import org.forgerock.opendj.server.embedded.EmbeddedDirectoryServer;
import org.forgerock.opendj.server.embedded.EmbeddedDirectoryServerException;
import org.forgerock.opendj.server.embedded.SetupParameters;
import org.forgerock.opendj.server.embedded.UpgradeParameters;
import org.opends.server.backends.MemoryBackend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class EmbeddedOpenDJ implements Runnable, Closeable {
    private static final String JAR_SCHEMA_DIRECTORY = "opendj/config/schema/";

    private static final String ARCHIVE_NAME = "opendj.zip";

    /**
     * How long the deletion of the instance directory is retried when there is no point in
     * waiting for the full {@link Config#getDeleteTimeout() configured timeout}: from the
     * shutdown hook, where a longer wait only makes Control-C look hung, and from the
     * constructor, where an initialization failure has to be reported promptly. What is still
     * locked when this expires is deleted on JVM exit anyway.
     */
    private static final long SHORT_DELETE_TIMEOUT_MS = 1_000L;

    private static final long INITIAL_DELETE_RETRY_DELAY_MS = 50L;

    private static final long MAX_DELETE_RETRY_DELAY_MS = 500L;

    /** Upper bound on the number of leftover paths reported when the deletion fails. */
    private static final int MAX_REPORTED_REMAINING_PATHS = 20;

    final static Logger logger = LoggerFactory.getLogger(EmbeddedOpenDJ.class.getName());
    final EmbeddedDirectoryServer server;

    final Config config;

    private final File instanceDirectory;
    private final File rootDirectory;
    private final Thread shutdownHook;

    /** Written under {@code this}, read without locking so that a running {@link #close()} blocks nothing. */
    private volatile boolean closed;

    public EmbeddedOpenDJ() {
        this(new Config());
    }

    public EmbeddedOpenDJ(Config config) {

        logger.info("Create embedded OpenDJ instance: {}", config);

        this.config = config;
        File instanceDirectory = null;
        try {
            // Create a fresh per-instance parent directory, only accessible by the
            // current user, instead of the fixed shared {java.io.tmpdir}/opendj
            // directory. The server root inside it must be named "opendj" because
            // setup from an archive requires the server root directory to match the
            // root directory contained in the archive. The whole directory is
            // deleted on close().
            instanceDirectory = Files.createTempDirectory("opendj").toFile();
            File rootDirectory = new File(instanceDirectory, "opendj");
            if (!rootDirectory.mkdir()) {
                // the parent has just been created, so this only fails on a real filesystem error
                throw new IOException("Cannot create the server root directory " + rootDirectory);
            }
            logger.info("OpenDJ server root: {}", rootDirectory);

            File configDirectory = new File(rootDirectory, "config");
            File schemaDirectory = new File(configDirectory, "schema");
            server = EmbeddedDirectoryServer.manageEmbeddedDirectoryServer(
                    ConfigParameters.configParams()
                            .serverRootDirectory(rootDirectory.getPath())
                            .configurationFile(Paths.get(rootDirectory.getPath(), "config", "config.ldif").toString()),
                    ConnectionParameters.connectionParams()
                            .hostName("localhost")
                            .ldapPort(config.getPort())
                            .adminPort(config.getAdminPort())
                            .bindDn("cn=Directory Manager")
                            .bindPassword(config.getAdminPassword()),
                    System.out,
                    System.err);

            copyFilesFromJar(Collections.singletonList(ARCHIVE_NAME),"embedded-opendj/",rootDirectory);
            final File archive = new File(rootDirectory, ARCHIVE_NAME);
            server.extractArchiveForSetup(archive);
            // The archive is only needed for the extraction above. Keeping it would leave a
            // full copy of the distribution in the temporary directory and one more file to
            // delete on close().
            if (!archive.delete()) {
                logger.warn("Cannot delete {} after extracting it", archive);
            }

            server.setup(
                    SetupParameters.setupParams()
                            .baseDn(config.getBaseDN())
                            .backendType(config.getBackendType())
                            .jmxPort(config.getJmxPort())
            );

            List<String> schemaFiles = new ArrayList<>();
            if (config.getLdifSchema() != null) {
                schemaFiles.add(config.getLdifSchema());
            }

            copyFilesFromJar(schemaFiles, JAR_SCHEMA_DIRECTORY, schemaDirectory);

            this.instanceDirectory = instanceDirectory;
            this.rootDirectory = rootDirectory;
        }catch (Exception e) {
            logger.error("Error initializing OpenDJ");
            deleteInstanceDirectory(instanceDirectory, shortDeleteTimeout(config));
            throw new RuntimeException(e);
        }
        shutdownHook = new Thread(this::close, "EmbeddedOpenDJ shutdown hook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    /**
     * Returns the server root directory of this embedded instance.
     *
     * @return the server root directory
     * @throws IllegalStateException
     *             If this instance has been closed, and the directory therefore deleted.
     */
    public File getServerRootDirectory() {
        checkNotClosed();
        return rootDirectory;
    }

    @Override
    public void run() {
        checkNotClosed();
        try {
            final DN baseDN = DN.valueOf(config.getBaseDN());
            try (ManagementContext managementContext = server.getConfiguration()) {
                BackendCfgClient userRoot = managementContext.getRootConfiguration().getBackend("userRoot");
                userRoot.setBaseDN((Collections.singletonList(baseDN)));
                userRoot.setEnabled(true);
                userRoot.commit();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            logger.info("Check upgrade OpenDJ ...");
            server.upgrade(UpgradeParameters.upgradeParams().isIgnoreErrors(false));

            logger.info("Start OpenDJ ...");
            server.start();

        } catch (Exception e) {
            logger.error("Error starting OpenDJ", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Stops this instance, if it is still running, and deletes its temporary directory.
     * <p>
     * This method is idempotent: it is also registered as a JVM shutdown hook.
     * <p>
     * When the server cannot be stopped, this instance stays open and keeps its shutdown hook
     * registered, so that a later call - or the hook at JVM exit - retries the stop.
     */
    @Override
    public synchronized void close()  {
        if (closed) {
            return;
        }
        final boolean fromShutdownHook = Thread.currentThread() == shutdownHook;
        if (server.isRunning()) {
            try {
                logger.info("Shutting down OpenDJ ...");
                server.stop(this.getClass().getName(), LocalizableMessage.raw("Stopped after receiving Control-C"));
            }catch (Throwable e) {
                logger.error("Error stopping OpenDJ", e);
                if (!fromShutdownHook) {
                    // The server is still running: deleting its directory now would destroy a
                    // live installation. Leave this instance open, with its shutdown hook still
                    // registered, so that the stop can be retried.
                    return;
                }
                // The JVM is going down and there will be no later attempt, so the temporary
                // directory is removed even though the server has not stopped cleanly.
            }
        }
        closed = true;
        unregisterShutdownHook();
        deleteInstanceDirectory(instanceDirectory,
                fromShutdownHook ? shortDeleteTimeout(config) : config.getDeleteTimeout());
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("this embedded OpenDJ instance is closed");
        }
    }

    private static long shortDeleteTimeout(Config config) {
        return Math.min(config.getDeleteTimeout(), SHORT_DELETE_TIMEOUT_MS);
    }

    private void unregisterShutdownHook() {
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException e) {
            // close() was reached from the shutdown hook itself: nothing to unregister
        }
    }

    /**
     * Deletes the temporary directory of an instance, retrying for a bounded period and
     * falling back to a deletion on JVM exit.
     * <p>
     * Deleting once is not enough. On Windows a file cannot be deleted while a handle to it
     * is still open, and {@code server.stop()} does not wait for the server threads to
     * terminate: an embedded server runs them as daemon threads, which the shutdown monitor
     * of the directory server ignores. Some handles are therefore released shortly after
     * {@code stop()} has returned, and a single best-effort deletion loses that race and
     * silently leaks the whole directory, backend data included. Retrying is a way to wait
     * for those handles to be released - a single attempt already removes everything that is
     * not in use at that moment.
     *
     * @param directory
     *            the directory to delete, may be {@code null} when the instance failed to
     *            initialize before creating it
     * @param timeoutMs
     *            how long the deletion is retried, in milliseconds
     */
    static void deleteInstanceDirectory(File directory, long timeoutMs) {
        deleteInstanceDirectory(directory, timeoutMs, FileUtils::deleteQuietly);
    }

    /**
     * Implements {@link #deleteInstanceDirectory(File, long)} with an injectable deletion, so
     * that tests can drive the retries without depending on a genuinely undeletable file.
     *
     * @param deleteAttempt
     *            performs one deletion attempt and reports whether the directory is gone
     */
    static void deleteInstanceDirectory(File directory, long timeoutMs, Predicate<File> deleteAttempt) {
        if (directory == null || !directory.exists()) {
            return;
        }
        final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        long retryDelay = INITIAL_DELETE_RETRY_DELAY_MS;
        while (true) {
            // The second test covers a directory removed by someone else in the meantime:
            // deleteQuietly() reports a failure for a directory that is already gone.
            if (deleteAttempt.test(directory) || !directory.exists()) {
                return;
            }
            if (System.nanoTime() >= deadline) {
                logger.warn("Cannot delete {} within {} ms, some files are still in use. "
                        + "They are now scheduled for deletion on JVM exit:{}",
                        directory, timeoutMs, remainingPaths(directory));
                break;
            }
            try {
                Thread.sleep(retryDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while deleting {}. What is left is now scheduled "
                        + "for deletion on JVM exit:{}", directory, remainingPaths(directory));
                break;
            }
            retryDelay = Math.min(retryDelay * 2, MAX_DELETE_RETRY_DELAY_MS);
        }
        deleteTreeOnExit(directory);
    }

    /**
     * Describes what is left in the given directory: one path per line, at most
     * {@link #MAX_REPORTED_REMAINING_PATHS} of them, followed by the number of paths left out.
     */
    static String remainingPaths(File directory) {
        final List<String> reported = new ArrayList<>();
        final int total = collectRemainingPaths(directory, reported);
        final StringBuilder description = new StringBuilder();
        for (String path : reported) {
            description.append("\n  ").append(path);
        }
        if (total > reported.size()) {
            description.append("\n  ... and ").append(total - reported.size()).append(" more");
        }
        return description.toString();
    }

    /**
     * Adds at most {@link #MAX_REPORTED_REMAINING_PATHS} paths of the given tree to
     * {@code reported} and returns how many paths it holds in total.
     */
    private static int collectRemainingPaths(File file, List<String> reported) {
        final File[] children = file.listFiles();
        if (children == null || children.length == 0) {
            if (reported.size() < MAX_REPORTED_REMAINING_PATHS) {
                reported.add(file.getPath());
            }
            return 1;
        }
        int total = 0;
        for (File child : children) {
            total += collectRemainingPaths(child, reported);
        }
        return total;
    }

    private static void deleteTreeOnExit(File file) {
        // File.deleteOnExit() deletes in reverse order of registration, so a directory has to
        // be registered before its content for the content to be removed first.
        file.deleteOnExit();
        final File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteTreeOnExit(child);
            }
        }
    }

    private void copyFilesFromJar(List<String> jarFiles, String jarDirectory, File outputDirectory) throws IOException{
        for(String jarFile : jarFiles) {
            File outputFile = new File(outputDirectory, new File(jarFile).getName());
            final String resourcePath = !jarFile.contains("/")
                    ? "/"+jarDirectory + jarFile
                    : jarFile;
            try (InputStream in = new File(jarFile).exists()
                    ? Files.newInputStream(new File(jarFile).toPath())
                    : MemoryBackend.class.getResourceAsStream(resourcePath)) {
                if (in == null) {
                    throw new IOException("cannot find " + resourcePath);
                }
                FileUtils.copyInputStreamToFile(in, outputFile);
            }
        }
    }

    /**
     * Imports the LDIF read from the given stream.
     * <p>
     * The stream is closed by this method, whether the import succeeds or not.
     *
     * @param inputStream
     *            the LDIF to import
     * @throws IllegalStateException
     *             If this instance has been closed.
     */
    public void importData(InputStream inputStream) throws EmbeddedDirectoryServerException, IOException {
        checkNotClosed();
        logger.info("start import ldif from stream");

        org.forgerock.opendj.ldap.Entry  entryBefore;
        long recordCount = 0;
        try (EntryReader reader = new LDIFEntryReader(new BufferedReader(new InputStreamReader(inputStream)));
             Connection connection = server.getInternalConnection()) {
            while (reader.hasNext() && (entryBefore = reader.readEntry()) != null) {
                recordCount++;
                try {
                    connection.add(entryBefore);
                    logger.info("import ldif : {}",entryBefore.getName());
                }catch (LdapException e) {
                    logger.error("import ldif : {} {}",entryBefore.getName(),e.toString());
                }
            }
        }
        if(recordCount == 0) {
            logger.error("no records were imported, check file contents and permissions");
            throw new RuntimeException("no records were imported");
        }
    }

    /**
     * Writes the entries below the given base DN to the given stream, as LDIF.
     * <p>
     * The stream is flushed and closed by this method, whether the export succeeds or not.
     *
     * @param baseDN
     *            the base DN of the subtree to export
     * @param out
     *            where the LDIF is written
     * @throws IllegalStateException
     *             If this instance has been closed.
     */
    public void getData(String baseDN, OutputStream out) throws IOException, EmbeddedDirectoryServerException {
        checkNotClosed();
        // resources are closed in reverse order, so the writer is flushed and closed last
        try (LDIFEntryWriter ldifWriter = new LDIFEntryWriter(out);
             Connection connection = server.getInternalConnection();
             ConnectionEntryReader reader =
                     connection.search(baseDN, SearchScope.WHOLE_SUBTREE, "(objectClass=*)")) {
            while (reader.hasNext()) {
                if (!reader.isReference()) {
                    SearchResultEntry se = reader.readEntry();
                    if (!skipEntry(se)) {
                        ldifWriter.writeEntry(se);
                        logger.info("export {}", se.toString());
                    }
                }
            }
        }
    }

    private boolean skipEntry(SearchResultEntry se) {
        for (String skip : config.getSkipSet()) {
            if (se.getName().toString().toLowerCase().contains(skip)){
                logger.trace("ignore export {}", se);
                return true;
            }
        }
        return false;
    }

    public boolean isRunning() {
        return server != null && server.isRunning();
    }

}
