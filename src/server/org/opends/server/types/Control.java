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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.types;

import org.opends.server.protocols.asn1.ASN1Writer;

import java.io.IOException;


/**
 * This class defines a data structure that holds information about a
 * control that can be included in a request or response.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=true,
     mayInvoke=true)
public abstract class Control
{
  // The criticality for this control.
  private boolean isCritical;

  // The OID for this control.
  private String oid;



  /**
   * Creates a new control with no value.
   *
   * @param  oid         The OID for this control.
   * @param  isCritical  Indicates whether this control should be
   *                     considered critical in processing the
   *                     request.
   */
  protected Control(String oid, boolean isCritical)
  {
    this.oid        = oid;
    this.isCritical = isCritical;
  }



  /**
   * Retrieves the OID for this control.
   *
   * @return  The OID for this control.
   */
  public final String getOID()
  {
    return oid;
  }


  /**
   * Indicates whether this control should be considered critical in
   * processing the request.
   *
   * @return  <CODE>true</CODE> if this code should be considered
   *          critical, or <CODE>false</CODE> if not.
   */
  public final boolean isCritical()
  {
    return isCritical;
  }



  /**
   * Retrieves a string representation of this control.
   *
   * @return  A string representation of this control.
   */
  @Override
  public final String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }

  /**
   * Writes this control to an ASN.1 writer.
   *
   * @param writer The ASN.1 writer to use.
   * @throws IOException If a problem occurs while writing to the
   *                     stream.
   */
  public final void write(ASN1Writer writer) throws IOException
  {
    writer.writeStartSequence();
    writer.writeOctetString(getOID());
    if(isCritical())
    {
      writer.writeBoolean(isCritical());
    }
    writeValue(writer);
    writer.writeEndSequence();
  }

  /**
   * Writes this control's value to an ASN.1 writer. The value
   * (if any) must be written as an ASN1OctetString.
   *
   * @param writer The ASN.1 writer to use.
   * @throws IOException If a problem occurs while writing to the
   *                     stream.
   */
  protected abstract void writeValue(ASN1Writer writer)
      throws IOException;



  /**
   * Appends a string representation of this control to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("Control(oid=");
    buffer.append(oid);
    buffer.append(",isCritical=");
    buffer.append(isCritical);
    buffer.append(")");
  }
}

