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
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.controls;

import static org.forgerock.opendj.ldap.CoreMessages.ERR_POSTREADREQ_CANNOT_DECODE_VALUE;
import static org.forgerock.opendj.ldap.CoreMessages.ERR_POSTREADREQ_NO_CONTROL_VALUE;
import static org.forgerock.opendj.ldap.CoreMessages.ERR_POSTREAD_CONTROL_BAD_OID;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.asn1.ASN1;
import org.forgerock.opendj.asn1.ASN1Reader;
import org.forgerock.opendj.asn1.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;

import com.forgerock.opendj.util.StaticUtils;
import com.forgerock.opendj.util.Validator;

/**
 * The post-read request control as defined in RFC 4527. This control allows the
 * client to read the target entry of an update operation immediately after the
 * modifications are applied. These reads are done as an atomic part of the
 * update operation.
 *
 * @see PostReadResponseControl
 * @see <a href="http://tools.ietf.org/html/rfc4527">RFC 4527 - Lightweight
 *      Directory Access Protocol (LDAP) Read Entry Controls </a>
 */
public final class PostReadRequestControl implements Control {
    /**
     * The IANA-assigned OID for the LDAP post-read request control used for
     * retrieving an entry in the state it had immediately after an update was
     * applied.
     */
    public static final String OID = "1.3.6.1.1.13.2";

    // The set of raw attributes to return in the entry.
    private final Set<String> attributes;

    private final boolean isCritical;

    private static final PostReadRequestControl CRITICAL_EMPTY_INSTANCE =
            new PostReadRequestControl(true, Collections.<String> emptySet());

    private static final PostReadRequestControl NONCRITICAL_EMPTY_INSTANCE =
            new PostReadRequestControl(false, Collections.<String> emptySet());

    /**
     * A decoder which can be used for decoding the post-read request control.
     */
    public static final ControlDecoder<PostReadRequestControl> DECODER =
            new ControlDecoder<PostReadRequestControl>() {

                public PostReadRequestControl decodeControl(final Control control,
                        final DecodeOptions options) throws DecodeException {
                    Validator.ensureNotNull(control);

                    if (control instanceof PostReadRequestControl) {
                        return (PostReadRequestControl) control;
                    }

                    if (!control.getOID().equals(OID)) {
                        final LocalizableMessage message =
                                ERR_POSTREAD_CONTROL_BAD_OID.get(control.getOID(), OID);
                        throw DecodeException.error(message);
                    }

                    if (!control.hasValue()) {
                        // The control must always have a value.
                        final LocalizableMessage message = ERR_POSTREADREQ_NO_CONTROL_VALUE.get();
                        throw DecodeException.error(message);
                    }

                    final ASN1Reader reader = ASN1.getReader(control.getValue());
                    Set<String> attributes;
                    try {
                        reader.readStartSequence();
                        if (reader.hasNextElement()) {
                            final String firstAttribute = reader.readOctetStringAsString();
                            if (reader.hasNextElement()) {
                                attributes = new LinkedHashSet<String>();
                                attributes.add(firstAttribute);
                                do {
                                    attributes.add(reader.readOctetStringAsString());
                                } while (reader.hasNextElement());
                                attributes = Collections.unmodifiableSet(attributes);
                            } else {
                                attributes = Collections.singleton(firstAttribute);
                            }
                        } else {
                            attributes = Collections.emptySet();
                        }
                        reader.readEndSequence();
                    } catch (final Exception ae) {
                        StaticUtils.DEBUG_LOG.throwing("PostReadRequestControl", "decodeControl",
                                ae);

                        final LocalizableMessage message =
                                ERR_POSTREADREQ_CANNOT_DECODE_VALUE.get(ae.getMessage());
                        throw DecodeException.error(message, ae);
                    }

                    if (attributes.isEmpty()) {
                        return control.isCritical() ? CRITICAL_EMPTY_INSTANCE
                                : NONCRITICAL_EMPTY_INSTANCE;
                    } else {
                        return new PostReadRequestControl(control.isCritical(), attributes);
                    }
                }

                public String getOID() {
                    return OID;
                }
            };

    /**
     * Creates a new post-read request control.
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
    public static PostReadRequestControl newControl(final boolean isCritical,
            final Collection<String> attributes) {
        Validator.ensureNotNull(attributes);

        if (attributes.isEmpty()) {
            return isCritical ? CRITICAL_EMPTY_INSTANCE : NONCRITICAL_EMPTY_INSTANCE;
        } else if (attributes.size() == 1) {
            return new PostReadRequestControl(isCritical, Collections.singleton(attributes
                    .iterator().next()));
        } else {
            final Set<String> attributeSet = new LinkedHashSet<String>(attributes);
            return new PostReadRequestControl(isCritical, Collections.unmodifiableSet(attributeSet));
        }
    }

    /**
     * Creates a new post-read request control.
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
    public static PostReadRequestControl newControl(final boolean isCritical,
            final String... attributes) {
        Validator.ensureNotNull((Object) attributes);

        if (attributes.length == 0) {
            return isCritical ? CRITICAL_EMPTY_INSTANCE : NONCRITICAL_EMPTY_INSTANCE;
        } else if (attributes.length == 1) {
            return new PostReadRequestControl(isCritical, Collections.singleton(attributes[0]));
        } else {
            final Set<String> attributeSet = new LinkedHashSet<String>(Arrays.asList(attributes));
            return new PostReadRequestControl(isCritical, Collections.unmodifiableSet(attributeSet));
        }
    }

    private PostReadRequestControl(final boolean isCritical, final Set<String> attributes) {
        this.isCritical = isCritical;
        this.attributes = attributes;
    }

    /**
     * Returns an unmodifiable set containing the names of attributes to be
     * included with the response control. Attributes that are sub-types of
     * listed attributes are implicitly included. The returned set may be empty,
     * indicating that all user attributes should be returned.
     *
     * @return An unmodifiable set containing the names of attributes to be
     *         included with the response control.
     */
    public Set<String> getAttributes() {
        return attributes;
    }

    /**
     * {@inheritDoc}
     */
    public String getOID() {
        return OID;
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    public boolean hasValue() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isCritical() {
        return isCritical;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("PostReadRequestControl(oid=");
        builder.append(getOID());
        builder.append(", criticality=");
        builder.append(isCritical());
        builder.append(", attributes=");
        builder.append(attributes);
        builder.append(")");
        return builder.toString();
    }

}
