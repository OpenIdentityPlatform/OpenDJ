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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.admin;

import static org.mockito.Mockito.*;

import org.forgerock.opendj.admin.server.RootCfg;
import org.forgerock.opendj.config.ConfigTestCase;
import org.forgerock.opendj.ldap.Entry;
import org.opends.server.admin.server.ServerManagedObject;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.config.ConfigurationRepository;
import org.testng.annotations.Test;

/**
 * An abstract class that all admin unit tests should extend.
 */
@Test(groups = { "precommit", "admin" }, singleThreaded = true)
public abstract class AdminTestCase extends ConfigTestCase {

    /**
     * Create a mock of ConfigurationRepository with provided entries registered.
     */
    protected final ConfigurationRepository createConfigRepositoryWithEntries(final Entry...entries) throws Exception {
        ConfigurationRepository configRepository = mock(ConfigurationRepository.class);
        for (Entry entry : entries) {
            when(configRepository.getEntry(entry.getName())).thenReturn(entry);
            when(configRepository.hasEntry(entry.getName())).thenReturn(true);
        }
        return configRepository;
    }

    /** Returns the name used for this entry (the value of the cn attribute) */
    protected final String entryName(final Entry entry) {
        return entry.getName().rdn().getFirstAVA().getAttributeValue().toString();
    }

    /** Gets the named parent configuration corresponding to the entry */
    protected final TestParentCfg getParentCfg(final Entry entry, final ServerManagementContext serverContext)
            throws Exception {
        return getParentCfg(entryName(entry), serverContext);
    }

    /** Gets the named parent configuration corresponding to provided name. */
    protected final TestParentCfg getParentCfg(final String name, final ServerManagementContext serverContext)
            throws Exception {
        ServerManagedObject<RootCfg> root = serverContext.getRootConfigurationManagedObject();
        TestParentCfg parent = root.getChild(TestCfg.getTestOneToManyParentRelationDefinition(), name)
                .getConfiguration();
        return parent;
    }
}
