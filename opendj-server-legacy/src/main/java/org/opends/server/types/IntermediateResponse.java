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
package org.opends.server.types;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.forgerock.opendj.ldap.ByteString;

/**
 * This class defines a data structure for holding information that
 * may be sent to the client in the form of an intermediate response.
 * It may contain an OID, value, and/or set of controls.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayInvoke=true)
public final class IntermediateResponse
{
  /** The value for this intermediate response. */
  private ByteString value;
  /** The set of controls for this intermediate response. */
  private final List<Control> controls;
  /** The operation with which this intermediate response is associated. */
  private final Operation operation;
  /** The OID for this intermediate response. */
  private String oid;

  /**
   * Creates a new intermediate response with the provided
   * information.
   *
   * @param  operation  The operation with which this intermediate
   *                    response is associated.
   * @param  oid        The OID for this intermediate response.
   * @param  value      The value for this intermediate response.
   * @param  controls   The set of controls to for this intermediate
   *                    response.
   */
  public IntermediateResponse(Operation operation, String oid,
                              ByteString value,
                              List<Control> controls)
  {
    this.operation = operation;
    this.oid       = oid;
    this.value     = value;

    if (controls != null)
    {
      this.controls = controls;
    }
    else
    {
      this.controls = new ArrayList<>(0);
    }
  }

  /**
   * Retrieves the operation with which this intermediate response
   * message is associated.
   *
   * @return  The operation with which this intermediate response
   *          message is associated.
   */
  public Operation getOperation()
  {
    return operation;
  }

  /**
   * Retrieves the OID for this intermediate response.
   *
   * @return  The OID for this intermediate response, or
   *          <CODE>null</CODE> if there is none.
   */
  public String getOID()
  {
    return oid;
  }

  /**
   * Specifies the OID for this intermediate response.
   *
   * @param  oid  The OID for this intermediate response.
   */
  public void setOID(String oid)
  {
    this.oid = oid;
  }

  /**
   * Retrieves the value for this intermediate response.
   *
   * @return  The value for this intermediate response, or
   *          <CODE>null</CODE> if there is none.
   */
  public ByteString getValue()
  {
    return value;
  }

  /**
   * Specifies the value for this intermediate response.
   *
   * @param  value  The value for this intermediate response.
   */
  public void setValue(ByteString value)
  {
    this.value = value;
  }

  /**
   * Retrieves the set of controls for this intermediate response.
   * The contents of the list may be altered by intermediate response
   * plugins.
   *
   * @return  The set of controls for this intermediate response.
   */
  public List<Control> getControls()
  {
    return controls;
  }

  /**
   * Retrieves a string representation of this intermediate response.
   *
   * @return  A string representation of this intermediate response.
   */
  @Override
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }

  /**
   * Appends a string representation of this intermediate response to
   * the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  private void toString(StringBuilder buffer)
  {
    buffer.append("IntermediateResponse(operation=");
    operation.toString(buffer);
    buffer.append(",oid=").append(oid);
    buffer.append(",value=").append(buffer);

    if (! controls.isEmpty())
    {
      buffer.append(",controls={");

      Iterator<Control> iterator = controls.iterator();
      iterator.next().toString(buffer);

      while (iterator.hasNext())
      {
        buffer.append(",");
        iterator.next().toString(buffer);
      }

      buffer.append("}");
    }

    buffer.append(")");
  }
}
