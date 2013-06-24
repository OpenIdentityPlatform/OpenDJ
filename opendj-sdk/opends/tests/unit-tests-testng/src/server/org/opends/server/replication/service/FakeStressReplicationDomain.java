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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013 ForgeRock AS
 */
package org.opends.server.replication.service;

import static org.opends.messages.ReplicationMessages.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opends.server.config.ConfigException;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ResultCode;

/**
 * This class is the minimum implementation of a Concrete ReplicationDomain
 * used to test the Generic Replication Service.
 */
@SuppressWarnings("javadoc")
public class FakeStressReplicationDomain extends ReplicationDomain
{
  /**
   * A blocking queue that is used to send the UpdateMsg received from the
   * Replication Service.
   */
  private BlockingQueue<UpdateMsg> queue = null;

  public FakeStressReplicationDomain(
      String serviceID,
      int serverID,
      Collection<String> replicationServers,
      int window,
      long heartbeatInterval,
      BlockingQueue<UpdateMsg> queue) throws ConfigException
  {
    super(serviceID, serverID, 100);
    startPublishService(replicationServers, window, heartbeatInterval, 500);
    startListenService();
    this.queue = queue;
  }

  private static final int IMPORT_SIZE = 100000000;

  @Override
  public long countEntries() throws DirectoryException
  {
    return IMPORT_SIZE;
  }

  @Override
  protected void exportBackend(OutputStream output) throws DirectoryException
  {
    System.out.println("export started");
    try
    {
      for (int i=0; i< IMPORT_SIZE; i++)
      {
        output.write("this is a long key like a dn or something similar: value\n\n".getBytes());
      }
      output.flush();
      output.close();
    }
    catch (IOException e)
    {
      throw new DirectoryException(ResultCode.OPERATIONS_ERROR,
          ERR_BACKEND_EXPORT_ENTRY.get("", ""));
    }
    System.out.println("export finished");
  }

  @Override
  public long getGenerationID()
  {
    return 1;
  }

  @Override
  protected void importBackend(InputStream input) throws DirectoryException
  {
    long startDate = System.currentTimeMillis();
    System.out.println("import started : " + startDate);

    byte[] buffer = new byte[10000];
    int ret;
    int count = 0;
    do
    {
      try
      {
        ret = input.read(buffer, 0, 10000);
      } catch (IOException e)
      {
        e.printStackTrace();
        throw new DirectoryException(
            ResultCode.OPERATIONS_ERROR,
            ERR_BACKEND_EXPORT_ENTRY.get("", ""));
      }
      count++;
    }
    while (ret >= 0);

    long endDate = System.currentTimeMillis();
    System.out.println("import end : " + endDate);
    System.out.println("import duration (sec): " + (endDate - startDate) / 1000);
    System.out.println("import count: " + count);
  }

  @Override
  public boolean processUpdate(UpdateMsg updateMsg, AtomicBoolean shutdown)
  {
    if (queue != null)
      queue.add(updateMsg);
    return true;
  }
}
