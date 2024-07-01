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
package org.opends.server.protocols.ldap;



import org.forgerock.opendj.io.ASN1Writer;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.util.ServerConstants.*;

import java.io.IOException;


/**
 * This class defines the structures and methods for an LDAP abandon request
 * protocol op, which is used to indicate that the server should stop processing
 * a previously requested operation.
 */
public class AbandonRequestProtocolOp
       extends ProtocolOp
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The message ID of the operation to abandon. */
  private int idToAbandon;



  /**
   * Creates a new abandon request protocol op to abandon the specified
   * operation.
   *
   * @param  idToAbandon  The message ID of the operation to abandon.
   */
  public AbandonRequestProtocolOp(int idToAbandon)
  {
    this.idToAbandon = idToAbandon;
  }



  /**
   * Retrieves the message ID of the operation to abandon.
   *
   * @return  The message ID of the operation to abandon.
   */
  public int getIDToAbandon()
  {
    return idToAbandon;
  }



  /**
   * Retrieves the BER type for this protocol op.
   *
   * @return  The BER type for this protocol op.
   */
  @Override
  public byte getType()
  {
    return OP_TYPE_ABANDON_REQUEST;
  }



  /**
   * Retrieves the name for this protocol op type.
   *
   * @return  The name for this protocol op type.
   */
  @Override
  public String getProtocolOpName()
  {
    return "Abandon Request";
  }

  /**
   * Writes this protocol op to an ASN.1 output stream.
   *
   * @param stream The ASN.1 output stream to write to.
   * @throws IOException If a problem occurs while writing to the stream.
   */
  @Override
  public void write(ASN1Writer stream) throws IOException
  {
    stream.writeInteger(OP_TYPE_ABANDON_REQUEST, idToAbandon);
  }


  /**
   * Appends a string representation of this LDAP protocol op to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the string should be appended.
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("AbandonRequest(idToAbandon=");
    buffer.append(idToAbandon);
    buffer.append(")");
  }



  /**
   * Appends a multi-line string representation of this LDAP protocol op to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   * @param  indent  The number of spaces from the margin that the lines should
   *                 be indented.
   */
  @Override
  public void toString(StringBuilder buffer, int indent)
  {
    StringBuilder indentBuf = new StringBuilder(indent);
    for (int i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    buffer.append(indentBuf);
    buffer.append("Abandon Request");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  ID to Abandon:  ");
    buffer.append(idToAbandon);
    buffer.append(EOL);
  }
}

