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
    private int port = Integer.parseInt(System.getProperty(CONFIG_PREFIX + ".port", "1389"));

    private int adminPort = Integer.parseInt(System.getProperty(CONFIG_PREFIX + ".admin_port", "4444"));

    private String adminPassword = System.getProperty(CONFIG_PREFIX + ".password", "passw0rd");

    private String baseDN = System.getProperty(CONFIG_PREFIX + ".root", "dc=openidentityplatform,dc=org");

    private String backendType = System.getProperty(CONFIG_PREFIX + ".backend", "je");

    private int jmxPort = Integer.parseInt(System.getProperty(CONFIG_PREFIX + ".jmx_port", "1689"));

    private String ldifSchema = System.getProperty(CONFIG_PREFIX + ".ldif.schema");

    private String file = System.getProperty(CONFIG_PREFIX + ".ldif.data", "/test.ldif");

    private Set<String> skipSet = new HashSet<>(Arrays.asList(System.getProperty(CONFIG_PREFIX + ".skip", ",ou=sample-skip-group,").toLowerCase().split(";")));

    private long deleteTimeout = Long.parseLong(System.getProperty(CONFIG_PREFIX + ".delete_timeout", "10000"));

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

    public void setDeleteTimeout(long deleteTimeout) {
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
