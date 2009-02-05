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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.ldap;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeTest;
import org.opends.server.types.*;
import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Reader;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.util.ArrayList;

public class TestLDAPFilter extends LdapTestCase
{
  @BeforeTest
  public void setup() throws Exception
  {
    TestCaseUtils.startServer();
  }
  @DataProvider(name="badfilterstrings")
  public Object[][] getBadFilterStrings() throws Exception
  {
    return new Object[][]
    {
      { null, null },
      { "", null },
      { "=", null },
      { "()", null },
      { "(&(objectClass=*)(sn=s*s)", null },
      { "(dob>12221)", null },
      { "(cn=bob\\2 doe)", null },
      { "(cn=\\4j\\w2\\yu)", null },
      { "(cn=ds\\2)", null },
      { "(&(givenname=bob)|(sn=pep)dob=12))", null },
      { "(:=bob)", null },
      { "(=sally)", null },
      { "(cn=billy bob", null },
      { "(|(!(title=sweep*)(l=Paris*)))", null },
      { "(|(!))", null },
      { "((uid=user.0))", null },
      { "(&&(uid=user.0))", null },
      { "!uid=user.0", null },
      { "(:dn:=Sally)", null },

    };
  }
  @DataProvider(name="filterstrings")
  public Object[][] getFilterStrings() throws Exception
  {
    LDAPFilter equal = LDAPFilter.createEqualityFilter("objectClass",
                                        ByteString.valueOf("\\test*(Value)"));
    LDAPFilter equal2 = LDAPFilter.createEqualityFilter("objectClass",
                                                      ByteString.valueOf(""));
    LDAPFilter approx = LDAPFilter.createApproximateFilter("sn",
                                        ByteString.valueOf("\\test*(Value)"));
    LDAPFilter greater = LDAPFilter.createGreaterOrEqualFilter("employeeNumber",
                                        ByteString.valueOf("\\test*(Value)"));
    LDAPFilter less = LDAPFilter.createLessOrEqualFilter("dob",
                                        ByteString.valueOf("\\test*(Value)"));
    LDAPFilter presense = LDAPFilter.createPresenceFilter("login");

    ArrayList<ByteString> any = new ArrayList<ByteString>(0);
    ArrayList<ByteString> multiAny = new ArrayList<ByteString>(1);
    multiAny.add(ByteString.valueOf("\\wid*(get)"));
    multiAny.add(ByteString.valueOf("*"));

    LDAPFilter substring1 = LDAPFilter.createSubstringFilter("givenName",
                                                 ByteString.valueOf("\\Jo*()"),
                                                      any,
                                                 ByteString.valueOf("\\n*()"));
    LDAPFilter substring2 = LDAPFilter.createSubstringFilter("givenName",
                                                 ByteString.valueOf("\\Jo*()"),
                                                      multiAny,
                                                 ByteString.valueOf("\\n*()"));
    LDAPFilter substring3 = LDAPFilter.createSubstringFilter("givenName",
                                                      ByteString.valueOf(""),
                                                      any,
                                                 ByteString.valueOf("\\n*()"));
    LDAPFilter substring4 = LDAPFilter.createSubstringFilter("givenName",
                                                 ByteString.valueOf("\\Jo*()"),
                                                      any,
                                                      ByteString.valueOf(""));
    LDAPFilter substring5 = LDAPFilter.createSubstringFilter("givenName",
                                                      ByteString.valueOf(""),
                                                      multiAny,
                                                      ByteString.valueOf(""));
    LDAPFilter extensible1 = LDAPFilter.createExtensibleFilter("2.4.6.8.19",
                                                "cn",
                                           ByteString.valueOf("\\John* (Doe)"),
                                                false);
    LDAPFilter extensible2 = LDAPFilter.createExtensibleFilter("2.4.6.8.19",
                                                "cn",
                                           ByteString.valueOf("\\John* (Doe)"),
                                                true);
    LDAPFilter extensible3 = LDAPFilter.createExtensibleFilter("2.4.6.8.19",
                                                null,
                                           ByteString.valueOf("\\John* (Doe)"),
                                                true);
    LDAPFilter extensible4 = LDAPFilter.createExtensibleFilter(null,
                                                "cn",
                                           ByteString.valueOf("\\John* (Doe)"),
                                                true);
    LDAPFilter extensible5 = LDAPFilter.createExtensibleFilter("2.4.6.8.19",
                                                null,
                                           ByteString.valueOf("\\John* (Doe)"),
                                                false);

    ArrayList<RawFilter> list1 = new ArrayList<RawFilter>();
    list1.add(equal);
    list1.add(approx);

    LDAPFilter and = LDAPFilter.createANDFilter(list1);

    ArrayList<RawFilter> list2 = new ArrayList<RawFilter>();
    list2.add(substring1);
    list2.add(extensible1);
    list2.add(and);

    return new Object[][]
    {
        { "(objectClass=\\5ctest\\2a\\28Value\\29)", equal },

        { "(objectClass=)", equal2 },

        { "(sn~=\\5ctest\\2a\\28Value\\29)", approx },

        { "(employeeNumber>=\\5ctest\\2a\\28Value\\29)", greater },

        { "(dob<=\\5ctest\\2a\\28Value\\29)", less },

        { "(login=*)", presense },

        { "(givenName=\\5cJo\\2a\\28\\29*\\5cn\\2a\\28\\29)", substring1 },

        { "(givenName=\\5cJo\\2a\\28\\29*\\5cwid\\2a\\28get\\29*\\2a*\\5cn\\2a\\28\\29)", substring2 },

        { "(givenName=*\\5cn\\2a\\28\\29)", substring3 },

        { "(givenName=\\5cJo\\2a\\28\\29*)", substring4 },

        { "(givenName=*\\5cwid\\2a\\28get\\29*\\2a*)", substring5 },

        { "(cn:2.4.6.8.19:=\\5cJohn\\2a \\28Doe\\29)", extensible1 },

        { "(cn:dn:2.4.6.8.19:=\\5cJohn\\2a \\28Doe\\29)", extensible2 },

        { "(:dn:2.4.6.8.19:=\\5cJohn\\2a \\28Doe\\29)", extensible3 },

        { "(cn:dn:=\\5cJohn\\2a \\28Doe\\29)", extensible4 },

        { "(:2.4.6.8.19:=\\5cJohn\\2a \\28Doe\\29)", extensible5 },

        { "(&(objectClass=\\5ctest\\2a\\28Value\\29)(sn~=\\5ctest\\2a\\28Value\\29))",
            LDAPFilter.createANDFilter(list1) },

        { "(|(objectClass=\\5ctest\\2a\\28Value\\29)(sn~=\\5ctest\\2a\\28Value\\29))",
            LDAPFilter.createORFilter(list1) },

        { "(!(objectClass=\\5ctest\\2a\\28Value\\29))", LDAPFilter.createNOTFilter(equal) },

        { "(|(givenName=\\5cJo\\2a\\28\\29*\\5cn\\2a\\28\\29)(cn:2.4.6.8.19:=\\5cJohn\\2a \\28Doe\\29)" +
            "(&(objectClass=\\5ctest\\2a\\28Value\\29)(sn~=\\5ctest\\2a\\28Value\\29)))",
            LDAPFilter.createORFilter(list2) }

    };
  }

  @Test(dataProvider = "filterstrings")
  public void testDecode(String filterStr, LDAPFilter filter) throws Exception
  {
    //LDAPFilter decodedFilter = LDAPFilter.decode(filterStr);
    //System.out.println(decodedFilter.);
    //System.out.println(filter.toString());
    LDAPFilter decoded = LDAPFilter.decode(filterStr);
    assertEquals(decoded.toString(), filter.toString());
    assertEquals(decoded.getAssertionValue(), filter.getAssertionValue());
    assertEquals(decoded.getAttributeType(), filter.getAttributeType());
    assertEquals(decoded.getDNAttributes(), filter.getDNAttributes());
    if(decoded.getFilterComponents() != null || filter.getFilterComponents() != null)
    {
      assertEquals(decoded.getFilterComponents().toString(), filter.getFilterComponents().toString());
    }
    assertEquals(decoded.getFilterType(), filter.getFilterType());
    assertEquals(decoded.getMatchingRuleID(), filter.getMatchingRuleID());
    if(decoded.getNOTComponent() != null || filter.getNOTComponent() != null)
    {
      assertEquals(decoded.getNOTComponent().toString(), filter.getNOTComponent().toString());
    }
    if(decoded.getSubAnyElements() != null && decoded.getSubAnyElements().size() > 0 ||
        filter.getSubAnyElements() != null && filter.getSubAnyElements().size() > 0)
    {
      assertEquals(decoded.getSubAnyElements(), filter.getSubAnyElements());
    }
    if(decoded.getSubFinalElement() != null && !decoded.getSubFinalElement().toString().equals("") ||
      filter.getSubFinalElement() != null && !filter.getSubFinalElement().toString().equals(""))
    {
      assertEquals(decoded.getSubFinalElement(), filter.getSubFinalElement());
    }
    if(decoded.getSubInitialElement() != null && !decoded.getSubInitialElement().toString().equals("") ||
        filter.getSubInitialElement() != null && !filter.getSubInitialElement().toString().equals(""))
    {
      assertEquals(decoded.getSubInitialElement(), filter.getSubInitialElement());
    }
  }

  @Test(dataProvider = "badfilterstrings", expectedExceptions = LDAPException.class)
  public void testDecodeException (String filterStr, LDAPFilter filter) throws Exception
  {
    LDAPFilter.decode(filterStr);
  }

  @Test
  public void testToSearchFilter() throws Exception
  {
    LDAPFilter filter = LDAPFilter.decode(
        "(&" +
          "(cn>=*)" +
          "(:2.5.13.2:=Bob)" +
          "(cn:=Jane)" +
          "(|" +
            "(sn<=gh*sh*sl)" +
            "(!(cn:dn:2.5.13.5:=Sally))" +
            "(cn~=blvd)" +
            "(cn=*)" +
          ")" +
          "(cn=*n)" +
          "(cn=n*)" +
          "(cn=n*n)" +
          "(:dn:1.3.6.1.4.1.1466.109.114.1:=Doe)" +
          "(cn:2.5.13.2:=)" +
        ")");

    SearchFilter searchFilter = filter.toSearchFilter();
    LDAPFilter newFilter = new LDAPFilter(searchFilter);
    assertEquals(filter.toString(), newFilter.toString());
  }

  @Test(dataProvider = "filterstrings")
  public void testEncodeDecode(String filterStr, LDAPFilter filter) throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    filter.write(writer);

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    assertEquals(LDAPFilter.decode(reader).toString(), filter.toString());
  }

  @Test
  public void testEncodeDecodeComplex() throws Exception
  {
    LDAPFilter filter = LDAPFilter.decode(
        "(&" +
          "(cn>=*)" +
          "(:1.2.3.4:=Bob)" +
          "(cn:=Jane)" +
          "(|" +
            "(sn<=gh*sh*sl)" +
            "(!(cn:dn:2.4.6.8.19:=Sally))" +
            "(cn~=blvd)" +
            "(cn=*)" +
          ")" +
          "(cn=*n)" +
          "(cn=n*)" +
          "(cn=n*n)" +
          "(:dn:1.2.3.4:=Doe)" +
          "(cn:2.4.6.8.10:=)" +
        ")");

    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    filter.write(writer);

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    assertEquals(LDAPFilter.decode(reader).toString(), filter.toString());
  }
}
