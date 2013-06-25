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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.ldap;


import java.io.IOException;

import org.opends.server.protocols.asn1.*;
import org.opends.server.types.ByteString;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.util.ServerConstants.*;


/**
 * This class defines the structures and methods for an LDAP compare request
 * protocol op, which is used to determine whether a particular entry contains
 * a specified attribute value.
 */
public class CompareRequestProtocolOp
       extends ProtocolOp
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The assertion value for this compare request.
  private ByteString assertionValue;

  // The DN for this compare request.
  private ByteString dn;

  // The attribute type for this compare request.
  private String attributeType;



  /**
   * Creates a new compare request protocol op with the provided information.
   *
   * @param  dn              The DN for this compare request.
   * @param  attributeType   The attribute type for this compare request.
   * @param  assertionValue  The assertion value for this compare request.
   */
  public CompareRequestProtocolOp(ByteString dn, String attributeType,
                                  ByteString assertionValue)
  {
    this.dn             = dn;
    this.attributeType  = attributeType;
    this.assertionValue = assertionValue;
  }



  /**
   * Retrieves the DN for this compare request.
   *
   * @return  The DN for this compare request.
   */
  public ByteString getDN()
  {
    return dn;
  }



  /**
   * Retrieves the attribute type for this compare request.
   *
   * @return  The attribute type for this compare request.
   */
  public String getAttributeType()
  {
    return attributeType;
  }



  /**
   * Retrieves the assertion value for this compare request.
   *
   * @return  The assertion value for this compare request.
   */
  public ByteString getAssertionValue()
  {
    return assertionValue;
  }



  /**
   * Retrieves the BER type for this protocol op.
   *
   * @return  The BER type for this protocol op.
   */
  public byte getType()
  {
    return OP_TYPE_COMPARE_REQUEST;
  }



  /**
   * Retrieves the name for this protocol op type.
   *
   * @return  The name for this protocol op type.
   */
  public String getProtocolOpName()
  {
    return "Compare Request";
  }

  /**
   * Writes this protocol op to an ASN.1 output stream.
   *
   * @param stream The ASN.1 output stream to write to.
   * @throws IOException If a problem occurs while writing to the stream.
   */
  public void write(ASN1Writer stream) throws IOException
  {
    stream.writeStartSequence(OP_TYPE_COMPARE_REQUEST);
    stream.writeOctetString(dn);

    stream.writeStartSequence();
    stream.writeOctetString(attributeType);
    stream.writeOctetString(assertionValue);
    stream.writeEndSequence();

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
    buffer.append("CompareRequest(dn=");
    buffer.append(dn.toString());
    buffer.append(", attribute=");
    buffer.append(attributeType);
    buffer.append(", value=");
    buffer.append(assertionValue.toString());
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
    buffer.append("Compare Request");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Target DN:  ");
    buffer.append(dn.toString());
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Attribute Type:  ");
    buffer.append(attributeType);
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Assertion Value:");
    buffer.append(EOL);
    assertionValue.toHexPlusAscii(buffer, indent+4);
  }
}

