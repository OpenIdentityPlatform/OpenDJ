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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.opendj.config;

import java.util.EnumSet;

import org.forgerock.util.Reject;

/** Definition of a configuration property containing a password. */
public final class PasswordPropertyDefinition extends PropertyDefinition<String> {
    /** Cryptographic services supplied by the server configuration repository. */
    public interface Codec {
        /** Checks that the named scheme is enabled and reversible. */
        void requireScheme(String scheme) throws Exception;

        /** Encrypts a plaintext value and returns it with its scheme prefix. */
        String encrypt(String scheme, String plaintext) throws Exception;

        /** Decrypts the unprefixed encoded value. */
        String decrypt(String scheme, String encoded) throws Exception;
    }

    /** Builder for password property definitions. */
    public static final class Builder extends AbstractBuilder<String, PasswordPropertyDefinition> {
        private String encryptionScheme = "AES";

        private Builder(AbstractManagedObjectDefinition<?, ?> definition, String name) {
            super(definition, name);
        }

        /** Sets the password storage scheme name. */
        public void setEncryptionScheme(String name) {
            Reject.ifNull(name);
            this.encryptionScheme = name;
        }

        @Override
        protected PasswordPropertyDefinition buildInstance(AbstractManagedObjectDefinition<?, ?> definition,
                String name, EnumSet<PropertyOption> options, AdministratorAction action,
                DefaultBehaviorProvider<String> defaultBehavior) {
            return new PasswordPropertyDefinition(definition, name, options, action, defaultBehavior, encryptionScheme);
        }
    }

    /** Creates a builder for the specified property. */
    public static Builder createBuilder(AbstractManagedObjectDefinition<?, ?> definition, String name) {
        return new Builder(definition, name);
    }

    private final String encryptionScheme;

    private PasswordPropertyDefinition(AbstractManagedObjectDefinition<?, ?> definition, String name,
            EnumSet<PropertyOption> options, AdministratorAction action, DefaultBehaviorProvider<String> defaultBehavior,
            String encryptionScheme) {
        super(definition, String.class, name, options, action, defaultBehavior);
        this.encryptionScheme = encryptionScheme;
    }

    /** Returns the name of the effective password storage scheme. */
    public String getEncryptionScheme() {
        return encryptionScheme;
    }

    /** Returns whether a value begins with the reserved scheme-prefix form. */
    public static boolean hasSchemePrefix(String value) {
        Reject.ifNull(value);
        return value.startsWith("{") && value.indexOf('}') > 1;
    }

    /** Protects a new value, or validates and preserves an already protected value. */
    public String encodeForStorage(String value, Codec codec) throws Exception {
        Reject.ifNull(value, codec);
        codec.requireScheme(encryptionScheme);
        if (hasSchemePrefix(value)) {
            int end = value.indexOf('}');
            if (end < 2 || !value.substring(1, end).equalsIgnoreCase(encryptionScheme)) {
                throw new IllegalArgumentException("Unexpected password storage scheme");
            }
            codec.decrypt(encryptionScheme, value.substring(end + 1));
            return value;
        }
        return codec.encrypt(encryptionScheme, value);
    }

    /** Decodes a protected value, leaving a legacy unprefixed value unchanged. */
    public String decodeFromStorage(String value, Codec codec) throws Exception {
        Reject.ifNull(value, codec);
        if (!hasSchemePrefix(value)) {
            return value;
        }
        int end = value.indexOf('}');
        if (end < 2 || !value.substring(1, end).equalsIgnoreCase(encryptionScheme)) {
            throw new IllegalArgumentException("Unexpected password storage scheme");
        }
        codec.requireScheme(encryptionScheme);
        return codec.decrypt(encryptionScheme, value.substring(end + 1));
    }

    @Override
    public String decodeValue(String value) {
        Reject.ifNull(value);
        return value;
    }

    @Override
    public void validateValue(String value) {
        Reject.ifNull(value);
    }

    @Override
    public String normalizeValue(String value) {
        Reject.ifNull(value);
        return value;
    }

    @Override
    public <R, P> R accept(PropertyDefinitionVisitor<R, P> visitor, P parameter) {
        return visitor.visitPassword(this, parameter);
    }

    @Override
    public <R, P> R accept(PropertyValueVisitor<R, P> visitor, String value, P parameter) {
        return visitor.visitPassword(this, value, parameter);
    }
}
