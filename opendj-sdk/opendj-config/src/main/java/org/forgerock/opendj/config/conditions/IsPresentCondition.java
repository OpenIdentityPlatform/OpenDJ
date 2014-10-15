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
package org.forgerock.opendj.config.conditions;

import java.util.SortedSet;

import org.forgerock.opendj.config.AbstractManagedObjectDefinition;
import org.forgerock.opendj.config.PropertyDefinition;
import org.forgerock.opendj.config.client.ManagedObject;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ServerManagedObject;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.util.Reject;

/**
 * A condition which evaluates to <code>true</code> if and only if a particular
 * property has any values specified.
 */
public final class IsPresentCondition implements Condition {

    /** The property name. */
    private final String propertyName;

    /** The property definition. */
    private PropertyDefinition<?> pd;

    /**
     * Creates a new is present condition.
     *
     * @param propertyName
     *            The property name.
     */
    public IsPresentCondition(String propertyName) {
        Reject.ifNull(propertyName);
        this.propertyName = propertyName;
    }

    /** {@inheritDoc} */
    public boolean evaluate(ManagementContext context, ManagedObject<?> managedObject) throws LdapException {
        SortedSet<?> values = managedObject.getPropertyValues(pd);
        return !values.isEmpty();
    }

    /** {@inheritDoc} */
    public boolean evaluate(ServerManagedObject<?> managedObject) throws ConfigException {
        SortedSet<?> values = managedObject.getPropertyValues(pd);
        return !values.isEmpty();
    }

    /** {@inheritDoc} */
    public void initialize(AbstractManagedObjectDefinition<?, ?> d) throws Exception {
        // Decode the property.
        this.pd = d.getPropertyDefinition(propertyName);
    }

}
