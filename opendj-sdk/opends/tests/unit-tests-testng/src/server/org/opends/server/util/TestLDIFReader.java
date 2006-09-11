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
package org.opends.server.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.RDN;
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
  // Top object class.
  private ObjectClass OC_TOP;

  // Person object class.
  private ObjectClass OC_PERSON;

  // Object class attribute type.
  private AttributeType AT_OC;

  // Common name attribute type.
  private AttributeType AT_CN;

  // Surname attribute type.
  private AttributeType AT_SN;

  // Description attribute type.
  private AttributeType AT_DESCR;

  // Telephone number attribute type.
  private AttributeType AT_TELN;

  // Temporary file containing an attribute value.
  private File TEMP_FILE = null;

  // Temporary file content.
  private static final String TEMP_FILE_STRING = "hello world";

  // Temporary file LDIF.
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
    // This test suite depends on having the schema available, so we'll
    // start the server.
    TestCaseUtils.startServer();

    // Initialize schema bits.
    OC_TOP = DirectoryServer.getObjectClass("top");
    OC_PERSON = DirectoryServer.getObjectClass("person");

    AT_OC = DirectoryServer.getObjectClassAttributeType();
    AT_CN = DirectoryServer.getAttributeType("cn");
    AT_SN = DirectoryServer.getAttributeType("sn");
    AT_DESCR = DirectoryServer.getAttributeType("description");
    AT_TELN = DirectoryServer.getAttributeType("telephonenumber");

    // Create a temporary file containing an attribute value.
    TEMP_FILE = File.createTempFile("tmp", "txt");
    OutputStream out = null;
    try {
      out = new FileOutputStream(TEMP_FILE);
      out.write(TEMP_FILE_STRING.getBytes("UTF-8"));
    } finally {
      if (out != null) {
        out.close();
      }
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
    LDIFReader reader = createLDIFReader("");

    try {
      Assert.assertEquals(reader.getEntriesIgnored(), 0);
      Assert.assertEquals(reader.getEntriesRead(), 0);
      Assert.assertEquals(reader.getEntriesRejected(), 0);
      Assert.assertEquals(reader.getLastEntryLineNumber(), -1);
    } finally {
      reader.close();
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
    LDIFReader reader = createLDIFReader("");

    try {
      Entry entry = reader.readEntry();

      Assert.assertNull(entry);

      Assert.assertEquals(reader.getEntriesIgnored(), 0);
      Assert.assertEquals(reader.getEntriesRead(), 0);
      Assert.assertEquals(reader.getEntriesRejected(), 0);
      Assert.assertEquals(reader.getLastEntryLineNumber(), -1);
    } finally {
      reader.close();
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
    LDIFReader reader = createLDIFReader("version: 1\n");

    try {
      Entry entry = reader.readEntry();

      Assert.assertNull(entry);

      Assert.assertEquals(reader.getEntriesIgnored(), 0);
      Assert.assertEquals(reader.getEntriesRead(), 0);
      Assert.assertEquals(reader.getEntriesRejected(), 0);
      Assert.assertEquals(reader.getLastEntryLineNumber(), 1);
    } finally {
      reader.close();
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
    LDIFReader reader = createLDIFReader("");
    try {
      ChangeRecordEntry change = reader.readChangeRecord(true);

      Assert.assertNull(change);

      Assert.assertEquals(reader.getEntriesIgnored(), 0);
      Assert.assertEquals(reader.getEntriesRead(), 0);
      Assert.assertEquals(reader.getEntriesRejected(), 0);
      Assert.assertEquals(reader.getLastEntryLineNumber(), -1);
    } finally {
      reader.close();
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
    final String ldifString = "dn: cn=john, dc=foo, dc=com\n"
        + "objectClass: top\n" + "objectClass: person\n" + "cn: john\n"
        + "sn: smith\n";

    LDIFReader reader = createLDIFReader(ldifString);

    try {
      Entry entry = reader.readEntry();
      Assert.assertNotNull(entry);

      Assert.assertEquals(entry.getDN(), DN
          .decode("cn=john, dc=foo, dc=com"));
      Assert.assertTrue(entry.hasObjectClass(OC_TOP));
      Assert.assertTrue(entry.hasObjectClass(OC_PERSON));
      Assert.assertTrue(entry.hasValue(AT_CN, null, new AttributeValue(
          AT_CN, "john")));
      Assert.assertTrue(entry.hasValue(AT_SN, null, new AttributeValue(
          AT_SN, "smith")));

      Assert.assertNull(reader.readEntry());

      Assert.assertEquals(reader.getEntriesIgnored(), 0);
      Assert.assertEquals(reader.getEntriesRead(), 1);
      Assert.assertEquals(reader.getEntriesRejected(), 0);
      Assert.assertEquals(reader.getLastEntryLineNumber(), 1);
    } finally {
      reader.close();
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

    LDIFReader reader = createLDIFReader(ldifString);

    try {
      Entry entry = reader.readEntry();
      Assert.assertNotNull(entry);

      Assert.assertTrue(entry.hasValue(AT_DESCR, null, new AttributeValue(
          AT_DESCR, "once upon a time in the west")));
    } finally {
      reader.close();
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

    LDIFReader reader = createLDIFReader(ldifString);

    try {
      Entry entry = reader.readEntry();
      Assert.assertNotNull(entry);

      Assert.assertTrue(entry.hasValue(AT_DESCR, null, new AttributeValue(
          AT_DESCR, "once upon a time in the west")));
    } finally {
      reader.close();
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
    final String ldifString = "dn: cn=john, dc=foo, dc=com\n"
        + "objectClass: top\n" + "objectClass: person\n" + "cn: john\n"
        + "sn: smith\n" + "\n" + "dn: cn=anne, dc=foo, dc=com\n"
        + "objectClass: top\n" + "objectClass: person\n" + "cn: anne\n"
        + "sn: other\n" + "\n";

    LDIFReader reader = createLDIFReader(ldifString);

    try {
      reader.readEntry();
      Entry entry = reader.readEntry();

      Assert.assertNotNull(entry);

      Assert.assertEquals(entry.getDN(), DN
          .decode("cn=anne, dc=foo, dc=com"));
      Assert.assertTrue(entry.hasObjectClass(OC_TOP));
      Assert.assertTrue(entry.hasObjectClass(OC_PERSON));
      Assert.assertTrue(entry.hasValue(AT_CN, null, new AttributeValue(
          AT_CN, "anne")));
      Assert.assertTrue(entry.hasValue(AT_SN, null, new AttributeValue(
          AT_SN, "other")));

      Assert.assertNull(reader.readEntry());

      Assert.assertEquals(reader.getEntriesIgnored(), 0);
      Assert.assertEquals(reader.getEntriesRead(), 2);
      Assert.assertEquals(reader.getEntriesRejected(), 0);
      Assert.assertEquals(reader.getLastEntryLineNumber(), 7);
    } finally {
      reader.close();
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
    LDIFReader reader = createLDIFReader(VALID_LDIF);

    try {
      ChangeRecordEntry change;
      AddChangeRecordEntry add;
      DeleteChangeRecordEntry delete;
      ModifyChangeRecordEntry modify;
      ModifyDNChangeRecordEntry modifyDN;
      DN dn;
      RDN rdn;
      Iterator<LDAPModification> i;
      Modification mod;
      Attribute attr;
      LinkedHashSet<AttributeValue> values;

      // Change record #1.
      change = reader.readChangeRecord(false);
      Assert.assertTrue(change instanceof AddChangeRecordEntry);
      add = (AddChangeRecordEntry) change;

      dn = DN.decode("cn=Fiona Jensen, ou=Marketing, dc=airius, dc=com");
      Assert.assertEquals(add.getDN(), dn);

      List<Attribute> attrs = new ArrayList<Attribute>();

      values = new LinkedHashSet<AttributeValue>();
      values.add(new AttributeValue(AT_OC, "top"));
      values.add(new AttributeValue(AT_OC, "person"));
      values.add(new AttributeValue(AT_OC, "organizationalPerson"));

      attrs.add(new Attribute(AT_OC, "objectclass", values));
      attrs.add(new Attribute("cn", "Fiona Jensen"));
      attrs.add(new Attribute("sn", "Jensen"));
      attrs.add(new Attribute("uid", "fiona"));
      attrs.add(new Attribute("telephonenumber", "+1 408 555 1212"));
      Assert.assertTrue(add.getAttributes().containsAll(attrs));

      // Change record #2.
      change = reader.readChangeRecord(false);
      Assert.assertTrue(change instanceof DeleteChangeRecordEntry);
      delete = (DeleteChangeRecordEntry) change;

      dn = DN.decode("cn=Robert Jensen, ou=Marketing, dc=airius, dc=com");
      Assert.assertEquals(delete.getDN(), dn);

      // Change record #3.
      change = reader.readChangeRecord(false);
      Assert.assertTrue(change instanceof ModifyDNChangeRecordEntry);
      modifyDN = (ModifyDNChangeRecordEntry) change;

      dn = DN
          .decode("cn=Paul Jensen, ou=Product Development, dc=airius, dc=com");
      Assert.assertEquals(modifyDN.getDN(), dn);

      rdn = RDN.decode("cn=paula jensen");
      Assert.assertEquals(modifyDN.getNewRDN(), rdn);
      Assert.assertNull(modifyDN.getNewSuperiorDN());
      Assert.assertTrue(modifyDN.deleteOldRDN());

      // Change record #4.
      change = reader.readChangeRecord(false);
      Assert.assertTrue(change instanceof ModifyDNChangeRecordEntry);
      modifyDN = (ModifyDNChangeRecordEntry) change;

      dn = DN
          .decode("ou=PD Accountants, ou=Product Development, dc=airius, dc=com");
      Assert.assertEquals(modifyDN.getDN(), dn);

      rdn = RDN.decode("ou=Product Development Accountants");
      Assert.assertEquals(modifyDN.getNewRDN(), rdn);
      dn = DN.decode("ou=Accounting, dc=airius, dc=com");
      Assert.assertEquals(modifyDN.getNewSuperiorDN(), dn);
      Assert.assertFalse(modifyDN.deleteOldRDN());

      // Change record #5.
      change = reader.readChangeRecord(false);
      Assert.assertTrue(change instanceof ModifyChangeRecordEntry);
      modify = (ModifyChangeRecordEntry) change;

      dn = DN
          .decode("cn=Paula Jensen, ou=Product Development, dc=airius, dc=com");
      Assert.assertEquals(modify.getDN(), dn);

      i = modify.getModifications().iterator();

      Assert.assertTrue(i.hasNext());
      mod = i.next().toModification();
      Assert.assertEquals(mod.getModificationType(), ModificationType.ADD);
      attr = new Attribute("postaladdress",
          "123 Anystreet $ Sunnyvale, CA $ 94086");
      Assert.assertEquals(mod.getAttribute(), attr);

      Assert.assertTrue(i.hasNext());
      mod = i.next().toModification();
      Assert.assertEquals(mod.getModificationType(),
          ModificationType.DELETE);
      attr = new Attribute(AT_DESCR);
      Assert.assertEquals(mod.getAttribute(), attr);

      Assert.assertTrue(i.hasNext());
      mod = i.next().toModification();
      Assert.assertEquals(mod.getModificationType(),
          ModificationType.REPLACE);
      values = new LinkedHashSet<AttributeValue>();
      values.add(new AttributeValue(AT_TELN, "+1 408 555 1234"));
      values.add(new AttributeValue(AT_TELN, "+1 408 555 5678"));
      attr = new Attribute(AT_TELN, "telephonenumber", values);
      Assert.assertEquals(mod.getAttribute(), attr);

      Assert.assertTrue(i.hasNext());
      mod = i.next().toModification();
      Assert.assertEquals(mod.getModificationType(),
          ModificationType.DELETE);
      attr = new Attribute("facsimiletelephonenumber", "+1 408 555 9876");
      Assert.assertEquals(mod.getAttribute(), attr);

      Assert.assertFalse(i.hasNext());

      // Change record #6.
      change = reader.readChangeRecord(false);
      Assert.assertTrue(change instanceof ModifyChangeRecordEntry);
      modify = (ModifyChangeRecordEntry) change;

      dn = DN
          .decode("cn=Ingrid Jensen, ou=Product Support, dc=airius, dc=com");
      Assert.assertEquals(modify.getDN(), dn);

      i = modify.getModifications().iterator();

      Assert.assertTrue(i.hasNext());
      mod = i.next().toModification();
      Assert.assertEquals(mod.getModificationType(),
          ModificationType.REPLACE);
      attr = new Attribute(DirectoryServer
          .getAttributeType("postaladdress"));
      Assert.assertEquals(mod.getAttribute(), attr);

      // Change record #7.
      change = reader.readChangeRecord(false);
      Assert.assertTrue(change instanceof ModifyChangeRecordEntry);
      modify = (ModifyChangeRecordEntry) change;

      Assert.assertTrue(modify.getDN().isNullDN());

      i = modify.getModifications().iterator();

      Assert.assertTrue(i.hasNext());
      mod = i.next().toModification();
      Assert.assertEquals(mod.getModificationType(),
          ModificationType.DELETE);
      attr = new Attribute(AT_DESCR);
      Assert.assertEquals(mod.getAttribute(), attr);

      // Change record #8.
      change = reader.readChangeRecord(false);
      Assert.assertTrue(change instanceof ModifyChangeRecordEntry);
      modify = (ModifyChangeRecordEntry) change;

      dn = DN.decode("uid=rogasawara, ou=\u55b6\u696d\u90e8, o=airius");
      Assert.assertEquals(modify.getDN(), dn);

      i = modify.getModifications().iterator();

      Assert.assertTrue(i.hasNext());
      mod = i.next().toModification();
      Assert.assertEquals(mod.getModificationType(),
          ModificationType.DELETE);
      attr = new Attribute(AT_DESCR);
      Assert.assertEquals(mod.getAttribute(), attr);

      Assert.assertFalse(i.hasNext());

      // Check final state.

      Assert.assertNull(reader.readChangeRecord(false));

      Assert.assertEquals(reader.getEntriesIgnored(), 0);
      Assert.assertEquals(reader.getEntriesRead(), 0);
      Assert.assertEquals(reader.getEntriesRejected(), 0);
      Assert.assertEquals(reader.getLastEntryLineNumber(), 72);
    } finally {
      reader.close();
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
    LDIFReader reader = createLDIFReader(VALID_LDIF);

    try {
      reader.readChangeRecord(false);
      reader.readChangeRecord(false);
      reader.readChangeRecord(false);

      reader.readChangeRecord(false);
      reader.rejectLastEntry("Rejected");

      reader.readChangeRecord(false);
      reader.rejectLastEntry("Rejected");

      reader.readChangeRecord(false);
      reader.readChangeRecord(false);
      reader.readChangeRecord(false);

      // Check final state.
      Assert.assertNull(reader.readChangeRecord(false));
      Assert.assertEquals(reader.getEntriesRejected(), 2);
    } finally {
      reader.close();
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
    LDIFReader reader = createLDIFReader(buffer.toString());

    try {
      ChangeRecordEntry change = reader.readChangeRecord(false);
      Assert.assertTrue(change instanceof AddChangeRecordEntry);
      AddChangeRecordEntry add = (AddChangeRecordEntry) change;

      DN dn = DN.decode("cn=john smith, dc=com");
      Assert.assertEquals(add.getDN(), dn);

      Attribute attr = new Attribute("description", TEMP_FILE_STRING);
      Assert.assertTrue(add.getAttributes().contains(attr));

      // Check final state.
      Assert.assertNull(reader.readChangeRecord(false));
    } finally {
      reader.close();
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
    LDIFReader reader = createLDIFReader(ldifString);
    ChangeRecordEntry change = null;

    try {
      change = reader.readChangeRecord(false);
    } finally {
      reader.close();
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
