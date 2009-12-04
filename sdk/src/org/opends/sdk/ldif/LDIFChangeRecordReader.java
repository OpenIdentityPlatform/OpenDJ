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
import static com.sun.opends.sdk.util.StaticUtils.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.opends.sdk.*;
import org.opends.sdk.requests.ModifyDNRequest;
import org.opends.sdk.requests.ModifyRequest;
import org.opends.sdk.requests.Requests;
import org.opends.sdk.schema.Schema;

import com.sun.opends.sdk.util.LocalizedIllegalArgumentException;
import com.sun.opends.sdk.util.Message;
import com.sun.opends.sdk.util.Validator;



/**
 * An LDIF change record reader reads change records using the LDAP Data
 * Interchange Format (LDIF) from a user defined source.
 *
 * @see <a href="http://tools.ietf.org/html/rfc2849">RFC 2849 - The LDAP
 *      Data Interchange Format (LDIF) - Technical Specification </a>
 */
public final class LDIFChangeRecordReader extends AbstractLDIFReader
    implements ChangeRecordReader
{
  /**
   * Parses the provided array of LDIF lines as a single LDIF change
   * record.
   *
   * @param ldifLines
   *          The lines of LDIF to be parsed.
   * @return The parsed LDIF change record.
   * @throws LocalizedIllegalArgumentException
   *           If {@code ldifLines} did not contain an LDIF change
   *           record, if it contained multiple change records, if
   *           contained malformed LDIF, or if the change record could
   *           not be decoded using the default schema.
   * @throws NullPointerException
   *           If {@code ldifLines} was {@code null}.
   */
  public static ChangeRecord valueOfLDIFChangeRecord(
      String... ldifLines) throws LocalizedIllegalArgumentException,
      NullPointerException
  {
    // LDIF change record reader is tolerant to missing change types.
    LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
        ldifLines);
    try
    {
      ChangeRecord record = reader.readChangeRecord();

      if (record == null)
      {
        // No change record found.
        Message message = WARN_READ_LDIF_RECORD_NO_CHANGE_RECORD_FOUND
            .get();
        throw new LocalizedIllegalArgumentException(message);
      }

      if (reader.readChangeRecord() != null)
      {
        // Multiple change records found.
        Message message = WARN_READ_LDIF_RECORD_MULTIPLE_CHANGE_RECORDS_FOUND
            .get();
        throw new LocalizedIllegalArgumentException(message);
      }

      return record;
    }
    catch (DecodeException e)
    {
      // Badly formed LDIF.
      throw new LocalizedIllegalArgumentException(e.getMessageObject());
    }
    catch (IOException e)
    {
      // This should never happen for a String based reader.
      Message message = WARN_READ_LDIF_RECORD_UNEXPECTED_IO_ERROR.get(e
          .getMessage());
      throw new LocalizedIllegalArgumentException(message);
    }
  }



  /**
   * Creates a new LDIF change record reader whose source is the
   * provided input stream.
   *
   * @param in
   *          The input stream to use.
   * @throws NullPointerException
   *           If {@code in} was {@code null}.
   */
  public LDIFChangeRecordReader(InputStream in)
      throws NullPointerException
  {
    super(in);
  }



  /**
   * Creates a new LDIF change record reader which will read lines of
   * LDIF from the provided list of LDIF lines.
   *
   * @param ldifLines
   *          The lines of LDIF to be read.
   * @throws NullPointerException
   *           If {@code ldifLines} was {@code null}.
   */
  public LDIFChangeRecordReader(List<String> ldifLines)
      throws NullPointerException
  {
    super(ldifLines);
  }



  /**
   * Creates a new LDIF change record reader which will read lines of
   * LDIF from the provided array of LDIF lines.
   *
   * @param ldifLines
   *          The lines of LDIF to be read.
   * @throws NullPointerException
   *           If {@code ldifLines} was {@code null}.
   */
  public LDIFChangeRecordReader(String... ldifLines)
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
  public ChangeRecord readChangeRecord() throws DecodeException,
      IOException
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
        final Message message = Message
            .raw("Skipping entry because it is in excluded branch");
        skipLDIFRecord(record, message);
        continue;
      }

      ChangeRecord changeRecord = null;
      try
      {
        if (!record.iterator.hasNext())
        {
          // FIXME: improve error.
          final Message message = Message.raw("Missing changetype");
          throw DecodeException.error(message);
        }

        final KeyValuePair pair = new KeyValuePair();
        final String ldifLine = readLDIFRecordKeyValuePair(record,
            pair, false);

        if (!toLowerCase(pair.key).equals("changetype"))
        {
          // Default to add change record.
          changeRecord = parseAddChangeRecordEntry(entryDN, ldifLine,
              record);
        }
        else
        {
          final String changeType = toLowerCase(pair.value);
          if (changeType.equals("add"))
          {
            changeRecord = parseAddChangeRecordEntry(entryDN, null,
                record);
          }
          else if (changeType.equals("delete"))
          {
            changeRecord = parseDeleteChangeRecordEntry(entryDN, record);
          }
          else if (changeType.equals("modify"))
          {
            changeRecord = parseModifyChangeRecordEntry(entryDN, record);
          }
          else if (changeType.equals("modrdn"))
          {
            changeRecord = parseModifyDNChangeRecordEntry(entryDN,
                record);
          }
          else if (changeType.equals("moddn"))
          {
            changeRecord = parseModifyDNChangeRecordEntry(entryDN,
                record);
          }
          else
          {
            // FIXME: improve error.
            final Message message = ERR_LDIF_INVALID_CHANGETYPE_ATTRIBUTE
                .get(pair.value, "add, delete, modify, moddn, modrdn");
            throw DecodeException.error(message);
          }
        }
      }
      catch (final DecodeException e)
      {
        rejectLDIFRecord(record, e.getMessageObject());
        continue;
      }

      if (changeRecord != null)
      {
        return changeRecord;
      }
    }
  }



  /**
   * Specifies whether or not all operational attributes should be
   * excluded from any change records that are read from LDIF. The
   * default is {@code false}.
   *
   * @param excludeOperationalAttributes
   *          {@code true} if all operational attributes should be
   *          excluded, or {@code false} otherwise.
   * @return A reference to this {@code LDIFChangeRecordReader}.
   */
  public LDIFChangeRecordReader setExcludeAllOperationalAttributes(
      boolean excludeOperationalAttributes)
  {
    this.excludeOperationalAttributes = excludeOperationalAttributes;
    return this;
  }



  /**
   * Specifies whether or not all user attributes should be excluded
   * from any change records that are read from LDIF. The default is
   * {@code false}.
   *
   * @param excludeUserAttributes
   *          {@code true} if all user attributes should be excluded, or
   *          {@code false} otherwise.
   * @return A reference to this {@code LDIFChangeRecordReader}.
   */
  public LDIFChangeRecordReader setExcludeAllUserAttributes(
      boolean excludeUserAttributes)
  {
    this.excludeUserAttributes = excludeUserAttributes;
    return this;
  }



  /**
   * Excludes the named attribute from any change records that are read
   * from LDIF. By default all attributes are included unless explicitly
   * excluded.
   *
   * @param attributeDescription
   *          The name of the attribute to be excluded.
   * @return A reference to this {@code LDIFChangeRecordReader}.
   */
  public LDIFChangeRecordReader setExcludeAttribute(
      AttributeDescription attributeDescription)
  {
    Validator.ensureNotNull(attributeDescription);
    excludeAttributes.add(attributeDescription);
    return this;
  }



  /**
   * Excludes all change records which target entries beneath the named
   * entry (inclusive) from being read from LDIF. By default all change
   * records are read unless explicitly excluded or included.
   *
   * @param excludeBranch
   *          The distinguished name of the branch to be excluded.
   * @return A reference to this {@code LDIFChangeRecordReader}.
   */
  public LDIFChangeRecordReader setExcludeBranch(DN excludeBranch)
  {
    Validator.ensureNotNull(excludeBranch);
    excludeBranches.add(excludeBranch);
    return this;
  }



  /**
   * Ensures that the named attribute is not excluded from any change
   * records that are read from LDIF. By default all attributes are
   * included unless explicitly excluded.
   *
   * @param attributeDescription
   *          The name of the attribute to be included.
   * @return A reference to this {@code LDIFChangeRecordReader}.
   */
  public LDIFChangeRecordReader setIncludeAttribute(
      AttributeDescription attributeDescription)
  {
    Validator.ensureNotNull(attributeDescription);
    includeAttributes.add(attributeDescription);
    return this;
  }



  /**
   * Ensures that all change records which target entries beneath the
   * named entry (inclusive) are read from LDIF. By default all change
   * records are read unless explicitly excluded or included.
   *
   * @param includeBranch
   *          The distinguished name of the branch to be included.
   * @return A reference to this {@code LDIFChangeRecordReader}.
   */
  public LDIFChangeRecordReader setIncludeBranch(DN includeBranch)
  {
    Validator.ensureNotNull(includeBranch);
    includeBranches.add(includeBranch);
    return this;
  }



  /**
   * Sets the schema which should be used for decoding change records
   * that are read from LDIF. The default schema is used if no other is
   * specified.
   *
   * @param schema
   *          The schema which should be used for decoding change
   *          records that are read from LDIF.
   * @return A reference to this {@code LDIFChangeRecordReader}.
   */
  public LDIFChangeRecordReader setSchema(Schema schema)
  {
    Validator.ensureNotNull(schema);
    this.schema = schema;
    return this;
  }



  /**
   * Specifies whether or not schema validation should be performed for
   * change records that are read from LDIF. The default is {@code true}
   * .
   *
   * @param validateSchema
   *          {@code true} if schema validation should be performed, or
   *          {@code false} otherwise.
   * @return A reference to this {@code LDIFChangeRecordReader}.
   */
  public LDIFChangeRecordReader setValidateSchema(boolean validateSchema)
  {
    this.validateSchema = validateSchema;
    return this;
  }



  private ChangeRecord parseAddChangeRecordEntry(DN entryDN,
      String lastLDIFLine, LDIFRecord record) throws DecodeException
  {
    // Use an Entry for the AttributeSequence.
    final Entry entry = new SortedEntry(entryDN);

    if (lastLDIFLine != null)
    {
      // This line was read when looking for the change type.
      readLDIFRecordAttributeValue(record, lastLDIFLine, entry);
    }

    while (record.iterator.hasNext())
    {
      final String ldifLine = record.iterator.next();
      readLDIFRecordAttributeValue(record, ldifLine, entry);
    }

    return Requests.newAddRequest(entry);
  }



  private ChangeRecord parseDeleteChangeRecordEntry(DN entryDN,
      LDIFRecord record) throws DecodeException
  {
    if (record.iterator.hasNext())
    {
      // FIXME: include line number in error.
      final Message message = ERR_LDIF_INVALID_DELETE_ATTRIBUTES.get();
      throw DecodeException.error(message);
    }

    return Requests.newDeleteRequest(entryDN);
  }



  private ChangeRecord parseModifyChangeRecordEntry(DN entryDN,
      LDIFRecord record) throws DecodeException
  {
    final ModifyRequest modifyRequest = Requests
        .newModifyRequest(entryDN);

    final KeyValuePair pair = new KeyValuePair();
    final List<ByteString> attributeValues = new ArrayList<ByteString>();

    while (record.iterator.hasNext())
    {
      readLDIFRecordKeyValuePair(record, pair, false);
      final String changeType = toLowerCase(pair.key);

      ModificationType modType;
      if (changeType.equals("add"))
      {
        modType = ModificationType.ADD;
      }
      else if (changeType.equals("delete"))
      {
        modType = ModificationType.DELETE;
      }
      else if (changeType.equals("replace"))
      {
        modType = ModificationType.REPLACE;
      }
      else if (changeType.equals("increment"))
      {
        modType = ModificationType.INCREMENT;
      }
      else
      {
        // FIXME: improve error.
        final Message message = ERR_LDIF_INVALID_MODIFY_ATTRIBUTE.get(
            pair.key, "add, delete, replace, increment");
        throw DecodeException.error(message);
      }

      AttributeDescription attributeDescription;
      try
      {
        attributeDescription = AttributeDescription.valueOf(pair.value,
            schema);
      }
      catch (final LocalizedIllegalArgumentException e)
      {
        throw DecodeException.error(e.getMessageObject());
      }

      // Skip the attribute if requested before performing any schema
      // checking: the attribute may have been excluded because it is
      // known to violate the schema.
      if (isAttributeExcluded(attributeDescription))
      {
        continue;
      }

      // Ensure that the binary option is present if required.
      if (!attributeDescription.getAttributeType().getSyntax()
          .isBEREncodingRequired())
      {
        if (validateSchema
            && attributeDescription.containsOption("binary"))
        {
          final Message message = ERR_LDIF_INVALID_ATTR_OPTION.get(
              entryDN.toString(), record.lineNumber, pair.value);
          throw DecodeException.error(message);
        }
      }
      else
      {
        attributeDescription = AttributeDescription.create(
            attributeDescription, "binary");
      }

      // Now go through the rest of the attributes until the "-" line is
      // reached.
      attributeValues.clear();
      while (record.iterator.hasNext())
      {
        final String ldifLine = record.iterator.next();
        if (ldifLine.equals("-"))
        {
          break;
        }

        // Parse the attribute description.
        final int colonPos = parseColonPosition(record, ldifLine);
        final String attrDescr = ldifLine.substring(0, colonPos);

        AttributeDescription attributeDescription2;
        try
        {
          attributeDescription2 = AttributeDescription.valueOf(
              attrDescr, schema);
        }
        catch (final LocalizedIllegalArgumentException e)
        {
          throw DecodeException.error(e.getMessageObject());
        }

        // Ensure that the binary option is present if required.
        if (attributeDescription.getAttributeType().getSyntax()
            .isBEREncodingRequired())
        {
          attributeDescription2 = AttributeDescription.create(
              attributeDescription2, "binary");
        }

        if (!attributeDescription2.equals(attributeDescription))
        {
          // TODO: include line number.
          final Message message = ERR_LDIF_INVALID_CHANGERECORD_ATTRIBUTE
              .get(attributeDescription2.toString(),
                  attributeDescription.toString());
          throw DecodeException.error(message);
        }

        // Now parse the attribute value.
        attributeValues.add(parseSingleValue(record, ldifLine, entryDN,
            colonPos, attrDescr));
      }

      Change change = new Change(modType, new LinkedAttribute(
          attributeDescription, attributeValues));
      modifyRequest.addChange(change);
    }

    return modifyRequest;
  }



  private ChangeRecord parseModifyDNChangeRecordEntry(DN entryDN,
      LDIFRecord record) throws DecodeException
  {
    ModifyDNRequest modifyDNRequest;

    // Parse the newrdn.
    if (!record.iterator.hasNext())
    {
      // TODO: include line number.
      final Message message = ERR_LDIF_NO_MOD_DN_ATTRIBUTES.get();
      throw DecodeException.error(message);
    }

    final KeyValuePair pair = new KeyValuePair();
    String ldifLine = record.iterator.next();
    readLDIFRecordKeyValuePair(record, pair, true);
    if (!toLowerCase(pair.key).equals("newrdn"))
    {
      // FIXME: improve error.
      final Message message = Message.raw("Missing newrdn");
      throw DecodeException.error(message);
    }

    try
    {
      final RDN newRDN = RDN.valueOf(pair.value, schema);
      modifyDNRequest = Requests.newModifyDNRequest(entryDN, newRDN);
    }
    catch (final LocalizedIllegalArgumentException e)
    {
      final Message message = ERR_LDIF_INVALID_DN.get(
          record.lineNumber, ldifLine, e.getMessageObject());
      throw DecodeException.error(message);
    }

    // Parse the deleteoldrdn.
    if (!record.iterator.hasNext())
    {
      // TODO: include line number.
      final Message message = ERR_LDIF_NO_DELETE_OLDRDN_ATTRIBUTE.get();
      throw DecodeException.error(message);
    }

    ldifLine = record.iterator.next();
    readLDIFRecordKeyValuePair(record, pair, true);
    if (!toLowerCase(pair.key).equals("deleteoldrdn"))
    {
      // FIXME: improve error.
      final Message message = Message.raw("Missing deleteoldrdn");
      throw DecodeException.error(message);
    }

    final String delStr = toLowerCase(pair.value);
    if (delStr.equals("false") || delStr.equals("no")
        || delStr.equals("0"))
    {
      modifyDNRequest.setDeleteOldRDN(false);
    }
    else if (delStr.equals("true") || delStr.equals("yes")
        || delStr.equals("1"))
    {
      modifyDNRequest.setDeleteOldRDN(true);
    }
    else
    {
      // FIXME: improve error.
      final Message message = ERR_LDIF_INVALID_DELETE_OLDRDN_ATTRIBUTE
          .get(pair.value);
      throw DecodeException.error(message);
    }

    // Parse the newsuperior if present.
    if (record.iterator.hasNext())
    {
      ldifLine = record.iterator.next();
      readLDIFRecordKeyValuePair(record, pair, true);
      if (!toLowerCase(pair.key).equals("newsuperior"))
      {
        // FIXME: improve error.
        final Message message = Message.raw("Missing newsuperior");
        throw DecodeException.error(message);
      }

      try
      {
        final DN newSuperiorDN = DN.valueOf(pair.value, schema);
        modifyDNRequest.setNewSuperior(newSuperiorDN.toString());
      }
      catch (final LocalizedIllegalArgumentException e)
      {
        final Message message = ERR_LDIF_INVALID_DN.get(
            record.lineNumber, ldifLine, e.getMessageObject());
        throw DecodeException.error(message);
      }
    }

    return modifyDNRequest;
  }

}
