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
 * Portions Copyright 2014-2016 ForgeRock AS.
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

/** A set of test cases for the verify-index tool (see issue #1897). */
@SuppressWarnings("javadoc")
public class VerifyIndexTestCase
       extends ToolsTestCase
{

  private String configFilePath ;

  /** Ensures that the Directory Server is running and performs other necessary setup. */
  @BeforeClass
  public void setup() throws Exception
  {
    TestCaseUtils.startServer();
    configFilePath = DirectoryServer.getConfigFile();

    // Add the airius.com suffix to userRoot
    String userRootDN  = "ds-cfg-backend-id=userRoot,cn=Backends,cn=config";
    ModifyRequest modifyRequest = Requests.newModifyRequest(userRootDN)
        .addModification(ADD, "ds-cfg-base-dn", "o=airius.com");
    ModifyOperation modifyOperation = getRootConnection().processModify(modifyRequest);
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
    String userRootDN  = "ds-cfg-backend-id=userRoot,cn=Backends,cn=config";
    ModifyRequest modifyRequest = Requests.newModifyRequest(userRootDN)
        .addModification(DELETE, "ds-cfg-base-dn", "o=airius.com");
    ModifyOperation modifyOperation = getRootConnection().processModify(modifyRequest);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }

  /**
   * Tests the verify-index -b o=airius.com -i description
   */
  @Test
  public void testNoIndexOnDescription()
  {
    String[] args = {
        "-f",configFilePath,
        "-b", "o=airius.com",
        "-i", "description" };
    assertEquals(VerifyIndex.mainVerifyIndex(args, false, null), 1);
  }
}
