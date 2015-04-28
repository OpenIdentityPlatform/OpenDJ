/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2015 ForgeRock AS
 */

package org.forgerock.opendj.ldif;

import static com.forgerock.opendj.ldap.CoreMessages.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.Matcher;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaValidationPolicy;
import org.forgerock.util.Reject;
import org.forgerock.util.Utils;

/**
 * An LDIF entry reader reads attribute value records (entries) using the LDAP
 * Data Interchange Format (LDIF) from a user defined source.
 *
 * @see <a href="http://tools.ietf.org/html/rfc2849">RFC 2849 - The LDAP Data
 *      Interchange Format (LDIF) - Technical Specification </a>
 */
public final class LDIFEntryReader extends AbstractLDIFReader implements EntryReader {
    /** Poison used to indicate end of LDIF. */
    private static final Entry EOF = new LinkedHashMapEntry();

    /**
     * Parses the provided array of LDIF lines as a single LDIF entry.
     *
     * @param ldifLines
     *            The lines of LDIF to be parsed.
     * @return The parsed LDIF entry.
     * @throws LocalizedIllegalArgumentException
     *             If {@code ldifLines} did not contain an LDIF entry, if it
     *             contained multiple entries, if contained malformed LDIF, or
     *             if the entry could not be decoded using the default schema.
     * @throws NullPointerException
     *             If {@code ldifLines} was {@code null}.
     */
    public static Entry valueOfLDIFEntry(final String... ldifLines) {
        final LDIFEntryReader reader = new LDIFEntryReader(ldifLines);
        try {
            if (!reader.hasNext()) {
                // No change record found.
                final LocalizableMessage message =
                        WARN_READ_LDIF_RECORD_NO_CHANGE_RECORD_FOUND.get();
                throw new LocalizedIllegalArgumentException(message);
            }

            final Entry entry = reader.readEntry();

            if (reader.hasNext()) {
                // Multiple change records found.
                final LocalizableMessage message =
                        WARN_READ_LDIF_RECORD_MULTIPLE_CHANGE_RECORDS_FOUND.get();
                throw new LocalizedIllegalArgumentException(message);
            }

            return entry;
        } catch (final DecodeException e) {
            // Badly formed LDIF.
            throw new LocalizedIllegalArgumentException(e.getMessageObject());
        } catch (final IOException e) {
            // This should never happen for a String based reader.
            final LocalizableMessage message =
                    WARN_READ_LDIF_RECORD_UNEXPECTED_IO_ERROR.get(e.getMessage());
            throw new LocalizedIllegalArgumentException(message);
        } finally {
            Utils.closeSilently(reader);
        }
    }

    private Entry nextEntry;

    /**
     * Creates a new LDIF entry reader whose source is the provided input
     * stream.
     *
     * @param in
     *            The input stream to use.
     * @throws NullPointerException
     *             If {@code in} was {@code null}.
     */
    public LDIFEntryReader(final InputStream in) {
        super(in);
    }

    /**
     * Creates a new LDIF entry reader which will read lines of LDIF from the
     * provided list of LDIF lines.
     *
     * @param ldifLines
     *            The lines of LDIF to be read.
     * @throws NullPointerException
     *             If {@code ldifLines} was {@code null}.
     */
    public LDIFEntryReader(final List<String> ldifLines) {
        super(ldifLines);
    }

    /**
     * Creates a new LDIF entry reader whose source is the provided character
     * stream reader.
     *
     * @param reader
     *            The character stream reader to use.
     * @throws NullPointerException
     *             If {@code reader} was {@code null}.
     */
    public LDIFEntryReader(final Reader reader) {
        super(reader);
    }

    /**
     * Creates a new LDIF entry reader which will read lines of LDIF from the
     * provided array of LDIF lines.
     *
     * @param ldifLines
     *            The lines of LDIF to be read.
     * @throws NullPointerException
     *             If {@code ldifLines} was {@code null}.
     */
    public LDIFEntryReader(final String... ldifLines) {
        super(Arrays.asList(ldifLines));
    }

    /** {@inheritDoc} */
    @Override
    public void close() throws IOException {
        close0();
    }

    /**
     * {@inheritDoc}
     *
     * @throws DecodeException
     *             If the entry could not be decoded because it was malformed.
     */
    @Override
    public boolean hasNext() throws DecodeException, IOException {
        return getNextEntry() != EOF;
    }

    /**
     * {@inheritDoc}
     *
     * @throws DecodeException
     *             If the entry could not be decoded because it was malformed.
     */
    @Override
    public Entry readEntry() throws DecodeException, IOException {
        if (!hasNext()) {
            // LDIF reader has completed successfully.
            throw new NoSuchElementException();
        }

        final Entry entry = nextEntry;
        nextEntry = null;
        return entry;
    }

    /**
     * Specifies whether or not all operational attributes should be excluded
     * from any entries that are read from LDIF. The default is {@code false}.
     *
     * @param excludeOperationalAttributes
     *            {@code true} if all operational attributes should be excluded,
     *            or {@code false} otherwise.
     * @return A reference to this {@code LDIFEntryReader}.
     */
    public LDIFEntryReader setExcludeAllOperationalAttributes(
            final boolean excludeOperationalAttributes) {
        this.excludeOperationalAttributes = excludeOperationalAttributes;
        return this;
    }

    /**
     * Specifies whether or not all user attributes should be excluded from any
     * entries that are read from LDIF. The default is {@code false}.
     *
     * @param excludeUserAttributes
     *            {@code true} if all user attributes should be excluded, or
     *            {@code false} otherwise.
     * @return A reference to this {@code LDIFEntryReader}.
     */
    public LDIFEntryReader setExcludeAllUserAttributes(final boolean excludeUserAttributes) {
        this.excludeUserAttributes = excludeUserAttributes;
        return this;
    }

    /**
     * Excludes the named attribute from any entries that are read from LDIF. By
     * default all attributes are included unless explicitly excluded.
     *
     * @param attributeDescription
     *            The name of the attribute to be excluded.
     * @return A reference to this {@code LDIFEntryReader}.
     */
    public LDIFEntryReader setExcludeAttribute(final AttributeDescription attributeDescription) {
        Reject.ifNull(attributeDescription);
        excludeAttributes.add(attributeDescription);
        return this;
    }

    /**
     * Excludes all entries beneath the named entry (inclusive) from being read
     * from LDIF. By default all entries are written unless explicitly excluded
     * or included by branches or filters.
     *
     * @param excludeBranch
     *            The distinguished name of the branch to be excluded.
     * @return A reference to this {@code LDIFEntryReader}.
     */
    public LDIFEntryReader setExcludeBranch(final DN excludeBranch) {
        Reject.ifNull(excludeBranch);
        excludeBranches.add(excludeBranch);
        return this;
    }

    /**
     * Excludes all entries which match the provided filter matcher from being
     * read from LDIF. By default all entries are read unless explicitly
     * excluded or included by branches or filters.
     *
     * @param excludeFilter
     *            The filter matcher.
     * @return A reference to this {@code LDIFEntryReader}.
     */
    public LDIFEntryReader setExcludeFilter(final Matcher excludeFilter) {
        Reject.ifNull(excludeFilter);
        excludeFilters.add(excludeFilter);
        return this;
    }

    /**
     * Ensures that the named attribute is not excluded from any entries that
     * are read from LDIF. By default all attributes are included unless
     * explicitly excluded.
     *
     * @param attributeDescription
     *            The name of the attribute to be included.
     * @return A reference to this {@code LDIFEntryReader}.
     */
    public LDIFEntryReader setIncludeAttribute(final AttributeDescription attributeDescription) {
        Reject.ifNull(attributeDescription);
        includeAttributes.add(attributeDescription);
        return this;
    }

    /**
     * Ensures that all entries beneath the named entry (inclusive) are read
     * from LDIF. By default all entries are written unless explicitly excluded
     * or included by branches or filters.
     *
     * @param includeBranch
     *            The distinguished name of the branch to be included.
     * @return A reference to this {@code LDIFEntryReader}.
     */
    public LDIFEntryReader setIncludeBranch(final DN includeBranch) {
        Reject.ifNull(includeBranch);
        includeBranches.add(includeBranch);
        return this;
    }

    /**
     * Ensures that all entries which match the provided filter matcher are read
     * from LDIF. By default all entries are read unless explicitly excluded or
     * included by branches or filters.
     *
     * @param includeFilter
     *            The filter matcher.
     * @return A reference to this {@code LDIFEntryReader}.
     */
    public LDIFEntryReader setIncludeFilter(final Matcher includeFilter) {
        Reject.ifNull(includeFilter);
        includeFilters.add(includeFilter);
        return this;
    }

    /**
     * Sets the rejected record listener which should be notified whenever an
     * LDIF record is skipped, malformed, or fails schema validation.
     * <p>
     * By default the {@link RejectedLDIFListener#FAIL_FAST} listener is used.
     *
     * @param listener
     *            The rejected record listener.
     * @return A reference to this {@code LDIFEntryReader}.
     */
    public LDIFEntryReader setRejectedLDIFListener(final RejectedLDIFListener listener) {
        this.rejectedRecordListener = listener;
        return this;
    }

    /**
     * Sets the schema which should be used for decoding entries that are read
     * from LDIF. The default schema is used if no other is specified.
     *
     * @param schema
     *            The schema which should be used for decoding entries that are
     *            read from LDIF.
     * @return A reference to this {@code LDIFEntryReader}.
     */
    public LDIFEntryReader setSchema(final Schema schema) {
        Reject.ifNull(schema);
        this.schema = schemaValidationPolicy.adaptSchemaForValidation(schema);
        return this;
    }

    /**
     * Specifies the schema validation which should be used when reading LDIF
     * entry records. If attribute value validation is enabled then all checks
     * will be performed.
     * <p>
     * Schema validation is disabled by default.
     * <p>
     * <b>NOTE:</b> this method copies the provided policy so changes made to it
     * after this method has been called will have no effect.
     *
     * @param policy
     *            The schema validation which should be used when reading LDIF
     *            entry records.
     * @return A reference to this {@code LDIFEntryReader}.
     */
    public LDIFEntryReader setSchemaValidationPolicy(final SchemaValidationPolicy policy) {
        this.schemaValidationPolicy = SchemaValidationPolicy.copyOf(policy);
        this.schema = schemaValidationPolicy.adaptSchemaForValidation(schema);
        return this;
    }

    private Entry getNextEntry() throws DecodeException, IOException {
        while (nextEntry == null) {
            // Read the set of lines that make up the next entry.
            final LDIFRecord record = readLDIFRecord();
            if (record == null) {
                nextEntry = EOF;
                break;
            }

            try {
                /*
                 * Read the DN of the entry and see if it is one that should be
                 * included in the import.
                 */
                final DN entryDN = readLDIFRecordDN(record);
                if (entryDN == null) {
                    // Skip version record.
                    continue;
                }

                // Skip if branch containing the entry DN is excluded.
                if (isBranchExcluded(entryDN)) {
                    final LocalizableMessage message =
                            ERR_LDIF_ENTRY_EXCLUDED_BY_DN
                                    .get(record.lineNumber, entryDN.toString());
                    handleSkippedRecord(record, message);
                    continue;
                }

                // Use an Entry for the AttributeSequence.
                final Entry entry = new LinkedHashMapEntry(entryDN);
                boolean schemaValidationFailure = false;
                final List<LocalizableMessage> schemaErrors = new LinkedList<>();
                while (record.iterator.hasNext()) {
                    final String ldifLine = record.iterator.next();
                    if (!readLDIFRecordAttributeValue(record, ldifLine, entry, schemaErrors)) {
                        schemaValidationFailure = true;
                    }
                }

                // Skip if the entry is excluded by any filters.
                if (isEntryExcluded(entry)) {
                    final LocalizableMessage message =
                            ERR_LDIF_ENTRY_EXCLUDED_BY_FILTER.get(record.lineNumber, entryDN
                                    .toString());
                    handleSkippedRecord(record, message);
                    continue;
                }

                if (!schema.validateEntry(entry, schemaValidationPolicy, schemaErrors)) {
                    schemaValidationFailure = true;
                }

                if (schemaValidationFailure) {
                    handleSchemaValidationFailure(record, schemaErrors);
                    continue;
                }

                if (!schemaErrors.isEmpty()) {
                    handleSchemaValidationWarning(record, schemaErrors);
                }

                nextEntry = entry;
            } catch (final DecodeException e) {
                handleMalformedRecord(record, e.getMessageObject());
                continue;
            }
        }

        return nextEntry;
    }

}
