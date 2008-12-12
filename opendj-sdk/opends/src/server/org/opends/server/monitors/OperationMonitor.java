/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.monitors;

import org.opends.server.api.MonitorProvider;
import org.opends.server.protocols.ldap.LDAPStatistics;
import org.opends.server.types.OperationType;

/**
 * This class defines a monitor object which are able to measure
 * an elapse time (nanosecs), a number of times the measurement was done,
 * the minimum and the maximum.
 */
public class OperationMonitor {

    /**
     * Undefined value for the timer.
     */
    public static long UNDEFINED_VALUE = 0;

    // Lock object
    private Object lock = new Object();
    // Type of the operation
    private OperationType opType;
    // Date of creation of the object
    private long creationTime;
    // ElapseTime
    private long eTime = UNDEFINED_VALUE;
    // Date the start method was called
    private long startTime = UNDEFINED_VALUE;
    // Date stop method was called
    private long stopTime = UNDEFINED_VALUE;
    // Accumualted time
    private long totalTime = UNDEFINED_VALUE;
    // Min time
    private long minTime = UNDEFINED_VALUE;
    // Max time
    private long maxTime = UNDEFINED_VALUE;

    // Counter
    private CounterMonitor counter;

    // Private constructor
    private OperationMonitor(OperationMonitor opMonitor) {
        this.opType = opMonitor.getType();
        this.eTime = opMonitor.getTime();
        this.startTime = opMonitor.getStartTime();
        this.stopTime = opMonitor.getStopTime();
        this.totalTime = opMonitor.getTotalTime();
        this.maxTime = opMonitor.getMaxTime();
        this.minTime = opMonitor.getMinTime();
        this.counter = opMonitor.getCounter().duplicate();
    }

    // Private constructor
    private OperationMonitor(OperationType opType) {
        this.opType = opType;
        this.counter = CounterMonitor.getCounter();
        this.creationTime = System.nanoTime();
    }

    /**
     * Gets a Operation Monitor object od the specified type.
     * @param opType of the monitor object.
     * @return the Operation monitor object.
     */
    public static OperationMonitor getOperationMonitor(OperationType opType) {
        return new OperationMonitor(opType);
    }

    /**
     * Returns a duplicate object.
     * @return the duplicate object.
     */
    public OperationMonitor duplicate() {
        return new OperationMonitor(this);
    }

    /**
     * Add the provider moniyot object to the current one.
     * @param opMonitor to add.
     */
    public void add(OperationMonitor opMonitor) {
        synchronized (lock) {
            this.counter.add(opMonitor.getCounter());
            this.totalTime += opMonitor.getTotalTime();
            if ((this.minTime == UNDEFINED_VALUE) ||
                    (this.minTime > opMonitor.getMinTime())) {
                this.minTime = opMonitor.getMinTime();
            }
            if (this.maxTime < opMonitor.getMaxTime()) {
                this.maxTime = opMonitor.getMaxTime();
            }
        }
    }

    /**
     * Sets the starTime.
     */
    public void start() {
        synchronized (lock) {
            this.counter.increment();
            this.startTime = System.nanoTime();
        }
    }

    /**
     * Sets the stopTime, process the elapseTime, min max and accumulated
     * time.
     */
    public void stop() {
        synchronized (lock) {
            this.stopTime = System.nanoTime();
            this.eTime = stopTime - startTime;
            totalTime += eTime;
            if ((this.minTime == UNDEFINED_VALUE) || (eTime < this.minTime)) {
                this.minTime = this.eTime;
            }
            if (eTime > this.maxTime) {
                this.maxTime = this.eTime;
            }
        }
    }

    /**
     * Gets a elapse time.
     * @return the elapse time.
     */
    public long getTime() {
        return eTime;
    }

    /**
     * Gets the Min time.
     * @return the min time.
     */
    public long getMinTime() {
        return this.minTime;
    }

    /**
     * Gets the max time.
     * @return the max time.
     */
    public long getMaxTime() {
        return this.maxTime;
    }

    /**
     * Gets the accumulated time.
     * @return the accumulated time.
     */
    public long getTotalTime() {
        return this.totalTime;
    }

    /**
     * Gets the startTime.
     * @return the date in a long format.
     */
    public long getStartTime() {
        return this.startTime;
    }

    /**
     * Get the stopTime.
     * @return the date in long format.
     */
    public long getStopTime() {
        return this.stopTime;
    }

    /**
     * Gets the counter object.
     * @return the counterMonitor object.
     */
    public CounterMonitor getCounter() {
        return this.counter;
    }

    /**
     * Gets the type of the OperationMonitor object.
     * @return the OperationType.
     */
    public OperationType getType() {
        return this.opType;
    }

    /**
     * Reset the values to 0.
     */
    public void reset() {
        this.counter.reset();
        this.totalTime=UNDEFINED_VALUE;
        this.minTime=UNDEFINED_VALUE;
        this.maxTime=UNDEFINED_VALUE;
    }

    /**
     * Updates the associated monitor provider object calling
     * updateMonitorData() method of the monitorProvider object.
     * @param provider to update.
     */
    public void updateMonitorProvider(MonitorProvider provider) {
        provider.updateMonitorData();
    }

    /**
     * Updates the associated monitor provider object.
     * @param stats provider to update.
     */
    public void updateMonitorProvider(LDAPStatistics stats) {
        stats.updateMonitor(this);
    }
}
