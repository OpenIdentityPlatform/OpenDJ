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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.util;

import org.opends.server.TestCaseUtils;
import org.forgerock.opendj.ldap.DN;
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

    /** {@inheritDoc} */
    @Override
    public ChangeOperationType getChangeOperationType() {
      // Will not use.
      return null;
    }



    /** {@inheritDoc} */
    @Override
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
    MyChangeRecordEntry entry = new MyChangeRecordEntry(DN.rootDN());

    Assert.assertEquals(entry.getDN(), DN.rootDN());
  }

  /**
   * Tests the constructor with non-null DN.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testConstructorNonNullDN() throws Exception {
    DN testDN1 = DN.valueOf("dc=hello, dc=world");
    DN testDN2 = DN.valueOf("dc=hello, dc=world");

    MyChangeRecordEntry entry = new MyChangeRecordEntry(testDN1);

    Assert.assertEquals(entry.getDN(), testDN2);
  }
}
