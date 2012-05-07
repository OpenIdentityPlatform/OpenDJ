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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package com.forgerock.opendj.ldap;

import static com.forgerock.opendj.ldap.LDAPConstants.*;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.opendj.asn1.ASN1Reader;
import org.forgerock.opendj.asn1.ASN1Writer;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.FilterVisitor;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;

/**
 * Common LDAP utility methods which may be used when implementing new controls
 * and extension.
 */
public final class LDAPUtils {

    private static final FilterVisitor<IOException, ASN1Writer> ASN1_ENCODER =
            new FilterVisitor<IOException, ASN1Writer>() {

                public IOException visitAndFilter(final ASN1Writer writer,
                        final List<Filter> subFilters) {
                    try {
                        writer.writeStartSequence(TYPE_FILTER_AND);
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

                public IOException visitApproxMatchFilter(final ASN1Writer writer,
                        final String attributeDescription, final ByteString assertionValue) {
                    try {
                        writer.writeStartSequence(TYPE_FILTER_APPROXIMATE);
                        writer.writeOctetString(attributeDescription);
                        writer.writeOctetString(assertionValue);
                        writer.writeEndSequence();
                        return null;
                    } catch (final IOException e) {
                        return e;
                    }
                }

                public IOException visitEqualityMatchFilter(final ASN1Writer writer,
                        final String attributeDescription, final ByteString assertionValue) {
                    try {
                        writer.writeStartSequence(TYPE_FILTER_EQUALITY);
                        writer.writeOctetString(attributeDescription);
                        writer.writeOctetString(assertionValue);
                        writer.writeEndSequence();
                        return null;
                    } catch (final IOException e) {
                        return e;
                    }
                }

                public IOException visitExtensibleMatchFilter(final ASN1Writer writer,
                        final String matchingRule, final String attributeDescription,
                        final ByteString assertionValue, final boolean dnAttributes) {
                    try {
                        writer.writeStartSequence(TYPE_FILTER_EXTENSIBLE_MATCH);

                        if (matchingRule != null) {
                            writer.writeOctetString(TYPE_MATCHING_RULE_ID, matchingRule);
                        }

                        if (attributeDescription != null) {
                            writer.writeOctetString(TYPE_MATCHING_RULE_TYPE, attributeDescription);
                        }

                        writer.writeOctetString(TYPE_MATCHING_RULE_VALUE, assertionValue);

                        if (dnAttributes) {
                            writer.writeBoolean(TYPE_MATCHING_RULE_DN_ATTRIBUTES, true);
                        }

                        writer.writeEndSequence();
                        return null;
                    } catch (final IOException e) {
                        return e;
                    }
                }

                public IOException visitGreaterOrEqualFilter(final ASN1Writer writer,
                        final String attributeDescription, final ByteString assertionValue) {
                    try {
                        writer.writeStartSequence(TYPE_FILTER_GREATER_OR_EQUAL);
                        writer.writeOctetString(attributeDescription);
                        writer.writeOctetString(assertionValue);
                        writer.writeEndSequence();
                        return null;
                    } catch (final IOException e) {
                        return e;
                    }
                }

                public IOException visitLessOrEqualFilter(final ASN1Writer writer,
                        final String attributeDescription, final ByteString assertionValue) {
                    try {
                        writer.writeStartSequence(TYPE_FILTER_LESS_OR_EQUAL);
                        writer.writeOctetString(attributeDescription);
                        writer.writeOctetString(assertionValue);
                        writer.writeEndSequence();
                        return null;
                    } catch (final IOException e) {
                        return e;
                    }
                }

                public IOException visitNotFilter(final ASN1Writer writer, final Filter subFilter) {
                    try {
                        writer.writeStartSequence(TYPE_FILTER_NOT);
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

                public IOException visitOrFilter(final ASN1Writer writer,
                        final List<Filter> subFilters) {
                    try {
                        writer.writeStartSequence(TYPE_FILTER_OR);
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

                public IOException visitPresentFilter(final ASN1Writer writer,
                        final String attributeDescription) {
                    try {
                        writer.writeOctetString(TYPE_FILTER_PRESENCE, attributeDescription);
                        return null;
                    } catch (final IOException e) {
                        return e;
                    }
                }

                public IOException visitSubstringsFilter(final ASN1Writer writer,
                        final String attributeDescription, final ByteString initialSubstring,
                        final List<ByteString> anySubstrings, final ByteString finalSubstring) {
                    try {
                        writer.writeStartSequence(TYPE_FILTER_SUBSTRING);
                        writer.writeOctetString(attributeDescription);

                        writer.writeStartSequence();
                        if (initialSubstring != null) {
                            writer.writeOctetString(TYPE_SUBINITIAL, initialSubstring);
                        }

                        for (final ByteSequence anySubstring : anySubstrings) {
                            writer.writeOctetString(TYPE_SUBANY, anySubstring);
                        }

                        if (finalSubstring != null) {
                            writer.writeOctetString(TYPE_SUBFINAL, finalSubstring);
                        }
                        writer.writeEndSequence();

                        writer.writeEndSequence();
                        return null;
                    } catch (final IOException e) {
                        return e;
                    }
                }

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
    public static Filter decodeFilter(final ASN1Reader reader) throws IOException {
        final byte type = reader.peekType();

        switch (type) {
        case TYPE_FILTER_AND:
            return decodeAndFilter(reader);

        case TYPE_FILTER_OR:
            return decodeOrFilter(reader);

        case TYPE_FILTER_NOT:
            return decodeNotFilter(reader);

        case TYPE_FILTER_EQUALITY:
            return decodeEqualityMatchFilter(reader);

        case TYPE_FILTER_GREATER_OR_EQUAL:
            return decodeGreaterOrEqualMatchFilter(reader);

        case TYPE_FILTER_LESS_OR_EQUAL:
            return decodeLessOrEqualMatchFilter(reader);

        case TYPE_FILTER_APPROXIMATE:
            return decodeApproxMatchFilter(reader);

        case TYPE_FILTER_SUBSTRING:
            return decodeSubstringsFilter(reader);

        case TYPE_FILTER_PRESENCE:
            return Filter.present(reader.readOctetStringAsString(type));

        case TYPE_FILTER_EXTENSIBLE_MATCH:
            return decodeExtensibleMatchFilter(reader);

        default:
            return Filter.unrecognized(type, reader.readOctetString(type));
        }
    }

    /**
     * Reads the next ASN.1 element from the provided {@code ASN1Reader} as a
     * {@code SearchResultEntry}.
     *
     * @param reader
     *            The {@code ASN1Reader} from which the ASN.1 encoded
     *            {@code SearchResultEntry} should be read.
     * @param options
     *            The decode options to use when decoding the entry.
     * @return The decoded {@code SearchResultEntry}.
     * @throws IOException
     *             If an error occurs while reading from {@code reader}.
     */
    public static SearchResultEntry decodeSearchResultEntry(final ASN1Reader reader,
            final DecodeOptions options) throws IOException {
        return LDAPReader.decodeEntry(reader, options);
    }

    /**
     * Writes the ASN.1 encoding of the provided {@code Filter} to the provided
     * {@code ASN1Writer}.
     *
     * @param writer
     *            The {@code ASN1Writer} to which the ASN.1 encoding of the
     *            provided {@code Filter} should be written.
     * @param filter
     *            The filter to be encoded.
     * @return The updated {@code ASN1Writer}.
     * @throws IOException
     *             If an error occurs while writing to {@code writer}.
     */
    public static ASN1Writer encodeFilter(final ASN1Writer writer, final Filter filter)
            throws IOException {
        final IOException e = filter.accept(ASN1_ENCODER, writer);
        if (e != null) {
            throw e;
        } else {
            return writer;
        }
    }

    /**
     * Writes the ASN.1 encoding of the provided {@code SearchResultEntry} to
     * the provided {@code ASN1Writer}.
     *
     * @param writer
     *            The {@code ASN1Writer} to which the ASN.1 encoding of the
     *            provided {@code SearchResultEntry} should be written.
     * @param entry
     *            The Search Result Entry to be encoded.
     * @return The updated {@code ASN1Writer}.
     * @throws IOException
     *             If an error occurs while writing to {@code writer}.
     */
    public static ASN1Writer encodeSearchResultEntry(final ASN1Writer writer,
            final SearchResultEntry entry) throws IOException {
        // FIXME: this should include Controls.
        LDAPWriter.encodeEntry(writer, entry);
        return writer;
    }

    // Decodes an and filter.
    private static Filter decodeAndFilter(final ASN1Reader reader) throws IOException {
        Filter filter;

        reader.readStartSequence(TYPE_FILTER_AND);
        try {
            if (reader.hasNextElement()) {
                final List<Filter> subFilters = new LinkedList<Filter>();
                do {
                    subFilters.add(decodeFilter(reader));
                } while (reader.hasNextElement());
                filter = Filter.and(subFilters);
            } else {
                // No sub-filters - this is an RFC 4526 absolute true filter.
                filter = Filter.alwaysTrue();
            }
        } finally {
            reader.readEndSequence();
        }

        return filter;
    }

    // Decodes an approximate match filter.
    private static Filter decodeApproxMatchFilter(final ASN1Reader reader) throws IOException {
        String attributeDescription;
        ByteString assertionValue;

        reader.readStartSequence(TYPE_FILTER_APPROXIMATE);
        try {
            attributeDescription = reader.readOctetStringAsString();
            assertionValue = reader.readOctetString();
        } finally {
            reader.readEndSequence();
        }

        return Filter.approx(attributeDescription, assertionValue);
    }

    // Decodes an equality match filter.
    private static Filter decodeEqualityMatchFilter(final ASN1Reader reader) throws IOException {
        String attributeDescription;
        ByteString assertionValue;

        reader.readStartSequence(TYPE_FILTER_EQUALITY);
        try {
            attributeDescription = reader.readOctetStringAsString();
            assertionValue = reader.readOctetString();
        } finally {
            reader.readEndSequence();
        }

        return Filter.equality(attributeDescription, assertionValue);
    }

    // Decodes an extensible match filter.
    private static Filter decodeExtensibleMatchFilter(final ASN1Reader reader) throws IOException {
        String matchingRule;
        String attributeDescription;
        boolean dnAttributes;
        ByteString assertionValue;

        reader.readStartSequence(TYPE_FILTER_EXTENSIBLE_MATCH);
        try {
            matchingRule = null;
            if (reader.peekType() == TYPE_MATCHING_RULE_ID) {
                matchingRule = reader.readOctetStringAsString(TYPE_MATCHING_RULE_ID);
            }
            attributeDescription = null;
            if (reader.peekType() == TYPE_MATCHING_RULE_TYPE) {
                attributeDescription = reader.readOctetStringAsString(TYPE_MATCHING_RULE_TYPE);
            }
            dnAttributes = false;
            if (reader.hasNextElement() && (reader.peekType() == TYPE_MATCHING_RULE_DN_ATTRIBUTES)) {
                dnAttributes = reader.readBoolean();
            }
            assertionValue = reader.readOctetString(TYPE_MATCHING_RULE_VALUE);
        } finally {
            reader.readEndSequence();
        }

        return Filter.extensible(matchingRule, attributeDescription, assertionValue,
                dnAttributes);
    }

    // Decodes a greater than or equal filter.
    private static Filter decodeGreaterOrEqualMatchFilter(final ASN1Reader reader)
            throws IOException {
        String attributeDescription;
        ByteString assertionValue;

        reader.readStartSequence(TYPE_FILTER_GREATER_OR_EQUAL);
        try {
            attributeDescription = reader.readOctetStringAsString();
            assertionValue = reader.readOctetString();
        } finally {
            reader.readEndSequence();
        }
        return Filter.greaterOrEqual(attributeDescription, assertionValue);
    }

    // Decodes a less than or equal filter.
    private static Filter decodeLessOrEqualMatchFilter(final ASN1Reader reader) throws IOException {
        String attributeDescription;
        ByteString assertionValue;

        reader.readStartSequence(TYPE_FILTER_LESS_OR_EQUAL);
        try {
            attributeDescription = reader.readOctetStringAsString();
            assertionValue = reader.readOctetString();
        } finally {
            reader.readEndSequence();
        }

        return Filter.lessOrEqual(attributeDescription, assertionValue);
    }

    // Decodes a not filter.
    private static Filter decodeNotFilter(final ASN1Reader reader) throws IOException {
        Filter subFilter;

        reader.readStartSequence(TYPE_FILTER_NOT);
        try {
            subFilter = decodeFilter(reader);
        } finally {
            reader.readEndSequence();
        }

        return Filter.not(subFilter);
    }

    // Decodes an or filter.
    private static Filter decodeOrFilter(final ASN1Reader reader) throws IOException {
        Filter filter;

        reader.readStartSequence(TYPE_FILTER_OR);
        try {
            if (reader.hasNextElement()) {
                final List<Filter> subFilters = new LinkedList<Filter>();
                do {
                    subFilters.add(decodeFilter(reader));
                } while (reader.hasNextElement());
                filter = Filter.or(subFilters);
            } else {
                // No sub-filters - this is an RFC 4526 absolute false filter.
                filter = Filter.alwaysFalse();
            }
        } finally {
            reader.readEndSequence();
        }

        return filter;
    }

    // Decodes a sub-strings filter.
    private static Filter decodeSubstringsFilter(final ASN1Reader reader) throws IOException {
        ByteString initialSubstring = null;
        List<ByteString> anySubstrings = null;
        ByteString finalSubstring = null;
        String attributeDescription;

        reader.readStartSequence(TYPE_FILTER_SUBSTRING);
        try {
            attributeDescription = reader.readOctetStringAsString();
            reader.readStartSequence();
            try {
                // FIXME: There should be at least one element in this substring
                // filter sequence.
                if (reader.peekType() == TYPE_SUBINITIAL) {
                    initialSubstring = reader.readOctetString(TYPE_SUBINITIAL);
                }
                if (reader.hasNextElement() && (reader.peekType() == TYPE_SUBANY)) {
                    anySubstrings = new LinkedList<ByteString>();
                    do {
                        anySubstrings.add(reader.readOctetString(TYPE_SUBANY));
                    } while (reader.hasNextElement() && (reader.peekType() == TYPE_SUBANY));
                }
                if (reader.hasNextElement() && (reader.peekType() == TYPE_SUBFINAL)) {
                    finalSubstring = reader.readOctetString(TYPE_SUBFINAL);
                }
            } finally {
                reader.readEndSequence();
            }
        } finally {
            reader.readEndSequence();
        }

        if (anySubstrings == null) {
            anySubstrings = Collections.emptyList();
        }

        return Filter.substrings(attributeDescription, initialSubstring, anySubstrings,
                finalSubstring);
    }

    /**
     * Prevent instantiation.
     */
    private LDAPUtils() {
        // Nothing to do.
    }
}
