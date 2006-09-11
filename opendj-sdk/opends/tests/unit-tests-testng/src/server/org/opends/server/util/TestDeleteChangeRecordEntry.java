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
package org.opends.server.util;

import org.opends.server.TestCaseUtils;
import org.opends.server.types.DN;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * This class defines a set of tests for the
 * {@link org.opends.server.util.DeleteChangeRecordEntry} class.
 * <p>
 * Note that we test shared behaviour with the abstract
 * {@link org.opends.server.util.ChangeRecordEntry} class in case it has
 * been overridden.
 */
public final class TestDeleteChangeRecordEntry extends UtilTestCase {
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
  @Test
  public void testConstructorNullDN() throws Exception {
    DeleteChangeRecordEntry entry = new DeleteChangeRecordEntry(null);

    Assert.assertEquals(entry.getDN(), new DN());
  }

  /**
   * Tests the constructor with empty DN.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testConstructorEmptyDN() throws Exception {
    DeleteChangeRecordEntry entry = new DeleteChangeRecordEntry(new DN());

    Assert.assertEquals(entry.getDN(), new DN());
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

    DeleteChangeRecordEntry entry = new DeleteChangeRecordEntry(testDN1);

    Assert.assertEquals(entry.getDN(), testDN2);
  }

  /**
   * Tests the change operation type is correct.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testChangeOperationType() throws Exception {
    DeleteChangeRecordEntry entry = new DeleteChangeRecordEntry(null);

    Assert.assertEquals(entry.getChangeOperationType(),
        ChangeOperationType.DELETE);
  }

}
