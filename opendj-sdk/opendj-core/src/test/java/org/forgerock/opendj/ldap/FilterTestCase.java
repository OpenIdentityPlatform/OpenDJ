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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2015 ForgeRock AS
 */

package org.forgerock.opendj.ldap;

import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class FilterTestCase extends SdkTestCase {
    @DataProvider(name = "badfilterstrings")
    public Object[][] getBadFilterStrings() throws Exception {
        return new Object[][] { { null, null }, { "", null }, { "=", null }, { "()", null },
            { "(&(objectClass=*)(sn=s*s)", null }, { "(dob>12221)", null },
            { "(cn=bob\\2 doe)", null }, { "(cn=\\4j\\w2\\yu)", null }, { "(cn=ds\\2)", null },
            { "(&(givenname=bob)|(sn=pep)dob=12))", null }, { "(:=bob)", null },
            { "(=sally)", null }, { "(cn=billy bob", null },
            { "(|(!(title=sweep*)(l=Paris*)))", null }, { "(|(!))", null },
            { "((uid=user.0))", null }, { "(&&(uid=user.0))", null }, { "!uid=user.0", null },
            { "(:dn:=Sally)", null }, };
    }

    @DataProvider(name = "filterstrings")
    public Object[][] getFilterStrings() throws Exception {
        final Filter equal =
                Filter.equality("objectClass", ByteString.valueOfUtf8("\\test*(Value)"));
        final Filter equal2 = Filter.equality("objectClass", ByteString.valueOfUtf8(""));
        final Filter approx =
                Filter.approx("sn", ByteString.valueOfUtf8("\\test*(Value)"));
        final Filter greater =
                Filter.greaterOrEqual("employeeNumber", ByteString
                        .valueOfUtf8("\\test*(Value)"));
        final Filter less =
                Filter.lessOrEqual("dob", ByteString.valueOfUtf8("\\test*(Value)"));
        final Filter presense = Filter.present("login");

        final ArrayList<ByteString> any = new ArrayList<>(0);
        final ArrayList<ByteString> multiAny = new ArrayList<>(1);
        multiAny.add(ByteString.valueOfUtf8("\\wid*(get)"));
        multiAny.add(ByteString.valueOfUtf8("*"));

        final Filter substring1 =
                Filter.substrings("givenName", ByteString.valueOfUtf8("\\Jo*()"), any,
                        ByteString.valueOfUtf8("\\n*()"));
        final Filter substring2 =
                Filter.substrings("givenName", ByteString.valueOfUtf8("\\Jo*()"), multiAny,
                        ByteString.valueOfUtf8("\\n*()"));
        final Filter substring3 =
                Filter.substrings("givenName", ByteString.valueOfUtf8(""), any, ByteString
                        .valueOfUtf8("\\n*()"));
        final Filter substring4 =
                Filter.substrings("givenName", ByteString.valueOfUtf8("\\Jo*()"), any,
                        ByteString.valueOfUtf8(""));
        final Filter substring5 =
                Filter.substrings("givenName", ByteString.valueOfUtf8(""), multiAny,
                        ByteString.valueOfUtf8(""));
        final Filter extensible1 =
                Filter.extensible("2.4.6.8.19", "cn", ByteString
                        .valueOfUtf8("\\John* (Doe)"), false);
        final Filter extensible2 =
                Filter.extensible("2.4.6.8.19", "cn", ByteString
                        .valueOfUtf8("\\John* (Doe)"), true);
        final Filter extensible3 =
                Filter.extensible("2.4.6.8.19", null, ByteString
                        .valueOfUtf8("\\John* (Doe)"), true);
        final Filter extensible4 =
                Filter.extensible(null, "cn", ByteString.valueOfUtf8("\\John* (Doe)"),
                        true);
        final Filter extensible5 =
                Filter.extensible("2.4.6.8.19", null, ByteString
                        .valueOfUtf8("\\John* (Doe)"), false);

        final ArrayList<Filter> list1 = new ArrayList<>();
        list1.add(equal);
        list1.add(approx);

        final Filter and = Filter.and(list1);

        final ArrayList<Filter> list2 = new ArrayList<>();
        list2.add(substring1);
        list2.add(extensible1);
        list2.add(and);

        return new Object[][] {
            { "(objectClass=\\5Ctest\\2A\\28Value\\29)", equal },

            { "(objectClass=)", equal2 },

            { "(sn~=\\5Ctest\\2A\\28Value\\29)", approx },

            { "(employeeNumber>=\\5Ctest\\2A\\28Value\\29)", greater },

            { "(dob<=\\5Ctest\\2A\\28Value\\29)", less },

            { "(login=*)", presense },

            { "(givenName=\\5CJo\\2A\\28\\29*\\5Cn\\2A\\28\\29)", substring1 },

            { "(givenName=\\5CJo\\2A\\28\\29*\\5Cwid\\2A\\28get\\29*\\2A*\\5Cn\\2A\\28\\29)",
                substring2 },

            { "(givenName=*\\5Cn\\2A\\28\\29)", substring3 },

            { "(givenName=\\5CJo\\2A\\28\\29*)", substring4 },

            { "(givenName=*\\5Cwid\\2A\\28get\\29*\\2A*)", substring5 },

            { "(cn:2.4.6.8.19:=\\5CJohn\\2A \\28Doe\\29)", extensible1 },

            { "(cn:dn:2.4.6.8.19:=\\5CJohn\\2A \\28Doe\\29)", extensible2 },

            { "(:dn:2.4.6.8.19:=\\5CJohn\\2A \\28Doe\\29)", extensible3 },

            { "(cn:dn:=\\5CJohn\\2A \\28Doe\\29)", extensible4 },

            { "(:2.4.6.8.19:=\\5CJohn\\2A \\28Doe\\29)", extensible5 },

            { "(&(objectClass=\\5Ctest\\2A\\28Value\\29)(sn~=\\5Ctest\\2A\\28Value\\29))",
                Filter.and(list1) },

            { "(|(objectClass=\\5Ctest\\2A\\28Value\\29)(sn~=\\5Ctest\\2A\\28Value\\29))",
                Filter.or(list1) },

            { "(!(objectClass=\\5Ctest\\2A\\28Value\\29))", Filter.not(equal) },

            {
                "(|(givenName=\\5CJo\\2A\\28\\29*\\5Cn\\2A\\28\\29)(cn:2.4.6.8.19:=\\5CJohn\\2A \\28Doe\\29)"
                        + "(&(objectClass=\\5Ctest\\2A\\28Value\\29)(sn~=\\5Ctest\\2A\\28Value\\29)))",
                Filter.or(list2) }

        };
    }

    /**
     * Decodes the specified filter strings.
     *
     * @param filterStr
     * @param filter
     * @throws Exception
     */
    @Test(dataProvider = "filterstrings")
    public void testDecode(final String filterStr, final Filter filter) throws Exception {
        final Filter decoded = Filter.valueOf(filterStr);
        assertEquals(decoded.toString(), filter.toString());
    }

    /**
     * Decodes the specified filter strings.
     *
     * @param filterStr
     * @param filter
     * @throws Exception
     */
    @Test(dataProvider = "filterstrings")
    public void testToString(final String filterStr, final Filter filter) throws Exception {
        assertEquals(filterStr, filter.toString());
    }

    /**
     * Decodes the erroneous filter strings.
     *
     * @param filterStr
     * @param filter
     * @throws Exception
     */
    @Test(dataProvider = "badfilterstrings", expectedExceptions = {
            LocalizedIllegalArgumentException.class, NullPointerException.class })
    public void testDecodeException(final String filterStr, final Filter filter) throws Exception {
        Filter.valueOf(filterStr);
    }

    @Test
    public void testGreaterThanFalse1() throws Exception {
        final Filter filter = Filter.greaterThan("cn", "bbb");
        final Entry entry = new LinkedHashMapEntry("dn: cn=bbb", "objectclass: top", "cn: bbb");
        final Matcher matcher = filter.matcher();
        assertFalse(matcher.matches(entry).toBoolean());
    }

    @Test
    public void testGreaterThanFalse2() throws Exception {
        final Filter filter = Filter.greaterThan("cn", "bbb");
        final Entry entry = new LinkedHashMapEntry("dn: cn=aaa", "objectclass: top", "cn: aaa");
        final Matcher matcher = filter.matcher();
        assertFalse(matcher.matches(entry).toBoolean());
    }

    @Test
    public void testGreaterThanTrue() throws Exception {
        final Filter filter = Filter.greaterThan("cn", "bbb");
        final Entry entry = new LinkedHashMapEntry("dn: cn=ccc", "objectclass: top", "cn: ccc");
        final Matcher matcher = filter.matcher();
        assertTrue(matcher.matches(entry).toBoolean());
    }

    @Test
    public void testLessThanFalse1() throws Exception {
        final Filter filter = Filter.lessThan("cn", "bbb");
        final Entry entry = new LinkedHashMapEntry("dn: cn=bbb", "objectclass: top", "cn: bbb");
        final Matcher matcher = filter.matcher();
        assertFalse(matcher.matches(entry).toBoolean());
    }

    @Test
    public void testLessThanFalse2() throws Exception {
        final Filter filter = Filter.lessThan("cn", "bbb");
        final Entry entry = new LinkedHashMapEntry("dn: cn=ccc", "objectclass: top", "cn: ccc");
        final Matcher matcher = filter.matcher();
        assertFalse(matcher.matches(entry).toBoolean());
    }

    @Test
    public void testLessThanTrue() throws Exception {
        final Filter filter = Filter.lessThan("cn", "bbb");
        final Entry entry = new LinkedHashMapEntry("dn: cn=aaa", "objectclass: top", "cn: aaa");
        final Matcher matcher = filter.matcher();
        assertTrue(matcher.matches(entry).toBoolean());
    }

    /**
     * Tests the matcher.
     *
     * @throws Exception
     */
    @Test
    public void testMatcher() throws Exception {
        final Filter equal =
                Filter.equality("cn", ByteString.valueOfUtf8("\\test*(Value)"));
        final LinkedHashMapEntry entry =
                new LinkedHashMapEntry(DN.valueOf("cn=\\test*(Value),dc=org"));
        entry.addAttribute("cn", "\\test*(Value)");
        entry.addAttribute("objectclass", "top,person");
        final Matcher matcher = equal.matcher();
        assertTrue(matcher.matches(entry).toBoolean());
    }

    @DataProvider
    public Object[][] getAssertionValues() {
        // Use List for assertion values instead of an array because a List has a
        // String representation which can be displayed by debuggers, etc.

        // @formatter:off
        return new Object[][] {
            {
                "(objectClass=*)", Collections.emptyList(), "(objectClass=*)"
            },
            {
                "(objectClass=*)", asList("dummy"), "(objectClass=*)"
            },
            {
                "(objectClass=*)", asList("dummy", "dummy"), "(objectClass=*)"
            },
            {
                "(cn=%s)", asList("dummy"), "(cn=dummy)"
            },
            {
                "(|(cn=%s)(uid=user.%s))", asList("alice", (Object) 1234), "(|(cn=alice)(uid=user.1234))"
            },
            {
                "(|(cn=%1$s)(sn=%1$s))", asList("alice"), "(|(cn=alice)(sn=alice))"
            },
            // Check escaping.
            {
                "(cn=%s)", asList("*"), "(cn=\\2A)"
            },
            {
                "(|(cn=%1$s)(sn=%1$s))", asList("alice)(objectClass=*"),
                "(|(cn=alice\\29\\28objectClass=\\2A)(sn=alice\\29\\28objectClass=\\2A))"
            },
        };
        // @formatter:on
    }

    @Test(dataProvider = "getAssertionValues")
    public void testValueOfTemplate(String template, List<?> assertionValues, String expected)
            throws Exception {
        Filter filter = Filter.format(template, assertionValues.toArray());
        assertEquals(filter.toString(), expected);
    }

    @DataProvider
    public Object[][] getEscapeAssertionValues() {
        // @formatter:off
        return new Object[][] {
            {
                "dummy", "dummy"
            },
            {
                1234, "1234"
            },
            {
                "*", "\\2A"
            },
            {
                "alice)(objectClass=*", "alice\\29\\28objectClass=\\2A"
            },
        };
        // @formatter:on
    }

    @Test(dataProvider = "getEscapeAssertionValues")
    public void testEscapeAssertionValue(Object unescaped, String expected) throws Exception {
        assertEquals(Filter.escapeAssertionValue(unescaped), expected);
    }
}
