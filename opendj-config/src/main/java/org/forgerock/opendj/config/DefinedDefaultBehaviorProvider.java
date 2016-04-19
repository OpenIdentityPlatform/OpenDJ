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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

/**
 * A default behavior provider which represents a well-defined set of default
 * values. It should be used by properties which have default value(s) which are
 * valid value(s) according to the constraints of the property's definition.
 *
 * @param <T>
 *            The type of values represented by this provider.
 */
public final class DefinedDefaultBehaviorProvider<T> extends DefaultBehaviorProvider<T> {

    /** The collection of default values. */
    private final Collection<String> values;

    /**
     * Create a new defined default behavior provider associated with the
     * specified list of values.
     *
     * @param values
     *            The list of values (must be non-<code>null</code> and not
     *            empty) in their string representation.
     * @throws IllegalArgumentException
     *             If the list of values was <code>null</code> or empty.
     */
    public DefinedDefaultBehaviorProvider(String... values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Null or empty list of default values");
        }
        this.values = Arrays.asList(values);
    }

    @Override
    public <R, P> R accept(DefaultBehaviorProviderVisitor<T, R, P> v, P p) {
        return v.visitDefined(this, p);
    }

    /**
     * Get a copy of the default values.
     *
     * @return Returns a newly allocated collection containing a copy of the
     *         default values.
     */
    public Collection<String> getDefaultValues() {
        return new ArrayList<>(values);
    }

}
