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
import static com.forgerock.opendj.ldap.CoreMessages.ERR_SORTRES_CONTROL_BAD_OID;
import static com.forgerock.opendj.ldap.CoreMessages.INFO_SORTRES_CONTROL_CANNOT_DECODE_VALUE;
import static com.forgerock.opendj.ldap.CoreMessages.INFO_SORTRES_CONTROL_NO_VALUE;

import java.io.IOException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.util.Reject;

/**
 * The server-side sort response control as defined in RFC 2891. This control is
 * included with a search result in response to a server-side sort request
 * included with a search request. The client application is assured that the
 * search results are sorted in the specified key order if and only if the
 * result code in this control is success. If the server omits this control from
 * the search result, the client SHOULD assume that the sort control was ignored
 * by the server.
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
 * @see ServerSideSortRequestControl
 * @see <a href="http://tools.ietf.org/html/rfc2891">RFC 2891 - LDAP Control
 *      Extension for Server Side Sorting of Search Results </a>
 */
public final class ServerSideSortResponseControl implements Control {
    /**
     * The OID for the server-side sort response control.
     */
    public static final String OID = "1.2.840.113556.1.4.474";

    /**
     * A decoder which can be used for decoding the server side sort response
     * control.
     */
    public static final ControlDecoder<ServerSideSortResponseControl> DECODER =
            new ControlDecoder<ServerSideSortResponseControl>() {

                public ServerSideSortResponseControl decodeControl(final Control control,
                        final DecodeOptions options) throws DecodeException {
                    Reject.ifNull(control, options);

                    if (control instanceof ServerSideSortResponseControl) {
                        return (ServerSideSortResponseControl) control;
                    }

                    if (!control.getOID().equals(OID)) {
                        final LocalizableMessage message =
                                ERR_SORTRES_CONTROL_BAD_OID.get(control.getOID(), OID);
                        throw DecodeException.error(message);
                    }

                    if (!control.hasValue()) {
                        // The request control must always have a value.
                        final LocalizableMessage message = INFO_SORTRES_CONTROL_NO_VALUE.get();
                        throw DecodeException.error(message);
                    }

                    final ASN1Reader reader = ASN1.getReader(control.getValue());
                    try {
                        reader.readStartSequence();

                        // FIXME: should really check that result code is one of
                        // the expected
                        // values listed in the RFC.
                        final ResultCode result = ResultCode.valueOf(reader.readEnumerated());

                        AttributeDescription attributeDescription = null;
                        if (reader.hasNextElement()) {
                            // FIXME: which schema should we use?
                            final Schema schema = options.getSchemaResolver().resolveSchema("");
                            final String ads = reader.readOctetStringAsString();
                            attributeDescription = AttributeDescription.valueOf(ads, schema);
                        }

                        return new ServerSideSortResponseControl(control.isCritical(), result,
                                attributeDescription);
                    } catch (final IOException | LocalizedIllegalArgumentException e) {
                        final LocalizableMessage message =
                                INFO_SORTRES_CONTROL_CANNOT_DECODE_VALUE.get(getExceptionMessage(e));
                        throw DecodeException.error(message, e);
                    }
                }

                public String getOID() {
                    return OID;
                }
            };

    /**
     * The BER type to use when encoding the attribute type element.
     */
    private static final byte TYPE_ATTRIBUTE_TYPE = (byte) 0x80;

    /**
     * Creates a new server-side response control with the provided sort result
     * and no attribute description.
     *
     * @param result
     *            The result code indicating the outcome of the server-side sort
     *            request. {@link ResultCode#SUCCESS} if the search results were
     *            sorted in accordance with the keys specified in the
     *            server-side sort request control, or an error code indicating
     *            why the results could not be sorted (such as
     *            {@link ResultCode#NO_SUCH_ATTRIBUTE} or
     *            {@link ResultCode#INAPPROPRIATE_MATCHING}).
     * @return The new control.
     * @throws NullPointerException
     *             If {@code result} was {@code null}.
     */
    public static ServerSideSortResponseControl newControl(final ResultCode result) {
        Reject.ifNull(result);

        return new ServerSideSortResponseControl(false, result, null);
    }

    /**
     * Creates a new server-side response control with the provided sort result
     * and attribute description.
     *
     * @param result
     *            The result code indicating the outcome of the server-side sort
     *            request. {@link ResultCode#SUCCESS} if the search results were
     *            sorted in accordance with the keys specified in the
     *            server-side sort request control, or an error code indicating
     *            why the results could not be sorted (such as
     *            {@link ResultCode#NO_SUCH_ATTRIBUTE} or
     *            {@link ResultCode#INAPPROPRIATE_MATCHING}).
     * @param attributeDescription
     *            The first attribute description specified in the list of sort
     *            keys that was in error, may be {@code null}.
     * @return The new control.
     * @throws NullPointerException
     *             If {@code result} was {@code null}.
     */
    public static ServerSideSortResponseControl newControl(final ResultCode result,
            final AttributeDescription attributeDescription) {
        Reject.ifNull(result);

        return new ServerSideSortResponseControl(false, result, attributeDescription);
    }

    /**
     * Creates a new server-side response control with the provided sort result
     * and attribute description. The attribute description will be decoded
     * using the default schema.
     *
     * @param result
     *            The result code indicating the outcome of the server-side sort
     *            request. {@link ResultCode#SUCCESS} if the search results were
     *            sorted in accordance with the keys specified in the
     *            server-side sort request control, or an error code indicating
     *            why the results could not be sorted (such as
     *            {@link ResultCode#NO_SUCH_ATTRIBUTE} or
     *            {@link ResultCode#INAPPROPRIATE_MATCHING}).
     * @param attributeDescription
     *            The first attribute description specified in the list of sort
     *            keys that was in error, may be {@code null}.
     * @return The new control.
     * @throws LocalizedIllegalArgumentException
     *             If {@code attributeDescription} could not be parsed using the
     *             default schema.
     * @throws NullPointerException
     *             If {@code result} was {@code null}.
     */
    public static ServerSideSortResponseControl newControl(final ResultCode result,
            final String attributeDescription) {
        Reject.ifNull(result);

        if (attributeDescription != null) {
            return new ServerSideSortResponseControl(false, result, AttributeDescription
                    .valueOf(attributeDescription));
        } else {
            return new ServerSideSortResponseControl(false, result, null);
        }
    }

    private final ResultCode result;

    private final AttributeDescription attributeDescription;

    private final boolean isCritical;

    private ServerSideSortResponseControl(final boolean isCritical, final ResultCode result,
            final AttributeDescription attributeDescription) {
        this.isCritical = isCritical;
        this.result = result;
        this.attributeDescription = attributeDescription;
    }

    /**
     * Returns the first attribute description specified in the list of sort
     * keys that was in error, or {@code null} if the attribute description was
     * not included with this control.
     *
     * @return The first attribute description specified in the list of sort
     *         keys that was in error.
     */
    public AttributeDescription getAttributeDescription() {
        return attributeDescription;
    }

    /** {@inheritDoc} */
    public String getOID() {
        return OID;
    }

    /**
     * Returns a result code indicating the outcome of the server-side sort
     * request. This will be {@link ResultCode#SUCCESS} if the search results
     * were sorted in accordance with the keys specified in the server-side sort
     * request control, or an error code indicating why the results could not be
     * sorted (such as {@link ResultCode#NO_SUCH_ATTRIBUTE} or
     * {@link ResultCode#INAPPROPRIATE_MATCHING}).
     *
     * @return The result code indicating the outcome of the server-side sort
     *         request.
     */
    public ResultCode getResult() {
        return result;
    }

    /** {@inheritDoc} */
    public ByteString getValue() {
        final ByteStringBuilder buffer = new ByteStringBuilder();
        final ASN1Writer writer = ASN1.getWriter(buffer);
        try {
            writer.writeStartSequence();
            writer.writeEnumerated(result.intValue());
            if (attributeDescription != null) {
                writer.writeOctetString(TYPE_ATTRIBUTE_TYPE, attributeDescription.toString());
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
        builder.append("ServerSideSortResponseControl(oid=");
        builder.append(getOID());
        builder.append(", criticality=");
        builder.append(isCritical());
        builder.append(", result=");
        builder.append(result);
        if (attributeDescription != null) {
            builder.append(", attributeDescription=");
            builder.append(attributeDescription);
        }
        builder.append(")");
        return builder.toString();
    }
}
