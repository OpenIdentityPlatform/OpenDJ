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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import static org.testng.Assert.*;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.ReplicationDomainCfgDefn.IsolationPolicy;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.types.DN;
import org.opends.server.types.ResultCode;
import org.testng.annotations.Test;

/**
 * Test behavior of an LDAP server that is not able to connect
 * to any of the configured Replication Server.
 */
public class IsolationTest extends ReplicationTestCase
{
  private static final String BASEDN_STRING = "dc=example,dc=com";

  /**
   * Check that the server correctly accept or reject updates when
   * the replication is configured but could not connect to
   * any of the configured replication server.
   */
  @SuppressWarnings("unchecked")
  @Test()
  public void noUpdateIsolationPolicyTest() throws Exception
  {
    ReplicationDomain domain = null;
    DN baseDn = DN.decode(BASEDN_STRING);
    SynchronizationProvider replicationPlugin = null;
    short serverId = 1;

    cleanDB();

    try
    {
      // configure and start replication of dc=example,dc=com on the server
      // using a replication server that is not started
      replicationPlugin = new MultimasterReplication();
      DirectoryServer.registerSynchronizationProvider(replicationPlugin);

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

      // check that the udates fail with the unwilling to perform error.
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

      // check that the operation was successfull.
      assertEquals(op.getResultCode(), ResultCode.SUCCESS, 
          op.getAdditionalLogMessage().toString());
    }
    finally
    {
      if (domain != null)
        MultimasterReplication.deleteDomain(baseDn);

      if (replicationPlugin != null)
      {
        replicationPlugin.finalizeSynchronizationProvider();
        DirectoryServer.deregisterSynchronizationProvider(replicationPlugin);
      }
    }
  }

  /**
   * Clean the database and replace with a single entry.
   *
   * @throws FileNotFoundException
   * @throws IOException
   * @throws Exception
   */
  private void cleanDB() throws FileNotFoundException, IOException, Exception
  {
    String baseentryldif =
      "dn:" + BASEDN_STRING + "\n"
       + "objectClass: top\n"
       + "objectClass: domain\n"
       + "dc: example\n"
       + "entryuuid: " + stringUID(1) + "\n";


      // Initialization :
      // Load the database with a single entry :
      String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
      String path = buildRoot + File.separator + "build" +
                    File.separator + "unit-tests" + File.separator +
                    "package"+ File.separator + "addModDelDependencyTest";
      OutputStream out = new FileOutputStream(new File(path));
      out.write(baseentryldif.getBytes());

      task("dn: ds-task-id=" + UUID.randomUUID()
          + ",cn=Scheduled Tasks,cn=Tasks\n"
          + "objectclass: top\n"
          + "objectclass: ds-task\n"
          + "objectclass: ds-task-import\n"
          + "ds-task-class-name: org.opends.server.tasks.ImportTask\n"
          + "ds-task-import-backend-id: userRoot\n"
          + "ds-task-import-ldif-file: " + path + "\n"
          + "ds-task-import-reject-file: " + path + "reject\n");
  }


  /**
   * Builds and return a uuid from an integer.
   * This methods assume that unique integers are used and does not make any
   * unicity checks. It is only responsible for generating a uid with a
   * correct syntax.
   */
  private String stringUID(int i)
  {
    return String.format("11111111-1111-1111-1111-%012x", i);
  }

}
