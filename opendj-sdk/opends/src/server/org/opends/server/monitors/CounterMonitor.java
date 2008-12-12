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

/**
 * This class implements a monitor a basic counter.
 */
public class CounterMonitor {

    // private counter
    private int count=0;
    // max counter
    private int max=0;

    // private constructor
    private CounterMonitor() {
    }

    // private constructor
    private CounterMonitor(CounterMonitor countMonitor) {
        this.count=countMonitor.getCount();
        this.max=countMonitor.getMax();
    }

    /**
     * Gets a CounterMonitor Object.
     * @return a counter monitor object.
     */
    public static CounterMonitor getCounter() {
        return new CounterMonitor();
    }

    /**
     * Gets a copy of the counter monitor.
     * @return a duplicate object.
     */
    public CounterMonitor duplicate() {
        return new CounterMonitor(this);
    }

    /**
     * Adds a counter Monitor to the current counter object.
     * @param countMonitor to add.
     */
    public void add(CounterMonitor countMonitor) {
        this.count+=countMonitor.count;
        if ((this.count>this.max) && (this.count>=countMonitor.max)) {
            this.max=this.count;
        }
        else {
            if (this.max<countMonitor.getMax()) {
                this.max=countMonitor.getMax();
            }
        }
    }

    /**
     * Increments the counter object.
     */
    public void increment() {
        count++;
        if (count>max) max=count;
    }

    /**
     * Decrements the counter object.
     */
    public void decrement() {
        count--;
    }

    /**
     * Gets the current counter value.
     * @return the current counter value.
     */
    public int getCount() {
        return count;
    }

    /**
     * Gets the maximum value the counter reached since the object creation.
     * @return the maximum value.
     */
    public int getMax() {
        return max;
    }

    /**
     * Reset the counter values (Value and max).
     */
    public void reset() {
        this.count=0;
        this.max=0;
    }

}
