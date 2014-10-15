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
 */
package org.forgerock.opendj.ldap.controls;

import static com.forgerock.opendj.ldap.CoreMessages.ERR_MANAGEDSAIT_CONTROL_BAD_OID;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_MANAGEDSAIT_INVALID_CONTROL_VALUE;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;

import org.forgerock.util.Reject;

/**
 * The ManageDsaIT request control as defined in RFC 3296. This control allows
 * manipulation of referral and other special objects as normal objects.
 * <p>
 * When this control is present in the request, the server will not generate a
 * referral or continuation reference based upon information held in referral
 * objects and instead will treat the referral object as a normal entry. The
 * server, however, is still free to return referrals for other reasons.
 *
 * <pre>
 * // &quot;dc=ref,dc=com&quot; holds a referral to something else.
 *
 * // Referral without the ManageDsaIT control:
 * SearchRequest request = Requests.newSearchRequest(
 *          &quot;dc=ref,dc=com&quot;,
 *          SearchScope.SUBORDINATES,
 *          &quot;(objectclass=*)&quot;,
 *          &quot;&quot;);
 *
 * ConnectionEntryReader reader = connection.search(request);
 * while (reader.hasNext()) {
 *     if (reader.isReference()) {
 *         SearchResultReference ref = reader.readReference();
 *         // References: ref.getURIs()
 *     }
 * }
 *
 * // Referral with the ManageDsaIT control:
 * request.addControl(ManageDsaITRequestControl.newControl(true));
 * SearchResultEntry entry = connection.searchSingleEntry(request);
 * // ...
 * </pre>
 *
 * @see <a href="http://tools.ietf.org/html/rfc3296">RFC 3296 - Named
 *      Subordinate References in Lightweight Directory Access Protocol (LDAP)
 *      Directories </a>
 */
public final class ManageDsaITRequestControl implements Control {
    /**
     * The OID for the ManageDsaIT request control.
     */
    public static final String OID = "2.16.840.1.113730.3.4.2";

    private static final ManageDsaITRequestControl CRITICAL_INSTANCE =
            new ManageDsaITRequestControl(true);
    private static final ManageDsaITRequestControl NONCRITICAL_INSTANCE =
            new ManageDsaITRequestControl(false);

    /**
     * A decoder which can be used for decoding the Manage DsaIT request
     * control.
     */
    public static final ControlDecoder<ManageDsaITRequestControl> DECODER =
            new ControlDecoder<ManageDsaITRequestControl>() {

                public ManageDsaITRequestControl decodeControl(final Control control,
                        final DecodeOptions options) throws DecodeException {
                    Reject.ifNull(control);

                    if (control instanceof ManageDsaITRequestControl) {
                        return (ManageDsaITRequestControl) control;
                    }

                    if (!control.getOID().equals(OID)) {
                        final LocalizableMessage message =
                                ERR_MANAGEDSAIT_CONTROL_BAD_OID.get(control.getOID(), OID);
                        throw DecodeException.error(message);
                    }

                    if (control.hasValue()) {
                        final LocalizableMessage message =
                                ERR_MANAGEDSAIT_INVALID_CONTROL_VALUE.get();
                        throw DecodeException.error(message);
                    }

                    return control.isCritical() ? CRITICAL_INSTANCE : NONCRITICAL_INSTANCE;
                }

                public String getOID() {
                    return OID;
                }
            };

    /**
     * Creates a new ManageDsaIT request control having the provided
     * criticality.
     *
     * @param isCritical
     *            {@code true} if it is unacceptable to perform the operation
     *            without applying the semantics of this control, or
     *            {@code false} if it can be ignored.
     * @return The new control.
     */
    public static ManageDsaITRequestControl newControl(final boolean isCritical) {
        return isCritical ? CRITICAL_INSTANCE : NONCRITICAL_INSTANCE;
    }

    private final boolean isCritical;

    private ManageDsaITRequestControl(final boolean isCritical) {
        this.isCritical = isCritical;
    }

    /** {@inheritDoc} */
    public String getOID() {
        return OID;
    }

    /** {@inheritDoc} */
    public ByteString getValue() {
        return null;
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
        builder.append("ManageDsaITRequestControl(oid=");
        builder.append(getOID());
        builder.append(", criticality=");
        builder.append(isCritical());
        builder.append(")");
        return builder.toString();
    }

}
