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
 * A default behavior provider which retrieves default values from a parent
 * managed object. It should be used by properties which inherit their default
 * value(s) from properties held in an other managed object.
 *
 * @param <T>
 *            The type of values represented by this provider.
 */
public final class RelativeInheritedDefaultBehaviorProvider<T> extends DefaultBehaviorProvider<T> {

    /** The type of managed object expected at the relative offset. */
    private final AbstractManagedObjectDefinition<?, ?> d;

    /**
     * The relative offset (where 1 = parent, 2 = grandparent) of the
     * managed object containing the property.
     */
    private final int offset;

    /** The name of the property containing the inherited default values. */
    private final String propertyName;

    /**
     * Create a relative inherited default behavior provider associated with a
     * parent managed object.
     *
     * @param d
     *            The type of parent managed object expected at the relative
     *            location.
     * @param propertyName
     *            The name of the property containing the inherited default
     *            values.
     * @param offset
     *            The relative location of the parent managed object (where 0 is
     *            the managed object itself, 1 is the parent, and 2 is the
     *            grand-parent).
     * @throws IllegalArgumentException
     *             If the offset is less than 0.
     */
    public RelativeInheritedDefaultBehaviorProvider(AbstractManagedObjectDefinition<?, ?> d, String propertyName,
        int offset) {
        // We do not decode the property name now because the property
        // might not have been constructed at this point (e.g. when the
        // offset is 0).
        if (offset < 0) {
            throw new IllegalArgumentException("Negative offset");
        }
        this.d = d;
        this.propertyName = propertyName;
        this.offset = offset;
    }

    @Override
    public <R, P> R accept(DefaultBehaviorProviderVisitor<T, R, P> v, P p) {
        return v.visitRelativeInherited(this, p);
    }

    /**
     * Get the definition of the parent managed object containing the inherited
     * default values.
     *
     * @return Returns the definition of the parent managed object containing
     *         the inherited default values.
     */
    public AbstractManagedObjectDefinition<?, ?> getManagedObjectDefinition() {
        return d;
    }

    /**
     * Get the absolute path of the managed object containing the property which
     * has the default values.
     *
     * @param path
     *            The path of the current managed object from which the relative
     *            path should be determined.
     * @return Returns the absolute path of the managed object containing the
     *         property which has the default values.
     */
    public ManagedObjectPath<?, ?> getManagedObjectPath(ManagedObjectPath<?, ?> path) {
        return path.parent(offset);
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

    /**
     * Get the relative location of the parent managed object.
     *
     * @return Returns the relative location of the parent managed object (where
     *         0 is the managed object itself, 1 is the parent, and 2 is the
     *         grand-parent).
     */
    public int getRelativeOffset() {
        return offset;
    }
}
