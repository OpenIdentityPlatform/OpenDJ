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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;

import org.opends.server.config.ConfigException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class TestAddressMask extends TypesTestCase {

	/* These are all valid rules -- should all pass. */
    @DataProvider(name = "validRules")
    public Object[][] validData() {
        return new Object[][] {
            { "129.34.55.67"},
            { "129.*.78.55"},
            {".central.sun.com"},
            {"foo.central.sun.com"},
            {"foo.*.sun.*"},
            {"128.*.*.*"},
            {"129.45.23.67/22"},
            {"128.33.23.21/32"},
            {"*.*.*.*"},
            {"129.45.67.34/0"},
            {"foo.com"},
            {"foo"}
        };
    }

 @DataProvider(name = "invalidRules")
  public Object[][] invalidData() {
    return new Object[][] {
         { "129.*.900.67" },
         { "129.67" },
         {"   "},
         {"129.56.78.90/2000"},
         {"677.777.AG.BC"},
         {"/34"},
         {"234.12.12.*/31"},
         {"234.12.12.90/"},
         {"129.34.56.78/-100"},
         {"129"},
         {"129.34.-90.67"},
         {"129.**.56.67"},
         {"foo bar.com"},
         {"12foo.example.com"},
         {"123.45."},
         {".central.sun day.com"},
         {"129.34.45.45/4/3/"}
    };
  }

 @DataProvider(name = "toStringRule")
 public Object[][] toStringData() {
     return new Object[][] {
         {"129.35.45.66/12"}
     };
 }

@Test(dataProvider = "validRules")
 public void testValidDecode(String mask)
 throws Exception {
     AddressMask.decode(mask);
 }

 @Test(expectedExceptions=ConfigException.class, dataProvider="invalidRules")
 public void testInvalidDecode(String mask)
 throws Exception {
     try {
         AddressMask.decode(mask);
     } catch (ConfigException e) {
         throw e;
     } catch (Exception e) {
         System.out.println(
                 "Invalid mask  <" + mask + "> threw wrong exception type.");
         throw e;
     }
     throw new RuntimeException(
             "Invalid mask <" + mask + "> did not throw an exception.");
 }

  @DataProvider(name = "matchRules")
  public Object[][] ruleMatchData() {
     return new Object[][] {
             {
                 //Rules
                 new String[] {
                         "129.56.*.22", //1
                         "*.domain.com", //2
                         "foo.example.com", //3
                         "126.67.89.90", //4
                         "90.89.78.67/30", //5
                         ".test.com", //6
                         "128.153.147.32/21",//7
                         "128.153.146.32/26",//8
                         "90.89.78.67/26"}, //9
                //Addresses
                new String[] {
                         "128.153.147.45", //rule 7
                         "128.153.146.60", //rule 8
                         "148.45.45.46",     //host
                         "129.56.78.22",    //rule 1
                         "148.45.45.47",    //host
                         "148.45.45.48",    //host
                         "90.89.78.65"},   //rule 5
              //Hostnames
              new String[]  {
                         "some.host.name", //addr
                         "some.host.name", //addr
                         "foo.example.com", //rule 3
                         "some.host.name", //addr
                         "foo.test.com",       //rule 6
                         "foo.domain.com", //rule 2
                         "some.host.name" //addr
                         }
             }
     };
 }

 @DataProvider(name = "noMatchRules")
 public Object[][] ruleNoMatchData() {
     return new Object[][] {
         {
             // Rule to not match
             new String[] {
                     "129.56.*.22", //1
                     "*.domain.com", //2
                     "foo.example.com", //3
                     "126.67.89.90", //4
                     "90.89.78.67/30", //5
                     ".test.com", //6
                     "128.153.147.32/21",//7
                     "128.153.146.32/26",//8
                     "90.89.78.67/26"}, //9
                     //Addresses
                     new String[] {
                              "128.153.140.45",
                              "128.153.143.255",
                              "148.45.45.46",
                              "126.56.78.22",
                              "148.45.45.47",
                              "148.45.45.48",
                              "90.89.78.128",
                              "148.45.45.49"},
                   //Hostnames
                   new String[]  {
                              "some.host.name",
                              "some.host.name",
                              "foo.examplee.com",
                              "some.host.name",
                              "foo.ttest.com",
                              "foo.domain.comm",
                              "some.host.name",
                              "f.e.c",
                              "foo.domain.cm"}
                  }
         };
     }

 @DataProvider(name = "matchWCRules")
 public Object[][] ruleMatchWCData() {
     return new Object[][] {
             {
                 //Rules
                 new String[] {
                         "*.*.*",
                         "*.*.*.*"},
                //Addresses
                new String[] {
                        "129.34.45.12",
                        "129.34.45.13"},
              //Hostnames
              new String[]  {
                         "some.host.name" ,
                         "some.host.name"}
             }
     };
 }

 @Test(dataProvider = "matchRules")
 public void testMatch(String[] rules, String[] addrs, String[]hostNames) {
     boolean ret;
     ret=match(rules,addrs,hostNames);
     assertTrue(ret);
 }

 @Test(dataProvider = "matchWCRules")
 public void testWildCardMatch(String[] rules, String[] addrs,
         String[]hostNames) {
     boolean ret;
     ret=match(rules,addrs,hostNames);
     assertTrue(ret);
 }

 @Test(dataProvider = "noMatchRules")
 public void testNoMatch(String[] rules, String[] addrs,
         String[] hostNames) {
     boolean ret;
     ret=match(rules,addrs,hostNames);
     assertFalse(ret);
 }

 @Test(dataProvider="toStringRule")
 public void testToString(String rule) {
     try {
         AddressMask m = AddressMask.decode(rule);
         assertEquals(rule, m.toString());
     } catch (ConfigException ce) {
         throw new RuntimeException(
                 "Invalid mask <" + rule +
                 "> all data should be valid for this test");
     }
 }

  private boolean match(String[] rules, String[] addrs,  String[]hostNames) {
    boolean ret=true;
    int i=0;

    AddressMask[] m = new AddressMask[rules.length];
    try {
      for (i = 0; i < rules.length; i++) {
        m[i] = AddressMask.decode(rules[i]);
      }
    } catch (ConfigException ce) {
      throw new RuntimeException(
              "Invalid mask <" + rules[i] +
                      "> all data must be valid for this test");
    }
    for(int j = 0; j < addrs.length; j++) {
      try  {
        InetAddress addr = InetAddress.getByName(addrs[j]);
        if(!AddressMask.maskListContains(addr.getAddress(), hostNames[j], m)) {
          ret=false;
          break;
        }
      } catch (UnknownHostException ex) {
        ret=false;
      }
    }
    return ret;
  }

  /*
    IPV6 data and tests.
  */

  //Invalid IPv6 expressions.
  @DataProvider(name = "invalid6Rules")
  public Object[][] inValid6Data() {
        return new Object[][] {
                {"2001:feca:ba23:cd1f:dcb1:1010:9234:4088///124"},
                {"2001:feca:ba23:cd1f:dcb1:1010:9234:4088?124"},
                {"2001:fecz:ba23:cd1f:dcb1:1010:9234:4088/124"},
                {"2001:fecd:ba23:cd1ff:dcb1:1010:9234:4088/46"},
                {"0:0:0:0:0:ffff:101..45.75.219"},
                {"0:0:0:0:0:0:101.45.75.700"},
                {"1080::8:800:200C:417A/500"},
                {"1080::8:800:*:417A/66"},
                 {"2001:fecd:ba23:cd1ff:dcb1:1010:202.45.66.20"},
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


  @DataProvider(name = "match6Rules")
  public Object[][] ruleMatch6Data() {
     return new Object[][] {
             {
                 //IPV6 Rules
                 new String[] {
                         "[12ab:0:0:cd30::]/60",
                         "::ffff:72.56.78.9",
                         "::",
                         "42ab:0:0:dd30::"},
                //IPv6 Addresses
                new String[] {
                         "12ab:0:0:cd3f:0000:0000:23DC:DC30",
                         "72.56.78.9",
                         "::",
                         "42ab:0000:0000:dd30:0000:0000:0000:0000"},
              //ignored Hostnames
              new String[]  {
                         "ignored.host.name",
                         "ignored.host.name",
                         "ignored.host.name",
                         "ignored.host.name"
                         }
             }
     };
 }

 @Test(dataProvider = "valid6Rules")
  public void testValid6Decode(String mask)
          throws Exception {
    AddressMask.decode(mask);
  }

  @Test(expectedExceptions=ConfigException.class, dataProvider="invalid6Rules")
  public void testInvalid6Decode(String mask)
          throws Exception {
    try {
      AddressMask.decode(mask);
    } catch (ConfigException e) {
      throw e;
    } catch (Exception e) {
      System.out.println(
              "Invalid mask  <" + mask + "> threw wrong exception type.");
      throw e;
    }
    throw new RuntimeException(
            "Invalid mask <" + mask + "> did not throw an exception.");
  }

  @Test(dataProvider = "match6Rules")
  public void testMatch6(String[] rules, String[] addrs, String[]hostNames) {
      boolean ret;
      ret=match(rules,addrs,hostNames);
      assertTrue(ret);
  }
}
