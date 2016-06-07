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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.util;

import static org.forgerock.opendj.ldap.schema.CoreSchema.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.opends.server.TestCaseUtils;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.Attributes;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.Modification;
import org.opends.server.types.RawModification;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * This class defines a set of tests for the
 * {@link org.opends.server.util.LDIFReader} class.
 */
public final class TestLDIFReader extends UtilTestCase {
  /** Temporary file containing an attribute value. */
  private File TEMP_FILE;

  /** Temporary file content. */
  private static final String TEMP_FILE_STRING = "hello world";
  /** Temporary file LDIF. */
  private static final String TEMP_FILE_LDIF = "dn: cn=john smith, dc=com\n"
      + "changetype: add\n" + "objectClass: top\n"
      + "objectClass: person\n" + "cn: john\n" + "sn: smith\n"
      + "description:< file:///";


  /**
   * String of valid LDIF change records.
   *
   * Take from example 6 in the LDIF RFC + a couple of additions.
   */
  private static final String VALID_LDIF = "version: 1\n"
      + "# Add a new entry\n"
      + "dn: cn=Fiona Jensen, ou=Marketing, dc=airius, dc=com\n"
      + "changetype: add\n"
      + "objectclass: top\n"
      + "objectclass: person\n"
      + "objectclass: organizationalPerson\n"
      + "cn: Fiona Jensen\n"
      + "sn: Jensen\n"
      + "uid: fiona\n"
      + "telephonenumber: +1 408 555 1212\n"
      + "\n"
      + "# Delete an existing entry\n"
      + "dn: cn=Robert Jensen, ou=Marketing, dc=airius, dc=com\n"
      + "changetype: delete\n"
      + "\n"
      + "# Modify an entry's relative distinguished name\n"
      + "dn: cn=Paul Jensen, ou=Product Development, dc=airius, dc=com\n"
      + "changetype: modrdn\n"
      + "newrdn: cn=Paula Jensen\n"
      + "deleteoldrdn: 1\n"
      + "\n"
      + "# Rename an entry and move all of its children to a new location in\n"
      + "# the directory tree (only implemented by LDAPv3 servers).\n"
      + "dn: ou=PD Accountants, ou=Product Development, dc=airius, dc=com\n"
      + "changetype: modrdn\n"
      + "newrdn: ou=Product Development Accountants\n"
      + "deleteoldrdn: 0\n"
      + "newsuperior: ou=Accounting, dc=airius, dc=com\n"
      + "\n"
      + "# Modify an entry: add an additional value to the postaladdress\n"
      + "# attribute, completely delete the description attribute, replace\n"
      + "# the telephonenumber attribute with two values, and delete a specific\n"
      + "# value from the facsimiletelephonenumber attribute\n"
      + "dn: cn=Paula Jensen, ou=Product Development, dc=airius, dc=com\n"
      + "changetype: modify\n"
      + "add: postaladdress\n"
      + "postaladdress: 123 Anystreet $ Sunnyvale, CA $ 94086\n"
      + "-\n"
      + "delete: description\n"
      + "-\n"
      + "replace: telephonenumber\n"
      + "telephonenumber: +1 408 555 1234\n"
      + "telephonenumber: +1 408 555 5678\n"
      + "-\n"
      + "delete: facsimiletelephonenumber\n"
      + "facsimiletelephonenumber: +1 408 555 9876\n"
      + "-\n"
      + "\n"
      + "# Modify an entry: replace the postaladdress attribute with an empty\n"
      + "# set of values (which will cause the attribute to be removed), and\n"
      + "# delete the entire description attribute. Note that the first will\n"
      + "# always succeed, while the second will only succeed if at least\n"
      + "# one value for the description attribute is present.\n"
      + "dn: cn=Ingrid Jensen, ou=Product Support, dc=airius, dc=com\n"
      + "changetype: modify\n"
      + "replace: postaladdress\n"
      + "-\n"
      + "delete: description\n"
      + "-\n"
      + "\n"
      + "# Modify rootDSE.\n"
      + "dn: \n"
      + "changetype: modify\n"
      + "delete: description\n"
      + "-\n"
      + "\n"
      + "# Modify base64 DN.\n"
      + "dn:: dWlkPXJvZ2FzYXdhcmEsb3U95Za25qWt6YOoLG89QWlyaXVz\n"
      + "# dn:: uid=<uid>,ou=<JapaneseOU>,o=Airius\n"
      + "changetype:: bW9kaWZ5\n"
      + "delete: description\n"
      + "-\n"
      + "\n";

  /**
   * Once-only initialization.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @BeforeClass
  public void setUp() throws Exception {
    TestCaseUtils.startServer();

    // Create a temporary file containing an attribute value.
    TEMP_FILE = File.createTempFile("tmp", "txt");
    try (OutputStream out = new FileOutputStream(TEMP_FILE)) {
      out.write(TEMP_FILE_STRING.getBytes("UTF-8"));
    }
  }

  /**
   * Once-only tear-down.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @AfterClass
  public void tearDown() throws Exception {
    if (TEMP_FILE != null) {
      TEMP_FILE.delete();
    }
  }

  /**
   * Check the initial state of an LDIF reader.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testInitialState() throws Exception {
    try (LDIFReader reader = createLDIFReader("")) {
      Assert.assertEquals(reader.getEntriesIgnored(), 0);
      Assert.assertEquals(reader.getEntriesRead(), 0);
      Assert.assertEquals(reader.getEntriesRejected(), 0);
      Assert.assertEquals(reader.getLastEntryLineNumber(), -1);
    }
  }

  /**
   * Attempt to read an entry from an empty LDIF stream.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testReadEntryEmptyStream() throws Exception {
    try (LDIFReader reader = createLDIFReader("")) {
      Entry entry = reader.readEntry();
      Assert.assertNull(entry);

      Assert.assertEquals(reader.getEntriesIgnored(), 0);
      Assert.assertEquals(reader.getEntriesRead(), 0);
      Assert.assertEquals(reader.getEntriesRejected(), 0);
      Assert.assertEquals(reader.getLastEntryLineNumber(), -1);
    }
  }

  /**
   * Attempt to read an entry from an empty LDIF stream containing just
   * the LDIF version.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dependsOnMethods = { "testReadEntryEmptyStream" })
  public void testReadEntryEmptyStreamVersion() throws Exception {
    try (LDIFReader reader = createLDIFReader("version: 1\n")) {
      Entry entry = reader.readEntry();
      Assert.assertNull(entry);

      Assert.assertEquals(reader.getEntriesIgnored(), 0);
      Assert.assertEquals(reader.getEntriesRead(), 0);
      Assert.assertEquals(reader.getEntriesRejected(), 0);
      Assert.assertEquals(reader.getLastEntryLineNumber(), 1);
    }
  }

  /**
   * Attempt to read a change record from an empty LDIF stream.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testChangeRecordEmptyStream() throws Exception {
    try (LDIFReader reader = createLDIFReader("")) {
      ChangeRecordEntry change = reader.readChangeRecord(true);
      Assert.assertNull(change);

      Assert.assertEquals(reader.getEntriesIgnored(), 0);
      Assert.assertEquals(reader.getEntriesRead(), 0);
      Assert.assertEquals(reader.getEntriesRejected(), 0);
      Assert.assertEquals(reader.getLastEntryLineNumber(), -1);
    }
  }

  /**
   * Attempt to read a single entry.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dependsOnMethods = { "testReadEntryEmptyStream" })
  public void testReadEntrySingle() throws Exception {
    // @formatter:off
    final String ldifString =
        "dn: cn=john, dc=foo, dc=com\n"
        + "objectClass: top\n"
        + "objectClass: person\n"
        + "cn: john\n"
        + "sn: smith\n";
    // @formatter:on

    try (LDIFReader reader = createLDIFReader(ldifString)) {
      Entry entry = reader.readEntry();
      Assert.assertNotNull(entry);

      Assert.assertEquals(entry.getName(), DN.valueOf("cn=john, dc=foo, dc=com"));
      Assert.assertTrue(entry.hasObjectClass(getTopObjectClass()));
      Assert.assertTrue(entry.hasObjectClass(getPersonObjectClass()));
      Assert.assertTrue(entry.hasValue(getCNAttributeType(), ByteString.valueOfUtf8("john")));
      Assert.assertTrue(entry.hasValue(getSNAttributeType(), ByteString.valueOfUtf8("smith")));

      Assert.assertNull(reader.readEntry());

      Assert.assertEquals(reader.getEntriesIgnored(), 0);
      Assert.assertEquals(reader.getEntriesRead(), 1);
      Assert.assertEquals(reader.getEntriesRejected(), 0);
      Assert.assertEquals(reader.getLastEntryLineNumber(), 1);
    }
  }

  /**
   * Attempt to read an entry containing a folded line.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dependsOnMethods = { "testReadEntrySingle" })
  public void testReadEntryFoldedLine() throws Exception {
    final String ldifString = "dn: cn=john, dc=foo, dc=com\n"
        + "objectClass: top\n" + "objectClass: person\n" + "cn: john\n"
        + "sn: smith\n" + "description: once upon a time\n"
        + "  in the west\n";

    try (LDIFReader reader = createLDIFReader(ldifString)) {
      Entry entry = reader.readEntry();
      Assert.assertNotNull(entry);
      Assert.assertTrue(entry.hasValue(getDescriptionAttributeType(),
                                       ByteString.valueOfUtf8("once upon a time in the west")));
    }
  }

  /**
   * Attempt to read an entry containing a base64 line.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dependsOnMethods = { "testReadEntrySingle" })
  public void testReadEntryBase64() throws Exception {
    final String ldifString = "dn: cn=john, dc=foo, dc=com\n"
        + "objectClass: top\n" + "objectClass: person\n" + "cn: john\n"
        + "sn: smith\n"
        + "description:: b25jZSB1cG9uIGEgdGltZSBpbiB0aGUgd2VzdA==\n";

    try (LDIFReader reader = createLDIFReader(ldifString)) {
      Entry entry = reader.readEntry();
      Assert.assertNotNull(entry);
      Assert.assertTrue(entry.hasValue(getDescriptionAttributeType(),
                                       ByteString.valueOfUtf8("once upon a time in the west")));
    }
  }

  /**
   * Attempt to read multiple entries.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dependsOnMethods = { "testReadEntrySingle" })
  public void testReadEntryMultiple() throws Exception {
    // @formatter:off
    final String ldifString =
        "dn: cn=john, dc=foo, dc=com\n"
        + "objectClass: top\n"
        + "objectClass: person\n"
        + "cn: john\n"
        + "sn: smith\n"
        + "\n"
        + "dn: cn=anne, dc=foo, dc=com\n"
        + "objectClass: top\n"
        + "objectClass: person\n"
        + "cn: anne\n"
        + "sn: other\n"
        + "\n";
    // @formatter:on

    try (LDIFReader reader = createLDIFReader(ldifString)) {
      reader.readEntry();
      Entry entry = reader.readEntry();

      Assert.assertNotNull(entry);

      Assert.assertEquals(entry.getName(), DN.valueOf("cn=anne, dc=foo, dc=com"));
      Assert.assertTrue(entry.hasObjectClass(getTopObjectClass()));
      Assert.assertTrue(entry.hasObjectClass(getPersonObjectClass()));
      Assert.assertTrue(entry.hasValue(getCNAttributeType(), ByteString.valueOfUtf8("anne")));
      Assert.assertTrue(entry.hasValue(getSNAttributeType(), ByteString.valueOfUtf8("other")));

      Assert.assertNull(reader.readEntry());

      Assert.assertEquals(reader.getEntriesIgnored(), 0);
      Assert.assertEquals(reader.getEntriesRead(), 2);
      Assert.assertEquals(reader.getEntriesRejected(), 0);
      Assert.assertEquals(reader.getLastEntryLineNumber(), 7);
    }
  }

  /**
   * Attempt to read multiple changes.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dependsOnMethods = { "testChangeRecordEmptyStream" })
  public void testReadChangeMultiple() throws Exception {
    try (LDIFReader reader = createLDIFReader(VALID_LDIF)) {
      ChangeRecordEntry change;
      AddChangeRecordEntry add;
      DeleteChangeRecordEntry delete;
      ModifyChangeRecordEntry modify;
      ModifyDNChangeRecordEntry modifyDN;
      DN dn;
      RDN rdn;
      Iterator<RawModification> i;
      Modification mod;
      Attribute attr;

      // Change record #1.
      change = reader.readChangeRecord(false);
      Assert.assertTrue(change instanceof AddChangeRecordEntry);
      add = (AddChangeRecordEntry) change;

      dn = DN.valueOf("cn=Fiona Jensen, ou=Marketing, dc=airius, dc=com");
      Assert.assertEquals(add.getDN(), dn);

      List<Attribute> attrs = new ArrayList<>();
      AttributeBuilder builder = new AttributeBuilder(getObjectClassAttributeType());
      builder.add("top");
      builder.add("person");
      builder.add("organizationalPerson");

      attrs.add(builder.toAttribute());
      attrs.add(Attributes.create("cn", "Fiona Jensen"));
      attrs.add(Attributes.create("sn", "Jensen"));
      attrs.add(Attributes.create("uid", "fiona"));
      attrs.add(Attributes.create("telephonenumber", "+1 408 555 1212"));
      Assert.assertTrue(add.getAttributes().containsAll(attrs));

      // Change record #2.
      change = reader.readChangeRecord(false);
      Assert.assertTrue(change instanceof DeleteChangeRecordEntry);
      delete = (DeleteChangeRecordEntry) change;

      dn = DN.valueOf("cn=Robert Jensen, ou=Marketing, dc=airius, dc=com");
      Assert.assertEquals(delete.getDN(), dn);

      // Change record #3.
      change = reader.readChangeRecord(false);
      Assert.assertTrue(change instanceof ModifyDNChangeRecordEntry);
      modifyDN = (ModifyDNChangeRecordEntry) change;

      dn = DN
          .valueOf("cn=Paul Jensen, ou=Product Development, dc=airius, dc=com");
      Assert.assertEquals(modifyDN.getDN(), dn);

      rdn = RDN.valueOf("cn=paula jensen");
      Assert.assertEquals(modifyDN.getNewRDN(), rdn);
      Assert.assertNull(modifyDN.getNewSuperiorDN());
      Assert.assertTrue(modifyDN.deleteOldRDN());

      // Change record #4.
      change = reader.readChangeRecord(false);
      Assert.assertTrue(change instanceof ModifyDNChangeRecordEntry);
      modifyDN = (ModifyDNChangeRecordEntry) change;

      dn = DN
          .valueOf("ou=PD Accountants, ou=Product Development, dc=airius, dc=com");
      Assert.assertEquals(modifyDN.getDN(), dn);

      rdn = RDN.valueOf("ou=Product Development Accountants");
      Assert.assertEquals(modifyDN.getNewRDN(), rdn);
      dn = DN.valueOf("ou=Accounting, dc=airius, dc=com");
      Assert.assertEquals(modifyDN.getNewSuperiorDN(), dn);
      Assert.assertFalse(modifyDN.deleteOldRDN());

      // Change record #5.
      change = reader.readChangeRecord(false);
      Assert.assertTrue(change instanceof ModifyChangeRecordEntry);
      modify = (ModifyChangeRecordEntry) change;

      dn = DN
          .valueOf("cn=Paula Jensen, ou=Product Development, dc=airius, dc=com");
      Assert.assertEquals(modify.getDN(), dn);

      i = modify.getModifications().iterator();

      Assert.assertTrue(i.hasNext());
      mod = i.next().toModification();
      Assert.assertEquals(mod.getModificationType(), ModificationType.ADD);
      attr = Attributes.create("postaladdress",
          "123 Anystreet $ Sunnyvale, CA $ 94086");
      Assert.assertEquals(mod.getAttribute(), attr);

      Assert.assertTrue(i.hasNext());
      mod = i.next().toModification();
      Assert.assertEquals(mod.getModificationType(),
          ModificationType.DELETE);
      attr = Attributes.empty(getDescriptionAttributeType());
      Assert.assertEquals(mod.getAttribute(), attr);

      Assert.assertTrue(i.hasNext());
      mod = i.next().toModification();
      Assert.assertEquals(mod.getModificationType(),
          ModificationType.REPLACE);
      builder = new AttributeBuilder(getTelephoneNumberAttributeType());
      builder.add("+1 408 555 1234");
      builder.add("+1 408 555 5678");
      Assert.assertEquals(mod.getAttribute(), builder.toAttribute());

      Assert.assertTrue(i.hasNext());
      mod = i.next().toModification();
      Assert.assertEquals(mod.getModificationType(),
          ModificationType.DELETE);
      attr = Attributes.create("facsimiletelephonenumber", "+1 408 555 9876");
      Assert.assertEquals(mod.getAttribute(), attr);

      Assert.assertFalse(i.hasNext());

      // Change record #6.
      change = reader.readChangeRecord(false);
      Assert.assertTrue(change instanceof ModifyChangeRecordEntry);
      modify = (ModifyChangeRecordEntry) change;

      dn = DN.valueOf("cn=Ingrid Jensen, ou=Product Support, dc=airius, dc=com");
      Assert.assertEquals(modify.getDN(), dn);

      i = modify.getModifications().iterator();

      Assert.assertTrue(i.hasNext());
      mod = i.next().toModification();
      Assert.assertEquals(mod.getModificationType(), ModificationType.REPLACE);
      attr = Attributes.empty(CoreSchema.getPostalAddressAttributeType());
      Assert.assertEquals(mod.getAttribute(), attr);

      // Change record #7.
      change = reader.readChangeRecord(false);
      Assert.assertTrue(change instanceof ModifyChangeRecordEntry);
      modify = (ModifyChangeRecordEntry) change;

      Assert.assertTrue(modify.getDN().isRootDN());

      i = modify.getModifications().iterator();

      Assert.assertTrue(i.hasNext());
      mod = i.next().toModification();
      Assert.assertEquals(mod.getModificationType(),
          ModificationType.DELETE);
      attr = Attributes.empty(getDescriptionAttributeType());
      Assert.assertEquals(mod.getAttribute(), attr);

      // Change record #8.
      change = reader.readChangeRecord(false);
      Assert.assertTrue(change instanceof ModifyChangeRecordEntry);
      modify = (ModifyChangeRecordEntry) change;

      dn = DN.valueOf("uid=rogasawara, ou=\u55b6\u696d\u90e8, o=airius");
      Assert.assertEquals(modify.getDN(), dn);

      i = modify.getModifications().iterator();

      Assert.assertTrue(i.hasNext());
      mod = i.next().toModification();
      Assert.assertEquals(mod.getModificationType(),
          ModificationType.DELETE);
      attr = Attributes.empty(getDescriptionAttributeType());
      Assert.assertEquals(mod.getAttribute(), attr);

      Assert.assertFalse(i.hasNext());

      // Check final state.

      Assert.assertNull(reader.readChangeRecord(false));

      Assert.assertEquals(reader.getEntriesIgnored(), 0);
      Assert.assertEquals(reader.getEntriesRead(), 0);
      Assert.assertEquals(reader.getEntriesRejected(), 0);
      Assert.assertEquals(reader.getLastEntryLineNumber(), 72);
    }
  }

  /**
   * Attempt to read multiple changes and rejects one.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dependsOnMethods = { "testReadChangeMultiple" })
  public void testReadChangeMultipleAndReject() throws Exception {
    try (LDIFReader reader = createLDIFReader(VALID_LDIF)) {
      reader.readChangeRecord(false);
      reader.readChangeRecord(false);
      reader.readChangeRecord(false);

      reader.readChangeRecord(false);
      reader.rejectLastEntry(LocalizableMessage.raw("Rejected"));

      reader.readChangeRecord(false);
      reader.rejectLastEntry(LocalizableMessage.raw("Rejected"));

      reader.readChangeRecord(false);
      reader.readChangeRecord(false);
      reader.readChangeRecord(false);

      // Check final state.
      Assert.assertNull(reader.readChangeRecord(false));
      Assert.assertEquals(reader.getEntriesRejected(), 2);
    }
  }

  /**
   * Attempt to read a change containing a file-based attribute.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dependsOnMethods = { "testReadChangeMultiple" })
  public void testReadChangeWithFileBaseAttribute() throws Exception {
    StringBuilder buffer = new StringBuilder(TEMP_FILE_LDIF);
    buffer.append(TEMP_FILE.getCanonicalPath());
    buffer.append("\n");

    try (LDIFReader reader = createLDIFReader(buffer.toString())) {
      ChangeRecordEntry change = reader.readChangeRecord(false);
      Assert.assertTrue(change instanceof AddChangeRecordEntry);
      AddChangeRecordEntry add = (AddChangeRecordEntry) change;

      DN dn = DN.valueOf("cn=john smith, dc=com");
      Assert.assertEquals(add.getDN(), dn);

      Attribute attr = Attributes.create("description", TEMP_FILE_STRING);
      Assert.assertTrue(add.getAttributes().contains(attr));

      // Check final state.
      Assert.assertNull(reader.readChangeRecord(false));
    }
  }

  /**
   * LDIF change reader - invalid data provider.
   *
   * @return Returns an array of invalid LDIF change records.
   */
  @DataProvider(name = "invalidLDIFChangeRecords")
  public Object[][] createInvalidLDIFChangeRecords() {
    return new Object[][] {
        {
          "changetype: add\n" +
          "objectClass: top\n" +
          "objectClass: person\n" +
          "cn: john\n" +
          "sn: smith\n"
        },
        {
          "dn: cn=john smith, dc=com\n" +
          "dn: cn=john smith, dc=com\n" +
          "changetype: add\n" +
          "objectClass: top\n" +
          "objectClass: person\n" +
          "cn: john\n" +
          "sn: smith\n"
        },
        {
          ": cn=john smith, dc=com\n" +
          "changetype: add\n" +
          "objectClass: top\n" +
          "objectClass: person\n" +
          "cn: john\n" +
          "sn: smith\n"
        },
        {
          "x: cn=john smith, dc=com\n" +
          "changetype: add\n" +
          "objectClass: top\n" +
          "objectClass: person\n" +
          "cn: john\n" +
          "sn: smith\n"
        },
        {
          "dn: foo\n" +
          "changetype: add\n" +
          "objectClass: top\n" +
          "objectClass: person\n" +
          "cn: john\n" +
          "sn: smith\n"
        },
        {
          "dn: cn,=john smith, dc=com\n" +
          "changetype: add\n" +
          "objectClass: top\n" +
          "objectClass: person\n" +
          "cn: john\n" +
          "sn: smith\n"
        },
        {
          "dn: cn=john smith, dc=com\n" +
          "chaxxngetype: add\n" +
          "objectClass: top\n" +
          "objectClass: person\n" +
          "cn: john\n" +
          "sn: smith\n"
        },
        {
          "dn: cn=john smith, dc=com\n" +
          "changetype: foo\n" +
          "objectClass: top\n" +
          "objectClass: person\n" +
          "cn: john\n" +
          "sn: smith\n"
        },
        {
          "dn: cn=john smith, dc=com\n" +
          "changetype:: add\n" +
          "objectClass: top\n" +
          "objectClass: person\n" +
          "cn: john\n" +
          "sn: smith\n"
        },
        {
          "dn: cn=john smith, dc=com\n" +
          ": add\n" +
          "objectClass: top\n" +
          "objectClass: person\n" +
          "cn: john\n" +
          "sn: smith\n"
        },
        {
          "dn: cn=john smith, dc=com\n" +
          "changetype:\n" +
          "objectClass: top\n" +
          "objectClass: person\n" +
          "cn: john\n" +
          "sn: smith\n"
        },
        {
          "dn: cn=john smith, dc=com\n" +
          "\n" +
          "objectClass: top\n" +
          "objectClass: person\n" +
          "cn: john\n" +
          "sn: smith\n"
        },
        {
          "dn: cn=john smith, dc=com\n" +
          "changetype: add\n" +
          "xxxx\n" +
          "objectClass: person\n" +
          "cn: john\n" +
          "sn: smith\n"
        },
        {
          "dn: cn=john smith, dc=com\n" +
          "changetype: add\n" +
          "objectClass: top\n" +
          "objectClass: person\n" +
          "cn: john\n" +
          "cn: john\n" +
          "sn: smith\n"
        },
        {
          "dn: cn=john smith, dc=com\n" +
          "changetype: add\n" +
          "objectClass: top\n" +
          "objectClass: person\n" +
          "cn: john\n" +
          "sn: smith\n" +
          "cn: john\n"
        },
        {
          "dn: cn=john smith, dc=com\n" +
          "changetype: add\n" +
          ": top\n" +
          "objectClass: person\n" +
          "cn: john\n" +
          "objectClass: person\n" +
          "sn: smith\n"
        },
        {
          "dn: cn=john smith, dc=com\n" +
          "changetype: add\n" +
          "objectclass: top\n" +
          "objectClass: person\n" +
          "cn: john\n" +
          "sn: smith\n" +
          "description:: YnJva2VuIGJhc2U2NA*=="
        },
        {
          "dn:: YnJva2VuIGJhc2U2NA*==" +
          "changetype: add\n" +
          "objectclass: top\n" +
          "objectClass: person\n" +
          "cn: john\n" +
          "sn: smith\n"
        },
        {
          "dn:: Y249YnJva2VuIGJhc2U2NCBkbix4" +
          "changetype: add\n" +
          "objectclass: top\n" +
          "objectClass: person\n" +
          "cn: john\n" +
          "sn: smith\n"
        },
        {
          "dn: cn=john smith, dc=com\n" +
          "changetype: add\n" +
          "objectclass: top\n" +
          "objectClass: person\n" +
          "cn: john\n" +
          "sn: smith\n" +
          "description:< brok@n:///bad/url"
        },
        {
          "dn: cn=john smith, dc=com\n" +
          "changetype: add\n" +
          "objectclass: top\n" +
          "objectClass: person\n" +
          "cn: john\n" +
          "sn: smith\n" +
          "description:< file:///bad/path/name"
        },
        {
          "dn: cn=john smith, dc=com\n" +
          "changetype: delete\n" +
          "objectclass: top\n" +
          "objectClass: person\n" +
          "cn: john\n" +
          "sn: smith\n" +
          "description:< file:///bad/path/name"
        },
        {
          "dn: cn=john smith, dc=com\n" +
          "changetype: modrdn\n"
        },
        {
          "dn: cn=john smith, dc=com\n" +
          "changetype: modrdn\n" +
          "newrdn: x\n"
        },
        {
          "dn: cn=john smith, dc=com\n" +
          "changetype: modrdn\n" +
          "newrdn: cn=foo\n"
        },
        {
          "dn: cn=john smith, dc=com\n" +
          "changetype: modrdn\n" +
          "newrdn: cn=foo\n" +
          "deleteoldxx: xxx\n"
        },
        {
          "dn: cn=john smith, dc=com\n" +
          "changetype: modrdn\n" +
          "newrdn: cn=foo\n" +
          "deleteoldrdn: xxx\n"
        },
        {
          "dn: cn=john smith, dc=com\n" +
          "changetype: modrdn\n" +
          "newrdn: cn=foo\n" +
          "deleteoldrdn: 0\n" +
          "newsu: xxxx\n"
        },
        {
          "dn: cn=john smith, dc=com\n" +
          "changetype: modrdn\n" +
          "newrdn: cn=foo\n" +
          "deleteoldrdn: 0\n" +
          "newsuperior: xxxx\n"
        },
    };
  }

  /**
   * Tests the read change record method against invalid LDIF records.
   *
   * @param ldifString
   *          The invalid LDIF change record.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "invalidLDIFChangeRecords",
      expectedExceptions = { LDIFException.class })
  public void testReadChangeInvalidData(String ldifString) throws Exception {
    final ChangeRecordEntry change;
    try (LDIFReader reader = createLDIFReader(ldifString)) {
      change = reader.readChangeRecord(false);
    }

    Assert.fail("Expected exception but got result: "
        + change.getChangeOperationType() + " - " + change.getDN());
  }

  /**
   * Create an LDIF reader from a string of LDIF.
   *
   * @param ldifString
   *          The string of LDIF. *
   * @return Returns the LDIF reader.
   * @throws Exception
   *           If an error occurred.
   */
  private LDIFReader createLDIFReader(String ldifString) throws Exception {
    byte[] bytes = StaticUtils.getBytes(ldifString);

    LDIFReader reader = new LDIFReader(new LDIFImportConfig(
        new ByteArrayInputStream(bytes)));

    return reader;
  }
}
