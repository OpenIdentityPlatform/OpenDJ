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
 *      Portions Copyright 2012-2014 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.controls;

import static com.forgerock.opendj.util.StaticUtils.getExceptionMessage;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_VLVRES_CONTROL_BAD_OID;
import static com.forgerock.opendj.ldap.CoreMessages.INFO_VLVRES_CONTROL_CANNOT_DECODE_VALUE;
import static com.forgerock.opendj.ldap.CoreMessages.INFO_VLVRES_CONTROL_NO_VALUE;

import java.io.IOException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.util.Reject;

/**
 * The virtual list view response control as defined in
 * draft-ietf-ldapext-ldapv3-vlv. This control is included with a search result
 * in response to a virtual list view request included with a search request.
 * <p>
 * If the result code included with this control indicates that the virtual list
 * view request succeeded then the content count and target position give
 * sufficient information for the client to update a list box slider position to
 * match the newly retrieved entries and identify the target entry.
 * <p>
 * The content count and context ID should be used in a subsequent virtual list
 * view requests.
 * <p>
 * The following example demonstrates use of the virtual list view controls.
 *
 * <pre>
 * ByteString contextID = ByteString.empty();
 *
 * // Add a window of 2 entries on either side of the first sn=Jensen entry.
 * SearchRequest request = Requests.newSearchRequest("ou=People,dc=example,dc=com",
 *          SearchScope.WHOLE_SUBTREE, "(sn=*)", "sn", "givenName")
 *          .addControl(ServerSideSortRequestControl.newControl(true, new SortKey("sn")))
 *          .addControl(VirtualListViewRequestControl.newAssertionControl(
 *                  true, ByteString.valueOf("Jensen"), 2, 2, contextID));
 *
 * SearchResultHandler resultHandler = new MySearchResultHandler();
 * Result result = connection.search(request, resultHandler);
 *
 * ServerSideSortResponseControl sssControl =
 *         result.getControl(ServerSideSortResponseControl.DECODER, new DecodeOptions());
 * if (sssControl != null &amp;&amp; sssControl.getResult() == ResultCode.SUCCESS) {
 *     // Entries are sorted.
 * } else {
 *     // Entries not necessarily sorted
 * }
 *
 * VirtualListViewResponseControl vlvControl =
 *         result.getControl(VirtualListViewResponseControl.DECODER, new DecodeOptions());
 * // Position in list: vlvControl.getTargetPosition()/vlvControl.getContentCount()
 * </pre>
 *
 * The search result handler in this case displays pages of results as LDIF on
 * standard out.
 *
 * <pre>
 * private static class MySearchResultHandler implements SearchResultHandler {
 *
 *     {@literal @}Override
 *     public void handleExceptionResult(LdapException error) {
 *         // Ignore.
 *     }
 *
 *     {@literal @}Override
 *     public void handleResult(Result result) {
 *         // Ignore.
 *     }
 *
 *     {@literal @}Override
 *     public boolean handleEntry(SearchResultEntry entry) {
 *         final LDIFEntryWriter writer = new LDIFEntryWriter(System.out);
 *         try {
 *             writer.writeEntry(entry);
 *             writer.flush();
 *         } catch (final IOException e) {
 *             // The writer could not write to System.out.
 *         }
 *         return true;
 *     }
 *
 *     {@literal @}Override
 *     public boolean handleReference(SearchResultReference reference) {
 *         System.out.println("Got a reference: " + reference.toString());
 *         return false;
 *     }
 * }
 * </pre>
 *
 * @see VirtualListViewRequestControl
 * @see <a href="http://tools.ietf.org/html/draft-ietf-ldapext-ldapv3-vlv">
 *      draft-ietf-ldapext-ldapv3-vlv - LDAP Extensions for Scrolling View
 *      Browsing of Search Results </a>
 */
public final class VirtualListViewResponseControl implements Control {
    /**
     * The OID for the virtual list view request control.
     */
    public static final String OID = "2.16.840.1.113730.3.4.10";

    /**
     * A decoder which can be used for decoding the virtual list view response
     * control.
     */
    public static final ControlDecoder<VirtualListViewResponseControl> DECODER =
            new ControlDecoder<VirtualListViewResponseControl>() {

                public VirtualListViewResponseControl decodeControl(final Control control,
                        final DecodeOptions options) throws DecodeException {
                    Reject.ifNull(control);

                    if (control instanceof VirtualListViewResponseControl) {
                        return (VirtualListViewResponseControl) control;
                    }

                    if (!control.getOID().equals(OID)) {
                        final LocalizableMessage message =
                                ERR_VLVRES_CONTROL_BAD_OID.get(control.getOID(), OID);
                        throw DecodeException.error(message);
                    }

                    if (!control.hasValue()) {
                        // The response control must always have a value.
                        final LocalizableMessage message = INFO_VLVRES_CONTROL_NO_VALUE.get();
                        throw DecodeException.error(message);
                    }

                    final ASN1Reader reader = ASN1.getReader(control.getValue());
                    try {
                        reader.readStartSequence();

                        final int targetPosition = (int) reader.readInteger();
                        final int contentCount = (int) reader.readInteger();
                        final ResultCode result = ResultCode.valueOf(reader.readEnumerated());
                        ByteString contextID = null;
                        if (reader.hasNextElement()) {
                            contextID = reader.readOctetString();
                        }

                        return new VirtualListViewResponseControl(control.isCritical(),
                                targetPosition, contentCount, result, contextID);
                    } catch (final IOException e) {
                        final LocalizableMessage message =
                                INFO_VLVRES_CONTROL_CANNOT_DECODE_VALUE.get(getExceptionMessage(e));
                        throw DecodeException.error(message, e);
                    }
                }

                public String getOID() {
                    return OID;
                }
            };

    /**
     * Creates a new virtual list view response control.
     *
     * @param targetPosition
     *            The position of the target entry in the result set.
     * @param contentCount
     *            An estimate of the total number of entries in the result set.
     * @param result
     *            The result code indicating the outcome of the virtual list
     *            view request.
     * @param contextID
     *            A server-defined octet string. If present, the contextID
     *            should be sent back to the server by the client in a
     *            subsequent virtual list request.
     * @return The new control.
     * @throws IllegalArgumentException
     *             If {@code targetPosition} or {@code contentCount} were less
     *             than {@code 0}.
     * @throws NullPointerException
     *             If {@code result} was {@code null}.
     */
    public static VirtualListViewResponseControl newControl(final int targetPosition,
            final int contentCount, final ResultCode result, final ByteString contextID) {
        Reject.ifNull(result);
        Reject.ifFalse(targetPosition >= 0, "targetPosition is less than 0");
        Reject.ifFalse(contentCount >= 0, "contentCount is less than 0");

        return new VirtualListViewResponseControl(false, targetPosition, contentCount, result,
                contextID);
    }

    private final int targetPosition;

    private final int contentCount;

    private final ResultCode result;

    private final ByteString contextID;

    private final boolean isCritical;

    private VirtualListViewResponseControl(final boolean isCritical, final int targetPosition,
            final int contentCount, final ResultCode result, final ByteString contextID) {
        this.isCritical = isCritical;
        this.targetPosition = targetPosition;
        this.contentCount = contentCount;
        this.result = result;
        this.contextID = contextID;
    }

    /**
     * Returns the estimated total number of entries in the result set.
     *
     * @return The estimated total number of entries in the result set.
     */
    public int getContentCount() {
        return contentCount;
    }

    /**
     * Returns a server-defined octet string which, if present, should be sent
     * back to the server by the client in a subsequent virtual list request.
     *
     * @return A server-defined octet string which, if present, should be sent
     *         back to the server by the client in a subsequent virtual list
     *         request, or {@code null} if there is no context ID.
     */
    public ByteString getContextID() {
        return contextID;
    }

    /** {@inheritDoc} */
    public String getOID() {
        return OID;
    }

    /**
     * Returns result code indicating the outcome of the virtual list view
     * request.
     *
     * @return The result code indicating the outcome of the virtual list view
     *         request.
     */
    public ResultCode getResult() {
        return result;
    }

    /**
     * Returns the position of the target entry in the result set.
     *
     * @return The position of the target entry in the result set.
     */
    public int getTargetPosition() {
        return targetPosition;
    }

    /** {@inheritDoc} */
    public ByteString getValue() {
        final ByteStringBuilder buffer = new ByteStringBuilder();
        final ASN1Writer writer = ASN1.getWriter(buffer);
        try {
            writer.writeStartSequence();
            writer.writeInteger(targetPosition);
            writer.writeInteger(contentCount);
            writer.writeEnumerated(result.intValue());
            if (contextID != null) {
                writer.writeOctetString(contextID);
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
        builder.append("VirtualListViewResponseControl(oid=");
        builder.append(getOID());
        builder.append(", criticality=");
        builder.append(isCritical());
        builder.append(", targetPosition=");
        builder.append(targetPosition);
        builder.append(", contentCount=");
        builder.append(contentCount);
        builder.append(", result=");
        builder.append(result);
        if (contextID != null) {
            builder.append(", contextID=");
            builder.append(contextID);
        }
        builder.append(")");
        return builder.toString();
    }

}
