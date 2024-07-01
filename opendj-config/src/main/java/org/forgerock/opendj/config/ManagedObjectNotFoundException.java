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

import static com.forgerock.opendj.ldap.config.ConfigMessages.*;

/** The requested managed object could not be located. */
public class ManagedObjectNotFoundException extends OperationsException {

    /** Version ID required by serializable classes. */
    private static final long serialVersionUID = -477551786551892978L;

    /** Create a managed object not found exception. */
    public ManagedObjectNotFoundException() {
        super(ERR_MANAGED_OBJECT_NOT_FOUND_EXCEPTION.get());
    }

    /**
     * Create a managed object not found exception with the specified cause.
     *
     * @param cause
     *            The cause of this exception.
     */
    public ManagedObjectNotFoundException(Throwable cause) {
        super(ERR_MANAGED_OBJECT_NOT_FOUND_EXCEPTION.get(), cause);
    }
}
