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
 * Copyright 2007-2008 Sun Microsystems, Inc.
 * Portions Copyright 2016 ForgeRock AS.
 */

package org.forgerock.opendj.config;

import org.forgerock.opendj.ldap.DN;

/** A common base interface for all server managed object configurations. */
public interface Configuration {

    /**
     * Gets the DN of the LDAP entry associated with this configuration.
     *
     * @return Returns the DN of the LDAP entry associated with this
     *         configuration.
     */
    DN dn();

    /**
     * Gets the configuration class associated with this configuration.
     *
     * @return Returns the configuration class associated with this
     *         configuration.
     */
    Class<? extends Configuration> configurationClass();
}
