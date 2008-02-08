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

import java.util.List;

import org.opends.server.admin.std.meta.RootCfgDefn;
import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * ClassPropertyDefinition Tester.
 */
public class ClassPropertyDefinitionTest extends DirectoryServerTestCase {

  ClassPropertyDefinition.Builder builder = null;

  /**
   * Sets up tests
   */
  @BeforeClass
  public void setUp() {
    ClassPropertyDefinition.setAllowClassValidation(true);
  }

  /**
   * @return data for testing
   */
  @DataProvider(name = "testBuilderAddInstanceOf")
  public Object[][] createBuilderAddInstanceOfData() {
    return new Object[][]{
            { "java.awt.Container" },
            { "java.awt.Container$1" },
            { "java.awt.Container$2$1" },
            { "java.awt.Container$2" },
            { "java.awt.Container$AccessibleAWTContainer$AccessibleContainerHandler" },
            { "java.awt.Container$DropTargetEventTargetFilter" },
            { "java.awt.Container$EventTargetFilter" },
            { "java.awt.Container$MouseEventTargetFilter" },
            { "java.awt.Container$WakingRunnable" },
            { "java.awt.Container$AccessibleAWTContainer" }
    };
  }

  /**
   * Tests builder.addInstanceOf with valid data
   * @param className name of class name to add
   */
  @Test(dataProvider = "testBuilderAddInstanceOf")
  public void testBuilderAddInstanceOf(String className) {
    ClassPropertyDefinition.Builder localBuilder =
            ClassPropertyDefinition.createBuilder(RootCfgDefn.getInstance(),
                "test-property");
    localBuilder.addInstanceOf(className);
    ClassPropertyDefinition cpd = localBuilder.getInstance();
    List<String> instances = cpd.getInstanceOfInterface();
    assertTrue(instances.contains(className));
  }

  /**
   * @return data for testing with illegal values
   */
  @DataProvider(name = "testBuilderAddInstanceOf2")
  public Object[][] createBuilderAddInstanceOfData2() {
    return new Object[][]{
            { "1" },
            { "" },
            { " " },
            { "  " },
            { "abc." },
            { "abc.123" },
            { "abc.123$" },
    };
  }

  /**
   * Tests builder.addInstanceOf with valid data
   * @param className name of class to add
   */
  @Test(dataProvider = "testBuilderAddInstanceOf2",
          expectedExceptions = {IllegalArgumentException.class})
  public void testBuilderAddInstanceOf2(String className) {
    ClassPropertyDefinition.Builder localBuilder =
            ClassPropertyDefinition.createBuilder(
                RootCfgDefn.getInstance(), "test-property");
    localBuilder.addInstanceOf(className);
    ClassPropertyDefinition cpd = localBuilder.getInstance();
    List<String> instances = cpd.getInstanceOfInterface();
    assertTrue(instances.contains(className));
  }

  /**
   * @return data for testing with illegal values
   */
  @DataProvider(name = "testLoadClassData")
  public Object[][] createTestLoadData() {
    return new Object[][]{
            { "java.io.Serializable",
                    "java.lang.String",
                    Object.class,
                    String.class },
            { "java.io.Serializable",
                    "java.lang.String",
                    String.class,
                    String.class },
            { "java.lang.Number", // abstract class
                    "java.lang.Long",
                    Number.class,
                    Long.class },
    };
  }

  @Test(dataProvider = "testLoadClassData")
  public <T> void testLoadClass(String interfaceName, String loadClassName,
                            Class<T> instanceOfClass, Class expectedClass) {
    ClassPropertyDefinition.Builder localBuilder =
            ClassPropertyDefinition.createBuilder(
                RootCfgDefn.getInstance(), "test-property");
    localBuilder.addInstanceOf(interfaceName);
    ClassPropertyDefinition cpd = localBuilder.getInstance();
    Class clazz = cpd.loadClass(loadClassName, instanceOfClass);
    assertEquals(clazz, expectedClass);
  }

  /**
   * @return data for testing with illegal values
   */
  @DataProvider(name = "testLoadClassData2")
  public Object[][] createTestLoadData2() {
    return new Object[][]{
            { "java.lang.Runnable",
                    "java.lang.String",
                    Object.class,
                    String.class },
            { "java.lang.Runnable",
                    "some.bogus.ClassName",
                    Object.class,
                    String.class },
            { "java.lang.Runnable",
                    "java.lang.String",
                    Number.class,
                    Number.class },
    };
  }

  @Test(dataProvider = "testLoadClassData2",
          expectedExceptions = {IllegalPropertyValueException.class})
  public <T> void testLoadClass2(String interfaceName, String loadClassName,
                            Class<T> instanceOfClass, Class expectedClass) {
    ClassPropertyDefinition.Builder localBuilder =
            ClassPropertyDefinition.createBuilder(
                RootCfgDefn.getInstance(), "test-property");
    localBuilder.addInstanceOf(interfaceName);
    ClassPropertyDefinition cpd = localBuilder.getInstance();
    Class clazz = cpd.loadClass(loadClassName, instanceOfClass);
    assertEquals(clazz, String.class);
  }

}
