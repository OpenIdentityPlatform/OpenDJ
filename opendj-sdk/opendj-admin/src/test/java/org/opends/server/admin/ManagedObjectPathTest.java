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
package org.opends.server.admin;

import static org.testng.Assert.*;

import org.forgerock.opendj.admin.client.ConnectionHandlerCfgClient;
import org.forgerock.opendj.admin.client.GlobalCfgClient;
import org.forgerock.opendj.admin.client.LDAPConnectionHandlerCfgClient;
import org.forgerock.opendj.admin.meta.ConnectionHandlerCfgDefn;
import org.forgerock.opendj.admin.meta.GlobalCfgDefn;
import org.forgerock.opendj.admin.meta.LDAPConnectionHandlerCfgDefn;
import org.forgerock.opendj.admin.meta.ReplicationDomainCfgDefn;
import org.forgerock.opendj.admin.meta.ReplicationSynchronizationProviderCfgDefn;
import org.forgerock.opendj.admin.meta.RootCfgDefn;
import org.forgerock.opendj.admin.server.ConnectionHandlerCfg;
import org.forgerock.opendj.admin.server.GlobalCfg;
import org.forgerock.opendj.admin.server.LDAPConnectionHandlerCfg;
import org.forgerock.opendj.config.ConfigTestCase;
import org.forgerock.opendj.ldap.DN;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ManagedObjectPathTest extends ConfigTestCase {

    @BeforeClass
    public void setUp() throws Exception {
        disableClassValidationForProperties();
    }

    @Test
    public void testEmptyPathIsEmpty() {
        ManagedObjectPath<?, ?> path = ManagedObjectPath.emptyPath();
        assertTrue(path.isEmpty());
    }

    @Test
    public void testEmptyPathHasZeroElements() {
        ManagedObjectPath<?, ?> path = ManagedObjectPath.emptyPath();
        assertEquals(path.size(), 0);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testEmptyPathHasNoParent() {
        ManagedObjectPath<?, ?> path = ManagedObjectPath.emptyPath();
        path.parent();
    }

    @Test
    public void testEmptyPathIsRootConfiguration() {
        ManagedObjectPath<?, ?> path = ManagedObjectPath.emptyPath();
        assertEquals(path.getManagedObjectDefinition(), RootCfgDefn.getInstance());
    }

    @Test
    public void testEmptyPathHasNoRelation() {
        ManagedObjectPath<?, ?> path = ManagedObjectPath.emptyPath();
        assertEquals(path.getRelationDefinition(), null);
    }

    @Test
    public void testEmptyPathHasNoName() {
        ManagedObjectPath<?, ?> path = ManagedObjectPath.emptyPath();
        assertNull(path.getName());
    }

    @Test
    public void testEmptyPathString() {
        ManagedObjectPath<?, ?> path = ManagedObjectPath.emptyPath();
        assertEquals(path.toString(), "/");
    }

    @Test
    public void testEmptyPathDecode() {
        ManagedObjectPath<?, ?> path = ManagedObjectPath.valueOf("/");
        assertEquals(path, ManagedObjectPath.emptyPath());
    }

    @Test
    public void testSingletonChild() {
        ManagedObjectPath<?, ?> path = ManagedObjectPath.emptyPath();
        ManagedObjectPath<GlobalCfgClient, GlobalCfg> child = path.child(RootCfgDefn.getInstance()
                .getGlobalConfigurationRelationDefinition());

        assertFalse(child.isEmpty());
        assertEquals(child.size(), 1);
        assertEquals(child.parent(), path);
        assertNull(child.getName());
        assertEquals(child.getManagedObjectDefinition(), GlobalCfgDefn.getInstance());
        assertEquals(child.getRelationDefinition(), RootCfgDefn.getInstance()
                .getGlobalConfigurationRelationDefinition());
        assertEquals(child.toString(), "/relation=global-configuration");
        assertEquals(child, ManagedObjectPath.valueOf("/relation=global-configuration"));
    }

    @Test
    public void testInstantiableChild() {
        ManagedObjectPath<?, ?> path = ManagedObjectPath.emptyPath();
        ManagedObjectPath<ConnectionHandlerCfgClient, ConnectionHandlerCfg> child = path.child(RootCfgDefn
                .getInstance().getConnectionHandlersRelationDefinition(), "LDAP connection handler");

        assertFalse(child.isEmpty());
        assertEquals(child.size(), 1);
        assertEquals(child.parent(), path);
        assertEquals(child.getName(), "LDAP connection handler");
        assertEquals(child.getManagedObjectDefinition(), ConnectionHandlerCfgDefn.getInstance());
        assertEquals(child.getRelationDefinition(), RootCfgDefn.getInstance()
                .getConnectionHandlersRelationDefinition());
        assertEquals(child.toString(), "/relation=connection-handler+name=LDAP connection handler");
        assertEquals(child, ManagedObjectPath.valueOf("/relation=connection-handler+name=LDAP connection handler"));
    }

    @Test
    public void testInstantiableChildWithSubtype() {
        ManagedObjectPath<?, ?> path = ManagedObjectPath.emptyPath();
        ManagedObjectPath<LDAPConnectionHandlerCfgClient, LDAPConnectionHandlerCfg> child = path.child(RootCfgDefn
                .getInstance().getConnectionHandlersRelationDefinition(), LDAPConnectionHandlerCfgDefn.getInstance(),
                "LDAP connection handler");

        assertFalse(child.isEmpty());
        assertEquals(child.size(), 1);
        assertEquals(child.parent(), path);
        assertEquals(child.getManagedObjectDefinition(), LDAPConnectionHandlerCfgDefn.getInstance());
        assertEquals(child.getRelationDefinition(), RootCfgDefn.getInstance()
                .getConnectionHandlersRelationDefinition());
        String childAsString =
                "/relation=connection-handler+type=ldap-connection-handler+name=LDAP connection handler";
        assertEquals(child.toString(), childAsString);
        assertEquals(child, ManagedObjectPath.valueOf(childAsString));
    }

    @Test
    public void testInstantiableChildMultipleLevels() {
        ManagedObjectPath<?, ?> root = ManagedObjectPath.emptyPath();
        ManagedObjectPath<?, ?> mmr = root.child(RootCfgDefn.getInstance()
                .getSynchronizationProvidersRelationDefinition(), ReplicationSynchronizationProviderCfgDefn
                .getInstance(), "MMR");
        ManagedObjectPath<?, ?> domain = mmr.child(ReplicationSynchronizationProviderCfgDefn.getInstance()
                .getReplicationDomainsRelationDefinition(), "Domain");
        assertFalse(domain.isEmpty());
        assertEquals(domain.size(), 2);
        assertEquals(domain.parent(), mmr);
        assertEquals(domain.parent(2), root);
        assertEquals(domain.getManagedObjectDefinition(), ReplicationDomainCfgDefn.getInstance());
        assertEquals(domain.getRelationDefinition(), ReplicationSynchronizationProviderCfgDefn.getInstance()
                .getReplicationDomainsRelationDefinition());
        String domainAsString = "/relation=synchronization-provider+type=replication-synchronization-provider"
                + "+name=MMR/relation=replication-domain+name=Domain";
        assertEquals(domain.toString(), domainAsString);
        assertEquals(domain, ManagedObjectPath.valueOf(domainAsString));
    }

    @Test
    public void testMatchesAndEqualsBehavior() {
        ManagedObjectPath<?, ?> path = ManagedObjectPath.emptyPath();

        ManagedObjectPath<ConnectionHandlerCfgClient, ConnectionHandlerCfg> child1 = path.child(RootCfgDefn
                .getInstance().getConnectionHandlersRelationDefinition(), "LDAP connection handler");

        ManagedObjectPath<LDAPConnectionHandlerCfgClient, LDAPConnectionHandlerCfg> child2 = path.child(RootCfgDefn
                .getInstance().getConnectionHandlersRelationDefinition(), LDAPConnectionHandlerCfgDefn.getInstance(),
                "LDAP connection handler");

        ManagedObjectPath<LDAPConnectionHandlerCfgClient, LDAPConnectionHandlerCfg> child3 = path.child(RootCfgDefn
                .getInstance().getConnectionHandlersRelationDefinition(), LDAPConnectionHandlerCfgDefn.getInstance(),
                "Another LDAP connection handler");

        // child 1 and child2 matches each other
        assertTrue(child1.matches(child1));
        assertTrue(child2.matches(child2));
        assertTrue(child1.matches(child2));
        assertTrue(child2.matches(child1));

        // child 1 and child2 are not equal to each other
        assertTrue(child1.equals(child1));
        assertTrue(child2.equals(child2));
        assertFalse(child1.equals(child2));
        assertFalse(child2.equals(child1));

        // child 1/2 does not match nor equals child3
        assertFalse(child1.matches(child3));
        assertFalse(child2.matches(child3));
        assertFalse(child3.matches(child1));
        assertFalse(child3.matches(child2));

        assertFalse(child1.equals(child3));
        assertFalse(child2.equals(child3));
        assertFalse(child3.equals(child1));
        assertFalse(child3.equals(child2));
    }

    @Test
    public void testToDN() throws Exception {
        ManagedObjectPath<?, ?> path = ManagedObjectPath.emptyPath();

        ManagedObjectPath<ConnectionHandlerCfgClient, ConnectionHandlerCfg> child1 = path.child(RootCfgDefn
                .getInstance().getConnectionHandlersRelationDefinition(), "LDAP connection handler");

        ManagedObjectPath<LDAPConnectionHandlerCfgClient, LDAPConnectionHandlerCfg> child2 = path.child(RootCfgDefn
                .getInstance().getConnectionHandlersRelationDefinition(), LDAPConnectionHandlerCfgDefn.getInstance(),
                "LDAP connection handler");

        ManagedObjectPath<LDAPConnectionHandlerCfgClient, LDAPConnectionHandlerCfg> child3 = path.child(RootCfgDefn
                .getInstance().getConnectionHandlersRelationDefinition(), LDAPConnectionHandlerCfgDefn.getInstance(),
                "Another LDAP connection handler");

        DN expectedEmpty = DN.rootDN();
        DN expectedChild1 = DN.valueOf("cn=LDAP connection handler,cn=connection handlers,cn=config");
        DN expectedChild2 = DN.valueOf("cn=LDAP connection handler,cn=connection handlers,cn=config");
        DN expectedChild3 = DN.valueOf("cn=Another LDAP connection handler,cn=connection handlers,cn=config");

        assertEquals(path.toDN(), expectedEmpty);
        assertEquals(child1.toDN(), expectedChild1);
        assertEquals(child2.toDN(), expectedChild2);
        assertEquals(child3.toDN(), expectedChild3);
    }
}
