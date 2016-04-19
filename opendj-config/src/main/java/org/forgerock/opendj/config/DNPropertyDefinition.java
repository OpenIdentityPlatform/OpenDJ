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
 * Portions Copyright 2016 ForgeRock AS.
 */

package org.forgerock.opendj.config;

import org.forgerock.util.Reject;

import java.util.EnumSet;

import org.forgerock.opendj.ldap.DN;

/** DN property definition. */
public final class DNPropertyDefinition extends PropertyDefinition<DN> {

    /** Optional base DN which all valid values must be immediately subordinate to. */
    private final DN baseDN;

    /** An interface for incrementally constructing DN property definitions. */
    public static final class Builder extends AbstractBuilder<DN, DNPropertyDefinition> {

        /** Optional base DN which all valid values must be immediately subordinate to. */
        private DN baseDN;

        /** Private constructor. */
        private Builder(AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
            super(d, propertyName);
        }

        /**
         * Set the base DN which all valid values must be immediately
         * subordinate to. By default there is no based DN.
         *
         * @param baseDN
         *            The string representation of the base DN.
         * @throws IllegalArgumentException
         *             If the provided string is not a valid DN string
         *             representation.
         */
        public void setBaseDN(String baseDN) {
            setBaseDN(baseDN != null ? DN.valueOf(baseDN) : null);
        }

        /**
         * Set the base DN which all valid values must be immediately
         * subordinate to. By default there is no based DN.
         *
         * @param baseDN
         *            The base DN.
         */
        public void setBaseDN(DN baseDN) {
            this.baseDN = baseDN;
        }

        @Override
        protected DNPropertyDefinition buildInstance(AbstractManagedObjectDefinition<?, ?> d, String propertyName,
            EnumSet<PropertyOption> options, AdministratorAction adminAction,
            DefaultBehaviorProvider<DN> defaultBehavior) {
            return new DNPropertyDefinition(d, propertyName, options, adminAction, defaultBehavior, baseDN);
        }
    }

    /**
     * Create a DN property definition builder.
     *
     * @param d
     *            The managed object definition associated with this property
     *            definition.
     * @param propertyName
     *            The property name.
     * @return Returns the new boolean property definition builder.
     */
    public static Builder createBuilder(AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
        return new Builder(d, propertyName);
    }

    /** Private constructor. */
    private DNPropertyDefinition(AbstractManagedObjectDefinition<?, ?> d, String propertyName,
        EnumSet<PropertyOption> options, AdministratorAction adminAction,
        DefaultBehaviorProvider<DN> defaultBehavior, DN baseDN) {
        super(d, DN.class, propertyName, options, adminAction, defaultBehavior);
        this.baseDN = baseDN;
    }

    /**
     * Get the base DN which all valid values must be immediately subordinate
     * to, or <code>null</code> if there is no based DN.
     *
     * @return Returns the base DN which all valid values must be immediately
     *         subordinate to.
     */
    public DN getBaseDN() {
        return baseDN;
    }

    @Override
    public void validateValue(DN value) {
        Reject.ifNull(value);

        if (baseDN != null) {
            DN parent = value.parent();

            if (parent == null) {
                parent = DN.rootDN();
            }

            if (!parent.equals(baseDN)) {
                throw PropertyException.illegalPropertyValueException(this, value);
            }
        }
    }

    @Override
    public DN decodeValue(String value) {
        Reject.ifNull(value);

        try {
            DN dn = DN.valueOf(value);
            validateValue(dn);
            return dn;
        } catch (PropertyException e) {
            throw PropertyException.illegalPropertyValueException(this, value);
        }
    }

    @Override
    public <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p) {
        return v.visitDN(this, p);
    }

    @Override
    public <R, P> R accept(PropertyValueVisitor<R, P> v, DN value, P p) {
        return v.visitDN(this, value, p);
    }

    @Override
    public int compare(DN o1, DN o2) {
        return o1.compareTo(o2);
    }
}
