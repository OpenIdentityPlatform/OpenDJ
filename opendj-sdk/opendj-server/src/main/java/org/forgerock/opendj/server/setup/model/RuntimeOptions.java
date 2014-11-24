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
 *      Copyright 2014 ForgeRock AS.
 */
package org.forgerock.opendj.server.setup.model;

/**
 * This class provides the model of the runtime options which can be used
 * for the server settings or for the import LDIF settings.
 */
class RuntimeOptions {

    static final int INITIAL_MEMORY = 128;
    static final int MAXIMUM_MEMORY = 256;

    private int initialMemory = -1;
    private int maximumMemory = -1;
    private String[] additionalArguments = {};

    RuntimeOptions() {
        // Nothing to do.
    }

    static RuntimeOptions getDefault() {
        final RuntimeOptions ro = new RuntimeOptions();
        ro.initialMemory = 128;
        ro.maximumMemory = 256;
        ro.additionalArguments = new String[] { "-client" };
        return ro;
    }

    /**
     * Returns the initial memory allowed to execute the command-line.
     *
     * @return the initial memory allowed to execute the command-line.
     */
    public int getInitialMemory() {
        return initialMemory;
    }

    /**
     * Sets the initial memory allowed to execute the command-line.
     *
     * @param initialMemory
     *            the initial memory allowed to execute the command-line.
     */
    public void setInitialMemory(int initialMemory) {
        this.initialMemory = initialMemory;
    }

    /**
     * Returns the maximum memory allowed to execute the command-line.
     *
     * @return the maximum memory allowed to execute the command-line.
     */
    public int getMaximumMemory() {
        return maximumMemory;
    }

    public void setMaximumMemory(int maximumMemory) {
        this.maximumMemory = maximumMemory;
    }

    /**
     * Returns the additional arguments to be used when executing the command-line.
     *
     * @return the additional arguments to be used when executing the command-line.
     */
    public String[] getAdditionalArguments() {
        return additionalArguments;
    }

    /**
     * Sets the additional arguments to be used when executing the command-line.
     *
     * @param additionalArguments
     *            the additional arguments to be used when executing the command-line. It cannot be null.
     */
    public void setAdditionalArguments(String... additionalArguments) {
        if (additionalArguments == null) {
            throw new IllegalArgumentException("additionalArguments cannot be null.");
        }
        this.additionalArguments = additionalArguments;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof RuntimeOptions)) {
            return false;
        }
        final RuntimeOptions other = (RuntimeOptions) o;
        if (initialMemory == other.initialMemory
                && maximumMemory == other.maximumMemory
                && additionalArguments.length == other.additionalArguments.length) {
            final String[] args = other.additionalArguments;
            for (int i = 0; i < args.length; i++) {
                if (!args[i].equals(additionalArguments[i])) {
                    return false;
                }
            }
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        int hashCode = 44 + initialMemory + maximumMemory;
        for (String arg : additionalArguments) {
            hashCode += arg.hashCode();
        }
        return hashCode;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Initial Memory: ").append(initialMemory).append("  Max Memory: ").append(maximumMemory);
        int i = 1;
        for (final String arg : additionalArguments) {
            sb.append(" arg ").append(i).append(": ").append(arg);
            i++;
        }
        return sb.toString();
    }

    /**
     * Returns the java argument to specify the initial memory to be used.
     *
     * @param value
     *            the value in megabytes to be specified.
     * @return the java argument to specify the initial memory to be used.
     */
    public static String getInitialMemoryArgument(int value) {
        return "-Xms" + value + "m";
    }

    /**
     * Returns the java argument to specify the maximum memory that can be used.
     *
     * @param value
     *            the value in megabytes to be specified.
     * @return the java argument to specify the maximum memory that can be used.
     */
    public static String getMaxMemoryArgument(int value) {
        return "-Xmx" + value + "m";
    }

}
