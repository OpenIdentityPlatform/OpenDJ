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
 * Portions Copyright 2015-2016 ForgeRock AS.
 */

package org.forgerock.opendj.config;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class RelativeInheritedDefaultBehaviorProviderTest extends ConfigTestCase {

    private static final int OFFSET = 0;

    private TestParentCfgDefn parentDefinition;

    private RelativeInheritedDefaultBehaviorProvider<Boolean> defaultBehaviorProvider;

    @BeforeClass
    public void setUp() {
        parentDefinition = TestParentCfgDefn.getInstance();
        this.defaultBehaviorProvider = new RelativeInheritedDefaultBehaviorProvider<>(
            parentDefinition,
            parentDefinition.getMandatoryBooleanPropertyPropertyDefinition().getName(),
            OFFSET);
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void testAccept() {
        defaultBehaviorProvider.accept(new DefaultBehaviorProviderVisitor<Boolean, Object, Object>() {

            @Override
            public Object visitAbsoluteInherited(AbsoluteInheritedDefaultBehaviorProvider d, Object o) {
                return null;
            }

            @Override
            public Object visitAlias(AliasDefaultBehaviorProvider d, Object o) {
                return null;
            }

            @Override
            public Object visitDefined(DefinedDefaultBehaviorProvider d, Object o) {
                return null;
            }

            @Override
            public Object visitRelativeInherited(RelativeInheritedDefaultBehaviorProvider d, Object o) {
                return null;
            }

            @Override
            public Object visitUndefined(UndefinedDefaultBehaviorProvider d, Object o) {
                return null;
            }
        }, new Object());
    }

    @Test
    public void testGetManagedObjectPath() {
        assertEquals(defaultBehaviorProvider.getManagedObjectPath(ManagedObjectPath.emptyPath()),
                ManagedObjectPath.emptyPath());
    }

    @Test
    public void testGetPropertyDefinition() {
        assertEquals(defaultBehaviorProvider.getPropertyName(),
                parentDefinition.getMandatoryBooleanPropertyPropertyDefinition().getName());
    }

    @Test
    public void testGetRelativeOffset() {
        assertEquals(defaultBehaviorProvider.getRelativeOffset(), OFFSET);
    }

}
