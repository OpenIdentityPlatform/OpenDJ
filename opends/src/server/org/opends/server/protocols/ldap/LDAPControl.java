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
import org.opends.server.types.Control;
import org.opends.server.types.ByteString;

import static org.opends.server.util.ServerConstants.*;


/**
 * This class defines the data structures and methods to use when interacting
 * with a generic LDAP control.
 */
public class LDAPControl extends Control
{
  // The control value.
  private ByteString value;



  /**
   * Creates a new LDAP control with the specified OID.  It will not be
   * critical, and will not have a value.
   *
   * @param  oid  The OID for this LDAP control.
   */
  public LDAPControl(String oid)
  {
    super(oid, false);
  }



  /**
   * Creates a new LDAP control with the specified OID and criticality.  It will
   * not have a value.
   *
   * @param  oid         The OID for this LDAP control.
   * @param  isCritical  Indicates whether this control should be considered
   *                     critical.
   */
  public LDAPControl(String oid, boolean isCritical)
  {
    super(oid, isCritical);
  }



  /**
   * Creates a new LDAP control with the specified OID, criticality, and value.
   *
   * @param  oid         The OID for this LDAP control.
   * @param  isCritical  Indicates whether this control should be considered
   *                     critical.
   * @param  value       The value for this LDAP control.
   */
  public LDAPControl(String oid, boolean isCritical, ByteString value)
  {
    super(oid, isCritical);
    this.value = value;
  }


  /**
   * Retrieves the value for this control.
   *
   * @return  The value for this control, or <CODE>null</CODE> if
   *          there is no value.
   */
  public final ByteString getValue()
  {
    return value;
  }



  /**
   * Indicates whether this control has a value.
   *
   * @return  <CODE>true</CODE> if this control has a value, or
   *          <CODE>false</CODE> if it does not.
   */
  public final boolean hasValue()
  {
    return (value != null);
  }

  /**
   * {@inheritDoc}
   */
  public void writeValue(ASN1Writer stream) throws IOException
  {
    if (value != null)
    {
      stream.writeOctetString(value);
    }
  }



  /**
   * Appends a string representation of this LDAP control to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("LDAPControl(oid=");
    buffer.append(getOID());
    buffer.append(", criticality=");
    buffer.append(isCritical());

    if (value != null)
    {
      buffer.append(", value=");
      value.toHexPlusAscii(buffer, 4);
    }

    buffer.append(")");
  }



  /**
   * Appends a multi-line string representation of this LDAP control to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   * @param  indent  The number of spaces to indent the information.
   */
  public void toString(StringBuilder buffer, int indent)
  {
    StringBuilder indentBuf = new StringBuilder(indent);
    for (int i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    buffer.append(indentBuf);
    buffer.append("LDAP Control");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  OID:  ");
    buffer.append(getOID());
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Criticality:  ");
    buffer.append(isCritical());
    buffer.append(EOL);

    if (value != null)
    {
      buffer.append(indentBuf);
      buffer.append("  Value:");
      value.toHexPlusAscii(buffer, indent+4);
    }
  }
}

