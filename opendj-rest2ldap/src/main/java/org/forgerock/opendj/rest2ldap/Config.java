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
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.schema.Schema;

/**
 * Common configuration options.
 */
final class Config {
    private final DecodeOptions options;
    private final ReadOnUpdatePolicy readOnUpdatePolicy;
    private final boolean useSubtreeDelete;
    private final boolean usePermissiveModify;

    Config(final ReadOnUpdatePolicy readOnUpdatePolicy, final boolean useSubtreeDelete,
            final boolean usePermissiveModify, final Schema schema) {
        this.readOnUpdatePolicy = readOnUpdatePolicy;
        this.useSubtreeDelete = useSubtreeDelete;
        this.usePermissiveModify = usePermissiveModify;
        this.options = new DecodeOptions().setSchema(schema);
    }

    /**
     * Returns the decoding options which should be used when decoding controls
     * in responses.
     *
     * @return The decoding options which should be used when decoding controls
     *         in responses.
     */
    DecodeOptions decodeOptions() {
        return options;
    }

    /**
     * Returns {@code true} if modify requests should include the permissive
     * modify control.
     *
     * @return {@code true} if modify requests should include the permissive
     *         modify control.
     */
    boolean usePermissiveModify() {
        return usePermissiveModify;
    }

    /**
     * Returns {@code true} if delete requests should include the subtree delete
     * control.
     *
     * @return {@code true} if delete requests should include the subtree delete
     *         control.
     */
    boolean useSubtreeDelete() {
        return useSubtreeDelete;
    }

    /**
     * Returns the policy which should be used in order to read an entry before
     * it is deleted, or after it is added or modified.
     *
     * @return The policy which should be used in order to read an entry before
     *         it is deleted, or after it is added or modified.
     */
    ReadOnUpdatePolicy readOnUpdatePolicy() {
        return readOnUpdatePolicy;
    }
}
