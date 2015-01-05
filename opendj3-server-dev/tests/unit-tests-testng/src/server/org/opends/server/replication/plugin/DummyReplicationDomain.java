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
 *      Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.TreeSet;

import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.common.StatusMachineEvent;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.service.ReplicationDomain;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;

@SuppressWarnings("javadoc")
public class DummyReplicationDomain extends ReplicationDomain
{

  private static DN baseDN;
  static
  {
    try
    {
      baseDN = DN.valueOf("cn=DummyReplicationDomain");
    }
    catch (DirectoryException e)
    {
      throw new RuntimeException(e);
    }
  }

  public DummyReplicationDomain(long generationId)
  {
    super(new DomainFakeCfg(null, -1, new TreeSet<String>()), generationId);
  }

  @Override
  public DN getBaseDN()
  {
    return baseDN;
  }

  @Override
  protected void signalNewStatus(StatusMachineEvent event)
  {
  }

  @Override
  public void sessionInitiated(ServerStatus initStatus, ServerState rsState)
  {
  }

  @Override
  public boolean setEclIncludes(int serverId, Set<String> includeAttributes,
      Set<String> includeAttributesForDeletes)
  {
    return false;
  }

  @Override
  protected void exportBackend(OutputStream output) throws DirectoryException
  {
  }

  @Override
  protected void importBackend(InputStream input) throws DirectoryException
  {
  }

  @Override
  public long countEntries() throws DirectoryException
  {
    return 0;
  }

  @Override
  public boolean processUpdate(UpdateMsg updateMsg)
  {
    return false;
  }

}
