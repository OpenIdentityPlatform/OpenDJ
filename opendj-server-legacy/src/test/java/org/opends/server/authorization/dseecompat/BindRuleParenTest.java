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
 * Portions Copyright 2026 3A Systems, LLC
 */
package org.opends.server.authorization.dseecompat;

import static org.assertj.core.api.Assertions.*;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Verifies that {@link BindRule#decode(String)} treats parentheses embedded in a
 * quoted bind rule expression (e.g. a DN value containing '(' or ')') as literal
 * data instead of grouping parentheses. Before the quote-aware scan, a grouped
 * bind rule whose value contained a parenthesis was wrongly rejected because the
 * embedded parenthesis was counted when locating the matching close parenthesis.
 */
@SuppressWarnings("javadoc")
public class BindRuleParenTest extends DirectoryServerTestCase
{
  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startFakeServer();
  }

  @AfterClass
  public void tearDown() throws DirectoryException
  {
    TestCaseUtils.shutdownFakeServer();
  }

  @DataProvider(name = "acisWithParenInValue")
  public Object[][] acisWithParenInValue()
  {
    return new Object[][] {
      // simple (ungrouped) bind rule with a ')' / '(' in the DN value
      { "(version 3.0; acl \"t\"; allow (search) userdn=\"ldap:///cn=a)b,dc=x\";)" },
      { "(version 3.0; acl \"t\"; allow (search) userdn=\"ldap:///cn=a(b,dc=x\";)" },
      // grouped bind rule with a ')' embedded in a value
      { "(version 3.0; acl \"t\"; allow (search) "
          + "(userdn=\"ldap:///cn=a)b,dc=x\" or userdn=\"ldap:///self\");)" },
      // grouped bind rule with a '(' embedded in a value
      { "(version 3.0; acl \"t\"; allow (search) "
          + "(userdn=\"ldap:///cn=a(b,dc=x\" or userdn=\"ldap:///self\");)" },
      // grouped bind rule with matched parentheses embedded in a value
      { "(version 3.0; acl \"t\"; allow (search) "
          + "(userdn=\"ldap:///cn=a(b),dc=x\" or userdn=\"ldap:///self\");)" },
    };
  }

  @Test(dataProvider = "acisWithParenInValue")
  public void decodesParenInsideQuotedValue(String aci) throws Exception
  {
    AciBody body = AciBody.decode(aci);
    assertThat(body.getPermBindRulePairs()).hasSize(1);
    assertThat(body.getPermBindRulePairs().get(0).getBindRule()).isNotNull();
  }
}
