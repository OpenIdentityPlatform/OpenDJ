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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */
package org.opends.server.replication.service;

import org.opends.server.types.ResultCode;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import org.opends.server.config.ConfigException;
import java.util.Collection;
import java.io.InputStream;
import java.io.OutputStream;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.types.DirectoryException;
import static org.opends.messages.ReplicationMessages.*;

/**
 * This class is the minimum implementation of a Concrete ReplicationDomain
 * used to test the Generic Replication Service.
 */
public class FakeReplicationDomain extends ReplicationDomain
{
  // A blocking queue that is used to send the UpdateMsg received from
  // the Replication Service.
  BlockingQueue<UpdateMsg> queue = null;

  // A string that will be exported should exportBackend be called.
  String exportString = null;

  // A StringBuilder that will be used to build a build a new String should the
  // import be called.
  StringBuilder importString = null;

  private int exportedEntryCount;

  private long generationID = 1;

  public FakeReplicationDomain(
      String serviceID,
      short serverID,
      Collection<String> replicationServers,
      int window,
      long heartbeatInterval,
      BlockingQueue<UpdateMsg> queue) throws ConfigException
  {
    super(serviceID, serverID);
    startPublishService(replicationServers, window, heartbeatInterval);
    startListenService();
    this.queue = queue;
  }

  public FakeReplicationDomain(
      String serviceID,
      short serverID,
      Collection<String> replicationServers,
      int window,
      long heartbeatInterval,
      String exportString,
      StringBuilder importString,
      int exportedEntryCount) throws ConfigException
  {
    super(serviceID, serverID);
    startPublishService(replicationServers, window, heartbeatInterval);
    startListenService();
    this.exportString = exportString;
    this.importString = importString;
    this.exportedEntryCount = exportedEntryCount;
  }

  @Override
  public long countEntries() throws DirectoryException
  {
    return exportedEntryCount;
  }

  @Override
  protected void exportBackend(OutputStream output) throws DirectoryException
  {
    try
    {
      output.write(exportString.getBytes());
      output.flush();
      output.close();
    }
    catch (IOException e)
    {
      throw new DirectoryException(ResultCode.OPERATIONS_ERROR,
          ERR_BACKEND_EXPORT_ENTRY.get("", ""));
    }

  }

  @Override
  public long getGenerationID()
  {
    return generationID;
  }

  @Override
  protected void importBackend(InputStream input) throws DirectoryException
  {
    byte[] buffer = new byte[1000];

    int ret;
    do
    {
      try
      {
        ret = input.read(buffer, 0, 1000);
      } catch (IOException e)
      {
        throw new DirectoryException(
            ResultCode.OPERATIONS_ERROR,
            ERR_BACKEND_EXPORT_ENTRY.get("", ""));
      }
      importString.append(new String(buffer, 0, ret));
    }
    while (ret >= 0);
  }

  @Override
  public boolean processUpdate(UpdateMsg updateMsg)
  {
    if (queue != null)
      queue.add(updateMsg);
    return true;
  }

  public void setGenerationID(long newGenerationID)
  {
    generationID = newGenerationID;
  }
}
