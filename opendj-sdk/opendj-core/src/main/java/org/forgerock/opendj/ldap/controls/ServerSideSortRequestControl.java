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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2012-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.controls;

import static com.forgerock.opendj.util.StaticUtils.getExceptionMessage;
import static com.forgerock.opendj.ldap.CoreMessages.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.SortKey;
import org.forgerock.util.Reject;

/**
 * The server-side sort request control as defined in RFC 2891. This control may
 * be included in a search request to indicate that search result entries should
 * be sorted by the server before being returned. The sort order is specified
 * using one or more sort keys, the first being the primary key, and so on.
 * <p>
 * This controls may be useful when the client has limited functionality or for
 * some other reason cannot sort the results but still needs them sorted. In
 * cases where the client can sort the results client-side sorting is
 * recommended in order to reduce load on the server. See {@link SortKey} for an
 * example of client-side sorting.
 * <p>
 * The following example demonstrates how to work with a server-side sort.
 *
 * <pre>
 * Connection connection = ...;
 *
 * SearchRequest request = Requests.newSearchRequest(
 *         "ou=People,dc=example,dc=com", SearchScope.WHOLE_SUBTREE, "(sn=Jensen)", "cn")
 *         .addControl(ServerSideSortRequestControl.newControl(true, new SortKey("cn")));
 *
 * SearchResultHandler resultHandler = new MySearchResultHandler();
 * Result result = connection.search(request, resultHandler);
 *
 * ServerSideSortResponseControl control = result.getControl(
 *         ServerSideSortResponseControl.DECODER, new DecodeOptions());
 * if (control != null && control.getResult() == ResultCode.SUCCESS) {
 *     // Entries are sorted.
 * } else {
 *     // Entries not sorted.
 * }
 * </pre>
 *
 * @see ServerSideSortResponseControl
 * @see SortKey
 * @see <a href="http://tools.ietf.org/html/rfc2891">RFC 2891 - LDAP Control
 *      Extension for Server Side Sorting of Search Results </a>
 */
public final class ServerSideSortRequestControl implements Control {
    /**
     * The OID for the server-side sort request control.
     */
    public static final String OID = "1.2.840.113556.1.4.473";

    /**
     * The BER type to use when encoding the orderingRule element.
     */
    private static final byte TYPE_ORDERING_RULE_ID = (byte) 0x80;

    /**
     * The BER type to use when encoding the reverseOrder element.
     */
    private static final byte TYPE_REVERSE_ORDER = (byte) 0x81;

    /**
     * A decoder which can be used for decoding the server side sort request
     * control.
     */
    public static final ControlDecoder<ServerSideSortRequestControl> DECODER =
            new ControlDecoder<ServerSideSortRequestControl>() {

                public ServerSideSortRequestControl decodeControl(final Control control,
                        final DecodeOptions options) throws DecodeException {
                    Reject.ifNull(control);

                    if (control instanceof ServerSideSortRequestControl) {
                        return (ServerSideSortRequestControl) control;
                    }

                    if (!control.getOID().equals(OID)) {
                        final LocalizableMessage message =
                                ERR_SORTREQ_CONTROL_BAD_OID.get(control.getOID(), OID);
                        throw DecodeException.error(message);
                    }

                    if (!control.hasValue()) {
                        // The request control must always have a value.
                        final LocalizableMessage message = INFO_SORTREQ_CONTROL_NO_VALUE.get();
                        throw DecodeException.error(message);
                    }

                    final ASN1Reader reader = ASN1.getReader(control.getValue());
                    try {
                        reader.readStartSequence();
                        if (!reader.hasNextElement()) {
                            final LocalizableMessage message =
                                    INFO_SORTREQ_CONTROL_NO_SORT_KEYS.get();
                            throw DecodeException.error(message);
                        }

                        final List<SortKey> keys = new LinkedList<>();
                        while (reader.hasNextElement()) {
                            reader.readStartSequence();
                            final String attrName = reader.readOctetStringAsString();

                            String orderingRule = null;
                            boolean reverseOrder = false;
                            if (reader.hasNextElement()
                                    && (reader.peekType() == TYPE_ORDERING_RULE_ID)) {
                                orderingRule = reader.readOctetStringAsString();
                            }
                            if (reader.hasNextElement()
                                    && (reader.peekType() == TYPE_REVERSE_ORDER)) {
                                reverseOrder = reader.readBoolean();
                            }
                            reader.readEndSequence();

                            keys.add(new SortKey(attrName, reverseOrder, orderingRule));
                        }
                        reader.readEndSequence();

                        return new ServerSideSortRequestControl(control.isCritical(), Collections
                                .unmodifiableList(keys));
                    } catch (final IOException e) {
                        final LocalizableMessage message =
                                INFO_SORTREQ_CONTROL_CANNOT_DECODE_VALUE
                                        .get(getExceptionMessage(e));
                        throw DecodeException.error(message, e);
                    }
                }

                public String getOID() {
                    return OID;
                }
            };

    /**
     * Creates a new server side sort request control with the provided
     * criticality and list of sort keys.
     *
     * @param isCritical
     *            {@code true} if it is unacceptable to perform the operation
     *            without applying the semantics of this control, or
     *            {@code false} if it can be ignored.
     * @param keys
     *            The list of sort keys.
     * @return The new control.
     * @throws IllegalArgumentException
     *             If {@code keys} was empty.
     * @throws NullPointerException
     *             If {@code keys} was {@code null}.
     */
    public static ServerSideSortRequestControl newControl(final boolean isCritical,
            final Collection<SortKey> keys) {
        Reject.ifNull(keys);
        Reject.ifFalse(!keys.isEmpty(), "keys must not be empty");

        return new ServerSideSortRequestControl(isCritical, Collections
                .unmodifiableList(new ArrayList<SortKey>(keys)));
    }

    /**
     * Creates a new server side sort request control with the provided
     * criticality and list of sort keys.
     *
     * @param isCritical
     *            {@code true} if it is unacceptable to perform the operation
     *            without applying the semantics of this control, or
     *            {@code false} if it can be ignored.
     * @param keys
     *            The list of sort keys.
     * @return The new control.
     * @throws IllegalArgumentException
     *             If {@code keys} was empty.
     * @throws NullPointerException
     *             If {@code keys} was {@code null}.
     */
    public static ServerSideSortRequestControl newControl(final boolean isCritical,
            final SortKey... keys) {
        return newControl(isCritical, Arrays.asList(keys));
    }

    /**
     * Creates a new server side sort request control with the provided
     * criticality and string representation of a list of sort keys. The string
     * representation is comprised of a comma separate list of sort keys as
     * defined in {@link SortKey#valueOf(String)}. There must be at least one
     * sort key present in the string representation.
     *
     * @param isCritical
     *            {@code true} if it is unacceptable to perform the operation
     *            without applying the semantics of this control, or
     *            {@code false} if it can be ignored.
     * @param sortKeys
     *            The list of sort keys.
     * @return The new control.
     * @throws LocalizedIllegalArgumentException
     *             If {@code sortKeys} is not a valid string representation of a
     *             list of sort keys.
     * @throws NullPointerException
     *             If {@code sortKeys} was {@code null}.
     */
    public static ServerSideSortRequestControl newControl(final boolean isCritical,
            final String sortKeys) {
        Reject.ifNull(sortKeys);

        final List<SortKey> keys = new LinkedList<>();
        final StringTokenizer tokenizer = new StringTokenizer(sortKeys, ",");
        while (tokenizer.hasMoreTokens()) {
            final String token = tokenizer.nextToken().trim();
            keys.add(SortKey.valueOf(token));
        }
        if (keys.isEmpty()) {
            final LocalizableMessage message = ERR_SORT_KEY_NO_SORT_KEYS.get(sortKeys);
            throw new LocalizedIllegalArgumentException(message);
        }
        return new ServerSideSortRequestControl(isCritical, Collections.unmodifiableList(keys));
    }

    private final List<SortKey> sortKeys;

    private final boolean isCritical;

    private ServerSideSortRequestControl(final boolean isCritical, final List<SortKey> keys) {
        this.isCritical = isCritical;
        this.sortKeys = keys;
    }

    /** {@inheritDoc} */
    public String getOID() {
        return OID;
    }

    /**
     * Returns an unmodifiable list containing the sort keys associated with
     * this server side sort request control. The list will contain at least one
     * sort key.
     *
     * @return An unmodifiable list containing the sort keys associated with
     *         this server side sort request control.
     */
    public List<SortKey> getSortKeys() {
        return sortKeys;
    }

    /** {@inheritDoc} */
    public ByteString getValue() {
        final ByteStringBuilder buffer = new ByteStringBuilder();
        final ASN1Writer writer = ASN1.getWriter(buffer);
        try {
            writer.writeStartSequence();
            for (final SortKey sortKey : sortKeys) {
                writer.writeStartSequence();
                writer.writeOctetString(sortKey.getAttributeDescription());

                if (sortKey.getOrderingMatchingRule() != null) {
                    writer.writeOctetString(TYPE_ORDERING_RULE_ID, sortKey
                            .getOrderingMatchingRule());
                }

                if (sortKey.isReverseOrder()) {
                    writer.writeBoolean(TYPE_REVERSE_ORDER, true);
                }

                writer.writeEndSequence();
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
        final StringBuilder buffer = new StringBuilder();
        buffer.append("ServerSideSortRequestControl(oid=");
        buffer.append(getOID());
        buffer.append(", criticality=");
        buffer.append(isCritical());
        buffer.append(", sortKeys=");
        buffer.append(sortKeys);
        buffer.append(")");
        return buffer.toString();
    }

}
