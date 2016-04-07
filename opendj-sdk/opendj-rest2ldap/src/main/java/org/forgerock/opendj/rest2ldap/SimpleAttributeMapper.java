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
 * Copyright 2012-2015 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

import static java.util.Collections.*;

import static org.forgerock.opendj.ldap.Filter.*;
import static org.forgerock.opendj.rest2ldap.Rest2LDAP.*;
import static org.forgerock.opendj.rest2ldap.Utils.*;
import static org.forgerock.util.promise.Promises.newExceptionPromise;
import static org.forgerock.util.promise.Promises.newResultPromise;

/**
 * An attribute mapper which provides a simple mapping from a JSON value to a
 * single LDAP attribute.
 */
public final class SimpleAttributeMapper extends AbstractLDAPAttributeMapper<SimpleAttributeMapper> {
    private Function<ByteString, ?, NeverThrowsException> decoder;
    private Function<Object, ByteString, NeverThrowsException> encoder;

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
    public SimpleAttributeMapper decoder(final Function<ByteString, ?, NeverThrowsException> f) {
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
    public SimpleAttributeMapper encoder(final Function<Object, ByteString, NeverThrowsException> f) {
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
        return "simple(" + ldapAttributeName + ")";
    }

    @Override
    Promise<Filter, ResourceException> getLDAPFilter(final RequestState requestState, final JsonPointer path,
            final JsonPointer subPath, final FilterType type, final String operator, final Object valueAssertion) {
        if (subPath.isEmpty()) {
            try {
                final ByteString va =
                        valueAssertion != null ? encoder().apply(valueAssertion) : null;
                return newResultPromise(toFilter(type, ldapAttributeName.toString(), va));
            } catch (final Exception e) {
                // Invalid assertion value - bad request.
                return newExceptionPromise((ResourceException) new BadRequestException(i18n(
                        "The request cannot be processed because it contained an "
                                + "illegal filter assertion value '%s' for field '%s'",
                        String.valueOf(valueAssertion), path), e));
            }
        } else {
            // This attribute mapper does not support partial filtering.
            return newResultPromise(alwaysFalse());
        }
    }

    @Override
    Promise<Attribute, ResourceException> getNewLDAPAttributes(
            final RequestState requestState, final JsonPointer path, final List<Object> newValues) {
        try {
            return newResultPromise(jsonToAttribute(newValues, ldapAttributeName, encoder()));
        } catch (final Exception ex) {
            return newExceptionPromise((ResourceException) new BadRequestException(i18n(
                    "The request cannot be processed because an error occurred while "
                            + "encoding the values for the field '%s': %s", path, ex.getMessage())));
        }
    }

    @Override
    SimpleAttributeMapper getThis() {
        return this;
    }

    @Override
    Promise<JsonValue, ResourceException> read(final RequestState requestState, final JsonPointer path, final Entry e) {
        try {
            final Object value;
            if (attributeIsSingleValued()) {
                value =
                        e.parseAttribute(ldapAttributeName).as(decoder(),
                                defaultJSONValues.isEmpty() ? null : defaultJSONValues.get(0));
            } else {
                final Set<Object> s =
                        e.parseAttribute(ldapAttributeName).asSetOf(decoder(), defaultJSONValues);
                value = s.isEmpty() ? null : new ArrayList<>(s);
            }
            return newResultPromise(value != null ? new JsonValue(value) : null);
        } catch (final Exception ex) {
            // The LDAP attribute could not be decoded.
            return newExceptionPromise(asResourceException(ex));
        }
    }

    private Function<ByteString, ?, NeverThrowsException> decoder() {
        return decoder == null ? byteStringToJson(ldapAttributeName) : decoder;
    }

    private Function<Object, ByteString, NeverThrowsException> encoder() {
        return encoder == null ? jsonToByteString(ldapAttributeName) : encoder;
    }

}
