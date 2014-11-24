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

import static com.forgerock.opendj.cli.CliConstants.*;
import static org.fest.assertions.Assertions.assertThat;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.server.setup.model.Certificate.CertificateType;
import org.forgerock.opendj.server.setup.model.Model.DataStoreModel;
import org.forgerock.opendj.server.setup.model.Model.Type;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * This class tests the model functionality.
 */
public class ModelTestCase extends AbstractSetupTestCase {

    /**
     * Generates a model and verifies if the configuration is valid.
     *
     * @throws ConfigException
     */
    @Test
    public void testCreateDefaultDS() throws ConfigException {
        final Model ds = new DataStoreModel();

        Assert.assertTrue(ds.isStandAlone());

        final ListenerSettings dsSettings = ds.getListenerSettings();

        // Verify connection handler by default
        assertTrue(dsSettings.isHTTPConnectionHandlerEnabled());
        assertFalse(dsSettings.isSSLEnabled());
        assertFalse(dsSettings.isTLSEnabled());
        assertFalse(dsSettings.isJMXConnectionHandlerEnabled());
        ds.getListenerSettings().setPassword("password");

        // Verify ports
        assertThat(dsSettings.getAdminPort()).isEqualTo(DEFAULT_ADMIN_PORT);
        assertThat(dsSettings.getHTTPPort()).isEqualTo(DEFAULT_HTTP_PORT);
        assertThat(dsSettings.getJMXPort()).isEqualTo(DEFAULT_JMX_PORT);
        assertThat(dsSettings.getLdapPort()).isEqualTo(DEFAULT_LDAP_PORT);
        assertThat(dsSettings.getLdapsPort()).isEqualTo(DEFAULT_LDAPS_PORT);
        assertThat(dsSettings.getSNMPPort()).isEqualTo(DEFAULT_SNMP_PORT);
        assertThat(dsSettings.getSSLPortNumber()).isEqualTo(DEFAULT_SSL_PORT);

        assertFalse(ds.isService());
        assertTrue(ds.isStartingServerAfterSetup());
        assertFalse(ds.isPartOfReplicationTopology());
        assertTrue(ds.isStartingServerAfterSetup());

        assertThat(ds.getDataConfiguration()).isNotNull();
        assertThat(ds.getServerRuntimeSettings()).isEqualTo(RuntimeOptions.getDefault());
        assertFalse(ds.hasLicense());

        assertThat(ds.getType()).isEqualTo(Type.STANDALONE);
        assertFalse(ds.isSecure());

        ds.validate();

    }

    /**
     * Configure a DS with null listener settings should fail.
     *
     * @throws ConfigException
     *             If this configuration is invalid.
     */
    @Test(expectedExceptions = ConfigException.class,
            expectedExceptionsMessageRegExp = "Invalid settings")
    public void testIsValidDSDoesNotAllowNullListenerSettings() throws ConfigException {
        final Model ds = new DataStoreModel();
        ds.setListenerSettings(null);
        ds.validate();
    }

    /**
     * Configure a DS with null data configuration should fail.
     *
     * @throws ConfigException
     *             If this configuration is invalid.
     */
    @Test(expectedExceptions = ConfigException.class,
            expectedExceptionsMessageRegExp = "Invalid data configuration")
    public void testIsValidDSDoesNotAllowNullDataConfiguration() throws ConfigException {
        final Model ds = new DataStoreModel();
        ds.setDataConfiguration(null);
        ds.validate();
    }

    /**
     * Configure a DS - import LDIF data with no path configured for the import LDIF.
     *
     * @throws ConfigException
     *             If this configuration is invalid.
     */
    @Test(expectedExceptions = ConfigException.class,
            expectedExceptionsMessageRegExp = "Invalid import ldif file.")
    public void testIsValidDSImportLDIFDoesNotAllowNullImportLDIFPath() throws ConfigException {
        final Model ds = new DataStoreModel();
        ds.getDataConfiguration().setType(DataConfiguration.Type.IMPORT_LDIF);

        ds.validate();
    }

    /**
     * Configure a DS - A password must be set for the root DN (password or password file).
     *
     * @throws ConfigException
     *             If this configuration is invalid.
     */
    @Test(expectedExceptions = ConfigException.class,
            expectedExceptionsMessageRegExp = "A password must be set for the root DN.")
    public void testIsValidDSDoesNotAllowNullPassword() throws ConfigException {
        final Model ds = new DataStoreModel();

        ds.validate();
    }

    /**
     * Configure a DS - A password must be set for the root DN (password or password file).
     *
     * @throws ConfigException
     *             If this configuration is invalid.
     */
    @Test(expectedExceptions = ConfigException.class,
            expectedExceptionsMessageRegExp = "A password must be set for the root DN.")
    public void testIsValidDSDoesNotAllowNullPasswordFile() throws ConfigException {
        final Model ds = new DataStoreModel();
        ds.getListenerSettings().setPasswordFile(null);
        ds.validate();
    }

    /**
     * Configure a DS - A password must be set for the root DN (password or password file).
     *
     * @throws ConfigException
     *             If this configuration is invalid.
     * @throws IOException
     *             If an error occurred when the temporary file is created.
     */
    @Test
    public void testIsValidDSAllowsPasswordFile() throws ConfigException, IOException {
        final Model ds = new DataStoreModel();
        File passwordFile = null;

        try {
            passwordFile = File.createTempFile("passwordFile", ".pwd");
            ds.getListenerSettings().setPasswordFile(passwordFile);
        } catch (IOException e) {
            throw e;
        } finally {
            if (passwordFile != null) {
                passwordFile.delete();
            }
        }

        ds.validate();
    }

    @Test
    public void testCreateDSAllowsNullCertificate() throws ConfigException {
        final Model ds = new DataStoreModel();
        ds.getListenerSettings().setCertificate(null);
        assertFalse(ds.isSecure());
    }

    @Test
    public void testCreateSecureDS() throws ConfigException {
        final Model ds = new DataStoreModel();
        final Certificate cert = new Certificate();
        cert.setType(CertificateType.SELF_SIGNED);
        ds.getListenerSettings().setCertificate(cert);

        assertTrue(ds.isSecure());
    }


    /**
     * Configure a DS - replication enabled with null replication configuration should fail.
     *
     * @throws ConfigException
     *             If this configuration is invalid.
     */
    @Test(expectedExceptions = ConfigException.class,
            expectedExceptionsMessageRegExp = "No replication configuration found")
    public void testCreateDSFirstInTopologyDoesNotAllowEmptyReplicationConfiguration() throws ConfigException {
        final Model ds = new DataStoreModel();
        ds.setType(Type.FIRST_IN_TOPOLOGY);
        assertTrue(ds.isFirstInTopology());
        assertThat(ds.getReplicationConfiguration()).isNull();

        ds.validate();
    }

    /**
     * The replication configuration doesn't set a valid administrator name.
     *
     * @throws NullPointerException
     *             If the administrator's name is null.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testCreateDefaultDSExistingTopologyDoesNotAllowNullAdministrator() throws ConfigException {
        final Model ds = new DataStoreModel();
        ReplicationConfiguration rConfig = new ReplicationConfiguration();
        ds.setReplicationConfiguration(rConfig);
        ds.setType(Type.IN_EXISTING_TOPOLOGY);
        assertTrue(ds.isPartOfReplicationTopology());
        assertThat(ds.getReplicationConfiguration()).isNotNull();

        ds.validate();
    }

    /**
     * The replication configuration doesn't allow null password.
     *
     * @throws NullPointerException
     *             If the password is null.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testCreateDefaultDSExistingTopologyDoesNotAllowNullPassword() throws ConfigException {
        final Model ds = new DataStoreModel();
        final ReplicationConfiguration rConfig = new ReplicationConfiguration();
        rConfig.setAdministrator("admin");

        ds.setReplicationConfiguration(rConfig);
        ds.setInExistingTopology();
        assertTrue(ds.isPartOfReplicationTopology());
        assertThat(ds.getReplicationConfiguration()).isNotNull();

        ds.validate();
    }


    /**
     * The replication configuration doesn't set a valid administrator name.
     *
     * @throws NullPointerException
     *             If the administrator's name is null.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testCreateDefaultDSExistingTopologyDoesNotAllowNullGlobalAdministrator() throws ConfigException {
        final Model ds = new DataStoreModel();
        ReplicationConfiguration rConfig = new ReplicationConfiguration();
        rConfig.setAdministrator("admin");
        rConfig.setPassword("password".toCharArray());
        ds.setReplicationConfiguration(rConfig);
        ds.setType(Type.IN_EXISTING_TOPOLOGY);
        assertTrue(ds.isPartOfReplicationTopology());
        assertThat(ds.getReplicationConfiguration()).isNotNull();

        ds.validate();
    }

    /**
     * The replication configuration doesn't allow null password for the global administrator.
     *
     * @throws NullPointerException
     *             If the password is null.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testCreateDefaultDSExistingTopologyDoesNotAllowNullPasswordForGlobalAdministrator()
            throws ConfigException {
        final Model ds = new DataStoreModel();
        final ReplicationConfiguration rConfig = new ReplicationConfiguration();
        rConfig.setAdministrator("admin");
        rConfig.setPassword("password".toCharArray());
        rConfig.setGlobalAdministratorUID("GlobalAdmin");
        ds.setReplicationConfiguration(rConfig);
        ds.setInExistingTopology();
        assertTrue(ds.isPartOfReplicationTopology());
        assertThat(ds.getReplicationConfiguration()).isNotNull();

        ds.validate();
    }

    /**
     * The replication configuration doesn't allow null suffixes.
     *
     * @throws ConfigException
     *             If the password is null.
     */
    @Test(expectedExceptions = ConfigException.class)
    public void testCreateDefaultDSExistingTopologyDoesNotAllowNullSuffixes() throws ConfigException {
        final Model ds = new DataStoreModel();

        final ReplicationConfiguration rConfig = new ReplicationConfiguration();
        rConfig.setAdministrator("admin");
        rConfig.setPassword("password".toCharArray());
        rConfig.setGlobalAdministratorUID("GlobalAdmin");
        rConfig.setGlobalAdministratorPassword("password2".toCharArray());

        ds.setReplicationConfiguration(rConfig);
        ds.setInExistingTopology();
        assertTrue(ds.isPartOfReplicationTopology());
        assertThat(ds.getReplicationConfiguration()).isNotNull();

        ds.validate();
    }

    /**
     * The replication configuration doesn't allow empty suffixes.
     *
     * @throws ConfigException
     *             If the password is null.
     */
    @Test(expectedExceptions = ConfigException.class)
    public void testCreateDefaultDSExistingTopologyDoesNotAllowEmptySuffixes() throws ConfigException {
        final Model ds = new DataStoreModel();

        final ReplicationConfiguration rConfig = new ReplicationConfiguration();
        rConfig.setAdministrator("admin");
        rConfig.setPassword("password".toCharArray());

        rConfig.setGlobalAdministratorUID("GlobalAdmin");
        rConfig.setGlobalAdministratorPassword("password2".toCharArray());
        rConfig.setSuffixes(new LinkedList<String>());

        ds.setReplicationConfiguration(rConfig);
        ds.setInExistingTopology();
        assertTrue(ds.isPartOfReplicationTopology());
        assertThat(ds.getReplicationConfiguration()).isNotNull();

        ds.validate();
    }

    /**
     * Creates a valid first DS in topology.
     *
     * @throws ConfigException
     *             If a configuration exception occurs.
     */
    @Test
    public void testCreateDSFirstInTopology() throws ConfigException {
        final Model ds = new DataStoreModel();
        ds.getListenerSettings().setPassword("password");
        ds.setFirstInTopology();
        assertTrue(ds.isFirstInTopology());
        assertThat(ds.getReplicationConfiguration()).isNull();

        // Sets the replication configuration
        final ReplicationConfiguration rconf = new ReplicationConfiguration();
        rconf.setAdministrator("admin");
        rconf.setPassword("password".toCharArray());
        ds.setReplicationConfiguration(rconf);

        assertThat(ds.getReplicationConfiguration().getReplicationPort()).isEqualTo(
                ReplicationConfiguration.DEFAULT_REPLICATION_PORT);
        assertFalse(ds.getReplicationConfiguration().isSecure());

        ds.validate();
    }

    /**
     * Demonstrates how to set the license for a DS.
     *
     * @throws ConfigException
     *             If a configuration exception occurs.
     */
    @Test
    public void testCreateDefaultDSWithLicense() throws ConfigException {
        final Model ds = new DataStoreModel();
        final String license = "This is a CDDL License";
        ds.setLicense(license);
        ds.getListenerSettings().setPassword("password");
        assertTrue(ds.hasLicense());
        ds.validate();
    }
}
