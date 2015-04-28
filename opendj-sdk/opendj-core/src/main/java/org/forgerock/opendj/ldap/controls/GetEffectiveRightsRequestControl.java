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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.UnknownSchemaElementException;

import org.forgerock.util.Reject;

/**
 * A partial implementation of the get effective rights request control as
 * defined in draft-ietf-ldapext-acl-model. The main differences are:
 * <ul>
 * <li>The response control is not supported. Instead the OpenDJ implementation
 * creates attributes containing effective rights information with the entry
 * being returned.
 * <li>The attribute type names are dynamically created.
 * <li>The set of attributes for which effective rights information is to be
 * requested can be included in the control.
 * </ul>
 * The get effective rights request control value has the following BER
 * encoding:
 *
 * <pre>
 *  GetRightsControl ::= SEQUENCE {
 *    authzId    authzId  -- Only the "dn:DN" form is supported.
 *    attributes  SEQUENCE OF AttributeType
 *  }
 * </pre>
 *
 * You can use the control to retrieve effective rights during a search:
 *
 * <pre>
 * String authDN = ...;
 *
 * SearchRequest request =
 *         Requests.newSearchRequest(
 *                     "dc=example,dc=com", SearchScope.WHOLE_SUBTREE,
 *                     "(uid=bjensen)", "cn", "aclRights", "aclRightsInfo")
 *                     .addControl(GetEffectiveRightsRequestControl.newControl(
 *                             true, authDN, "cn"));
 *
 * ConnectionEntryReader reader = connection.search(request);
 * while (reader.hasNext()) {
 *      if (!reader.isReference()) {
 *          SearchResultEntry entry = reader.readEntry();
 *          // Interpret aclRights and aclRightsInfo
 *      }
 * }
 * </pre>
 *
 * The entries returned by the search hold the {@code aclRights} and
 * {@code aclRightsInfo} attributes with the effective rights information. You
 * must parse the attribute options and values to interpret the information.
 *
 * @see <a
 *      href="http://tools.ietf.org/html/draft-ietf-ldapext-acl-model">draft-ietf-ldapext-acl-model
 *      - Access Control Model for LDAPv3 </a>
 **/
public final class GetEffectiveRightsRequestControl implements Control {
    /**
     * The OID for the get effective rights request control.
     */
    public static final String OID = "1.3.6.1.4.1.42.2.27.9.5.2";

    /**
     * A decoder which can be used for decoding the get effective rights request
     * control.
     */
    public static final ControlDecoder<GetEffectiveRightsRequestControl> DECODER =
            new ControlDecoder<GetEffectiveRightsRequestControl>() {

                public GetEffectiveRightsRequestControl decodeControl(final Control control,
                        final DecodeOptions options) throws DecodeException {
                    Reject.ifNull(control);

                    if (control instanceof GetEffectiveRightsRequestControl) {
                        return (GetEffectiveRightsRequestControl) control;
                    }

                    if (!control.getOID().equals(OID)) {
                        final LocalizableMessage message =
                                ERR_GETEFFECTIVERIGHTS_CONTROL_BAD_OID.get(control.getOID(), OID);
                        throw DecodeException.error(message);
                    }

                    DN authorizationDN = null;
                    List<AttributeType> attributes = Collections.emptyList();

                    if (control.hasValue()) {
                        final ASN1Reader reader = ASN1.getReader(control.getValue());
                        try {
                            reader.readStartSequence();
                            final String authzIDString = reader.readOctetStringAsString();
                            final String lowerAuthzIDString = authzIDString.toLowerCase();
                            Schema schema;

                            // Make sure authzId starts with "dn:" and is a
                            // valid DN.
                            if (lowerAuthzIDString.startsWith("dn:")) {
                                final String authorizationDNString = authzIDString.substring(3);
                                schema =
                                        options.getSchemaResolver().resolveSchema(
                                                authorizationDNString);
                                try {
                                    authorizationDN = DN.valueOf(authorizationDNString, schema);
                                } catch (final LocalizedIllegalArgumentException e) {
                                    final LocalizableMessage message =
                                            ERR_GETEFFECTIVERIGHTS_INVALID_AUTHZIDDN
                                                    .get(getExceptionMessage(e));
                                    throw DecodeException.error(message, e);
                                }
                            } else {
                                final LocalizableMessage message =
                                        INFO_GETEFFECTIVERIGHTS_INVALID_AUTHZID
                                                .get(lowerAuthzIDString);
                                throw DecodeException.error(message);
                            }

                            // There is an sequence containing an attribute list, try to decode it.
                            if (reader.hasNextElement()) {
                                attributes = new LinkedList<>();
                                reader.readStartSequence();
                                while (reader.hasNextElement()) {
                                    // Decode as an attribute type.
                                    final String attributeName = reader.readOctetStringAsString();
                                    AttributeType attributeType;
                                    try {
                                        // FIXME: we're using the schema
                                        // associated with the authzid
                                        // which is not really correct. We
                                        // should really use the schema
                                        // associated with the entry.
                                        attributeType = schema.getAttributeType(attributeName);
                                    } catch (final UnknownSchemaElementException e) {
                                        final LocalizableMessage message =
                                                ERR_GETEFFECTIVERIGHTS_UNKNOWN_ATTRIBUTE
                                                        .get(attributeName);
                                        throw DecodeException.error(message, e);
                                    }
                                    attributes.add(attributeType);
                                }
                                reader.readEndSequence();
                                attributes = Collections.unmodifiableList(attributes);
                            }
                            reader.readEndSequence();
                        } catch (final IOException e) {
                            final LocalizableMessage message =
                                    INFO_GETEFFECTIVERIGHTS_DECODE_ERROR.get(e.getMessage());
                            throw DecodeException.error(message);
                        }
                    }

                    return new GetEffectiveRightsRequestControl(control.isCritical(),
                            authorizationDN, attributes);

                }

                public String getOID() {
                    return OID;
                }
            };

    /**
     * Creates a new get effective rights request control with the provided
     * criticality, optional authorization name and attribute list.
     *
     * @param isCritical
     *            {@code true} if it is unacceptable to perform the operation
     *            without applying the semantics of this control, or
     *            {@code false} if it can be ignored.
     * @param authorizationName
     *            The distinguished name of the user for which effective rights
     *            are to be returned, or {@code null} if the client's
     *            authentication ID is to be used.
     * @param attributes
     *            The list of attributes for which effective rights are to be
     *            returned, which may be empty indicating that no attribute
     *            rights are to be returned.
     * @return The new control.
     * @throws NullPointerException
     *             If {@code attributes} was {@code null}.
     */
    public static GetEffectiveRightsRequestControl newControl(final boolean isCritical,
            final DN authorizationName, final Collection<AttributeType> attributes) {
        Reject.ifNull(attributes);

        final Collection<AttributeType> copyOfAttributes =
                Collections.unmodifiableList(new ArrayList<AttributeType>(attributes));
        return new GetEffectiveRightsRequestControl(isCritical, authorizationName, copyOfAttributes);
    }

    /**
     * Creates a new get effective rights request control with the provided
     * criticality, optional authorization name and attribute list. The
     * authorization name and attributes, if provided, will be decoded using the
     * default schema.
     *
     * @param isCritical
     *            {@code true} if it is unacceptable to perform the operation
     *            without applying the semantics of this control, or
     *            {@code false} if it can be ignored.
     * @param authorizationName
     *            The distinguished name of the user for which effective rights
     *            are to be returned, or {@code null} if the client's
     *            authentication ID is to be used.
     * @param attributes
     *            The list of attributes for which effective rights are to be
     *            returned, which may be empty indicating that no attribute
     *            rights are to be returned.
     * @return The new control.
     * @throws UnknownSchemaElementException
     *             If the default schema is a strict schema and one or more of
     *             the requested attribute types were not recognized.
     * @throws LocalizedIllegalArgumentException
     *             If {@code authorizationName} is not a valid LDAP string
     *             representation of a DN.
     * @throws NullPointerException
     *             If {@code attributes} was {@code null}.
     */
    public static GetEffectiveRightsRequestControl newControl(final boolean isCritical,
            final String authorizationName, final String... attributes) {
        Reject.ifNull((Object) attributes);

        final DN dn = authorizationName == null ? null : DN.valueOf(authorizationName);

        List<AttributeType> copyOfAttributes;
        if (attributes != null && attributes.length > 0) {
            copyOfAttributes = new ArrayList<>(attributes.length);
            for (final String attribute : attributes) {
                copyOfAttributes.add(Schema.getDefaultSchema().getAttributeType(attribute));
            }
            copyOfAttributes = Collections.unmodifiableList(copyOfAttributes);
        } else {
            copyOfAttributes = Collections.emptyList();
        }

        return new GetEffectiveRightsRequestControl(isCritical, dn, copyOfAttributes);
    }

    /** The DN representing the authzId (may be null meaning use the client's DN). */
    private final DN authorizationName;

    /** The unmodifiable list of attributes to be queried (may be empty). */
    private final Collection<AttributeType> attributes;

    private final boolean isCritical;

    private GetEffectiveRightsRequestControl(final boolean isCritical, final DN authorizationName,
            final Collection<AttributeType> attributes) {
        this.isCritical = isCritical;
        this.authorizationName = authorizationName;
        this.attributes = attributes;
    }

    /**
     * Returns an unmodifiable list of attributes for which effective rights are
     * to be returned, which may be empty indicating that no attribute rights
     * are to be returned.
     *
     * @return The unmodifiable list of attributes for which effective rights
     *         are to be returned.
     */
    public Collection<AttributeType> getAttributes() {
        return attributes;
    }

    /**
     * Returns the distinguished name of the user for which effective rights are
     * to be returned, or {@code null} if the client's authentication ID is to
     * be used.
     *
     * @return The distinguished name of the user for which effective rights are
     *         to be returned.
     */
    public DN getAuthorizationName() {
        return authorizationName;
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
            if (authorizationName != null) {
                writer.writeOctetString("dn:" + authorizationName);
            }

            if (!attributes.isEmpty()) {
                writer.writeStartSequence();
                for (final AttributeType attribute : attributes) {
                    writer.writeOctetString(attribute.getNameOrOID());
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
        return authorizationName != null || !attributes.isEmpty();
    }

    /** {@inheritDoc} */
    public boolean isCritical() {
        return isCritical;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("GetEffectiveRightsRequestControl(oid=");
        builder.append(getOID());
        builder.append(", criticality=");
        builder.append(isCritical());
        builder.append(", authorizationDN=\"");
        builder.append(authorizationName);
        builder.append("\"");
        builder.append(", attributes=(");
        builder.append(attributes);
        builder.append("))");
        return builder.toString();
    }
}
