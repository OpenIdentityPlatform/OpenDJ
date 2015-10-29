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

import static com.forgerock.opendj.util.StaticUtils.toLowerCase;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_LDIF_COULD_NOT_BASE64_DECODE_ATTR;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_LDIF_COULD_NOT_BASE64_DECODE_DN;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_LDIF_INVALID_DN;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_LDIF_INVALID_LEADING_SPACE;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_LDIF_INVALID_URL;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_LDIF_MALFORMED_ATTRIBUTE_NAME;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_LDIF_MULTI_VALUED_SINGLE_VALUED_ATTRIBUTE;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_LDIF_NO_ATTR_NAME;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_LDIF_NO_DN;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_LDIF_UNEXPECTED_BINARY_OPTION;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_LDIF_UNKNOWN_ATTRIBUTE_TYPE;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_LDIF_URL_IO_ERROR;
import static com.forgerock.opendj.ldap.CoreMessages.WARN_LDIF_DUPLICATE_ATTRIBUTE_VALUE;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaValidationPolicy;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.forgerock.opendj.ldap.schema.UnknownSchemaElementException;

import org.forgerock.util.Reject;

/**
 * Common LDIF reader functionality.
 */
abstract class AbstractLDIFReader extends AbstractLDIFStream {
    static final class KeyValuePair {
        String key;
        String value;
    }

    /**
     * LDIF reader implementation interface.
     */
    interface LDIFReaderImpl {

        /**
         * Closes any resources associated with this LDIF reader implementation.
         *
         * @throws IOException
         *             If an error occurs while closing.
         */
        void close() throws IOException;

        /**
         * Reads the next line of LDIF from the underlying LDIF source.
         * Implementations must remove trailing line delimiters.
         *
         * @return The next line of LDIF, or {@code null} if the end of the LDIF
         *         source has been reached.
         * @throws IOException
         *             If an error occurs while reading from the LDIF source.
         */
        String readLine() throws IOException;
    }

    static final class LDIFRecord {
        final Iterator<String> iterator;
        final LinkedList<String> ldifLines;
        final long lineNumber;

        private LDIFRecord(final long lineNumber, final LinkedList<String> ldifLines) {
            this.lineNumber = lineNumber;
            this.ldifLines = ldifLines;
            this.iterator = ldifLines.iterator();
        }
    }

    /**
     * LDIF output stream writer implementation.
     */
    private static final class LDIFReaderInputStreamImpl implements LDIFReaderImpl {
        private BufferedReader reader;

        LDIFReaderInputStreamImpl(final Reader reader) {
            this.reader =
                    reader instanceof BufferedReader ? (BufferedReader) reader
                            : new BufferedReader(reader);
        }

        @Override
        public void close() throws IOException {
            if (reader != null) {
                reader.close();
                reader = null;
            }
        }

        @Override
        public String readLine() throws IOException {
            String line = null;
            if (reader != null) {
                line = reader.readLine();
                if (line == null) {
                    // Automatically close.
                    close();
                }
            }
            return line;
        }
    }

    /**
     * LDIF output stream writer implementation.
     */
    private static final class LDIFReaderListImpl implements LDIFReaderImpl {
        private final Iterator<String> iterator;

        LDIFReaderListImpl(final List<String> ldifLines) {
            this.iterator = ldifLines.iterator();
        }

        @Override
        public void close() throws IOException {
            // Nothing to do.
        }

        @Override
        public String readLine() throws IOException {
            if (iterator.hasNext()) {
                return iterator.next();
            } else {
                return null;
            }
        }
    }

    RejectedLDIFListener rejectedRecordListener = RejectedLDIFListener.FAIL_FAST;
    Schema schema = Schema.getDefaultSchema().asNonStrictSchema();
    SchemaValidationPolicy schemaValidationPolicy = SchemaValidationPolicy.ignoreAll();
    private final LDIFReaderImpl impl;
    private long lineNumber;

    AbstractLDIFReader(final InputStream in) {
        this(new InputStreamReader(in));
    }

    AbstractLDIFReader(final List<String> ldifLines) {
        Reject.ifNull(ldifLines);
        this.impl = new LDIFReaderListImpl(ldifLines);
    }

    AbstractLDIFReader(final Reader reader) {
        this.impl = new LDIFReaderInputStreamImpl(reader);
    }

    final void close0() throws IOException {
        impl.close();
    }

    final void handleMalformedRecord(final LDIFRecord record, final LocalizableMessage message)
            throws DecodeException {
        rejectedRecordListener.handleMalformedRecord(record.lineNumber, record.ldifLines, message);
    }

    final void handleSchemaValidationFailure(final LDIFRecord record,
            final List<LocalizableMessage> messages) throws DecodeException {
        rejectedRecordListener.handleSchemaValidationFailure(record.lineNumber, record.ldifLines,
                messages);
    }

    final void handleSchemaValidationWarning(final LDIFRecord record,
            final List<LocalizableMessage> messages) throws DecodeException {
        rejectedRecordListener.handleSchemaValidationWarning(record.lineNumber, record.ldifLines,
                messages);
    }

    final void handleSkippedRecord(final LDIFRecord record, final LocalizableMessage message)
            throws DecodeException {
        rejectedRecordListener.handleSkippedRecord(record.lineNumber, record.ldifLines, message);
    }

    final int parseColonPosition(final LDIFRecord record, final String ldifLine)
            throws DecodeException {
        final int colonPos = ldifLine.indexOf(":");
        if (colonPos <= 0) {
            final LocalizableMessage message =
                    ERR_LDIF_NO_ATTR_NAME.get(record.lineNumber, ldifLine);
            throw DecodeException.error(message);
        }
        return colonPos;
    }

    final ByteString parseSingleValue(final LDIFRecord record, final String ldifLine,
            final DN entryDN, final int colonPos, final String attrName) throws DecodeException {

        /*
         * Look at the character immediately after the colon. If there is none,
         * then assume an attribute with an empty value. If it is another colon,
         * then the value must be base64-encoded. If it is a less-than sign,
         * then assume that it is a URL. Otherwise, it is a regular value.
         */
        final int length = ldifLine.length();
        ByteString value;
        if (colonPos == length - 1) {
            value = ByteString.empty();
        } else {
            final char c = ldifLine.charAt(colonPos + 1);
            if (c == ':') {
                /*
                 * The value is base64-encoded. Find the first non-blank
                 * character, take the rest of the line, and base64-decode it.
                 */
                int pos = colonPos + 2;
                while (pos < length && ldifLine.charAt(pos) == ' ') {
                    pos++;
                }

                try {
                    value = ByteString.valueOfBase64(ldifLine.substring(pos));
                } catch (final LocalizedIllegalArgumentException e) {
                    /*
                     * The value did not have a valid base64-encoding.
                     */
                    final LocalizableMessage message =
                            ERR_LDIF_COULD_NOT_BASE64_DECODE_ATTR.get(entryDN.toString(),
                                    record.lineNumber, ldifLine, e.getMessageObject());
                    throw DecodeException.error(message);
                }
            } else if (c == '<') {
                /*
                 * Find the first non-blank character, decode the rest of the
                 * line as a URL, and read its contents.
                 */
                int pos = colonPos + 2;
                while (pos < length && ldifLine.charAt(pos) == ' ') {
                    pos++;
                }

                URL contentURL;
                try {
                    contentURL = new URL(ldifLine.substring(pos));
                } catch (final Exception e) {
                    // The URL was malformed or had an invalid protocol.
                    final LocalizableMessage message =
                            ERR_LDIF_INVALID_URL.get(entryDN.toString(), record.lineNumber,
                                    attrName, String.valueOf(e));
                    throw DecodeException.error(message);
                }

                InputStream inputStream = null;
                ByteStringBuilder builder = null;
                try {
                    builder = new ByteStringBuilder();
                    inputStream = contentURL.openConnection().getInputStream();

                    int bytesRead;
                    final byte[] buffer = new byte[4096];
                    while ((bytesRead = inputStream.read(buffer)) > 0) {
                        builder.appendBytes(buffer, 0, bytesRead);
                    }

                    value = builder.toByteString();
                } catch (final Exception e) {
                    /*
                     * We were unable to read the contents of that URL for some
                     * reason.
                     */
                    final LocalizableMessage message =
                            ERR_LDIF_URL_IO_ERROR.get(entryDN.toString(), record.lineNumber,
                                    attrName, String.valueOf(contentURL), String.valueOf(e));
                    throw DecodeException.error(message);
                } finally {
                    if (inputStream != null) {
                        try {
                            inputStream.close();
                        } catch (final Exception e) {
                            // Ignore.
                        }
                    }
                }
            } else {
                /*
                 * The rest of the line should be the value. Skip over any
                 * spaces and take the rest of the line as the value.
                 */
                int pos = colonPos + 1;
                while (pos < length && ldifLine.charAt(pos) == ' ') {
                    pos++;
                }

                value = ByteString.valueOfUtf8(ldifLine.substring(pos));
            }
        }
        return value;
    }

    final LDIFRecord readLDIFRecord() throws IOException {
        // Read the entry lines into a buffer.
        final StringBuilder lastLineBuilder = new StringBuilder();
        final LinkedList<String> ldifLines = new LinkedList<>();
        long recordLineNumber = 0;
        final int stateStart = 0;
        final int stateStartCommentLine = 1;
        final int stateGotLDIFLine = 2;
        final int stateGotCommentLine = 3;
        final int appendingLDIFLine = 4;
        int state = stateStart;

        while (true) {
            final String line = readLine();

            switch (state) {
            case stateStart:
                if (line == null) {
                    // We have reached the end of the LDIF source.
                    return null;
                } else if (line.length() == 0) {
                    // Skip leading blank lines.
                } else if (line.charAt(0) == '#') {
                    // This is a comment at the start of the LDIF record.
                    state = stateStartCommentLine;
                } else if (isContinuationLine(line)) {
                    /*
                     * Fatal: got a continuation line at the start of the
                     * record.
                     */
                    final LocalizableMessage message =
                            ERR_LDIF_INVALID_LEADING_SPACE.get(lineNumber, line);
                    throw DecodeException.fatalError(message);
                } else {
                    // Got the first line of LDIF.
                    ldifLines.add(line);
                    recordLineNumber = lineNumber;
                    state = stateGotLDIFLine;
                }
                break;
            case stateStartCommentLine:
                if (line == null) {
                    // We have reached the end of the LDIF source.
                    return null;
                } else if (line.length() == 0) {
                    // Skip leading blank lines and comments.
                    state = stateStart;
                } else if (line.charAt(0) == '#') {
                    // This is another comment at the start of the LDIF record.
                } else if (isContinuationLine(line)) {
                    // Skip comment continuation lines.
                } else {
                    // Got the first line of LDIF.
                    ldifLines.add(line);
                    recordLineNumber = lineNumber;
                    state = stateGotLDIFLine;
                }
                break;
            case stateGotLDIFLine:
                if (line == null) {
                    // We have reached the end of the LDIF source.
                    return new LDIFRecord(recordLineNumber, ldifLines);
                } else if (line.length() == 0) {
                    // We have reached the end of the LDIF record.
                    return new LDIFRecord(recordLineNumber, ldifLines);
                } else if (line.charAt(0) == '#') {
                    // This is a comment.
                    state = stateGotCommentLine;
                } else if (isContinuationLine(line)) {
                    // Got a continuation line for the previous line.
                    lastLineBuilder.setLength(0);
                    lastLineBuilder.append(ldifLines.removeLast());
                    lastLineBuilder.append(line.substring(1));
                    state = appendingLDIFLine;
                } else {
                    // Got the next line of LDIF.
                    ldifLines.add(line);
                    state = stateGotLDIFLine;
                }
                break;
            case stateGotCommentLine:
                if (line == null) {
                    // We have reached the end of the LDIF source.
                    return new LDIFRecord(recordLineNumber, ldifLines);
                } else if (line.length() == 0) {
                    // We have reached the end of the LDIF record.
                    return new LDIFRecord(recordLineNumber, ldifLines);
                } else if (line.charAt(0) == '#') {
                    // This is another comment.
                    state = stateGotCommentLine;
                } else if (isContinuationLine(line)) {
                    // Skip comment continuation lines.
                } else {
                    // Got the next line of LDIF.
                    ldifLines.add(line);
                    state = stateGotLDIFLine;
                }
                break;
            default: // appendingLDIFLine:
                if (line == null) {
                    // We have reached the end of the LDIF source.
                    ldifLines.add(lastLineBuilder.toString());
                    return new LDIFRecord(recordLineNumber, ldifLines);
                } else if (line.length() == 0) {
                    // We have reached the end of the LDIF record.
                    ldifLines.add(lastLineBuilder.toString());
                    return new LDIFRecord(recordLineNumber, ldifLines);
                } else if (line.charAt(0) == '#') {
                    // This is a comment.
                    ldifLines.add(lastLineBuilder.toString());
                    state = stateGotCommentLine;
                } else if (isContinuationLine(line)) {
                    // Got another continuation line for the previous line.
                    lastLineBuilder.append(line.substring(1));
                } else {
                    // Got the next line of LDIF.
                    ldifLines.add(lastLineBuilder.toString());
                    ldifLines.add(line);
                    state = stateGotLDIFLine;
                }
                break;
            }
        }
    }

    final boolean readLDIFRecordAttributeValue(final LDIFRecord record, final String ldifLine,
            final Entry entry, final List<LocalizableMessage> schemaErrors) throws DecodeException {
        // Parse the attribute description.
        final int colonPos = parseColonPosition(record, ldifLine);
        final String attrDescr = ldifLine.substring(0, colonPos);

        AttributeDescription attributeDescription;
        try {
            attributeDescription = AttributeDescription.valueOf(attrDescr, schema);
        } catch (final UnknownSchemaElementException e) {
            final LocalizableMessage message =
                    ERR_LDIF_UNKNOWN_ATTRIBUTE_TYPE.get(record.lineNumber, entry.getName()
                            .toString(), attrDescr);
            switch (schemaValidationPolicy.checkAttributesAndObjectClasses()) {
            case REJECT:
                schemaErrors.add(message);
                return false;
            case WARN:
                schemaErrors.add(message);
                return true;
            default: // Ignore
                /*
                 * This should not happen: we should be using a non-strict
                 * schema for this policy.
                 */
                throw new IllegalStateException("Schema is not consistent with policy", e);
            }
        } catch (final LocalizedIllegalArgumentException e) {
            final LocalizableMessage message =
                    ERR_LDIF_MALFORMED_ATTRIBUTE_NAME.get(record.lineNumber, entry.getName()
                            .toString(), attrDescr);
            throw DecodeException.error(message);
        }

        // Now parse the attribute value.
        final ByteString value =
                parseSingleValue(record, ldifLine, entry.getName(), colonPos, attrDescr);

        /*
         * Skip the attribute if requested before performing any schema
         * checking: the attribute may have been excluded because it is known to
         * violate the schema.
         */
        if (isAttributeExcluded(attributeDescription)) {
            return true;
        }

        final Syntax syntax = attributeDescription.getAttributeType().getSyntax();

        // Ensure that the binary option is present if required.
        if (!syntax.isBEREncodingRequired()) {
            if (schemaValidationPolicy.checkAttributeValues().needsChecking()
                    && attributeDescription.hasOption("binary")) {
                final LocalizableMessage message =
                        ERR_LDIF_UNEXPECTED_BINARY_OPTION.get(record.lineNumber, entry.getName()
                                .toString(), attrDescr);
                schemaErrors.add(message);
                return !schemaValidationPolicy.checkAttributeValues().isReject();
            }
        } else {
            attributeDescription = attributeDescription.withOption("binary");
        }

        final boolean checkAttributeValues =
                schemaValidationPolicy.checkAttributeValues().needsChecking();
        if (checkAttributeValues) {
            final LocalizableMessageBuilder builder = new LocalizableMessageBuilder();
            if (!syntax.valueIsAcceptable(value, builder)) {
                schemaErrors.add(builder.toMessage());
                if (schemaValidationPolicy.checkAttributeValues().isReject()) {
                    return false;
                }
            }
        }

        Attribute attribute = entry.getAttribute(attributeDescription);
        if (attribute == null) {
            attribute = new LinkedAttribute(attributeDescription, value);
            entry.addAttribute(attribute);
        } else if (checkAttributeValues) {
            if (!attribute.add(value)) {
                final LocalizableMessage message =
                        WARN_LDIF_DUPLICATE_ATTRIBUTE_VALUE.get(record.lineNumber, entry.getName()
                                .toString(), attrDescr, value.toString());
                schemaErrors.add(message);
                if (schemaValidationPolicy.checkAttributeValues().isReject()) {
                    return false;
                }
            } else if (attributeDescription.getAttributeType().isSingleValue()) {
                final LocalizableMessage message =
                        ERR_LDIF_MULTI_VALUED_SINGLE_VALUED_ATTRIBUTE.get(record.lineNumber, entry
                                .getName().toString(), attrDescr);
                schemaErrors.add(message);
                if (schemaValidationPolicy.checkAttributeValues().isReject()) {
                    return false;
                }
            }
        } else {
            attribute.add(value);
        }

        return true;
    }

    final DN readLDIFRecordDN(final LDIFRecord record) throws DecodeException {
        String ldifLine = record.iterator.next();
        int colonPos = ldifLine.indexOf(":");
        if (colonPos <= 0) {
            throw DecodeException.error(ERR_LDIF_NO_ATTR_NAME.get(record.lineNumber, ldifLine));
        }

        String attrName = toLowerCase(ldifLine.substring(0, colonPos));
        if ("version".equals(attrName)) {
            // This is the version line, try the next line if there is one.
            if (!record.iterator.hasNext()) {
                return null;
            }

            ldifLine = record.iterator.next();
            colonPos = ldifLine.indexOf(":");
            if (colonPos <= 0) {
                throw DecodeException.error(ERR_LDIF_NO_ATTR_NAME.get(record.lineNumber, ldifLine));
            }

            attrName = toLowerCase(ldifLine.substring(0, colonPos));
        }

        if (!"dn".equals(attrName)) {
            throw DecodeException.error(ERR_LDIF_NO_DN.get(record.lineNumber, ldifLine));
        }

        /*
         * Look at the character immediately after the colon. If there is none,
         * then assume the null DN. If it is another colon, then the DN must be
         * base64-encoded. Otherwise, it may be one or more spaces.
         */
        final int length = ldifLine.length();
        if (colonPos == length - 1) {
            return DN.rootDN();
        }

        String dnString = null;

        if (ldifLine.charAt(colonPos + 1) == ':') {
            /*
             * The DN is base64-encoded. Find the first non-blank character and
             * take the rest of the line and base64-decode it.
             */
            int pos = colonPos + 2;
            while (pos < length && ldifLine.charAt(pos) == ' ') {
                pos++;
            }

            final String base64DN = ldifLine.substring(pos);
            try {
                dnString = ByteString.valueOfBase64(base64DN).toString();
            } catch (final LocalizedIllegalArgumentException e) {
                // The value did not have a valid base64-encoding.
                final LocalizableMessage message =
                        ERR_LDIF_COULD_NOT_BASE64_DECODE_DN.get(record.lineNumber, ldifLine, e
                                .getMessageObject());
                throw DecodeException.error(message);
            }
        } else {
            /*
             * The rest of the value should be the DN. Skip over any spaces and
             * attempt to decode the rest of the line as the DN.
             */
            int pos = colonPos + 1;
            while (pos < length && ldifLine.charAt(pos) == ' ') {
                pos++;
            }

            dnString = ldifLine.substring(pos);
        }

        try {
            return DN.valueOf(dnString, schema);
        } catch (final LocalizedIllegalArgumentException e) {
            final LocalizableMessage message =
                    ERR_LDIF_INVALID_DN.get(record.lineNumber, ldifLine, e.getMessageObject());
            throw DecodeException.error(message);
        }
    }

    final String readLDIFRecordKeyValuePair(final LDIFRecord record, final KeyValuePair pair,
            final boolean allowBase64) {
        final String ldifLine = record.iterator.next();
        final int colonPos = ldifLine.indexOf(":");
        if (colonPos <= 0) {
            pair.key = null;
            return ldifLine;
        }
        pair.key = ldifLine.substring(0, colonPos);

        /*
         * Look at the character immediately after the colon. If there is none,
         * then no value was specified. Throw an exception
         */
        final int length = ldifLine.length();
        if (colonPos == length - 1) {
            pair.key = null;
            return ldifLine;
        }

        if (allowBase64 && ldifLine.charAt(colonPos + 1) == ':') {
            /*
             * The value is base64-encoded. Find the first non-blank character,
             * take the rest of the line, and base64-decode it.
             */
            int pos = colonPos + 2;
            while (pos < length && ldifLine.charAt(pos) == ' ') {
                pos++;
            }

            try {
                pair.value = ByteString.valueOfBase64(ldifLine.substring(pos)).toString();
            } catch (final LocalizedIllegalArgumentException e) {
                pair.key = null;
                return ldifLine;
            }
        } else {
            /*
             * The rest of the value should be the changetype. Skip over any
             * spaces and attempt to decode the rest of the line as the
             * changetype string.
             */
            int pos = colonPos + 1;
            while (pos < length && ldifLine.charAt(pos) == ' ') {
                pos++;
            }

            pair.value = ldifLine.substring(pos);
        }

        return ldifLine;
    }

    /**
     * Determines whether the provided line is a continuation line. Note that
     * while RFC 2849 technically only allows a space in this position, both
     * OpenLDAP and the Sun Java System Directory Server allow a tab as well, so
     * we will too for compatibility reasons. See issue #852 for details.
     */
    private boolean isContinuationLine(final String line) {
        return line.charAt(0) == ' ' || line.charAt(0) == '\t';
    }

    private String readLine() throws IOException {
        final String line = impl.readLine();
        if (line != null) {
            lineNumber++;
        }
        return line;
    }
}
