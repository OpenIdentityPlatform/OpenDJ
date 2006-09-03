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

import static org.testng.AssertJUnit.assertEquals;

import org.opends.server.api.SubtreeSpecification;
import org.opends.server.types.DN;
import org.testng.annotations.Test;

/**
 * This class defines a set of tests for the
 * {@link org.opends.server.core.AbsoluteSubtreeSpecification} class.
 * <p>
 * This test suite is shorter than the RFC 3672 test suite because it
 * focuses on testing only the pieces of functionality that are specific
 * to the syntax.
 */
public final class TestAbsoluteSubtreeSpecification extends
    SubtreeSpecificationTestCase {

  /**
   * Tests the {@link AbsoluteSubtreeSpecification#valueOf(String)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = DirectoryException.class)
  public void testValueOf1() throws Exception {
    String input = "{}";

    AbsoluteSubtreeSpecification.valueOf(input);
  }

  /**
   * Tests the {@link AbsoluteSubtreeSpecification#valueOf(String)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = DirectoryException.class)
  public void testValueOf2() throws Exception {
    String input = "  {   }  ";

    AbsoluteSubtreeSpecification.valueOf(input);
  }

  /**
   * Tests the {@link AbsoluteSubtreeSpecification#valueOf(String)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testValueOf3() throws Exception {
    String input = "{ absoluteBase \"dc=sun, dc=com\" }";
    String output = "{ absoluteBase \"dc=sun,dc=com\" }";

    SubtreeSpecification ss = AbsoluteSubtreeSpecification.valueOf(input);
    assertEquals(output, ss.toString());
  }

  /**
   * Tests the {@link AbsoluteSubtreeSpecification#valueOf(String)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testValueOf4() throws Exception {

    String input = "{absoluteBase \"dc=sun, dc=com\"}";
    String output = "{ absoluteBase \"dc=sun,dc=com\" }";

    SubtreeSpecification ss = AbsoluteSubtreeSpecification.valueOf(input);
    assertEquals(output, ss.toString());
  }

  /**
   * Tests the {@link AbsoluteSubtreeSpecification#valueOf(String)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testValueOf5() throws Exception {

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
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testMatches1() throws Exception {
    DN dn = DN.decode("dc=abc, dc=sun, dc=com");

    String value = "{ absoluteBase \"dc=sun, dc=com\", "
        + "specificationFilter \"(objectClass=person)\" }";
    SubtreeSpecification ss = AbsoluteSubtreeSpecification.valueOf(value);

    assertEquals(true, ss
        .isWithinScope(createEntry(dn, getObjectClasses())));
  }

  /**
   * Tests the {@link AbsoluteSubtreeSpecification#isWithinScope(Entry)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testMatches2() throws Exception {
    DN dn = DN.decode("dc=abc, dc=sun, dc=com");

    String value = "{ absoluteBase \"dc=sun, dc=com\", "
        + "specificationFilter \"(objectClass=organization)\" }";
    SubtreeSpecification ss = AbsoluteSubtreeSpecification.valueOf(value);

    assertEquals(false, ss
        .isWithinScope(createEntry(dn, getObjectClasses())));
  }
}
