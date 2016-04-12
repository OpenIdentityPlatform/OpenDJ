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
 * Copyright 2011-2016 ForgeRock AS.
 */
package org.forgerock.openidm.accountchange;

import java.io.File;
import java.io.IOException;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;

import net.jcip.annotations.ThreadSafe;

/**
 * Fast request queue implementation on top of Berkley DB Java Edition.
 * The queue uses the canonalised dn for key and store only
 * the last password change for each dn.
 * <p/>
 */
@ThreadSafe
public class PersistedQueue {

    /** JE DB environment. */
    private final Environment dbEnv;
    /** JE DB instance for the queue. */
    private final Database queueDatabase;
    /** Queue cache size - number of element operations it is allowed to loose in case of system crash. */
    private final int cacheSize;
    /** This queue name. */
    private final String queueName;
    /** Queue operation counter, which is used to sync the queue database to disk periodically. */
    private int opsCounter;

    /**
     * Creates instance of persistent queue.
     *
     * @param queueEnvPath queue database environment directory path
     * @param queueName    descriptive queue name
     * @param cacheSize    how often to sync the queue to disk
     */
    public PersistedQueue(final File queueEnvPath,
                          final String queueName,
                          final int cacheSize) {
        // Create parent dirs for queue environment directory
        queueEnvPath.mkdirs();

        // Setup database environment
        final EnvironmentConfig dbEnvConfig = new EnvironmentConfig();
        dbEnvConfig.setTransactional(false);
        dbEnvConfig.setAllowCreate(true);
        this.dbEnv = new Environment(queueEnvPath,
                dbEnvConfig);

        // Setup non-transactional deferred-write queue database
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(false);
        dbConfig.setAllowCreate(true);
        dbConfig.setDeferredWrite(true);
        this.queueDatabase = dbEnv.openDatabase(null,
                queueName,
                dbConfig);
        this.queueName = queueName;
        this.cacheSize = cacheSize;
        this.opsCounter = 0;
    }

    /**
     * Retrieves and returns element from the head of this queue.
     *
     * @return element from the head of the queue or null if queue is empty
     * @throws IOException in case of disk IO failure
     */
    public String[] poll() throws IOException {
        final DatabaseEntry key = new DatabaseEntry();
        final DatabaseEntry data = new DatabaseEntry();
        final Cursor cursor = queueDatabase.openCursor(null, null);
        try {
            cursor.getFirst(key, data, LockMode.RMW);
            if (data.getData() == null) {
                return null;
            }
            final String res = new String(data.getData(), "UTF-8");
            final String dn = new String(key.getData(), "UTF-8");
            cursor.delete();
            opsCounter++;
            if (opsCounter >= cacheSize) {
                queueDatabase.sync();
                opsCounter = 0;
            }
            return new String[]{dn, res};
        } finally {
            cursor.close();
        }
    }

    /**
     * Pushes element to the queue.
     * <p/>
     * The entries are sorted in natural order and not in order of time when
     * they were added.
     *
     * @param dn The entry DN
     * @param element element
     * @throws IOException in case of disk IO failure
     */
    public synchronized void push(final String dn, final String element) throws IOException {
        Cursor cursor = queueDatabase.openCursor(null, null);
        try {
            // Open a cursor using a database handle
            DatabaseEntry foundChange = new DatabaseEntry();
            final DatabaseEntry key = new DatabaseEntry(dn.getBytes("UTF-8"));
            final DatabaseEntry data = new DatabaseEntry(element.getBytes("UTF-8"));

            //Find the data
            OperationStatus retVal = cursor.getSearchKey(key, foundChange, LockMode.DEFAULT);

            if (OperationStatus.SUCCESS.equals(retVal)) {
                cursor.putCurrent(data);
                opsCounter++;
            } else if (OperationStatus.NOTFOUND.equals(retVal)) {
                queueDatabase.put(null, key, data);
                opsCounter++;
            }
            if (opsCounter >= cacheSize) {
                queueDatabase.sync();
                opsCounter = 0;
            }
        } catch (IOException willNeverOccur) {
            //TODO test this catch
            willNeverOccur.printStackTrace();
            throw willNeverOccur;
        } finally {
            cursor.close();
        }
    }

    /**
     * Returns the size of this queue.
     *
     * @return the size of the queue
     */
    public long size() {
        return queueDatabase.count();
    }

    /**
     * Returns this queue name.
     *
     * @return this queue name
     */
    public String getQueueName() {
        return queueName;
    }

    /**
     * Closes this queue and frees up all resources associated to it.
     */
    public void close() {
        queueDatabase.sync();
        queueDatabase.close();
        dbEnv.close();
    }
}
