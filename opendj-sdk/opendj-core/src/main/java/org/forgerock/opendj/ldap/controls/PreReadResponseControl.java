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
 *      Portions copyright 2012-2014 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.controls;

import static com.forgerock.opendj.ldap.CoreMessages.*;

import java.io.IOException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.LDAP;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.Entries;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.util.Reject;

/**
 * The pre-read response control as defined in RFC 4527. This control is
 * returned by the server in response to a successful update operation which
 * included a pre-read request control. The control contains a Search Result
 * Entry containing, subject to access controls and other constraints, values of
 * the requested attributes.
 * <p>
 * The following example gets the entry as it was before the modify operation.
 *
 * <pre>
 * Connection connection = ...;
 * String DN = ...;
 *
 * ModifyRequest request =
 *         Requests.newModifyRequest(DN)
 *         .addControl(PreReadRequestControl.newControl(true, "mail"))
 *         .addModification(ModificationType.REPLACE,
 *                 "mail", "modified@example.com");
 *
 * Result result = connection.modify(request);
 * PreReadResponseControl control =
 *             result.getControl(PreReadResponseControl.DECODER,
 *                     new DecodeOptions());
 * Entry unmodifiedEntry = control.getEntry();
 * </pre>
 *
 * @see PreReadRequestControl
 * @see <a href="http://tools.ietf.org/html/rfc4527">RFC 4527 - Lightweight
 *      Directory Access Protocol (LDAP) Read Entry Controls </a>
 */
public final class PreReadResponseControl implements Control {

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
    /**
     * The IANA-assigned OID for the LDAP pre-read response control used for
     * retrieving an entry in the state it had immediately before an update was
     * applied.
     */
    public static final String OID = PreReadRequestControl.OID;

    /**
     * A decoder which can be used for decoding the pre-read response control.
     */
    public static final ControlDecoder<PreReadResponseControl> DECODER =
            new ControlDecoder<PreReadResponseControl>() {

                public PreReadResponseControl decodeControl(final Control control,
                        final DecodeOptions options) throws DecodeException {
                    Reject.ifNull(control);

                    if (control instanceof PreReadResponseControl) {
                        return (PreReadResponseControl) control;
                    }

                    if (!control.getOID().equals(OID)) {
                        final LocalizableMessage message =
                                ERR_PREREAD_CONTROL_BAD_OID.get(control.getOID(), OID);
                        throw DecodeException.error(message);
                    }

                    if (!control.hasValue()) {
                        // The control must always have a value.
                        final LocalizableMessage message = ERR_PREREADRESP_NO_CONTROL_VALUE.get();
                        throw DecodeException.error(message);
                    }

                    final ASN1Reader reader = ASN1.getReader(control.getValue());
                    final Entry entry;
                    try {
                        entry = LDAP.readEntry(reader, options);
                    } catch (final IOException le) {
                        logger.debug(LocalizableMessage.raw("Unable to read result entry", le));
                        final LocalizableMessage message =
                                ERR_PREREADRESP_CANNOT_DECODE_VALUE.get(le.getMessage());
                        throw DecodeException.error(message, le);
                    }

                    /*
                     * FIXME: the RFC states that the control contains a
                     * SearchResultEntry rather than an Entry. Can we assume
                     * that the response will not contain a nested set of
                     * controls?
                     */
                    return new PreReadResponseControl(control.isCritical(), Entries
                            .unmodifiableEntry(entry));
                }

                public String getOID() {
                    return OID;
                }
            };

    /**
     * Creates a new pre-read response control.
     *
     * @param entry
     *            The entry whose contents reflect the state of the updated
     *            entry immediately before the update operation was performed.
     * @return The new control.
     * @throws NullPointerException
     *             If {@code entry} was {@code null}.
     */
    public static PreReadResponseControl newControl(final Entry entry) {
        /*
         * FIXME: all other control implementations are fully immutable. We
         * should really do a defensive copy here in order to be consistent,
         * rather than just wrap it. Also, the RFC states that the control
         * contains a SearchResultEntry rather than an Entry. Can we assume that
         * the response will not contain a nested set of controls?
         */
        return new PreReadResponseControl(false, Entries.unmodifiableEntry(entry));
    }

    private final Entry entry;

    private final boolean isCritical;

    private PreReadResponseControl(final boolean isCritical, final Entry entry) {
        this.isCritical = isCritical;
        this.entry = entry;
    }

    /**
     * Returns an unmodifiable entry whose contents reflect the state of the
     * updated entry immediately before the update operation was performed.
     *
     * @return The unmodifiable entry whose contents reflect the state of the
     *         updated entry immediately before the update operation was
     *         performed.
     */
    public Entry getEntry() {
        return entry;
    }

    /** {@inheritDoc} */
    public String getOID() {
        return OID;
    }

    /** {@inheritDoc} */
    public ByteString getValue() {
        try {
            final ByteStringBuilder buffer = new ByteStringBuilder();
            LDAP.writeEntry(ASN1.getWriter(buffer), entry);
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
        builder.append("PreReadResponseControl(oid=");
        builder.append(getOID());
        builder.append(", criticality=");
        builder.append(isCritical());
        builder.append(", entry=");
        builder.append(entry);
        builder.append(")");
        return builder.toString();
    }
}
