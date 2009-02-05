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


import org.opends.server.types.ByteString;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.controls.ControlDecoder;
import org.opends.server.protocols.asn1.ASN1Writer;

import java.io.IOException;


/**
 * This class implements the Sun-defined replication repair control.
 * This control can be used to modify the content of a replicated database
 * on a single server without impacting the other servers that are replicated
 * with this server.
 * It also allows to modify attributes like entryuuid and ds-sync-hist that
 * are normally not modifiable from an external connection.
 */
public class ReplicationRepairRequestControl extends Control
{
  /**
   * ControlDecoder implentation to decode this control from a ByteString.
   */
  private final static class Decoder
      implements ControlDecoder<ReplicationRepairRequestControl>
  {
    /**
     * {@inheritDoc}
     */
    public ReplicationRepairRequestControl decode(boolean isCritical,
                                                  ByteString value)
           throws DirectoryException
    {
      return new ReplicationRepairRequestControl(isCritical);
    }

    /**
     * {@inheritDoc}
     */
    public String getOID()
    {
      return OID_REPLICATION_REPAIR_CONTROL;
    }

  }

  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<ReplicationRepairRequestControl> DECODER =
    new Decoder();

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
   * @param  isCritical  Indicates whether support for this control should be
   *                     considered a critical part of the client processing.
   */
  public ReplicationRepairRequestControl(boolean isCritical)
  {
    super(OID_REPLICATION_REPAIR_CONTROL, isCritical);

  }

  /**
   * Writes this control's value to an ASN.1 writer. The value (if any) must be
   * written as an ASN1OctetString.
   *
   * @param writer The ASN.1 writer to use.
   * @throws IOException If a problem occurs while writing to the stream.
   */
  @Override
  protected void writeValue(ASN1Writer writer) throws IOException {
    // No value element
  }



  /**
   * Appends a string representation of this replication repair request control
   * to the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("ReplicationRepairRequestControl()");
  }
}

