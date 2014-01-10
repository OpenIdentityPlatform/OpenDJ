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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 */

package org.forgerock.opendj.config;

import static org.fest.assertions.Assertions.assertThat;
import static org.testng.Assert.assertEquals;

import java.util.Collection;
import java.util.Collections;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings({ "javadoc", "unchecked", "rawtypes" })
@Test(singleThreaded = true)
public class AbstractManagedObjectDefinitionTest extends ConfigTestCase {

    /** A test managed object definition. */
    private static class ManagedObjectDef extends AbstractManagedObjectDefinition {

        protected ManagedObjectDef(String name, AbstractManagedObjectDefinition parent) {
            super(name, parent);
        }
    }

    private ManagedObjectDef top = new ManagedObjectDef("topmost", null);

    private ManagedObjectDef middle1 = new ManagedObjectDef("middle1", top);

    private ManagedObjectDef middle2 = new ManagedObjectDef("middle2", top);

    private ManagedObjectDef bottom1 = new ManagedObjectDef("bottom1", middle1);

    private ManagedObjectDef bottom2 = new ManagedObjectDef("bottom2", middle1);

    private ManagedObjectDef bottom3 = new ManagedObjectDef("bottom3", middle1);

    @BeforeClass
    public void setUp() throws Exception {
        TestCfg.setUp();
    }

    @AfterClass
    public void tearDown() {
        TestCfg.cleanup();
    }

    @DataProvider(name = "isChildOf")
    public Object[][] createIsChildOf() {
        return new Object[][] {
            // child def, parent def, is child of
            { top, top, true },
            { middle1, middle1, true },
            { bottom1, bottom1, true },
            { top, middle1, false },
            { top, bottom1, false },
            { middle1, top, true },
            { bottom1, top, true },
            { bottom1, middle1, true },
        };
    }

    @Test(dataProvider = "isChildOf")
    public void testIsChildOf(ManagedObjectDef childDef, ManagedObjectDef parentDef, boolean expectedIsChildOf) {
        assertEquals(childDef.isChildOf(parentDef), expectedIsChildOf);
    }

    @DataProvider(name = "isParentOf")
    public Object[][] createIsParentOf() {
        return new Object[][] {
            // parent def, child def, is parent of
            { top, top, true },
            { middle1, middle1, true },
            { bottom1, bottom1, true },
            { top, middle1, true },
            { top, bottom1, true },
            { middle1, top, false },
            { bottom1, top, false },
            { bottom1, middle1, false },
        };
    }

    @Test(dataProvider = "isParentOf")
    public void testIsParentOf(ManagedObjectDef parentDef, ManagedObjectDef childDef, boolean expectedIsParentOf) {
        assertEquals(parentDef.isParentOf(childDef), expectedIsParentOf);
    }

    @Test
    public void testGetAllChildrenOfTop() {
        Collection<AbstractManagedObjectDefinition> children = top.getAllChildren();
        assertThat(children).hasSize(5);
        assertThat(children).containsOnly(middle1, middle2, bottom1, bottom2, bottom3);
    }

    @Test
    public void testGetAllChildrenOfMiddle() {
        Collection<AbstractManagedObjectDefinition> children = middle1.getAllChildren();
        assertThat(children).hasSize(3);
        assertThat(children).containsOnly(bottom1, bottom2, bottom3);
    }

    @Test
    public void testGetAllChildrenNoChild() {
        Collection<AbstractManagedObjectDefinition> children = middle2.getAllChildren();
        assertThat(children).isEmpty();
    }

    @Test
    public void testGetChildrenTop() {
        Collection<AbstractManagedObjectDefinition> children = top.getChildren();
        assertThat(children).hasSize(2);
        assertThat(children).containsOnly(middle1, middle2);
    }

    @Test
    public void testGetChildrenMiddleNoChild() {
        Collection<AbstractManagedObjectDefinition> children = middle2.getChildren();
        assertThat(children).isEmpty();
    }

    /** Check default value of "class" property provided for parent. */
    @Test
    public void testPropertyOverrideParent() {
        AbstractManagedObjectDefinition<?, ?> def = TestParentCfgDefn.getInstance();
        PropertyDefinition<?> propertyDef = def.getPropertyDefinition("mandatory-class-property");
        DefaultBehaviorProvider<?> provider = propertyDef.getDefaultBehaviorProvider();
        assertEquals(provider.getClass(), DefinedDefaultBehaviorProvider.class);

        DefinedDefaultBehaviorProvider<?> definedProvider = (DefinedDefaultBehaviorProvider<?>) provider;
        assertEquals(definedProvider.getDefaultValues(),
                Collections.singleton("org.opends.server.extensions.SomeVirtualAttributeProvider"));
    }

    /** Check default value of "class" property provided for child. */
    @Test
    public void testPropertyOverrideChild() {
        AbstractManagedObjectDefinition<?, ?> def = TestChildCfgDefn.getInstance();
        PropertyDefinition<?> propertyDef = def.getPropertyDefinition("mandatory-class-property");
        DefaultBehaviorProvider<?> provider = propertyDef.getDefaultBehaviorProvider();
        assertEquals(provider.getClass(), DefinedDefaultBehaviorProvider.class);

        DefinedDefaultBehaviorProvider<?> definedProvider = (DefinedDefaultBehaviorProvider<?>) provider;
        assertEquals(definedProvider.getDefaultValues(),
                Collections.singleton("org.opends.server.extensions.UserDefinedVirtualAttributeProvider"));
    }

    @Test
    public void testGetReverseRelationDefinitions() {
        Collection<RelationDefinition<TestParentCfgClient, TestParentCfg>> parentRelDef =
                TestParentCfgDefn.getInstance().getReverseRelationDefinitions();

        assertThat(parentRelDef).hasSize(2);
        assertThat(parentRelDef).contains(TestCfg.getTestOneToManyParentRelationDefinition());
        assertThat(parentRelDef).contains(TestCfg.getTestOneToZeroOrOneParentRelationDefinition());

        Collection<RelationDefinition<TestChildCfgClient, TestChildCfg>> childRelDef = TestChildCfgDefn.getInstance()
                .getReverseRelationDefinitions();

        assertThat(childRelDef).hasSize(2);
        assertThat(childRelDef).contains(TestParentCfgDefn.getInstance().getTestChildrenRelationDefinition());
        assertThat(childRelDef).contains(TestParentCfgDefn.getInstance().getOptionalTestChildRelationDefinition());
    }

    @Test
    public void testGetAllReverseRelationDefinitions() {
        Collection<RelationDefinition<? super TestParentCfgClient, ? super TestParentCfg>> parentRelDef =
                TestParentCfgDefn.getInstance().getAllReverseRelationDefinitions();

        assertThat(parentRelDef).hasSize(2);
        assertThat(parentRelDef).contains(TestCfg.getTestOneToManyParentRelationDefinition());
        assertThat(parentRelDef).contains(TestCfg.getTestOneToZeroOrOneParentRelationDefinition());

        Collection<RelationDefinition<? super TestChildCfgClient, ? super TestChildCfg>> childRelDef =
                TestChildCfgDefn.getInstance().getAllReverseRelationDefinitions();

        assertThat(childRelDef).hasSize(2);
        assertThat(childRelDef).contains(TestParentCfgDefn.getInstance().getTestChildrenRelationDefinition());
        assertThat(childRelDef).contains(TestParentCfgDefn.getInstance().getOptionalTestChildRelationDefinition());
    }
}
