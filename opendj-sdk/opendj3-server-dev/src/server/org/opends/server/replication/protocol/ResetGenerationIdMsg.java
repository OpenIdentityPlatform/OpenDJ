/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2014 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import java.util.zip.DataFormatException;

/**
 * This message is used by an LDAP server to communicate to the topology
 * that the generation must be reset for the domain.
 */
public class ResetGenerationIdMsg extends ReplicationMsg
{
  private final long generationId;

  /**
   * Creates a new message.
   * @param generationId The new reference value of the generationID.
   */
  public ResetGenerationIdMsg(long generationId)
  {
    this.generationId = generationId;
  }

  /**
   * Creates a new GenerationIdMessage from its encoded form.
   *
   * @param in The byte array containing the encoded form of the
   *           WindowMessage.
   * @throws DataFormatException If the byte array does not contain a valid
   *                             encoded form of the WindowMessage.
   */
  ResetGenerationIdMsg(byte[] in) throws DataFormatException
  {
    final ByteArrayScanner scanner = new ByteArrayScanner(in);
    if (scanner.nextByte() != MSG_TYPE_RESET_GENERATION_ID)
    {
      throw new DataFormatException(
          "input is not a valid GenerationId Message");
    }
    generationId = scanner.nextLongUTF8();
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes(short protocolVersion)
  {
    final ByteArrayBuilder builder = new ByteArrayBuilder();
    builder.appendByte(MSG_TYPE_RESET_GENERATION_ID);
    builder.appendLongUTF8(generationId);
    return builder.toByteArray();
  }

  /**
   * Returns the generation Id set in this message.
   * @return the value of the generation ID.
   *
   */
  public long getGenerationId()
  {
    return this.generationId;
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return "ResetGenerationIdMsg content: " +
      "\ngenerationId: " + generationId;
  }
}
