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
 */

package org.forgerock.opendj.config;

import org.forgerock.opendj.ldap.DN;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
@Test(singleThreaded = true)
public class AggregationPropertyDefinitionTest extends ConfigTestCase {

    @BeforeClass
    public void setUp() throws Exception {
        TestCfg.setUp();
    }

    @AfterClass
    public void tearDown() {
        TestCfg.cleanup();
    }

     /** Tests that the {@link AggregationPropertyDefinition#normalizeValue(String)} works. */
    @Test
    public void testNormalizeValue() throws Exception {
        TestChildCfgDefn definition = TestChildCfgDefn.getInstance();
        AggregationPropertyDefinition<?, ?> propertyDef = definition.getAggregationPropertyPropertyDefinition();
        String nvalue = propertyDef.normalizeValue("  LDAP   connection    handler  ");
        Assert.assertEquals(nvalue, "ldap connection handler");
    }

    /** Tests that the {@link AggregationPropertyDefinition#getChildDN(String)} works. */
    @Test
    public void testGetChildDN() throws Exception {
        TestChildCfgDefn definition = TestChildCfgDefn.getInstance();
        AggregationPropertyDefinition<?, ?> propertyDef = definition.getAggregationPropertyPropertyDefinition();
        DN expected = DN.valueOf("cn=ldap connection handler, cn=connection handlers, cn=config");
        DN actual = propertyDef.getChildDN("  LDAP  connection handler  ");
        Assert.assertEquals(actual, expected);
    }

    @Test
    public void testGetChildPath() throws Exception {
        TestChildCfgDefn definition = TestChildCfgDefn.getInstance();
        AggregationPropertyDefinition<?, ?> propertyDef = definition.getAggregationPropertyPropertyDefinition();
        ManagedObjectPath<?, ?> path = propertyDef.getChildPath("LDAP connection handler");

        Assert.assertSame(path.getManagedObjectDefinition(), propertyDef.getRelationDefinition().getChildDefinition());
        Assert.assertSame(path.getRelationDefinition(), propertyDef.getRelationDefinition());
    }

}
