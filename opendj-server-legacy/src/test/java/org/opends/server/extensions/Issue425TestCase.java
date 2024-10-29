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
 * Copyright 2024 3A Systems, LLC.
 */
package org.opends.server.extensions;

import org.opends.server.TestCaseUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * A set of test cases for the governing structure rule virtual attribute
 * provider.
 */
public class Issue425TestCase
       extends ExtensionsTestCase
{

  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.clearBackend("userRoot", "dc=example,dc=com");

    int resultCode = TestCaseUtils.applyModifications(true,
    "dn: cn=schema",
    "changetype: modify",
    "add: nameForms",
    "nameForms: ( 1.3.6.1.1.2.1\n" +
            "          NAME 'domainNameForm'\n" +
            "          OC domain\n" +
            "          MUST dc\n" +
            "          X-ORIGIN 'RFC2377' )",
    "nameForms: ( 1.3.6.1.4.1.56521.999.2.7.2\n" +
            "          NAME 'ouForm'\n" +
            "          OC organizationalUnit\n" +
            "          MUST ou\n" +
            "          X-ORIGIN 'fake name form' )",
    "-",
    "add: ditStructureRules",
    "dITStructureRules: ( 20\n" +
            "          NAME 'rootSuffixStructure'\n" +
            "          FORM domainNameForm )",
    "dITStructureRules: ( 21\n" +
            "          NAME 'ouStructure'\n" +
            "          FORM ouForm\n" +
            "          SUP 20 )"
    );
    assertEquals(resultCode, 0);
  }

    @Test
    public void test()
            throws Exception {
      TestCaseUtils.addEntry(
              "dn: ou=Accounts,dc=example,dc=com",
              "objectClass: organizationalunit",
              "objectClass: top",
              "ou: People"
      );
      //add OC subentry without DSR (warning level)
      TestCaseUtils.addEntry(
              "dn: cn=test-subentry,ou=Accounts,dc=example,dc=com",
              "objectClass: top",
              "objectClass: extensibleObject",
              "objectClass: subentry",
              "objectClass: collectiveAttributeSubentry",
              "subtreeSpecification: {}",
              "cn: test-subentry"
      );
      //Entry o=test-subentry2,ou=Accounts,dc=example,dc=com violates the Directory Server schema configuration because its RDN does not contain attribute cn that is required by name form subentryNameForm
      assertThrows(new ThrowingRunnable() {
                     @Override
                     public void run() throws Throwable {
                       TestCaseUtils.addEntry(
                               "dn: o=test-subentry2,ou=Accounts,dc=example,dc=com",
                               "objectClass: top",
                               "objectClass: extensibleObject",
                               "objectClass: subentry",
                               "objectClass: collectiveAttributeSubentry",
                               "subtreeSpecification: {}",
                               "cn: test-subentry2"
                       );
                     }
                   }
      );
      int resultCode = TestCaseUtils.applyModifications(true,
              "dn: cn=schema",
              "changetype: modify",
              "add: ditStructureRules",
              "dITStructureRules: ( 177\n"+
                      "          NAME 'subentryStructure'\n"+
                      "          FORM subentryNameForm\n"+
                      "          SUP ( 20 21 ) )"
      );
      assertEquals(resultCode, 0);

      //add OC subentry with DSR: RDN OC subentry MUST cn
      TestCaseUtils.addEntry(
              "dn: cn=test-subentry-ok,ou=Accounts,dc=example,dc=com",
              "objectClass: top",
              "objectClass: extensibleObject",
              "objectClass: subentry",
              "objectClass: collectiveAttributeSubentry",
              "subtreeSpecification: {}",
              "cn: test-subentry-ok"
      );

      //add OC subentry with DSR violation: RDN OC subentry MUST cn
      assertThrows(new ThrowingRunnable() {
                     @Override
                     public void run() throws Throwable {
                       TestCaseUtils.addEntry(
                               "dn: o=test-subentry-error,ou=Accounts,dc=example,dc=com",
                               "objectClass: top",
                               "objectClass: extensibleObject",
                               "objectClass: subentry",
                               "objectClass: collectiveAttributeSubentry",
                               "subtreeSpecification: {}",
                               "cn: test-subentry-error"
                       );
                     }
                   }
      );
    }
}
