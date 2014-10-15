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
 *      Portions copyright 2011-2014 ForgeRock AS
 */
package org.forgerock.opendj.ldap.controls;

import static com.forgerock.opendj.util.StaticUtils.getExceptionMessage;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_SUBENTRIES_CANNOT_DECODE_VALUE;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_SUBENTRIES_CONTROL_BAD_OID;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_SUBENTRIES_NO_CONTROL_VALUE;

import java.io.IOException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.util.Reject;

/**
 * The sub-entries request control as defined in RFC 3672. This control may be
 * included in a search request to indicate that sub-entries should be included
 * in the search results.
 * <p>
 * In the absence of the sub-entries request control, sub-entries are not
 * visible to search operations unless the target/base of the operation is a
 * sub-entry. In the presence of the sub-entry request control, sub-entries are
 * visible if and only if the control's value is {@code TRUE}.
 * <p>
 * Consider "Class of Service" sub-entries such as the following:
 *
 * <pre>
 * dn: cn=Gold Class of Service,dc=example,dc=com
 * objectClass: collectiveAttributeSubentry
 * objectClass: extensibleObject
 * objectClass: subentry
 * objectClass: top
 * cn: Gold Class of Service
 * diskQuota;collective: 100 GB
 * mailQuota;collective: 10 GB
 * subtreeSpecification: { base "ou=People", specificationFilter "(classOfService=
 *  gold)" }
 * </pre>
 *
 * To access the sub-entries in your search, use the control with value
 * {@code TRUE}.
 *
 * <pre>
 * Connection connection = ...;
 *
 * SearchRequest request = Requests.newSearchRequest("dc=example,dc=com",
 *         SearchScope.WHOLE_SUBTREE, "cn=*Class of Service", "cn", "subtreeSpecification")
 *         .addControl(SubentriesRequestControl.newControl(true, true));
 *  
 * ConnectionEntryReader reader = connection.search(request);
 * while (reader.hasNext()) {
 *     if (reader.isEntry()) {
 *         SearchResultEntry entry = reader.readEntry();
 *         // ...
 *     }
 * }
 * </pre>
 *
 * @see <a href="http://tools.ietf.org/html/rfc3672">RFC 3672 - Subentries in
 *      the Lightweight Directory Access Protocol </a>
 */
public final class SubentriesRequestControl implements Control {

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
    /**
     * The OID for the sub-entries request control.
     */
    public static final String OID = "1.3.6.1.4.1.4203.1.10.1";

    private static final SubentriesRequestControl CRITICAL_VISIBLE_INSTANCE =
            new SubentriesRequestControl(true, true);
    private static final SubentriesRequestControl NONCRITICAL_VISIBLE_INSTANCE =
            new SubentriesRequestControl(false, true);
    private static final SubentriesRequestControl CRITICAL_INVISIBLE_INSTANCE =
            new SubentriesRequestControl(true, false);
    private static final SubentriesRequestControl NONCRITICAL_INVISIBLE_INSTANCE =
            new SubentriesRequestControl(false, false);

    /**
     * A decoder which can be used for decoding the sub-entries request control.
     */
    public static final ControlDecoder<SubentriesRequestControl> DECODER =
            new ControlDecoder<SubentriesRequestControl>() {

                public SubentriesRequestControl decodeControl(final Control control,
                        final DecodeOptions options) throws DecodeException {
                    Reject.ifNull(control);

                    if (control instanceof SubentriesRequestControl) {
                        return (SubentriesRequestControl) control;
                    }

                    if (!control.getOID().equals(OID)) {
                        final LocalizableMessage message =
                                ERR_SUBENTRIES_CONTROL_BAD_OID.get(control.getOID(), OID);
                        throw DecodeException.error(message);
                    }

                    if (!control.hasValue()) {
                        // The response control must always have a value.
                        final LocalizableMessage message = ERR_SUBENTRIES_NO_CONTROL_VALUE.get();
                        throw DecodeException.error(message);
                    }

                    final ASN1Reader reader = ASN1.getReader(control.getValue());
                    final boolean visibility;
                    try {
                        visibility = reader.readBoolean();
                    } catch (final IOException e) {
                        logger.debug(LocalizableMessage.raw("Unable to read visbility", e));
                        final LocalizableMessage message =
                                ERR_SUBENTRIES_CANNOT_DECODE_VALUE.get(getExceptionMessage(e));
                        throw DecodeException.error(message);
                    }

                    return newControl(control.isCritical(), visibility);
                }

                public String getOID() {
                    return OID;
                }
            };

    /**
     * Creates a new sub-entries request control having the provided criticality
     * and sub-entry visibility.
     *
     * @param isCritical
     *            {@code true} if it is unacceptable to perform the operation
     *            without applying the semantics of this control, or
     *            {@code false} if it can be ignored.
     * @param visibility
     *            {@code true} if sub-entries should be included in the search
     *            results and normal entries excluded, or {@code false} if
     *            normal entries should be included and sub-entries excluded.
     * @return The new control.
     */
    public static SubentriesRequestControl newControl(final boolean isCritical,
            final boolean visibility) {
        if (isCritical) {
            return visibility ? CRITICAL_VISIBLE_INSTANCE : CRITICAL_INVISIBLE_INSTANCE;
        } else {
            return visibility ? NONCRITICAL_VISIBLE_INSTANCE : NONCRITICAL_INVISIBLE_INSTANCE;
        }
    }

    private final boolean isCritical;
    private final boolean visibility;

    private SubentriesRequestControl(final boolean isCritical, final boolean visibility) {
        this.isCritical = isCritical;
        this.visibility = visibility;
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
            writer.writeBoolean(visibility);
            return buffer.toByteString();
        } catch (final IOException ioe) {
            // This should never happen unless there is a bug somewhere.
            throw new RuntimeException(ioe);
        }
    }

    /**
     * Returns a boolean indicating whether or not sub-entries should be
     * included in the search results.
     *
     * @return {@code true} if sub-entries should be included in the search
     *         results and normal entries excluded, or {@code false} if normal
     *         entries should be included and sub-entries excluded.
     */
    public boolean getVisibility() {
        return visibility;
    }

    /** {@inheritDoc} */
    public boolean hasValue() {
        return false;
    }

    /** {@inheritDoc} */
    public boolean isCritical() {
        return isCritical;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("SubentriesRequestControl(oid=");
        builder.append(getOID());
        builder.append(", criticality=");
        builder.append(isCritical());
        builder.append(", visibility=");
        builder.append(getVisibility());
        builder.append(")");
        return builder.toString();
    }

}
