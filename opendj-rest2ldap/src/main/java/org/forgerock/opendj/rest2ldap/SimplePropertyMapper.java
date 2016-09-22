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
 * Copyright 2012-2016 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.services.context.Context;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;

import static java.util.Collections.*;

import static org.forgerock.json.JsonValue.*;
import static org.forgerock.opendj.ldap.Filter.*;
import static org.forgerock.opendj.rest2ldap.Rest2Ldap.asResourceException;
import static org.forgerock.opendj.rest2ldap.Utils.*;
import static org.forgerock.util.promise.Promises.newResultPromise;

/** An property mapper which provides a simple mapping from a JSON value to a single LDAP attribute. */
public final class SimplePropertyMapper extends AbstractLdapPropertyMapper<SimplePropertyMapper> {
    private Function<ByteString, ?, ? extends Exception> decoder;
    private Function<Object, ByteString, ? extends Exception> encoder;

    SimplePropertyMapper(final AttributeDescription ldapAttributeName) {
        super(ldapAttributeName);
    }

    /**
     * Sets the decoder which will be used for converting LDAP attribute values
     * to JSON values.
     *
     * @param f
     *            The function to use for decoding LDAP attribute values.
     * @return This property mapper.
     */
    public SimplePropertyMapper decoder(final Function<ByteString, ?, ? extends Exception> f) {
        this.decoder = f;
        return this;
    }

    /**
     * Sets the default JSON value which should be substituted when the LDAP attribute is not found in the LDAP entry.
     *
     * @param defaultValue
     *            The default JSON value.
     * @return This property mapper.
     */
    public SimplePropertyMapper defaultJsonValue(final Object defaultValue) {
        this.defaultJsonValues = defaultValue != null ? singletonList(defaultValue) : emptyList();
        return this;
    }

    /**
     * Sets the default JSON values which should be substituted when the LDAP attribute is not found in the LDAP entry.
     *
     * @param defaultValues
     *            The default JSON values.
     * @return This property mapper.
     */
    public SimplePropertyMapper defaultJsonValues(final Collection<?> defaultValues) {
        this.defaultJsonValues = defaultValues != null ? new ArrayList<>(defaultValues) : emptyList();
        return this;
    }

    /**
     * Sets the encoder which will be used for converting JSON values to LDAP
     * attribute values.
     *
     * @param f
     *            The function to use for encoding LDAP attribute values.
     * @return This property mapper.
     */
    public SimplePropertyMapper encoder(final Function<Object, ByteString, ? extends Exception> f) {
        this.encoder = f;
        return this;
    }

    /**
     * Indicates that JSON values are base 64 encodings of binary data. Calling
     * this method with the value {@code true} is equivalent to the following:
     *
     * <pre>
     * mapper.decoder(...); // function that converts binary data to base 64
     * mapper.encoder(...); // function that converts base 64 to binary data
     * </pre>
     *
     * Passing in a value of {@code false} resets the encoding and decoding
     * functions to the default.
     *
     * @param isBinary {@code true} if this property is binary.
     * @return This property mapper.
     */
    public SimplePropertyMapper isBinary(final boolean isBinary) {
        if (isBinary) {
            decoder = byteStringToBase64();
            encoder = base64ToByteString();
        } else {
            decoder = null;
            encoder = null;
        }
        return this;
    }

    @Override
    public String toString() {
        return "simple(" + ldapAttributeName + ")";
    }

    @Override
    Promise<Filter, ResourceException> getLdapFilter(final Context context, final Resource resource,
                                                     final JsonPointer path, final JsonPointer subPath,
                                                     final FilterType type, final String operator,
                                                     final Object valueAssertion) {
        if (subPath.isEmpty()) {
            try {
                final ByteString va = valueAssertion != null ? encoder().apply(valueAssertion) : null;
                return newResultPromise(toFilter(type, ldapAttributeName.toString(), va));
            } catch (final Exception e) {
                // Invalid assertion value - bad request.
                return newBadRequestException(
                        ERR_ILLEGAL_FILTER_ASSERTION_VALUE.get(String.valueOf(valueAssertion), path), e).asPromise();
            }
        } else {
            // This property mapper does not support partial filtering.
            return newResultPromise(alwaysFalse());
        }
    }

    @Override
    Promise<Attribute, ResourceException> getNewLdapAttributes(final Context context, final Resource resource,
                                                               final JsonPointer path, final List<Object> newValues) {
        try {
            return newResultPromise(jsonToAttribute(newValues, ldapAttributeName, encoder()));
        } catch (final Exception ex) {
            return newBadRequestException(ERR_ENCODING_VALUES_FOR_FIELD.get(path, ex.getMessage())).asPromise();
        }
    }

    @Override
    SimplePropertyMapper getThis() {
        return this;
    }

    @SuppressWarnings("fallthrough")
    @Override
    Promise<JsonValue, ResourceException> read(final Context context, final Resource resource,
                                               final JsonPointer path, final Entry e) {
        try {
            final Set<Object> s = e.parseAttribute(ldapAttributeName).asSetOf(decoder(), defaultJsonValues);
            switch (s.size()) {
            case 0:
                return newResultPromise(null);
            case 1:
                if (attributeIsSingleValued()) {
                    return newResultPromise(new JsonValue(s.iterator().next()));
                }
                // Fall-though: unexpectedly got multiple values. It's probably best to just return them.
            default:
                return newResultPromise(new JsonValue(new ArrayList<>(s)));
            }
        } catch (final Exception ex) {
            // The LDAP attribute could not be decoded.
            return asResourceException(ex).asPromise();
        }
    }

    private Function<ByteString, ?, ? extends Exception> decoder() {
        return decoder == null ? byteStringToJson(ldapAttributeName) : decoder;
    }

    private Function<Object, ByteString, ? extends Exception> encoder() {
        return encoder == null ? jsonToByteString(ldapAttributeName) : encoder;
    }

    @Override
    JsonValue toJsonSchema() {
        final AttributeType attrType = ldapAttributeName.getAttributeType();

        final JsonValue jsonSchema = json(object(field("type", toJsonSchemaType(attrType))));
        final String description = attrType.getDescription();
        if (description != null && !"".equals(description)) {
            jsonSchema.put("title", description);
        }

        putWritabilityProperties(jsonSchema);
        return jsonSchema;
    }

    private static String toJsonSchemaType(AttributeType attrType) {
        if (attrType.isPlaceHolder()) {
            return "string";
        }
        // TODO JNR cannot use switch + SchemaConstants.SYNTAX_DIRECTORY_STRING_OID
        // because the class is not public
        // this is not nice :(
        // TODO JNR not so sure about these mappings
        final String oid = attrType.getSyntax().getOID();
        if (CoreSchema.getDirectoryStringSyntax().getOID().equals(oid)
                || CoreSchema.getOctetStringSyntax().getOID().equals(oid)) {
            return "string";
        } else if (CoreSchema.getBooleanSyntax().getOID().equals(oid)) {
            return "boolean";
        } else if (CoreSchema.getIntegerSyntax().getOID().equals(oid)) {
            return "integer";
        } else if (CoreSchema.getNumericStringSyntax().getOID().equals(oid)) {
            return "number";
        }
        return "string";
    }
}
