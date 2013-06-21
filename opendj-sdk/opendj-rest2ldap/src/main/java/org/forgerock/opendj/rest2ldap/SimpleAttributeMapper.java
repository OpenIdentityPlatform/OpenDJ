/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2012-2013 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.forgerock.opendj.ldap.Filter.alwaysFalse;
import static org.forgerock.opendj.ldap.Functions.fixedFunction;
import static org.forgerock.opendj.rest2ldap.Rest2LDAP.asResourceException;
import static org.forgerock.opendj.rest2ldap.Utils.base64ToByteString;
import static org.forgerock.opendj.rest2ldap.Utils.byteStringToBase64;
import static org.forgerock.opendj.rest2ldap.Utils.byteStringToJson;
import static org.forgerock.opendj.rest2ldap.Utils.i18n;
import static org.forgerock.opendj.rest2ldap.Utils.jsonToAttribute;
import static org.forgerock.opendj.rest2ldap.Utils.jsonToByteString;
import static org.forgerock.opendj.rest2ldap.Utils.toFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.Function;

/**
 * An attribute mapper which provides a simple mapping from a JSON value to a
 * single LDAP attribute.
 */
public final class SimpleAttributeMapper extends AbstractLDAPAttributeMapper<SimpleAttributeMapper> {
    private Function<ByteString, ?, Void> decoder = null;
    private Function<Object, ByteString, Void> encoder = null;

    SimpleAttributeMapper(final AttributeDescription ldapAttributeName) {
        super(ldapAttributeName);
    }

    /**
     * Sets the decoder which will be used for converting LDAP attribute values
     * to JSON values.
     *
     * @param f
     *            The function to use for decoding LDAP attribute values.
     * @return This attribute mapper.
     */
    public SimpleAttributeMapper decoder(final Function<ByteString, ?, Void> f) {
        this.decoder = f;
        return this;
    }

    /**
     * Sets the default JSON value which should be substituted when the LDAP
     * attribute is not found in the LDAP entry.
     *
     * @param defaultValue
     *            The default JSON value.
     * @return This attribute mapper.
     */
    public SimpleAttributeMapper defaultJSONValue(final Object defaultValue) {
        this.defaultJSONValues = defaultValue != null ? singletonList(defaultValue) : emptyList();
        return this;
    }

    /**
     * Sets the encoder which will be used for converting JSON values to LDAP
     * attribute values.
     *
     * @param f
     *            The function to use for encoding LDAP attribute values.
     * @return This attribute mapper.
     */
    public SimpleAttributeMapper encoder(final Function<Object, ByteString, Void> f) {
        this.encoder = f;
        return this;
    }

    /**
     * Indicates that JSON values are base 64 encodings of binary data. Calling
     * this method is equivalent to the following:
     *
     * <pre>
     * mapper.decoder(...); // function that converts binary data to base 64
     * mapper.encoder(...); // function that converts base 64 to binary data
     * </pre>
     *
     * @return This attribute mapper.
     */
    public SimpleAttributeMapper isBinary() {
        decoder = byteStringToBase64();
        encoder = base64ToByteString();
        return this;
    }

    @Override
    public String toString() {
        return "simple(" + ldapAttributeName.toString() + ")";
    }

    @Override
    void getLDAPFilter(final Context c, final JsonPointer path, final JsonPointer subPath,
            final FilterType type, final String operator, final Object valueAssertion,
            final ResultHandler<Filter> h) {
        if (subPath.isEmpty()) {
            try {
                final ByteString va =
                        valueAssertion != null ? encoder().apply(valueAssertion, null) : null;
                h.handleResult(toFilter(c, type, ldapAttributeName.toString(), va));
            } catch (final Exception e) {
                // Invalid assertion value - bad request.
                h.handleError(new BadRequestException(i18n(
                        "The request cannot be processed because it contained an "
                                + "illegal filter assertion value '%s' for field '%s'", String
                                .valueOf(valueAssertion), path), e));
            }
        } else {
            // This attribute mapper does not support partial filtering.
            h.handleResult(alwaysFalse());
        }
    }

    @Override
    void getNewLDAPAttributes(final Context c, final JsonPointer path,
            final List<Object> newValues, final ResultHandler<Attribute> h) {
        try {
            h.handleResult(jsonToAttribute(newValues, ldapAttributeName, encoder()));
        } catch (final Exception ex) {
            h.handleError(new BadRequestException(i18n(
                    "The request cannot be processed because an error occurred while "
                            + "encoding the values for the field '%s': %s", path, ex.getMessage())));
        }
    }

    @Override
    SimpleAttributeMapper getThis() {
        return this;
    }

    @Override
    void read(final Context c, final JsonPointer path, final Entry e,
            final ResultHandler<JsonValue> h) {
        try {
            final Object value;
            if (attributeIsSingleValued()) {
                value =
                        e.parseAttribute(ldapAttributeName).as(decoder(),
                                defaultJSONValues.isEmpty() ? null : defaultJSONValues.get(0));
            } else {
                final Set<Object> s =
                        e.parseAttribute(ldapAttributeName).asSetOf(decoder(), defaultJSONValues);
                value = s.isEmpty() ? null : new ArrayList<Object>(s);
            }
            h.handleResult(value != null ? new JsonValue(value) : null);
        } catch (final Exception ex) {
            // The LDAP attribute could not be decoded.
            h.handleError(asResourceException(ex));
        }
    }

    private Function<ByteString, ? extends Object, Void> decoder() {
        return decoder == null ? fixedFunction(byteStringToJson(), ldapAttributeName) : decoder;
    }

    private Function<Object, ByteString, Void> encoder() {
        return encoder == null ? fixedFunction(jsonToByteString(), ldapAttributeName) : encoder;
    }

}
