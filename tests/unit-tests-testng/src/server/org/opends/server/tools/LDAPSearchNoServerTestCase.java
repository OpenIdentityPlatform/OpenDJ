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
 *      Copyright 2013 ForgeRock AS
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
