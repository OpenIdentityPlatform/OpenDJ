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
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.protocols.ldap;


import java.io.IOException;

import org.forgerock.opendj.io.*;
import org.forgerock.opendj.ldap.ByteString;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.util.ServerConstants.*;


/**
 * This class defines the structures and methods for an LDAP intermediate
 * response protocol op, which is used to provide information to a client before
 * the final response for an operation.
 */
public class IntermediateResponseProtocolOp
       extends ProtocolOp
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The value for this intermediate response. */
  private ByteString value;

  /** The OID for this intermediate response. */
  private String oid;



  /**
   * Creates a new intermediate protocol op with the specified OID and no
   * value.
   *
   * @param  oid  The OID for this intermediate response.
   */
  public IntermediateResponseProtocolOp(String oid)
  {
    this.oid   = oid;
    this.value = null;
  }



  /**
   * Creates a new intermediate response protocol op with the specified OID and
   * value.
   *
   * @param  oid    The OID for this intermediate response.
   * @param  value  The value for this intermediate response.
   */
  public IntermediateResponseProtocolOp(String oid, ByteString value)
  {
    this.oid   = oid;
    this.value = value;
  }



  /**
   * Retrieves the OID for this intermediate response.
   *
   * @return  The OID for this intermediate response, or <CODE>null</CODE> if
   *          there is no OID.
   */
  public String getOID()
  {
    return oid;
  }


  /**
   * Retrieves the value for this intermediate response.
   *
   * @return  The value for this intermediate response, or <CODE>null</CODE> if
   *          there is no value.
   */
  public ByteString getValue()
  {
    return value;
  }



  /**
   * Retrieves the BER type for this protocol op.
   *
   * @return  The BER type for this protocol op.
   */
  public byte getType()
  {
    return OP_TYPE_INTERMEDIATE_RESPONSE;
  }



  /**
   * Retrieves the name for this protocol op type.
   *
   * @return  The name for this protocol op type.
   */
  public String getProtocolOpName()
  {
    return "Intermediate Response";
  }

  /**
   * Writes this protocol op to an ASN.1 output stream.
   *
   * @param stream The ASN.1 output stream to write to.
   * @throws IOException If a problem occurs while writing to the stream.
   */
  public void write(ASN1Writer stream) throws IOException
  {
    stream.writeStartSequence(OP_TYPE_INTERMEDIATE_RESPONSE);

    if (oid != null)
    {
      stream.writeOctetString(TYPE_INTERMEDIATE_RESPONSE_OID, oid);
    }

    if (value != null)
    {
      stream.writeOctetString(TYPE_INTERMEDIATE_RESPONSE_VALUE, value);
    }

    stream.writeEndSequence();
  }



  /**
   * Appends a string representation of this LDAP protocol op to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the string should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("IntermediateResponse(oid=").append(oid);
    if (value != null)
    {
      buffer.append(", value=").append(value);
    }
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
  public void toString(StringBuilder buffer, int indent)
  {
    StringBuilder indentBuf = new StringBuilder(indent);
    for (int i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    buffer.append(indentBuf);
    buffer.append("Intermediate Response");
    buffer.append(EOL);

    if (oid != null)
    {
      buffer.append(indentBuf);
      buffer.append("  OID:  ");
      buffer.append(oid);
      buffer.append(EOL);
    }

    if (value != null)
    {
      buffer.append(indentBuf);
      buffer.append("  Value:");
      buffer.append(EOL);
      buffer.append(value.toHexPlusAsciiString(indent+4));
    }
  }
}

