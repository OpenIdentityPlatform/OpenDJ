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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.*;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.StaticUtils;
import static org.opends.server.util.StaticUtils.getBytes;

import org.testng.annotations.DataProvider;
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
   * Encodes this entry using the V3 encoding.
   *
   * @param  buffer  The buffer to encode into.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to encode the entry.
   */
  private void encodeV1(Entry entry, ByteStringBuilder buffer)
         throws DirectoryException
  {
    // The version number will be one byte.
    buffer.append((byte)0x01);

    // TODO: Can we encode the DN directly into buffer?
    byte[] dnBytes  = getBytes(entry.getDN().toString());
    buffer.appendBERLength(dnBytes.length);
    buffer.append(dnBytes);


    // Encode number of OCs and 0 terminated names.
    int i = 1;
    ByteStringBuilder bsb = new ByteStringBuilder();
    for (String ocName : entry.getObjectClasses().values())
    {
      bsb.append(ocName);
      if(i < entry.getObjectClasses().values().size())
      {
        bsb.append((byte)0x00);
      }
      i++;
    }
    buffer.appendBERLength(bsb.length());
    buffer.append(bsb);


    // Encode the user attributes in the appropriate manner.
    encodeV1Attributes(buffer, entry.getUserAttributes());


    // The operational attributes will be encoded in the same way as
    // the user attributes.
    encodeV1Attributes(buffer, entry.getOperationalAttributes());
  }

  private void encodeV1Attributes(ByteStringBuilder buffer,
                                Map<AttributeType,List<Attribute>> attributes)
      throws DirectoryException
  {
    int numAttributes = 0;

    // First count how many attributes are there to encode.
    for (List<Attribute> attrList : attributes.values())
    {
      for (Attribute a : attrList)
      {
        if (a.isVirtual() || a.isEmpty())
        {
          continue;
        }

        numAttributes++;
      }
    }

    // Encoded one-to-five byte number of attributes
    buffer.appendBERLength(numAttributes);


    // The attributes will be encoded as a sequence of:
    // - A UTF-8 byte representation of the attribute name.
    // - A zero delimiter
    // - A one-to-five byte number of values for the attribute
    // - A sequence of:
    //   - A one-to-five byte length for the value
    //   - A UTF-8 byte representation for the value
    for (List<Attribute> attrList : attributes.values())
    {
      for (Attribute a : attrList)
      {
        byte[] nameBytes = getBytes(a.getNameWithOptions());
        buffer.append(nameBytes);
        buffer.append((byte)0x00);

        buffer.appendBERLength(a.size());
        for(AttributeValue v : a)
        {
          buffer.appendBERLength(v.getValue().length());
          buffer.append(v.getValue());
        }
      }
    }
  }

    /**
   * Encodes this entry using the V3 encoding.
   *
   * @param  buffer  The buffer to encode into.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to encode the entry.
   */
  private void encodeV2(Entry entry, ByteStringBuilder buffer,
                        EntryEncodeConfig config)
         throws DirectoryException
  {
    // The version number will be one byte.
    buffer.append((byte)0x02);

    // Get the encoded respresentation of the config.
    config.encode(buffer);

    // If we should include the DN, then it will be encoded as a
    // one-to-five byte length followed by the UTF-8 byte
    // representation.
    if (! config.excludeDN())
    {
      // TODO: Can we encode the DN directly into buffer?
      byte[] dnBytes  = getBytes(entry.getDN().toString());
      buffer.appendBERLength(dnBytes.length);
      buffer.append(dnBytes);
    }


    // Encode the object classes in the appropriate manner.
    if (config.compressObjectClassSets())
    {
      ByteStringBuilder bsb = new ByteStringBuilder();
      config.getCompressedSchema().encodeObjectClasses(bsb,
          entry.getObjectClasses());
      buffer.appendBERLength(bsb.length());
      buffer.append(bsb);
    }
    else
    {
      // Encode number of OCs and 0 terminated names.
      int i = 1;
      ByteStringBuilder bsb = new ByteStringBuilder();
      for (String ocName : entry.getObjectClasses().values())
      {
        bsb.append(ocName);
        if(i < entry.getObjectClasses().values().size())
        {
          bsb.append((byte)0x00);
        }
        i++;
      }
      buffer.appendBERLength(bsb.length());
      buffer.append(bsb);
    }


    // Encode the user attributes in the appropriate manner.
    encodeV2Attributes(buffer, entry.getUserAttributes(), config);


    // The operational attributes will be encoded in the same way as
    // the user attributes.
    encodeV2Attributes(buffer, entry.getOperationalAttributes(), config);
  }

  private void encodeV2Attributes(ByteStringBuilder buffer,
                                Map<AttributeType,List<Attribute>> attributes,
                                EntryEncodeConfig config)
      throws DirectoryException
  {
    int numAttributes = 0;

    // First count how many attributes are there to encode.
    for (List<Attribute> attrList : attributes.values())
    {
      for (Attribute a : attrList)
      {
        if (a.isVirtual() || a.isEmpty())
        {
          continue;
        }

        numAttributes++;
      }
    }

    // Encoded one-to-five byte number of attributes
    buffer.appendBERLength(numAttributes);

    if (config.compressAttributeDescriptions())
    {
      for (List<Attribute> attrList : attributes.values())
      {
        for (Attribute a : attrList)
        {
          if (a.isVirtual() || a.isEmpty())
          {
            continue;
          }

          ByteStringBuilder bsb = new ByteStringBuilder();
          config.getCompressedSchema().encodeAttribute(bsb, a);
          buffer.appendBERLength(bsb.length());
          buffer.append(bsb);
        }
      }
    }
    else
    {
      // The attributes will be encoded as a sequence of:
      // - A UTF-8 byte representation of the attribute name.
      // - A zero delimiter
      // - A one-to-five byte number of values for the attribute
      // - A sequence of:
      //   - A one-to-five byte length for the value
      //   - A UTF-8 byte representation for the value
      for (List<Attribute> attrList : attributes.values())
      {
        for (Attribute a : attrList)
        {
          byte[] nameBytes = getBytes(a.getNameWithOptions());
          buffer.append(nameBytes);
          buffer.append((byte)0x00);

          buffer.appendBERLength(a.size());
          for(AttributeValue v : a)
          {
            buffer.appendBERLength(v.getValue().length());
            buffer.append(v.getValue());
          }
        }
      }
    }
  }

  /**
   * Test entry.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test()
  public void testEntryToAndFromDatabase() throws Exception {
    // Make sure that the server is up and running.
    TestCaseUtils.startServer();

    // Convert the test LDIF string to a byte array
    byte[] originalLDIFBytes = StaticUtils.getBytes(ldifString);

    LDIFReader reader = new LDIFReader(new LDIFImportConfig(
        new ByteArrayInputStream(originalLDIFBytes)));

    Entry entryBefore, entryAfter;
    while ((entryBefore = reader.readEntry(false)) != null) {
      ByteString bytes = ID2Entry.entryToDatabase(entryBefore,
          new DataConfig(false, false, null));

      entryAfter = ID2Entry.entryFromDatabase(bytes,
                        DirectoryServer.getDefaultCompressedSchema());

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
          boolean found = false;

          for (Attribute attrAfter : listAfter) {
            if (attrAfter.optionsEqual(attrBefore.getOptions())) {
              // Found the corresponding attribute

              assertEquals(attrBefore, attrAfter);
              found = true;
            }
          }

          assertTrue(found);
        }
      }
    }
    reader.close();
  }

  /**
   * Tests the entry encoding and decoding process the version 1 encoding.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test()
  public void testEntryToAndFromDatabaseV1() throws Exception {
    // Make sure that the server is up and running.
    TestCaseUtils.startServer();

    // Convert the test LDIF string to a byte array
    byte[] originalLDIFBytes = StaticUtils.getBytes(ldifString);

    LDIFReader reader = new LDIFReader(new LDIFImportConfig(
        new ByteArrayInputStream(originalLDIFBytes)));

    Entry entryBefore, entryAfterV1;
    while ((entryBefore = reader.readEntry(false)) != null) {
      ByteStringBuilder bsb = new ByteStringBuilder();
      encodeV1(entryBefore, bsb);
      entryAfterV1 = Entry.decode(bsb.asReader());

      assertEquals(entryBefore, entryAfterV1);
    }
    reader.close();
  }

  /**
   * Retrieves a set of entry encode configurations that may be used to test the
   * entry encoding and decoding capabilities.
   */
  @DataProvider(name = "encodeConfigs")
  public Object[][] getEntryEncodeConfigs()
  {
    return new Object[][]
    {
      new Object[] { new EntryEncodeConfig() },
      new Object[] { new EntryEncodeConfig(false, false, false) },
      new Object[] { new EntryEncodeConfig(true, false, false) },
      new Object[] { new EntryEncodeConfig(false, true, false) },
      new Object[] { new EntryEncodeConfig(false, false, true) },
      new Object[] { new EntryEncodeConfig(true, true, false) },
      new Object[] { new EntryEncodeConfig(true, false, true) },
      new Object[] { new EntryEncodeConfig(false, true, true) },
      new Object[] { new EntryEncodeConfig(true, true, true) },
    };
  }

  /**
   * Tests the entry encoding and decoding process the version 1 encoding.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "encodeConfigs")
  public void testEntryToAndFromDatabaseV2(EntryEncodeConfig config)
         throws Exception {
    // Make sure that the server is up and running.
    TestCaseUtils.startServer();

    // Convert the test LDIF string to a byte array
    byte[] originalLDIFBytes = StaticUtils.getBytes(ldifString);

    LDIFReader reader = new LDIFReader(new LDIFImportConfig(
        new ByteArrayInputStream(originalLDIFBytes)));

    Entry entryBefore, entryAfterV2;
    while ((entryBefore = reader.readEntry(false)) != null) {
      ByteStringBuilder bsb = new ByteStringBuilder();
      encodeV2(entryBefore, bsb, config);
      entryAfterV2 = Entry.decode(bsb.asReader());
      if (config.excludeDN())
      {
        entryAfterV2.setDN(entryBefore.getDN());
      }
      assertEquals(entryBefore, entryAfterV2);
    }
    reader.close();
  }

  /**
   * Tests the entry encoding and decoding process the version 1 encoding.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "encodeConfigs")
  public void testEntryToAndFromDatabaseV3(EntryEncodeConfig config)
         throws Exception {
    // Make sure that the server is up and running.
    TestCaseUtils.startServer();

    // Convert the test LDIF string to a byte array
    byte[] originalLDIFBytes = StaticUtils.getBytes(ldifString);

    LDIFReader reader = new LDIFReader(new LDIFImportConfig(
        new ByteArrayInputStream(originalLDIFBytes)));

    Entry entryBefore, entryAfterV3;
    while ((entryBefore = reader.readEntry(false)) != null) {
      ByteStringBuilder bsb = new ByteStringBuilder();
      entryBefore.encode(bsb, config);
      entryAfterV3 = Entry.decode(bsb.asReader());
      if (config.excludeDN())
      {
        entryAfterV3.setDN(entryBefore.getDN());
      }
      assertEquals(entryBefore, entryAfterV3);
    }
    reader.close();
  }
}
