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
public abstract class Model {

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

    private ReplicationConfiguration replicationConfiguration;

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
        return license != null && !license.isEmpty();
    }

    /**
     * Returns {@code true} if this configuration is stand alone data store.
     *
     * @return {@code true} if this configuration is stand alone data store.
     */
    boolean isStandAlone() {
        return type == Type.STANDALONE;
    }

    /**
     * Returns {@code true} if this configuration is a first server in a replication topology.
     *
     * @return {@code true} if this configuration is a first server in a replication topology.
     */
    boolean isFirstInTopology() {
        return type == Type.FIRST_IN_TOPOLOGY;
    }

    /**
     * Returns {@code true} if this configuration is part of a replication topology.
     *
     * @return {@code true} if this configuration is part of a replication topology.
     */
    boolean isPartOfReplicationTopology() {
        return type == Type.IN_EXISTING_TOPOLOGY;
    }

    /**
     * Returns {@code true} if this configuration has a certificate linked to it. That generally means SSL and/or SSL
     * are activated.
     *
     * @return {@code true} if this configuration has a certificate linked to it.
     */
    boolean isSecure() {
        return getListenerSettings() != null
            && getListenerSettings().getCertificate() != null;
    }

    /**
     * Sets this configuration as a stand alone data store.
     */
    void setStandAloneDS() {
        setType(Type.STANDALONE);
    }

    /**
     * Sets the type of this server as : replication activated and this is the first server in topology.
     */
    void setFirstInTopology() {
        setType(Type.FIRST_IN_TOPOLOGY);
    }

    /**
     * Sets the type of this server as : replication activated and this is a server in an existing topology.
     */
    void setInExistingTopology() {
        setType(Type.IN_EXISTING_TOPOLOGY);
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

    /**
     * Returns {@code true} if the directory server should start as a service.
     *
     * @return {@code true} if the directory server should start as a service, {@code false} otherwise.
     */
    public boolean isService() {
        return isService;
    }

    /**
     * Sets the directory server as a service.
     *
     * @param isAService
     *            {@code true} if the directory server should start as a service, {@code false} otherwise.
     */
    public void setService(boolean isAService) {
        isService = isAService;
    }

    /**
     * Returns the instance path.
     *
     * @return The instance path where the binaries are installed.
     */
    public String getInstancePath() {
        return instancePath;
    }

    /**
     * Sets the current instance path location.
     *
     * @param iPath
     *            The instance path.
     */
    public void setInstancePath(String iPath) {
        instancePath = iPath;
    }

    /**
     * Returns the license.
     *
     * @return The license.
     */
    public String getLicense() {
        return license;
    }

    /**
     * Sets the license linked to this installation.
     *
     * @param theLicense
     *            The license to set.
     */
    public void setLicense(String theLicense) {
        license = theLicense;
    }

    /**
     * Returns the runtime options that apply to this installation.
     *
     * @return The runtime options that apply to this installation.
     */
    public RuntimeOptions getServerRuntimeSettings() {
        return serverRuntimeSettings;
    }

    /**
     * Sets the runtime options that apply to this installation.
     *
     * @param settings
     *            The runtime options that apply to this installation.
     */
    public void setServerRuntimeOptions(RuntimeOptions settings) {
        serverRuntimeSettings = settings;
    }

    /**
     * Returns the runtime options that apply to the current import LDIF.
     *
     * @return The runtime options that apply to the current import LDIF.
     */
    public RuntimeOptions getImportLdifRuntimeOptions() {
        return importLdifRuntimeSettings;
    }

    /**
     * Sets the runtime options that apply to the current import LDIF.
     *
     * @param settings
     *            The runtime options that apply to the current import LDIF.
     */
    public void setImportLdifRuntimeOptions(RuntimeOptions settings) {
        importLdifRuntimeSettings = settings;
    }

    /**
     * Returns the data configuration of this model.
     *
     * @return The data configuration of this model.
     */
    public DataConfiguration getDataConfiguration() {
        return dataConfiguration;
    }

    /**
     * Sets the data configuration of this model.
     *
     * @param dConfiguration
     *            The data configuration to set for this model.
     */
    public void setDataConfiguration(DataConfiguration dConfiguration) {
        dataConfiguration = dConfiguration;
    }

    /**
     * Returns the replication configuration of this model.
     *
     * @return The replication configuration of this model.
     */
    public ReplicationConfiguration getReplicationConfiguration() {
        return replicationConfiguration;
    }

    /**
     * Sets the replication configuration of this model.
     *
     * @param replicationConfiguration
     *            The replication configuration to set for this model.
     */
    public void setReplicationConfiguration(ReplicationConfiguration replicationConfiguration) {
        this.replicationConfiguration = replicationConfiguration;
    }

    /**
     * Returns the installation path of this model.
     *
     * @return The installation path of this model.
     */
    public String getInstallationPath() {
        return installationPath;
    }

    /**
     * Sets the installation path of this model.
     *
     * @param installationPath
     *            The installation path of this model.
     */
    public void setInstallationPath(String installationPath) {
        this.installationPath = installationPath;
    }

    /**
     * Creates a basic data store model configuration for setup.
     */
    public static class DataStoreModel extends Model {
        /**
         * The default data store model.
         */
        public DataStoreModel() {
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
            if (getReplicationConfiguration() == null) {
                throw new ConfigException(LocalizableMessage.raw("No replication configuration found"));
            }
            if (isPartOfReplicationTopology()) {
                Reject.ifNull(getReplicationConfiguration().getAdministrator(),
                        "Administrator name should not be null");
                Reject.ifNull(getReplicationConfiguration().getPassword(), "Admin password should not be null");
                Reject.ifNull(getReplicationConfiguration().getGlobalAdministrator(),
                        "Global administrator should not be null");
                Reject.ifNull(getReplicationConfiguration().getGlobalAdministratorPassword(),
                        "Global administrator should not be null");
                if (getReplicationConfiguration().getSuffixes() == null
                        || getReplicationConfiguration().getSuffixes().size() == 0) {
                    throw new ConfigException(
                            LocalizableMessage.raw("At least one base DN should be selected "
                                    + "to replicate content with"));
                }
            }
        }
        final ListenerSettings settings = getListenerSettings();
        final DataConfiguration dataConf = getDataConfiguration();
        if (settings == null) {
            throw new ConfigException(LocalizableMessage.raw("Invalid settings"));
        }
        if (dataConf == null) {
            throw new ConfigException(LocalizableMessage.raw("Invalid data configuration"));
        }
        if (dataConf.isImportLDIF() && dataConf.getLdifImportDataPath() == null) {
            throw new ConfigException(LocalizableMessage.raw("Invalid import ldif file."));
        }
        if (settings.getPasswordFile() == null && settings.getPassword() == null) {
            throw new ConfigException(LocalizableMessage.raw("A password must be set for the root DN."));
        }
    }

}
