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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.GenericControl;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaValidationPolicy;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.forgerock.opendj.ldap.schema.UnknownSchemaElementException;
import org.forgerock.util.Reject;
import org.forgerock.util.Utils;

import static com.forgerock.opendj.ldap.CoreMessages.*;
import static com.forgerock.opendj.util.StaticUtils.*;

/**
 * An LDIF change record reader reads change records using the LDAP Data
 * Interchange Format (LDIF) from a user defined source.
 * <p>
 * The following example reads changes from LDIF, and writes the changes to the
 * directory server.
 *
 * <pre>
 * InputStream ldif = ...;
 * LDIFChangeRecordReader reader = new LDIFChangeRecordReader(ldif);
 *
 * Connection connection = ...;
 * connection.bind(...);
 *
 * ConnectionChangeRecordWriter writer =
 *         new ConnectionChangeRecordWriter(connection);
 * while (reader.hasNext()) {
 *     ChangeRecord changeRecord = reader.readChangeRecord();
 *     writer.writeChangeRecord(changeRecord);
 * }
 * </pre>
 *
 * @see <a href="http://tools.ietf.org/html/rfc2849">RFC 2849 - The LDAP Data
 *      Interchange Format (LDIF) - Technical Specification </a>
 */
public final class LDIFChangeRecordReader extends AbstractLDIFReader implements ChangeRecordReader {
    private static final Pattern CONTROL_REGEX = Pattern
            .compile("^\\s*(\\d+(.\\d+)*)(\\s+((true)|(false)))?\\s*(:(:)?\\s*?\\S+)?\\s*$");

    /** Poison used to indicate end of LDIF. */
    private static final ChangeRecord EOF = Requests.newAddRequest(DN.rootDN());

    /**
     * Parses the provided array of LDIF lines as a single LDIF change record.
     *
     * @param ldifLines
     *            The lines of LDIF to be parsed.
     * @return The parsed LDIF change record.
     * @throws LocalizedIllegalArgumentException
     *             If {@code ldifLines} did not contain an LDIF change record,
     *             if it contained multiple change records, if contained
     *             malformed LDIF, or if the change record could not be decoded
     *             using the default schema.
     * @throws NullPointerException
     *             If {@code ldifLines} was {@code null}.
     */
    public static ChangeRecord valueOfLDIFChangeRecord(final String... ldifLines) {
        // LDIF change record reader is tolerant to missing change types.
        final LDIFChangeRecordReader reader = new LDIFChangeRecordReader(ldifLines);
        try {
            if (!reader.hasNext()) {
                // No change record found.
                final LocalizableMessage message =
                        WARN_READ_LDIF_RECORD_NO_CHANGE_RECORD_FOUND.get();
                throw new LocalizedIllegalArgumentException(message);
            }

            final ChangeRecord record = reader.readChangeRecord();

            if (reader.hasNext()) {
                // Multiple change records found.
                final LocalizableMessage message =
                        WARN_READ_LDIF_RECORD_MULTIPLE_CHANGE_RECORDS_FOUND.get();
                throw new LocalizedIllegalArgumentException(message);
            }

            return record;
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

    private ChangeRecord nextChangeRecord;

    /**
     * Creates a new LDIF change record reader whose source is the provided
     * input stream.
     *
     * @param in
     *            The input stream to use.
     * @throws NullPointerException
     *             If {@code in} was {@code null}.
     */
    public LDIFChangeRecordReader(final InputStream in) {
        super(in);
    }

    /**
     * Creates a new LDIF change record reader which will read lines of LDIF
     * from the provided list of LDIF lines.
     *
     * @param ldifLines
     *            The lines of LDIF to be read.
     * @throws NullPointerException
     *             If {@code ldifLines} was {@code null}.
     */
    public LDIFChangeRecordReader(final List<String> ldifLines) {
        super(ldifLines);
    }

    /**
     * Creates a new LDIF change record reader whose source is the provided
     * character stream reader.
     *
     * @param reader
     *            The character stream reader to use.
     * @throws NullPointerException
     *             If {@code reader} was {@code null}.
     */
    public LDIFChangeRecordReader(final Reader reader) {
        super(reader);
    }

    /**
     * Creates a new LDIF change record reader which will read lines of LDIF
     * from the provided array of LDIF lines.
     *
     * @param ldifLines
     *            The lines of LDIF to be read.
     * @throws NullPointerException
     *             If {@code ldifLines} was {@code null}.
     */
    public LDIFChangeRecordReader(final String... ldifLines) {
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
     *             If the change record could not be decoded because it was
     *             malformed.
     */
    @Override
    public boolean hasNext() throws DecodeException, IOException {
        return getNextChangeRecord() != EOF;
    }

    /**
     * {@inheritDoc}
     *
     * @throws DecodeException
     *             If the entry could not be decoded because it was malformed.
     */
    @Override
    public ChangeRecord readChangeRecord() throws DecodeException, IOException {
        if (!hasNext()) {
            // LDIF reader has completed successfully.
            throw new NoSuchElementException();
        }

        final ChangeRecord changeRecord = nextChangeRecord;
        nextChangeRecord = null;
        return changeRecord;
    }

    /**
     * Specifies whether or not all operational attributes should be excluded
     * from any change records that are read from LDIF. The default is
     * {@code false}.
     *
     * @param excludeOperationalAttributes
     *            {@code true} if all operational attributes should be excluded,
     *            or {@code false} otherwise.
     * @return A reference to this {@code LDIFChangeRecordReader}.
     */
    public LDIFChangeRecordReader setExcludeAllOperationalAttributes(
            final boolean excludeOperationalAttributes) {
        this.excludeOperationalAttributes = excludeOperationalAttributes;
        return this;
    }

    /**
     * Specifies whether or not all user attributes should be excluded from any
     * change records that are read from LDIF. The default is {@code false}.
     *
     * @param excludeUserAttributes
     *            {@code true} if all user attributes should be excluded, or
     *            {@code false} otherwise.
     * @return A reference to this {@code LDIFChangeRecordReader}.
     */
    public LDIFChangeRecordReader setExcludeAllUserAttributes(final boolean excludeUserAttributes) {
        this.excludeUserAttributes = excludeUserAttributes;
        return this;
    }

    /**
     * Excludes the named attribute from any change records that are read from
     * LDIF. By default all attributes are included unless explicitly excluded.
     *
     * @param attributeDescription
     *            The name of the attribute to be excluded.
     * @return A reference to this {@code LDIFChangeRecordReader}.
     */
    public LDIFChangeRecordReader setExcludeAttribute(
            final AttributeDescription attributeDescription) {
        Reject.ifNull(attributeDescription);
        excludeAttributes.add(attributeDescription);
        return this;
    }

    /**
     * Excludes all change records which target entries beneath the named entry
     * (inclusive) from being read from LDIF. By default all change records are
     * read unless explicitly excluded or included.
     *
     * @param excludeBranch
     *            The distinguished name of the branch to be excluded.
     * @return A reference to this {@code LDIFChangeRecordReader}.
     */
    public LDIFChangeRecordReader setExcludeBranch(final DN excludeBranch) {
        Reject.ifNull(excludeBranch);
        excludeBranches.add(excludeBranch);
        return this;
    }

    /**
     * Ensures that the named attribute is not excluded from any change records
     * that are read from LDIF. By default all attributes are included unless
     * explicitly excluded.
     *
     * @param attributeDescription
     *            The name of the attribute to be included.
     * @return A reference to this {@code LDIFChangeRecordReader}.
     */
    public LDIFChangeRecordReader setIncludeAttribute(
            final AttributeDescription attributeDescription) {
        Reject.ifNull(attributeDescription);
        includeAttributes.add(attributeDescription);
        return this;
    }

    /**
     * Ensures that all change records which target entries beneath the named
     * entry (inclusive) are read from LDIF. By default all change records are
     * read unless explicitly excluded or included.
     *
     * @param includeBranch
     *            The distinguished name of the branch to be included.
     * @return A reference to this {@code LDIFChangeRecordReader}.
     */
    public LDIFChangeRecordReader setIncludeBranch(final DN includeBranch) {
        Reject.ifNull(includeBranch);
        includeBranches.add(includeBranch);
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
     * @return A reference to this {@code LDIFChangeRecordReader}.
     */
    public LDIFChangeRecordReader setRejectedLDIFListener(final RejectedLDIFListener listener) {
        this.rejectedRecordListener = listener;
        return this;
    }

    /**
     * Sets the schema which should be used for decoding change records that are
     * read from LDIF. The default schema is used if no other is specified.
     *
     * @param schema
     *            The schema which should be used for decoding change records
     *            that are read from LDIF.
     * @return A reference to this {@code LDIFChangeRecordReader}.
     */
    public LDIFChangeRecordReader setSchema(final Schema schema) {
        Reject.ifNull(schema);
        this.schema = schemaValidationPolicy.adaptSchemaForValidation(schema);
        return this;
    }

    /**
     * Specifies the schema validation which should be used when reading LDIF
     * change records. If attribute value validation is enabled then all checks
     * will be performed.
     * <p>
     * Schema validation is disabled by default.
     * <p>
     * <b>NOTE:</b> this method copies the provided policy so changes made to it
     * after this method has been called will have no effect.
     *
     * @param policy
     *            The schema validation which should be used when reading LDIF
     *            change records.
     * @return A reference to this {@code LDIFChangeRecordReader}.
     */
    public LDIFChangeRecordReader setSchemaValidationPolicy(final SchemaValidationPolicy policy) {
        this.schemaValidationPolicy = SchemaValidationPolicy.copyOf(policy);
        this.schema = schemaValidationPolicy.adaptSchemaForValidation(schema);
        return this;
    }

    private ChangeRecord getNextChangeRecord() throws DecodeException, IOException {
        while (nextChangeRecord == null) {
            // Read the set of lines that make up the next entry.
            final LDIFRecord record = readLDIFRecord();
            if (record == null) {
                nextChangeRecord = EOF;
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
                            ERR_LDIF_CHANGE_EXCLUDED_BY_DN.get(record.lineNumber, entryDN);
                    handleSkippedRecord(record, message);
                    continue;
                }

                KeyValuePair pair;
                String ldifLine;
                List<Control> controls = null;
                while (true) {
                    if (!record.iterator.hasNext()) {
                        throw DecodeException.error(
                                ERR_LDIF_NO_CHANGE_TYPE.get(record.lineNumber, entryDN));
                    }

                    pair = new KeyValuePair();
                    ldifLine = readLDIFRecordKeyValuePair(record, pair, false);
                    if (pair.key == null) {
                        throw DecodeException.error(
                                ERR_LDIF_MALFORMED_CHANGE_TYPE.get(record.lineNumber, entryDN, ldifLine));
                    }

                    if (!"control".equals(toLowerCase(pair.key))) {
                        break;
                    }

                    if (controls == null) {
                        controls = new LinkedList<>();
                    }

                    controls.add(parseControl(entryDN, record, ldifLine, pair.value));
                }

                if (!"changetype".equals(toLowerCase(pair.key))) {
                    // Default to add change record.
                    nextChangeRecord = parseAddChangeRecordEntry(entryDN, ldifLine, record);
                } else {
                    final String changeType = toLowerCase(pair.value);
                    if ("add".equals(changeType)) {
                        nextChangeRecord = parseAddChangeRecordEntry(entryDN, null, record);
                    } else if ("delete".equals(changeType)) {
                        nextChangeRecord = parseDeleteChangeRecordEntry(entryDN, record);
                    } else if ("modify".equals(changeType)) {
                        nextChangeRecord = parseModifyChangeRecordEntry(entryDN, record);
                    } else if ("modrdn".equals(changeType)) {
                        nextChangeRecord = parseModifyDNChangeRecordEntry(entryDN, record);
                    } else if ("moddn".equals(changeType)) {
                        nextChangeRecord = parseModifyDNChangeRecordEntry(entryDN, record);
                    } else {
                        throw DecodeException.error(
                                ERR_LDIF_BAD_CHANGE_TYPE.get(record.lineNumber, entryDN, pair.value));
                    }

                    // Add the controls to the record.
                    if (controls != null) {
                        for (final Control control : controls) {
                            nextChangeRecord.addControl(control);
                        }
                    }
                }
            } catch (final DecodeException e) {
                handleMalformedRecord(record, e.getMessageObject());
                continue;
            }
        }
        return nextChangeRecord;
    }

    private ChangeRecord parseAddChangeRecordEntry(final DN entryDN, final String lastLDIFLine,
            final LDIFRecord record) throws DecodeException {
        // Use an Entry for the AttributeSequence.
        final Entry entry = new LinkedHashMapEntry(entryDN);
        boolean schemaValidationFailure = false;
        final List<LocalizableMessage> schemaErrors = new LinkedList<>();

        if (lastLDIFLine != null
                // This line was read when looking for the change type.
                && !readLDIFRecordAttributeValue(record, lastLDIFLine, entry, schemaErrors)) {
            schemaValidationFailure = true;
        }

        while (record.iterator.hasNext()) {
            final String ldifLine = record.iterator.next();
            if (!readLDIFRecordAttributeValue(record, ldifLine, entry, schemaErrors)) {
                schemaValidationFailure = true;
            }
        }

        if (!schema.validateEntry(entry, schemaValidationPolicy, schemaErrors)) {
            schemaValidationFailure = true;
        }

        if (schemaValidationFailure) {
            handleSchemaValidationFailure(record, schemaErrors);
            return null;
        }

        if (!schemaErrors.isEmpty()) {
            handleSchemaValidationWarning(record, schemaErrors);
        }
        return Requests.newAddRequest(entry);
    }

    private Control parseControl(final DN entryDN, final LDIFRecord record, final String ldifLine,
            final String value) throws DecodeException {
        final Matcher matcher = CONTROL_REGEX.matcher(value);
        if (!matcher.matches()) {
            throw DecodeException.error(ERR_LDIF_MALFORMED_CONTROL.get(record.lineNumber, entryDN, ldifLine));
        }
        final String oid = matcher.group(1);
        final boolean isCritical = matcher.group(5) != null;
        final String controlValueString = matcher.group(7);
        ByteString controlValue = null;
        if (controlValueString != null) {
            controlValue =
                    parseSingleValue(record, ldifLine, entryDN, ldifLine.indexOf(':', 8), oid);
        }
        return GenericControl.newControl(oid, isCritical, controlValue);
    }

    private ChangeRecord parseDeleteChangeRecordEntry(final DN entryDN, final LDIFRecord record)
            throws DecodeException {
        if (record.iterator.hasNext()) {
            throw DecodeException.error(ERR_LDIF_MALFORMED_DELETE.get(record.lineNumber, entryDN));
        }
        return Requests.newDeleteRequest(entryDN);
    }

    private ChangeRecord parseModifyChangeRecordEntry(final DN entryDN, final LDIFRecord record)
            throws DecodeException {
        final ModifyRequest modifyRequest = Requests.newModifyRequest(entryDN);
        final KeyValuePair pair = new KeyValuePair();
        final List<ByteString> attributeValues = new ArrayList<>();
        boolean schemaValidationFailure = false;
        final List<LocalizableMessage> schemaErrors = new LinkedList<>();

        while (record.iterator.hasNext()) {
            String ldifLine = readLDIFRecordKeyValuePair(record, pair, false);
            if (pair.key == null) {
                throw DecodeException.error(
                        ERR_LDIF_MALFORMED_MODIFICATION_TYPE.get(record.lineNumber, entryDN, ldifLine));
            }

            final String changeType = toLowerCase(pair.key);

            ModificationType modType;
            if ("add".equals(changeType)) {
                modType = ModificationType.ADD;
            } else if ("delete".equals(changeType)) {
                modType = ModificationType.DELETE;
            } else if ("replace".equals(changeType)) {
                modType = ModificationType.REPLACE;
            } else if ("increment".equals(changeType)) {
                modType = ModificationType.INCREMENT;
            } else {
                throw DecodeException.error(
                        ERR_LDIF_BAD_MODIFICATION_TYPE.get(record.lineNumber, entryDN, pair.key));
            }

            AttributeDescription attributeDescription;
            try {
                attributeDescription = AttributeDescription.valueOf(pair.value, schema);
            } catch (final UnknownSchemaElementException e) {
                final LocalizableMessage message =
                        ERR_LDIF_UNKNOWN_ATTRIBUTE_TYPE.get(record.lineNumber, entryDN, pair.value);
                switch (schemaValidationPolicy.checkAttributesAndObjectClasses()) {
                case REJECT:
                    schemaValidationFailure = true;
                    schemaErrors.add(message);
                    continue;
                case WARN:
                    schemaErrors.add(message);
                    continue;
                default: // Ignore
                    /*
                     * This should not happen: we should be using a non-strict
                     * schema for this policy.
                     */
                    throw new IllegalStateException("Schema is not consistent with policy", e);
                }
            } catch (final LocalizedIllegalArgumentException e) {
                throw DecodeException.error(
                        ERR_LDIF_MALFORMED_ATTRIBUTE_NAME.get(record.lineNumber, entryDN, pair.value));
            }

            /*
             * Skip the attribute if requested before performing any schema
             * checking: the attribute may have been excluded because it is
             * known to violate the schema.
             */
            if (isAttributeExcluded(attributeDescription)) {
                continue;
            }

            final Syntax syntax = attributeDescription.getAttributeType().getSyntax();

            // Ensure that the binary option is present if required.
            if (!syntax.isBEREncodingRequired()) {
                if (schemaValidationPolicy.checkAttributeValues().needsChecking()
                        && attributeDescription.hasOption("binary")) {
                    final LocalizableMessage message =
                            ERR_LDIF_UNEXPECTED_BINARY_OPTION.get(record.lineNumber, entryDN, pair.value);
                    if (schemaValidationPolicy.checkAttributeValues().isReject()) {
                        schemaValidationFailure = true;
                    }
                    schemaErrors.add(message);
                    continue;
                }
            } else {
                attributeDescription = attributeDescription.withOption("binary");
            }

            /*
             * Now go through the rest of the attributes until the "-" line is
             * reached.
             */
            attributeValues.clear();
            while (record.iterator.hasNext()) {
                ldifLine = record.iterator.next();
                if ("-".equals(ldifLine)) {
                    break;
                }

                // Parse the attribute description.
                final int colonPos = parseColonPosition(record, ldifLine);
                final String attrDescr = ldifLine.substring(0, colonPos);

                AttributeDescription attributeDescription2;
                try {
                    attributeDescription2 = AttributeDescription.valueOf(attrDescr, schema);
                } catch (final LocalizedIllegalArgumentException e) {
                    /*
                     * No need to catch schema exception here because it implies
                     * that the attribute name is wrong and the record is
                     * malformed.
                     */
                    throw DecodeException.error(
                            ERR_LDIF_MALFORMED_ATTRIBUTE_NAME.get(record.lineNumber, entryDN, attrDescr));
                }

                // Ensure that the binary option is present if required.
                if (attributeDescription.getAttributeType().getSyntax().isBEREncodingRequired()) {
                    attributeDescription2 = attributeDescription2.withOption("binary");
                }

                if (!attributeDescription2.equals(attributeDescription)) {
                    // Malformed record.
                    throw DecodeException.error(ERR_LDIF_ATTRIBUTE_NAME_MISMATCH.get(
                            record.lineNumber, entryDN, attributeDescription2, attributeDescription));
                }

                // Parse the attribute value and check it if needed.
                final ByteString value =
                        parseSingleValue(record, ldifLine, entryDN, colonPos, attrDescr);
                if (schemaValidationPolicy.checkAttributeValues().needsChecking()) {
                    final LocalizableMessageBuilder builder = new LocalizableMessageBuilder();
                    if (!syntax.valueIsAcceptable(value, builder)) {
                        /*
                         * Just log a message, but don't skip the value since
                         * this could change the semantics of the modification
                         * (e.g. if all values in a delete are skipped then this
                         * implies that the whole attribute should be removed).
                         */
                        if (schemaValidationPolicy.checkAttributeValues().isReject()) {
                            schemaValidationFailure = true;
                        }
                        schemaErrors.add(builder.toMessage());
                    }
                }
                attributeValues.add(value);
            }

            final Modification change =
                    new Modification(modType, new LinkedAttribute(attributeDescription,
                            attributeValues));
            modifyRequest.addModification(change);
        }

        if (schemaValidationFailure) {
            handleSchemaValidationFailure(record, schemaErrors);
            return null;
        }

        if (!schemaErrors.isEmpty()) {
            handleSchemaValidationWarning(record, schemaErrors);
        }

        return modifyRequest;
    }

    private ChangeRecord parseModifyDNChangeRecordEntry(final DN entryDN, final LDIFRecord record)
            throws DecodeException {
        // Parse the newrdn.
        if (!record.iterator.hasNext()) {
            throw DecodeException.error(ERR_LDIF_NO_NEW_RDN.get(record.lineNumber, entryDN));
        }

        final KeyValuePair pair = new KeyValuePair();
        String ldifLine = readLDIFRecordKeyValuePair(record, pair, true);

        if (pair.key == null || !"newrdn".equals(toLowerCase(pair.key))) {
            throw DecodeException.error(
                    ERR_LDIF_MALFORMED_NEW_RDN.get(record.lineNumber, entryDN, ldifLine));
        }

        final ModifyDNRequest modifyDNRequest;
        try {
            final RDN newRDN = RDN.valueOf(pair.value, schema);
            modifyDNRequest = Requests.newModifyDNRequest(entryDN, newRDN);
        } catch (final LocalizedIllegalArgumentException e) {
            throw DecodeException.error(
                    ERR_LDIF_MALFORMED_NEW_RDN.get(record.lineNumber, entryDN, pair.value));
        }

        // Parse the deleteoldrdn.
        if (!record.iterator.hasNext()) {
            final LocalizableMessage message =
                    ERR_LDIF_NO_DELETE_OLD_RDN.get(record.lineNumber, entryDN.toString());
            throw DecodeException.error(message);
        }

        ldifLine = readLDIFRecordKeyValuePair(record, pair, true);
        if (pair.key == null || !"deleteoldrdn".equals(toLowerCase(pair.key))) {
            final LocalizableMessage message =
                    ERR_LDIF_MALFORMED_DELETE_OLD_RDN.get(record.lineNumber, entryDN.toString(),
                            ldifLine);
            throw DecodeException.error(message);
        }

        final String delStr = toLowerCase(pair.value);
        if ("false".equals(delStr) || "no".equals(delStr) || "0".equals(delStr)) {
            modifyDNRequest.setDeleteOldRDN(false);
        } else if ("true".equals(delStr) || "yes".equals(delStr) || "1".equals(delStr)) {
            modifyDNRequest.setDeleteOldRDN(true);
        } else {
            final LocalizableMessage message =
                    ERR_LDIF_MALFORMED_DELETE_OLD_RDN.get(record.lineNumber, entryDN.toString(),
                            pair.value);
            throw DecodeException.error(message);
        }

        // Parse the newsuperior if present.
        if (record.iterator.hasNext()) {
            ldifLine = readLDIFRecordKeyValuePair(record, pair, true);
            if (pair.key == null || !"newsuperior".equals(toLowerCase(pair.key))) {
                throw DecodeException.error(
                        ERR_LDIF_MALFORMED_NEW_SUPERIOR.get(record.lineNumber, entryDN, ldifLine));
            }

            try {
                final DN newSuperiorDN = DN.valueOf(pair.value, schema);
                modifyDNRequest.setNewSuperior(newSuperiorDN.toString());
            } catch (final LocalizedIllegalArgumentException e) {
                final LocalizableMessage message =
                        ERR_LDIF_MALFORMED_NEW_SUPERIOR.get(record.lineNumber, entryDN.toString(),
                                pair.value);
                throw DecodeException.error(message);
            }
        }

        return modifyDNRequest;
    }

}
