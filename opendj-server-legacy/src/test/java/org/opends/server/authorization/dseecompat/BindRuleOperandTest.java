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
package org.opends.server.authorization.dseecompat;

import static org.assertj.core.api.Assertions.*;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Verifies that {@link BindRule#decode(String)} rejects a boolean ("and"/"or")
 * bind rule that is missing one of its operands, instead of throwing a
 * {@link NullPointerException}. An empty group such as "()" decodes to a null
 * bind rule; before this fix, using such a group as the left side, or leaving
 * the right side of the boolean empty (e.g. "(()or)"), dereferenced that null
 * while parsing and failed with an NPE rather than a diagnosable
 * {@link AciException} (issue #719).
 */
@SuppressWarnings("javadoc")
public class BindRuleOperandTest extends DirectoryServerTestCase
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

  @DataProvider(name = "bindRulesWithMissingOperand")
  public Object[][] bindRulesWithMissingOperand()
  {
    return new Object[][] {
      // the exact bind rule from issue #719: "or" with no right operand and an
      // empty left group
      { "(()or)" },
      // empty right operand after a valid left operand
      { "(userdn=\"ldap:///self\" or)" },
      { "(userdn=\"ldap:///self\" and)" },
      // empty left operand before a valid right operand
      { "()or userdn=\"ldap:///self\"" },
      // an empty group is not a bind rule on its own
      { "()" },
    };
  }

  @Test(dataProvider = "bindRulesWithMissingOperand")
  public void rejectsMissingOperand(String bindRule)
  {
    assertThatThrownBy(() -> BindRule.decode(bindRule)).isInstanceOf(AciException.class);
  }

  /** Reproduces the full ACI from issue #719 through {@link Aci#decode}. */
  @Test
  public void decodeOfAciWithMissingOperandThrowsAciException()
  {
    String aci = "(version 3.0; acl \"ac\"; allow (search)(()or) (userdn=\"ldap:///self\"); )";
    assertThatThrownBy(() -> Aci.decode(ByteString.valueOfUtf8(aci), DN.rootDN()))
        .isInstanceOf(AciException.class);
  }
}
