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

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.util.Reject;

/**
 * This class provides configuration's model for the OpenDJ3 setup.
 */
abstract class Model {

    /**
     * This enumeration is used to know what kind of server we want to set up.
     */
    public enum Type {
        /**
         * Stand alone server.
         */
        STANDALONE,
        /**
         * First server in topology.
         */
        FIRST_IN_TOPOLOGY,
        /**
         * Replicate the new suffix with existing server.
         */
        IN_EXISTING_TOPOLOGY
    }

    private Type type;
    private boolean startingServerAfterSetup;
    private boolean isService;
    private String instancePath;
    private String installationPath;

    private String license;
    private ListenerSettings settings;
    private RuntimeOptions serverRuntimeSettings;
    private RuntimeOptions importLdifRuntimeSettings;
    private DataConfiguration dataConfiguration;

    private ReplicationConfiguration replicationConfiguration = null;

    /**
     * Returns the listener settings.
     *
     * @return The listener settings.
     */
    public ListenerSettings getListenerSettings() {
        return settings;
    }

    /**
     * Sets the listener settings.
     *
     * @param lSettings
     *            The listener settings to set.
     */
    public void setListenerSettings(final ListenerSettings lSettings) {
        settings = lSettings;
    }

    /**
     * Returns {@code true} if this configuration has a non-empty license.
     *
     * @return {@code true} if this configuration has a license.
     */
    public boolean hasLicense() {
        return (this.license != null && !license.isEmpty());
    }

    /**
     * Returns {@code true} if this configuration is stand alone data store.
     *
     * @return {@code true} if this configuration is stand alone data store.
     */
    boolean isStandAlone() {
        return (type == Type.STANDALONE);
    }

    /**
     * Returns {@code true} if this configuration is a first server in a replication topology.
     *
     * @return {@code true} if this configuration is a first server in a replication topology.
     */
    boolean isFirstInTopology() {
        return (type == Type.FIRST_IN_TOPOLOGY);
    }

    /**
     * Returns {@code true} if this configuration is part of a replication topology.
     *
     * @return {@code true} if this configuration is part of a replication topology.
     */
    boolean isPartOfReplicationTopology() {
        return (type == Type.IN_EXISTING_TOPOLOGY);
    }

    /**
     * Returns {@code true} if this configuration has a certificate linked to it.
     * That generally means SSL and/or SSL are activated.
     *
     * @return {@code true} if this configuration has a certificate linked to it.
     */
    boolean isSecure() {
        return (this.getListenerSettings() != null
                && this.getListenerSettings().getCertificate() != null);
    }

    /**
     * Sets this configuration as a stand alone data store.
     */
    void setStandAloneDS() {
        setType(Type.STANDALONE);
    }

    /**
     * Sets the type of this server as : replication activated
     * and this is the first server in topology.
     */
    void setFirstInTopology() {
        this.setType(Type.FIRST_IN_TOPOLOGY);
    }

    /**
     * Sets the type of this server as : replication activated
     * and this is a server in an existing topology.
     */
    void setInExistingTopology() {
        this.setType(Type.IN_EXISTING_TOPOLOGY);
    }

    /**
     * Returns the type of this configuration.
     *
     * @return The type of this configuration.
     */
    public Type getType() {
        return type;
    }

    /**
     * Sets the type of this configuration.
     *
     * @param mtype
     *            The type of this configuration (standalone, etc...)
     */
    public void setType(Type mtype) {
        type = mtype;
    }

    /**
     * Returns {@code true} if the server must start after the installation.
     *
     * @return {@code true} if the server must start after the installation.
     */
    public boolean isStartingServerAfterSetup() {
        return startingServerAfterSetup;
    }

    /**
     * Sets if the server should start after its installation.
     *
     * @param startServerAfterSetup
     *            {@code true} if the server must start after the installation.
     */
    public void setStartingServerAfterSetup(boolean startServerAfterSetup) {
        startingServerAfterSetup = startServerAfterSetup;
    }

    public boolean isService() {
        return isService;
    }

    public void setService(boolean isAService) {
        isService = isAService;
    }

    public String getInstancePath() {
        return instancePath;
    }

    public void setInstancePath(String iPath) {
        instancePath = iPath;
    }

    public String getLicense() {
        return license;
    }

    public void setLicense(String theLicense) {
        license = theLicense;
    }

    public RuntimeOptions getServerRuntimeSettings() {
        return serverRuntimeSettings;
    }

    public void setServerRuntimeOptions(RuntimeOptions settings) {
        serverRuntimeSettings = settings;
    }

    public RuntimeOptions getImportLdifRuntimeOptions() {
        return importLdifRuntimeSettings;
    }

    public void setImportLdifRuntimeOptions(RuntimeOptions settings) {
        importLdifRuntimeSettings = settings;
    }

    public DataConfiguration getDataConfiguration() {
        return dataConfiguration;
    }

    public void setDataConfiguration(DataConfiguration dConfiguration) {
        dataConfiguration = dConfiguration;
    }

    public ReplicationConfiguration getReplicationConfiguration() {
        return replicationConfiguration;
    }

    public void setReplicationConfiguration(ReplicationConfiguration replicationConfiguration) {
        this.replicationConfiguration = replicationConfiguration;
    }



    public String getInstallationPath() {
        return installationPath;
    }

    public void setInstallationPath(String installationPath) {
        this.installationPath = installationPath;
    }

    /**
     * Creates a basic data store model configuration for setup.
     */
    static class DataStoreModel extends Model {
        DataStoreModel() {
            setStandAloneDS();
            setDataConfiguration(new DataConfiguration());
            setListenerSettings(new ListenerSettings());
            setServerRuntimeOptions(RuntimeOptions.getDefault());
            setImportLdifRuntimeOptions(RuntimeOptions.getDefault());
            setStartingServerAfterSetup(true);
        }
    }

    /**
     * Checks the validity of the current setup configuration.
     *
     * @throws ConfigException
     *             If this configuration is invalid.
     */
    void validate() throws ConfigException {
        if (isFirstInTopology() || isPartOfReplicationTopology()) {
            if (this.getReplicationConfiguration() == null) {
                throw new ConfigException(LocalizableMessage.raw("No replication configuration found"));
            }
            if (isPartOfReplicationTopology()) {
                Reject.ifNull(this.getReplicationConfiguration().getAdministrator(),
                        "Administrator name should not be null");
                Reject.ifNull(this.getReplicationConfiguration().getPassword(),
                        "Admin password should not be null");
                Reject.ifNull(this.getReplicationConfiguration().getGlobalAdministrator(),
                        "Global administrator should not be null");
                Reject.ifNull(this.getReplicationConfiguration().getGlobalAdministratorPassword(),
                        "Global administrator should not be null");
                if (getReplicationConfiguration().getSuffixes() == null
                        || getReplicationConfiguration().getSuffixes().size() == 0) {
                    throw new ConfigException(
                            LocalizableMessage.raw(
                                    "At least one base DN should be selected to replicate content with"));
                }
            }
        }
        if (getListenerSettings() == null) {
            throw new ConfigException(LocalizableMessage.raw("Invalid settings"));
        }
        if (getDataConfiguration() == null) {
            throw new ConfigException(LocalizableMessage.raw("Invalid data configuration"));
        }
        if (getDataConfiguration().isImportLDIF()) {
            if (getDataConfiguration().getLdifImportDataPath() == null) {
                throw new ConfigException(LocalizableMessage.raw("Invalid import ldif file."));
            }
        }
        if (getListenerSettings().getPasswordFile() == null && getListenerSettings().getPassword() == null) {
            throw new ConfigException(LocalizableMessage.raw("A password must be set for the root DN."));
        }
    }

}
