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
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.guitools.controlpanel.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Tests {@link Utilities#unescapeUtf8(String)}, which the control panel applies to DN strings (issue #1153). */
@SuppressWarnings("javadoc")
public class UnescapeUtf8TestCase extends DirectoryServerTestCase
{
  @DataProvider
  public Object[][] dns()
  {
    return new Object[][] {
      { "cn=a\\\\41,dc=x" },
      { "cn=a\\\\\\\\41,dc=x" },
      { "cn=J\\C3\\B6rg\\\\C3\\\\B6,dc=x" },
      { "cn=Before\\0dAfter,dc=x" },
      { "cn=Smith\\, John,dc=x" },
    };
  }

  /** The displayed DN must still be the same DN once the user selects it and the control panel parses it back. */
  @Test(dataProvider = "dns")
  public void unescapedDNParsesBackToTheSameDN(String dnString)
  {
    final DN dn = DN.valueOf(dnString);
    assertThat((Object) DN.valueOf(Utilities.unescapeUtf8(dn.toString()))).isEqualTo(dn);
  }

  @Test
  public void escapedBackslashIsNotTheStartOfAHexPair()
  {
    assertThat(Utilities.unescapeUtf8("cn=a\\\\41,dc=x")).isEqualTo("cn=a\\\\41,dc=x");
  }

  @Test
  public void hexPairsAreDecodedAsUtf8()
  {
    assertThat(Utilities.unescapeUtf8("cn=J\\C3\\B6rg,dc=x")).isEqualTo("cn=Jörg,dc=x");
  }
}
