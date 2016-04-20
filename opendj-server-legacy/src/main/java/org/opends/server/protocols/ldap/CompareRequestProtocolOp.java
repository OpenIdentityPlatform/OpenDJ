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


import java.io.IOException;

import org.forgerock.opendj.io.*;
import org.forgerock.opendj.ldap.ByteString;

import org.forgerock.i18n.slf4j.LocalizedLogger;
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
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The assertion value for this compare request. */
  private ByteString assertionValue;

  /** The DN for this compare request. */
  private ByteString dn;

  /** The attribute type for this compare request. */
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
  @Override
  public byte getType()
  {
    return OP_TYPE_COMPARE_REQUEST;
  }



  /**
   * Retrieves the name for this protocol op type.
   *
   * @return  The name for this protocol op type.
   */
  @Override
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
  @Override
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
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("CompareRequest(dn=");
    buffer.append(dn);
    buffer.append(", attribute=");
    buffer.append(attributeType);
    buffer.append(", value=");
    buffer.append(assertionValue);
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
    buffer.append("Compare Request");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Target DN:  ");
    buffer.append(dn);
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Attribute Type:  ");
    buffer.append(attributeType);
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Assertion Value:");
    buffer.append(EOL);
    buffer.append(assertionValue.toHexPlusAsciiString(indent+4));
  }
}

