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
 *      Copyright 2013-2015 ForgeRock AS.
 */

package org.forgerock.opendj.io;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.FilterVisitor;
import org.forgerock.opendj.ldap.schema.Schema;

/**
 * This class contains various static utility methods encoding and decoding LDAP
 * protocol elements.
 *
 * @see LDAPReader
 * @see LDAPWriter
 */
public final class LDAP {
    // @Checkstyle:ignore AvoidNestedBlocks

    /**
     * The OID for the Kerberos V GSSAPI mechanism.
     */
    public static final String OID_GSSAPI_KERBEROS_V = "1.2.840.113554.1.2.2";

    /**
     * The OID for the LDAP notice of disconnection extended operation.
     */
    public static final String OID_NOTICE_OF_DISCONNECTION = "1.3.6.1.4.1.1466.20036";

    /**
     * The protocol op type for abandon requests.
     */
    public static final byte OP_TYPE_ABANDON_REQUEST = 0x50;

    /**
     * The protocol op type for add requests.
     */
    public static final byte OP_TYPE_ADD_REQUEST = 0x68;

    /**
     * The protocol op type for add responses.
     */
    public static final byte OP_TYPE_ADD_RESPONSE = 0x69;

    /**
     * The protocol op type for bind requests.
     */
    public static final byte OP_TYPE_BIND_REQUEST = 0x60;

    /**
     * The protocol op type for bind responses.
     */
    public static final byte OP_TYPE_BIND_RESPONSE = 0x61;

    /**
     * The protocol op type for compare requests.
     */
    public static final byte OP_TYPE_COMPARE_REQUEST = 0x6E;

    /**
     * The protocol op type for compare responses.
     */
    public static final byte OP_TYPE_COMPARE_RESPONSE = 0x6F;

    /**
     * The protocol op type for delete requests.
     */
    public static final byte OP_TYPE_DELETE_REQUEST = 0x4A;

    /**
     * The protocol op type for delete responses.
     */
    public static final byte OP_TYPE_DELETE_RESPONSE = 0x6B;

    /**
     * The protocol op type for extended requests.
     */
    public static final byte OP_TYPE_EXTENDED_REQUEST = 0x77;

    /**
     * The protocol op type for extended responses.
     */
    public static final byte OP_TYPE_EXTENDED_RESPONSE = 0x78;

    /**
     * The protocol op type for intermediate responses.
     */
    public static final byte OP_TYPE_INTERMEDIATE_RESPONSE = 0x79;

    /**
     * The protocol op type for modify DN requests.
     */
    public static final byte OP_TYPE_MODIFY_DN_REQUEST = 0x6C;

    /**
     * The protocol op type for modify DN responses.
     */
    public static final byte OP_TYPE_MODIFY_DN_RESPONSE = 0x6D;

    /**
     * The protocol op type for modify requests.
     */
    public static final byte OP_TYPE_MODIFY_REQUEST = 0x66;

    /**
     * The protocol op type for modify responses.
     */
    public static final byte OP_TYPE_MODIFY_RESPONSE = 0x67;
    /**
     * The protocol op type for search requests.
     */
    public static final byte OP_TYPE_SEARCH_REQUEST = 0x63;
    /**
     * The protocol op type for search result done elements.
     */
    public static final byte OP_TYPE_SEARCH_RESULT_DONE = 0x65;
    /**
     * The protocol op type for search result entries.
     */
    public static final byte OP_TYPE_SEARCH_RESULT_ENTRY = 0x64;
    /**
     * The protocol op type for search result references.
     */
    public static final byte OP_TYPE_SEARCH_RESULT_REFERENCE = 0x73;
    /**
     * The protocol op type for unbind requests.
     */
    public static final byte OP_TYPE_UNBIND_REQUEST = 0x42;
    /**
     * The BER type to use for the AuthenticationChoice element in a bind
     * request when SASL authentication is to be used.
     */
    public static final byte TYPE_AUTHENTICATION_SASL = (byte) 0xA3;
    /**
     * The BER type to use for the AuthenticationChoice element in a bind
     * request when simple authentication is to be used.
     */
    public static final byte TYPE_AUTHENTICATION_SIMPLE = (byte) 0x80;
    /**
     * The BER type to use for encoding the sequence of controls in an LDAP
     * message.
     */
    public static final byte TYPE_CONTROL_SEQUENCE = (byte) 0xA0;
    /**
     * The BER type to use for the OID of an extended request.
     */
    public static final byte TYPE_EXTENDED_REQUEST_OID = (byte) 0x80;
    /**
     * The BER type to use for the value of an extended request.
     */
    public static final byte TYPE_EXTENDED_REQUEST_VALUE = (byte) 0x81;
    /**
     * The BER type to use for the OID of an extended response.
     */
    public static final byte TYPE_EXTENDED_RESPONSE_OID = (byte) 0x8A;
    /**
     * The BER type to use for the value of an extended response.
     */
    public static final byte TYPE_EXTENDED_RESPONSE_VALUE = (byte) 0x8B;
    /**
     * The BER type to use for AND filter components.
     */
    public static final byte TYPE_FILTER_AND = (byte) 0xA0;
    /**
     * The BER type to use for approximate filter components.
     */
    public static final byte TYPE_FILTER_APPROXIMATE = (byte) 0xA8;
    /**
     * The BER type to use for equality filter components.
     */
    public static final byte TYPE_FILTER_EQUALITY = (byte) 0xA3;
    /**
     * The BER type to use for extensible matching filter components.
     */
    public static final byte TYPE_FILTER_EXTENSIBLE_MATCH = (byte) 0xA9;
    /**
     * The BER type to use for greater than or equal to filter components.
     */
    public static final byte TYPE_FILTER_GREATER_OR_EQUAL = (byte) 0xA5;
    /**
     * The BER type to use for less than or equal to filter components.
     */
    public static final byte TYPE_FILTER_LESS_OR_EQUAL = (byte) 0xA6;
    /**
     * The BER type to use for NOT filter components.
     */
    public static final byte TYPE_FILTER_NOT = (byte) 0xA2;
    /**
     * The BER type to use for OR filter components.
     */
    public static final byte TYPE_FILTER_OR = (byte) 0xA1;
    /**
     * The BER type to use for presence filter components.
     */
    public static final byte TYPE_FILTER_PRESENCE = (byte) 0x87;
    /**
     * The BER type to use for substring filter components.
     */
    public static final byte TYPE_FILTER_SUBSTRING = (byte) 0xA4;
    /**
     * The BER type to use for the OID of an intermediate response message.
     */
    public static final byte TYPE_INTERMEDIATE_RESPONSE_OID = (byte) 0x80;
    /**
     * The BER type to use for the value of an intermediate response message.
     */
    public static final byte TYPE_INTERMEDIATE_RESPONSE_VALUE = (byte) 0x81;
    /**
     * The BER type to use for the DN attributes flag in a matching rule
     * assertion.
     */
    public static final byte TYPE_MATCHING_RULE_DN_ATTRIBUTES = (byte) 0x84;
    /**
     * The BER type to use for the matching rule OID in a matching rule
     * assertion.
     */
    public static final byte TYPE_MATCHING_RULE_ID = (byte) 0x81;
    /**
     * The BER type to use for the attribute type in a matching rule assertion.
     */
    public static final byte TYPE_MATCHING_RULE_TYPE = (byte) 0x82;
    /**
     * The BER type to use for the assertion value in a matching rule assertion.
     */
    public static final byte TYPE_MATCHING_RULE_VALUE = (byte) 0x83;
    /**
     * The BER type to use for the newSuperior component of a modify DN request.
     */
    public static final byte TYPE_MODIFY_DN_NEW_SUPERIOR = (byte) 0x80;
    /**
     * The BER type to use for encoding the sequence of referral URLs in an
     * LDAPResult element.
     */
    public static final byte TYPE_REFERRAL_SEQUENCE = (byte) 0xA3;
    /**
     * The BER type to use for the server SASL credentials in a bind response.
     */
    public static final byte TYPE_SERVER_SASL_CREDENTIALS = (byte) 0x87;
    /**
     * The BER type to use for the subAny component(s) of a substring filter.
     */
    public static final byte TYPE_SUBANY = (byte) 0x81;
    /**
     * The BER type to use for the subFinal components of a substring filter.
     */
    public static final byte TYPE_SUBFINAL = (byte) 0x82;
    /**
     * The BER type to use for the subInitial component of a substring filter.
     */
    public static final byte TYPE_SUBINITIAL = (byte) 0x80;
    private static final FilterVisitor<IOException, ASN1Writer> ASN1_ENCODER =
            new FilterVisitor<IOException, ASN1Writer>() {

                @Override
                public IOException visitAndFilter(final ASN1Writer writer,
                        final List<Filter> subFilters) {
                    try {
                        writer.writeStartSequence(LDAP.TYPE_FILTER_AND);
                        for (final Filter subFilter : subFilters) {
                            final IOException e = subFilter.accept(this, writer);
                            if (e != null) {
                                return e;
                            }
                        }
                        writer.writeEndSequence();
                        return null;
                    } catch (final IOException e) {
                        return e;
                    }
                }

                @Override
                public IOException visitApproxMatchFilter(final ASN1Writer writer,
                        final String attributeDescription, final ByteString assertionValue) {
                    try {
                        writer.writeStartSequence(LDAP.TYPE_FILTER_APPROXIMATE);
                        writer.writeOctetString(attributeDescription);
                        writer.writeOctetString(assertionValue);
                        writer.writeEndSequence();
                        return null;
                    } catch (final IOException e) {
                        return e;
                    }
                }

                @Override
                public IOException visitEqualityMatchFilter(final ASN1Writer writer,
                        final String attributeDescription, final ByteString assertionValue) {
                    try {
                        writer.writeStartSequence(LDAP.TYPE_FILTER_EQUALITY);
                        writer.writeOctetString(attributeDescription);
                        writer.writeOctetString(assertionValue);
                        writer.writeEndSequence();
                        return null;
                    } catch (final IOException e) {
                        return e;
                    }
                }

                @Override
                public IOException visitExtensibleMatchFilter(final ASN1Writer writer,
                        final String matchingRule, final String attributeDescription,
                        final ByteString assertionValue, final boolean dnAttributes) {
                    try {
                        writer.writeStartSequence(LDAP.TYPE_FILTER_EXTENSIBLE_MATCH);

                        if (matchingRule != null) {
                            writer.writeOctetString(LDAP.TYPE_MATCHING_RULE_ID, matchingRule);
                        }

                        if (attributeDescription != null) {
                            writer.writeOctetString(LDAP.TYPE_MATCHING_RULE_TYPE,
                                    attributeDescription);
                        }

                        writer.writeOctetString(LDAP.TYPE_MATCHING_RULE_VALUE, assertionValue);

                        if (dnAttributes) {
                            writer.writeBoolean(LDAP.TYPE_MATCHING_RULE_DN_ATTRIBUTES, true);
                        }

                        writer.writeEndSequence();
                        return null;
                    } catch (final IOException e) {
                        return e;
                    }
                }

                @Override
                public IOException visitGreaterOrEqualFilter(final ASN1Writer writer,
                        final String attributeDescription, final ByteString assertionValue) {
                    try {
                        writer.writeStartSequence(LDAP.TYPE_FILTER_GREATER_OR_EQUAL);
                        writer.writeOctetString(attributeDescription);
                        writer.writeOctetString(assertionValue);
                        writer.writeEndSequence();
                        return null;
                    } catch (final IOException e) {
                        return e;
                    }
                }

                @Override
                public IOException visitLessOrEqualFilter(final ASN1Writer writer,
                        final String attributeDescription, final ByteString assertionValue) {
                    try {
                        writer.writeStartSequence(LDAP.TYPE_FILTER_LESS_OR_EQUAL);
                        writer.writeOctetString(attributeDescription);
                        writer.writeOctetString(assertionValue);
                        writer.writeEndSequence();
                        return null;
                    } catch (final IOException e) {
                        return e;
                    }
                }

                @Override
                public IOException visitNotFilter(final ASN1Writer writer, final Filter subFilter) {
                    try {
                        writer.writeStartSequence(LDAP.TYPE_FILTER_NOT);
                        final IOException e = subFilter.accept(this, writer);
                        if (e != null) {
                            return e;
                        }
                        writer.writeEndSequence();
                        return null;
                    } catch (final IOException e) {
                        return e;
                    }
                }

                @Override
                public IOException visitOrFilter(final ASN1Writer writer,
                        final List<Filter> subFilters) {
                    try {
                        writer.writeStartSequence(LDAP.TYPE_FILTER_OR);
                        for (final Filter subFilter : subFilters) {
                            final IOException e = subFilter.accept(this, writer);
                            if (e != null) {
                                return e;
                            }
                        }
                        writer.writeEndSequence();
                        return null;
                    } catch (final IOException e) {
                        return e;
                    }
                }

                @Override
                public IOException visitPresentFilter(final ASN1Writer writer,
                        final String attributeDescription) {
                    try {
                        writer.writeOctetString(LDAP.TYPE_FILTER_PRESENCE, attributeDescription);
                        return null;
                    } catch (final IOException e) {
                        return e;
                    }
                }

                @Override
                public IOException visitSubstringsFilter(final ASN1Writer writer,
                        final String attributeDescription, final ByteString initialSubstring,
                        final List<ByteString> anySubstrings, final ByteString finalSubstring) {
                    try {
                        writer.writeStartSequence(LDAP.TYPE_FILTER_SUBSTRING);
                        writer.writeOctetString(attributeDescription);

                        writer.writeStartSequence();
                        if (initialSubstring != null) {
                            writer.writeOctetString(LDAP.TYPE_SUBINITIAL, initialSubstring);
                        }

                        for (final ByteSequence anySubstring : anySubstrings) {
                            writer.writeOctetString(LDAP.TYPE_SUBANY, anySubstring);
                        }

                        if (finalSubstring != null) {
                            writer.writeOctetString(LDAP.TYPE_SUBFINAL, finalSubstring);
                        }
                        writer.writeEndSequence();

                        writer.writeEndSequence();
                        return null;
                    } catch (final IOException e) {
                        return e;
                    }
                }

                @Override
                public IOException visitUnrecognizedFilter(final ASN1Writer writer,
                        final byte filterTag, final ByteString filterBytes) {
                    try {
                        writer.writeOctetString(filterTag, filterBytes);
                        return null;
                    } catch (final IOException e) {
                        return e;
                    }
                }
            };

    /**
     * Creates a new LDAP reader which will read LDAP messages from an ASN.1
     * reader using the provided decoding options.
     *
     * @param <R>
     *            The type of ASN.1 reader used for decoding elements.
     * @param asn1Reader
     *            The ASN.1 reader from which LDAP messages will be read.
     * @param options
     *            LDAP message decoding options.
     * @return A new LDAP reader which will read LDAP messages from an ASN.1
     *         reader using the provided decoding options.
     */
    public static <R extends ASN1Reader> LDAPReader<R> getReader(final R asn1Reader,
            final DecodeOptions options) {
        return new LDAPReader<>(asn1Reader, options);
    }

    /**
     * Creates a new LDAP writer which will write LDAP messages to the provided
     * ASN.1 writer.
     *
     * @param <W>
     *            The type of ASN.1 writer used for encoding elements.
     * @param asn1Writer
     *            The ASN.1 writer to which LDAP messages will be written.
     * @return A new LDAP writer which will write LDAP messages to the provided
     *         ASN.1 writer.
     */
    public static <W extends ASN1Writer> LDAPWriter<W> getWriter(final W asn1Writer) {
        return new LDAPWriter<>(asn1Writer);
    }

    /**
     * Reads the next ASN.1 element from the provided {@code ASN1Reader} as a
     * {@code Filter}.
     *
     * @param reader
     *            The {@code ASN1Reader} from which the ASN.1 encoded
     *            {@code Filter} should be read.
     * @return The decoded {@code Filter}.
     * @throws IOException
     *             If an error occurs while reading from {@code reader}.
     */
    public static Filter readFilter(final ASN1Reader reader) throws IOException {
        final byte type = reader.peekType();
        switch (type) {
        case LDAP.TYPE_FILTER_AND:
            return readAndFilter(reader);
        case LDAP.TYPE_FILTER_OR:
            return readOrFilter(reader);
        case LDAP.TYPE_FILTER_NOT:
            return readNotFilter(reader);
        case LDAP.TYPE_FILTER_EQUALITY:
            return readEqualityMatchFilter(reader);
        case LDAP.TYPE_FILTER_GREATER_OR_EQUAL:
            return readGreaterOrEqualMatchFilter(reader);
        case LDAP.TYPE_FILTER_LESS_OR_EQUAL:
            return readLessOrEqualMatchFilter(reader);
        case LDAP.TYPE_FILTER_APPROXIMATE:
            return readApproxMatchFilter(reader);
        case LDAP.TYPE_FILTER_SUBSTRING:
            return readSubstringsFilter(reader);
        case LDAP.TYPE_FILTER_PRESENCE:
            return Filter.present(reader.readOctetStringAsString(type));
        case LDAP.TYPE_FILTER_EXTENSIBLE_MATCH:
            return readExtensibleMatchFilter(reader);
        default:
            return Filter.unrecognized(type, reader.readOctetString(type));
        }
    }

    /**
     * Reads the next ASN.1 element from the provided {@code ASN1Reader} as a an
     * {@code Entry}.
     *
     * @param reader
     *            The {@code ASN1Reader} from which the ASN.1 encoded
     *            {@code Entry} should be read.
     * @param options
     *            The decode options to use when decoding the entry.
     * @return The decoded {@code Entry}.
     * @throws IOException
     *             If an error occurs while reading from {@code reader}.
     */
    public static Entry readEntry(final ASN1Reader reader, final DecodeOptions options)
            throws IOException {
        return readEntry(reader, OP_TYPE_SEARCH_RESULT_ENTRY, options);
    }

    /**
     * Writes a {@code Filter} to the provided {@code ASN1Writer}.
     *
     * @param writer
     *            The {@code ASN1Writer} to which the ASN.1 encoded
     *            {@code Filter} should be written.
     * @param filter
     *            The filter.
     * @throws IOException
     *             If an error occurs while writing to {@code writer}.
     */
    public static void writeFilter(final ASN1Writer writer, final Filter filter) throws IOException {
        final IOException e = filter.accept(ASN1_ENCODER, writer);
        if (e != null) {
            throw e;
        }
    }

    /**
     * Writes an {@code Entry} to the provided {@code ASN1Writer}.
     *
     * @param writer
     *            The {@code ASN1Writer} to which the ASN.1 encoded
     *            {@code Entry} should be written.
     * @param entry
     *            The entry.
     * @throws IOException
     *             If an error occurs while writing to {@code writer}.
     */
    public static void writeEntry(final ASN1Writer writer, final Entry entry) throws IOException {
        writeEntry(writer, OP_TYPE_SEARCH_RESULT_ENTRY, entry);
    }

    static AttributeDescription readAttributeDescription(final String attributeDescription,
            final Schema schema) throws DecodeException {
        try {
            return AttributeDescription.valueOf(attributeDescription, schema);
        } catch (final LocalizedIllegalArgumentException e) {
            throw DecodeException.error(e.getMessageObject());
        }
    }

    static DN readDN(final String dn, final Schema schema) throws DecodeException {
        try {
            return DN.valueOf(dn, schema);
        } catch (final LocalizedIllegalArgumentException e) {
            throw DecodeException.error(e.getMessageObject());
        }
    }

    static Entry readEntry(final ASN1Reader reader, final byte tagType, final DecodeOptions options)
            throws DecodeException, IOException {
        reader.readStartSequence(tagType);
        final Entry entry;
        try {
            final String dnString = reader.readOctetStringAsString();
            final Schema schema = options.getSchemaResolver().resolveSchema(dnString);
            final DN dn = readDN(dnString, schema);
            entry = options.getEntryFactory().newEntry(dn);
            reader.readStartSequence();
            try {
                while (reader.hasNextElement()) {
                    reader.readStartSequence();
                    try {
                        final String ads = reader.readOctetStringAsString();
                        final AttributeDescription ad = readAttributeDescription(ads, schema);
                        final Attribute attribute = options.getAttributeFactory().newAttribute(ad);
                        reader.readStartSet();
                        try {
                            while (reader.hasNextElement()) {
                                attribute.add(reader.readOctetString());
                            }
                            entry.addAttribute(attribute);
                        } finally {
                            reader.readEndSet();
                        }
                    } finally {
                        reader.readEndSequence();
                    }
                }
            } finally {
                reader.readEndSequence();
            }
        } finally {
            reader.readEndSequence();
        }
        return entry;
    }

    static void writeAttribute(final ASN1Writer writer, final Attribute attribute)
            throws IOException {
        writer.writeStartSequence();
        {
            writer.writeOctetString(attribute.getAttributeDescriptionAsString());
            writer.writeStartSet();
            {
                for (final ByteString value : attribute) {
                    writer.writeOctetString(value);
                }
            }
            writer.writeEndSet();
        }
        writer.writeEndSequence();
    }

    static void writeEntry(final ASN1Writer writer, final byte typeTag, final Entry entry)
            throws IOException {
        writer.writeStartSequence(typeTag);
        {
            writer.writeOctetString(entry.getName().toString());
            writer.writeStartSequence();
            {
                for (final Attribute attr : entry.getAllAttributes()) {
                    writeAttribute(writer, attr);
                }
            }
            writer.writeEndSequence();
        }
        writer.writeEndSequence();
    }

    private static Filter readAndFilter(final ASN1Reader reader) throws IOException {
        reader.readStartSequence(LDAP.TYPE_FILTER_AND);
        try {
            if (reader.hasNextElement()) {
                final List<Filter> subFilters = new LinkedList<>();
                do {
                    subFilters.add(readFilter(reader));
                } while (reader.hasNextElement());
                return Filter.and(subFilters);
            } else {
                // No sub-filters - this is an RFC 4526 absolute true filter.
                return Filter.alwaysTrue();
            }
        } finally {
            reader.readEndSequence();
        }
    }

    private static Filter readApproxMatchFilter(final ASN1Reader reader) throws IOException {
        reader.readStartSequence(LDAP.TYPE_FILTER_APPROXIMATE);
        try {
            final String attributeDescription = reader.readOctetStringAsString();
            final ByteString assertionValue = reader.readOctetString();
            return Filter.approx(attributeDescription, assertionValue);
        } finally {
            reader.readEndSequence();
        }

    }

    private static Filter readEqualityMatchFilter(final ASN1Reader reader) throws IOException {
        reader.readStartSequence(LDAP.TYPE_FILTER_EQUALITY);
        try {
            final String attributeDescription = reader.readOctetStringAsString();
            final ByteString assertionValue = reader.readOctetString();
            return Filter.equality(attributeDescription, assertionValue);
        } finally {
            reader.readEndSequence();
        }
    }

    private static Filter readExtensibleMatchFilter(final ASN1Reader reader) throws IOException {
        reader.readStartSequence(LDAP.TYPE_FILTER_EXTENSIBLE_MATCH);
        try {
            String matchingRule = null;
            if (reader.peekType() == LDAP.TYPE_MATCHING_RULE_ID) {
                matchingRule = reader.readOctetStringAsString(LDAP.TYPE_MATCHING_RULE_ID);
            }
            String attributeDescription = null;
            if (reader.peekType() == LDAP.TYPE_MATCHING_RULE_TYPE) {
                attributeDescription = reader.readOctetStringAsString(LDAP.TYPE_MATCHING_RULE_TYPE);
            }
            boolean dnAttributes = false;
            if (reader.hasNextElement()
                    && (reader.peekType() == LDAP.TYPE_MATCHING_RULE_DN_ATTRIBUTES)) {
                dnAttributes = reader.readBoolean();
            }
            final ByteString assertionValue = reader.readOctetString(LDAP.TYPE_MATCHING_RULE_VALUE);
            return Filter.extensible(matchingRule, attributeDescription, assertionValue,
                    dnAttributes);
        } finally {
            reader.readEndSequence();
        }
    }

    private static Filter readGreaterOrEqualMatchFilter(final ASN1Reader reader) throws IOException {
        reader.readStartSequence(LDAP.TYPE_FILTER_GREATER_OR_EQUAL);
        try {
            final String attributeDescription = reader.readOctetStringAsString();
            final ByteString assertionValue = reader.readOctetString();
            return Filter.greaterOrEqual(attributeDescription, assertionValue);
        } finally {
            reader.readEndSequence();
        }
    }

    private static Filter readLessOrEqualMatchFilter(final ASN1Reader reader) throws IOException {
        reader.readStartSequence(LDAP.TYPE_FILTER_LESS_OR_EQUAL);
        try {
            final String attributeDescription = reader.readOctetStringAsString();
            final ByteString assertionValue = reader.readOctetString();
            return Filter.lessOrEqual(attributeDescription, assertionValue);
        } finally {
            reader.readEndSequence();
        }
    }

    private static Filter readNotFilter(final ASN1Reader reader) throws IOException {
        reader.readStartSequence(LDAP.TYPE_FILTER_NOT);
        try {
            return Filter.not(readFilter(reader));
        } finally {
            reader.readEndSequence();
        }
    }

    private static Filter readOrFilter(final ASN1Reader reader) throws IOException {
        reader.readStartSequence(LDAP.TYPE_FILTER_OR);
        try {
            if (reader.hasNextElement()) {
                final List<Filter> subFilters = new LinkedList<>();
                do {
                    subFilters.add(readFilter(reader));
                } while (reader.hasNextElement());
                return Filter.or(subFilters);
            } else {
                // No sub-filters - this is an RFC 4526 absolute false filter.
                return Filter.alwaysFalse();
            }
        } finally {
            reader.readEndSequence();
        }
    }

    private static Filter readSubstringsFilter(final ASN1Reader reader) throws IOException {
        reader.readStartSequence(LDAP.TYPE_FILTER_SUBSTRING);
        try {
            final String attributeDescription = reader.readOctetStringAsString();
            reader.readStartSequence();
            try {
                // FIXME: There should be at least one element in this substring
                // filter sequence.
                ByteString initialSubstring = null;
                if (reader.peekType() == LDAP.TYPE_SUBINITIAL) {
                    initialSubstring = reader.readOctetString(LDAP.TYPE_SUBINITIAL);
                }
                final List<ByteString> anySubstrings;
                if (reader.hasNextElement() && (reader.peekType() == LDAP.TYPE_SUBANY)) {
                    anySubstrings = new LinkedList<>();
                    do {
                        anySubstrings.add(reader.readOctetString(LDAP.TYPE_SUBANY));
                    } while (reader.hasNextElement() && (reader.peekType() == LDAP.TYPE_SUBANY));
                } else {
                    anySubstrings = Collections.emptyList();
                }
                ByteString finalSubstring = null;
                if (reader.hasNextElement() && (reader.peekType() == LDAP.TYPE_SUBFINAL)) {
                    finalSubstring = reader.readOctetString(LDAP.TYPE_SUBFINAL);
                }
                return Filter.substrings(attributeDescription, initialSubstring, anySubstrings,
                        finalSubstring);
            } finally {
                reader.readEndSequence();
            }
        } finally {
            reader.readEndSequence();
        }
    }

    /** Prevent instantiation. */
    private LDAP() {
        // Nothing to do.
    }
}
