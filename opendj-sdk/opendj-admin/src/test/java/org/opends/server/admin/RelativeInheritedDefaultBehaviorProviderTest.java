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

import org.forgerock.opendj.config.ConfigTestCase;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class RelativeInheritedDefaultBehaviorProviderTest extends ConfigTestCase {

    private static final int OFFSET = 0;

    private TestParentCfgDefn parentDefinition;

    private RelativeInheritedDefaultBehaviorProvider<Boolean> defaultBehaviorProvider = null;

    @BeforeClass
    public void setUp() {
        disableClassValidationForProperties();
        parentDefinition = TestParentCfgDefn.getInstance();
        this.defaultBehaviorProvider = new RelativeInheritedDefaultBehaviorProvider<Boolean>(
            parentDefinition,
            parentDefinition.getMandatoryBooleanPropertyPropertyDefinition().getName(),
            OFFSET);
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void testAccept() {
        defaultBehaviorProvider.accept(new DefaultBehaviorProviderVisitor<Boolean, Object, Object>() {

            public Object visitAbsoluteInherited(AbsoluteInheritedDefaultBehaviorProvider d, Object o) {
                return null;
            }

            public Object visitAlias(AliasDefaultBehaviorProvider d, Object o) {
                return null;
            }

            public Object visitDefined(DefinedDefaultBehaviorProvider d, Object o) {
                return null;
            }

            public Object visitRelativeInherited(RelativeInheritedDefaultBehaviorProvider d, Object o) {
                return null;
            }

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
