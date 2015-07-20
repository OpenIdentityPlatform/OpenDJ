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

import static com.forgerock.opendj.util.StaticUtils.byteToHex;
import static com.forgerock.opendj.util.StaticUtils.getExceptionMessage;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_VLVREQ_CONTROL_BAD_OID;
import static com.forgerock.opendj.ldap.CoreMessages.INFO_VLVREQ_CONTROL_CANNOT_DECODE_VALUE;
import static com.forgerock.opendj.ldap.CoreMessages.INFO_VLVREQ_CONTROL_INVALID_TARGET_TYPE;
import static com.forgerock.opendj.ldap.CoreMessages.INFO_VLVREQ_CONTROL_NO_VALUE;

import java.io.IOException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.util.Reject;

/**
 * The virtual list view request control as defined in
 * draft-ietf-ldapext-ldapv3-vlv. This control allows a client to specify that
 * the server return, for a given search request with associated sort keys, a
 * contiguous subset of the search result set. This subset is specified in terms
 * of offsets into the ordered list, or in terms of a greater than or equal
 * assertion value.
 * <p>
 * This control must be used in conjunction with the server-side sort request
 * control in order to ensure that results are returned in a consistent order.
 * <p>
 * This control is similar to the simple paged results request control, except
 * that it allows the client to move backwards and forwards in the result set.
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
 * @see VirtualListViewResponseControl
 * @see ServerSideSortRequestControl
 * @see <a href="http://tools.ietf.org/html/draft-ietf-ldapext-ldapv3-vlv">
 *      draft-ietf-ldapext-ldapv3-vlv - LDAP Extensions for Scrolling View
 *      Browsing of Search Results </a>
 */
public final class VirtualListViewRequestControl implements Control {
    /**
     * The OID for the virtual list view request control.
     */
    public static final String OID = "2.16.840.1.113730.3.4.9";

    /**
     * A decoder which can be used for decoding the virtual list view request
     * control.
     */
    public static final ControlDecoder<VirtualListViewRequestControl> DECODER =
            new ControlDecoder<VirtualListViewRequestControl>() {

                public VirtualListViewRequestControl decodeControl(final Control control,
                        final DecodeOptions options) throws DecodeException {
                    Reject.ifNull(control);

                    if (control instanceof VirtualListViewRequestControl) {
                        return (VirtualListViewRequestControl) control;
                    }

                    if (!control.getOID().equals(OID)) {
                        final LocalizableMessage message =
                                ERR_VLVREQ_CONTROL_BAD_OID.get(control.getOID(), OID);
                        throw DecodeException.error(message);
                    }

                    if (!control.hasValue()) {
                        // The request control must always have a value.
                        final LocalizableMessage message = INFO_VLVREQ_CONTROL_NO_VALUE.get();
                        throw DecodeException.error(message);
                    }

                    final ASN1Reader reader = ASN1.getReader(control.getValue());
                    try {
                        reader.readStartSequence();

                        final int beforeCount = (int) reader.readInteger();
                        final int afterCount = (int) reader.readInteger();

                        int offset = -1;
                        int contentCount = -1;
                        ByteString assertionValue = null;
                        final byte targetType = reader.peekType();
                        switch (targetType) {
                        case TYPE_TARGET_BYOFFSET:
                            reader.readStartSequence();
                            offset = (int) reader.readInteger();
                            contentCount = (int) reader.readInteger();
                            reader.readEndSequence();
                            break;
                        case TYPE_TARGET_GREATERTHANOREQUAL:
                            assertionValue = reader.readOctetString();
                            break;
                        default:
                            final LocalizableMessage message =
                                    INFO_VLVREQ_CONTROL_INVALID_TARGET_TYPE
                                            .get(byteToHex(targetType));
                            throw DecodeException.error(message);
                        }

                        ByteString contextID = null;
                        if (reader.hasNextElement()) {
                            contextID = reader.readOctetString();
                        }

                        return new VirtualListViewRequestControl(control.isCritical(), beforeCount,
                                afterCount, contentCount, offset, assertionValue, contextID);
                    } catch (final IOException e) {
                        final LocalizableMessage message =
                                INFO_VLVREQ_CONTROL_CANNOT_DECODE_VALUE.get(getExceptionMessage(e));
                        throw DecodeException.error(message, e);
                    }
                }

                public String getOID() {
                    return OID;
                }
            };

    /**
     * The BER type to use when encoding the byOffset target element.
     */
    private static final byte TYPE_TARGET_BYOFFSET = (byte) 0xA0;

    /**
     * The BER type to use when encoding the greaterThanOrEqual target element.
     */
    private static final byte TYPE_TARGET_GREATERTHANOREQUAL = (byte) 0x81;

    /**
     * Creates a new virtual list view request control that will identify the
     * target entry by an assertion value. The assertion value is encoded
     * according to the ORDERING matching rule for the attribute description in
     * the sort control. The assertion value is used to determine the target
     * entry by comparison with the values of the attribute specified as the
     * primary sort key. The first list entry who's value is no less than (less
     * than or equal to when the sort order is reversed) the supplied value is
     * the target entry.
     *
     * @param isCritical
     *            {@code true} if it is unacceptable to perform the operation
     *            without applying the semantics of this control, or
     *            {@code false} if it can be ignored.
     * @param assertionValue
     *            The assertion value that will be used to locate the target
     *            entry.
     * @param beforeCount
     *            The number of entries before the target entry to be included
     *            in the search results.
     * @param afterCount
     *            The number of entries after the target entry to be included in
     *            the search results.
     * @param contextID
     *            The context ID provided by the server in the last virtual list
     *            view response for the same set of criteria, or {@code null} if
     *            there was no previous virtual list view response or the server
     *            did not include a context ID in the last response.
     * @return The new control.
     * @throws IllegalArgumentException
     *             If {@code beforeCount} or {@code afterCount} were less than
     *             {@code 0}.
     * @throws NullPointerException
     *             If {@code assertionValue} was {@code null}.
     */
    public static VirtualListViewRequestControl newAssertionControl(final boolean isCritical,
            final ByteString assertionValue, final int beforeCount, final int afterCount,
            final ByteString contextID) {
        Reject.ifNull(assertionValue);
        Reject.ifFalse(beforeCount >= 0, "beforeCount is less than 0");
        Reject.ifFalse(afterCount >= 0, "afterCount is less than 0");

        return new VirtualListViewRequestControl(isCritical, beforeCount, afterCount, -1, -1,
                assertionValue, contextID);
    }

    /**
     * Creates a new virtual list view request control that will identify the
     * target entry by a positional offset within the complete result set.
     *
     * @param isCritical
     *            {@code true} if it is unacceptable to perform the operation
     *            without applying the semantics of this control, or
     *            {@code false} if it can be ignored.
     * @param offset
     *            The positional offset of the target entry in the result set,
     *            where {@code 1} is the first entry.
     * @param contentCount
     *            The content count returned by the server in the last virtual
     *            list view response, or {@code 0} if this is the first virtual
     *            list view request.
     * @param beforeCount
     *            The number of entries before the target entry to be included
     *            in the search results.
     * @param afterCount
     *            The number of entries after the target entry to be included in
     *            the search results.
     * @param contextID
     *            The context ID provided by the server in the last virtual list
     *            view response for the same set of criteria, or {@code null} if
     *            there was no previous virtual list view response or the server
     *            did not include a context ID in the last response.
     * @return The new control.
     * @throws IllegalArgumentException
     *             If {@code beforeCount}, {@code afterCount}, or
     *             {@code contentCount} were less than {@code 0}, or if
     *             {@code offset} was less than {@code 1}.
     */
    public static VirtualListViewRequestControl newOffsetControl(final boolean isCritical,
            final int offset, final int contentCount, final int beforeCount, final int afterCount,
            final ByteString contextID) {
        Reject.ifFalse(beforeCount >= 0, "beforeCount is less than 0");
        Reject.ifFalse(afterCount >= 0, "afterCount is less than 0");
        Reject.ifFalse(offset > 0, "offset is less than 1");
        Reject.ifFalse(contentCount >= 0, "contentCount is less than 0");

        return new VirtualListViewRequestControl(isCritical, beforeCount, afterCount, contentCount,
                offset, null, contextID);
    }

    private final int beforeCount;

    private final int afterCount;

    private final ByteString contextID;

    private final boolean isCritical;

    private final int contentCount;

    private final int offset;

    private final ByteString assertionValue;

    private VirtualListViewRequestControl(final boolean isCritical, final int beforeCount,
            final int afterCount, final int contentCount, final int offset,
            final ByteString assertionValue, final ByteString contextID) {
        this.isCritical = isCritical;
        this.beforeCount = beforeCount;
        this.afterCount = afterCount;
        this.contentCount = contentCount;
        this.offset = offset;
        this.assertionValue = assertionValue;
        this.contextID = contextID;
    }

    /**
     * Returns the number of entries after the target entry to be included in
     * the search results.
     *
     * @return The number of entries after the target entry to be included in
     *         the search results.
     */
    public int getAfterCount() {
        return afterCount;
    }

    /**
     * Returns the assertion value that will be used to locate the target entry,
     * if applicable.
     *
     * @return The assertion value that will be used to locate the target entry,
     *         or {@code null} if this control is using a target offset.
     */
    public ByteString getAssertionValue() {
        return assertionValue;
    }

    /**
     * Returns the assertion value that will be used to locate the target entry,
     * if applicable, decoded as a UTF-8 string.
     *
     * @return The assertion value that will be used to locate the target entry
     *         decoded as a UTF-8 string, or {@code null} if this control is
     *         using a target offset.
     */
    public String getAssertionValueAsString() {
        return assertionValue != null ? assertionValue.toString() : null;
    }

    /**
     * Returns the number of entries before the target entry to be included in
     * the search results.
     *
     * @return The number of entries before the target entry to be included in
     *         the search results.
     */
    public int getBeforeCount() {
        return beforeCount;
    }

    /**
     * Returns the content count returned by the server in the last virtual list
     * view response, if applicable.
     *
     * @return The content count returned by the server in the last virtual list
     *         view response, which may be {@code 0} if this is the first
     *         virtual list view request, or {@code -1} if this control is using
     *         a target assertion.
     */
    public int getContentCount() {
        return contentCount;
    }

    /**
     * Returns the context ID provided by the server in the last virtual list
     * view response for the same set of criteria, or {@code null} if there was
     * no previous virtual list view response or the server did not include a
     * context ID in the last response.
     *
     * @return The context ID provided by the server in the last virtual list
     *         view response, or {@code null} if unavailable.
     */
    public ByteString getContextID() {
        return contextID;
    }

    /**
     * Returns the positional offset of the target entry in the result set, if
     * applicable, where {@code 1} is the first entry.
     *
     * @return The positional offset of the target entry in the result set, or
     *         {@code -1} if this control is using a target assertion.
     */
    public int getOffset() {
        return offset;
    }

    /** {@inheritDoc} */
    public String getOID() {
        return OID;
    }

    /** {@inheritDoc} */
    public ByteString getValue() {
        final ByteStringBuilder buffer = new ByteStringBuilder();
        final ASN1Writer writer = ASN1.getWriter(buffer);
        try {
            writer.writeStartSequence();
            writer.writeInteger(beforeCount);
            writer.writeInteger(afterCount);
            if (hasTargetOffset()) {
                writer.writeStartSequence(TYPE_TARGET_BYOFFSET);
                writer.writeInteger(offset);
                writer.writeInteger(contentCount);
                writer.writeEndSequence();
            } else {
                writer.writeOctetString(TYPE_TARGET_GREATERTHANOREQUAL, assertionValue);
            }
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

    /**
     * Returns {@code true} if this control is using a target offset, or
     * {@code false} if this control is using a target assertion.
     *
     * @return {@code true} if this control is using a target offset, or
     *         {@code false} if this control is using a target assertion.
     */
    public boolean hasTargetOffset() {
        return assertionValue == null;
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
        builder.append("VirtualListViewRequestControl(oid=");
        builder.append(getOID());
        builder.append(", criticality=");
        builder.append(isCritical());
        builder.append(", beforeCount=");
        builder.append(beforeCount);
        builder.append(", afterCount=");
        builder.append(afterCount);
        if (hasTargetOffset()) {
            builder.append(", offset=");
            builder.append(offset);
            builder.append(", contentCount=");
            builder.append(contentCount);
        } else {
            builder.append(", greaterThanOrEqual=");
            builder.append(assertionValue);
        }
        if (contextID != null) {
            builder.append(", contextID=");
            builder.append(contextID);
        }
        builder.append(")");
        return builder.toString();
    }
}
