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
 * Copyright 2013 ForgeRock AS.
 */
package org.opends.server.tools;

import static org.testng.Assert.*;

import java.net.InetSocketAddress;

import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.testng.annotations.Test;

/**
 * A set of test cases for the LDAPSearch tool.
 */
public class LDAPSearchNoServerTestCase
       extends ToolsTestCase
{

  /**
   * Tests a simple search with the server down, we should get error code 91.
   */
  @Test
  public void testSearchWithServerDown()
  {
    InetSocketAddress freeAddress =
        (InetSocketAddress) TestCaseUtils.findFreeSocketAddress();
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(freeAddress.getPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "--noPropertiesFile",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err),
        LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR);
  }

}
