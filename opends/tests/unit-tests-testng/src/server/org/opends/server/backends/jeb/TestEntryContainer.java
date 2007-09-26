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
package org.opends.server.backends.jeb;

import static org.testng.AssertJUnit.assertTrue;

import java.io.File;
import java.util.List;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.Backend;
import org.opends.server.core.DirectoryServer;
import org.opends.server.admin.std.server.LocalDBBackendCfg;
import org.opends.server.admin.std.meta.LocalDBBackendCfgDefn;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.types.Entry;
import org.opends.server.types.FilePermission;
import org.opends.server.types.DN;
import org.opends.server.util.Base64;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;

/**
 * EntryContainer tester.
 */
public class TestEntryContainer extends JebTestCase {
  private  String beID="userRoot";
  private BackendImpl be;

  private static final String ldifString = "dn: dc=com\n"
      + "objectClass: top\n" + "objectClass: domain\n" + "\n"
      + "dn: dc=example,dc=com\n" + "objectClass: top\n"
      + "objectClass: domain\n" + "\n"
      + "dn: ou=People,dc=example,dc=com\n" + "objectClass: top\n"
      + "objectClass: organizationalUnit\n" + "\n"
      + "dn: uid=user.1,ou=People,dc=example,dc=com\n"
      + "objectClass: top\n" + "objectClass: person\n"
      + "objectClass: organizationalPerson\n"
      + "objectClass: inetOrgPerson\n" + "uid: user.1\n"
      + "homePhone: 951-245-7634\n"
      + "description: This is the description for Aaccf Amar.\n"
      + "st: NC\n" + "mobile: 027-085-0537\n"
      + "postalAddress: Aaccf Amar$17984 Thirteenth Street"
      + "$Rockford, NC  85762\n" + "mail: user.1@example.com\n"
      + "cn: Aaccf Amar\n" + "l: Rockford\n" + "pager: 508-763-4246\n"
      + "street: 17984 Thirteenth Street\n"
      + "telephoneNumber: 216-564-6748\n" + "employeeNumber: 1\n"
      + "sn: Amar\n" + "givenName: Aaccf\n" + "postalCode: 85762\n"
      + "userPassword: password\n" + "initials: AA\n" + "\n"
      + "dn: o=Airius\n" + "objectClass: top\n"
      + "objectClass: organization\n" + "\n"
      + "dn:: b3U95Za25qWt6YOoLG89QWlyaXVz\n"
      + "# dn:: ou=<JapaneseOU>,o=Airius\n" + "objectclass: top\n"
      + "objectclass: organizationalUnit\n" + "ou:: 5Za25qWt6YOo\n"
      + "# ou:: <JapaneseOU>\n" + "ou;lang-ja:: 5Za25qWt6YOo\n"
      + "# ou;lang-ja:: <JapaneseOU>\n"
      + "ou;lang-ja;phonetic:: 44GI44GE44GO44KH44GG44G2\n"
      + "# ou;lang-ja:: <JapaneseOU_in_phonetic_representation>\n"
      + "ou;lang-en: Sales\n" + "description: Japanese office\n" + "\n"
      + "dn:: dWlkPXJvZ2FzYXdhcmEsb3U95Za25qWt6YOoLG89QWlyaXVz\n"
      + "# dn:: uid=<uid>,ou=<JapaneseOU>,o=Airius\n"
      + "userpassword: {SHA}O3HSv1MusyL4kTjP+HKI5uxuNoM=\n"
      + "objectclass: top\n" + "objectclass: person\n"
      + "objectclass: organizationalPerson\n"
      + "objectclass: inetOrgPerson\n" + "uid: rogasawara\n"
      + "mail: rogasawara@airius.co.jp\n"
      + "givenname;lang-ja:: 44Ot44OJ44OL44O8\n"
      + "# givenname;lang-ja:: <JapaneseGivenname>\n"
      + "sn;lang-ja:: 5bCP56yg5Y6f\n" + "# sn;lang-ja:: <JapaneseSn>\n"
      + "cn;lang-ja:: 5bCP56yg5Y6fIOODreODieODi+ODvA==\n"
      + "# cn;lang-ja:: <JapaneseCn>\n"
      + "title;lang-ja:: 5Za25qWt6YOoIOmDqOmVtw==\n"
      + "# title;lang-ja:: <JapaneseTitle>\n" + "preferredlanguage: ja\n"
      + "givenname:: 44Ot44OJ44OL44O8\n"
      + "# givenname:: <JapaneseGivenname>\n" + "sn:: 5bCP56yg5Y6f\n"
      + "# sn:: <JapaneseSn>\n" + "cn:: 5bCP56yg5Y6fIOODreODieODi+ODvA==\n"
      + "# cn:: <JapaneseCn>\n" + "title:: 5Za25qWt6YOoIOmDqOmVtw==\n"
      + "# title:: <JapaneseTitle>\n"
      + "givenname;lang-ja;phonetic:: 44KN44Gp44Gr44O8\n"
      + "# givenname;lang-ja;phonetic:: "
      + "<JapaneseGivenname_in_phonetic_representation_kana>\n"
      + "sn;lang-ja;phonetic:: 44GK44GM44GV44KP44KJ\n"
      + "# sn;lang-ja;phonetic:: "
      + "<JapaneseSn_in_phonetic_representation_kana>\n"
      + "cn;lang-ja;phonetic:: 44GK44GM44GV44KP44KJIOOCjeOBqeOBq+ODvA==\n"
      + "# cn;lang-ja;phonetic:: "
      + "<JapaneseCn_in_phonetic_representation_kana>\n"
      + "title;lang-ja;phonetic:: " + ""
      + "44GI44GE44GO44KH44GG44G2IOOBtuOBoeOCh+OBhg==\n"
      + "# title;lang-ja;phonetic::\n"
      + "# <JapaneseTitle_in_phonetic_representation_kana>\n"
      + "givenname;lang-en: Rodney\n" + "sn;lang-en: Ogasawara\n"
      + "cn;lang-en: Rodney Ogasawara\n"
      + "title;lang-en: Sales, Director\n" + "\n" + "";

  private List<Entry> entryList;

  private long calculatedHighestID = 0;

  /**
   * Set up the environment for performing the tests in this suite.
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass
  public void setUp() throws Exception {
    // This test suite depends on having the schema available, so we'll make
    // sure the server is started.
    TestCaseUtils.startServer();

    // Create a set of entries
    entryList = TestCaseUtils.entriesFromLdifString(ldifString);

    // Remember the highest entryID
    calculatedHighestID = entryList.size();
  }

  /**
   * Tears down the environment for performing the tests in this suite.
   *
   * @throws Exception
   *           If the environment could not be finalized.
   */
  @AfterClass
  public void tearDown() throws Exception {
    TestCaseUtils.clearJEBackend(false, beID, "dc=example,dc=com");
  }

  /**
   * Test the entry container.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test()
  public void test1() throws Exception {
    TestCaseUtils.clearJEBackend(false, beID, null);
    be=(BackendImpl) DirectoryServer.getBackend(beID);
    RootContainer rootContainer = be.getRootContainer();
    EntryContainer entryContainer =
        rootContainer.getEntryContainer(DN.decode("dc=example,dc=com"));

    entryContainer.sharedLock.lock();
    try
    {
      EntryID actualHighestID = entryContainer.getHighestEntryID();
      assertTrue(actualHighestID.equals(new EntryID(0)));

      for (Entry entry : entryList) {
        entryContainer.addEntry(entry, null);
        Entry afterEntry = entryContainer.getEntry(entry.getDN());
        assertTrue(afterEntry != null);
      }
      actualHighestID = entryContainer.getHighestEntryID();
      assertTrue(actualHighestID.equals(new EntryID(calculatedHighestID)));
    }
    finally
    {
      entryContainer.sharedLock.unlock();
    }
  }
}
