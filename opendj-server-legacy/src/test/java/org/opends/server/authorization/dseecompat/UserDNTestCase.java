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
 * Copyright 2026 3A Systems, LLC
 */
package org.opends.server.authorization.dseecompat;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Tests for decoding of the userdn bind rule. */
public class UserDNTestCase extends AciTestCase
{
  /**
   * ACIs whose userdn LDAP URL contains a percent sign followed by fewer than
   * two hexadecimal digits - see issue #673.
   *
   * @return The malformed ACIs.
   */
  @DataProvider
  public Object[][] truncatedPercentAcis()
  {
    return new Object[][] {
      { "(version 3.0; acl \":\"; allow (search) userdn=\"ldap://cn=name%B\"; )" },
      { "(version 3.0; acl \":\"; allow (search) userdn=\"ldap://cn=name%\"; )" },
    };
  }

  /**
   * A truncated percent-encoded sequence in the userdn URL must be rejected
   * with a clean AciException instead of an ArrayIndexOutOfBoundsException -
   * see issue #673.
   *
   * @param aciString
   *          The ACI to decode.
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(dataProvider = "truncatedPercentAcis", expectedExceptions = AciException.class)
  public void testTruncatedPercentInUserDN(String aciString) throws Exception
  {
    Aci.decode(ByteString.valueOfUtf8(aciString), DN.rootDN());
  }

  /**
   * ACIs whose userdn LDAP URL contains a malformed bracketed IPv6 host - see
   * issue #726.
   *
   * @return The malformed ACIs.
   */
  @DataProvider
  public Object[][] malformedIPv6HostAcis()
  {
    return new Object[][] {
      { "(version 3.0; acl \"f\"; allow (search) userdn=\"q://[:1\"; )" },
      { "(version 3.0; acl \"f\"; allow (search) userdn=\"ldap://[::1:389/dc=example,dc=com\"; )" },
    };
  }

  /**
   * A malformed bracketed IPv6 host in the userdn URL must be rejected with a
   * clean AciException instead of a StringIndexOutOfBoundsException - see
   * issue #726.
   *
   * @param aciString
   *          The ACI to decode.
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(dataProvider = "malformedIPv6HostAcis", expectedExceptions = AciException.class)
  public void testMalformedIPv6HostInUserDN(String aciString) throws Exception
  {
    Aci.decode(ByteString.valueOfUtf8(aciString), DN.rootDN());
  }

  /**
   * A well-formed bracketed IPv6 host in the userdn URL must still decode.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test
  public void testValidIPv6HostInUserDN() throws Exception
  {
    String aciString = "(version 3.0; acl \"f\"; allow (search) "
        + "userdn=\"ldap://[::1]:389/uid=user,dc=example,dc=com\"; )";
    Aci.decode(ByteString.valueOfUtf8(aciString), DN.rootDN());
  }
}
