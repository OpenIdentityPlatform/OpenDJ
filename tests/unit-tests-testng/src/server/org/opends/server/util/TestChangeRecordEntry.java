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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.util;

import org.opends.server.TestCaseUtils;
import org.opends.server.types.DN;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * This class defines a set of tests for the abstract
 * {@link org.opends.server.util.ChangeRecordEntry} class.
 * <p>
 * Since the class is abstract and cannot be abstract, this test suite
 * will run tests against a derived inner class.
 */
public final class TestChangeRecordEntry extends UtilTestCase {
  /**
   * Perform tests against this inner class.
   */
  private static final class MyChangeRecordEntry extends ChangeRecordEntry {

    /**
     * Create a new test record.
     *
     * @param dn
     *          The test record's DN.
     */
    public MyChangeRecordEntry(DN dn) {
      super(dn);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ChangeOperationType getChangeOperationType() {
      // Will not use.
      return null;
    }



    /**
     * {@inheritDoc}
     */
    @Override()
    public String toString()
    {
      return "MyChangeRecordEntry()";
    }
  }

  /**
   * Once-only initialization.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @BeforeClass
  public void setUp() throws Exception {
    // This test suite depends on having the schema available, so we'll
    // start the server.
    TestCaseUtils.startServer();
  }

  /**
   * Tests the constructor with null DN.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = { NullPointerException.class,
                               AssertionError.class })
  public void testConstructorNullDN() throws Exception {
    new MyChangeRecordEntry(null);
  }

  /**
   * Tests the constructor with empty DN.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testConstructorEmptyDN() throws Exception {
    MyChangeRecordEntry entry = new MyChangeRecordEntry(DN.nullDN());

    Assert.assertEquals(entry.getDN(), DN.nullDN());
  }

  /**
   * Tests the constructor with non-null DN.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testConstructorNonNullDN() throws Exception {
    DN testDN1 = DN.decode("dc=hello, dc=world");
    DN testDN2 = DN.decode("dc=hello, dc=world");

    MyChangeRecordEntry entry = new MyChangeRecordEntry(testDN1);

    Assert.assertEquals(entry.getDN(), testDN2);
  }
}
