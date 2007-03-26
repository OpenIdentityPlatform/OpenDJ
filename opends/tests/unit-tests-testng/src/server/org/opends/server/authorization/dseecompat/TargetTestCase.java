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

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.DN;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;

public class TargetTestCase extends DirectoryServerTestCase
{
  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
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
              "(target=\"ldap:///uid=bjensen*\")" +
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
         // These tests fail as we attempt to normalize the pattern as a DN.
         // <FAIL>
//         {
//              "dc=example,dc=com",
//              "(target=\"ldap:///*,ou=people,dc=example,dc=com\")" +
//                   "(targetattr=\"*\")(targetScope=\"subtree\")" +
//                   "(version 3.0; acl \"example\";" +
//                   " allow (all) userdn=\"ldap:///self\";)",
//              "uid=bjensen,ou=people,dc=example,dc=com",
//         },
//         {
//              "dc=example,dc=com",
//              "(target=\"ldap:///uid=bjensen,*,dc=com\")" +
//                   "(targetattr=\"*\")(targetScope=\"subtree\")" +
//                   "(version 3.0; acl \"example\";" +
//                   " allow (all) userdn=\"ldap:///self\";)",
//              "uid=bjensen,ou=people,dc=example,dc=com",
//         },
//         {
//              "dc=example,dc=com",
//              "(target=\"ldap:///*Anderson,ou=People,dc=example,dc=com\")" +
//                   "(targetattr=\"*\")(targetScope=\"subtree\")" +
//                   "(version 3.0; acl \"example\";" +
//                   " allow (all) userdn=\"ldap:///self\";)",
//              "uid=bjensen,ou=people,dc=example,dc=com",
//         },
         // </FAIL>
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
    };
  }


  @Test(dataProvider = "applicableTargets")
  public void applicableTargets(String aciDN, String aciString, String entryDN)
       throws Exception
  {
    Aci aci = Aci.decode(new ASN1OctetString(aciString), DN.decode(aciDN));
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
    Aci aci = Aci.decode(new ASN1OctetString(aciString), DN.decode(aciDN));
    boolean match = AciTargets.isTargetApplicable(aci,
                                                  aci.getTargets(),
                                                  DN.decode(entryDN));
    assertTrue(!match, aciString + " in entry " + aciDN +
         " incorrectly applied to " + entryDN);
  }
}
