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
import org.forgerock.opendj.ldap.ByteString;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.util.ServerConstants.*;

import java.io.IOException;


/**
 * This class defines the structures and methods for an LDAP delete request
 * protocol op, which is used to remove an entry from the Directory Server.
 */
public class DeleteRequestProtocolOp
       extends ProtocolOp
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The DN for this delete request. */
  private ByteString dn;



  /**
   * Creates a new delete request protocol op with the specified DN.
   *
   * @param  dn  The DN for this delete request protocol op.
   */
  public DeleteRequestProtocolOp(ByteString dn)
  {
    this.dn = dn;
  }



  /**
   * Retrieves the DN for this delete request.
   *
   * @return  The DN for this delete request.
   */
  public ByteString getDN()
  {
    return dn;
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
    stream.writeOctetString(OP_TYPE_DELETE_REQUEST, dn);
  }



  /**
   * Retrieves the BER type for this protocol op.
   *
   * @return  The BER type for this protocol op.
   */
  @Override
  public byte getType()
  {
    return OP_TYPE_DELETE_REQUEST;
  }



  /**
   * Retrieves the name for this protocol op type.
   *
   * @return  The name for this protocol op type.
   */
  @Override
  public String getProtocolOpName()
  {
    return "Delete Request";
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
    buffer.append("DeleteRequest(dn=");
    buffer.append(dn);
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
    buffer.append("Delete Request");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Entry DN:  ");
    buffer.append(dn);
    buffer.append(EOL);
  }
}

