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
package org.opends.server.replication.plugin;

import org.opends.server.replication.ReplicationTestCase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Test basic computations done by the GenerationIdChecksum class that is used
 * to compute generation ids for domains.
 */
public class GenerationIdChecksumTest extends ReplicationTestCase
{

  /**
   * Basic usage test (reset and single update method)
   */
  @Test
  public void testResetAndSingleUpdate()
  {
    GenerationIdChecksum checksum = new GenerationIdChecksum();

    // Default value test
    assertEquals(checksum.getValue(), 0L);

    // Update method simple version test
    checksum.update(3);
    assertEquals(checksum.getValue(), 3L);
    checksum.update(4);
    assertEquals(checksum.getValue(), 7L);
    checksum.update(125);
    assertEquals(checksum.getValue(), 132L);

    // Reset test
    checksum.reset();
    assertEquals(checksum.getValue(), 0L);

    // Update method simple version test, again
    checksum.update(101);
    assertEquals(checksum.getValue(), 101L);
    checksum.update(2);
    assertEquals(checksum.getValue(), 103L);
    checksum.update(66);
    assertEquals(checksum.getValue(), 169L);
  }

  /**
   * Provider for testArrayUpdate method
   */
  @DataProvider(name = "arrayUpdateProvider")
  protected Object[][] arrayUpdateProvider()
  {
    return new Object[][] {
      {new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, 0, 10, 55},
      {new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, 0, 1, 1},
      {new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, 0, 2, 3},
      {new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, 0, 3, 6},
      {new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, 3, 1, 4},
      {new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, 3, 2, 9},
      {new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, 3, 3, 15},
      {new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, 7, 1, 8},
      {new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, 7, 2, 17},
      {new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, 7, 3, 27},
      {new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, 9, 1, 10},
      {new byte[]{2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, 0, 10, 65},
      {new byte[]{118, 119, 120, 121, 122, 123, 124, 125, 126, 127}, 0, 10, 1225}};
  }

  /**
   * Test of update method, array version
   */
  @Test(dataProvider = "arrayUpdateProvider")
  public void testArrayUpdate(byte[] b, int off, int len, long expectedChecksum)
  {
    GenerationIdChecksum checksum = new GenerationIdChecksum();
    checksum.update(b, off, len);
    assertEquals(checksum.getValue(), expectedChecksum);
  }
}
