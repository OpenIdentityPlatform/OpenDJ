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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.util.StaticUtils.*;
import static org.testng.Assert.*;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.EntryEncodeConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * DnKeyFormat Tester.
 */
@SuppressWarnings("javadoc")
public class TestDnKeyFormat extends DirectoryServerTestCase {

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
    buffer.appendByte(0x01);

    // TODO: Can we encode the DN directly into buffer?
    byte[] dnBytes  = getBytes(entry.getName().toString());
    buffer.appendBERLength(dnBytes.length);
    buffer.appendBytes(dnBytes);


    // Encode number of OCs and 0 terminated names.
    int i = 1;
    ByteStringBuilder bsb = new ByteStringBuilder();
    for (String ocName : entry.getObjectClasses().values())
    {
      bsb.appendUtf8(ocName);
      if(i < entry.getObjectClasses().values().size())
      {
        bsb.appendByte(0x00);
      }
      i++;
    }
    buffer.appendBERLength(bsb.length());
    buffer.appendBytes(bsb);


    encodeV1Attributes(buffer, entry.getAllAttributes(), false);
    encodeV1Attributes(buffer, entry.getAllAttributes(), true);
  }

  private void encodeV1Attributes(ByteStringBuilder buffer, Iterable<Attribute> attributes, boolean isOperational)
  {
    // Encoded one-to-five byte number of attributes
    buffer.appendBERLength(countNbAttrsToEncode(attributes, isOperational));

    append(buffer, attributes, isOperational);
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
    buffer.appendByte(0x02);

    // Get the encoded respresentation of the config.
    config.encode(buffer);

    // If we should include the DN, then it will be encoded as a
    // one-to-five byte length followed by the UTF-8 byte
    // representation.
    if (! config.excludeDN())
    {
      // TODO: Can we encode the DN directly into buffer?
      byte[] dnBytes  = getBytes(entry.getName().toString());
      buffer.appendBERLength(dnBytes.length);
      buffer.appendBytes(dnBytes);
    }


    // Encode the object classes in the appropriate manner.
    if (config.compressObjectClassSets())
    {
      config.getCompressedSchema().encodeObjectClasses(buffer,
          entry.getObjectClasses());
    }
    else
    {
      // Encode number of OCs and 0 terminated names.
      int i = 1;
      ByteStringBuilder bsb = new ByteStringBuilder();
      for (String ocName : entry.getObjectClasses().values())
      {
        bsb.appendUtf8(ocName);
        if(i < entry.getObjectClasses().values().size())
        {
          bsb.appendByte(0x00);
        }
        i++;
      }
      buffer.appendBERLength(bsb.length());
      buffer.appendBytes(bsb);
    }

    encodeV2Attributes(buffer, entry.getAllAttributes(), config, false);
    encodeV2Attributes(buffer, entry.getAllAttributes(), config, true);
  }

  private void encodeV2Attributes(
      ByteStringBuilder buffer, Iterable<Attribute> attributes, EntryEncodeConfig config, boolean isOperational)
      throws DirectoryException
  {
    // Encoded one-to-five byte number of attributes
    buffer.appendBERLength(countNbAttrsToEncode(attributes, isOperational));

    if (config.compressAttributeDescriptions())
    {
      for (Attribute a : attributes)
      {
        if (a.isVirtual() || a.isEmpty())
        {
          continue;
        }

        ByteStringBuilder bsb = new ByteStringBuilder();
        config.getCompressedSchema().encodeAttribute(bsb, a);
        buffer.appendBERLength(bsb.length());
        buffer.appendBytes(bsb);
      }
    }
    else
    {
      append(buffer, attributes, isOperational);
    }
  }

  private int countNbAttrsToEncode(Iterable<Attribute> attributes, boolean isOperational)
  {
    int result = 0;
    for (Attribute a : attributes)
    {
      if (!a.isVirtual()
          && !a.isEmpty()
          && a.getAttributeDescription().getAttributeType().isOperational() == isOperational)
      {
        result++;
      }
    }
    return result;
  }

  /**
   * The attributes will be encoded as a sequence of:
   * - A UTF-8 byte representation of the attribute name.
   * - A zero delimiter
   * - A one-to-five byte number of values for the attribute
   * - A sequence of:
   *   - A one-to-five byte length for the value
   *   - A UTF-8 byte representation for the value
   */
  private void append(ByteStringBuilder buffer, Iterable<Attribute> attributes, boolean isOperational)
  {
    for (Attribute a : attributes)
    {
      if (a.getAttributeDescription().getAttributeType().isOperational() != isOperational)
      {
        break;
      }

      buffer.appendBytes(getBytes(a.getAttributeDescription().toString()));
      buffer.appendByte(0x00);

      buffer.appendBERLength(a.size());
      for (ByteString v : a)
      {
        buffer.appendBERLength(v.length());
        buffer.appendBytes(v);
      }
    }
  }

  /**
   * Test entry.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testEntryToAndFromDatabase() throws Exception {
    ensureServerIsUpAndRunning();

    // Convert the test LDIF string to a byte array
    byte[] originalLDIFBytes = StaticUtils.getBytes(ldifString);

    try (final LDIFReader reader = new LDIFReader(new LDIFImportConfig(new ByteArrayInputStream(originalLDIFBytes))))
    {
      Entry entryBefore, entryAfter;
      DataConfig dataConfig = new DataConfig.Builder().compress(false).encode(false).build();
      ID2Entry id2entry = new ID2Entry(new TreeName("o=test", "id2entry"), dataConfig);
      while ((entryBefore = reader.readEntry(false)) != null) {
        ByteString bytes = id2entry.entryToDatabase(entryBefore, dataConfig);

        entryAfter = id2entry.entryFromDatabase(bytes, DirectoryServer.getDefaultCompressedSchema());

        // check DN and number of attributes
        assertThat(entryBefore.getAllAttributes()).hasSameSizeAs(entryAfter.getAllAttributes());
        assertEquals(entryBefore.getName(), entryAfter.getName());

        // check the object classes were not changed
        for (String ocBefore : entryBefore.getObjectClasses().values()) {
          ObjectClass objectClass = getServerContext().getSchema().getObjectClass(ocBefore);
          String ocAfter = entryAfter.getObjectClasses().get(objectClass);
          assertEquals(ocBefore, ocAfter);
        }

        // check the user attributes were not changed
        for (AttributeType attrType : entryBefore.getUserAttributes().keySet()) {
          List<Attribute> listBefore = entryBefore.getAllAttributes(attrType);
          List<Attribute> listAfter = entryAfter.getAllAttributes(attrType);
          assertThat(listBefore).hasSameSizeAs(listAfter);

          for (Attribute attrBefore : listBefore) {
            boolean found = false;

            for (Attribute attrAfter : listAfter) {
              if (attrAfter.getAttributeDescription().equals(attrBefore.getAttributeDescription())) {
                // Found the corresponding attribute
                assertEquals(attrBefore, attrAfter);
                found = true;
              }
            }

            assertTrue(found);
          }
        }
      }
    }
  }

  /**
   * Tests the entry encoding and decoding process the version 1 encoding.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testEntryToAndFromDatabaseV1() throws Exception {
    ensureServerIsUpAndRunning();

    // Convert the test LDIF string to a byte array
    byte[] originalLDIFBytes = StaticUtils.getBytes(ldifString);

    try (final LDIFReader reader = new LDIFReader(new LDIFImportConfig(new ByteArrayInputStream(originalLDIFBytes))))
    {
      Entry entryBefore, entryAfterV1;
      while ((entryBefore = reader.readEntry(false)) != null) {
        ByteStringBuilder bsb = new ByteStringBuilder();
        encodeV1(entryBefore, bsb);
        entryAfterV1 = Entry.decode(bsb.asReader());

        assertEquals(entryBefore, entryAfterV1);
      }
    }
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
    ensureServerIsUpAndRunning();

    // Convert the test LDIF string to a byte array
    byte[] originalLDIFBytes = StaticUtils.getBytes(ldifString);

    try (final LDIFReader reader = new LDIFReader(new LDIFImportConfig(new ByteArrayInputStream(originalLDIFBytes))))
    {
      Entry entryBefore, entryAfterV2;
      while ((entryBefore = reader.readEntry(false)) != null) {
        ByteStringBuilder bsb = new ByteStringBuilder();
        encodeV2(entryBefore, bsb, config);
        entryAfterV2 = Entry.decode(bsb.asReader());
        if (config.excludeDN())
        {
          entryAfterV2.setDN(entryBefore.getName());
        }
        assertEquals(entryBefore, entryAfterV2);
      }
    }
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
    ensureServerIsUpAndRunning();

    // Convert the test LDIF string to a byte array
    byte[] originalLDIFBytes = StaticUtils.getBytes(ldifString);

    try (final LDIFReader reader = new LDIFReader(new LDIFImportConfig(new ByteArrayInputStream(originalLDIFBytes))))
    {
      Entry entryBefore, entryAfterV3;
      while ((entryBefore = reader.readEntry(false)) != null) {
        ByteStringBuilder bsb = new ByteStringBuilder();
        entryBefore.encode(bsb, config);
        entryAfterV3 = Entry.decode(bsb.asReader());
        if (config.excludeDN())
        {
          entryAfterV3.setDN(entryBefore.getName());
        }
        assertEquals(entryBefore, entryAfterV3);
      }
    }
  }

  @DataProvider
  private Object[][] findDnKeyParentData()
  {
    return new Object[][]
    {
      // dn, expected length of parent
      { "dc=example", 0 },
      { "dc=example,dc=com", 7 },
      { "dc=example,dc=com\\,org", 11 },
    };
  }

  @Test(dataProvider="findDnKeyParentData")
  public void testFindDnKeyParent(String dn, int expectedLength) throws Exception
  {
    ensureServerIsUpAndRunning();
    ByteString dnKey = DnKeyFormat.dnToDNKey(DN.valueOf(dn), 0);
    assertThat(DnKeyFormat.findDNKeyParent(dnKey)).isEqualTo(expectedLength);
  }

  @DataProvider
  private Object[][] testIsChildData()
  {
    return new Object[][]
    {
      {           "dc=example,dc=com\\,org", // parentDn
        "ou=people,dc=example,dc=com\\,org", // childDn
        true },                              // Is childDn a child of parentDn ?

      { "dc=example,dc=com",
                   "dc=com",
        false },

      {  "ou=people,dc=example,dc=com",
        "ou=people1,dc=example,dc=com",
        false },

      {                      "dc=example,dc=com",
        "uid=user.0,ou=people,dc=example,dc=com",
        false },

      {           "dc=example,dc=com",
        "ou=people,dc=elpmaxe,dc=com",
        false },

      { "dc=example,dc=com",
        "dc=example,dc=com",
        false },
    };
  }

  @Test(dataProvider="testIsChildData")
  public void testIsChild(String parentDn, String childDn, boolean expected) {
    assertThat(
      DnKeyFormat.isChild(
          DnKeyFormat.dnToDNKey(DN.valueOf(parentDn), 0),
          DnKeyFormat.dnToDNKey(DN.valueOf(childDn), 0))
      ).isEqualTo(expected);
  }

  private void ensureServerIsUpAndRunning() throws Exception
  {
    TestCaseUtils.startServer();
  }
}
