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

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;
import static com.forgerock.opendj.ldap.CoreMessages.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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
 * The pre-read request control as defined in RFC 4527. This control allows the
 * client to read the target entry of an update operation immediately before the
 * modifications are applied. These reads are done as an atomic part of the
 * update operation.
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
 * @see PreReadResponseControl
 * @see <a href="http://tools.ietf.org/html/rfc4527">RFC 4527 - Lightweight
 *      Directory Access Protocol (LDAP) Read Entry Controls </a>
 */
public final class PreReadRequestControl implements Control {

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
    /**
     * The IANA-assigned OID for the LDAP pre-read request control used for
     * retrieving an entry in the state it had immediately before an update was
     * applied.
     */
    public static final String OID = "1.3.6.1.1.13.1";

    /** The list of raw attributes to return in the entry. */
    private final List<String> attributes;

    private final boolean isCritical;

    private static final PreReadRequestControl CRITICAL_EMPTY_INSTANCE = new PreReadRequestControl(
            true, Collections.<String> emptyList());

    private static final PreReadRequestControl NONCRITICAL_EMPTY_INSTANCE =
            new PreReadRequestControl(false, Collections.<String> emptyList());

    /**
     * A decoder which can be used for decoding the pre-read request control.
     */
    public static final ControlDecoder<PreReadRequestControl> DECODER =
            new ControlDecoder<PreReadRequestControl>() {

                public PreReadRequestControl decodeControl(final Control control,
                        final DecodeOptions options) throws DecodeException {
                    Reject.ifNull(control);

                    if (control instanceof PreReadRequestControl) {
                        return (PreReadRequestControl) control;
                    }

                    if (!control.getOID().equals(OID)) {
                        final LocalizableMessage message =
                                ERR_PREREAD_CONTROL_BAD_OID.get(control.getOID(), OID);
                        throw DecodeException.error(message);
                    }

                    if (!control.hasValue()) {
                        // The control must always have a value.
                        final LocalizableMessage message = ERR_PREREADREQ_NO_CONTROL_VALUE.get();
                        throw DecodeException.error(message);
                    }

                    final ASN1Reader reader = ASN1.getReader(control.getValue());
                    List<String> attributes;
                    try {
                        reader.readStartSequence();
                        if (reader.hasNextElement()) {
                            final String firstAttribute = reader.readOctetStringAsString();
                            if (reader.hasNextElement()) {
                                attributes = new ArrayList<>();
                                attributes.add(firstAttribute);
                                do {
                                    attributes.add(reader.readOctetStringAsString());
                                } while (reader.hasNextElement());
                                attributes = unmodifiableList(attributes);
                            } else {
                                attributes = singletonList(firstAttribute);
                            }
                        } else {
                            attributes = emptyList();
                        }
                        reader.readEndSequence();
                    } catch (final Exception ex) {
                        logger.debug(LocalizableMessage.raw("Unable to read sequence", ex));

                        final LocalizableMessage message =
                                ERR_PREREADREQ_CANNOT_DECODE_VALUE.get(ex.getMessage());
                        throw DecodeException.error(message, ex);
                    }

                    if (attributes.isEmpty()) {
                        return control.isCritical() ? CRITICAL_EMPTY_INSTANCE
                                : NONCRITICAL_EMPTY_INSTANCE;
                    } else {
                        return new PreReadRequestControl(control.isCritical(), attributes);
                    }
                }

                public String getOID() {
                    return OID;
                }
            };

    /**
     * Creates a new pre-read request control.
     *
     * @param isCritical
     *            {@code true} if it is unacceptable to perform the operation
     *            without applying the semantics of this control, or
     *            {@code false} if it can be ignored
     * @param attributes
     *            The list of attributes to be included with the response
     *            control. Attributes that are sub-types of listed attributes
     *            are implicitly included. The list may be empty, indicating
     *            that all user attributes should be returned.
     * @return The new control.
     * @throws NullPointerException
     *             If {@code attributes} was {@code null}.
     */
    public static PreReadRequestControl newControl(final boolean isCritical,
            final Collection<String> attributes) {
        Reject.ifNull(attributes);

        if (attributes.isEmpty()) {
            return isCritical ? CRITICAL_EMPTY_INSTANCE : NONCRITICAL_EMPTY_INSTANCE;
        } else if (attributes.size() == 1) {
            return new PreReadRequestControl(isCritical,
                    singletonList(attributes.iterator().next()));
        } else {
            return new PreReadRequestControl(isCritical, unmodifiableList(new ArrayList<String>(
                    attributes)));
        }
    }

    /**
     * Creates a new pre-read request control.
     *
     * @param isCritical
     *            {@code true} if it is unacceptable to perform the operation
     *            without applying the semantics of this control, or
     *            {@code false} if it can be ignored
     * @param attributes
     *            The list of attributes to be included with the response
     *            control. Attributes that are sub-types of listed attributes
     *            are implicitly included. The list may be empty, indicating
     *            that all user attributes should be returned.
     * @return The new control.
     * @throws NullPointerException
     *             If {@code attributes} was {@code null}.
     */
    public static PreReadRequestControl newControl(final boolean isCritical,
            final String... attributes) {
        Reject.ifNull((Object) attributes);

        if (attributes.length == 0) {
            return isCritical ? CRITICAL_EMPTY_INSTANCE : NONCRITICAL_EMPTY_INSTANCE;
        } else if (attributes.length == 1) {
            return new PreReadRequestControl(isCritical, singletonList(attributes[0]));
        } else {
            return new PreReadRequestControl(isCritical, unmodifiableList(new ArrayList<String>(
                    asList(attributes))));
        }
    }

    private PreReadRequestControl(final boolean isCritical, final List<String> attributes) {
        this.isCritical = isCritical;
        this.attributes = attributes;
    }

    /**
     * Returns an unmodifiable list containing the names of attributes to be
     * included with the response control. Attributes that are sub-types of
     * listed attributes are implicitly included. The returned list may be
     * empty, indicating that all user attributes should be returned.
     *
     * @return An unmodifiable list containing the names of attributes to be
     *         included with the response control.
     */
    public List<String> getAttributes() {
        return attributes;
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
            if (attributes != null) {
                for (final String attr : attributes) {
                    writer.writeOctetString(attr);
                }
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
        builder.append("PreReadRequestControl(oid=");
        builder.append(getOID());
        builder.append(", criticality=");
        builder.append(isCritical());
        builder.append(", attributes=");
        builder.append(attributes);
        builder.append(")");
        return builder.toString();
    }

}
