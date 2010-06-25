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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
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
   * This is the generation id for an empty backend.
   */
  public static final long EMPTY_BACKEND_GENERATION_ID = 48L;

  /**
   * Update the checksum with one added byte.
   */
  private void updateWithOneByte(byte b)
  {
    /**
     * The "end of line" code is CRLF under windows but LF on UNIX. So to get
     * the same checksum value on every platforms, we always exclude the CR and
     * LF characters from the computation.
     */
    if ((b != 0x0D) && (b != 0x0A)) // CR=0D and LF=0A
    {
      checksum += (long) b;
    }
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
    if (checksum != 0L)
    {
      return checksum;
    } else
    {
      // Computing an empty backend writes the number of entries (0) only, which
      // will not be added to the checksum as no entries will follow. To treat
      // this special case, and to keep consistency with old versions, in that
      // case we hardcode and return the generation id value for an empty
      // backend.
      return EMPTY_BACKEND_GENERATION_ID;
    }
  }

  /**
   * {@inheritDoc}
   */
  public void reset()
  {
    checksum = 0L;
  }
}
