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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
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
  /** Checksum to be returned. */
  private long checksum;

  /** This is the generation id for an empty backend. */
  public static final long EMPTY_BACKEND_GENERATION_ID = 48L;

  /** Update the checksum with one added byte. */
  private void updateWithOneByte(byte b)
  {
    /*
     * The "end of line" code is CRLF under windows but LF on UNIX. So to get
     * the same checksum value on every platforms, we always exclude the CR and
     * LF characters from the computation.
     */
    if (b != 0x0D && b != 0x0A) // CR=0D and LF=0A
    {
      checksum += b;
    }
  }

  @Override
  public void update(int b)
  {
    updateWithOneByte((byte) b);
  }

  @Override
  public void update(byte[] b, int off, int len)
  {
    for (int i = off; i < off + len; i++)
    {
      updateWithOneByte(b[i]);
    }
  }

  @Override
  public long getValue()
  {
    if (checksum != 0L)
    {
      return checksum;
    } else
    {
      /*
       * Computing an empty backend writes the number of entries (0) only,
       * will not be added to the checksum as no entries will follow. To treat
       * this special case, and to keep consistency with old versions, in that
       * case we hardcode and return the generation id value for an empty
       * backend.
       */
      return EMPTY_BACKEND_GENERATION_ID;
    }
  }

  @Override
  public void reset()
  {
    checksum = 0L;
  }
}
