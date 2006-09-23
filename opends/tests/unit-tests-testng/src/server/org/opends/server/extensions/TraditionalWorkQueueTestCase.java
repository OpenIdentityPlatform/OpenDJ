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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.WorkQueue;
import org.opends.server.config.ConfigEntry;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.Attribute;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.DN;
import org.opends.server.tools.LDAPSearch;
import org.opends.server.types.ResultCode;

import static org.testng.Assert.*;



/**
 * A set of test cases for the traditional work queue.
 */
public class TraditionalWorkQueueTestCase
       extends ExtensionsTestCase
{
  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Tests to ensure that the work queue is configured and enabled within the
   * Directory Server.
   */
  @Test()
  public void testWorkQueueEnabled()
  {
    WorkQueue workQueue = DirectoryServer.getWorkQueue();
    assertNotNull(workQueue);
    assertTrue(workQueue instanceof TraditionalWorkQueue);
  }



  /**
   * Verifies that the number of worker threads can be altered on the fly.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public void testChangingNumWorkerThreads()
         throws Exception
  {
    DN     dn   = DN.decode("cn=Work Queue,cn=config");
    String attr = "ds-cfg-num-worker-threads";
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              new Attribute(attr, "30")));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperation modifyOperation = conn.processModify(dn, mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

    mods.clear();
    mods.add(new Modification(ModificationType.REPLACE,
                              new Attribute(attr, "24")));
    modifyOperation = conn.processModify(dn, mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    // Perform seven external searches so that we can make sure that the
    // unneeded worker threads can die off.
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-b", "",
      "-s", "base",
      "(objectClass=*)",
      "1.1"
    };

    for (int i=0; i < 7; i++)
    {
      assertEquals(LDAPSearch.mainSearch(args, false, null, null), 0);
    }
  }
}

