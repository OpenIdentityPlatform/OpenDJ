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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */

package org.opends.server.authorization.dseecompat;

import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ByteString;
import static org.opends.server.config.ConfigConstants.ATTR_AUTHZ_GLOBAL_ACI;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.testng.Assert;

public class TargetTestCase extends AciTestCase
{
  private static final String testUser="uid=user.3,ou=People,o=test";
  private static final String aciUser = "o=test";

  private static final
  String entryCheckACI = "(target=\"ldap:///ou=People,o=test\")" +
          "(version 3.0; acl \"entryCheck aci\";" +
          "deny(all) (ssf < \"1\");)";

  private static final
  String attrACI = "(targetattr!=\"userPassword\")" +
        "(version 3.0; acl \"user attr ACI\"; " +
        "allow (search, read, compare) " +
        "userdn=\"ldap:///anyone\";)";


  @DataProvider
  public Object[][] matchingPatterns()
  {
    return new Object[][] {
         {
              "uid=bj*,ou=people,dc=example,dc=com",
              "uid=bjensen,ou=people,dc=example,dc=com"
         },
         {
              "uid=*,ou=people,dc=example,dc=com",
              "uid=bjensen,ou=people,dc=example,dc=com"
         },
         {
              "uid=bjensen*,**",
              "uid=bjensen,ou=people,dc=example,dc=com"
         },
         {
              "*jensen,ou=People,dc=example,dc=com",
              "uid=bjensen,ou=people,dc=example,dc=com"
         },
         {
              "bjensen,ou=People,dc=example,dc=com",
              "uid=bjensen,ou=people,dc=example,dc=com"
         },
         {
              "**",
              "uid=bjensen,ou=people,dc=example,dc=com"
         },
         {
              "*",
              "dc=com"
         },
         {
              "uid=bj*+sn=*,ou=people,dc=example,dc=com",
              "sn=jensen+uid=bjensen,ou=people,dc=example,dc=com"
         },
         {
              "bjensen",
              "uid=bjensen"
         },
         {
              "uid=dmiller, **, ou=branch level two, **, ou=aci branches, " +
                   "dc=example,dc=com",
              "uid=dmiller, ou=branch level three, ou=branch level two, " +
                   "ou=branch level one, ou=aci branches, dc=example,dc=com"
         },
    };
  }


  @DataProvider
  public Object[][] nonMatchingPatterns()
  {
    return new Object[][] {
         {
              "uid=bj*,ou=people,dc=example,dc=com",
              "uid=bjensen,ou=j,ou=people,dc=example,dc=com"
         },
         {
              "uid=*,ou=people,dc=example,dc=com",
              "cn=bjensen,ou=people,dc=example,dc=com"
         },
         {
              "uid=bjensen*,**",
              "uid=bjensen"
         },
         {
              "**",
              ""
         },
         {
              "*",
              "dc=example,dc=com"
         },
         {
              "uid=bj*+cn=*,ou=people,dc=example,dc=com",
              "sn=jensen+uid=bjensen,ou=people,dc=example,dc=com"
         },
         {
              "uid=dmiller, **, ou=Bad branch level, **, ou=aci branches, " +
                   "dc=example,dc=com",
              "uid=dmiller, ou=branch level three, ou=branch level two, " +
                   "ou=branch level one, ou=aci branches, dc=example,dc=com"
         },
         {
              "uid=dmiller, **, dc=example,dc=com",
              "uid=dmiller, dc=example,dc=com"
         },
    };
  }


  @DataProvider
  public Object[][] invalidPatterns()
  {
    return new Object[][] {
         {
              "uid=bj**,ou=people,dc=example,dc=com"
         },
         {
              "uid*=bjensen,ou=people,dc=example,dc=com",
         },
         {
              "uid=bjensen*,***",
         },
         {
              "uid=bjensen+*=jensen,ou=people,dc=example,dc=com"
         },
    };
  }


  @DataProvider
  public Object[][] applicableTargets()
  {
    return new Object[][] {
         {
              "dc=example,dc=com",
              "(target=\"ldap:///uid=bj*,ou=people,dc=example,dc=com\")" +
                   "(targetattr=\"*\")(targetScope=\"subtree\")" +
                   "(version 3.0; acl \"example\";" +
                   " allow (all) userdn=\"ldap:///self\";)",
              "uid=bjensen,ou=people,dc=example,dc=com",
         },
         {
              "dc=example,dc=com",
              "(target=\"ldap:///uid=*,ou=people,dc=example,dc=com\")" +
                   "(targetattr=\"*\")(targetScope=\"subtree\")" +
                   "(version 3.0; acl \"example\";" +
                   " allow (all) userdn=\"ldap:///self\";)",
              "uid=bjensen,ou=people,dc=example,dc=com",
         },
         {
              "dc=example,dc=com",
              "(target=\"ldap:///uid=bjensen*,**\")" +
                   "(targetattr=\"*\")(targetScope=\"subtree\")" +
                   "(version 3.0; acl \"example\";" +
                   " allow (all) userdn=\"ldap:///self\";)",
              "uid=bjensen,ou=people,dc=example,dc=com",
         },
         {
              "dc=example,dc=com",
              "(target=\"ldap:///uid=*,*,dc=example,dc=com\")" +
                   "(targetattr=\"*\")(targetScope=\"subtree\")" +
                   "(version 3.0; acl \"example\";" +
                   " allow (all) userdn=\"ldap:///self\";)",
              "uid=bjensen,ou=people,dc=example,dc=com",
         },
         {
              "dc=example,dc=com",
              "(target=\"ldap:///uid=*,ou=*,dc=example,dc=com\")" +
                   "(targetattr=\"*\")(targetScope=\"subtree\")" +
                   "(version 3.0; acl \"example\";" +
                   " allow (all) userdn=\"ldap:///self\";)",
              "uid=bjensen,ou=people,dc=example,dc=com",
         },
         {
              "dc=example,dc=com",
              "(target=\"ldap:///uid=BJ*,ou=people,dc=example,dc=com\")" +
                   "(targetattr=\"*\")(targetScope=\"subtree\")" +
                   "(version 3.0; acl \"example\";" +
                   " allow (all) userdn=\"ldap:///self\";)",
              "uid=bjensen,ou=people,dc=example,dc=com",
         },
         {
              "dc=example,dc=com",
              "(target!=\"ldap:///cn=*,ou=people,dc=example,dc=com\")" +
                   "(targetattr=\"*\")(targetScope=\"subtree\")" +
                   "(version 3.0; acl \"example\";" +
                   " allow (all) userdn=\"ldap:///self\";)",
              "uid=bjensen,ou=people,dc=example,dc=com",
         },
         {
              "dc=example,dc=com",
              "(target=\"ldap:///*,ou=people,dc=example,dc=com\")" +
                   "(targetattr=\"*\")(targetScope=\"subtree\")" +
                   "(version 3.0; acl \"example\";" +
                   " allow (all) userdn=\"ldap:///self\";)",
              "uid=bjensen,ou=people,dc=example,dc=com",
         },
         {
              "dc=example,dc=com",
              "(target=\"ldap:///uid=bjensen,**,dc=com\")" +
                   "(targetattr=\"*\")(targetScope=\"subtree\")" +
                   "(version 3.0; acl \"example\";" +
                   " allow (all) userdn=\"ldap:///self\";)",
              "uid=bjensen,ou=people,dc=example,dc=com",
         },
         {
              "dc=example,dc=com",
              "(target=\"ldap:///uid=bjensen,*,*,dc=com\")" +
                   "(targetattr=\"*\")(targetScope=\"subtree\")" +
                   "(version 3.0; acl \"example\";" +
                   " allow (all) userdn=\"ldap:///self\";)",
              "uid=bjensen,ou=people,dc=example,dc=com",
         },
         {
              "dc=example,dc=com",
              "(target=\"ldap:///*=*jensen,ou=People,dc=example,dc=com\")" +
                   "(targetattr=\"*\")(targetScope=\"subtree\")" +
                   "(version 3.0; acl \"example\";" +
                   " allow (all) userdn=\"ldap:///self\";)",
              "uid=bjensen,ou=people,dc=example,dc=com",
         },
         {
              "dc=example,dc=com",
              "(target=\"ldap:///*jensen,ou=People,dc=example,dc=com\")" +
                   "(targetattr=\"*\")(targetScope=\"subtree\")" +
                   "(version 3.0; acl \"example\";" +
                   " allow (all) userdn=\"ldap:///self\";)",
              "uid=bjensen,ou=people,dc=example,dc=com",
         },
         {
              "ou=aci branch,o=ACI Tests,dc=example,dc=com",
              "(target=\"ldap:///ou=Peo*,ou=aci branch, o=ACI Tests," +
                   "dc=example,dc=com\")(targetattr=\"*\")" +
                   "(version 3.0; acl \"add_aci3\"; allow" +
                   "(search,read) userdn=\"ldap:///all\";)",
              "uid=scarter,ou=People,ou=aci branch,o=ACI Tests," +
                   "dc=example,dc=com",
         },
         {
              "ou=aci branch,o=ACI Tests,dc=example,dc=com",
              "(target=\"ldap:///ou=*eople,ou=aci branch,o=ACI Tests," +
                   "dc=example,dc=com\")(targetattr=\"*\")" +
                   "(version 3.0; acl \"add_aci3\"; allow" +
                   "(search,read) userdn=\"ldap:///all\";)",
              "uid=scarter,ou=People,ou=aci branch,o=ACI Tests," +
                   "dc=example,dc=com",
         },
         {
              "ou=aci branch,o=ACI Tests,dc=example,dc=com",
              "(target=\"ldap:///ou=Pe*le,ou=aci branch,o=ACI Tests," +
                   "dc=example,dc=com\")(targetattr=\"*\")" +
                   "(version 3.0; acl \"add_aci3\"; allow" +
                   "(search,read) userdn=\"ldap:///all\";)",
              "uid=scarter,ou=People,ou=aci branch,o=ACI Tests," +
                   "dc=example,dc=com",
         },
         {
              "ou=aci branch,o=ACI Tests,dc=example,dc=com",
              "(target=\"ldap:///ou=Pe*l*,ou=aci branch,o=ACI Tests," +
                   "dc=example,dc=com\")(targetattr=\"*\")" +
                   "(version 3.0; acl \"add_aci3\"; allow" +
                   "(search,read) userdn=\"ldap:///all\";)",
              "uid=scarter,ou=People,ou=aci branch,o=ACI Tests," +
                   "dc=example,dc=com",
         },
         {
              "dc=example,dc=com",
              "(target=\"ldap:///**\")" +
                   "(targetattr=\"*\")(targetScope=\"subtree\")" +
                   "(version 3.0; acl \"example\";" +
                   " allow (all) userdn=\"ldap:///self\";)",
              "uid=bjensen,ou=people,dc=example,dc=com",
         },
         {
              "dc=example,dc=com",
              "(target=\"ldap:///*\")" +
                   "(targetattr=\"*\")(targetScope=\"subtree\")" +
                   "(version 3.0; acl \"example\";" +
                   " allow (all) userdn=\"ldap:///self\";)",
              "uid=bjensen,ou=people,dc=example,dc=com",
         },
    };
  }


  @DataProvider
  public Object[][] nonApplicableTargets()
  {
    return new Object[][] {
         {
              "ou=staff,dc=example,dc=com",
              "(target=\"ldap:///uid=bj*,ou=people,dc=example,dc=com\")" +
                   "(targetattr=\"*\")(targetScope=\"subtree\")" +
                   "(version 3.0; acl \"example\";" +
                   " allow (all) userdn=\"ldap:///self\";)",
              "uid=bjensen,ou=people,dc=example,dc=com",
         },
         {
              "uid=bjensen,ou=people,dc=example,dc=com",
              "(targetattr=\"*\")(targetScope=\"onelevel\")" +
                   "(version 3.0; acl \"example\";" +
                   " allow (all) userdn=\"ldap:///self\";)",
              "uid=bjensen,ou=people,dc=example,dc=com",
         },
         {
              "dc=example,dc=com",
              "(target=\"ldap:///uid=bjensen,*,dc=com\")" +
                   "(targetattr=\"*\")(targetScope=\"subtree\")" +
                   "(version 3.0; acl \"example\";" +
                   " allow (all) userdn=\"ldap:///self\";)",
              "uid=bjensen,ou=people,dc=example,dc=com",
         },
         {
              "dc=example,dc=com",
              "(target=\"ldap:///uid=bjensen*,*\")" +
                   "(targetattr=\"*\")(targetScope=\"subtree\")" +
                   "(version 3.0; acl \"example\";" +
                   " allow (all) userdn=\"ldap:///self\";)",
              "uid=bjensen,ou=people,dc=example,dc=com",
         },
         {
              "dc=example,dc=com",
              "(target=\"ldap:///uid=*,dc=example,dc=com\")" +
                   "(targetattr=\"*\")(targetScope=\"subtree\")" +
                   "(version 3.0; acl \"example\";" +
                   " allow (all) userdn=\"ldap:///self\";)",
              "uid=bjensen,ou=people,dc=example,dc=com",
         },
         {
              "dc=example,dc=com",
              "(target=\"ldap:///**\")" +
                   "(targetattr=\"*\")(targetScope=\"subtree\")" +
                   "(version 3.0; acl \"example\";" +
                   " allow (all) userdn=\"ldap:///self\";)",
              "",
         },
         {
              "dc=example,dc=com",
              "(target=\"ldap:///*\")" +
                   "(targetattr=\"*\")(targetScope=\"subtree\")" +
                   "(version 3.0; acl \"example\";" +
                   " allow (all) userdn=\"ldap:///self\";)",
              "",
         },
    };
  }

  @BeforeClass
  public void setupClass() throws Exception {
    deleteAttrFromAdminEntry(ACCESS_HANDLER_DN, ATTR_AUTHZ_GLOBAL_ACI);
    addEntries("o=test");
  }


  @Test(dataProvider = "matchingPatterns")
  public void matchingPatterns(String pattern, String entryDN)
       throws Exception
  {
    PatternDN patternDN = PatternDN.decode(pattern);
    boolean match = patternDN.matchesDN(DN.decode(entryDN));
    assertTrue(match, pattern + " did not match " + entryDN);
  }


  @Test(dataProvider = "nonMatchingPatterns")
  public void nonMatchingPatterns(String pattern, String entryDN)
       throws Exception
  {
    PatternDN patternDN = PatternDN.decode(pattern);
    boolean match = patternDN.matchesDN(DN.decode(entryDN));
    assertTrue(!match, pattern + " should not have matched " + entryDN);
  }


  @Test(dataProvider = "invalidPatterns",
        expectedExceptions = DirectoryException.class)
  public void invalidPatterns(String pattern)
       throws Exception
  {
    PatternDN.decode(pattern);
    fail("Invalid DN pattern " + pattern + " did not throw an exception");
  }


  @Test(dataProvider = "applicableTargets")
  public void applicableTargets(String aciDN, String aciString, String entryDN)
       throws Exception
  {
    Aci aci = Aci.decode(ByteString.valueOf(aciString), DN.decode(aciDN));
    boolean match = AciTargets.isTargetApplicable(aci,
                                                  aci.getTargets(),
                                                  DN.decode(entryDN));
    assertTrue(match, aciString + " in entry " + aciDN +
         " did not apply to " + entryDN);
  }


  @Test(dataProvider = "nonApplicableTargets")
  public void nonApplicableTargets(String aciDN, String aciString,
                                   String entryDN)
       throws Exception
  {
    Aci aci = Aci.decode(ByteString.valueOf(aciString), DN.decode(aciDN));
    boolean match = AciTargets.isTargetApplicable(aci,
                                                  aci.getTargets(),
                                                  DN.decode(entryDN));
    assertTrue(!match, aciString + " in entry " + aciDN +
         " incorrectly applied to " + entryDN);
  }

  /**
   * Test entry check ACI. Related to issue 4278.
   *
   * @throws Exception If a test doesn't pass.
   */
  @Test()
  public void testEntryCheckACI() throws Exception {
    try {
      String addACILDIF = makeAddLDIF("aci", aciUser, entryCheckACI);
      LDIFModify(addACILDIF, DIR_MGR_DN, PWD);
      String addAttrACILDIF = makeAddLDIF("aci", aciUser, attrACI);
      LDIFModify(addAttrACILDIF, DIR_MGR_DN, PWD);
         String userResults =
            LDAPSearchParams(testUser, PWD, null,null, null,
                     testUser, filter, null);
      Assert.assertTrue(userResults.equals(""));
    }
    finally
    {
      deleteAttrFromEntry(aciUser, "aci");
    }
  }
}
