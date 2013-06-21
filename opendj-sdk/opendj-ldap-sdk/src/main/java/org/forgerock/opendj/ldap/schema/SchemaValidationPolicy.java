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
 *      Copyright 2011-2012 ForgeRock AS
 */

package org.forgerock.opendj.ldap.schema;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.ErrorResultException;

/**
 * This class provides various schema validation policy options for controlling
 * how entries should be validated against the directory schema.
 */
public final class SchemaValidationPolicy {
    /**
     * A call-back which will be called during DIT structure rule schema
     * validation in order to retrieve the parent of the entry being validated.
     */
    public static interface EntryResolver {
        /**
         * Returns the named entry in order to enforce DIT structure rules.
         *
         * @param dn
         *            The name of the entry to be returned.
         * @return The named entry.
         * @throws ErrorResultException
         *             If the entry could not be retrieved.
         */
        Entry getEntry(DN dn) throws ErrorResultException;
    }

    /**
     * The schema validation policy.
     */
    public static enum Policy {
        /**
         * Schema validation will not be performed.
         */
        IGNORE,

        /**
         * Schema validation will be performed, but failures will not cause the
         * overall validation to fail. Error messages will be returned.
         */
        WARN,

        /**
         * Schema validation will be performed and failures will cause the
         * overall validation to fail. Error messages will be returned.
         */
        REJECT;

        private Policy() {
            // Nothing to do.
        }

        /**
         * Returns {@code true} if this policy is {@code IGNORE}.
         *
         * @return {@code true} if this policy is {@code IGNORE}.
         */
        public boolean isIgnore() {
            return this == IGNORE;
        }

        /**
         * Returns {@code true} if this policy is {@code REJECT}.
         *
         * @return {@code true} if this policy is {@code REJECT}.
         */
        public boolean isReject() {
            return this == REJECT;
        }

        /**
         * Returns {@code true} if this policy is {@code WARN}.
         *
         * @return {@code true} if this policy is {@code WARN}.
         */
        public boolean isWarn() {
            return this == WARN;
        }

        /**
         * Returns {@code true} if this policy is {@code WARN} or {@code REJECT}
         * .
         *
         * @return {@code true} if this policy is {@code WARN} or {@code REJECT}
         *         .
         */
        public boolean needsChecking() {
            return this != IGNORE;
        }
    }

    /**
     * Creates a copy of the provided schema validation policy.
     *
     * @param policy
     *            The policy to be copied.
     * @return The copy of the provided schema validation policy.
     */
    public static SchemaValidationPolicy copyOf(final SchemaValidationPolicy policy) {
        return defaultPolicy().assign(policy);
    }

    /**
     * Creates a new schema validation policy with default settings. More
     * specifically:
     * <ul>
     * <li>Entries not having a single structural object class will be rejected
     * <li>Entries having attributes which are not permitted by its object
     * classes or DIT content rule (if present) will be rejected
     * <li>Entries not conforming to name forms will be rejected
     * <li>DIT structure rules will not be ignored
     * </ul>
     *
     * @return The new schema validation policy.
     */
    public static SchemaValidationPolicy defaultPolicy() {
        return new SchemaValidationPolicy();
    }

    /**
     * Creates a new schema validation policy which will not perform any schema
     * validation.
     *
     * @return The new schema validation policy.
     */
    public static SchemaValidationPolicy ignoreAll() {
        return new SchemaValidationPolicy().checkAttributesAndObjectClasses(Policy.IGNORE)
                .checkAttributeValues(Policy.IGNORE).checkDITContentRules(Policy.IGNORE)
                .checkNameForms(Policy.IGNORE).requireSingleStructuralObjectClass(Policy.IGNORE);
    }

    private Policy checkNameForms = Policy.REJECT;

    private Policy checkDITStructureRules = Policy.IGNORE;

    private Policy checkDITContentRules = Policy.REJECT;

    private Policy requireSingleStructuralObjectClass = Policy.REJECT;

    private Policy checkAttributesAndObjectClasses = Policy.REJECT;

    private Policy checkAttributeValues = Policy.REJECT;

    private EntryResolver checkDITStructureRulesEntryResolver = null;

    // Prevent direct instantiation.
    private SchemaValidationPolicy() {
        // Nothing to do.
    }

    /**
     * Returns the policy for verifying that the user attributes in an entry
     * conform to its object classes. More specifically, an entry must contain
     * all required user attributes, and must not contain any user attributes
     * which are not declared as required or optional by its object classes.
     * <p>
     * By default entries which have missing or additional user attributes will
     * be rejected.
     *
     * @return The policy for verifying that the user attributes in an entry
     *         conform to its object classes.
     */
    public Policy checkAttributesAndObjectClasses() {
        return checkAttributesAndObjectClasses;
    }

    /**
     * Specifies the policy for verifying that the user attributes in an entry
     * conform to its object classes. More specifically, an entry must contain
     * all required user attributes, and must not contain any user attributes
     * which are not declared as required or optional by its object classes.
     * <p>
     * By default entries which have missing or additional user attributes will
     * be rejected.
     *
     * @param policy
     *            The policy for verifying that the user attributes in an entry
     *            conform to its object classes.
     * @return A reference to this {@code SchemaValidationPolicy}.
     */
    public SchemaValidationPolicy checkAttributesAndObjectClasses(final Policy policy) {
        this.checkAttributesAndObjectClasses = policy;
        return this;
    }

    /**
     * Returns the policy for verifying that the user attributes in an entry
     * conform to their associated attribute type descriptions. This may
     * include:
     * <ul>
     * <li>checking that there is at least one value
     * <li>checking that single-valued attributes contain only a single value
     * <li>checking that there are no duplicate values according to the
     * attribute's default equality matching rule
     * <li>checking that attributes which require BER encoding specify the
     * {@code ;binary} attribute option
     * <li>checking that the values are valid according to the attribute's
     * syntax.
     * </ul>
     * Schema validation implementations specify exactly which of the above
     * checks will be performed.
     * <p>
     * By default entries which have invalid attribute values will be rejected.
     *
     * @return The policy for verifying that the user attributes in an entry
     *         conform to their associated attribute type descriptions.
     */
    public Policy checkAttributeValues() {
        return checkAttributeValues;
    }

    /**
     * Specifies the policy for verifying that the user attributes in an entry
     * conform to their associated attribute type descriptions. This may
     * include:
     * <ul>
     * <li>checking that there is at least one value
     * <li>checking that single-valued attributes contain only a single value
     * <li>checking that there are no duplicate values according to the
     * attribute's default equality matching rule
     * <li>checking that attributes which require BER encoding specify the
     * {@code ;binary} attribute option
     * <li>checking that the values are valid according to the attribute's
     * syntax.
     * </ul>
     * Schema validation implementations specify exactly which of the above
     * checks will be performed.
     * <p>
     * By default entries which have invalid attribute values will be rejected.
     *
     * @param policy
     *            The policy for verifying that the user attributes in an entry
     *            conform to their associated attribute type descriptions.
     * @return A reference to this {@code SchemaValidationPolicy}.
     */
    public SchemaValidationPolicy checkAttributeValues(final Policy policy) {
        this.checkAttributeValues = policy;
        return this;
    }

    /**
     * Returns the policy for validating entries against content rules defined
     * in the schema.
     * <p>
     * By default content rules will be ignored during validation.
     *
     * @return The policy for validating entries against content rules defined
     *         in the schema.
     */
    public Policy checkDITContentRules() {
        return checkDITContentRules;
    }

    /**
     * Specifies the policy for validating entries against content rules defined
     * in the schema.
     * <p>
     * By default content rules will be ignored during validation.
     *
     * @param policy
     *            The policy for validating entries against content rules
     *            defined in the schema.
     * @return A reference to this {@code SchemaValidationPolicy}.
     */
    public SchemaValidationPolicy checkDITContentRules(final Policy policy) {
        this.checkDITContentRules = policy;
        return this;
    }

    /**
     * Returns the policy for validating entries against structure rules defined
     * in the schema.
     * <p>
     * By default structure rules will be ignored during validation.
     *
     * @return The policy for validating entries against structure rules defined
     *         in the schema.
     */
    public Policy checkDITStructureRules() {
        return checkDITStructureRules;
    }

    /**
     * Specifies the policy for validating entries against structure rules
     * defined in the schema.
     * <p>
     * By default structure rules will be ignored during validation.
     *
     * @param policy
     *            The policy for validating entries against structure rules
     *            defined in the schema.
     * @param resolver
     *            The parent entry resolver which should be used for retrieving
     *            the parent entry during DIT structure rule validation.
     * @return A reference to this {@code SchemaValidationPolicy}.
     * @throws IllegalArgumentException
     *             If {@code resolver} was {@code null} and
     *             {@code checkDITStructureRules} is either {@code WARN} or
     *             {@code REJECT}.
     */
    public SchemaValidationPolicy checkDITStructureRules(final Policy policy,
            final EntryResolver resolver) {
        if (checkDITStructureRules.needsChecking() && resolver == null) {
            throw new IllegalArgumentException(
                    "Validation of structure rules enabled by resolver was null");
        }
        this.checkDITStructureRules = policy;
        this.checkDITStructureRulesEntryResolver = resolver;
        return this;
    }

    /**
     * Returns parent entry resolver which should be used for retrieving the
     * parent entry during DIT structure rule validation.
     * <p>
     * By default no resolver is defined because structure rules will be ignored
     * during validation.
     *
     * @return The parent entry resolver which should be used for retrieving the
     *         parent entry during DIT structure rule validation.
     */
    public EntryResolver checkDITStructureRulesEntryResolver() {
        return checkDITStructureRulesEntryResolver;
    }

    /**
     * Returns the policy for validating entries against name forms defined in
     * the schema.
     * <p>
     * By default name forms will be ignored during validation.
     *
     * @return The policy for validating entries against name forms defined in
     *         the schema.
     */
    public Policy checkNameForms() {
        return checkNameForms;
    }

    /**
     * Specifies the policy for validating entries against name forms defined in
     * the schema.
     * <p>
     * By default name forms will be ignored during validation.
     *
     * @param policy
     *            The policy for validating entries against name forms defined
     *            in the schema.
     * @return A reference to this {@code SchemaValidationPolicy}.
     */
    public SchemaValidationPolicy checkNameForms(final Policy policy) {
        this.checkNameForms = policy;
        return this;
    }

    /**
     * Returns the policy for verifying that entries have only a single
     * structural object class.
     * <p>
     * By default entries which do not have a structural object class or which
     * have more than one structural object class will be rejected.
     *
     * @return The policy for checking that entries have one and only one
     *         structural object class.
     */
    public Policy requireSingleStructuralObjectClass() {
        return requireSingleStructuralObjectClass;
    }

    /**
     * Specifies the policy for verifying that entries have only a single
     * structural object class.
     * <p>
     * By default entries which do not have a structural object class or which
     * have more than one structural object class will be rejected.
     *
     * @param policy
     *            The policy for checking that entries have one and only one
     *            structural object class.
     * @return A reference to this {@code SchemaValidationPolicy}.
     */
    public SchemaValidationPolicy requireSingleStructuralObjectClass(final Policy policy) {
        this.requireSingleStructuralObjectClass = policy;
        return this;
    }

    // Assigns the provided options to this set of options.
    SchemaValidationPolicy assign(final SchemaValidationPolicy policy) {
        this.checkAttributeValues = policy.checkAttributeValues;
        this.checkNameForms = policy.checkNameForms;
        this.checkAttributesAndObjectClasses = policy.checkAttributesAndObjectClasses;
        this.checkDITContentRules = policy.checkDITContentRules;
        this.checkDITStructureRules = policy.checkDITStructureRules;
        this.checkDITStructureRulesEntryResolver = policy.checkDITStructureRulesEntryResolver;
        this.requireSingleStructuralObjectClass = policy.requireSingleStructuralObjectClass;
        return this;
    }

}
