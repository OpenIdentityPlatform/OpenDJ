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

import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.tools.LDAPModify;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class ReplicationRepairControlTest extends ReplicationTestCase
{
  @Test()
  public void testRepairControl()
         throws Exception
  {
    TestCaseUtils.startServer();

    TestCaseUtils.initializeTestBackend(true);
    
    // Test that we can't add an entry with the entryuuid attribute
    // without specifying the replication repair control. 
    String path = TestCaseUtils.createTempFile(
        "dn: uid=test.repair,o=test\n" +
        "changetype: add\n" +
        "objectClass: top\n" +
        "objectClass: person" +
        "objectClass: organizationalPerson" +
        "objectClass: inetOrgPerson" +
        "uid: test.repair" +
        "givenName: Test" +
        "sn: User" +
        "cn: Test User" +
        "userPassword: password" +
        "entryuuid: d5b910d8-47cb-4ac0-9e5f-0f4a77de58d4");
    
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a",
      "-f", path
    };
    
    assertEquals(LDAPModify.mainModify(args, false, null, null), 65);
    
    // Test that we can't add an entry with the ds-sync-hist attribute
    // without specifying the replication repair control. 
    String path1 = TestCaseUtils.createTempFile(
        "dn: uid=test.repair,o=test\n" +
        "changetype: add\n" +
        "objectClass: top\n" +
        "objectClass: person" +
        "objectClass: organizationalPerson" +
        "objectClass: inetOrgPerson" +
        "uid: test.repair" +
        "givenName: Test" +
        "sn: User" +
        "cn: Test User" +
        "userPassword: password" +
        "ds-sync-hist: description:00000108b3a6cbb800000001:del:deleted_value");
    
    String[] args1 =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a",
      "-f", path1
    };


    assertEquals(LDAPModify.mainModify(args1, false, null, null), 65);
    
    // Now Test specifying the replication repair control makes
    // possible to add an entry with the entryuuid and ds-sync-hist attributes
    // (notice the -J repairControlOid in the ldapmodify arguments)
    String path2 = TestCaseUtils.createTempFile(
        "dn: uid=test.repair,o=test\n" +
        "changetype: add\n" +
        "objectClass: top\n" +
        "objectClass: person" +
        "objectClass: organizationalPerson" +
        "objectClass: inetOrgPerson" +
        "uid: test.repair" +
        "givenName: Test" +
        "sn: User" +
        "cn: Test User" +
        "userPassword: password" +
        "ds-sync-hist: description:00000108b3a6cbb800000001:del:deleted_value" +
        "entryuuid: d5b910d8-47cb-4ac0-9e5f-0f4a77de58d4");
    
    String[] args2 =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-J", "1.3.6.1.4.1.26027.1.5.2", 
      "-a",
      "-f", path2
    };

    assertEquals(LDAPModify.mainModify(args2, false, null, null), 0);
  }  
}
