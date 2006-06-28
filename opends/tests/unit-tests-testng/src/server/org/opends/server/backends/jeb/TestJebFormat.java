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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;

import org.opends.server.InitialDirectoryServerFixture;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.ObjectClass;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.Test;

/**
 * JebFormat Tester.
 */
public class TestJebFormat extends JebTestCase {
  private static final String ldifString =
    "dn: uid=user.1,ou=People,dc=example,dc=com\n"
      + "objectClass: top\n"
      + "objectClass: person\n"
      + "objectClass: organizationalPerson\n"
      + "objectClass: inetOrgPerson\n"
      + "uid: user.1\n"
      + "homePhone: 951-245-7634\n"
      + "description: This is the description for Aaccf Amar.\n"
      + "st: NC\n"
      + "mobile: 027-085-0537\n"
      + "postalAddress: Aaccf Amar$17984 Thirteenth Street"
      + "$Rockford, NC  85762\n"
      + "mail: user.1@example.com\n"
      + "cn: Aaccf Amar\n"
      + "l: Rockford\n"
      + "pager: 508-763-4246\n"
      + "street: 17984 Thirteenth Street\n"
      + "telephoneNumber: 216-564-6748\n"
      + "employeeNumber: 1\n"
      + "sn: Amar\n"
      + "givenName: Aaccf\n"
      + "postalCode: 85762\n"
      + "userPassword: password\n"
      + "initials: AA\n"
      + "\n"
      + "dn:: b3U95Za25qWt6YOoLG89QWlyaXVz\n"
      + "# dn:: ou=<JapaneseOU>,o=Airius\n"
      + "objectclass: top\n"
      + "objectclass: organizationalUnit\n"
      + "ou:: 5Za25qWt6YOo\n"
      + "# ou:: <JapaneseOU>\n"
      + "ou;lang-ja:: 5Za25qWt6YOo\n"
      + "# ou;lang-ja:: <JapaneseOU>\n"
      + "ou;lang-ja;phonetic:: 44GI44GE44GO44KH44GG44G2\n"
      + "# ou;lang-ja:: <JapaneseOU_in_phonetic_representation>\n"
      + "ou;lang-en: Sales\n"
      + "description: Japanese office\n"
      + "\n"
      + "dn:: dWlkPXJvZ2FzYXdhcmEsb3U95Za25qWt6YOoLG89QWlyaXVz\n"
      + "# dn:: uid=<uid>,ou=<JapaneseOU>,o=Airius\n"
      + "userpassword: {SHA}O3HSv1MusyL4kTjP+HKI5uxuNoM=\n"
      + "objectclass: top\n"
      + "objectclass: person\n"
      + "objectclass: organizationalPerson\n"
      + "objectclass: inetOrgPerson\n"
      + "uid: rogasawara\n"
      + "mail: rogasawara@airius.co.jp\n"
      + "givenname;lang-ja:: 44Ot44OJ44OL44O8\n"
      + "# givenname;lang-ja:: <JapaneseGivenname>\n"
      + "sn;lang-ja:: 5bCP56yg5Y6f\n"
      + "# sn;lang-ja:: <JapaneseSn>\n"
      + "cn;lang-ja:: 5bCP56yg5Y6fIOODreODieODi+ODvA==\n"
      + "# cn;lang-ja:: <JapaneseCn>\n"
      + "title;lang-ja:: 5Za25qWt6YOoIOmDqOmVtw==\n"
      + "# title;lang-ja:: <JapaneseTitle>\n"
      + "preferredlanguage: ja\n"
      + "givenname:: 44Ot44OJ44OL44O8\n"
      + "# givenname:: <JapaneseGivenname>\n"
      + "sn:: 5bCP56yg5Y6f\n"
      + "# sn:: <JapaneseSn>\n"
      + "cn:: 5bCP56yg5Y6fIOODreODieODi+ODvA==\n"
      + "# cn:: <JapaneseCn>\n"
      + "title:: 5Za25qWt6YOoIOmDqOmVtw==\n"
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
      + "title;lang-ja;phonetic:: "
      + ""
      + "44GI44GE44GO44KH44GG44G2IOOBtuOBoeOCh+OBhg==\n"
      + "# title;lang-ja;phonetic::\n"
      + "# <JapaneseTitle_in_phonetic_representation_kana>\n"
      + "givenname;lang-en: Rodney\n"
      + "sn;lang-en: Ogasawara\n"
      + "cn;lang-en: Rodney Ogasawara\n"
      + "title;lang-en: Sales, Director\n" + "\n" + "";

  /**
   * Test entry IDs.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test()
  public void testEntryIDToAndFromDatabase() throws Exception {
    long[] vals = { 128, 1234567, 0, 1, -1, 2 ^ 32 - 1, 2 ^ 63 - 1 };

    for (long before : vals) {
      byte[] bytes = JebFormat.entryIDToDatabase(before);
      long after = JebFormat.entryIDFromDatabase(bytes);

      assertEquals(before, after);
    }
  }

  private void entryIDListToAndFromDatabase(long[] before) throws Exception {
    byte[] bytes = JebFormat.entryIDListToDatabase(before);
    /*
     * printError(String.format("encoded count=%d len=%d",
     * before.length, bytes.length));
     */
    long[] after = JebFormat.entryIDListFromDatabase(bytes);

    assertTrue(Arrays.equals(before, after));
  }

  /**
   * Test entry ID lists.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test()
  public void testEntryIDListToAndFromDatabase() throws Exception {
    long[] array;
    array = new long[] { 1, 2, 3, 4, 5 };
    entryIDListToAndFromDatabase(array);
    array = new long[] { 999999 };
    entryIDListToAndFromDatabase(array);
    array = new long[] { 1, 128, 1234567 };
    entryIDListToAndFromDatabase(array);
    array = new long[100000];
    for (int i = 0; i < 100000; i++) {
      array[i] = i * 2 + 1;
    }
    entryIDListToAndFromDatabase(array);
  }

  /**
   * Test entry.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test()
  public void testEntryToAndFromDatabase() throws Exception {
    InitialDirectoryServerFixture.FACTORY.setUp();

    // Convert the test LDIF string to a byte array
    byte[] originalLDIFBytes = StaticUtils.getBytes(ldifString);

    LDIFReader reader = new LDIFReader(new LDIFImportConfig(
        new ByteArrayInputStream(originalLDIFBytes)));

    Entry entryBefore, entryAfter;
    while ((entryBefore = reader.readEntry(false)) != null) {
      byte[] bytes = JebFormat.entryToDatabase(entryBefore);

      entryAfter = JebFormat.entryFromDatabase(bytes);

      // check DN and number of attributes
      assertEquals(entryBefore.getAttributes().size(), entryAfter
          .getAttributes().size());

      assertEquals(entryBefore.getDN(), entryAfter.getDN());

      // check the object classes were not changed
      for (String ocBefore : entryBefore.getObjectClasses().values()) {
        ObjectClass objectClass = DirectoryServer.getObjectClass(ocBefore
            .toLowerCase());
        if (objectClass == null) {
          objectClass = DirectoryServer.getDefaultObjectClass(ocBefore);
        }
        String ocAfter = entryAfter.getObjectClasses().get(objectClass);

        assertEquals(ocBefore, ocAfter);
      }

      // check the user attributes were not changed
      for (AttributeType attrType : entryBefore.getUserAttributes()
          .keySet()) {
        List<Attribute> listBefore = entryBefore.getAttribute(attrType);
        List<Attribute> listAfter = entryAfter.getAttribute(attrType);

        assertTrue(listAfter != null);

        assertEquals(listBefore.size(), listAfter.size());

        for (Attribute attrBefore : listBefore) {
          for (Attribute attrAfter : listAfter) {
            if (attrAfter.optionsEqual(attrBefore.getOptions())) {
              // Found the corresponding attribute

              String beforeAttrString = attrBefore.toString();
              String afterAttrString = attrAfter.toString();

              if (!beforeAttrString.equals(afterAttrString)) {
                System.out.printf(
                    "Original attr:\n%s\nRetrieved attr:\n%s\n\n",
                    beforeAttrString, afterAttrString);
              }

              assertEquals(beforeAttrString, afterAttrString);
            }
          }
        }
      }
    }
    reader.close();

    InitialDirectoryServerFixture.FACTORY.tearDown();
  }
}
