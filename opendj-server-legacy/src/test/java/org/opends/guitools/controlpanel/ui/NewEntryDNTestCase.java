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
package org.opends.guitools.controlpanel.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.RDN;
import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Tests the DN that the new entry panels show for the naming value typed by the user (issue #1153). */
@SuppressWarnings("javadoc")
public class NewEntryDNTestCase extends DirectoryServerTestCase
{
  private static final String PARENT = "ou=People,dc=example,dc=com";

  @DataProvider
  public Object[][] namingValues()
  {
    return new Object[][] { { "John Smith" }, { "Smith, John" }, { "a+b" }, { "#1 fan" }, { "Smith;Jr" },
      { "a\\b" }, { "a=b" }, { " lead" } };
  }

  @Test(dataProvider = "namingValues")
  public void newEntryDNUnderAParentNodeKeepsTheWholeValue(String value)
  {
    assertNewEntryDN(AbstractNewEntryPanel.getNewEntryDN("cn", value, DN.valueOf(PARENT)), value);
  }

  @Test(dataProvider = "namingValues")
  public void newEntryDNUnderATypedParentKeepsTheWholeValue(String value)
  {
    assertNewEntryDN(AbstractNewEntryPanel.getNewEntryDN("cn", value, PARENT), value);
  }

  @Test
  public void newEntryDNUnderAParentThatIsStillBeingTypedEscapesTheValue()
  {
    assertThat(AbstractNewEntryPanel.getNewEntryDN("cn", "Smith, John", "ou=People,dc")).isEqualTo(
        "cn=Smith\\, John,ou=People,dc");
  }

  private static void assertNewEntryDN(String dnString, String value)
  {
    final DN dn = DN.valueOf(dnString);
    assertThat((Object) dn.parent()).isEqualTo(DN.valueOf(PARENT));
    final RDN rdn = dn.rdn();
    assertThat(rdn.size()).isEqualTo(1);
    assertThat(rdn.getFirstAVA().getAttributeType().hasName("cn")).isTrue();
    assertThat((Object) rdn.getFirstAVA().getAttributeValue()).isEqualTo(ByteString.valueOfUtf8(value));
  }
}
