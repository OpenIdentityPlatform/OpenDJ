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

import java.util.Arrays;
import java.util.List;

import org.forgerock.opendj.config.AbstractManagedObjectDefinition;
import org.forgerock.opendj.config.client.ManagedObject;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ServerManagedObject;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.util.Reject;

/**
 * A condition which evaluates to <code>true</code> if and only if all of its
 * sub-conditions are <code>true</code>.
 */
final class ANDCondition implements Condition {

    /** The list of sub-conditions. */
    private final List<Condition> conditions;

    /**
     * Creates a new logical AND condition with the provided sub-conditions.
     *
     * @param conditions
     *            The sub-conditions which will be combined using a logical AND.
     */
    public ANDCondition(Condition... conditions) {
        Reject.ifNull(conditions);
        this.conditions = Arrays.asList(conditions);
    }

    @Override
    public boolean evaluate(ManagementContext context, ManagedObject<?> managedObject) throws LdapException {
        for (Condition condition : conditions) {
            if (!condition.evaluate(context, managedObject)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean evaluate(ServerManagedObject<?> managedObject) throws ConfigException {
        for (Condition condition : conditions) {
            if (!condition.evaluate(managedObject)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void initialize(AbstractManagedObjectDefinition<?, ?> d) throws Exception {
        for (Condition condition : conditions) {
            condition.initialize(d);
        }
    }
}
