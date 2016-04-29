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
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.types;

import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.TestCaseUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;
import static org.testng.Assert.*;

@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "types" }, sequential = true)
public class LDAPURLTest extends TypesTestCase
{

  private static final String DEFAULT_SEARCH_FILTER = "(objectClass=*)";
  private static final String DEFAULT_END_URL = "??base?" + DEFAULT_SEARCH_FILTER;

  @BeforeClass
  public void setup() throws Exception
  {
    TestCaseUtils.startServer();
  }

  @Test(expectedExceptions = DirectoryException.class)
  public void decodeWithNoSchemeSeparator() throws Exception
  {
    final String sample = "";
    LDAPURL.decode(sample, true);
  }

  @Test(expectedExceptions = DirectoryException.class)
  public void decodeNoSchemeWithSchemeSeparator() throws Exception
  {
    final String sample = "://localhost:389/??sub?objectclass=person";
    LDAPURL.decode(sample, true);
  }

  @Test(expectedExceptions = DirectoryException.class)
  public void decodeNoHost() throws Exception
  {
    final String sample = "ldap://:389";
    LDAPURL.decode(sample, true);
  }

  @Test(expectedExceptions = DirectoryException.class)
  public void decodeNoPort() throws Exception
  {
    final String sample = "ldap://localhost:";
    LDAPURL.decode(sample, true);
  }

  @Test(expectedExceptions = DirectoryException.class)
  public void decodeNotAPortNumber() throws Exception
  {
    final String sample = "ldap://localhost:port";
    LDAPURL.decode(sample, true);
  }

  @Test
  public void decodeSampleLDAP() throws Exception
  {
    final String sample = "ldap://";
    final LDAPURL ldapURL = LDAPURL.decode(sample, true);
    assertThat(ldapURL.getScheme()).isEqualTo("ldap");
    assertThat(ldapURL.getHost()).isNull();
    assertDefaultValues(ldapURL);
    assertThat(ldapURL.toString()).isEqualTo(sample + "/" + DEFAULT_END_URL);
  }

  @Test
  public void decodeSampleLDAPS() throws Exception
  {
    final String sample = "ldaps://";
    final LDAPURL ldapURL = LDAPURL.decode(sample, true);
    assertThat(ldapURL.getScheme()).isEqualTo("ldaps");
    assertThat(ldapURL.getHost()).isNull();
    assertDefaultValues(ldapURL);
    assertThat(ldapURL.toString()).isEqualTo(sample + "/" + DEFAULT_END_URL);
  }

  @Test
  public void decodeSampleLDAPWithUselessSlash() throws Exception
  {
    final String sample = "ldap:///";
    final LDAPURL ldapURL = LDAPURL.decode(sample, true);
    assertThat(ldapURL.getScheme()).isEqualTo("ldap");
    assertThat(ldapURL.getHost()).isNull();
    assertDefaultValues(ldapURL);
    assertThat(ldapURL.toString()).isEqualTo(sample + DEFAULT_END_URL);
  }

  @DataProvider
  public Object[][] numberQuestionsMarks()
  {
    return new Object[][] { { 0 }, { 1 }, { 2 }, { 3 }, { 4 }, };
  }

  @Test(dataProvider = "numberQuestionsMarks")
  public void decodeSampleLDAPWithUselessQuestionMarks(int nbQuestionMarks)
      throws Exception
  {
    StringBuilder sb = new StringBuilder("ldap://localhost/");
    for (int i = 0; i < nbQuestionMarks; i++)
    {
      sb.append("?");
    }
    final String sample = sb.toString();
    final LDAPURL ldapURL = LDAPURL.decode(sample, true);
    assertThat(ldapURL.getScheme()).isEqualTo("ldap");
    assertThat(ldapURL.getHost()).isEqualTo("localhost");
    assertDefaultValues(ldapURL);
    assertThat(ldapURL.toString()).isEqualTo("ldap://localhost:389/" + DEFAULT_END_URL);
  }

  private void assertDefaultValues(final LDAPURL ldapURL) throws Exception
  {
    assertThat(ldapURL.getPort()).isEqualTo(389);
    assertThat(ldapURL.getRawBaseDN()).isNullOrEmpty();
    assertThat(ldapURL.getAttributes()).isEmpty();
    assertThat(ldapURL.getScope()).isEqualTo(SearchScope.BASE_OBJECT);
    assertThat(ldapURL.getRawFilter()).isEqualToIgnoringCase(DEFAULT_SEARCH_FILTER);
    assertThat(ldapURL.getFilter()).isEqualTo(LDAPURL.DEFAULT_SEARCH_FILTER);
    assertThat(ldapURL.getExtensions()).isEmpty();
  }

  @Test
  public void decodeSampleLDAPWithAttributes() throws Exception
  {
    final String sample = "ldap://localhost/?cn,tel,mail";
    final LDAPURL ldapURL = LDAPURL.decode(sample, true);
    assertThat(ldapURL.getScheme()).isEqualTo("ldap");
    assertThat(ldapURL.getHost()).isEqualTo("localhost");
    assertThat(ldapURL.getPort()).isEqualTo(389);
    assertThat(ldapURL.getRawBaseDN()).isNullOrEmpty();
    assertThat(ldapURL.getAttributes()).containsOnly("cn", "tel", "mail");
    assertThat(ldapURL.getScope()).isEqualTo(SearchScope.BASE_OBJECT);
    assertThat(ldapURL.getRawFilter()).isEqualToIgnoringCase("(objectClass=*)");
    assertThat(ldapURL.getExtensions()).isEmpty();
    assertThat(ldapURL.toString()).isEqualTo("ldap://localhost:389/?cn,tel,mail?base?" + DEFAULT_SEARCH_FILTER);
  }

  @Test
  public void decodeSampleLDAPWithExtensions() throws Exception
  {
    final String sample = "ldap://localhost/????ext1,ext2,ext3";
    final LDAPURL ldapURL = LDAPURL.decode(sample, true);
    assertThat(ldapURL.getScheme()).isEqualTo("ldap");
    assertThat(ldapURL.getHost()).isEqualTo("localhost");
    assertThat(ldapURL.getPort()).isEqualTo(389);
    assertThat(ldapURL.getRawBaseDN()).isNullOrEmpty();
    assertThat(ldapURL.getAttributes()).isEmpty();
    assertThat(ldapURL.getScope()).isEqualTo(SearchScope.BASE_OBJECT);
    assertThat(ldapURL.getRawFilter()).isEqualToIgnoringCase(DEFAULT_SEARCH_FILTER);
    assertThat(ldapURL.getFilter()).isEqualTo(LDAPURL.DEFAULT_SEARCH_FILTER);
    assertThat(ldapURL.getExtensions()).containsOnly("ext1", "ext2", "ext3");
    assertThat(ldapURL.toString()).isEqualTo("ldap://localhost:389/" + DEFAULT_END_URL + "?ext1,ext2,ext3");
  }

  @DataProvider
  public Object[][] allSearchScopes()
  {
    return new Object[][] {
      { "", SearchScope.BASE_OBJECT }, // this is the default
      { "base", SearchScope.BASE_OBJECT },
      { "one", SearchScope.SINGLE_LEVEL },
      { "sub", SearchScope.WHOLE_SUBTREE },
      { "subord", SearchScope.SUBORDINATES },
      { "subordinate", SearchScope.SUBORDINATES },
    };
  }

  @Test(dataProvider = "allSearchScopes")
  public void decodeSimpleSample(String actualSearchScope,
      SearchScope expectedSearchScope) throws Exception
  {
    final String sample = "ldap://localhost/??" + actualSearchScope;
    final LDAPURL ldapURL = LDAPURL.decode(sample, true);
    assertThat(ldapURL.getScheme()).isEqualTo("ldap");
    assertThat(ldapURL.getHost()).isEqualTo("localhost");
    assertThat(ldapURL.getPort()).isEqualTo(389);
    assertThat(ldapURL.getRawBaseDN()).isEqualTo("");
    assertThat(ldapURL.getAttributes()).isEmpty();
    assertThat(ldapURL.getScope()).isEqualTo(expectedSearchScope);
    assertThat(ldapURL.getRawFilter()).isEqualToIgnoringCase(DEFAULT_SEARCH_FILTER);
    assertThat(ldapURL.getFilter()).isEqualTo(LDAPURL.DEFAULT_SEARCH_FILTER);
    assertThat(ldapURL.getExtensions()).isEmpty();

    // tweaks for the next assert
    if ("".equals(actualSearchScope))
    {
      actualSearchScope = "base";
    }
    else if ("subord".equals(actualSearchScope))
    {
      actualSearchScope = "subordinate";
    }
    assertThat(ldapURL.toString()).isEqualTo(
        "ldap://localhost:389/??" + actualSearchScope + "?" + DEFAULT_SEARCH_FILTER);
  }

  @Test(expectedExceptions = DirectoryException.class)
  public void decodeInvalidSearchScope() throws Exception
  {
    final String sample = "ldap://localhost/??unheardOf";
    LDAPURL.decode(sample, true);
  }

  @Test
  public void decodeComplexSample() throws Exception
  {
    final String sample =
        "ldap://ldap.netscape.com:1389/ou=Sales,o=Netscape,c=US?cn,tel,mail?sub?(objectclass=person)?ext1,ext2,ext3";
    final LDAPURL ldapURL = LDAPURL.decode(sample, true);
    assertThat(ldapURL.getScheme()).isEqualTo("ldap");
    assertThat(ldapURL.getHost()).isEqualTo("ldap.netscape.com");
    assertThat(ldapURL.getPort()).isEqualTo(1389);
    assertThat(ldapURL.getRawBaseDN()).isEqualTo("ou=Sales,o=Netscape,c=US");
    assertThat(ldapURL.getAttributes()).containsOnly("cn", "tel", "mail");
    assertThat(ldapURL.getScope()).isEqualTo(SearchScope.WHOLE_SUBTREE);
    assertThat(ldapURL.getRawFilter()).isEqualToIgnoringCase("(objectClass=person)");
    assertThat(ldapURL.getExtensions()).containsOnly("ext1", "ext2", "ext3");
    assertThat(ldapURL.toString()).isEqualToIgnoringCase(sample);
  }

  @Test
  public void decodeComplexSampleWithUrlDecode() throws Exception
  {
    final String sample = "ld%2Fap://ldap.netsca%2Fpe.com:1389/ou=Sa%2Fles,o=Netscape,"
        + "c=US?c%2Fn,tel,mail?sub?(objectclass=per%2Fson)?ext%2F1,ext%2F2,ext%2F3";
    final LDAPURL ldapURL = LDAPURL.decode(sample, true);
    assertThat(ldapURL.getScheme()).isEqualTo("ld/ap");
    assertThat(ldapURL.getHost()).isEqualTo("ldap.netsca/pe.com");
    assertThat(ldapURL.getPort()).isEqualTo(1389);
    assertThat(ldapURL.getRawBaseDN()).isEqualTo("ou=Sa/les,o=Netscape,c=US");
    assertThat(ldapURL.getAttributes()).containsOnly("c/n", "tel", "mail");
    assertThat(ldapURL.getScope()).isEqualTo(SearchScope.WHOLE_SUBTREE);
    assertThat(ldapURL.getRawFilter()).isEqualToIgnoringCase("(objectClass=per/son)");
    assertThat(ldapURL.getExtensions()).containsOnly("ext/1", "ext/2", "ext/3");

    // FIXME, why does toString() do not URL encode the "/"?
    assertThat(ldapURL.toString()).isEqualToIgnoringCase(
        sample.replaceAll("%2F", "/"));
  }

  @Test
  public void urlDecodeNull() throws Exception
  {
    assertEquals(LDAPURL.urlDecode(null), "");
  }

  @Test(expectedExceptions = DirectoryException.class)
  public void urlDecodeNeedMoreBytesAfterPercentSign() throws Exception
  {
    assertNull(LDAPURL.urlDecode("%"));
  }

  @Test(expectedExceptions = DirectoryException.class)
  public void urlDecodeNotHexOneAfterPercentSign() throws Exception
  {
    assertNull(LDAPURL.urlDecode("%z1"));
  }

  @Test(expectedExceptions = DirectoryException.class)
  public void urlDecodeNotHexTwoAfterPercentSign() throws Exception
  {
    assertNull(LDAPURL.urlDecode("%1z"));
  }

  @Test
  public void urlDecodeAllPercentSigns() throws Exception
  {
    String decoded1 = LDAPURL.urlDecode("%21%23%24%26%27%28%29%2A%2B%2C%2F%3A%3B%3D%3F%40%5B%5D");
    assertThat(decoded1).isEqualTo("!#$&'()*+,/:;=?@[]");

    String decoded2 = LDAPURL.urlDecode("%20%22%25%2D%2E%3C%3E%5C%5E%5F%60%7B%7C%7D%7E");
    assertThat(decoded2).isEqualTo(" \"%-.<>\\^_`{|}~");

    // unix, then MacOS, then windows style newlines
    String decoded3 = LDAPURL.urlDecode("%0A %0D %0D%0A");
    assertThat(decoded3).isEqualTo("\n \r \r\n");
  }

}
