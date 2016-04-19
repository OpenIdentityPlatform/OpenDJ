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
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.config;

/**
 * A default behavior provider which indicates undefined behavior. It should be
 * used by properties which have no default values or behavior as such. For
 * example, a description property, when left unset, has no default value and no
 * side-effects.
 *
 * @param <T>
 *            The type of values represented by this provider.
 */
public final class UndefinedDefaultBehaviorProvider<T> extends DefaultBehaviorProvider<T> {

    /** Create an undefined default behavior provider. */
    public UndefinedDefaultBehaviorProvider() {
        // No implementation required.
    }

    @Override
    public <R, P> R accept(DefaultBehaviorProviderVisitor<T, R, P> v, P p) {
        return v.visitUndefined(this, p);
    }

}
