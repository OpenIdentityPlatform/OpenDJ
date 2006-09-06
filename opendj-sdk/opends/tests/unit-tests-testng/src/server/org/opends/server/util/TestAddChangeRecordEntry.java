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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.opends.server.types.DN;
import org.opends.server.types.LDIFImportConfig;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * This class defines a set of tests for the
 * {@link org.opends.server.util.AddChangeRecordEntry} class.
 */
public final class TestAddChangeRecordEntry extends UtilTestCase {
  // An empty LDIF reader.
  private LDIFReader emptyReader;

  /**
   * Once-only initialization.
   * 
   * @throws IOException
   *           If an I/O error occurred.
   */
  @BeforeClass
  public void setUp() throws IOException {
    InputStream stream = new ByteArrayInputStream(new byte[0]);
    LDIFImportConfig config = new LDIFImportConfig(stream);
    emptyReader = new LDIFReader(config);
  }

  /**
   * Tests the constructor with null DN.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testConstructorNullDN() throws Exception {
    AddChangeRecordEntry entry = new AddChangeRecordEntry(null, emptyReader);

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
    AddChangeRecordEntry entry = new AddChangeRecordEntry(new DN(),
        emptyReader);

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

    AddChangeRecordEntry entry = new AddChangeRecordEntry(testDN1,
        emptyReader);

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
    AddChangeRecordEntry entry = new AddChangeRecordEntry(null, emptyReader);

    Assert.assertEquals(entry.getChangeOperationType(),
        ChangeOperationType.ADD);
  }

  /**
   * Tests parse and getAttributes methods.
   * <p>
   * Due to tight coupling between the
   * {@link AddChangeRecordEntry#parse(java.util.LinkedList, long)}
   * method and the {@link LDIFReader} class it is not easy to test the
   * {@link AddChangeRecordEntry#getAttributes()} method. Instead, we'll
   * test that in the {@link LDIFReader} test suite.
   * 
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(enabled = false)
  public void testGetAttributes() throws Exception {
    // FIXME: Due to tight coupling between the parse() method and the
    // LDIFReader it is not easy to test the getAttributes() method.
    // Instead, we'll test that in the LDIFReader test suite.
  }

}
