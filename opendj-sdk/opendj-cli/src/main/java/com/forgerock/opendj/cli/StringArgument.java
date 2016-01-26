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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions copyright 2014-2016 ForgeRock AS
 */
package com.forgerock.opendj.cli;

import org.forgerock.i18n.LocalizableMessageBuilder;

/** This class defines an argument type that will accept any string value. */
public final class StringArgument extends Argument {
    /**
     * Returns a builder which can be used for incrementally constructing a new
     * {@link StringArgument}.
     *
     * @param name
     *         The generic name that will be used to refer to this argument.
     * @return A builder to continue building the {@link StringArgument}.
     */
    public static Builder builder(final String name) {
        return new Builder(name);
    }

    /** A fluent API for incrementally constructing {@link StringArgument}. */
    public static final class Builder extends ArgumentBuilder<Builder, String, StringArgument> {
        private Builder(final String name) {
            super(name);
        }

        @Override
        Builder getThis() {
            return this;
        }

        @Override
        public StringArgument buildArgument() throws ArgumentException {
            return new StringArgument(this);
        }
    }

    private StringArgument(final Builder builder) throws ArgumentException {
        super(builder);
    }

    @Override
    public boolean valueIsAcceptable(final String valueString, final LocalizableMessageBuilder invalidReason) {
        // All values will be acceptable for this argument.
        return true;
    }
}
