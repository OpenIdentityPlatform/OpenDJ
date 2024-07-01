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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.forgerock.opendj.config.server.ServerManagedObject;
import org.forgerock.opendj.config.server.ServerManagementContext;
import org.forgerock.opendj.config.server.spi.ConfigurationRepository;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldif.LDIF;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.testng.annotations.Test;

/** An abstract class that all admin unit tests should extend. */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "admin" }, singleThreaded = true)
public abstract class AdminTestCase extends ConfigTestCase {

    /** Create a mock of ConfigurationRepository with provided entries registered. */
    protected final ConfigurationRepository createConfigRepositoryWithEntries(final Entry...entries) throws Exception {
        ConfigurationRepository configRepository = mock(ConfigurationRepository.class);
        for (Entry entry : entries) {
            when(configRepository.getEntry(entry.getName())).thenReturn(entry);
            when(configRepository.hasEntry(entry.getName())).thenReturn(true);
        }
        return configRepository;
    }

    /** Returns the name used for the provided entry (the value of the cn attribute). */
    protected final String entryName(final Entry entry) {
        return entry.getName().rdn().getFirstAVA().getAttributeValue().toString();
    }

    /** Gets the named parent configuration corresponding to the provided entry. */
    protected final TestParentCfg getParentCfg(final Entry entry, final ServerManagementContext serverContext)
            throws Exception {
        return getParentCfg(entryName(entry), serverContext);
    }

    /** Gets the named parent configuration corresponding to provided name. */
    protected final TestParentCfg getParentCfg(final String name, final ServerManagementContext serverContext)
            throws Exception {
        ServerManagedObject<RootCfg> root = serverContext.getRootConfigurationManagedObject();
        return root.getChild(TestCfg.getTestOneToManyParentRelationDefinition(), name).getConfiguration();
    }

    protected static final Entry CONFIG_ENTRY = LDIF.makeEntry(
        "dn: cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-branch",
        "cn: config");

    protected static final Entry CONN_HANDLER_ENTRY = LDIF.makeEntry(
        "dn: cn=Connection Handlers,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-branch",
        "cn: Connection Handlers");

    protected static final Entry LDAP_CONN_HANDLER_ENTRY = LDIF.makeEntry(
        "dn: cn=LDAP Connection Handler,cn=Connection Handlers,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-connection-handler",
        "objectClass: ds-cfg-ldap-connection-handler",
        "cn: LDAP Connection Handler",
        "ds-cfg-java-class: org.opends.server.protocols.ldap.LDAPConnectionHandler",
        "ds-cfg-enabled: true",
        "ds-cfg-listen-address: 0.0.0.0", "ds-cfg-listen-port: 389");

    protected static final Entry LDAPS_CONN_HANDLER_ENTRY = LDIF.makeEntry(
        "dn: cn=LDAPS Connection Handler,cn=Connection Handlers,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-connection-handler",
        "objectClass: ds-cfg-ldap-connection-handler",
        "cn: LDAPS Connection Handler",
        "ds-cfg-java-class: org.opends.server.protocols.ldap.LDAPConnectionHandler",
        "ds-cfg-enabled: false",
        "ds-cfg-listen-address: 0.0.0.0",
        "ds-cfg-listen-port: 636",
        "ds-cfg-use-ssl: true",
        "ds-cfg-ssl-client-auth-policy: optional",
        "ds-cfg-ssl-cert-nickname: server-cert",
        "ds-cfg-key-manager-provider: cn=JKS,cn=Key Manager Providers,cn=config",
        "ds-cfg-trust-manager-provider: cn=JKS,cn=Trust Manager Providers,cn=config");

    protected static final Entry JMX_CONN_HANDLER_ENTRY = LDIF.makeEntry(
        "dn: cn=JMX Connection Handler,cn=Connection Handlers,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-connection-handler",
        "objectClass: ds-cfg-jmx-connection-handler",
        "cn: JMX Connection Handler",
        "ds-cfg-java-class: org.opends.server.protocols.jmx.JmxConnectionHandler",
        "ds-cfg-enabled: false",
        "ds-cfg-listen-port: 1689");

}
