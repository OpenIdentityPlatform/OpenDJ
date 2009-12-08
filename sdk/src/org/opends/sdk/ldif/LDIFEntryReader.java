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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk.ldif;



import static com.sun.opends.sdk.messages.Messages.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.opends.sdk.*;
import org.opends.sdk.schema.Schema;

import com.sun.opends.sdk.util.Validator;



/**
 * An LDIF entry reader reads attribute value records (entries) using
 * the LDAP Data Interchange Format (LDIF) from a user defined source.
 *
 * @see <a href="http://tools.ietf.org/html/rfc2849">RFC 2849 - The LDAP
 *      Data Interchange Format (LDIF) - Technical Specification </a>
 */
public final class LDIFEntryReader extends AbstractLDIFReader implements
    EntryReader
{
  /**
   * Parses the provided array of LDIF lines as a single LDIF entry.
   *
   * @param ldifLines
   *          The lines of LDIF to be parsed.
   * @return The parsed LDIF entry.
   * @throws LocalizedIllegalArgumentException
   *           If {@code ldifLines} did not contain an LDIF entry, if it
   *           contained multiple entries, if contained malformed LDIF,
   *           or if the entry could not be decoded using the default
   *           schema.
   * @throws NullPointerException
   *           If {@code ldifLines} was {@code null}.
   */
  public static Entry valueOfLDIFEntry(String... ldifLines)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    LDIFEntryReader reader = new LDIFEntryReader(ldifLines);
    try
    {
      Entry entry = reader.readEntry();

      if (entry == null)
      {
        // No change record found.
        LocalizableMessage message = WARN_READ_LDIF_RECORD_NO_CHANGE_RECORD_FOUND
            .get();
        throw new LocalizedIllegalArgumentException(message);
      }

      if (reader.readEntry() != null)
      {
        // Multiple change records found.
        LocalizableMessage message = WARN_READ_LDIF_RECORD_MULTIPLE_CHANGE_RECORDS_FOUND
            .get();
        throw new LocalizedIllegalArgumentException(message);
      }

      return entry;
    }
    catch (DecodeException e)
    {
      // Badly formed LDIF.
      throw new LocalizedIllegalArgumentException(e.getMessageObject());
    }
    catch (IOException e)
    {
      // This should never happen for a String based reader.
      LocalizableMessage message = WARN_READ_LDIF_RECORD_UNEXPECTED_IO_ERROR.get(e
          .getMessage());
      throw new LocalizedIllegalArgumentException(message);
    }
  }



  /**
   * Creates a new LDIF entry reader whose source is the provided input
   * stream.
   *
   * @param in
   *          The input stream to use.
   * @throws NullPointerException
   *           If {@code in} was {@code null}.
   */
  public LDIFEntryReader(InputStream in) throws NullPointerException
  {
    super(in);
  }



  /**
   * Creates a new LDIF entry reader which will read lines of LDIF from
   * the provided list of LDIF lines.
   *
   * @param ldifLines
   *          The lines of LDIF to be read.
   * @throws NullPointerException
   *           If {@code ldifLines} was {@code null}.
   */
  public LDIFEntryReader(List<String> ldifLines)
      throws NullPointerException
  {
    super(ldifLines);
  }



  /**
   * Creates a new LDIF entry reader which will read lines of LDIF from
   * the provided array of LDIF lines.
   *
   * @param ldifLines
   *          The lines of LDIF to be read.
   * @throws NullPointerException
   *           If {@code ldifLines} was {@code null}.
   */
  public LDIFEntryReader(String... ldifLines)
      throws NullPointerException
  {
    super(Arrays.asList(ldifLines));
  }



  /**
   * {@inheritDoc}
   */
  public void close() throws IOException
  {
    close0();
  }



  /**
   * {@inheritDoc}
   */
  public Entry readEntry() throws DecodeException, IOException
  {
    // Continue until an unfiltered entry is obtained.
    while (true)
    {
      LDIFRecord record = null;

      // Read the set of lines that make up the next entry.
      record = readLDIFRecord();
      if (record == null)
      {
        return null;
      }

      // Read the DN of the entry and see if it is one that should be
      // included in the import.
      DN entryDN;
      try
      {
        entryDN = readLDIFRecordDN(record);
        if (entryDN == null)
        {
          // Skip version record.
          continue;
        }
      }
      catch (final DecodeException e)
      {
        rejectLDIFRecord(record, e.getMessageObject());
        continue;
      }

      // Skip if branch containing the entry DN is excluded.
      if (isBranchExcluded(entryDN))
      {
        final LocalizableMessage message = LocalizableMessage
            .raw("Skipping entry because it is in excluded branch");
        skipLDIFRecord(record, message);
        continue;
      }

      // Use an Entry for the AttributeSequence.
      final Entry entry = new SortedEntry(entryDN);
      try
      {
        while (record.iterator.hasNext())
        {
          final String ldifLine = record.iterator.next();
          readLDIFRecordAttributeValue(record, ldifLine, entry);
        }
      }
      catch (final DecodeException e)
      {
        rejectLDIFRecord(record, e.getMessageObject());
        continue;
      }

      // Skip if the entry is excluded by any filters.
      if (isEntryExcluded(entry))
      {
        final LocalizableMessage message = LocalizableMessage
            .raw("Skipping entry due to exclusing filters");
        skipLDIFRecord(record, message);
        continue;
      }

      return entry;
    }
  }



  /**
   * Specifies whether or not all operational attributes should be
   * excluded from any entries that are read from LDIF. The default is
   * {@code false}.
   *
   * @param excludeOperationalAttributes
   *          {@code true} if all operational attributes should be
   *          excluded, or {@code false} otherwise.
   * @return A reference to this {@code LDIFEntryReader}.
   */
  public LDIFEntryReader setExcludeAllOperationalAttributes(
      boolean excludeOperationalAttributes)
  {
    this.excludeOperationalAttributes = excludeOperationalAttributes;
    return this;
  }



  /**
   * Specifies whether or not all user attributes should be excluded
   * from any entries that are read from LDIF. The default is {@code
   * false}.
   *
   * @param excludeUserAttributes
   *          {@code true} if all user attributes should be excluded, or
   *          {@code false} otherwise.
   * @return A reference to this {@code LDIFEntryReader}.
   */
  public LDIFEntryReader setExcludeAllUserAttributes(
      boolean excludeUserAttributes)
  {
    this.excludeUserAttributes = excludeUserAttributes;
    return this;
  }



  /**
   * Excludes the named attribute from any entries that are read from
   * LDIF. By default all attributes are included unless explicitly
   * excluded.
   *
   * @param attributeDescription
   *          The name of the attribute to be excluded.
   * @return A reference to this {@code LDIFEntryReader}.
   */
  public LDIFEntryReader setExcludeAttribute(
      AttributeDescription attributeDescription)
  {
    Validator.ensureNotNull(attributeDescription);
    excludeAttributes.add(attributeDescription);
    return this;
  }



  /**
   * Excludes all entries beneath the named entry (inclusive) from being
   * read from LDIF. By default all entries are written unless
   * explicitly excluded or included by branches or filters.
   *
   * @param excludeBranch
   *          The distinguished name of the branch to be excluded.
   * @return A reference to this {@code LDIFEntryReader}.
   */
  public LDIFEntryReader setExcludeBranch(DN excludeBranch)
  {
    Validator.ensureNotNull(excludeBranch);
    excludeBranches.add(excludeBranch);
    return this;
  }



  /**
   * Excludes all entries which match the provided filter matcher from
   * being read from LDIF. By default all entries are read unless
   * explicitly excluded or included by branches or filters.
   *
   * @param excludeFilter
   *          The filter matcher.
   * @return A reference to this {@code LDIFEntryReader}.
   */
  public LDIFEntryReader setExcludeFilter(Matcher excludeFilter)
  {
    Validator.ensureNotNull(excludeFilter);
    excludeFilters.add(excludeFilter);
    return this;
  }



  /**
   * Ensures that the named attribute is not excluded from any entries
   * that are read from LDIF. By default all attributes are included
   * unless explicitly excluded.
   *
   * @param attributeDescription
   *          The name of the attribute to be included.
   * @return A reference to this {@code LDIFEntryReader}.
   */
  public LDIFEntryReader setIncludeAttribute(
      AttributeDescription attributeDescription)
  {
    Validator.ensureNotNull(attributeDescription);
    includeAttributes.add(attributeDescription);
    return this;
  }



  /**
   * Ensures that all entries beneath the named entry (inclusive) are
   * read from LDIF. By default all entries are written unless
   * explicitly excluded or included by branches or filters.
   *
   * @param includeBranch
   *          The distinguished name of the branch to be included.
   * @return A reference to this {@code LDIFEntryReader}.
   */
  public LDIFEntryReader setIncludeBranch(DN includeBranch)
  {
    Validator.ensureNotNull(includeBranch);
    includeBranches.add(includeBranch);
    return this;
  }



  /**
   * Ensures that all entries which match the provided filter matcher
   * are read from LDIF. By default all entries are read unless
   * explicitly excluded or included by branches or filters.
   *
   * @param includeFilter
   *          The filter matcher.
   * @return A reference to this {@code LDIFEntryReader}.
   */
  public LDIFEntryReader setIncludeFilter(Matcher includeFilter)
  {
    Validator.ensureNotNull(includeFilter);
    includeFilters.add(includeFilter);
    return this;
  }



  /**
   * Sets the schema which should be used for decoding entries that are
   * read from LDIF. The default schema is used if no other is
   * specified.
   *
   * @param schema
   *          The schema which should be used for decoding entries that
   *          are read from LDIF.
   * @return A reference to this {@code LDIFEntryReader}.
   */
  public LDIFEntryReader setSchema(Schema schema)
  {
    Validator.ensureNotNull(schema);
    this.schema = schema;
    return this;
  }



  /**
   * Specifies whether or not schema validation should be performed for
   * entries that are read from LDIF. The default is {@code true}.
   *
   * @param validateSchema
   *          {@code true} if schema validation should be performed, or
   *          {@code false} otherwise.
   * @return A reference to this {@code LDIFEntryReader}.
   */
  public LDIFEntryReader setValidateSchema(boolean validateSchema)
  {
    this.validateSchema = validateSchema;
    return this;
  }

}
