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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.replication.protocol;

import java.io.Serializable;
import java.util.zip.DataFormatException;


/**
 * This message is used by an LDAP server to communicate to the topology
 * that the generation must be reset for the domain.
 */
public class ResetGenerationId extends ReplicationMessage implements
    Serializable
{
  private static final long serialVersionUID = 7657049716115572226L;


  /**
   * Creates a new message.
   */
  public ResetGenerationId()
  {
  }

  /**
   * Creates a new GenerationIdMessage from its encoded form.
   *
   * @param in The byte array containing the encoded form of the
   *           WindowMessage.
   * @throws DataFormatException If the byte array does not contain a valid
   *                             encoded form of the WindowMessage.
   */
  public ResetGenerationId(byte[] in) throws DataFormatException
  {
    if (in[0] != MSG_TYPE_RESET_GENERATION_ID)
      throw new
      DataFormatException("input is not a valid GenerationId Message");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes()
  {
    int length = 1;
    byte[] resultByteArray = new byte[length];

    /* put the type of the operation */
    resultByteArray[0] = MSG_TYPE_RESET_GENERATION_ID;
    return resultByteArray;
  }
}
