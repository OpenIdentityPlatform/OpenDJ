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
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.opends.server.admin;

import static org.testng.Assert.*;

import java.util.Comparator;
import java.util.List;

import org.forgerock.opendj.admin.meta.RootCfgDefn;
import org.forgerock.opendj.config.ConfigTestCase;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ClassPropertyDefinitionTest extends ConfigTestCase {

    @BeforeClass
    public void setUp() throws Exception {
        disableClassValidationForProperties();
    }

    // Dummy class used in tests.
    public static final class Dummy {
        public class X {
            // no implementation
        }

        public Comparator<Dummy> comparator() {
            return new Comparator<ClassPropertyDefinitionTest.Dummy>() {
                public int compare(Dummy o1, Dummy o2) {
                    // No implementation required.
                    return 0;
                }
            };
        }
    }

    ClassPropertyDefinition.Builder builder = null;

    @DataProvider(name = "validClassNames")
    public Object[][] createBuilderAddInstanceOfData() {
        return new Object[][] {
            { "org.opends.server.admin.ClassPropertyDefinitionTest" },
            { "org.opends.server.admin.ClassPropertyDefinitionTest$Dummy" },
            { "org.opends.server.admin.ClassPropertyDefinitionTest$Dummy$X" },
            { "org.opends.server.admin.ClassPropertyDefinitionTest$Dummy$1" }, };
    }

    @Test(dataProvider = "validClassNames")
    public void testBuilderAddInstanceOf(String classNameToAdd) {
        ClassPropertyDefinition.Builder localBuilder = ClassPropertyDefinition.createBuilder(RootCfgDefn.getInstance(),
                "test-property");
        localBuilder.addInstanceOf(classNameToAdd);
        ClassPropertyDefinition propertyDef = localBuilder.getInstance();
        List<String> instances = propertyDef.getInstanceOfInterface();
        assertTrue(instances.contains(classNameToAdd));
    }

    @DataProvider(name = "invalidClassNames")
    public Object[][] createBuilderAddInstanceOfDataInvalid() {
        return new Object[][] {
            { "1" },
            { "" },
            { " " },
            { "  " },
            { "abc." },
            { "abc.123" },
            { "abc.123$" },
        };
    }

    @Test(dataProvider = "invalidClassNames", expectedExceptions = { IllegalArgumentException.class })
    public void testBuilderAddInstanceInvalid(String className) {
        ClassPropertyDefinition.Builder localBuilder = ClassPropertyDefinition.createBuilder(RootCfgDefn.getInstance(),
                "test-property");
        localBuilder.addInstanceOf(className);
        ClassPropertyDefinition propertyDef = localBuilder.getInstance();
        List<String> instances = propertyDef.getInstanceOfInterface();
        assertTrue(instances.contains(className));
    }

    /**
     * @return data for testing with illegal values
     */
    @DataProvider(name = "loadClasses")
    public Object[][] createLoadData() {
        return new Object[][] {
            { "java.io.Serializable", "java.lang.String", Object.class, String.class },
            { "java.io.Serializable", "java.lang.String", String.class, String.class },
            // abstractclass
            { "java.lang.Number", "java.lang.Long", Number.class, Long.class }, };
    }

    @Test(dataProvider = "loadClasses")
    public <T> void testLoadClass(String interfaceName, String loadClassName, Class<T> instanceOfClass,
            Class<?> expectedClass) {
        ClassPropertyDefinition.Builder localBuilder = ClassPropertyDefinition.createBuilder(RootCfgDefn.getInstance(),
                "test-property");
        localBuilder.addInstanceOf(interfaceName);
        ClassPropertyDefinition propertyDef = localBuilder.getInstance();
        Class<?> clazz = propertyDef.loadClass(loadClassName, instanceOfClass);
        assertEquals(clazz, expectedClass);
    }

    @DataProvider(name = "loadClassesIllegal")
    public Object[][] createLoadDataIllegal() {
        return new Object[][] {
            { "java.lang.Runnable", "java.lang.String", Object.class, String.class },
            { "java.lang.Runnable", "some.bogus.ClassName", Object.class, String.class },
            { "java.lang.Runnable", "java.lang.String", Number.class, Number.class }, };
    }

    @SuppressWarnings("unused")
    @Test(dataProvider = "loadClassesIllegal", expectedExceptions = { IllegalPropertyValueException.class })
    public <T> void testLoadClassIllegal(String interfaceName, String loadClassName, Class<T> instanceOfClass,
            Class<?> expectedClass) {
        ClassPropertyDefinition.Builder localBuilder = ClassPropertyDefinition.createBuilder(RootCfgDefn.getInstance(),
                "test-property");
        localBuilder.addInstanceOf(interfaceName);
        ClassPropertyDefinition propertyDef = localBuilder.getInstance();
        Class<?> clazz = propertyDef.loadClass(loadClassName, instanceOfClass);
        assertEquals(clazz, String.class);
    }

}
