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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import java.io.IOException;

import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.controls.ControlDecoder;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;

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
  /** ControlDecoder implementation to decode this control from a ByteString. */
  private static final class Decoder
      implements ControlDecoder<ReplicationRepairRequestControl>
  {
    @Override
    public ReplicationRepairRequestControl decode(boolean isCritical,
                                                  ByteString value)
           throws DirectoryException
    {
      return new ReplicationRepairRequestControl(isCritical);
    }

    @Override
    public String getOID()
    {
      return OID_REPLICATION_REPAIR_CONTROL;
    }
  }

  /** The Control Decoder that can be used to decode this control. */
  public static final ControlDecoder<ReplicationRepairRequestControl> DECODER =
    new Decoder();

  /** The OID of the Replication repair Control. */
  public static final String
          OID_REPLICATION_REPAIR_CONTROL = "1.3.6.1.4.1.26027.1.5.2";

  /** Creates a new instance of the replication repair request control with the default settings. */
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
   * Writes this control value to an ASN.1 writer. The value (if any) must be
   * written as an ASN1OctetString.
   *
   * @param writer The ASN.1 writer to use.
   * @throws IOException If a problem occurs while writing to the stream.
   */
  @Override
  protected void writeValue(ASN1Writer writer) throws IOException {
    // No value element
  }

  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("ReplicationRepairRequestControl()");
  }
}
