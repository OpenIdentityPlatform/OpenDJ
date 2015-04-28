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
 *      Portions copyright 2012-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.controls;

import static com.forgerock.opendj.ldap.CoreMessages.*;
import static com.forgerock.opendj.util.StaticUtils.getExceptionMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.io.LDAP;
import org.forgerock.opendj.ldap.AbstractFilterVisitor;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.util.Reject;

/**
 * The matched values request control as defined in RFC 3876. The matched values
 * control may be included in a search request to indicate that only attribute
 * values matching one or more filters contained in the matched values control
 * should be returned to the client.
 * <p>
 * The matched values request control supports a subset of the LDAP filter type
 * defined in RFC 4511, and is defined as follows:
 *
 * <pre>
 * ValuesReturnFilter ::= SEQUENCE OF SimpleFilterItem
 *
 * SimpleFilterItem ::= CHOICE {
 *        equalityMatch   [3] AttributeValueAssertion,
 *        substrings      [4] SubstringFilter,
 *        greaterOrEqual  [5] AttributeValueAssertion,
 *        lessOrEqual     [6] AttributeValueAssertion,
 *        present         [7] AttributeDescription,
 *        approxMatch     [8] AttributeValueAssertion,
 *        extensibleMatch [9] SimpleMatchingAssertion }
 *
 * SimpleMatchingAssertion ::= SEQUENCE {
 *        matchingRule    [1] MatchingRuleId OPTIONAL,
 *        type            [2] AttributeDescription OPTIONAL,
 * --- at least one of the above must be present
 *        matchValue      [3] AssertionValue}
 * </pre>
 *
 * For example Barbara Jensen's entry contains two common name values, Barbara
 * Jensen and Babs Jensen. The following code retrieves only the latter.
 *
 * <pre>
 * String DN = &quot;uid=bjensen,ou=People,dc=example,dc=com&quot;;
 * SearchRequest request = Requests.newSearchRequest(DN,
 *          SearchScope.BASE_OBJECT, &quot;(objectclass=*)&quot;, &quot;cn&quot;)
 *          .addControl(MatchedValuesRequestControl
 *                  .newControl(true, &quot;(cn=Babs Jensen)&quot;));
 *
 * // Get the entry, retrieving cn: Babs Jensen, not cn: Barbara Jensen
 * SearchResultEntry entry = connection.searchSingleEntry(request);
 * </pre>
 *
 * @see <a href="http://tools.ietf.org/html/rfc3876">RFC 3876 - Returning
 *      Matched Values with the Lightweight Directory Access Protocol version 3
 *      (LDAPv3) </a>
 */
public final class MatchedValuesRequestControl implements Control {
    /**
     * Visitor for validating matched values filters.
     */
    private static final class FilterValidator extends
            AbstractFilterVisitor<LocalizedIllegalArgumentException, Filter> {

        @Override
        public LocalizedIllegalArgumentException visitAndFilter(final Filter p,
                final List<Filter> subFilters) {
            final LocalizableMessage message = ERR_MVFILTER_BAD_FILTER_AND.get(p.toString());
            return new LocalizedIllegalArgumentException(message);
        }

        @Override
        public LocalizedIllegalArgumentException visitExtensibleMatchFilter(final Filter p,
                final String matchingRule, final String attributeDescription,
                final ByteString assertionValue, final boolean dnAttributes) {
            if (dnAttributes) {
                final LocalizableMessage message = ERR_MVFILTER_BAD_FILTER_EXT.get(p.toString());
                return new LocalizedIllegalArgumentException(message);
            } else {
                return null;
            }
        }

        @Override
        public LocalizedIllegalArgumentException visitNotFilter(final Filter p,
                final Filter subFilter) {
            final LocalizableMessage message = ERR_MVFILTER_BAD_FILTER_NOT.get(p.toString());
            return new LocalizedIllegalArgumentException(message);
        }

        @Override
        public LocalizedIllegalArgumentException visitOrFilter(final Filter p,
                final List<Filter> subFilters) {
            final LocalizableMessage message = ERR_MVFILTER_BAD_FILTER_OR.get(p.toString());
            return new LocalizedIllegalArgumentException(message);
        }

        @Override
        public LocalizedIllegalArgumentException visitUnrecognizedFilter(final Filter p,
                final byte filterTag, final ByteString filterBytes) {
            final LocalizableMessage message =
                    ERR_MVFILTER_BAD_FILTER_UNRECOGNIZED.get(p.toString(), filterTag);
            return new LocalizedIllegalArgumentException(message);
        }
    }

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /**
     * The OID for the matched values request control used to specify which
     * particular attribute values should be returned in a search result entry.
     */
    public static final String OID = "1.2.826.0.1.3344810.2.3";

    /**
     * A decoder which can be used for decoding the matched values request
     * control.
     */
    public static final ControlDecoder<MatchedValuesRequestControl> DECODER =
            new ControlDecoder<MatchedValuesRequestControl>() {

                public MatchedValuesRequestControl decodeControl(final Control control,
                        final DecodeOptions options) throws DecodeException {
                    Reject.ifNull(control);

                    if (control instanceof MatchedValuesRequestControl) {
                        return (MatchedValuesRequestControl) control;
                    }

                    if (!control.getOID().equals(OID)) {
                        final LocalizableMessage message =
                                ERR_MATCHEDVALUES_CONTROL_BAD_OID.get(control.getOID(), OID);
                        throw DecodeException.error(message);
                    }

                    if (!control.hasValue()) {
                        // The response control must always have a value.
                        final LocalizableMessage message = ERR_MATCHEDVALUES_NO_CONTROL_VALUE.get();
                        throw DecodeException.error(message);
                    }

                    final ASN1Reader reader = ASN1.getReader(control.getValue());
                    try {
                        reader.readStartSequence();
                        if (!reader.hasNextElement()) {
                            final LocalizableMessage message = ERR_MATCHEDVALUES_NO_FILTERS.get();
                            throw DecodeException.error(message);
                        }

                        final LinkedList<Filter> filters = new LinkedList<>();
                        do {
                            final Filter filter = LDAP.readFilter(reader);
                            try {
                                validateFilter(filter);
                            } catch (final LocalizedIllegalArgumentException e) {
                                throw DecodeException.error(e.getMessageObject());
                            }
                            filters.add(filter);
                        } while (reader.hasNextElement());

                        reader.readEndSequence();

                        return new MatchedValuesRequestControl(control.isCritical(), Collections
                                .unmodifiableList(filters));
                    } catch (final IOException e) {
                        logger.debug(LocalizableMessage.raw("%s", e));

                        final LocalizableMessage message =
                                ERR_MATCHEDVALUES_CANNOT_DECODE_VALUE_AS_SEQUENCE
                                        .get(getExceptionMessage(e));
                        throw DecodeException.error(message);
                    }
                }

                public String getOID() {
                    return OID;
                }
            };

    private static final FilterValidator FILTER_VALIDATOR = new FilterValidator();

    /**
     * Creates a new matched values request control with the provided
     * criticality and list of filters.
     *
     * @param isCritical
     *            {@code true} if it is unacceptable to perform the operation
     *            without applying the semantics of this control, or
     *            {@code false} if it can be ignored.
     * @param filters
     *            The list of filters of which at least one must match an
     *            attribute value in order for the attribute value to be
     *            returned to the client. The list must not be empty.
     * @return The new control.
     * @throws LocalizedIllegalArgumentException
     *             If one or more filters failed to conform to the filter
     *             constraints defined in RFC 3876.
     * @throws IllegalArgumentException
     *             If {@code filters} was empty.
     * @throws NullPointerException
     *             If {@code filters} was {@code null}.
     */
    public static MatchedValuesRequestControl newControl(final boolean isCritical,
            final Collection<Filter> filters) {
        Reject.ifNull(filters);
        Reject.ifFalse(filters.size() > 0, "filters is empty");

        List<Filter> copyOfFilters;
        if (filters.size() == 1) {
            copyOfFilters = Collections.singletonList(validateFilter(filters.iterator().next()));
        } else {
            copyOfFilters = new ArrayList<>(filters.size());
            for (final Filter filter : filters) {
                copyOfFilters.add(validateFilter(filter));
            }
            copyOfFilters = Collections.unmodifiableList(copyOfFilters);
        }

        return new MatchedValuesRequestControl(isCritical, copyOfFilters);
    }

    /**
     * Creates a new matched values request control with the provided
     * criticality and list of filters.
     *
     * @param isCritical
     *            {@code true} if it is unacceptable to perform the operation
     *            without applying the semantics of this control, or
     *            {@code false} if it can be ignored.
     * @param filters
     *            The list of filters of which at least one must match an
     *            attribute value in order for the attribute value to be
     *            returned to the client. The list must not be empty.
     * @return The new control.
     * @throws LocalizedIllegalArgumentException
     *             If one or more filters could not be parsed, or if one or more
     *             filters failed to conform to the filter constraints defined
     *             in RFC 3876.
     * @throws NullPointerException
     *             If {@code filters} was {@code null}.
     */
    public static MatchedValuesRequestControl newControl(final boolean isCritical,
            final String... filters) {
        Reject.ifFalse(filters.length > 0, "filters is empty");

        final List<Filter> parsedFilters = new ArrayList<>(filters.length);
        for (final String filter : filters) {
            parsedFilters.add(validateFilter(Filter.valueOf(filter)));
        }
        return new MatchedValuesRequestControl(isCritical, Collections
                .unmodifiableList(parsedFilters));
    }

    private static Filter validateFilter(final Filter filter) {
        final LocalizedIllegalArgumentException e = filter.accept(FILTER_VALIDATOR, filter);
        if (e != null) {
            throw e;
        }
        return filter;
    }

    private final Collection<Filter> filters;

    private final boolean isCritical;

    private MatchedValuesRequestControl(final boolean isCritical, final Collection<Filter> filters) {
        this.isCritical = isCritical;
        this.filters = filters;
    }

    /**
     * Returns an unmodifiable collection containing the list of filters
     * associated with this matched values control.
     *
     * @return An unmodifiable collection containing the list of filters
     *         associated with this matched values control.
     */
    public Collection<Filter> getFilters() {
        return filters;
    }

    /** {@inheritDoc} */
    public String getOID() {
        return OID;
    }

    /** {@inheritDoc} */
    @Override
    public ByteString getValue() {
        final ByteStringBuilder buffer = new ByteStringBuilder();
        final ASN1Writer writer = ASN1.getWriter(buffer);
        try {
            writer.writeStartSequence();
            for (final Filter f : filters) {
                LDAP.writeFilter(writer, f);
            }
            writer.writeEndSequence();
            return buffer.toByteString();
        } catch (final IOException ioe) {
            // This should never happen unless there is a bug somewhere.
            throw new RuntimeException(ioe);
        }
    }

    /** {@inheritDoc} */
    public boolean hasValue() {
        return true;
    }

    /** {@inheritDoc} */
    public boolean isCritical() {
        return isCritical;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("MatchedValuesRequestControl(oid=");
        builder.append(getOID());
        builder.append(", criticality=");
        builder.append(isCritical());
        builder.append(")");
        return builder.toString();
    }
}
