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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.replication;


import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.util.promise.ResultHandler;
import org.opends.server.TestCaseUtils;
import org.forgerock.opendj.ldap.DN;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

@SuppressWarnings("javadoc")
public class ChangeNumberControlPluginTestCase extends ReplicationTestCase
{

  private static final String HOSTNAME = "127.0.0.1";

  private static final Control CSN_CONTROL = new Control()
  {
    @Override
    public String getOID()
    {
      return OID_CSN_CONTROL;
    }

    @Override
    public ByteString getValue()
    {
      return null;
    }

    @Override
    public boolean hasValue()
    {
      return false;
    }

    @Override
    public boolean isCritical()
    {
      return false;
    }
  };

  private static final ResultHandler<Result> ASSERTION_RESULT_HANDLER = new ResultHandler<Result>()
  {
    @Override
    public void handleResult(final Result result)
    {
      assertTrue(result.containsControl(CSN_CONTROL.getOID()));
    }
  };

  /**
   * The replicationServer that will be used in this test.
   */
  private DN baseDn;

  /**
   * Before starting the tests, start the server and configure a
   * replicationServer.
   */

  @Override
  @BeforeClass(alwaysRun=true)
  public void setUp() throws Exception {
    super.setUp();

    baseDn = DN.valueOf(TEST_ROOT_DN_STRING);
    final int replServerPort = TestCaseUtils.findFreePort();

    // replication server
    String replServerLdif =
        "dn: cn=Replication Server, " + SYNCHRO_PLUGIN_DN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-server\n"
        + "cn: Replication Server\n"
        + "ds-cfg-replication-port: " + replServerPort + "\n"
        + "ds-cfg-replication-db-directory: ChangeNumberControlDbTest\n"
        + "ds-cfg-replication-server-id: 103\n";

    // suffix synchronized
    String testName = "changeNumberControlPluginTestCase";
    String synchroServerLdif =
        "dn: cn=" + testName + ", cn=domains, " + SYNCHRO_PLUGIN_DN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-domain\n"
        + "cn: " + testName + "\n"
        + "ds-cfg-base-dn: " + baseDn + "\n"
        + "ds-cfg-replication-server: localhost:" + replServerPort + "\n"
        + "ds-cfg-server-id: 1\n"
        + "ds-cfg-receive-status: true\n";

    configureReplication(replServerLdif, synchroServerLdif);
  }

  @Test
  public void changeNumberControlAddRequestTest() throws Exception {
    try (final LDAPConnectionFactory factory = new LDAPConnectionFactory(HOSTNAME, getServerLdapPort());
         final Connection connection = factory.getConnection())
    {
      final AddRequest addRequest = Requests.newAddRequest("dn: cn=user1," + baseDn,
                                                           "changetype: add",
                                                           "objectClass: person",
                                                           "cn: user1",
                                                           "sn: User Test 10")
                                               .addControl(CSN_CONTROL);
      connection.addAsync(addRequest)
                .thenOnResult(ASSERTION_RESULT_HANDLER);
    }
  }

  @Test
  public void changeNumberControlDeleteRequestTest() throws Exception
  {
    try (final LDAPConnectionFactory factory = new LDAPConnectionFactory(HOSTNAME, getServerLdapPort());
         final Connection connection = factory.getConnection())
    {
      final DeleteRequest deleteRequest = Requests.newDeleteRequest("cn=user111," + baseDn)
                                                  .addControl(CSN_CONTROL);
      connection.deleteAsync(deleteRequest)
                .thenOnResult(ASSERTION_RESULT_HANDLER);
    }
  }

  @Test
  public void changeNumberControlModifyRequestTest() throws Exception
  {
    try (final LDAPConnectionFactory factory = new LDAPConnectionFactory(HOSTNAME, getServerLdapPort());
         final Connection connection = factory.getConnection())
    {
      final ModifyRequest modifyRequest = Requests.newModifyRequest("dn: cn=user1," + baseDn,
                                                                    "changetype: modify",
                                                                    "add: description",
                                                                    "description: blah")
                                                  .addControl(CSN_CONTROL);
      connection.modifyAsync(modifyRequest).thenOnResult(ASSERTION_RESULT_HANDLER);
    }
  }

  @Test
  public void changeNumberControlModifyDNRequestTest() throws Exception
  {
    try (final LDAPConnectionFactory factory = new LDAPConnectionFactory(HOSTNAME, getServerLdapPort());
         final Connection connection = factory.getConnection())
    {
      final ModifyDNRequest modifyDNRequest = Requests.newModifyDNRequest("cn=user.1" + baseDn, "cn=user.111")
                                                      .addControl(CSN_CONTROL)
                                                      .setDeleteOldRDN(true);
      connection.modifyDNAsync(modifyDNRequest)
                .thenOnResult(ASSERTION_RESULT_HANDLER);
    }
  }
}
