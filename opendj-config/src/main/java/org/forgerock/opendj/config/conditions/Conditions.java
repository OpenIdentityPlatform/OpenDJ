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

/** This class consists exclusively of static methods that operate on or return conditions. */
public final class Conditions {

    /** A condition which always evaluates to <code>false</code>. */
    public static final Condition FALSE = new Condition() {

        @Override
        public boolean evaluate(ManagementContext context, ManagedObject<?> managedObject) throws LdapException {
            return false;
        }

        @Override
        public boolean evaluate(ServerManagedObject<?> managedObject) throws ConfigException {
            return false;
        }

        @Override
        public void initialize(AbstractManagedObjectDefinition<?, ?> d) throws Exception {
            // No implementation required.
        }

    };

    /** A condition which always evaluates to <code>true</code>. */
    public static final Condition TRUE = new Condition() {

        @Override
        public boolean evaluate(ManagementContext context, ManagedObject<?> managedObject) throws LdapException {
            return true;
        }

        @Override
        public boolean evaluate(ServerManagedObject<?> managedObject) throws ConfigException {
            return true;
        }

        @Override
        public void initialize(AbstractManagedObjectDefinition<?, ?> d) throws Exception {
            // No implementation required.
        }

    };

    /**
     * Creates a condition which evaluates to <code>true</code> if and only if
     * all of its sub-conditions are <code>true</code>.
     *
     * @param conditions
     *            The sub-conditions which be combined using a logical AND.
     * @return Returns a condition which evaluates to <code>true</code> if and
     *         only if all of its sub-conditions are <code>true</code>.
     */
    public static Condition and(Condition... conditions) {
        return new ANDCondition(conditions);
    }

    /**
     * Creates a condition which evaluates to <code>true</code> if and only if a
     * property contains a particular value.
     *
     * @param propertyName
     *            The property name.
     * @param propertyStringValue
     *            The string representation of the required property value.
     * @return Returns a condition which evaluates to <code>true</code> if and
     *         only if a property contains a particular value.
     */
    public static Condition contains(String propertyName, String propertyStringValue) {
        return new ContainsCondition(propertyName, propertyStringValue);
    }

    /**
     * Creates a condition which evaluates to <code>false</code> if and only if
     * the first sub-condition evaluates to <code>true</code> and the second
     * sub-condition evaluates to <code>false</code>. This can be used to
     * represent if-then relationships.
     *
     * @param premise
     *            The sub-condition which, when <code>true</code> implies that
     *            the implication sub-condition must also be <code>true</code>.
     * @param implication
     *            The sub-condition which, must be <code>true</code> when the
     *            premise is <code>true</code>.
     * @return Returns a condition which evaluates to <code>false</code> if and
     *         only if the first sub-condition evaluates to <code>true</code>
     *         and the second sub-condition evaluates to <code>false</code>.
     */
    public static Condition implies(Condition premise, Condition implication) {
        return or(not(premise), implication);
    }

    /**
     * Creates a condition which evaluates to <code>true</code> if and only if a
     * particular property has any values specified.
     *
     * @param propertyName
     *            The property name.
     * @return Returns a condition which evaluates to <code>true</code> if and
     *         only if a particular property has any values specified.
     */
    public static Condition isPresent(String propertyName) {
        return new IsPresentCondition(propertyName);
    }

    /**
     * Creates a condition which evaluates to <code>true</code> if the
     * sub-condition is <code>false</code>, or <code>false</code> if the
     * sub-condition is <code>true</code>.
     *
     * @param condition
     *            The sub-condition which will be inverted.
     * @return Returns a condition which evaluates to <code>true</code> if the
     *         sub-condition is <code>false</code>, or <code>false</code> if the
     *         sub-condition is <code>true</code>.
     */
    public static Condition not(Condition condition) {
        return new NOTCondition(condition);
    }

    /**
     * Creates a condition which evaluates to <code>false</code> if and only if
     * all of its sub-conditions are <code>false</code>.
     *
     * @param conditions
     *            The sub-conditions which be combined using a logical OR.
     * @return Returns a condition which evaluates to <code>false</code> if and
     *         only if all of its sub-conditions are <code>false</code>.
     */
    public static Condition or(Condition... conditions) {
        return new ORCondition(conditions);
    }

    /** Prevent instantiation. */
    private Conditions() {
        // No implementation required.
    }

}
