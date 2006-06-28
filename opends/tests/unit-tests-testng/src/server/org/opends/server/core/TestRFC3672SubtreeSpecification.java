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
 * {@link org.opends.server.core.RFC3672SubtreeSpecification} class.
 */
public final class TestRFC3672SubtreeSpecification extends
    SubtreeSpecificationTestCase {

  // Cached root DN.
  private DN rootDN = new DN();

  /**
   * Tests the {@link RFC3672SubtreeSpecification#valueOf(DN, String)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testValueOf1() throws Exception {

    String input = "{}";
    String output = "{ }";

    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        input);
    assertEquals(output, ss.toString());
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#valueOf(DN, String)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testValueOf2() throws Exception {

    String input = "  {    }    ";
    String output = "{ }";

    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        input);
    assertEquals(output, ss.toString());
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#valueOf(DN, String)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testValueOf3() throws Exception {

    String input = "{ base \"dc=sun, dc=com\" }";
    String output = "{ base \"dc=sun,dc=com\" }";

    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        input);
    assertEquals(output, ss.toString());
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#valueOf(DN, String)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testValueOf4() throws Exception {

    String input = "{base \"dc=sun, dc=com\"}";
    String output = "{ base \"dc=sun,dc=com\" }";

    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        input);
    assertEquals(output, ss.toString());
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#valueOf(DN, String)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testValueOf5() throws Exception {

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
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testValueOf6() throws Exception {

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
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testValueOf7() throws Exception {

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
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testValueOf8() throws Exception {

    String input = "{ specificationFilter and:{item:top, item:person} }";
    String output = "{ specificationFilter and:{item:top, item:person} }";

    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        input);
    assertEquals(output, ss.toString());
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#valueOf(DN, String)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testValueOf9() throws Exception {

    String input = "{ specificationFilter or:{item:top, item:person} }";
    String output = "{ specificationFilter or:{item:top, item:person} }";

    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        input);
    assertEquals(output, ss.toString());
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#valueOf(DN, String)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testValueOf10() throws Exception {

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
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testMatches1() throws Exception {
    DN dn = DN.decode("dc=sun, dc=com");

    String value = "{ base \"dc=sun, dc=com\" }";
    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        value);

    assertEquals(true, ss
        .isWithinScope(createEntry(dn, getObjectClasses())));
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testMatches2() throws Exception {
    DN dn = DN.decode("dc=com");

    String value = "{ base \"dc=sun, dc=com\" }";
    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        value);

    assertEquals(false, ss
        .isWithinScope(createEntry(dn, getObjectClasses())));
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testMatches3() throws Exception {
    DN dn = DN.decode("dc=foo, dc=sun, dc=com");

    String value = "{ base \"dc=sun, dc=com\" }";
    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        value);

    assertEquals(true, ss
        .isWithinScope(createEntry(dn, getObjectClasses())));
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testMatches4() throws Exception {
    DN dn = DN.decode("dc=foo, dc=bar, dc=com");

    String value = "{ base \"dc=sun, dc=com\" }";
    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        value);

    assertEquals(false, ss
        .isWithinScope(createEntry(dn, getObjectClasses())));
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testMatches5() throws Exception {
    DN dn = DN.decode("dc=sun, dc=com");

    String value = "{ base \"dc=sun, dc=com\", minimum 1 }";
    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        value);

    assertEquals(false, ss
        .isWithinScope(createEntry(dn, getObjectClasses())));
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testMatches6() throws Exception {
    DN dn = DN.decode("dc=abc, dc=sun, dc=com");

    String value = "{ base \"dc=sun, dc=com\", minimum 1 }";
    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        value);

    assertEquals(true, ss
        .isWithinScope(createEntry(dn, getObjectClasses())));
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testMatches7() throws Exception {
    DN dn = DN.decode("dc=xyz, dc=abc, dc=sun, dc=com");

    String value = "{ base \"dc=sun, dc=com\", minimum 1 }";
    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        value);

    assertEquals(true, ss
        .isWithinScope(createEntry(dn, getObjectClasses())));
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testMatches8() throws Exception {
    DN dn = DN.decode("dc=sun, dc=com");

    String value = "{ base \"dc=sun, dc=com\", maximum 0 }";
    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        value);

    assertEquals(true, ss
        .isWithinScope(createEntry(dn, getObjectClasses())));
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testMatches9() throws Exception {
    DN dn = DN.decode("dc=foo, dc=sun, dc=com");

    String value = "{ base \"dc=sun, dc=com\", maximum 0 }";
    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        value);

    assertEquals(false, ss
        .isWithinScope(createEntry(dn, getObjectClasses())));
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testMatches10() throws Exception {
    DN dn = DN.decode("dc=bar, dc=foo, dc=sun, dc=com");

    String value = "{ base \"dc=sun, dc=com\", maximum 1 }";
    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        value);

    assertEquals(false, ss
        .isWithinScope(createEntry(dn, getObjectClasses())));
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testMatches11() throws Exception {
    DN dn = DN.decode("dc=bar, dc=foo, dc=sun, dc=com");

    String value = "{ base \"dc=sun, dc=com\", maximum 2 }";
    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        value);

    assertEquals(true, ss
        .isWithinScope(createEntry(dn, getObjectClasses())));
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testMatches12() throws Exception {
    DN dn = DN.decode("dc=sun, dc=com");

    String value = "{ base \"dc=sun, dc=com\", "
        + "specificExclusions { chopAfter:\"\" } }";
    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        value);

    assertEquals(true, ss
        .isWithinScope(createEntry(dn, getObjectClasses())));
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testMatches13() throws Exception {
    DN dn = DN.decode("dc=foo, dc=sun, dc=com");

    String value = "{ base \"dc=sun, dc=com\", "
        + "specificExclusions { chopAfter:\"\" } }";
    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        value);

    assertEquals(false, ss
        .isWithinScope(createEntry(dn, getObjectClasses())));
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testMatches14() throws Exception {
    DN dn = DN.decode("dc=foo, dc=sun, dc=com");

    String value = "{ base \"dc=sun, dc=com\", "
        + "specificExclusions { chopAfter:\"dc=foo\" } }";
    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        value);

    assertEquals(true, ss
        .isWithinScope(createEntry(dn, getObjectClasses())));
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testMatches15() throws Exception {
    DN dn = DN.decode("dc=bar, dc=foo, dc=sun, dc=com");

    String value = "{ base \"dc=sun, dc=com\", "
        + "specificExclusions { chopAfter:\"dc=foo\" } }";
    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        value);

    assertEquals(false, ss
        .isWithinScope(createEntry(dn, getObjectClasses())));
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testMatches16() throws Exception {
    DN dn = DN.decode("dc=foo, dc=sun, dc=com");

    String value = "{ base \"dc=sun, dc=com\", "
        + "specificExclusions { chopBefore:\"dc=foo\" } }";
    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        value);

    assertEquals(false, ss
        .isWithinScope(createEntry(dn, getObjectClasses())));
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testMatches17() throws Exception {
    DN dn = DN.decode("dc=bar, dc=foo, dc=sun, dc=com");

    String value = "{ base \"dc=sun, dc=com\", "
        + "specificExclusions { chopBefore:\"dc=foo\" } }";
    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        value);

    assertEquals(false, ss
        .isWithinScope(createEntry(dn, getObjectClasses())));
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testMatches18() throws Exception {
    DN dn = DN.decode("dc=abc, dc=sun, dc=com");

    String value = "{ base \"dc=sun, dc=com\", "
        + "specificExclusions { chopBefore:\"dc=foo\" } }";
    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        value);

    assertEquals(true, ss
        .isWithinScope(createEntry(dn, getObjectClasses())));
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testMatches19() throws Exception {
    DN dn = DN.decode("dc=abc, dc=sun, dc=com");

    String value = "{ base \"dc=sun, dc=com\", "
        + "specificationFilter item:person }";
    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        value);

    assertEquals(true, ss
        .isWithinScope(createEntry(dn, getObjectClasses())));
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testMatches20() throws Exception {
    DN dn = DN.decode("dc=abc, dc=sun, dc=com");

    String value = "{ base \"dc=sun, dc=com\", "
        + "specificationFilter item:organization }";
    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        value);

    assertEquals(false, ss
        .isWithinScope(createEntry(dn, getObjectClasses())));
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testMatches21() throws Exception {
    DN dn = DN.decode("dc=abc, dc=sun, dc=com");

    String value = "{ base \"dc=sun, dc=com\", "
        + "specificationFilter not:item:person }";
    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        value);

    assertEquals(false, ss
        .isWithinScope(createEntry(dn, getObjectClasses())));
  }

  /**
   * Tests the {@link RFC3672SubtreeSpecification#isWithinScope(Entry)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testMatches22() throws Exception {
    DN dn = DN.decode("dc=abc, dc=sun, dc=com");

    String value = "{ base \"dc=sun, dc=com\", "
        + "specificationFilter not:item:organization }";
    SubtreeSpecification ss = RFC3672SubtreeSpecification.valueOf(rootDN,
        value);

    assertEquals(true, ss
        .isWithinScope(createEntry(dn, getObjectClasses())));
  }
}
