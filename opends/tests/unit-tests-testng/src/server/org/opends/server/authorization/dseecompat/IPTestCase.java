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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */


package org.opends.server.authorization.dseecompat;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;
import java.net.InetAddress;

/**
 * Test of IP bind rule address decoding and address matching.
 */
public class IPTestCase extends AciTestCase {

    //Various patterns and addresses that can be put in IP bind rule
    //expressions. For example: ip="72.*.78.*,*.*.*.*".

    private String ipExpr="72.56.78.9,127.0.0.1";
    private String ipExprWc="72.*.78.*,*.*.*.*";
    private String netmaskWc=
             "72.56.78.0+255.255.255.240,127.0.0.0+255.255.255.0";
    private String netmaskWcOverRide=
                   "72.*.78.*+255.255.255.248,*.0.0.0+192.0.0.0";
    private String ip6Expr="12AB:0000:0000:CD30:0000:0000:0000:0000";
    private String ip6ExprC="12ab:0:0:cd30::";
    private String ip6ExprCidr="12ab:0:0:cd30::/60";
    private String ip6ExprCidrB="[12ab:0:0:cd30::]/60";
    private String ip4compat="::ffff:127.0.0.1,::ffff:72.56.78.9";
    private String cidr=
             "72.56.78.0/28,127.0.0.0/24";
    private String cidrWc=
                   "72.*.78.*/29,*.0.0.0/7";
    private String
            mixed="::ffff:72.56.78.9,45.*.33.*,[12ab:0:0:cd30::]/60," +
                 "56.56.78.0+255.255.255.0";

    //Providers that test the above expressions.
    //Mix of Ipv6 and Ipv4 addresses.
    @DataProvider(name = "mixedMatches")
    public Object[][] mixedData() {
        return new Object[][] {
                {"12AB:0000:0000:CD30:0000:0000:0000:0000"},
                {"12ab:0:0:cd3f:0000:0000:23DC:DC30"},
                {"45.56.33.9"},
                {"72.56.78.9"},
                {"56.56.78.9"}

        };
    }
    //Ipv6 addresses in long and various compressed forms.
    @DataProvider(name = "v6Matches")
    public Object[][] v6MatchData() {
        return new Object[][] {
                {"12AB:0000:0000:CD30:0000:0000:0000:0000"},
                {"12AB::CD30:0:0:0:0"},
                {"12ab:0:0:cd30::"}
        };
    }

    //Ipv6 addresses used in cidr tests.
    @DataProvider(name = "v6Matches1")
    public Object[][] v6MatchData1() {
        return new Object[][] {
                {"12ab:0:0:cd3f:0000:0000:23DC:DC30"},
                {"12ab::cd3f:0:0:23dc:dc30"}
        };
    }

    //Ipv4 addresses.
    @DataProvider(name = "v4Matches")
    public Object[][] v4MatchData() {
        return new Object[][] {
                {"127.0.0.1"},
                {"72.56.78.9"}
        };
    }

    //Valid IPv4 expressions.
    @DataProvider(name = "validRules")
    public Object[][] validData() {
        return new Object[][] {
            { "129.34.55.67/0"},
            { "129.*.78.55+255.255.248.0"},
            {"128.*.*.*"},
            {"129.45.23.67/22"},
            {"128.33.23.*/32"},
            {"*.*.*.*"},
            {"129.45.67.34/0"},
            {"129.45.67.34+255.255.255.0"}
        };
    }

    //Valid IPv6 expressions.
    @DataProvider(name = "valid6Rules")
    public Object[][] valid6Data() {
        return new Object[][] {
                {"2001:fecd:ba23:cd1f:dcb1:1010:9234:4088/124"},
                {"2001:fecd:ba23:cd1f:dcb1:1010:9234:4088"},
                {"[2001:fecd:ba23:cd1f:dcb1:1010:9234:4088]/45"},
                {"::/128"},
                {"::1/128"},
                {"::"},
                {"0:0:0:0:0:ffff:101.45.75.219"},
                {"1080::8:800:200C:417A"},
                {"0:0:0:0:0:0:101.45.75.219"},
                {"::101.45.75.219"}
        };
    }

    //Invalid Ipv4 expressions.
    @DataProvider(name = "invalidRules")
    public Object[][] inValidData() {
        return new Object[][] {
                {"128.33.23.xx"},
                {"128.33.23.22++"},
                {"128.33.23.22+"},
                {"128.33.23.22+56"},
                {"128.33.23.22+255.255.45"},
                {"128.33.23.22+255.255.45.45"},//netmask is invalid
                {"128.33.23.22/-1"},
                {"128..33.23"},
                {"128.33.23.66.88"},
                {"128.33.600.66"},
                {"128.33.9.66/33"},
                {"."},
                {"foo"}
        };
    }

    //Invalid IPv6 expressions.
    @DataProvider(name = "invalid6Rules")
    public Object[][] inValid5Data() {
        return new Object[][] {
                {"2001:feca:ba23:cd1f:dcb1:1010:9234:4088///124"},
                {"2001:feca:ba23:cd1f:dcb1:1010:9234:4088?124"},
                {"2001:fecz:ba23:cd1f:dcb1:1010:9234:4088/124"},
                {"2001:fecd:ba23:cd1ff:dcb1:1010:9234:4088/46"},
                {"0:0:0:0:0:ffff:101..45.75.219"},
                {"0:0:0:0:0:0:101.45.75.700"},
                {"1080::8:800:200C:417A/500"},
                {"1080::8:800:*:417A/66"},
        };
    }

    /**
     * This test uses the mixed (ipv4 and 6) expression above to match against
     * ipv4 and 6 addresses. All addresses should pass.
     *
     * @param ipStr The string to convert into InetAddress.
     * @throws Exception If the evaluation doesn't return true.
     */
    @Test(dataProvider="mixedMatches")
    public void testMixed(String ipStr) throws Exception {
         IP ip=(IP) IP.decode(mixed, EnumBindRuleType.EQUAL_BINDRULE_TYPE);
         InetAddress addr=InetAddress.getByName(ipStr);
         EnumEvalResult res=ip.evaluate(addr);
         if(res != EnumEvalResult.TRUE)
             throw new RuntimeException ("Addr: " + ipStr +
                     "expr: " + mixed);
     }


    /**
     * Test Ipv6 Ipv4 compat expression. All addresses should pass.
     *
     * @param ipStr The string to convert into IPv4 InetAddress.
     * @throws Exception If the evaluation doesn't return true.
     */
    @Test(dataProvider = "v4Matches")
    public void test4compat(String ipStr) throws Exception {
        IP ip=(IP) IP.decode(ip4compat, EnumBindRuleType.EQUAL_BINDRULE_TYPE);
        InetAddress addr=InetAddress.getByName(ipStr);
        EnumEvalResult res=ip.evaluate(addr);
        if(res != EnumEvalResult.TRUE)
            throw new RuntimeException ("Addr: " + ipStr +
                    "expr: " + ip4compat);
    }

    /**
     * Test various IPv6 expressions. First IPv6 expression with CIDR prefix,
     * then RFC 2732 format (brackets around address) expression.
     *
     * @param ipStr The string to convert into IPv6 InetAddress.
     * @throws Exception If the evaluation doesn't return true.
     */
    @Test(dataProvider = "v6Matches1")
    public void test6Cidr(String ipStr) throws Exception {
        IP ip=(IP) IP.decode(ip6ExprCidr, EnumBindRuleType.EQUAL_BINDRULE_TYPE);
        InetAddress addr=InetAddress.getByName(ipStr);
        EnumEvalResult res=ip.evaluate(addr);
        if(res != EnumEvalResult.TRUE)
            throw new RuntimeException ("Addr: " + ipStr +
                                        "expr: " + ip6ExprCidr);
        IP ip1=(IP) IP.decode(ip6ExprCidrB, EnumBindRuleType.EQUAL_BINDRULE_TYPE);
        EnumEvalResult res1=ip1.evaluate(addr);
        if(res1 != EnumEvalResult.TRUE)
            throw new RuntimeException ("Addr: " + ipStr +
                                        "expr: " + ip6ExprCidrB);
    }

    /**
     * Test IPv6 address expressions. First using the long form, then the
     * compressed form. The addresses to match have long and compressed forms
     * also. All tests should pass.
     *
     * @param ipStr The string to convert into IPv6 InetAddress.
     * @throws Exception If the evaluation doesn't return true.
     */
    @Test(dataProvider = "v6Matches")
    public void test6Simple(String ipStr) throws Exception {
        IP ip=(IP) IP.decode(ip6Expr, EnumBindRuleType.EQUAL_BINDRULE_TYPE);
        InetAddress addr=InetAddress.getByName(ipStr);
        EnumEvalResult res=ip.evaluate(addr);
        if(res != EnumEvalResult.TRUE)
            throw new RuntimeException ("Addr: " + ipStr +
                                        "expr: " + ip6Expr);
        IP ip1=(IP) IP.decode(ip6ExprC, EnumBindRuleType.EQUAL_BINDRULE_TYPE);
        EnumEvalResult res1=ip1.evaluate(addr);
        if(res1 != EnumEvalResult.TRUE)
            throw new RuntimeException ("Addr: " + ipStr +
                                        "expr: " + ip6ExprC);
    }

    /**
     * Test IPv4 cidr prefix expressions and cidr prefix with wild-card
     * expressions. All tests should pass.
     *
     * @param ipStr The string to convert into IPv4 InetAddress.
     * @throws Exception If the evaluation doesn't return true.
     */
    @Test(dataProvider = "v4Matches")
    public void test4NCidr(String ipStr) throws Exception {
        IP ip=(IP) IP.decode(cidr, EnumBindRuleType.EQUAL_BINDRULE_TYPE);
        InetAddress addr=InetAddress.getByName(ipStr);
        EnumEvalResult res=ip.evaluate(addr);
        if(res != EnumEvalResult.TRUE)
            throw new RuntimeException ("Addr: " + ipStr +
                                        "expr: " + cidr);

        IP ip1=(IP) IP.decode(cidrWc, EnumBindRuleType.EQUAL_BINDRULE_TYPE);
        EnumEvalResult res1=ip.evaluate(addr);
        if(res1 != EnumEvalResult.TRUE)
            throw new RuntimeException ("Addr: " + ipStr +
                                        "expr: " + cidrWc);
    }

    /**
     * Test IPv4 netmask expressions and netmask with wild-card expressions.
     * All tests should pass.
     *
     * @param ipStr The string to convert into IPv4 InetAddress.
     * @throws Exception If the evaluation doesn't return true.
     */
    @Test(dataProvider = "v4Matches")
    public void test4Netmasks(String ipStr) throws Exception {
        IP ip=(IP) IP.decode(netmaskWc, EnumBindRuleType.EQUAL_BINDRULE_TYPE);
        InetAddress addr=InetAddress.getByName(ipStr);
        EnumEvalResult res=ip.evaluate(addr);
        if(res != EnumEvalResult.TRUE)
            throw new RuntimeException ("Addr: " + ipStr +
                                        "expr: " + netmaskWc);
        IP ip1 = (IP) IP.decode(netmaskWcOverRide,
                EnumBindRuleType.EQUAL_BINDRULE_TYPE);
        EnumEvalResult res1=ip1.evaluate(addr);
        if(res1 != EnumEvalResult.TRUE)
            throw new RuntimeException ("Addr: " + ipStr +
                                        "expr: " + netmaskWc);
    }

    /**
     * Test IPv4 expressions and expression with wild-cards.
     * All Tests should pass.
     *
     * @param ipStr The string to convert into IPv4 InetAddress.
     * @throws Exception  If the evaluation doesn't return true.
     */
    @Test(dataProvider = "v4Matches")
    public void test4SimpleWildCard(String ipStr) throws Exception {
        IP ip=(IP) IP.decode(ipExpr, EnumBindRuleType.EQUAL_BINDRULE_TYPE);
        InetAddress addr=InetAddress.getByName(ipStr);
        EnumEvalResult res=ip.evaluate(addr);
        if(res != EnumEvalResult.TRUE)
            throw new RuntimeException ("Addr: " + ipStr +
                                        "expr: " + ipExpr);
        IP ipWc=(IP) IP.decode(ipExprWc, EnumBindRuleType.EQUAL_BINDRULE_TYPE);
        EnumEvalResult resWc=ipWc.evaluate(addr);
        if(resWc != EnumEvalResult.TRUE)
            throw new RuntimeException ("Addr: " + ipStr +
                                        "expr:" + ipExprWc);
    }

    /**
     * Test  decoding of various valid rules.
     *
     * @param mask The expression to decode.
     * @throws Exception If the valid rule failed decoding.
     */
    @Test(dataProvider = "validRules")
    public void testValidIPDecode(String mask)
            throws Exception {
         IP.decode(mask, EnumBindRuleType.EQUAL_BINDRULE_TYPE);
    }

    /**
     * Test decoding of invalid rules.
     *
     * @param mask The expression to decode.
     * @throws Exception If the valid rule failed decoding.
     */
    @Test(expectedExceptions= AciException.class, dataProvider="invalidRules")
    public void testInvalidDecode(String mask)
            throws Exception {
        try {
            IP.decode(mask, EnumBindRuleType.EQUAL_BINDRULE_TYPE);
        } catch (AciException ae) {
            throw ae;
        } catch (Exception e) {
            System.out.println(
                    "Invalid mask  <" + mask + "> threw wrong exception type.");
            throw e;
        }
        throw new RuntimeException(
                "Invalid mask <" + mask + "> did not throw an exception.");
    }

    /**
     * Test decoding of valid IPv6 rules.
     *
     * @param mask  The expression to decode.
     * @throws Exception If the valid rule failed decoding.
     */
    @Test(dataProvider = "valid6Rules")
    public void testValidIP6Decode(String mask)
            throws Exception {
        IP.decode(mask, EnumBindRuleType.EQUAL_BINDRULE_TYPE);
    }


    /**
     * Test deocding of invalid IPV6 rules.
     *
     * @param mask The expression to decode.
     * @throws Exception If the valid rule failed decoding.
     */
    @Test(expectedExceptions= AciException.class, dataProvider="invalid6Rules")
    public void testInvalid6Decode(String mask)
            throws Exception {
        try {
            IP.decode(mask, EnumBindRuleType.EQUAL_BINDRULE_TYPE);
        } catch (AciException ae) {
            throw ae;
        } catch (Exception e) {
            System.out.println(
                    "Invalid mask  <" + mask + "> threw wrong exception type.");
            throw e;
        }
        throw new RuntimeException(
                "Invalid mask <" + mask + "> did not throw an exception.");
    }
}
