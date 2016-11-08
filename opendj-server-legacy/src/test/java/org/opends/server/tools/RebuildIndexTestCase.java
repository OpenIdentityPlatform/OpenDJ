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
package org.opends.server.tools;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.testng.Assert.*;

import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** A set of test cases for the rebuild-index tool (see issue #1897). */
@SuppressWarnings("javadoc")
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
    // Backend should be disabled.
    ModifyRequest modifyRequest = Requests.newModifyRequest(userRootDN)
        .addModification(ADD, "ds-cfg-base-dn", baseDN)
        .addModification(REPLACE, "ds-cfg-enabled", "false");
    final ModifyOperation modifyOperation = getRootConnection().processModify(modifyRequest);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }


  @AfterClass
  public void cleanup() throws Exception
  {
    ModifyRequest modifyRequest = Requests.newModifyRequest(userRootDN)
        .addModification(DELETE, "ds-cfg-base-dn", baseDN)
        .addModification(REPLACE, "ds-cfg-enabled", "true");
    final ModifyOperation modifyOperation = getRootConnection().processModify(modifyRequest);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }

  /** Tries to rebuild an index but the index doesn't exist in the base DN. */
  @Test
  public void testRebuildIndexOnNonExistentShouldFail()
  {
    final String[] args = {
        "-f",configFilePath,
        "-b", baseDN,
        "-i", "description",
        "--offline"
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
        "-i", "ds-sync-hist",
        "--offline"
    };
    assertEquals(RebuildIndex.mainRebuildIndex(args, false, null, null), 0);
  }

  @Test
  public void testRebuildDegradedIndexShouldSucceedWithEmptyList()
  {
    final String[] args = {
            "-f", configFilePath,
            "-b", baseDN,
            "--rebuildDegraded",
            "--offline"
    };
    assertEquals(RebuildIndex.mainRebuildIndex(args, false, null, null), 0);
  }
}

