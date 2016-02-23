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
 * Copyright 2014 ForgeRock AS.
 */
package org.forgerock.opendj.server.setup.model;

import java.util.List;

/**
 * This class provides the model of the replication configuration.
 */
class ReplicationConfiguration {

    /** Default port number for the replication port. */
    static final int DEFAULT_REPLICATION_PORT = 389;

    /** First in topology. */
    private int replicationPort;
    private boolean isSecure;

    /** Part of topology. */
    private String hostName;
    private int hostPort;
    private String administrator;
    private char[] password;
    private List<String> suffixes;
    private boolean createGlobalAdministrator;
    private String globalAdministrator;
    private char[] globalAdministratorPassword;

    ReplicationConfiguration() {
        replicationPort = DEFAULT_REPLICATION_PORT;
        isSecure = false;
        createGlobalAdministrator = false;
    }

    /**
     * Returns the replication port.
     *
     * @return The replication port.
     */
    public int getReplicationPort() {
        return replicationPort;
    }

    /**
     * Sets the port used in replication.
     *
     * @param port
     *            The replication port.
     */
    public void setReplicationPort(int port) {
        replicationPort = port;
    }

    /**
     * Returns {@code true} if this connection should be secure.
     *
     * @return {@code true} if this is a secure connection.
     */
    public boolean isSecure() {
        return isSecure;
    }

    /**
     * Sets this connection to secure if needed.
     *
     * @param secure
     *            {@code true} if the connection needs to be secure.
     */
    public void setSecure(boolean secure) {
        isSecure = secure;
    }

    /**
     * Returns the host name.
     *
     * @return The host name.
     */
    public String getHostName() {
        return hostName;
    }

    /**
     * Sets the host name.
     *
     * @param hName
     *            The host name.
     */
    public void setHostName(String hName) {
        hostName = hName;
    }

    /**
     * Returns the host port.
     *
     * @return The host port.
     */
    public int getHostPort() {
        return hostPort;
    }

    /**
     * Sets the host port.
     *
     * @param hPort
     *            The host port to set.
     */
    public void setHostPort(int hPort) {
        hostPort = hPort;
    }

    /**
     * Returns the administrator name.
     *
     * @return The administrator name.
     */
    public String getAdministrator() {
        return administrator;
    }

    /**
     * Sets the administrator name.
     *
     * @param adminName
     *            The administrator name to set.
     */
    public void setAdministrator(String adminName) {
        administrator = adminName;
    }

    /**
     * Returns the password.
     *
     * @return The password.
     */
    public char[] getPassword() {
        return password;
    }

    /**
     * Sets the password linked to the administrator name.
     *
     * @param adminPassword
     *            The password linked to this administrator name.
     */
    public void setPassword(char[] adminPassword) {
        password = adminPassword;
    }

    /**
     * Returns a list of the suffixes.
     *
     * @return A list of suffixes.
     */
    public List<String> getSuffixes() {
        return suffixes;
    }

    /**
     * Sets the list of the suffixes.
     *
     * @param lSuffixes
     *            The list of the existing suffixes.
     */
    public void setSuffixes(List<String> lSuffixes) {
        suffixes = lSuffixes;
    }

    /**
     * Returns the need to create the global administrator.
     *
     * @return {@code true} if the global administrator creation is needed.
     */
    public boolean isCreateGlobalAdministrator() {
        return createGlobalAdministrator;
    }

    /**
     * Sets the global administrator creation.
     *
     * @param createGlobalAdministrator
     *            {@code true} if the global administrator creation is required.
     */
    public void setCreateGlobalAdministrator(boolean createGlobalAdministrator) {
        this.createGlobalAdministrator = createGlobalAdministrator;
    }

    /**
     * Returns the UID of the global administrator.
     *
     * @return The UID of the global administrator.
     */
    public String getGlobalAdministrator() {
        return globalAdministrator;
    }

    /**
     * Sets the UID of the global administrator.
     *
     * @param globalAdministratorUID
     *            The UID of the global administrator.
     */
    public void setGlobalAdministratorUID(String globalAdministratorUID) {
        this.globalAdministrator = globalAdministratorUID;
    }

    /**
     * Returns the password of the global administrator.
     *
     * @return The password of the global administrator.
     */
    public String getGlobalAdministratorPassword() {
        return String.valueOf(globalAdministratorPassword);
    }

    /**
     * Sets the password of the global administrator.
     *
     * @param globalAdministratorPwd
     *            The password of the global administrator.
     */
    public void setGlobalAdministratorPassword(char[] globalAdministratorPwd) {
        this.globalAdministratorPassword = globalAdministratorPwd;
    }

}
