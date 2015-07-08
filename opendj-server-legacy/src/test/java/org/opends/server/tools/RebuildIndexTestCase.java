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
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.tools;


import java.util.ArrayList;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.Attributes;
import org.opends.server.types.DN;
import org.opends.server.types.Modification;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;

import static org.testng.Assert.*;




/**
 * A set of test cases for the rebuild-index tool (see issue #1897).
 */
public class RebuildIndexTestCase
       extends ToolsTestCase
{

  private String configFilePath;

  private String userRootDN = "ds-cfg-backend-id=userRoot,cn=Backends,cn=config";
  private String baseDN = "o=airius.com";

  /**
   * Ensures that the Directory Server is running and performs other necessary
   * setup.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void setup()
         throws Exception
  {
    TestCaseUtils.startServer();
    configFilePath = DirectoryServer.getConfigFile();

    // Add the airius.com suffix to userRoot
    final InternalClientConnection rootConnection =
      InternalClientConnection.getRootConnection();
    final ArrayList<Modification> mods = new ArrayList<>();
    mods.add(new Modification(ModificationType.ADD,
        Attributes.create("ds-cfg-base-dn", baseDN)));
    // Backend should be disabled.
    mods.add(new Modification(ModificationType.REPLACE,
        Attributes.create("ds-cfg-enabled", "false")));
    final ModifyOperation modifyOperation =
         rootConnection.processModify(DN.valueOf(userRootDN), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }


  /**
   * Performs necessary cleanup.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @AfterClass
  public void cleanup()
         throws Exception
  {
    // remove the airius.com suffix to userRoot
    final InternalClientConnection rootConnection =
      InternalClientConnection.getRootConnection();
    final ArrayList<Modification> mods = new ArrayList<>();
    mods.add(new Modification(ModificationType.DELETE,
        Attributes.create("ds-cfg-base-dn", baseDN)));
    mods.add(new Modification(ModificationType.REPLACE,
        Attributes.create("ds-cfg-enabled", "true")));
    final ModifyOperation modifyOperation =
         rootConnection.processModify(DN.valueOf(userRootDN), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }

  /**
   * Tries to rebuild an index but the index doesn't exist in the base DN.
   */
  @Test
  public void testRebuildIndexOnNonExistentShouldFail()
  {
    final String[] args = {
        "-f",configFilePath,
        "-b", baseDN,
        "-i", "description"
    };
    assertEquals(RebuildIndex.mainRebuildIndex(args, false, null, null), 1);
  }

  /**
   * Tries to rebuild a valid index.
   */
  @Test
  public void testRebuildIndexShouldSuccess()
  {
    final String[] args = {
        "-f", configFilePath,
        "-b", baseDN,
        "-i", "ds-sync-hist"
    };
    assertEquals(RebuildIndex.mainRebuildIndex(args, false, null, null), 0);
  }
}