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
 * Copyright 2007-2008 Sun Microsystems, Inc.
 * Portions copyright 2014-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config.client.ldap;

import static org.fest.assertions.Assertions.assertThat;
import static org.forgerock.opendj.ldap.Connections.newInternalConnection;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.opendj.config.AdminTestCase;
import org.forgerock.opendj.config.LDAPProfile;
import org.forgerock.opendj.config.PropertyException;
import org.forgerock.opendj.config.TestCfg;
import org.forgerock.opendj.config.TestChildCfgClient;
import org.forgerock.opendj.config.TestChildCfgDefn;
import org.forgerock.opendj.config.TestParentCfgClient;
import org.forgerock.opendj.config.client.ManagedObject;
import org.forgerock.opendj.config.client.ManagedObjectDecodingException;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.MemoryBackend;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.opendj.server.config.client.RootCfgClient;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
@Test(singleThreaded = true)
public class AggregationClientTest extends AdminTestCase {

    /** Test LDIF. */
    private static final String[] TEST_LDIF = new String[] {
        // @formatter:off
        // Base entries.
        "dn:",
        "objectclass: top",
        "objectclass: ds-cfg-branch",
        "",
        "dn: cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-branch",
        "cn: config",
        "",
        "dn: cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-branch",
        "cn: test-parents",
        "",
        // Parent 1 - uses default values for
        // optional-multi-valued-dn-property.
        "dn: cn=test parent 1,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-test-parent-dummy",
        "cn: test parent 1",
        "ds-cfg-enabled: true",
        "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
        "ds-cfg-attribute-type: description",
        "",
        // Child base entry.
        "dn:cn=test children,cn=test parent 1,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-branch",
        "cn: multiple children",
        "",
        // Child 1 has no references.
        "dn: cn=test child 1,cn=test children,cn=test parent 1,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-test-child-dummy",
        "cn: test child 1",
        "ds-cfg-enabled: true",
        "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
        "ds-cfg-attribute-type: description",
        "",
        // Child 2 has a single valid reference.
        "dn: cn=test child 2,cn=test children,cn=test parent 1,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-test-child-dummy",
        "cn: test child 2",
        "ds-cfg-enabled: true",
        "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
        "ds-cfg-attribute-type: description",
        "ds-cfg-rotation-policy: cn=LDAP Connection Handler, cn=connection handlers, cn=config",
        "",
        // Child 3 has a multiple valid references.
        "dn: cn=test child 3,cn=test children,cn=test parent 1,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-test-child-dummy",
        "cn: test child 3",
        "ds-cfg-enabled: true",
        "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
        "ds-cfg-attribute-type: description",
        "ds-cfg-rotation-policy: cn=LDAP Connection Handler, cn=connection handlers, cn=config",
        "ds-cfg-rotation-policy: cn=LDAPS Connection Handler, cn=connection handlers, cn=config",
        "",
        // Child 4 has a single bad reference.
        "dn: cn=test child 4,cn=test children,cn=test parent 1,cn=test parents,cn=config", "objectclass: top",
        "objectclass: ds-cfg-test-child-dummy", "cn: test child 4", "ds-cfg-enabled: true",
        "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
        "ds-cfg-attribute-type: description",
        "ds-cfg-rotation-policy: cn=LDAP Connection Handler, cn=bad rdn, cn=config",
        "",
        "dn: cn=Connection Handlers,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-branch",
        "cn: Connection Handlers",
        "",
        "dn: cn=LDAP Connection Handler,cn=Connection Handlers,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-connection-handler",
        "objectClass: ds-cfg-ldap-connection-handler",
        "cn: LDAP Connection Handler",
        "ds-cfg-java-class: org.opends.server.protocols.ldap.LDAPConnectionHandler",
        "ds-cfg-enabled: true",
        "ds-cfg-listen-address: 0.0.0.0", "ds-cfg-listen-port: 389",
        "",
        "dn: cn=HTTP Connection Handler,cn=Connection Handlers,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-connection-handler",
        "objectClass: ds-cfg-http-connection-handler",
        "cn: HTTP Connection Handler",
        "ds-cfg-java-class: org.opends.server.protocols.http.HTTPConnectionHandler",
        "ds-cfg-enabled: false",
        "ds-cfg-listen-address: 0.0.0.0",
        "ds-cfg-listen-port: 8080",
        "",
        "dn: cn=JMX Connection Handler,cn=Connection Handlers,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-connection-handler",
        "objectClass: ds-cfg-jmx-connection-handler",
        "cn: JMX Connection Handler",
        "ds-cfg-java-class: org.opends.server.protocols.jmx.JmxConnectionHandler",
        "ds-cfg-enabled: false",
        "ds-cfg-listen-port: 1689",
        "" };
    // @formatter:on

    @BeforeClass
    public void setUp() throws Exception {
        TestCfg.setUp();
    }

    /** Tears down test environment. */
    @AfterClass
    public void tearDown() {
        TestCfg.cleanup();
    }

    /**
     * Tests that aggregation contains no values when it contains does not
     * contain any DN attribute values.
     *
     * @throws Exception
     *             If the test unexpectedly fails.
     */
    @Test
    public void testAggregationEmpty() throws Exception {
        MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
        Connection c = newInternalConnection(backend);
        ManagementContext ctx =
                LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
        TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
        TestChildCfgClient child = parent.getTestChild("test child 1");
        assertSetEquals(child.getAggregationProperty(), new String[0]);
    }

    /**
     * Tests that aggregation contains single valid value when it contains a
     * single valid DN attribute values.
     *
     * @throws Exception
     *             If the test unexpectedly fails.
     */
    @Test
    public void testAggregationSingle() throws Exception {
        MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
        Connection c = newInternalConnection(backend);
        ManagementContext ctx =
                LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
        TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
        TestChildCfgClient child = parent.getTestChild("test child 2");

        // Test normalization.
        assertSetEquals(child.getAggregationProperty(), "LDAP Connection Handler");
        assertSetEquals(child.getAggregationProperty(), "  LDAP   Connection  Handler ");
        assertSetEquals(child.getAggregationProperty(), "  ldap connection HANDLER ");
    }

    /**
     * Tests that aggregation contains multiple valid values when it contains a
     * multiple valid DN attribute values.
     *
     * @throws Exception
     *             If the test unexpectedly fails.
     */
    @Test
    public void testAggregationMultiple() throws Exception {
        MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
        Connection c = newInternalConnection(backend);
        ManagementContext ctx =
                LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
        TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
        TestChildCfgClient child = parent.getTestChild("test child 3");
        assertSetEquals(child.getAggregationProperty(), "LDAPS Connection Handler",
                "LDAP Connection Handler");
    }

    /**
     * Tests that aggregation is rejected when the LDAP DN contains a valid RDN
     * but an invalid parent DN.
     *
     * @throws Exception
     *             If the test unexpectedly fails.
     */
    @Test
    public void testAggregationBadBaseDN() throws Exception {
        MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
        Connection c = newInternalConnection(backend);
        ManagementContext ctx =
                LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
        TestParentCfgClient parent = getTestParent(ctx, "test parent 1");

        try {
            parent.getTestChild("test child 4");
            Assert.fail("Unexpectedly retrieved test child 4"
                    + " when it had a bad aggregation value");
        } catch (ManagedObjectDecodingException e) {
            Collection<PropertyException> causes = e.getCauses();
            Assert.assertEquals(causes.size(), 1);

            Throwable cause = causes.iterator().next();
            if (cause instanceof PropertyException) {
                PropertyException pe = (PropertyException) cause;
                Assert.assertEquals(pe.getPropertyDefinition(), TestChildCfgDefn.getInstance()
                        .getAggregationPropertyPropertyDefinition());
            } else {
                // Got an unexpected cause.
                throw e;
            }
        }
    }

    /**
     * Tests creation of a child managed object with a single reference.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testCreateChildManagedObject() throws Exception {
        MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
        Connection c = newInternalConnection(backend);
        ManagementContext ctx =
                LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
        TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
        TestChildCfgClient child =
                parent.createTestChild(TestChildCfgDefn.getInstance(), "test child new", null);
        child.setMandatoryBooleanProperty(true);
        child.setMandatoryReadOnlyAttributeTypeProperty(Schema.getDefaultSchema().getAttributeType(
                "description"));
        child.setAggregationProperty(Collections.singleton("LDAP Connection Handler"));
        child.commit();

        String dn = "cn=test child new,cn=test children,cn=test parent 1,cn=test parents,cn=config";
        assertThat(backend.get(dn)).isEqualTo(new LinkedHashMapEntry(
                "dn: " + dn,
                "cn: test child new",
                "objectClass: top",
                "objectClass: ds-cfg-test-child-dummy",
                "ds-cfg-enabled: true",
                "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
                "ds-cfg-attribute-type: description",
                "ds-cfg-rotation-policy: cn=LDAP Connection Handler,cn=connection handlers,cn=config"));
    }

    /**
     * Tests modification of a child managed object so that it has a different
     * reference.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testModifyChildManagedObject() throws Exception {
        MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
        Connection c = newInternalConnection(backend);
        ManagementContext ctx =
                LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
        TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
        TestChildCfgClient child = parent.getTestChild("test child 2");
        child.setAggregationProperty(Arrays.asList("JMX Connection Handler",
                "HTTP Connection Handler"));
        child.commit();

        String dn = "cn=test child 2,cn=test children,cn=test parent 1,cn=test parents,cn=config";
        assertThat(backend.get(dn).parseAttribute("ds-cfg-rotation-policy").asSetOfString())
                .containsOnly("cn=HTTP Connection Handler,cn=connection handlers,cn=config",
                        "cn=JMX Connection Handler,cn=connection handlers,cn=config");
    }

    /** Retrieve the named test parent managed object. */
    private TestParentCfgClient getTestParent(ManagementContext context, String name)
            throws Exception {
        ManagedObject<RootCfgClient> root = context.getRootConfigurationManagedObject();
        return root.getChild(TestCfg.getTestOneToManyParentRelationDefinition(), name)
                .getConfiguration();
    }

    /** Asserts that the actual set of DNs contains the expected values. */
    private void assertSetEquals(SortedSet<String> actual, String... expected) {
        SortedSet<String> values = new TreeSet<>(
            TestChildCfgDefn.getInstance().getAggregationPropertyPropertyDefinition());
        if (expected != null) {
            for (String value : expected) {
                values.add(value);
            }
        }
        Assert.assertEquals((Object) actual, (Object) values);
    }

}
