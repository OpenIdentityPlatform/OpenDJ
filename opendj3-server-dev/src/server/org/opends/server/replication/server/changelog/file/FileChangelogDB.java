/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2014 ForgeRock AS.
 */
package org.opends.server.replication.server.changelog.file;

import org.opends.server.admin.std.server.ReplicationServerCfg;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexDB;
import org.opends.server.replication.server.changelog.api.ChangelogDB;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy;
import org.opends.server.replication.server.changelog.api.ReplicationDomainDB;
import org.opends.server.replication.server.changelog.je.MultiDomainDBCursor;
import org.opends.server.types.DN;

/**
 * File-based implementation of the ChangelogDB interface.
 */
public class FileChangelogDB implements ChangelogDB, ReplicationDomainDB
{

  /**
   * Creates the changelog DB.
   *
   * @param replicationServer
   *            replication server
   * @param cfg
   *            configuration
   */
  public FileChangelogDB(ReplicationServer replicationServer,
      ReplicationServerCfg cfg)
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public ServerState getDomainOldestCSNs(DN baseDN)
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public ServerState getDomainNewestCSNs(DN baseDN)
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public long getDomainLatestTrimDate(DN baseDN)
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public void removeDomain(DN baseDN) throws ChangelogException
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public MultiDomainDBCursor getCursorFrom(MultiDomainServerState startState,
      PositionStrategy positionStrategy) throws ChangelogException
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public DBCursor<UpdateMsg> getCursorFrom(DN baseDN, ServerState startState,
      PositionStrategy positionStrategy) throws ChangelogException
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public DBCursor<UpdateMsg> getCursorFrom(DN baseDN, int serverId,
      CSN startCSN, PositionStrategy positionStrategy)
      throws ChangelogException
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public void unregisterCursor(DBCursor<?> cursor)
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public boolean publishUpdateMsg(DN baseDN, UpdateMsg updateMsg)
      throws ChangelogException
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public void replicaHeartbeat(DN baseDN, CSN heartbeatCSN)
      throws ChangelogException
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public void replicaOffline(DN baseDN, CSN offlineCSN)
      throws ChangelogException
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public void initializeDB()
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public void setPurgeDelay(long delayInMillis)
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public void setComputeChangeNumber(boolean computeChangeNumber)
      throws ChangelogException
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public void shutdownDB() throws ChangelogException
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public void removeDB() throws ChangelogException
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public ChangeNumberIndexDB getChangeNumberIndexDB()
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public ReplicationDomainDB getReplicationDomainDB()
  {
    throw new RuntimeException("Not implemented");
  }

  /**
   * Clear the database.
   */
  public void clearDB()
  {
    throw new RuntimeException("Not implemented");
  }

}
