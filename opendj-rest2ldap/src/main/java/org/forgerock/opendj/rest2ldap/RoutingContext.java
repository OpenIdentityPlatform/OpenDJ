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
 *
 */
package org.forgerock.opendj.rest2ldap;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.services.context.AbstractContext;
import org.forgerock.services.context.Context;

/**
 * A {@link Context} which communicates the current Rest2Ldap routing state to downstream handlers.
 */
final class RoutingContext extends AbstractContext {
    private final DN dn;
    private final Resource resource;

    RoutingContext(final Context parent, final DN dn, final Resource resource) {
        super(parent, "routing context");
        this.dn = dn;
        this.resource = resource;
    }

    DN getDn() {
        return dn;
    }

    Resource getType() {
        return resource;
    }
}
