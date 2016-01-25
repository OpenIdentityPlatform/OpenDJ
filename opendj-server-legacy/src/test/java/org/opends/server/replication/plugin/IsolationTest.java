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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.testng.Assert.*;

import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.TestCaseUtils;
import org.forgerock.opendj.server.config.meta.ReplicationDomainCfgDefn.IsolationPolicy;
import org.opends.server.core.ModifyOperation;
import org.opends.server.replication.ReplicationTestCase;
import org.testng.annotations.Test;

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
  @Test
  public void noUpdateIsolationPolicyTest() throws Exception
  {
    LDAPReplicationDomain domain = null;
    DN baseDn = DN.valueOf(TEST_ROOT_DN_STRING);
    int serverId = 1;

    try
    {
      // configure and start replication of TEST_ROOT_DN_STRING on the server
      // using a replication server that is not started

      int replServerPort = TestCaseUtils.findFreePort();
      SortedSet<String> replServers = new TreeSet<>();
      replServers.add("localhost:" + replServerPort);
      DomainFakeCfg domainConf =
        new DomainFakeCfg(baseDn, serverId, replServers);
      domainConf.setHeartbeatInterval(100000);
      domain = MultimasterReplication.createNewDomain(domainConf);
      domain.start();

      // check that the updates fail with the unwilling to perform error.
      ModifyOperation op = modify(baseDn, "description", "test");
      assertEquals(op.getResultCode(), ResultCode.UNWILLING_TO_PERFORM);

      // now configure the domain to accept changes even though it is not
      // connected to any replication server.
      domainConf.setIsolationPolicy(IsolationPolicy.ACCEPT_ALL_UPDATES);
      domain.applyConfigurationChange(domainConf);

      // try a new modify operation on the base entry.
      op = modify(baseDn, "description", "test");

      // check that the operation was successful.
      assertEquals(op.getResultCode(), ResultCode.SUCCESS,
          op.getAdditionalLogItems().toString());
    }
    finally
    {
      if (domain != null)
      {
        MultimasterReplication.deleteDomain(baseDn);
      }
    }
  }

  private ModifyOperation modify(DN baseDn, String attrName, String attrValue)
  {
    return getRootConnection().processModify(modifyRequest(baseDn, REPLACE, attrName, attrValue));
  }
}
