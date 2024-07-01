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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.replication.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.SortedSet;
import java.util.concurrent.BlockingQueue;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.replication.plugin.DomainFakeCfg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.DirectoryException;
import org.forgerock.opendj.ldap.ResultCode;

/**
 * This class is the minimum implementation of a Concrete ReplicationDomain
 * used to test the Generic Replication Service.
 */
@SuppressWarnings("javadoc")
class FakeStressReplicationDomain extends ReplicationDomain
{
  /**
   * A blocking queue that is used to send the UpdateMsg received from the
   * Replication Service.
   */
  private final BlockingQueue<UpdateMsg> queue;

  FakeStressReplicationDomain(DN baseDN, int serverID,
      SortedSet<String> replicationServers, long heartbeatInterval,
      BlockingQueue<UpdateMsg> queue) throws ConfigException
  {
    super(newConfig(baseDN, serverID, replicationServers, heartbeatInterval), 1);
    startPublishService();
    startListenService();
    this.queue = queue;
  }

  private static DomainFakeCfg newConfig(DN baseDN, int serverID,
      SortedSet<String> replicationServers, long heartbeatInterval)
  {
    final DomainFakeCfg fakeCfg =
        new DomainFakeCfg(baseDN, serverID, replicationServers);
    fakeCfg.setHeartbeatInterval(heartbeatInterval);
    fakeCfg.setChangetimeHeartbeatInterval(500);
    return fakeCfg;
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
      throw new DirectoryException(ResultCode.OPERATIONS_ERROR, LocalizableMessage.raw("exportBackend"));
    }
    System.out.println("export finished");
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
      }
      catch (IOException e)
      {
        e.printStackTrace();
        throw new DirectoryException(ResultCode.OPERATIONS_ERROR, LocalizableMessage.raw("importBackend"));
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
  public boolean processUpdate(UpdateMsg updateMsg)
  {
    if (queue != null)
    {
      queue.add(updateMsg);
    }
    return true;
  }
}
