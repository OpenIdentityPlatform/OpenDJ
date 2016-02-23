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
 * Portions Copyright 2013-2014 ForgeRock AS.
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
