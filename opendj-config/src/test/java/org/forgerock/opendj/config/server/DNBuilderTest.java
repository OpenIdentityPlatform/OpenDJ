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
 *      Portions Copyright 2015 ForgeRock AS.
 */
package org.forgerock.opendj.config.server;

import static org.testng.Assert.assertEquals;

import org.forgerock.opendj.config.AdminTestCase;
import org.forgerock.opendj.config.LDAPProfile;
import org.forgerock.opendj.config.ManagedObjectPath;
import org.forgerock.opendj.config.RelationDefinition;
import org.forgerock.opendj.config.SingletonRelationDefinition;
import org.forgerock.opendj.config.TestCfg;
import org.forgerock.opendj.config.TestChildCfg;
import org.forgerock.opendj.config.TestChildCfgClient;
import org.forgerock.opendj.config.TestChildCfgDefn;
import org.forgerock.opendj.config.TestParentCfgDefn;
import org.forgerock.opendj.ldap.DN;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public final class DNBuilderTest extends AdminTestCase {

    @BeforeClass
    public void setUp() throws Exception {
        TestCfg.setUp();
    }

    @AfterClass
    public void tearDown() {
        TestCfg.cleanup();
    }

    @Test
    public void createWithInstantiableRelationDefinition() throws Exception {
        ManagedObjectPath<?, ?> parentPath = ManagedObjectPath.emptyPath().
            child(TestCfg.getTestOneToManyParentRelationDefinition(), "test-parent-1");
        ManagedObjectPath<?, ?> childPath = parentPath.child(TestParentCfgDefn.getInstance().
            getTestChildrenRelationDefinition(), "test-child-1");

        assertEquals(
            DNBuilder.create(childPath),
            DN.valueOf("cn=test-child-1,cn=test children,cn=test-parent-1,cn=test parents,cn=config"));
    }

    @Test
    public void createWithSingletonRelationDefinition() throws Exception {
        SingletonRelationDefinition.Builder<TestChildCfgClient, TestChildCfg> builder =
            new SingletonRelationDefinition.Builder<>(
                TestParentCfgDefn.getInstance(), "singleton-test-child", TestChildCfgDefn.getInstance());
        final SingletonRelationDefinition<TestChildCfgClient, TestChildCfg> relationDef = builder.getInstance();

        LDAPProfile.Wrapper wrapper = new LDAPProfile.Wrapper() {
            public String getRelationRDNSequence(RelationDefinition<?, ?> r) {
                return (r == relationDef) ? "cn=singleton-test-child" : null;
            }
        };

        ManagedObjectPath<?, ?> path = ManagedObjectPath.emptyPath().
                child(TestCfg.getTestOneToManyParentRelationDefinition(), "test-parent-1");
        ManagedObjectPath<?, ?> childPath = path.child(relationDef);

        LDAPProfile.getInstance().pushWrapper(wrapper);
        try {
            assertEquals(
                DNBuilder.create(childPath),
                DN.valueOf("cn=singleton-test-child,cn=test-parent-1,cn=test parents,cn=config"));
        } finally {
            LDAPProfile.getInstance().popWrapper();
        }
    }

    /**
     * Tests construction of a DN from a managed object path containing a
     * subordinate one-to-zero-or-one relationship.
     */
    @Test
    public void createWithOptionalRelationDefinition() throws Exception {
        ManagedObjectPath<?, ?> path =  ManagedObjectPath
                .emptyPath().child(TestCfg.getTestOneToManyParentRelationDefinition(), "test-parent-1");
        ManagedObjectPath<?, ?> childPath =
                path.child(TestParentCfgDefn.getInstance().getOptionalTestChildRelationDefinition());

        assertEquals(
            DNBuilder.create(childPath),
            DN.valueOf("cn=optional test child,cn=test-parent-1,cn=test parents,cn=config"));
    }

}
