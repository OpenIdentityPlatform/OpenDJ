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
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.extensions;

import java.io.File;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.tools.LDAPSearch;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * A set of test cases for the StartTLS extended operation handler.
 */
public class StartTLSExtendedOperationTestCase
       extends ExtensionsTestCase
{
  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Tests the use of the StartTLS extended operation to communicate with the
   * server in conjunction with no authentication and using a client trust store
   * to validate the server certificate.
   */
  @Test
  public void testStartTLSNoAuthTrustStore()
  {
    String trustStorePath = DirectoryServer.getInstanceRoot() + File.separator +
                            "config" + File.separator + "client.truststore";

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-q",
      "-P", trustStorePath,
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests the use of the StartTLS extended operation to communicate with the
   * server in conjunction with no authentication and using blind trust.
   */
  @Test
  public void testStartTLSNoAuthTrustAll()
  {
    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-q",
      "-X",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests the use of the StartTLS extended operation to communicate with the
   * server in conjunction with simple authentication and using a client trust
   * store to validate the server certificate.
   */
  @Test
  public void testStartTLSSimpleAuthTrustStore()
  {
    String trustStorePath = DirectoryServer.getInstanceRoot() + File.separator +
                            "config" + File.separator + "client.truststore";

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-q",
      "-P", trustStorePath,
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests the use of the StartTLS extended operation to communicate with the
   * server in conjunction with simple authentication and using blind trust.
   */
  @Test
  public void testStartTLSSimpleAuthTrustAll()
  {
    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-q",
      "-X",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests the use of the StartTLS extended operation to communicate with the
   * server in conjunction with SASL EXTERNAL authentication and using a client
   * trust store to validate the server certificate.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testStartTLSExternalAuthTrustStore()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
         "dn: cn=Test User,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "cn: Test User",
         "givenName: Test",
         "sn: User");

    String keyStorePath = DirectoryServer.getInstanceRoot() + File.separator +
                          "config" + File.separator + "client.keystore";
    String trustStorePath = DirectoryServer.getInstanceRoot() + File.separator +
                            "config" + File.separator + "client.truststore";

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-q",
      "-K", keyStorePath,
      "-W", "password",
      "-P", trustStorePath,
      "-r",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests the use of the StartTLS extended operation to communicate with the
   * server in conjunction with SASL EXTERNAL authentication and using blind
   * trust.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testStartTLSExternalAuthTrustAll()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
         "dn: cn=Test User,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "cn: Test User",
         "givenName: Test",
         "sn: User");


    String keyStorePath = DirectoryServer.getInstanceRoot() + File.separator +
                          "config" + File.separator + "client.keystore";

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-q",
      "-K", keyStorePath,
      "-W", "password",
      "-X",
      "-r",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }
}

