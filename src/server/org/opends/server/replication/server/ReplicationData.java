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
 *      Portions Copyright 2010-2011 ForgeRock AS.
 */
package org.opends.server.replication.server;

import java.io.UnsupportedEncodingException;

import com.sleepycat.je.DatabaseEntry;

import org.opends.server.replication.protocol.ProtocolVersion;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.UpdateMsg;

/**
 * SuperClass of DatabaseEntry used for data stored in the ReplicationServer
 * Databases.
 */
public class ReplicationData extends DatabaseEntry
{
  private static final long serialVersionUID = 1L;

  /**
   * Creates a new ReplicationData object from an UpdateMsg.
   *
   * @param change the UpdateMsg used to create the ReplicationData.
   */
  public ReplicationData(UpdateMsg change)
  {
    // Always keep messages in the replication DB with the current protocol
    // version
    try
    {
      this.setData(change.getBytes());
    }
    catch (UnsupportedEncodingException e)
    {
      // This should not happen - UTF-8 is always available.
      throw new RuntimeException(e);
    }
  }

  /**
   * Generate an UpdateMsg from its byte[] form.
   *
   * @param data The DatabaseEntry used to generate the UpdateMsg.
   *
   * @return     The generated change.
   *
   * @throws Exception When the data was not a valid Update Message.
   */
  public static UpdateMsg generateChange(byte[] data)
                                             throws Exception
  {
    return (UpdateMsg) ReplicationMsg.generateMsg(
        data, ProtocolVersion.getCurrentVersion());
  }
}
