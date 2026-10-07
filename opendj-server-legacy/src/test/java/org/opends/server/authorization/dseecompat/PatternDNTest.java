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

import static com.forgerock.opendj.ldap.CoreMessages.ERR_ATTR_SYNTAX_DN_TRAILING_ESCAPE;
import static org.assertj.core.api.Assertions.*;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Verifies that {@link PatternDN}, which parses the ACI target and userdn patterns and the DN criteria of
 * the access log filtering, reads a pattern the same way {@link DN#valueOf(String)} reads a DN (issue #1153).
 */
@SuppressWarnings("javadoc")
public class PatternDNTest extends DirectoryServerTestCase
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

  /** Patterns without a wildcard: each one must match the DN that DN.valueOf() reads from the same string. */
  @DataProvider
  public Object[][] patternsWithoutWildcard()
  {
    return new Object[][] {
      { "cn=a\\2Cb,dc=x" },
      { "cn=\"a\\,b\",dc=x" },
      // A hex pair inside quotes is decoded as it is outside quotes
      { "cn=\"a\\2Cb\",dc=x" },
      { "cn=\"J\\C3\\B6rg\",dc=x" },
      // Hex pairs that are still pending at the closing quote, or before an escaped character
      { "cn=\"ab\\2C\",dc=x" },
      { "cn=\"\\C3\\B6\\,\",dc=x" },
      // An empty value is followed by the next AVA or RDN, it does not swallow it
      { "cn=+sn=x,dc=y" },
      { "cn=x+sn=,dc=y" },
      { "cn=,dc=x" },
      { "cn= ,dc=x" },
      { "cn=;dc=x" },
      // A hex string may be followed directly by '+'
      { "cn=#04024869+sn=x,dc=y" },
      { "sn=x+cn=#04024869,dc=y" },
      // The RFC 2253 separator
      { "cn=a;dc=x" },
      // The AVAs of a multi-valued RDN match whatever their order
      { "sn=x+cn=y,dc=z" },
      { "cn=y+sn=x,dc=z" },
    };
  }

  @Test(dataProvider = "patternsWithoutWildcard")
  public void patternMatchesTheDNParsedFromTheSameString(String pattern) throws Exception
  {
    final DN dn = DN.valueOf(pattern);
    assertThat(PatternDN.decode(pattern).matchesDN(dn)).as("pattern %s against DN %s", pattern, dn).isTrue();
  }

  @DataProvider
  public Object[][] patternsAndOtherDNs()
  {
    return new Object[][] {
      { "cn=\"a\\2Cb\",dc=x", "cn=a2Cb,dc=x" },
      { "cn=+sn=x,dc=y", "cn=\\+sn\\=x,dc=y" },
      { "cn=,dc=x", "dc=x" },
      { "cn=,dc=x", "cn=\\,dc\\=x" },
      { "cn=#04024869+sn=x,dc=y", "cn=#04024869,dc=y" },
      { "sn=x+cn=y,dc=z", "sn=y+cn=x,dc=z" },
      { "sn=x+cn=y,dc=z", "sn=x+givenName=y,dc=z" },
    };
  }

  @Test(dataProvider = "patternsAndOtherDNs")
  public void patternDoesNotMatchADifferentDN(String pattern, String otherDN) throws Exception
  {
    assertThat(PatternDN.decode(pattern).matchesDN(DN.valueOf(otherDN))).isFalse();
  }

  /** Patterns that end in a lone backslash, which DN.valueOf() rejects too. */
  @DataProvider
  public Object[][] patternsWithATrailingBackslash()
  {
    return new Object[][] {
      { "cn=a\\" },
      { "cn=\\" },
      { "cn=a,dc=x\\" },
      { "cn=a*\\" },
    };
  }

  /** The pattern is rejected with the result code and the message of DN.valueOf(). */
  @Test(dataProvider = "patternsWithATrailingBackslash")
  public void patternWithATrailingBackslashIsRejected(String pattern) throws Exception
  {
    final Throwable patternError = catchThrowable(() -> PatternDN.decode(pattern));
    assertThat(patternError).isInstanceOf(DirectoryException.class);
    assertThat(((DirectoryException) patternError).getResultCode()).isEqualTo(ResultCode.INVALID_DN_SYNTAX);
    assertThat(((DirectoryException) patternError).getMessageObject().toString())
        .isEqualTo(ERR_ATTR_SYNTAX_DN_TRAILING_ESCAPE.get(pattern).toString());
    if (!pattern.contains("*"))
    {
      final Throwable dnError = catchThrowable(() -> DN.valueOf(pattern));
      assertThat(dnError).isInstanceOf(LocalizedIllegalArgumentException.class);
      assertThat(dnError.getMessage()).isEqualTo(patternError.getMessage());
    }
  }

  @DataProvider
  public Object[][] wildcardPatterns()
  {
    return new Object[][] {
      { "cn=#04024869+sn=*,dc=y", "cn=#04024869+sn=x,dc=y" },
      { "cn=a\\2Cb*,dc=x", "cn=a\\,bcd,dc=x" },
      { "cn=*,dc=x", "cn=a,dc=x" },
    };
  }

  @Test(dataProvider = "wildcardPatterns")
  public void wildcardPatternMatches(String pattern, String dn) throws Exception
  {
    assertThat(PatternDN.decode(pattern).matchesDN(DN.valueOf(dn))).isTrue();
  }
}
