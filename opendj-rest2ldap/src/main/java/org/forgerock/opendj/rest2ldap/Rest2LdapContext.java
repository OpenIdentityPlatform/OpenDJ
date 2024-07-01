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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import org.forgerock.services.context.AbstractContext;
import org.forgerock.services.context.Context;

/**
 * A {@link Context} which communicates the {@link Rest2Ldap} instance to downstream handlers and property mappers.
 */
final class Rest2LdapContext extends AbstractContext {
    private final Rest2Ldap rest2ldap;

    Rest2LdapContext(final Context parent, final Rest2Ldap rest2ldap) {
        super(parent, "rest2ldap context");
        this.rest2ldap = rest2ldap;
    }

    Rest2Ldap getRest2ldap() {
        return rest2ldap;
    }
}
