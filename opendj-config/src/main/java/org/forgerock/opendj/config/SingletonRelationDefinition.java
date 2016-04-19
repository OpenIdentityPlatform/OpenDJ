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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */

package org.forgerock.opendj.config;

/**
 * A managed object composite relationship definition which represents a
 * composition of a single managed object (i.e. the managed object must be
 * present).
 *
 * @param <C>
 *            The type of client managed object configuration that this relation
 *            definition refers to.
 * @param <S>
 *            The type of server managed object configuration that this relation
 *            definition refers to.
 */
public final class SingletonRelationDefinition<C extends ConfigurationClient, S extends Configuration> extends
    RelationDefinition<C, S> {

    /**
     * An interface for incrementally constructing singleton relation
     * definitions.
     *
     * @param <C>
     *            The type of client managed object configuration that this
     *            relation definition refers to.
     * @param <S>
     *            The type of server managed object configuration that this
     *            relation definition refers to.
     */
    public static final class Builder<C extends ConfigurationClient, S extends Configuration> extends
        AbstractBuilder<C, S, SingletonRelationDefinition<C, S>> {

        /** The optional default managed object associated with this singleton relation. */
        private DefaultManagedObject<? extends C, ? extends S> defaultManagedObject;

        /**
         * Creates a new builder which can be used to incrementally build an
         * singleton relation definition.
         *
         * @param pd
         *            The parent managed object definition.
         * @param name
         *            The name of the relation.
         * @param cd
         *            The child managed object definition.
         */
        // @Checkstyle:ignore
        public Builder(AbstractManagedObjectDefinition<?, ?> pd, String name, AbstractManagedObjectDefinition<C, S> cd) {
            super(pd, name, cd);
        }

        /**
         * Sets the optional default managed object associated with this
         * singleton relation definition.
         *
         * @param defaultManagedObject
         *            The default managed object or <code>null</code> if there
         *            is no default managed object defined for this relation
         *            definition.
         */
        public void setDefaultManagedObject(DefaultManagedObject<? extends C, ? extends S> defaultManagedObject) {
            this.defaultManagedObject = defaultManagedObject;
        }

        @Override
        protected SingletonRelationDefinition<C, S> buildInstance(Common<C, S> common) {
            return new SingletonRelationDefinition<>(common, defaultManagedObject);
        }
    }

    /** The optional default managed object associated with this singleton relation. */
    private final DefaultManagedObject<? extends C, ? extends S> defaultManagedObject;

    /** Private constructor. */
    private SingletonRelationDefinition(Common<C, S> common,
        DefaultManagedObject<? extends C, ? extends S> defaultManagedObject) {
        super(common);
        this.defaultManagedObject = defaultManagedObject;
    }

    @Override
    public <R, P> R accept(RelationDefinitionVisitor<R, P> v, P p) {
        return v.visitSingleton(this, p);
    }

    /**
     * Gets the optional default managed object associated with this singleton
     * relation definition.
     *
     * @return Returns the default managed object or <code>null</code> if there
     *         is no default managed object defined for this relation
     *         definition.
     */
    public DefaultManagedObject<? extends C, ? extends S> getDefaultManagedObject() {
        return defaultManagedObject;
    }

    @Override
    public void toString(StringBuilder builder) {
        builder.append("name=");
        builder.append(getName());
        builder.append(" type=singleton parent=");
        builder.append(getParentDefinition().getName());
        builder.append(" child=");
        builder.append(getChildDefinition().getName());
    }

    @Override
    protected void initialize() throws Exception {
        if (defaultManagedObject != null) {
            defaultManagedObject.initialize();
        }
    }

}
