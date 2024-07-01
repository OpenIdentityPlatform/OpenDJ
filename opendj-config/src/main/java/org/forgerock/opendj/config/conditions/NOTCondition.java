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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config.conditions;

import org.forgerock.opendj.config.AbstractManagedObjectDefinition;
import org.forgerock.opendj.config.client.ManagedObject;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ServerManagedObject;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.util.Reject;

/**
 * A condition which evaluates to <code>true</code> if the sub-condition is
 * <code>false</code>, or <code>false</code> if the sub-condition is
 * <code>true</code>.
 */
public final class NOTCondition implements Condition {

    /** The single sub-condition. */
    private final Condition condition;

    /**
     * Creates a new logical NOT condition with the provided sub-condition.
     *
     * @param condition
     *            The sub-condition which will be inverted.
     */
    public NOTCondition(Condition condition) {
        Reject.ifNull(condition);
        this.condition = condition;
    }

    @Override
    public boolean evaluate(ManagementContext context, ManagedObject<?> managedObject) throws LdapException {
        return !condition.evaluate(context, managedObject);
    }

    @Override
    public boolean evaluate(ServerManagedObject<?> managedObject) throws ConfigException {
        return !condition.evaluate(managedObject);
    }

    @Override
    public void initialize(AbstractManagedObjectDefinition<?, ?> d) throws Exception {
        condition.initialize(d);
    }

}
