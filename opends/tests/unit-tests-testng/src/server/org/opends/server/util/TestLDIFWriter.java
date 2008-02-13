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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.util;

import static org.opends.server.util.StaticUtils.*;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;

import org.opends.messages.Message;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.Modification;
import org.opends.server.types.RawModification;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * This class defines a set of tests for the
 * {@link org.opends.server.util.LDIFWriter} class.
 */
public final class TestLDIFWriter extends UtilTestCase {

  // Data used in writeModifyEntry tests.
  private Object[][] MODIFY_ENTRY_DATA_LDIF;

  // Data used in writeModifyDNEntry tests.
  private Object[][] MODIFY_DN_ENTRY_DATA_LDIF;

  /**
   * Tests will be performed against a byte array output stream.
   */
  private static final class Writer {
    // The underlying output stream.
    private final ByteArrayOutputStream stream;

    // The underlying LDIF config.
    private final LDIFExportConfig config;

    // The LDIF writer.
    private final LDIFWriter writer;

    /**
     * Create a new string writer.
     */
    public Writer() {
      this.stream = new ByteArrayOutputStream();
      this.config = new LDIFExportConfig(stream);
      try {
        this.writer = new LDIFWriter(config);
      } catch (IOException e) {
        // Should not happen.
        throw new RuntimeException(e);
      }
    }

    /**
     * Get the LDIF writer.
     *
     * @return Returns the LDIF writer.
     */
    public LDIFWriter getLDIFWriter() {
      return writer;
    }

    /**
     * Close the writer and get a string reader for the LDIF content.
     *
     * @return Returns the string contents of the writer.
     * @throws Exception
     *           If an error occurred closing the writer.
     */
    public BufferedReader getLDIFBufferedReader() throws Exception {
      writer.close();
      String ldif = stream.toString("UTF-8");
      StringReader reader = new StringReader(ldif);
      return new BufferedReader(reader);
    }

    /**
     * Close the writer and get an LDIF reader for the LDIF content.
     *
     * @return Returns an LDIF Reader.
     * @throws Exception
     *           If an error occurred closing the writer.
     */
    public LDIFReader getLDIFReader() throws Exception {
      writer.close();

      ByteArrayInputStream istream = new ByteArrayInputStream(stream.toByteArray());
      LDIFImportConfig config = new LDIFImportConfig(istream);
      return new LDIFReader(config);
    }
  }

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

    String[] modifyEntryDataLDIF = {
        "dn: cn=Paula Jensen,ou=Product Development,dc=airius,dc=com\n" +
        "changetype: modify\n" +
        "add: postaladdress\n" +
        "postaladdress: 123 Anystreet $ Sunnyvale, CA $ 94086\n" +
        "-\n" +
        "delete: description\n" +
        "-\n" +
        "replace: telephonenumber\n" +
        "telephonenumber: +1 408 555 1234\n" +
        "telephonenumber: +1 408 555 5678\n" +
        "-\n" +
        "delete: facsimiletelephonenumber\n" +
        "facsimiletelephonenumber: +1 408 555 9876\n" +
        "\n",
        "dn: cn=Ingrid Jensen,ou=Product Support,dc=airius,dc=com\n" +
        "changetype: modify\n" +
        "replace: postaladdress\n" +
        "-\n" +
        "delete: description\n" +
        "\n",
        "dn: \n" +
        "changetype: modify\n" +
        "delete: description\n" +
        "\n",
        "dn:: dWlkPXJvZ2FzYXdhcmEsb3U95Za25qWt6YOoLG89QWlyaXVz\n" +
        "changetype: modify\n" +
        "add: description\n" +
        "description:: dWlkPXJvZ2FzYXdhcmEsb3U95Za25qWt6YOoLG89QWlyaXVz" +
        "\n"
    };
    List<Object[]> changes = createChangeRecords(
        ModifyChangeRecordEntry.class, modifyEntryDataLDIF);
    MODIFY_ENTRY_DATA_LDIF = changes.toArray(new Object[0][]);

    String[] modifyDNEntryDataLDIF = {
        "dn: cn=Paula Jensen,ou=Product Development,dc=airius,dc=com\n" +
        "changetype: modrdn\n" +
        "newrdn: cn=Paul Jensen\n" +
        "deleteoldrdn: 1\n",
        "dn: cn=Ingrid Jensen,ou=Product Support,dc=airius,dc=com\n" +
        "changetype: moddn\n" +
        "newrdn: cn=Ingrid Jensen\n" +
        "deleteoldrdn: 0\n" +
        "newsuperior: ou=Product Development,dc=airius,dc=com\n"
    };
    changes = createChangeRecords(ModifyDNChangeRecordEntry.class,
        modifyDNEntryDataLDIF);
    MODIFY_DN_ENTRY_DATA_LDIF = changes.toArray(new Object[0][]);
  }

  /**
   * Check that creating a writer and closing it immediately does not
   * write anything.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void TestEmptyWriter() throws Exception {
    Writer writer = new Writer();

    Assert.assertNull(writer.getLDIFBufferedReader().readLine());
  }

  /**
   * LDIF writer - example comment strings.
   *
   * @return Returns an array of comment strings and their expected LDIF
   *         form.
   */
  @DataProvider(name = "writeCommentDataProvider")
  public Object[][] createTestWriteCommentData() {
    return new Object[][] {
        { "", 40, new String[] { "# " } },
        {
            "one two three four five six seven "
                + "eight nine ten eleven twelve thirteen "
                + "fourteen fifteen sixteen seventeen "
                + "eighteen nineteen",
            40,
            new String[] { "# one two three four five six seven",
                "# eight nine ten eleven twelve thirteen",
                "# fourteen fifteen sixteen seventeen",
                "# eighteen nineteen" } },
        {
            "one two three four five six seven "
                + "eight nine ten\neleven twelve thirteen "
                + "fourteen fifteen\r\nsixteen seventeen "
                + "eighteen nineteen",
            40,
            new String[] { "# one two three four five six seven",
                "# eight nine ten", "# eleven twelve thirteen fourteen",
                "# fifteen", "# sixteen seventeen eighteen nineteen" } },
        {
            "one two three four five six seven "
                + "eight nine ten eleven twelve thirteen "
                + "fourteen fifteen sixteen seventeen "
                + "eighteen nineteen",
            -1,
            new String[] { "# one two three four five "
                + "six seven eight nine ten eleven "
                + "twelve thirteen fourteen fifteen "
                + "sixteen seventeen eighteen nineteen" } },
        {
            "onetwothreefourfivesixseven"
                + "eightnineteneleventwelvethirteen"
                + "fourteenfifteensixteenseventeen"
                + "eighteennineteen",
            40,
            new String[] { "# onetwothreefourfivesixseveneightninete",
                           "# neleventwelvethirteenfourteenfifteensi",
                           "# xteenseventeeneighteennineteen" } }, };
  }

  /**
   * Test the {@link LDIFWriter#writeComment(Message, int)} method.
   *
   * @param comment
   *          The input comment string.
   * @param wrapColumn
   *          The wrap column.
   * @param expectedLDIF
   *          An array of expected lines.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "writeCommentDataProvider")
  public void TestWriteComment(String comment, int wrapColumn,
      String[] expectedLDIF) throws Exception {
    Writer writer = new Writer();

    LDIFWriter ldifWriter = writer.getLDIFWriter();
    ldifWriter.writeComment(Message.raw(comment), wrapColumn);

    checkLDIFOutput(writer, expectedLDIF);
  }

  /**
   * LDIF writer - sample entry provider.
   *
   * @return Returns an array of LDAP entry objects.
   * @throws Exception If an error occurred whilst constructing the test entries.
   */
  @DataProvider(name = "entryDataProvider")
  public Object[][] createTestEntryData() throws Exception {
    String[][] input = {
        {
          "cn=john smith, dc=com",
          "objectclass", "top",
          "objectclass", "person",
          "cn", "john smith",
          "sn", "smith",
          "description", "description of john"
        },
        {
          "",
          "objectclass", "top",
          "description", "root DSE"
        },
    };

    List<Entry[]> entries = new LinkedList<Entry[]>();

    for (String[] s : input) {
      DN dn = DN.decode(s[0]);
      Entry entry = new Entry(dn, null, null, null);

      for (int i = 1; i < s.length; i+=2) {
        String atype = toLowerCase(s[i]);
        String avalue = toLowerCase(s[i+1]);

        if (atype.equals("objectclass")) {
          entry.addObjectClass(DirectoryServer.getObjectClass(avalue));
        } else {
          Attribute attr = new Attribute(atype, avalue);

          // Assume that there will be no duplicates.
          entry.addAttribute(attr, null);
        }
      }

      entries.add(new Entry[]{ entry });
    }

    return entries.toArray(new Object[0][]);
  }

  /**
   * Test the {@link LDIFWriter#writeEntry(Entry)} method.
   *
   * @param entry
   *          The entry to ouput.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "entryDataProvider")
  public void TestWriteEntry(Entry entry)
      throws Exception {
    // FIXME: This test need more work. It should really check that the
    // LDIF output is correct, rather than re-parsing it, because the
    // parser could be tolerant to malformed LDIF output.
    Writer writer = new Writer();

    LDIFWriter ldifWriter = writer.getLDIFWriter();
    ldifWriter.writeEntry(entry);

    LDIFReader reader = writer.getLDIFReader();
    Entry readEntry = reader.readEntry();
    reader.close();

    Assert.assertEquals(readEntry.getDN(), entry.getDN());
  }

  /**
   * Test the {@link LDIFWriter#writeAddChangeRecord(Entry)} method.
   *
   * @param entry
   *          The entry to ouput.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "entryDataProvider")
  public void TestWriteAddEntry(Entry entry)
      throws Exception {
    // FIXME: This test need more work. It should really check that the
    // LDIF output is correct, rather than re-parsing it, because the
    // parser could be tolerant to malformed LDIF output.
    Writer writer = new Writer();

    LDIFWriter ldifWriter = writer.getLDIFWriter();
    ldifWriter.writeAddChangeRecord(entry);

    LDIFReader reader = writer.getLDIFReader();
    ChangeRecordEntry add = reader.readChangeRecord(false);
    reader.close();

    Assert.assertTrue(add instanceof AddChangeRecordEntry);
    Assert.assertEquals(add.getDN(), entry.getDN());
  }

  /**
   * LDIF writer - sample modification provider.
   *
   * @return Returns an array of LDAP modification objects.
   * @throws Exception If an error occurred whilst constructing the test entries.
   */
  @DataProvider(name = "writeModifyDataProvider")
  public Object[][] createTestWriteModifyData() throws Exception {
    return MODIFY_ENTRY_DATA_LDIF;
  }

  /**
   * Test the {@link LDIFWriter#writeModifyChangeRecord(DN, List)}
   * method.
   *
   * @param change
   *          The modification change record.
   * @param expectedLDIF
   *          An array of expected lines.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "writeModifyDataProvider")
  public void TestWriteModifyChangeRecord(ModifyChangeRecordEntry change,
      String[] expectedLDIF) throws Exception {
    Writer writer = new Writer();

    LDIFWriter ldifWriter = writer.getLDIFWriter();

    List<Modification> mods = new LinkedList<Modification>();
    for (RawModification lmod : change.getModifications()) {
      mods.add(lmod.toModification());
    }
    ldifWriter.writeModifyChangeRecord(change.getDN(), mods);

    checkLDIFOutput(writer, expectedLDIF);
  }

  /**
   * Test the {@link LDIFWriter#writeDeleteChangeRecord(Entry, boolean)} method.
   *
   * @param entry
   *          The entry to ouput.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "entryDataProvider")
  public void TestWriteDeleteEntry(Entry entry)
      throws Exception {
    Writer writer = new Writer();

    LDIFWriter ldifWriter = writer.getLDIFWriter();
    ldifWriter.writeDeleteChangeRecord(entry, false);

    String[] expectedLDIF = new String[] {
      "dn: " + entry.getDN(),
      "changetype: delete"
    };

    checkLDIFOutput(writer, expectedLDIF);
  }

  /**
   * LDIF writer - sample modification DN provider.
   *
   * @return Returns an array of LDAP modification DN objects.
   * @throws Exception If an error occurred whilst constructing the test entries.
   */
  @DataProvider(name = "writeModifyDNDataProvider")
  public Object[][] createTestWriteModifyDNData() throws Exception {
    return MODIFY_DN_ENTRY_DATA_LDIF;
  }

  /**
   * Test the {@link LDIFWriter#writeModifyChangeRecord(DN, List)}
   * method.
   *
   * @param change
   *          The modification change record.
   * @param expectedLDIF
   *          An array of expected lines.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "writeModifyDNDataProvider")
  public void TestWriteModifyDNChangeRecord(
      ModifyDNChangeRecordEntry change, String[] expectedLDIF)
      throws Exception {
    Writer writer = new Writer();

    LDIFWriter ldifWriter = writer.getLDIFWriter();
    ldifWriter.writeModifyDNChangeRecord(change.getDN(),
        change.getNewRDN(), change.deleteOldRDN(), change
            .getNewSuperiorDN());

    checkLDIFOutput(writer, expectedLDIF);
  }

  /**
   * Close the LDIF writer and read its content and check it against the
   * expected output.
   *
   * @param writer
   *          The LDIF writer.
   * @param expectedLDIF
   *          The expected LDIF output.
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  private void checkLDIFOutput(Writer writer, String[] expectedLDIF)
      throws Exception {
    BufferedReader reader = writer.getLDIFBufferedReader();

    StringBuilder expected = new StringBuilder();
    StringBuilder actual = new StringBuilder();

    boolean failed = false;

    for (String expectedLine : expectedLDIF) {
      String actualLine = reader.readLine();

      if (!failed && !actualLine.equals(expectedLine)) {
        failed = true;
      }

      expected.append("    ");
      expected.append(expectedLine);
      expected.append("\n");

      actual.append("    ");
      actual.append(actualLine);
      actual.append("\n");
    }

    String actualLine = reader.readLine();
    while (actualLine != null) {
      if (actualLine.trim().length() != 0) {
        failed = true;
      }

      actual.append("    ");
      actual.append(actualLine);
      actual.append("\n");
      actualLine = reader.readLine();
    }

    if (failed) {
      Assert.fail("expected:\n" + expected.toString() + "\nbut was:\n"
          + actual.toString());
    }
  }

  /**
   * Generate change records of the requested type from the input LDIF
   * strings.
   *
   * @param inputLDIF
   *          The input LDIF change records.
   * @return The data provider object array.
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  private <T extends ChangeRecordEntry> List<Object[]> createChangeRecords(
      Class<T> theClass, String[] inputLDIF) throws Exception {
    List<Object[]> changes = new LinkedList<Object[]>();
    for (String ldifString : inputLDIF) {
      byte[] bytes = StaticUtils.getBytes(ldifString);

      LDIFReader reader = new LDIFReader(new LDIFImportConfig(
          new ByteArrayInputStream(bytes)));
      ChangeRecordEntry change = reader.readChangeRecord(false);

      Assert.assertNotNull(change);
      Assert.assertTrue(theClass.isInstance(change));

      String[] lines = ldifString.split("\\n");
      Object[] objs = new Object[] { change, lines };
      changes.add(objs);
    }

    return changes;
  }
}
