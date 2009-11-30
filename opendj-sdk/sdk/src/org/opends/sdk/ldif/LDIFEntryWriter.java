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



import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import org.opends.sdk.*;
import org.opends.sdk.schema.Schema;
import org.opends.sdk.util.ByteString;
import org.opends.sdk.util.Validator;



/**
 * An LDIF entry writer writes attribute value records (entries) using
 * the LDAP Data Interchange Format (LDIF) to a user defined
 * destination.
 *
 * @see <a href="http://tools.ietf.org/html/rfc2849">RFC 2849 - The LDAP
 *      Data Interchange Format (LDIF) - Technical Specification </a>
 */
public final class LDIFEntryWriter extends AbstractLDIFWriter implements
    EntryWriter
{

  /**
   * Creates a new LDIF entry writer which will append lines of LDIF to
   * the provided list.
   *
   * @param ldifLines
   *          The list to which lines of LDIF should be appended.
   */
  public LDIFEntryWriter(List<String> ldifLines)
  {
    super(ldifLines);
  }



  /**
   * Creates a new LDIF entry writer whose destination is the provided
   * output stream.
   *
   * @param out
   *          The output stream to use.
   */
  public LDIFEntryWriter(OutputStream out)
  {
    super(out);
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
  public void flush() throws IOException
  {
    flush0();
  }



  /**
   * Specifies whether or not user-friendly comments should be added
   * whenever distinguished names or UTF-8 attribute values are
   * encountered which contained non-ASCII characters. The default is
   * {@code false}.
   *
   * @param addUserFriendlyComments
   *          {@code true} if user-friendly comments should be added, or
   *          {@code false} otherwise.
   * @return A reference to this {@code LDIFEntryWriter}.
   */
  public LDIFEntryWriter setAddUserFriendlyComments(
      boolean addUserFriendlyComments)
  {
    this.addUserFriendlyComments = addUserFriendlyComments;
    return this;
  }



  /**
   * Specifies whether or not all operational attributes should be
   * excluded from any entries that are written to LDIF. The default is
   * {@code false}.
   *
   * @param excludeOperationalAttributes
   *          {@code true} if all operational attributes should be
   *          excluded, or {@code false} otherwise.
   * @return A reference to this {@code LDIFEntryWriter}.
   */
  public LDIFEntryWriter setExcludeAllOperationalAttributes(
      boolean excludeOperationalAttributes)
  {
    this.excludeOperationalAttributes = excludeOperationalAttributes;
    return this;
  }



  /**
   * Specifies whether or not all user attributes should be excluded
   * from any entries that are written to LDIF. The default is {@code
   * false}.
   *
   * @param excludeUserAttributes
   *          {@code true} if all user attributes should be excluded, or
   *          {@code false} otherwise.
   * @return A reference to this {@code LDIFEntryWriter}.
   */
  public LDIFEntryWriter setExcludeAllUserAttributes(
      boolean excludeUserAttributes)
  {
    this.excludeUserAttributes = excludeUserAttributes;
    return this;
  }



  /**
   * Excludes the named attribute from any entries that are written to
   * LDIF. By default all attributes are included unless explicitly
   * excluded.
   *
   * @param attributeDescription
   *          The name of the attribute to be excluded.
   * @return A reference to this {@code LDIFEntryWriter}.
   */
  public LDIFEntryWriter setExcludeAttribute(
      AttributeDescription attributeDescription)
  {
    Validator.ensureNotNull(attributeDescription);
    excludeAttributes.add(attributeDescription);
    return this;
  }



  /**
   * Excludes all entries beneath the named entry (inclusive) from being
   * written to LDIF. By default all entries are written unless
   * explicitly excluded or included by branches or filters.
   *
   * @param excludeBranch
   *          The distinguished name of the branch to be excluded.
   * @return A reference to this {@code LDIFEntryWriter}.
   */
  public LDIFEntryWriter setExcludeBranch(DN excludeBranch)
  {
    Validator.ensureNotNull(excludeBranch);
    excludeBranches.add(excludeBranch);
    return this;
  }



  /**
   * Excludes all entries which match the provided filter matcher from
   * being written to LDIF. By default all entries are written unless
   * explicitly excluded or included by branches or filters.
   *
   * @param excludeFilter
   *          The filter matcher.
   * @return A reference to this {@code LDIFEntryWriter}.
   */
  public LDIFEntryWriter setExcludeFilter(Matcher excludeFilter)
  {
    Validator.ensureNotNull(excludeFilter);
    excludeFilters.add(excludeFilter);
    return this;
  }



  /**
   * Ensures that the named attribute is not excluded from any entries
   * that are written to LDIF. By default all attributes are included
   * unless explicitly excluded.
   *
   * @param attributeDescription
   *          The name of the attribute to be included.
   * @return A reference to this {@code LDIFEntryWriter}.
   */
  public LDIFEntryWriter setIncludeAttribute(
      AttributeDescription attributeDescription)
  {
    Validator.ensureNotNull(attributeDescription);
    includeAttributes.add(attributeDescription);
    return this;
  }



  /**
   * Ensures that all entries beneath the named entry (inclusive) are
   * written to LDIF. By default all entries are written unless
   * explicitly excluded or included by branches or filters.
   *
   * @param includeBranch
   *          The distinguished name of the branch to be included.
   * @return A reference to this {@code LDIFEntryWriter}.
   */
  public LDIFEntryWriter setIncludeBranch(DN includeBranch)
  {
    Validator.ensureNotNull(includeBranch);
    includeBranches.add(includeBranch);
    return this;
  }



  /**
   * Ensures that all entries which match the provided filter matcher
   * are written to LDIF. By default all entries are written unless
   * explicitly excluded or included by branches or filters.
   *
   * @param includeFilter
   *          The filter matcher.
   * @return A reference to this {@code LDIFEntryWriter}.
   */
  public LDIFEntryWriter setIncludeFilter(Matcher includeFilter)
  {
    Validator.ensureNotNull(includeFilter);
    includeFilters.add(includeFilter);
    return this;
  }



  /**
   * Sets the schema which should be used when filtering entries (not
   * required if no filtering is to be performed). The default schema is
   * used if no other is specified.
   *
   * @param schema
   *          The schema which should be used when filtering entries.
   * @return A reference to this {@code LDIFEntryWriter}.
   */
  public LDIFEntryWriter setSchema(Schema schema)
  {
    Validator.ensureNotNull(schema);
    this.schema = schema;
    return this;
  }



  /**
   * Specifies the column at which long lines should be wrapped. A value
   * less than or equal to zero (the default) indicates that no wrapping
   * should be performed.
   *
   * @param wrapColumn
   *          The column at which long lines should be wrapped.
   * @return A reference to this {@code LDIFEntryWriter}.
   */
  public LDIFEntryWriter setWrapColumn(int wrapColumn)
  {
    this.wrapColumn = wrapColumn;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public LDIFEntryWriter writeComment(CharSequence comment)
      throws IOException, NullPointerException
  {
    writeComment0(comment);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public LDIFEntryWriter writeEntry(Entry entry) throws IOException,
      NullPointerException
  {
    Validator.ensureNotNull(entry);

    // Skip if branch containing the entry is excluded.
    if (isBranchExcluded(entry.getName()))
    {
      return this;
    }

    // Skip if the entry is excluded by any filters.
    if (isEntryExcluded(entry))
    {
      return this;
    }

    writeKeyAndValue("dn", entry.getName().toString());
    for (final Attribute attribute : entry.getAttributes())
    {
      // Filter the attribute if required.
      if (isAttributeExcluded(attribute.getAttributeDescription()))
      {
        continue;
      }

      final String attributeDescription =
          attribute.getAttributeDescriptionAsString();
      for (final ByteString value : attribute)
      {
        writeKeyAndValue(attributeDescription, value);
      }
    }

    // Make sure there is a blank line after the entry.
    impl.println();

    return this;
  }
}
