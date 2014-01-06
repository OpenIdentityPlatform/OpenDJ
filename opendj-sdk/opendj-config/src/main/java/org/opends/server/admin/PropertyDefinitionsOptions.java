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
 *      Copyright 2013 ForgeRock AS.
 */
package org.opends.server.admin;

/**
 * Provides options for {@code PropertyDefinition property definitions}.
 * <p>
 * These options are used by some {@code PropertyDefinition} methods to define
 * strategy used when processing value(s) of a property definition.
 */
public final class PropertyDefinitionsOptions {

    /** Immutable options with no validation of java classes or LDAP attributes. */
    public static final PropertyDefinitionsOptions NO_VALIDATION_OPTIONS = new PropertyDefinitionsOptions().
        setAllowClassValidation(false).
        setCheckSchemaForAttributes(false).
        freeze();

    /** By default, class validation is enabled. */
    private boolean allowClassValidation = true;

    /** By default, attributes validation against the schema is enabled. */
    private boolean checkSchemaForAttributes = true;

    /**
     * If true, then the instance is frozen so state can't be changed (object is
     * immutable).
     */
    private boolean isFrozen = false;

    /**
     * Creates a new set of properties options with default settings. By
     * default, class validation and attributes checking are enabled.
     */
    public PropertyDefinitionsOptions() {
        // empty implementation
    }

    /**
     * Determine whether or not class property definitions should validate class
     * name property values. Validation involves checking that the class exists
     * and that it implements the required interfaces.
     *
     * @return Returns <code>true</code> if class property definitions should
     *         validate class name property values.
     */
    public boolean allowClassValidation() {
        return allowClassValidation;
    }

    /**
     * Specify whether or not class property definitions should validate class
     * name property values. Validation involves checking that the class exists
     * and that it implements the required interfaces.
     * <p>
     * By default validation is switched on.
     *
     * @param value
     *            <code>true</code> if class property definitions should
     *            validate class name property values.
     * @return A reference to this definitions options.
     */
    public PropertyDefinitionsOptions setAllowClassValidation(boolean value) {
        if (isFrozen) {
            throw new IllegalStateException("This object is frozen, it can't be changed");
        }
        allowClassValidation = value;
        return this;
    }

    /**
     * Determines whether or not attribute type names should be validated
     * against the schema.
     *
     * @return Returns <code>true</code> if attribute type names should be
     *         validated against the schema.
     */
    public boolean checkSchemaForAttributes() {
        return checkSchemaForAttributes;
    }

    /**
     * Specify whether or not attribute type names should be validated against
     * the schema.
     * <p>
     * By default validation is switched on.
     *
     * @param value
     *            <code>true</code> if attribute type names should be validated
     *            against the schema.
     * @return A reference to this definitions options.
     */
    public PropertyDefinitionsOptions setCheckSchemaForAttributes(boolean value) {
        if (isFrozen) {
            throw new IllegalStateException("This object is frozen, it can't be changed");
        }
        checkSchemaForAttributes = value;
        return this;
    }

    /**
     * Freeze this object, making it effectively immutable.
     * <p>
     * Once this method is called, all {@code set} methods will throw
     * an IllegalStateException if called.
     *
     * @return A reference to this definitions options.
     */
    public PropertyDefinitionsOptions freeze() {
        isFrozen = true;
        return this;
    }

}
