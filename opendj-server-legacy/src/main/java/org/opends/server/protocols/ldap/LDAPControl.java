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
import org.opends.server.types.Control;
import org.forgerock.opendj.ldap.ByteString;

import static org.opends.server.util.ServerConstants.*;

/**
 * This class defines the data structures and methods to use when interacting
 * with a generic LDAP control.
 */
public class LDAPControl extends Control
{
  /** The control value. */
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
    return value != null;
  }

  @Override
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
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("LDAPControl(oid=");
    buffer.append(getOID());
    buffer.append(", criticality=");
    buffer.append(isCritical());

    if (value != null)
    {
      buffer.append(", value=");
      buffer.append(value.toHexPlusAsciiString(4));
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
      buffer.append(value.toHexPlusAsciiString(indent+4));
    }
  }
}
