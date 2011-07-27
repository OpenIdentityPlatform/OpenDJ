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
 *      Portions Copyright 2011 ForgeRock AS
 */
package org.opends.server.replication.plugin;


import java.net.ServerSocket;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.testng.Assert.*;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.ReplicationDomainCfgDefn.IsolationPolicy;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.types.DN;
import org.opends.server.types.ResultCode;
import org.testng.annotations.Test;
import static org.opends.server.TestCaseUtils.*;

/**
 * Test behavior of an LDAP server that is not able to connect
 * to any of the configured Replication Server.
 */
public class IsolationTest extends ReplicationTestCase
{
  /**
   * Check that the server correctly accept or reject updates when
   * the replication is configured but could not connect to
   * any of the configured replication server.
   *
   * @throws Exception If an unexpected error occurred.
   */
  @Test()
  public void noUpdateIsolationPolicyTest() throws Exception
  {
    LDAPReplicationDomain domain = null;
    DN baseDn = DN.decode(TEST_ROOT_DN_STRING);
    int serverId = 1;

    try
    {
      // configure and start replication of TEST_ROOT_DN_STRING on the server
      // using a replication server that is not started

      // find  a free port for the replicationServer
      ServerSocket socket = TestCaseUtils.bindFreePort();
      int replServerPort = socket.getLocalPort();
      socket.close();
      SortedSet<String> replServers = new TreeSet<String>();
          replServers.add("localhost:" + replServerPort);
      DomainFakeCfg domainConf =
        new DomainFakeCfg(baseDn, serverId, replServers);
      domainConf.setHeartbeatInterval(100000);
      domain = MultimasterReplication.createNewDomain(domainConf);
      domain.start();

      // check that the updates fail with the unwilling to perform error.
      InternalClientConnection conn =
        InternalClientConnection.getRootConnection();
      ModifyOperation op =
        conn.processModify(baseDn, generatemods("description", "test"));

      // check that the update failed.
      assertEquals(ResultCode.UNWILLING_TO_PERFORM, op.getResultCode());

      // now configure the domain to accept changes even though it is not
      // connectetd to any replication server.
      domainConf.setIsolationPolicy(IsolationPolicy.ACCEPT_ALL_UPDATES);
      domain.applyConfigurationChange(domainConf);

      // try a new modify operation on the base entry.
      op = conn.processModify(baseDn, generatemods("description", "test"));

      // check that the operation was successful.
      assertEquals(op.getResultCode(), ResultCode.SUCCESS,
          op.getAdditionalLogItems().toString());
    }
    finally
    {
      if (domain != null)
        MultimasterReplication.deleteDomain(baseDn);
    }
  }
}
