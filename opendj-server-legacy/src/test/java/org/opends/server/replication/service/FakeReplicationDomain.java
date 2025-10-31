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
 * Portions Copyright 2025 3A Systems LLC.
 */
package org.opends.server.replication.service;

import static org.forgerock.i18n.LocalizableMessage.*;
import static org.forgerock.opendj.ldap.ResultCode.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.SortedSet;
import java.util.concurrent.BlockingQueue;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.replication.plugin.DomainFakeCfg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.types.DirectoryException;

/**
 * This class is the minimum implementation of a Concrete ReplicationDomain
 * used to test the Generic Replication Service.
 */
@SuppressWarnings("javadoc")
public class FakeReplicationDomain extends ReplicationDomain
{
  /** A blocking queue that is used to send the UpdateMsg received from the Replication Service. */
  private BlockingQueue<UpdateMsg> queue;
  /** A string that will be exported should exportBackend be called. */
  private String exportString;
  /** A StringBuffer that will be used to build a new String should the import be called. */
  private StringBuffer importString;
  private int exportedEntryCount;

  private FakeReplicationDomain(DN baseDN, int serverID,
      SortedSet<String> replicationServers, int window, long heartbeatInterval,
      long generationId) throws ConfigException
  {
    super(newConfig(baseDN, serverID, replicationServers, window, heartbeatInterval), generationId);
    startPublishService();
    startListenService();
  }

  private static DomainFakeCfg newConfig(DN baseDN, int serverID,
      SortedSet<String> replicationServers, int window, long heartbeatInterval)
  {
    DomainFakeCfg fakeCfg = new DomainFakeCfg(baseDN, serverID, replicationServers);
    fakeCfg.setHeartbeatInterval(heartbeatInterval);
    fakeCfg.setChangetimeHeartbeatInterval(500);
    fakeCfg.setWindowSize(window);
    return fakeCfg;
  }

  public FakeReplicationDomain(DN baseDN, int serverID,
      SortedSet<String> replicationServers, long heartbeatInterval,
      long generationId) throws ConfigException
  {
    this(baseDN, serverID, replicationServers, 100, heartbeatInterval, generationId);
  }

  FakeReplicationDomain(DN baseDN, int serverID,
      SortedSet<String> replicationServers, int window, long heartbeatInterval,
      BlockingQueue<UpdateMsg> queue) throws ConfigException
  {
    this(baseDN, serverID, replicationServers, window, heartbeatInterval, 1);
    this.queue = queue;
  }

  FakeReplicationDomain(DN baseDN, int serverID,
      SortedSet<String> replicationServers, long heartbeatInterval,
      String exportString, StringBuffer importString, int exportedEntryCount)
      throws ConfigException
  {
    this(baseDN, serverID, replicationServers, 100, heartbeatInterval, 1);
    this.exportString = exportString;
    this.importString = importString;
    this.exportedEntryCount = exportedEntryCount;
  }

  public void initExport(String exportString, int exportedEntryCount)
  {
    this.exportString = exportString;
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
      throw new DirectoryException(OPERATIONS_ERROR, raw("IOException during exportBackend"), e);
    }
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
      }
      catch (IOException e)
      {
        throw new DirectoryException(OPERATIONS_ERROR, raw("IOException during importBackend"), e);
      }
      if (ret>0)
      {
        importString.append(new String(buffer, 0, ret));
      }
    }
    while (ret >= 0);
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
