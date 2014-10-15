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
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.forgerock.opendj.config.client.ldap;

import java.util.Collection;
import java.util.Collections;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.Constraint;
import org.forgerock.opendj.config.ManagedObjectPath;
import org.forgerock.opendj.config.client.ClientConstraintHandler;
import org.forgerock.opendj.config.client.ManagedObject;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.config.server.ServerConstraintHandler;
import org.forgerock.opendj.ldap.LdapException;

/**
 * A mock constraint which can be configured to refuse various types of
 * operation.
 */
public final class MockConstraint extends Constraint {

    /**
     * Mock client constraint handler.
     */
    private class Handler extends ClientConstraintHandler {

        /** {@inheritDoc} */
        @Override
        public boolean isAddAcceptable(ManagementContext context, ManagedObject<?> managedObject,
                Collection<LocalizableMessage> unacceptableReasons) throws LdapException {
            if (!allowAdds) {
                unacceptableReasons.add(LocalizableMessage.raw("Adds not allowed"));
            }

            return allowAdds;
        }

        /** {@inheritDoc} */
        @Override
        public boolean isDeleteAcceptable(ManagementContext context, ManagedObjectPath<?, ?> path,
                Collection<LocalizableMessage> unacceptableReasons) throws LdapException {
            if (!allowDeletes) {
                unacceptableReasons.add(LocalizableMessage.raw("Deletes not allowed"));
            }

            return allowDeletes;
        }

        /** {@inheritDoc} */
        @Override
        public boolean isModifyAcceptable(ManagementContext context, ManagedObject<?> managedObject,
                Collection<LocalizableMessage> unacceptableReasons) throws LdapException {
            if (!allowModifies) {
                unacceptableReasons.add(LocalizableMessage.raw("Modifies not allowed"));
            }

            return allowModifies;
        }

    }

    /** Determines if add operations are allowed. */
    private final boolean allowAdds;

    /** Determines if modify operations are allowed. */
    private final boolean allowModifies;

    /** Determines if delete operations are allowed. */
    private final boolean allowDeletes;

    /**
     * Creates a new mock constraint.
     *
     * @param allowAdds
     *            Determines if add operations are allowed.
     * @param allowModifies
     *            Determines if modify operations are allowed.
     * @param allowDeletes
     *            Determines if delete operations are allowed.
     */
    public MockConstraint(boolean allowAdds, boolean allowModifies, boolean allowDeletes) {
        this.allowAdds = allowAdds;
        this.allowModifies = allowModifies;
        this.allowDeletes = allowDeletes;
    }

    /** {@inheritDoc} */
    public Collection<ClientConstraintHandler> getClientConstraintHandlers() {
        return Collections.<ClientConstraintHandler> singleton(new Handler());
    }

    /** {@inheritDoc} */
    public Collection<ServerConstraintHandler> getServerConstraintHandlers() {
        return Collections.emptySet();
    }

}
