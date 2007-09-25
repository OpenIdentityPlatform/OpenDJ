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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.admin.ads;

import javax.naming.ldap.Control;

/**
 * This class implements the LDAP subtree delete control for JNDI.
 */
public class SubtreeDeleteControl implements Control
{
  /**
   * The serial version identifier required to satisfy the compiler
   * because this class implements <CODE>javax.ldap.naming.Control</CODE>,
   * which extends the <CODE>java.io.Serializable</CODE> interface.
   * This value was generated using the <CODE>serialver</CODE>
   * command-line utility included with the Java SDK.
   */
  static final long serialVersionUID = 3941576361457157921L;

  /**
    * Retrieves the object identifier assigned for the LDAP control.
    *
    * @return The non-null object identifier string.
    */
  public String getID() {
    return "1.2.840.113556.1.4.805";
  }

  /**
    * Determines the criticality of the LDAP control.
    * A critical control must not be ignored by the server.
    * In other words, if the server receives a critical control
    * that it does not support, regardless of whether the control
    * makes sense for the operation, the operation will not be performed
    * and an <tt>OperationNotSupportedException</tt> will be thrown.
    * @return true if this control is critical; false otherwise.
    */
  public boolean isCritical() {
    return true;
  }

  /**
    * Retrieves the ASN.1 BER encoded value of the LDAP control.
    * The result is the raw BER bytes including the tag and length of
    * the control's value. It does not include the controls OID or criticality.
    *
    * Null is returned if the value is absent.
    *
    * @return A possibly null byte array representing the ASN.1 BER encoded
    *         value of the LDAP control.
    */
  public byte[] getEncodedValue() {
    return new byte[] {};
  }

}
