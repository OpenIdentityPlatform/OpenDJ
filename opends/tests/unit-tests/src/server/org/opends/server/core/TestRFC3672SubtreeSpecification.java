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
 * {@link org.opends.server.core.RFC3672SubtreeSpecification} class.
 *
 * @author Matthew Swift
 */
public final class TestRFC3672SubtreeSpecification extends
    DirectoryServerTestCase {

  // Cached root DN.
  private DN rootDN;

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
   * Get the RFC3672 subtree specification test suite.
   *
   * @return The test suite.
   */
  public static Test getTestSuite() {
    // Create the basic test suite.
    TestSuite suite = new TestSuite();
    suite.addTestSuite(TestRFC3672SubtreeSpecification.class);

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
  public TestRFC3672SubtreeSpecification(String name) {
    super(name);
  }

  /**
   * {@inheritDoc}
   */
  public void setUp() throws Exception {
    super.setUp();

    try {
      rootDN = DN.decode("");

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

    } catch (DirectoryException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * {@inheritDoc}
   */
  public void tearDown() throws Exception {
    super.tearDown();
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#valueOf(DN, String)}
   * method.
   */
  public void testValueOf1() throws DirectoryException {

    String input = "{}";
    String output = "{ }";

    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        input);
    assertEquals(output, ss.toString());
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#valueOf(DN, String)}
   * method.
   */
  public void testValueOf2() throws DirectoryException {

    String input = "  {    }    ";
    String output = "{ }";

    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        input);
    assertEquals(output, ss.toString());
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#valueOf(DN, String)}
   * method.
   */
  public void testValueOf3() throws DirectoryException {

    String input = "{ base \"dc=sun, dc=com\" }";
    String output = "{ base \"dc=sun,dc=com\" }";

    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        input);
    assertEquals(output, ss.toString());
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#valueOf(DN, String)}
   * method.
   */
  public void testValueOf4() throws DirectoryException {

    String input = "{base \"dc=sun, dc=com\"}";
    String output = "{ base \"dc=sun,dc=com\" }";

    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        input);
    assertEquals(output, ss.toString());
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#valueOf(DN, String)}
   * method.
   */
  public void testValueOf5() throws DirectoryException {

    String input = "{ base \"dc=sun, dc=com\", "
        + "specificationFilter item:ds-config-rootDN }";
    String output = "{ base \"dc=sun,dc=com\", "
        + "specificationFilter item:ds-config-rootDN }";

    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        input);
    assertEquals(output, ss.toString());
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#valueOf(DN, String)}
   * method.
   */
  public void testValueOf6() throws DirectoryException {

    String input = "{ base \"dc=sun, dc=com\", minimum 0 , maximum 10, "
        + "specificExclusions {chopBefore:\"o=abc\", "
        + "chopAfter:\"o=xyz\"} , specificationFilter not:not:item:foo }";
    String output = "{ base \"dc=sun,dc=com\", "
        + "specificExclusions { chopBefore:\"o=abc\", "
        + "chopAfter:\"o=xyz\" }, maximum 10, specificationFilter "
        + "not:not:item:foo }";

    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        input);
    assertEquals(output, ss.toString());
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#valueOf(DN, String)}
   * method.
   */
  public void testValueOf7() throws DirectoryException {

    String input = "{ base \"\", minimum 0,maximum 10,"
        + "specificExclusions {chopBefore:\"o=abc\","
        + "chopAfter:\"o=xyz\"},specificationFilter not:not:item:foo}";
    String output = "{ specificExclusions { chopBefore:\"o=abc\", "
        + "chopAfter:\"o=xyz\" }, "
        + "maximum 10, specificationFilter not:not:item:foo }";

    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        input);
    assertEquals(output, ss.toString());
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#valueOf(DN, String)}
   * method.
   */
  public void testValueOf8() throws DirectoryException {

    String input = "{ specificationFilter and:{item:top, item:person} }";
    String output = "{ specificationFilter and:{item:top, item:person} }";

    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        input);
    assertEquals(output, ss.toString());
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#valueOf(DN, String)}
   * method.
   */
  public void testValueOf9() throws DirectoryException {

    String input = "{ specificationFilter or:{item:top, item:person} }";
    String output = "{ specificationFilter or:{item:top, item:person} }";

    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        input);
    assertEquals(output, ss.toString());
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#valueOf(DN, String)}
   * method.
   */
  public void testValueOf10() throws DirectoryException {

    String input = "{ specificationFilter "
        + "or:{item:top, item:foo, and:{item:one, item:two}} }";
    String output = "{ specificationFilter "
        + "or:{item:top, item:foo, and:{item:one, item:two}} }";

    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        input);
    assertEquals(output, ss.toString());
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   */
  public void testMatches1() throws DirectoryException {
    try {
      DN dn = DN.decode("dc=sun, dc=com");

      String value = "{ base \"dc=sun, dc=com\" }";
      SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
          value);

      assertEquals(true, ss.isWithinScope(new FilterableEntryImpl(dn,
          objectClasses)));
    } catch (DirectoryException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   */
  public void testMatches2() throws DirectoryException {
    try {
      DN dn = DN.decode("dc=com");

      String value = "{ base \"dc=sun, dc=com\" }";
      SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
          value);

      assertEquals(false, ss.isWithinScope(new FilterableEntryImpl(dn,
          objectClasses)));
    } catch (DirectoryException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   */
  public void testMatches3() throws DirectoryException {
    try {
      DN dn = DN.decode("dc=foo, dc=sun, dc=com");

      String value = "{ base \"dc=sun, dc=com\" }";
      SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
          value);

      assertEquals(true, ss.isWithinScope(new FilterableEntryImpl(dn,
          objectClasses)));
    } catch (DirectoryException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   */
  public void testMatches4() throws DirectoryException {
    try {
      DN dn = DN.decode("dc=foo, dc=bar, dc=com");

      String value = "{ base \"dc=sun, dc=com\" }";
      SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
          value);

      assertEquals(false, ss.isWithinScope(new FilterableEntryImpl(dn,
          objectClasses)));
    } catch (DirectoryException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   */
  public void testMatches5() throws DirectoryException {
    try {
      DN dn = DN.decode("dc=sun, dc=com");

      String value = "{ base \"dc=sun, dc=com\", minimum 1 }";
      SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
          value);

      assertEquals(false, ss.isWithinScope(new FilterableEntryImpl(dn,
          objectClasses)));
    } catch (DirectoryException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   */
  public void testMatches6() throws DirectoryException {
    try {
      DN dn = DN.decode("dc=abc, dc=sun, dc=com");

      String value = "{ base \"dc=sun, dc=com\", minimum 1 }";
      SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
          value);

      assertEquals(true, ss.isWithinScope(new FilterableEntryImpl(dn,
          objectClasses)));
    } catch (DirectoryException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   */
  public void testMatches7() throws DirectoryException {
    try {
      DN dn = DN.decode("dc=xyz, dc=abc, dc=sun, dc=com");

      String value = "{ base \"dc=sun, dc=com\", minimum 1 }";
      SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
          value);

      assertEquals(true, ss.isWithinScope(new FilterableEntryImpl(dn,
          objectClasses)));
    } catch (DirectoryException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   */
  public void testMatches8() throws DirectoryException {
    try {
      DN dn = DN.decode("dc=sun, dc=com");

      String value = "{ base \"dc=sun, dc=com\", maximum 0 }";
      SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
          value);

      assertEquals(true, ss.isWithinScope(new FilterableEntryImpl(dn,
          objectClasses)));
    } catch (DirectoryException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   */
  public void testMatches9() throws DirectoryException {
    try {
      DN dn = DN.decode("dc=foo, dc=sun, dc=com");

      String value = "{ base \"dc=sun, dc=com\", maximum 0 }";
      SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
          value);

      assertEquals(false, ss.isWithinScope(new FilterableEntryImpl(dn,
          objectClasses)));
    } catch (DirectoryException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   */
  public void testMatches10() throws DirectoryException {
    try {
      DN dn = DN.decode("dc=bar, dc=foo, dc=sun, dc=com");

      String value = "{ base \"dc=sun, dc=com\", maximum 1 }";
      SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
          value);

      assertEquals(false, ss.isWithinScope(new FilterableEntryImpl(dn,
          objectClasses)));
    } catch (DirectoryException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   */
  public void testMatches11() throws DirectoryException {
    try {
      DN dn = DN.decode("dc=bar, dc=foo, dc=sun, dc=com");

      String value = "{ base \"dc=sun, dc=com\", maximum 2 }";
      SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
          value);

      assertEquals(true, ss.isWithinScope(new FilterableEntryImpl(dn,
          objectClasses)));
    } catch (DirectoryException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   */
  public void testMatches12() throws DirectoryException {
    try {
      DN dn = DN.decode("dc=sun, dc=com");

      String value = "{ base \"dc=sun, dc=com\", "
          + "specificExclusions { chopAfter:\"\" } }";
      SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
          value);

      assertEquals(true, ss.isWithinScope(new FilterableEntryImpl(dn,
          objectClasses)));
    } catch (DirectoryException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   */
  public void testMatches13() throws DirectoryException {
    try {
      DN dn = DN.decode("dc=foo, dc=sun, dc=com");

      String value = "{ base \"dc=sun, dc=com\", "
          + "specificExclusions { chopAfter:\"\" } }";
      SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
          value);

      assertEquals(false, ss.isWithinScope(new FilterableEntryImpl(dn,
          objectClasses)));
    } catch (DirectoryException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   */
  public void testMatches14() throws DirectoryException {
    try {
      DN dn = DN.decode("dc=foo, dc=sun, dc=com");

      String value = "{ base \"dc=sun, dc=com\", "
          + "specificExclusions { chopAfter:\"dc=foo\" } }";
      SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
          value);

      assertEquals(true, ss.isWithinScope(new FilterableEntryImpl(dn,
          objectClasses)));
    } catch (DirectoryException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   */
  public void testMatches15() throws DirectoryException {
    try {
      DN dn = DN.decode("dc=bar, dc=foo, dc=sun, dc=com");

      String value = "{ base \"dc=sun, dc=com\", "
          + "specificExclusions { chopAfter:\"dc=foo\" } }";
      SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
          value);

      assertEquals(false, ss.isWithinScope(new FilterableEntryImpl(dn,
          objectClasses)));
    } catch (DirectoryException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   */
  public void testMatches16() throws DirectoryException {
    try {
      DN dn = DN.decode("dc=foo, dc=sun, dc=com");

      String value = "{ base \"dc=sun, dc=com\", "
          + "specificExclusions { chopBefore:\"dc=foo\" } }";
      SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
          value);

      assertEquals(false, ss.isWithinScope(new FilterableEntryImpl(dn,
          objectClasses)));
    } catch (DirectoryException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   */
  public void testMatches17() throws DirectoryException {
    try {
      DN dn = DN.decode("dc=bar, dc=foo, dc=sun, dc=com");

      String value = "{ base \"dc=sun, dc=com\", "
          + "specificExclusions { chopBefore:\"dc=foo\" } }";
      SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
          value);

      assertEquals(false, ss.isWithinScope(new FilterableEntryImpl(dn,
          objectClasses)));
    } catch (DirectoryException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   */
  public void testMatches18() throws DirectoryException {
    try {
      DN dn = DN.decode("dc=abc, dc=sun, dc=com");

      String value = "{ base \"dc=sun, dc=com\", "
          + "specificExclusions { chopBefore:\"dc=foo\" } }";
      SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
          value);

      assertEquals(true, ss.isWithinScope(new FilterableEntryImpl(dn,
          objectClasses)));
    } catch (DirectoryException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   */
  public void testMatches19() throws DirectoryException {
    try {
      DN dn = DN.decode("dc=abc, dc=sun, dc=com");

      String value = "{ base \"dc=sun, dc=com\", "
          + "specificationFilter item:person }";
      SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
          value);

      assertEquals(true, ss.isWithinScope(new FilterableEntryImpl(dn,
          objectClasses)));
    } catch (DirectoryException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   */
  public void testMatches20() throws DirectoryException {
    try {
      DN dn = DN.decode("dc=abc, dc=sun, dc=com");

      String value = "{ base \"dc=sun, dc=com\", "
          + "specificationFilter item:organization }";
      SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
          value);

      assertEquals(false, ss.isWithinScope(new FilterableEntryImpl(dn,
          objectClasses)));
    } catch (DirectoryException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   */
  public void testMatches21() throws DirectoryException {
    try {
      DN dn = DN.decode("dc=abc, dc=sun, dc=com");

      String value = "{ base \"dc=sun, dc=com\", "
          + "specificationFilter not:item:person }";
      SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
          value);

      assertEquals(false, ss.isWithinScope(new FilterableEntryImpl(dn,
          objectClasses)));
    } catch (DirectoryException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   */
  public void testMatches22() throws DirectoryException {
    try {
      DN dn = DN.decode("dc=abc, dc=sun, dc=com");

      String value = "{ base \"dc=sun, dc=com\", "
          + "specificationFilter not:item:organization }";
      SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
          value);

      assertEquals(true, ss.isWithinScope(new FilterableEntryImpl(dn,
          objectClasses)));
    } catch (DirectoryException e) {
      throw new RuntimeException(e);
    }
  }
}
