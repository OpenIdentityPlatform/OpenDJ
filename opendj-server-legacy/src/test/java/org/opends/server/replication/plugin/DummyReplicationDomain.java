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
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.TreeSet;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.common.StatusMachineEvent;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.service.ReplicationDomain;
import org.opends.server.types.DirectoryException;

@SuppressWarnings("javadoc")
public class DummyReplicationDomain extends ReplicationDomain
{
  private static DN baseDN = DN.valueOf("cn=DummyReplicationDomain");

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
