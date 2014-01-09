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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.forgerock.opendj.config;

import static com.forgerock.opendj.ldap.AdminMessages.*;

/**
 * The requested managed object could not be located.
 */
public class ManagedObjectNotFoundException extends OperationsException {

    /**
     * Version ID required by serializable classes.
     */
    private static final long serialVersionUID = -477551786551892978L;

    /**
     * Create a managed object not found exception.
     */
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
