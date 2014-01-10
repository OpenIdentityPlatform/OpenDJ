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
