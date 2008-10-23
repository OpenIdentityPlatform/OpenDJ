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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.net.ServerSocket;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.ReplicationTestCase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.opends.server.tools.LDAPModify;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.opends.server.TestCaseUtils.*;

public class ChangeNumberControlPluginTestCase
    extends ReplicationTestCase {

  /**
   * The port of the replicationServer.
   */
  private int replServerPort;
  
  /**
   * The replicationServer that will be used in this test.
   */
  private static final int WINDOW_SIZE = 10;
  private static final int REPLICATION_QUEUE_SIZE = 100;
  private DN baseDn;
  
  /**
   * Before starting the tests, start the server and configure a
   * replicationServer.
   */
  
  @BeforeClass(alwaysRun=true)
  public void setUp() throws Exception {
    super.setUp();

    baseDn = DN.decode(TEST_ROOT_DN_STRING);

    //  find  a free port for the replicationServer
    ServerSocket socket = TestCaseUtils.bindFreePort();
    replServerPort = socket.getLocalPort();
    socket.close();

    // replication server
    String replServerLdif =
        "dn: cn=Replication Server, " + SYNCHRO_PLUGIN_DN + "\n"
        + "objectClass: top\n" 
        + "objectClass: ds-cfg-replication-server\n"
        + "cn: Replication Server\n" 
        + "ds-cfg-replication-port: " + replServerPort + "\n"
        + "ds-cfg-replication-db-directory: ChangeNumberControlDbTest\n"
        + "ds-cfg-replication-server-id: 103\n";
    replServerEntry = TestCaseUtils.entryFromLdifString(replServerLdif);

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
    synchroServerEntry = TestCaseUtils.entryFromLdifString(synchroServerLdif);

    configureReplication();
  }

  @DataProvider(name = "operations")
  public Object[][] createLdapRequests() {
    return new Object[][] {
      new Object[] {
        "dn: cn=user1," + baseDn + "\n"
         + "changetype: add" + "\n"
         + "objectClass: person" + "\n"
         + "cn: user1" + "\n"
         + "sn: User Test 10"},
      new Object[] {
         "dn: cn=user1," + baseDn + "\n"
         + "changetype: modify" + "\n"
         + "add: description" + "\n"
         + "description: blah"},
      new Object[] {
         "dn: cn=user1," + baseDn + "\n"
         + "changetype: moddn" + "\n"
         + "newrdn: cn=user111" + "\n"
         + "deleteoldrdn: 1"},
      new Object[] {
         "dn: cn=user111," + baseDn + "\n"
         + "changetype: delete"}
    };
  }
  
  @Test(dataProvider="operations")
  public void ChangeNumberControlTest(String request) throws Exception {

    String path = TestCaseUtils.createTempFile(request);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-J", OID_CSN_CONTROL + ":false",
      "--noPropertiesFile",
      "-f", path
    };

    String resultPath = TestCaseUtils.createTempFile();
    
    FileOutputStream fos = new FileOutputStream(resultPath);

    assertEquals(LDAPModify.mainModify(args, false, fos, System.err), 0);
    //fos.flush();
    fos.close();
    
    assertTrue(isCsnLinePresent(resultPath));
  }

  private boolean isCsnLinePresent(String file) throws Exception {
    FileReader fr = new FileReader(file);
    BufferedReader br = new BufferedReader(fr);
    String line = null;
    boolean found = false;
    while ((line = br.readLine()) != null) {
      if (line.contains("operation change number is")) {
        found = true;
      }
    }
    return (found);
  }
  
  /**
   * Utility function. Can be used to create and add and entry
   * in the local DS from its ldif description.
   *
   * @param entryString  The entry in ldif from.
   * @return             The ResultCode of the operation.
   * @throws Exception   If something went wrong.
   */
  private ResultCode addEntry(String entryString) throws Exception
  {
    Entry entry;
    AddOperationBasis addOp;
    entry = TestCaseUtils.entryFromLdifString(entryString);
    addOp = new AddOperationBasis(InternalClientConnection.getRootConnection(),
       InternalClientConnection.nextOperationID(), InternalClientConnection
       .nextMessageID(), null, entry.getDN(), entry.getObjectClasses(),
       entry.getUserAttributes(), entry.getOperationalAttributes());
    addOp.setInternalOperation(true);
    addOp.run();

    return addOp.getResultCode();
  }
}
