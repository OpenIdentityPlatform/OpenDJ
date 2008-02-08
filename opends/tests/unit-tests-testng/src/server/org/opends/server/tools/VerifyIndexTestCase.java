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
package org.opends.server.tools;


import java.util.ArrayList;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.Attribute;
import org.opends.server.types.DN;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ResultCode;

import static org.testng.Assert.*;




/**
 * A set of test cases for the verify-index tool (see issue #1897)
 */
public class VerifyIndexTestCase
       extends ToolsTestCase
{
  
  private String configFilePath ;

  /**
   * Ensures that the Directory Server is running and performs other necessary
   * setup.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void setup()
         throws Exception
  {
    TestCaseUtils.startServer();
    configFilePath = DirectoryServer.getConfigFile();
    
    // Add the airius.com suffix to userRoot
    InternalClientConnection rootConnection =
      InternalClientConnection.getRootConnection();
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.ADD,
                      new Attribute("ds-cfg-base-dn", "o=airius.com")));
    String userRootDN  = "ds-cfg-backend-id=userRoot,cn=Backends,cn=config";
    ModifyOperation modifyOperation =
         rootConnection.processModify(DN.decode(userRootDN), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }

  
  /**
   * Performs necessary cleanup.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @AfterClass()
  public void cleanup()
         throws Exception
  {
    // remove the airius.com suffix to userRoot
    InternalClientConnection rootConnection =
      InternalClientConnection.getRootConnection();
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.DELETE,
                      new Attribute("ds-cfg-base-dn", "o=airius.com")));
    String userRootDN  = "ds-cfg-backend-id=userRoot,cn=Backends,cn=config";
    ModifyOperation modifyOperation =
         rootConnection.processModify(DN.decode(userRootDN), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }

  /**
   * Tests the verify-index -b o=airius.com -i description
   */
  @Test()
  public void testNoIndexOnDescription()
  {
    String[] args = {
        "-f",configFilePath,
        "-b", "o=airius.com",
        "-i", "description" };
    assertEquals(VerifyIndex.mainVerifyIndex(args, false, null, null), 1);
  }
}

