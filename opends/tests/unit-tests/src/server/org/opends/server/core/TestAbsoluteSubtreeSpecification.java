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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.opends.server.ConfigurationTestCaseDependency;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.DirectoryServerTestSetup;
import org.opends.server.InitialDirectoryServerTestCaseDependency;
import org.opends.server.SchemaTestCaseDependency;
import org.opends.server.api.SubtreeSpecification;
import org.opends.server.types.*;

/**
 * This class defines a set of JUnit tests for the
 * {@link org.opends.server.core.AbsoluteSubtreeSpecification} class.
 * <p>
 * This test suite is shorter than the RFC 3672 test suite because it
 * focuses on testing only the pieces of functionality that are specific
 * to the syntax.
 *
 * @author Matthew Swift
 */
public final class TestAbsoluteSubtreeSpecification extends
    DirectoryServerTestCase {

  // Cached set of entry object classes.
  private Set<ObjectClass> objectClasses;

  /**
   * Simple wrapper for creating entries that can be filtered.
   */
  private static class FilterableEntryImpl extends Entry {
    public static HashMap<ObjectClass, String> getObjectClasses(
        Set<ObjectClass> objectClasses) {
      HashMap<ObjectClass, String> map = new HashMap<ObjectClass, String>();

      for (ObjectClass oc : objectClasses) {
        if (oc != null) {
          map.put(oc, oc.getNameOrOID());
        }
      }

      return map;
    }

    /**
     * Construct a filterable entry from a DN and set of object classes.
     * It will not contain any attributes.
     *
     * @param entryDN
     *          The entry's DN.
     * @param objectClasses
     *          The entry's object classes.
     */
    public FilterableEntryImpl(DN entryDN, Set<ObjectClass> objectClasses) {
      super(entryDN, getObjectClasses(objectClasses), null, null);
    }
  }

  /**
   * Get the absolute subtree specification test suite.
   *
   * @return The test suite.
   */
  public static Test getTestSuite() {
    // Create the basic test suite.
    TestSuite suite = new TestSuite();
    suite.addTestSuite(TestAbsoluteSubtreeSpecification.class);

    // Wrap it up with dependencies.
    DirectoryServerTestSetup wrapper = new DirectoryServerTestSetup(suite);

    InitialDirectoryServerTestCaseDependency initial;
    initial = new InitialDirectoryServerTestCaseDependency();
    wrapper.registerDependency(initial);

    ConfigurationTestCaseDependency config;
    config = new ConfigurationTestCaseDependency(initial);
    wrapper.registerDependency(config);

    SchemaTestCaseDependency schema;
    schema = new SchemaTestCaseDependency(config);
    wrapper.registerDependency(schema);

    return wrapper;
  }

  /**
   * Creates a new instance of this JUnit test case with the provided
   * name.
   *
   * @param name
   *          The name to use for this JUnit test case.
   */
  public TestAbsoluteSubtreeSpecification(String name) {
    super(name);
  }

  /**
   * {@inheritDoc}
   */
  public void setUp() throws Exception {
    super.setUp();

    objectClasses = new HashSet<ObjectClass>();

    ObjectClass oc = DirectoryServer.getObjectClass("top");
    if (oc == null) {
      throw new RuntimeException("Unable to resolve object class top");
    }
    objectClasses.add(oc);

    oc = DirectoryServer.getObjectClass("person");
    if (oc == null) {
      throw new RuntimeException("Unable to resolve object class person");
    }
    objectClasses.add(oc);
  }

  /**
   * {@inheritDoc}
   */
  public void tearDown() throws Exception {
    super.tearDown();
  }

  /**
   * Tests the {@link AbsoluteSubtreeSpecification#valueOf(String)}
   * method.
   */
  public void testValueOf1() throws DirectoryException {
    String input = "{}";

    try {
      AbsoluteSubtreeSpecification.valueOf(input);
    } catch (DirectoryException e) {
      return;
    }

    fail("Expected DirectoryException");
  }

  /**
   * Tests the {@link AbsoluteSubtreeSpecification#valueOf(String)}
   * method.
   */
  public void testValueOf2() throws DirectoryException {
    String input = "  {   }  ";

    try {
      AbsoluteSubtreeSpecification.valueOf(input);
    } catch (DirectoryException e) {
      return;
    }

    fail("Expected DirectoryException");
  }

  /**
   * Tests the {@link AbsoluteSubtreeSpecification#valueOf(String)}
   * method.
   */
  public void testValueOf3() throws DirectoryException {

    String input = "{ absoluteBase \"dc=sun, dc=com\" }";
    String output = "{ absoluteBase \"dc=sun,dc=com\" }";

    SubtreeSpecification ss = AbsoluteSubtreeSpecification.valueOf(input);
    assertEquals(output, ss.toString());
  }

  /**
   * Tests the {@link AbsoluteSubtreeSpecification#valueOf(String)}
   * method.
   */
  public void testValueOf4() throws DirectoryException {

    String input = "{absoluteBase \"dc=sun, dc=com\"}";
    String output = "{ absoluteBase \"dc=sun,dc=com\" }";

    SubtreeSpecification ss = AbsoluteSubtreeSpecification.valueOf(input);
    assertEquals(output, ss.toString());
  }

  /**
   * Tests the {@link AbsoluteSubtreeSpecification#valueOf(String)}
   * method.
   */
  public void testValueOf5() throws DirectoryException {

    String input = "{ absoluteBase \"dc=sun, dc=com\", "
        + "specificationFilter \"(objectClass=*)\" }";
    String output = "{ absoluteBase \"dc=sun,dc=com\", "
        + "specificationFilter \"(objectClass=*)\" }";

    SubtreeSpecification ss = AbsoluteSubtreeSpecification.valueOf(input);
    assertEquals(output, ss.toString());
  }

  /**
   * Tests the {@link AbsoluteSubtreeSpecification#isWithinScope(Entry)}
   * method.
   */
  public void testMatches1() throws DirectoryException {
    try {
      DN dn = DN.decode("dc=abc, dc=sun, dc=com");

      String value = "{ absoluteBase \"dc=sun, dc=com\", "
          + "specificationFilter \"(objectClass=person)\" }";
      SubtreeSpecification ss = AbsoluteSubtreeSpecification.valueOf(value);

      assertEquals(true, ss.isWithinScope(new FilterableEntryImpl(dn,
          objectClasses)));
    } catch (DirectoryException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Tests the {@link AbsoluteSubtreeSpecification#isWithinScope(Entry)}
   * method.
   */
  public void testMatches2() throws DirectoryException {
    try {
      DN dn = DN.decode("dc=abc, dc=sun, dc=com");

      String value = "{ absoluteBase \"dc=sun, dc=com\", "
          + "specificationFilter \"(objectClass=organization)\" }";
      SubtreeSpecification ss = AbsoluteSubtreeSpecification.valueOf(value);

      assertEquals(false, ss.isWithinScope(new FilterableEntryImpl(dn,
          objectClasses)));
    } catch (DirectoryException e) {
      throw new RuntimeException(e);
    }
  }
}
