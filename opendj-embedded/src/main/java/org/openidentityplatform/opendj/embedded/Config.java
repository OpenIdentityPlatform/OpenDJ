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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Config {

    private final String CONFIG_PREFIX = Config.class.getPackage().getName();
    private int port = intProperty(CONFIG_PREFIX + ".port", "1389");

    private int adminPort = intProperty(CONFIG_PREFIX + ".admin_port", "4444");

    private String adminPassword = System.getProperty(CONFIG_PREFIX + ".password", "passw0rd");

    private String baseDN = System.getProperty(CONFIG_PREFIX + ".root", "dc=openidentityplatform,dc=org");

    private String backendType = System.getProperty(CONFIG_PREFIX + ".backend", "je");

    private int jmxPort = intProperty(CONFIG_PREFIX + ".jmx_port", "1689");

    private String ldifSchema = System.getProperty(CONFIG_PREFIX + ".ldif.schema");

    private String file = System.getProperty(CONFIG_PREFIX + ".ldif.data", "/test.ldif");

    private Set<String> skipSet = new HashSet<>(Arrays.asList(System.getProperty(CONFIG_PREFIX + ".skip", ",ou=sample-skip-group,").toLowerCase().split(";")));

    private long deleteTimeout = timeoutProperty(CONFIG_PREFIX + ".delete_timeout", "10000");

    /**
     * Returns the value of a system property holding a number.
     * <p>
     * These fields are initialized when the instance is created, so an unhandled
     * {@link NumberFormatException} would surface from the constructor of
     * {@link EmbeddedOpenDJ} saying only which string was rejected, without naming the
     * property that carried it.
     *
     * @param name
     *            the name of the system property
     * @param defaultValue
     *            the value used when the property is not set
     * @return the value of the property, or {@code defaultValue} when it is not set
     * @throws IllegalArgumentException
     *             If the value of the property is not a number.
     */
    private static int intProperty(String name, String defaultValue) {
        final String value = System.getProperty(name, defaultValue);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw invalidProperty(name, value, e);
        }
    }

    /**
     * Returns the value of a system property holding a duration in milliseconds.
     *
     * @param name
     *            the name of the system property
     * @param defaultValue
     *            the value used when the property is not set
     * @return the value of the property, or {@code defaultValue} when it is not set
     * @throws IllegalArgumentException
     *             If the value of the property is not a number, or is negative.
     */
    private static long timeoutProperty(String name, String defaultValue) {
        final String value = System.getProperty(name, defaultValue);
        final long timeout;
        try {
            timeout = Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw invalidProperty(name, value, e);
        }
        if (timeout < 0) {
            throw new IllegalArgumentException("Invalid value \"" + value + "\" for the system property "
                    + name + ": a timeout in milliseconds cannot be negative");
        }
        return timeout;
    }

    private static IllegalArgumentException invalidProperty(String name, String value, NumberFormatException cause) {
        return new IllegalArgumentException("Invalid value \"" + value + "\" for the system property "
                + name + ": expected a number", cause);
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getAdminPort() {
        return adminPort;
    }

    public void setAdminPort(int adminPort) {
        this.adminPort = adminPort;
    }


    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public String getBaseDN() {
        return baseDN;
    }

    public void setBaseDN(String baseDN) {
        this.baseDN = baseDN;
    }

    public String getBackendType() {
        return backendType;
    }

    public void setBackendType(String backendType) {
        this.backendType = backendType;
    }

    public int getJmxPort() {
        return jmxPort;
    }

    public void setJmxPort(int jmxPort) {
        this.jmxPort = jmxPort;
    }

    public String getLdifSchema() {
        return ldifSchema;
    }

    public void setLdifSchema(String ldifSchema) {
        this.ldifSchema = ldifSchema;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }


    public Set<String> getSkipSet() {
        return skipSet;
    }

    public void setSkipSet(Set<String> skipSet) {
        this.skipSet = skipSet;
    }

    /**
     * Returns how long {@link EmbeddedOpenDJ#close()} retries the deletion of the temporary
     * directory of the instance, in milliseconds.
     * <p>
     * The deletion has to be retried because a file cannot be deleted on Windows while a
     * handle to it is still open, and the server threads release their handles shortly after
     * the server has been stopped. Whatever is still locked when this expires is scheduled for
     * deletion on JVM exit. Set it to {@code 0} to delete once and never wait.
     *
     * @return the deletion timeout in milliseconds
     */
    public long getDeleteTimeout() {
        return deleteTimeout;
    }

    /**
     * Sets how long {@link EmbeddedOpenDJ#close()} retries the deletion of the temporary
     * directory of the instance.
     *
     * @param deleteTimeout
     *            the deletion timeout in milliseconds, {@code 0} to delete once and never wait
     * @throws IllegalArgumentException
     *             If the timeout is negative.
     */
    public void setDeleteTimeout(long deleteTimeout) {
        if (deleteTimeout < 0) {
            throw new IllegalArgumentException("The delete timeout cannot be negative, but was " + deleteTimeout);
        }
        this.deleteTimeout = deleteTimeout;
    }

    @Override
    public String toString() {
        return "Config {" +
                "port=" + port +
                ", adminPort=" + adminPort +
                ", adminPassword='" + adminPassword + '\'' +
                ", baseDN='" + baseDN + '\'' +
                ", backendType='" + backendType + '\'' +
                ", jmxPort=" + jmxPort +
                ", ldifSchema='" + ldifSchema + '\'' +
                ", file='" + file + '\'' +
                ", skipSet=" + skipSet +
                ", deleteTimeout=" + deleteTimeout +
                '}';
    }
}
