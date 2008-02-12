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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;


import org.opends.server.types.Control;
import org.opends.server.types.LDAPException;



/**
 * This class implements the Sun-defined replication repair control.
 * This control can be used to modify the content of a replicated database
 * on a single server without impacting the other servers that are replicated
 * with this server.
 * It also allows to modify attributes like entryuuid and ds-sync-hist that
 * are normally not modifiable from an external connection.
 */
public class ReplicationRepairRequestControl
       extends Control
{
  /**
   * The OID of the Replication repair Control.
   */
  public static final String
          OID_REPLICATION_REPAIR_CONTROL = "1.3.6.1.4.1.26027.1.5.2";


  /**
   * Creates a new instance of the replication repair request control with the
   * default settings.
   */
  public ReplicationRepairRequestControl()
  {
    super(OID_REPLICATION_REPAIR_CONTROL, false);

  }



  /**
   * Creates a new instance of the replication repair control with the
   * provided information.
   *
   * @param  oid         The OID to use for this control.
   * @param  isCritical  Indicates whether support for this control should be
   *                     considered a critical part of the client processing.
   */
  public ReplicationRepairRequestControl(String oid, boolean isCritical)
  {
    super(oid, isCritical);

  }



  /**
   * Creates a new replication repair request control from the contents of the
   * provided control.
   *
   * @param  control  The generic control containing the information to use to
   *                  create this replication repair request control.
   *
   * @return  The replication repair request control decoded from the provided
   *          control.
   *
   * @throws  LDAPException  If this control cannot be decoded as a valid
   *                         replication repair request control.
   */
  public static ReplicationRepairRequestControl decodeControl(Control control)
         throws LDAPException
  {
    return new ReplicationRepairRequestControl(control.getOID(),
                                           control.isCritical());
  }



  /**
   * Retrieves a string representation of this replication repair request
   * control.
   *
   * @return  A string representation of this replication repair request
   *          control.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this replication repair request control
   * to the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("ReplicationRepairRequestControl()");
  }
}

