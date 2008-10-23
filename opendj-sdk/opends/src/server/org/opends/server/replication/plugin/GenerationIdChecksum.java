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

import java.util.zip.Checksum;

/**
 * This class computes the generation id used for a replication domain.
 * It is a checksum based on some special entries/attributes of the domain.
 * The written stream to this class is the LDIF representation of the entries
 * we are interested in for computing the generation id. The current
 * implementation simply does the sum of each written byte and stores the value
 * in a long. We do not care about the cycling long as the probability of 2
 * data sets having the same checksum is very low.
 */
public class GenerationIdChecksum implements Checksum
{
  // Checksum to be returned.
  private long checksum = 0L;

  /**
   * Update the checksum with one added byte.
   */
  private void updateWithOneByte(byte b)
  {
    checksum += (long) b;
  }

  /**
   * {@inheritDoc}
   */
  public void update(int b)
  {
    updateWithOneByte((byte) b);
  }

  /**
   * {@inheritDoc}
   */
  public void update(byte[] b, int off, int len)
  {
    for (int i = off; i < (off + len); i++)
    {
      updateWithOneByte(b[i]);
    }
  }

  /**
   * {@inheritDoc}
   */
  public long getValue()
  {
    return checksum;
  }

  /**
   * {@inheritDoc}
   */
  public void reset()
  {
    checksum = 0L;
  }
}
