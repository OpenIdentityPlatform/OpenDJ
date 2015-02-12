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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2014 ForgeRock AS
 */
package org.opends.server.replication;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;

import org.opends.server.TestCaseUtils;
import org.opends.server.tools.LDAPModify;
import org.opends.server.types.DN;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

@SuppressWarnings("javadoc")
public class ChangeNumberControlPluginTestCase extends ReplicationTestCase
{

  /**
   * The port of the replicationServer.
   */
  private int replServerPort;

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

    replServerPort = TestCaseUtils.findFreePort();

    // replication server
    String replServerLdif =
        "dn: cn=Replication Server, " + SYNCHRO_PLUGIN_DN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-server\n"
        + "cn: Replication Server\n"
        + "ds-cfg-replication-port: " + replServerPort + "\n"
        + "ds-cfg-replication-db-directory: ChangeNumberControlDbTest\n"
        + "ds-cfg-replication-db-implementation: " + replicationDbImplementation + "\n"
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
    try
    {
      BufferedReader br = new BufferedReader(fr);
      String line = null;
      boolean found = false;
      while ((line = br.readLine()) != null)
      {
        if (line.contains(INFO_CHANGE_NUMBER_CONTROL_RESULT.get("%s", "%s")
            .toString().split("%s")[1]))
        {
          found = true;
        }
      }
      return found;
    }
    finally
    {
      StaticUtils.close(fr);
    }
  }

}
