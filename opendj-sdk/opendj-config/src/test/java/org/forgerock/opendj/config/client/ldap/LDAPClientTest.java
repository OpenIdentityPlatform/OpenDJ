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
 *      Portions copyright 2014 ForgeRock AS.
 */
package org.forgerock.opendj.config.client.ldap;

import static org.fest.assertions.Assertions.assertThat;
import static org.forgerock.opendj.ldap.Connections.newInternalConnection;
import static org.forgerock.opendj.ldap.LdapException.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.opendj.config.AdminTestCase;
import org.forgerock.opendj.config.Constraint;
import org.forgerock.opendj.config.LDAPProfile;
import org.forgerock.opendj.config.ManagedObjectAlreadyExistsException;
import org.forgerock.opendj.config.ManagedObjectNotFoundException;
import org.forgerock.opendj.config.TestCfg;
import org.forgerock.opendj.config.TestChildCfgClient;
import org.forgerock.opendj.config.TestChildCfgDefn;
import org.forgerock.opendj.config.TestParentCfgClient;
import org.forgerock.opendj.config.TestParentCfgDefn;
import org.forgerock.opendj.config.client.ManagedObject;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.config.client.OperationRejectedException;
import org.forgerock.opendj.ldap.AbstractConnectionWrapper;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.MemoryBackend;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.opendj.server.config.client.RootCfgClient;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
@Test(singleThreaded = true)
public final class LDAPClientTest extends AdminTestCase {

    // @Checkstyle:off
    private static final String[] TEST_LDIF = new String[] {
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
        // Parent 2 - overrides default values for
        // optional-multi-valued-dn-property.
        "dn: cn=test parent 2,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-test-parent-dummy",
        "cn: test parent 2",
        "ds-cfg-enabled: true",
        "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
        "ds-cfg-attribute-type: description",
        "ds-cfg-base-dn: dc=default value p2v1,dc=com",
        "ds-cfg-base-dn: dc=default value p2v2,dc=com",
        "",
        // Parent 3 - overrides default values for
        // optional-multi-valued-dn-property.
        "dn: cn=test parent 3,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-test-parent-dummy",
        "cn: test parent 3",
        "ds-cfg-enabled: true",
        "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
        "ds-cfg-attribute-type: description",
        "ds-cfg-base-dn: dc=default value p3v1,dc=com",
        "ds-cfg-base-dn: dc=default value p3v2,dc=com",
        "",
        // Child base entries.
        "dn:cn=test children,cn=test parent 1,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-branch",
        "cn: multiple children",
        "",
        "dn:cn=test children,cn=test parent 2,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-branch",
        "cn: multiple children",
        "",
        // Child 1 inherits defaults for both
        // optional-multi-valued-dn-property1 and
        // optional-multi-valued-dn-property2.
        "dn: cn=test child 1,cn=test children,cn=test parent 1,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-test-child-dummy",
        "cn: test child 1",
        "ds-cfg-enabled: true",
        "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
        "ds-cfg-attribute-type: description",
        "",
        // Child 2 inherits defaults for
        // optional-multi-valued-dn-property2.
        "dn: cn=test child 2,cn=test children,cn=test parent 1,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-test-child-dummy",
        "cn: test child 2",
        "ds-cfg-enabled: true",
        "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
        "ds-cfg-attribute-type: description",
        "ds-cfg-base-dn: dc=default value c2v1,dc=com",
        "ds-cfg-base-dn: dc=default value c2v2,dc=com",
        "",
        // Child 3 overrides defaults for
        // optional-multi-valued-dn-property1 and
        // optional-multi-valued-dn-property2.
        "dn: cn=test child 3,cn=test children,cn=test parent 1,cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-test-child-dummy",
        "cn: test child 3",
        "ds-cfg-enabled: true",
        "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
        "ds-cfg-attribute-type: description",
        "ds-cfg-base-dn: dc=default value c3v1,dc=com",
        "ds-cfg-base-dn: dc=default value c3v2,dc=com",
        "ds-cfg-group-dn: dc=default value c3v3,dc=com",
        "ds-cfg-group-dn: dc=default value c3v4,dc=com",
        "",
        // Child 4 inherits overridden defaults for both
        // optional-multi-valued-dn-property1 and
        // optional-multi-valued-dn-property2.
        "dn: cn=test child 1,cn=test children,cn=test parent 2,cn=test parents,cn=config",
        "objectclass: top", "objectclass: ds-cfg-test-child-dummy", "cn: test child 1",
        "ds-cfg-enabled: true",
        "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
        "ds-cfg-attribute-type: description", "", };

    // @Checkstyle:on

    /**
     * Provide valid naming exception to client API exception mappings.
     *
     * @return Returns valid naming exception to client API exception mappings.
     */
    @DataProvider(name = "createManagedObjectExceptions")
    public Object[][] createManagedObjectExceptions() {
        return new Object[][] {
            // result code corresponding to exception thrown, expected
            // exception, expected code result
            { ResultCode.PROTOCOL_ERROR, LdapException.class, ResultCode.PROTOCOL_ERROR },
            { ResultCode.UNAVAILABLE, LdapException.class, ResultCode.UNAVAILABLE },
            { ResultCode.ENTRY_ALREADY_EXISTS, ManagedObjectAlreadyExistsException.class, null },
            { ResultCode.INSUFFICIENT_ACCESS_RIGHTS, LdapException.class, ResultCode.INSUFFICIENT_ACCESS_RIGHTS },
            { ResultCode.UNWILLING_TO_PERFORM, OperationRejectedException.class, null } };
    }

    /**
     * Provide valid naming exception to client API exception mappings.
     *
     * @return Returns valid naming exception to client API exception mappings.
     */
    @DataProvider(name = "getManagedObjectExceptions")
    public Object[][] getManagedObjectExceptions() {
        return new Object[][] {
            // result code corresponding to exception thrown, expected
            // exception, expected code result
            { ResultCode.PROTOCOL_ERROR, LdapException.class, ResultCode.PROTOCOL_ERROR },
            { ResultCode.UNAVAILABLE, LdapException.class, ResultCode.UNAVAILABLE },
            { ResultCode.NO_SUCH_OBJECT, ManagedObjectNotFoundException.class, null },
            { ResultCode.INSUFFICIENT_ACCESS_RIGHTS, LdapException.class, ResultCode.INSUFFICIENT_ACCESS_RIGHTS },
            { ResultCode.UNWILLING_TO_PERFORM, LdapException.class, ResultCode.UNWILLING_TO_PERFORM } };
    }

    @BeforeClass
    public void setUp() throws Exception {
        TestCfg.setUp();
    }

    @AfterClass
    public void tearDown() {
        TestCfg.cleanup();
    }

    /**
     * Tests creation of a child managed object.
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
        child.setMandatoryReadOnlyAttributeTypeProperty(getAttributeType("description"));
        child.commit();

        String dn = "cn=test child new,cn=test children,cn=test parent 1,cn=test parents,cn=config";
        assertThat(backend.get(dn))
                .isEqualTo(
                        new LinkedHashMapEntry(
                                "dn: " + dn,
                                "cn: test child new",
                                "objectClass: top",
                                "objectClass: ds-cfg-test-child-dummy",
                                "ds-cfg-enabled: true",
                                "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
                                "ds-cfg-attribute-type: description"));
    }

    /**
     * Tests creation of a top-level managed object using fails when an
     * underlying exception occurs.
     */
    @Test(dataProvider = "createManagedObjectExceptions")
    public void testCreateManagedObjectException(final ResultCode resultCodeOfThrownException,
            Class<? extends Exception> expectedExceptionClass, ResultCode expectedCode)
            throws Exception {
        MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
        Connection c = new AbstractConnectionWrapper<Connection>(newInternalConnection(backend)) {
            @Override
            public Result add(Entry entry) throws LdapException {
                throw newLdapException(resultCodeOfThrownException);
            }
        };
        ManagementContext ctx =
                LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
        try {
            TestParentCfgClient parent = createTestParent(ctx, "test parent new");
            parent.setMandatoryBooleanProperty(true);
            parent.setMandatoryReadOnlyAttributeTypeProperty(getAttributeType("description"));
            parent.commit();
        } catch (Exception e) {
            if (expectedExceptionClass.equals(LdapException.class)) {
                assertThat(e).isInstanceOf(LdapException.class);
                assertThat(((LdapException) e).getResult().getResultCode()).isEqualTo(expectedCode);
            } else {
                assertThat(e).isInstanceOf(expectedExceptionClass);
            }
        }
    }

    /**
     * Tests creation of a top-level managed object.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testCreateTopLevelManagedObject() throws Exception {
        MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
        Connection c = newInternalConnection(backend);
        ManagementContext ctx =
                LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
        TestParentCfgClient parent = createTestParent(ctx, "test parent new");
        parent.setMandatoryBooleanProperty(true);
        parent.setMandatoryReadOnlyAttributeTypeProperty(getAttributeType("description"));
        parent.commit();

        String dn = "cn=test parent new,cn=test parents,cn=config";
        assertThat(backend.get(dn))
                .isEqualTo(
                        new LinkedHashMapEntry(
                                "dn: " + dn,
                                "cn: test parent new",
                                "objectClass: top",
                                "objectClass: ds-cfg-test-parent-dummy",
                                "ds-cfg-enabled: true",
                                "ds-cfg-java-class: org.opends.server.extensions.SomeVirtualAttributeProvider",
                                "ds-cfg-attribute-type: description"));
    }

    /**
     * Tests retrieval of a child managed object with non-default values.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testGetChildManagedObject() throws Exception {
        MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
        Connection c = newInternalConnection(backend);
        ManagementContext ctx =
                LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
        TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
        TestChildCfgClient child = parent.getTestChild("test child 3");
        Assert.assertEquals(child.isMandatoryBooleanProperty(), Boolean.TRUE);
        Assert.assertEquals(child.getMandatoryClassProperty(),
                "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
        Assert.assertEquals(child.getMandatoryReadOnlyAttributeTypeProperty(),
                getAttributeType("description"));
        assertDNSetEquals(child.getOptionalMultiValuedDNProperty1(),
                "dc=default value c3v1,dc=com", "dc=default value c3v2,dc=com");
        assertDNSetEquals(child.getOptionalMultiValuedDNProperty2(),
                "dc=default value c3v3,dc=com", "dc=default value c3v4,dc=com");
    }

    /**
     * Tests retrieval of a child managed object with default values.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testGetChildManagedObjectDefault() throws Exception {
        MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
        Connection c = newInternalConnection(backend);
        ManagementContext ctx =
                LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
        TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
        TestChildCfgClient child = parent.getTestChild("test child 1");
        Assert.assertEquals(child.isMandatoryBooleanProperty(), Boolean.TRUE);
        Assert.assertEquals(child.getMandatoryClassProperty(),
                "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
        Assert.assertEquals(child.getMandatoryReadOnlyAttributeTypeProperty(),
                getAttributeType("description"));
        assertDNSetEquals(child.getOptionalMultiValuedDNProperty1(), "dc=domain1,dc=com",
                "dc=domain2,dc=com", "dc=domain3,dc=com");
        assertDNSetEquals(child.getOptionalMultiValuedDNProperty2(), "dc=domain1,dc=com",
                "dc=domain2,dc=com", "dc=domain3,dc=com");
        Assert.assertEquals(child.isMandatoryBooleanProperty(), Boolean.TRUE);
    }

    /**
     * Tests retrieval of a top-level managed object fails when an underlying
     * LdapException occurs.
     *
     * @param expectedExceptionClass
     *            The expected client API exception class.
     */
    @Test(dataProvider = "getManagedObjectExceptions")
    public void testGetManagedObjectException(final ResultCode resultCodeOfThrownException,
            final Class<? extends Exception> expectedExceptionClass, final ResultCode expectedCode)
            throws Exception {
        MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
        Connection c = new AbstractConnectionWrapper<Connection>(newInternalConnection(backend)) {
            @Override
            public SearchResultEntry readEntry(DN name, String... attributeDescriptions) throws LdapException {
                throw newLdapException(resultCodeOfThrownException);
            }
        };
        ManagementContext ctx =
                LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
        try {
            getTestParent(ctx, "test parent 2");
        } catch (Exception e) {
            if (expectedExceptionClass.equals(LdapException.class)) {
                assertThat(e).isInstanceOf(LdapException.class);
                assertThat(((LdapException) e).getResult().getResultCode()).isEqualTo(expectedCode);
            } else {
                assertThat(e).isInstanceOf(expectedExceptionClass);
            }
        }
    }

    /**
     * Tests retrieval of a top-level managed object with non-default values.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testGetTopLevelManagedObject() throws Exception {
        MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
        Connection c = newInternalConnection(backend);
        ManagementContext ctx =
                LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
        TestParentCfgClient parent = getTestParent(ctx, "test parent 2");
        Assert.assertEquals(parent.isMandatoryBooleanProperty(), Boolean.TRUE);
        Assert.assertEquals(parent.getMandatoryClassProperty(),
                "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
        Assert.assertEquals(parent.getMandatoryReadOnlyAttributeTypeProperty(),
                getAttributeType("description"));
        assertDNSetEquals(parent.getOptionalMultiValuedDNProperty(),
                "dc=default value p2v1,dc=com", "dc=default value p2v2,dc=com");
    }

    /**
     * Tests retrieval of a top-level managed object with default values.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testGetTopLevelManagedObjectDefault() throws Exception {
        MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
        Connection c = newInternalConnection(backend);
        ManagementContext ctx =
                LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
        TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
        Assert.assertEquals(parent.isMandatoryBooleanProperty(), Boolean.TRUE);
        Assert.assertEquals(parent.getMandatoryClassProperty(),
                "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
        Assert.assertEquals(parent.getMandatoryReadOnlyAttributeTypeProperty(),
                getAttributeType("description"));
        assertDNSetEquals(parent.getOptionalMultiValuedDNProperty(), "dc=domain1,dc=com",
                "dc=domain2,dc=com", "dc=domain3,dc=com");
    }

    /**
     * Tests retrieval of relative inherited default values.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testInheritedDefaultValues1() throws Exception {
        MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
        Connection c = newInternalConnection(backend);
        ManagementContext ctx =
                LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
        TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
        TestChildCfgClient child =
                parent.createTestChild(TestChildCfgDefn.getInstance(), "test child new", null);

        // Check pre-commit values.
        Assert.assertEquals(child.isMandatoryBooleanProperty(), null);
        Assert.assertEquals(child.getMandatoryClassProperty(),
                "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
        Assert.assertEquals(child.getMandatoryReadOnlyAttributeTypeProperty(), null);
        assertDNSetEquals(child.getOptionalMultiValuedDNProperty1(), "dc=domain1,dc=com",
                "dc=domain2,dc=com", "dc=domain3,dc=com");
        assertDNSetEquals(child.getOptionalMultiValuedDNProperty2(), "dc=domain1,dc=com",
                "dc=domain2,dc=com", "dc=domain3,dc=com");

        // Check that the default values are not committed.
        child.setMandatoryBooleanProperty(true);
        child.setMandatoryReadOnlyAttributeTypeProperty(getAttributeType("description"));
        child.commit();

        String dn = "cn=test child new,cn=test children,cn=test parent 1,cn=test parents,cn=config";
        assertThat(backend.get(dn))
                .isEqualTo(
                        new LinkedHashMapEntry(
                                "dn: " + dn,
                                "cn: test child new",
                                "objectClass: top",
                                "objectClass: ds-cfg-test-child-dummy",
                                "ds-cfg-enabled: true",
                                "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
                                "ds-cfg-attribute-type: description"));
    }

    /**
     * Tests retrieval of relative inherited default values.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testInheritedDefaultValues2() throws Exception {
        MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
        Connection c = newInternalConnection(backend);
        ManagementContext ctx =
                LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
        TestParentCfgClient parent = getTestParent(ctx, "test parent 2");
        TestChildCfgClient child =
                parent.createTestChild(TestChildCfgDefn.getInstance(), "test child new", null);

        // Check pre-commit values.
        Assert.assertEquals(child.isMandatoryBooleanProperty(), null);
        Assert.assertEquals(child.getMandatoryClassProperty(),
                "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
        Assert.assertEquals(child.getMandatoryReadOnlyAttributeTypeProperty(), null);
        assertDNSetEquals(child.getOptionalMultiValuedDNProperty1(),
                "dc=default value p2v1,dc=com", "dc=default value p2v2,dc=com");
        assertDNSetEquals(child.getOptionalMultiValuedDNProperty2(),
                "dc=default value p2v1,dc=com", "dc=default value p2v2,dc=com");

        // Check that the default values are not committed.
        child.setMandatoryBooleanProperty(true);
        child.setMandatoryReadOnlyAttributeTypeProperty(getAttributeType("description"));
        child.commit();

        String dn = "cn=test child new,cn=test children,cn=test parent 2,cn=test parents,cn=config";
        assertThat(backend.get(dn))
                .isEqualTo(
                        new LinkedHashMapEntry(
                                "dn: " + dn,
                                "cn: test child new",
                                "objectClass: top",
                                "objectClass: ds-cfg-test-child-dummy",
                                "ds-cfg-enabled: true",
                                "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
                                "ds-cfg-attribute-type: description"));
    }

    /**
     * Tests listing of child managed objects.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testListChildManagedObjects() throws Exception {
        MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
        Connection c = newInternalConnection(backend);
        ManagementContext ctx =
                LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
        TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
        String[] actual = parent.listTestChildren();
        String[] expected = new String[] { "test child 1", "test child 2", "test child 3" };
        Assert.assertEqualsNoOrder(actual, expected);
    }

    /**
     * Tests listing of child managed objects when their are not any.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testListChildManagedObjectsEmpty() throws Exception {
        MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
        Connection c = newInternalConnection(backend);
        ManagementContext ctx =
                LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
        TestParentCfgClient parent = getTestParent(ctx, "test parent 3");
        String[] actual = parent.listTestChildren();
        String[] expected = new String[] {};
        Assert.assertEqualsNoOrder(actual, expected);
    }

    /**
     * Tests listing of top level managed objects.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testListTopLevelManagedObjects() throws Exception {
        MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
        Connection c = newInternalConnection(backend);
        ManagementContext ctx =
                LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
        String[] actual = listTestParents(ctx);
        String[] expected = new String[] { "test parent 1", "test parent 2", "test parent 3" };
        Assert.assertEqualsNoOrder(actual, expected);
    }

    /**
     * Tests listing of top level managed objects when their are not any.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testListTopLevelManagedObjectsEmpty() throws Exception {
        MemoryBackend backend = new MemoryBackend();
        Connection c = newInternalConnection(backend);
        ManagementContext ctx =
                LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
        String[] actual = listTestParents(ctx);
        String[] expected = new String[] {};
        Assert.assertEqualsNoOrder(actual, expected);
    }

    /**
     * Tests modification of a child managed object.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testModifyChildManagedObjectResetToDefault() throws Exception {
        MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
        Connection c = newInternalConnection(backend);
        ManagementContext ctx =
                LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
        TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
        TestChildCfgClient child = parent.getTestChild("test child 2");
        child.setOptionalMultiValuedDNProperty1(Collections.<DN> emptySet());
        child.commit();

        String dn = "cn=test child 2,cn=test children,cn=test parent 1,cn=test parents,cn=config";
        assertThat(backend.get(dn))
                .isEqualTo(
                        new LinkedHashMapEntry(
                                "dn: " + dn,
                                "cn: test child 2",
                                "objectClass: top",
                                "objectClass: ds-cfg-test-child-dummy",
                                "ds-cfg-enabled: true",
                                "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
                                "ds-cfg-attribute-type: description"));
    }

    /**
     * Tests modification of a top-level managed object.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testModifyTopLevelManagedObjectNoChanges() throws Exception {
        MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
        final AtomicBoolean isModified = new AtomicBoolean();
        Connection c = new AbstractConnectionWrapper<Connection>(newInternalConnection(backend)) {
            @Override
            public Result modify(ModifyRequest request) throws LdapException {
                isModified.set(true);
                return super.modify(request);
            }
        };
        ManagementContext ctx =
                LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
        TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
        parent.commit();
        assertThat(isModified.get()).isFalse(); // Nothing to do, so no modify.
    }

    /**
     * Tests modification of a top-level managed object.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testModifyTopLevelManagedObjectWithChanges() throws Exception {
        MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
        final AtomicBoolean isModified = new AtomicBoolean();
        Connection c = new AbstractConnectionWrapper<Connection>(newInternalConnection(backend)) {
            @Override
            public Result modify(ModifyRequest request) throws LdapException {
                isModified.set(true);
                return super.modify(request);
            }
        };
        ManagementContext ctx =
                LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
        TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
        parent.setMandatoryBooleanProperty(false);
        parent.setOptionalMultiValuedDNProperty(Arrays.asList(DN.valueOf("dc=mod1,dc=com"), DN
                .valueOf("dc=mod2,dc=com")));
        parent.commit();

        String dn = "cn=test parent 1,cn=test parents,cn=config";
        assertThat(backend.get(dn))
                .isEqualTo(
                        new LinkedHashMapEntry(
                                "dn: " + dn,
                                "objectclass: top",
                                "objectclass: ds-cfg-test-parent-dummy",
                                "cn: test parent 1",
                                "ds-cfg-enabled: false",
                                "ds-cfg-base-dn: dc=mod1,dc=com",
                                "ds-cfg-base-dn: dc=mod2,dc=com",
                                "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
                                "ds-cfg-attribute-type: description"));
        assertThat(isModified.get()).isTrue();
    }

    /**
     * Tests removal of a child managed object.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testRemoveChildManagedObject() throws Exception {
        MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
        Connection c = newInternalConnection(backend);
        ManagementContext ctx =
                LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
        TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
        parent.removeTestChild("test child 1");
        String dn = "cn=test child 1,cn=test children,cn=test parent 1,cn=test parents,cn=config";
        assertThat(backend.get(dn)).isNull();
    }

    /**
     * Tests removal of a top-level managed object.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testRemoveTopLevelManagedObject() throws Exception {
        MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
        Connection c = newInternalConnection(backend);
        ManagementContext ctx =
                LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
        removeTestParent(ctx, "test parent 1");
        String dn = "cn=test parent 1,cn=test parents,cn=config";
        assertThat(backend.get(dn)).isNull();
    }

    /**
     * Tests creation of a child managed object succeeds when registered add
     * constraints succeed.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testAddConstraintSuccess() throws Exception {
        Constraint constraint = new MockConstraint(true, false, false);
        TestCfg.addConstraint(constraint);
        try {
            MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
            Connection c = newInternalConnection(backend);
            ManagementContext ctx =
                    LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
            TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
            TestChildCfgClient child =
                    parent.createTestChild(TestChildCfgDefn.getInstance(), "test child new", null);
            child.setMandatoryBooleanProperty(true);
            child.setMandatoryReadOnlyAttributeTypeProperty(getAttributeType("description"));
            child.commit();

            String dn =
                    "cn=test child new,cn=test children,cn=test parent 1,cn=test parents,cn=config";
            assertThat(backend.get(dn)).isEqualTo(new LinkedHashMapEntry(
                    "dn: " + dn,
                    "objectclass: top",
                    "objectclass: ds-cfg-test-child-dummy",
                    "cn: test child new",
                    "ds-cfg-enabled: true",
                    "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
                    "ds-cfg-attribute-type: description"));
        } finally {
            TestCfg.removeConstraint(constraint);
        }
    }

    /**
     * Tests creation of a child managed object fails when registered add
     * constraints fail.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test(expectedExceptions = OperationRejectedException.class)
    public void testAddConstraintFail() throws Exception {
        Constraint constraint = new MockConstraint(false, true, true);
        TestCfg.addConstraint(constraint);
        try {
            MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
            Connection c = newInternalConnection(backend);
            ManagementContext ctx =
                    LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
            TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
            TestChildCfgClient child =
                    parent.createTestChild(TestChildCfgDefn.getInstance(), "test child new", null);
            child.setMandatoryBooleanProperty(true);
            child.setMandatoryReadOnlyAttributeTypeProperty(getAttributeType("description"));
            child.commit();
            Assert.fail("The add constraint failed to prevent creation of the managed object");
        } finally {
            TestCfg.removeConstraint(constraint);
        }
    }

    /**
     * Tests removal of a child managed object succeeds when registered remove
     * constraints succeed.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testRemoveConstraintSuccess() throws Exception {
        Constraint constraint = new MockConstraint(false, false, true);
        TestCfg.addConstraint(constraint);
        try {
            MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
            Connection c = newInternalConnection(backend);
            ManagementContext ctx =
                    LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
            TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
            parent.removeTestChild("test child 1");
            String dn =
                    "cn=test child 1,cn=test children,cn=test parent 1,cn=test parents,cn=config";
            assertThat(backend.get(dn)).isNull();
        } finally {
            TestCfg.removeConstraint(constraint);
        }
    }

    /**
     * Tests removal of a child managed object fails when registered remove
     * constraints fails.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test(expectedExceptions = OperationRejectedException.class)
    public void testRemoveConstraintFail() throws Exception {
        Constraint constraint = new MockConstraint(true, true, false);
        TestCfg.addConstraint(constraint);
        try {
            MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
            Connection c = newInternalConnection(backend);
            ManagementContext ctx =
                    LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
            TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
            parent.removeTestChild("test child 1");
            Assert.fail("The remove constraint failed to prevent removal of the managed object");
        } finally {
            TestCfg.removeConstraint(constraint);
        }
    }

    /**
     * Tests modification of a child managed object succeeds when registered
     * remove constraints succeed.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test
    public void testModifyConstraintSuccess() throws Exception {
        Constraint constraint = new MockConstraint(false, true, false);
        TestCfg.addConstraint(constraint);
        try {
            MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
            Connection c = newInternalConnection(backend);
            ManagementContext ctx =
                    LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
            TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
            TestChildCfgClient child = parent.getTestChild("test child 2");
            child.setOptionalMultiValuedDNProperty1(Collections.<DN> emptySet());
            child.commit();

            String dn =
                    "cn=test child 2,cn=test children,cn=test parent 1,cn=test parents,cn=config";
            assertThat(backend.get(dn)).isEqualTo(new LinkedHashMapEntry(
                    "dn: " + dn,
                    "cn: test child 2",
                    "objectClass: top",
                    "objectClass: ds-cfg-test-child-dummy",
                    "ds-cfg-enabled: true",
                    "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
                    "ds-cfg-attribute-type: description"));
        } finally {
            TestCfg.removeConstraint(constraint);
        }
    }

    /**
     * Tests modification of a child managed object fails when registered remove
     * constraints fails.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    @Test(expectedExceptions = OperationRejectedException.class)
    public void testModifyConstraintFail() throws Exception {
        Constraint constraint = new MockConstraint(true, false, true);
        TestCfg.addConstraint(constraint);
        try {
            MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(TEST_LDIF));
            Connection c = newInternalConnection(backend);
            ManagementContext ctx =
                    LDAPManagementContext.newManagementContext(c, LDAPProfile.getInstance());
            TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
            TestChildCfgClient child = parent.getTestChild("test child 2");
            child.setOptionalMultiValuedDNProperty1(Collections.<DN> emptySet());
            child.commit();
            Assert.fail("The modify constraint failed to prevent modification of the managed object");
        } finally {
            TestCfg.removeConstraint(constraint);
        }
    }

    /** Asserts that the actual set of DNs contains the expected values. */
    private void assertDNSetEquals(SortedSet<DN> actual, String... expected) {
        String[] actualStrings = new String[actual.size()];
        int i = 0;
        for (DN dn : actual) {
            actualStrings[i] = dn.toString();
            i++;
        }
        Assert.assertEqualsNoOrder(actualStrings, expected);
    }

    /** Create the named test parent managed object. */
    private TestParentCfgClient createTestParent(ManagementContext context, String name)
            throws Exception {
        ManagedObject<RootCfgClient> root = context.getRootConfigurationManagedObject();
        return root.createChild(TestCfg.getTestOneToManyParentRelationDefinition(),
                TestParentCfgDefn.getInstance(), name, null).getConfiguration();
    }

    /** Retrieve the named test parent managed object. */
    private TestParentCfgClient getTestParent(ManagementContext context, String name)
            throws Exception {
        ManagedObject<RootCfgClient> root = context.getRootConfigurationManagedObject();
        return root.getChild(TestCfg.getTestOneToManyParentRelationDefinition(), name)
                .getConfiguration();
    }

    /** List test parent managed objects. */
    private String[] listTestParents(ManagementContext context) throws Exception {
        ManagedObject<RootCfgClient> root = context.getRootConfigurationManagedObject();
        return root.listChildren(TestCfg.getTestOneToManyParentRelationDefinition());
    }

    /** Remove the named test parent managed object. */
    private void removeTestParent(ManagementContext context, String name) throws Exception {
        ManagedObject<RootCfgClient> root = context.getRootConfigurationManagedObject();
        root.removeChild(TestCfg.getTestOneToManyParentRelationDefinition(), name);
    }

    private AttributeType getAttributeType(String type) {
        return Schema.getDefaultSchema().getAttributeType(type);
    }
}
