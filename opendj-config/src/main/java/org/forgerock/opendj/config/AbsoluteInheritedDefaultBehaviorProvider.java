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

/**
 * A default behavior provider which retrieves default values from a managed
 * object in an absolute location. It should be used by properties which inherit
 * their default value(s) from properties held in an other managed object.
 *
 * @param <T>
 *            The type of values represented by this provider.
 */
public final class AbsoluteInheritedDefaultBehaviorProvider<T> extends DefaultBehaviorProvider<T> {

    /** The absolute path to the managed object containing the property. */
    private ManagedObjectPath<?, ?> path;

    /**
     * The string representation of the managed object path specifying
     * the absolute location of the managed object.
     */
    private final String pathString;

    /** The name of the property containing the inherited default values. */
    private final String propertyName;

    /**
     * Create an absolute inherited default behavior provider associated with
     * the managed object at the specified absolute location.
     *
     * @param pathString
     *            The string representation of the managed object path
     *            specifying the absolute location of the managed object.
     * @param propertyName
     *            The name of the property containing the inherited default
     *            values.
     */
    public AbsoluteInheritedDefaultBehaviorProvider(String pathString, String propertyName) {
        this.pathString = pathString;
        this.propertyName = propertyName;
    }

    @Override
    public <R, P> R accept(DefaultBehaviorProviderVisitor<T, R, P> v, P p) {
        return v.visitAbsoluteInherited(this, p);
    }

    /**
     * Get the definition of the parent managed object containing the inherited
     * default values.
     *
     * @return Returns the definition of the parent managed object containing
     *         the inherited default values.
     */
    public AbstractManagedObjectDefinition<?, ?> getManagedObjectDefinition() {
        return path.getManagedObjectDefinition();
    }

    /**
     * Get the absolute path of the managed object containing the property which
     * has the default values.
     *
     * @return Returns the absolute path of the managed object containing the
     *         property which has the default values.
     */
    public ManagedObjectPath<?, ?> getManagedObjectPath() {
        return path;
    }

    /**
     * Gets the name of the property containing the inherited default values.
     *
     * @return Returns the name of the property containing the inherited default
     *         values.
     */
    public String getPropertyName() {
        return propertyName;
    }

    @Override
    protected void initialize() throws Exception {
        // Decode the path.
        path = ManagedObjectPath.valueOf(pathString);
    }

}
