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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk.controls;



import org.opends.sdk.util.ByteString;
import org.opends.sdk.util.StaticUtils;



/**
 * A generic raw request. A raw control is a control whose parameters
 * have not been fully decoded.
 * <p>
 * TODO: this should be hooked into the remaining controls class
 * hierarchy.
 * <p>
 * TODO: push ASN1 encoding into ASN1 package.
 */
public final class GenericControl extends Control
{

  // The control value.
  private final ByteString value;



  /**
   * Creates a new control with the specified OID. It will not be
   * critical, and will not have a value.
   * 
   * @param oid
   *          The OID for this control.
   */
  public GenericControl(String oid)
  {
    this(oid, false, null);
  }



  /**
   * Creates a new raw control with the specified OID and criticality.
   * It will not have a value.
   * 
   * @param oid
   *          The OID for this control.
   * @param isCritical
   *          Indicates whether this control should be considered
   *          critical.
   */
  public GenericControl(String oid, boolean isCritical)
  {
    this(oid, isCritical, null);
  }



  /**
   * Creates a new raw control with the specified OID, criticality, and
   * value.
   * 
   * @param oid
   *          The OID for this control.
   * @param isCritical
   *          Indicates whether this control should be considered
   *          critical.
   * @param value
   *          The value for this control.
   */
  public GenericControl(String oid, boolean isCritical, ByteString value)
  {
    super(oid, isCritical);
    this.value = value;
  }



  /**
   * Retrieves the value for this control.
   * 
   * @return The value for this control, or <CODE>null</CODE> if there
   *         is no value.
   */
  @Override
  public ByteString getValue()
  {
    return value;
  }



  /**
   * Indicates whether this control has a value.
   * 
   * @return <CODE>true</CODE> if this control has a value, or
   *         <CODE>false</CODE> if it does not.
   */
  @Override
  public boolean hasValue()
  {
    return (value != null);
  }



  /**
   * Appends a string representation of this control to the provided
   * buffer.
   * 
   * @param buffer
   *          The buffer to which the information should be appended.
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("Control(oid=");
    buffer.append(getOID());
    buffer.append(", criticality=");
    buffer.append(isCritical());

    if (value != null)
    {
      buffer.append(", value=");
      StaticUtils.toHexPlusAscii(value, buffer, 4);
    }

    buffer.append(")");
  }

}
