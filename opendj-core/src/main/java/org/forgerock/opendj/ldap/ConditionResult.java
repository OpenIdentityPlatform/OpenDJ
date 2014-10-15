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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.forgerock.opendj.ldap;

/**
 * The result of a tri-state logical expression. Condition results are used to
 * represent the result of a conditional evaluation that can yield three
 * possible values: {@code FALSE} (i.e. "no"), {@code TRUE} (i.e. "yes"), or
 * {@code UNDEFINED} (i.e. "maybe"). A result of {@code UNDEFINED} indicates
 * that further investigation may be required.
 */
public enum ConditionResult {
    /**
     * Indicates that the condition evaluated to {@code false}.
     */
    FALSE("false"),

    /**
     * Indicates that the condition could not be evaluated and its result is
     * undefined.
     */
    UNDEFINED("undefined"),

    /**
     * Indicates that the condition evaluated to {@code true}.
     */
    TRUE("true");

    /** Boolean -> ConditionResult map. */
    private static final boolean[] BOOLEAN_MAP = { false, false, true };

    /** AND truth table. */
    private static final ConditionResult[][] LOGICAL_AND = { { FALSE, FALSE, FALSE },
        { FALSE, UNDEFINED, UNDEFINED }, { FALSE, UNDEFINED, TRUE }, };

    /** NOT truth table. */
    private static final ConditionResult[] LOGICAL_NOT = { TRUE, UNDEFINED, FALSE };

    /** OR truth table. */
    private static final ConditionResult[][] LOGICAL_OR = { { FALSE, UNDEFINED, TRUE },
        { UNDEFINED, UNDEFINED, TRUE }, { TRUE, TRUE, TRUE }, };

    /**
     * Returns the logical AND of zero condition results, which is always
     * {@code TRUE}.
     *
     * @return The logical OR of zero condition results, which is always
     *         {@code TRUE}.
     */
    public static ConditionResult and() {
        return TRUE;
    }

    /**
     * Returns the logical AND of the provided condition result, which is always
     * {@code r}.
     *
     * @param r
     *            The condition result.
     * @return The logical AND of the provided condition result, which is always
     *         {@code r}.
     */
    public static ConditionResult and(final ConditionResult r) {
        return r;
    }

    /**
     * Returns the logical AND of the provided condition results, which is
     * {@code TRUE} if all of the provided condition results are {@code TRUE},
     * {@code FALSE} if at least one of them is {@code FALSE}, and
     * {@code UNDEFINED} otherwise. Note that {@code TRUE} is returned if the
     * provided list of results is empty.
     *
     * @param results
     *            The condition results to be compared.
     * @return The logical AND of the provided condition results.
     */
    public static ConditionResult and(final ConditionResult... results) {
        ConditionResult finalResult = TRUE;
        for (final ConditionResult result : results) {
            finalResult = and(finalResult, result);
            if (finalResult == FALSE) {
                break;
            }
        }
        return finalResult;
    }

    /**
     * Returns the logical AND of the provided condition results, which is
     * {@code TRUE} if both of the provided condition results are {@code TRUE},
     * {@code FALSE} if at least one of them is {@code FALSE} , and
     * {@code UNDEFINED} otherwise.
     *
     * @param r1
     *            The first condition result to be compared.
     * @param r2
     *            The second condition result to be compared.
     * @return The logical AND of the provided condition results.
     */
    public static ConditionResult and(final ConditionResult r1, final ConditionResult r2) {
        return LOGICAL_AND[r1.ordinal()][r2.ordinal()];
    }

    /**
     * Returns the logical NOT of the provided condition result, which is
     * {@code TRUE} if the provided condition result is {@code FALSE},
     * {@code TRUE} if it is {@code FALSE}, and {@code UNDEFINED} otherwise.
     *
     * @param r
     *            The condition result to invert.
     * @return The logical NOT of the provided condition result.
     */
    public static ConditionResult not(final ConditionResult r) {
        return LOGICAL_NOT[r.ordinal()];
    }

    /**
     * Returns the logical OR of zero condition results, which is always
     * {@code FALSE}.
     *
     * @return The logical OR of zero condition results, which is always
     *         {@code FALSE}.
     */
    public static ConditionResult or() {
        return FALSE;
    }

    /**
     * Returns the logical OR of the provided condition result, which is always
     * {@code r}.
     *
     * @param r
     *            The condition result.
     * @return The logical OR of the provided condition result, which is always
     *         {@code r}.
     */
    public static ConditionResult or(final ConditionResult r) {
        return r;
    }

    /**
     * Returns the logical OR of the provided condition results, which is
     * {@code FALSE} if all of the provided condition results are {@code FALSE},
     * {@code TRUE} if at least one of them is {@code TRUE}, and
     * {@code UNDEFINED} otherwise. Note that {@code FALSE} is returned if the
     * provided list of results is empty.
     *
     * @param results
     *            The condition results to be compared.
     * @return The logical OR of the provided condition results.
     */
    public static ConditionResult or(final ConditionResult... results) {
        ConditionResult finalResult = FALSE;
        for (final ConditionResult result : results) {
            finalResult = or(finalResult, result);
            if (finalResult == TRUE) {
                break;
            }
        }
        return finalResult;
    }

    /**
     * Returns the logical OR of the provided condition results, which is
     * {@code FALSE} if both of the provided condition results are {@code FALSE}
     * , {@code TRUE} if at least one of them is {@code TRUE} , and
     * {@code UNDEFINED} otherwise.
     *
     * @param r1
     *            The first condition result to be compared.
     * @param r2
     *            The second condition result to be compared.
     * @return The logical OR of the provided condition results.
     */
    public static ConditionResult or(final ConditionResult r1, final ConditionResult r2) {
        return LOGICAL_OR[r1.ordinal()][r2.ordinal()];
    }

    /**
     * Returns the condition result which is equivalent to the provided boolean
     * value.
     *
     * @param b
     *            The boolean value.
     * @return {@code TRUE} if {@code b} was {@code true}, otherwise
     *         {@code FALSE} .
     */
    public static ConditionResult valueOf(final boolean b) {
        return b ? TRUE : FALSE;
    }

    /** The human-readable name for this result. */
    private final String resultName;

    /** Prevent instantiation. */
    private ConditionResult(final String resultName) {
        this.resultName = resultName;
    }

    /**
     * Converts this condition result to a boolean value. {@code FALSE} and
     * {@code UNDEFINED} are both converted to {@code false}, and {@code TRUE}
     * is converted to {@code true}.
     *
     * @return The boolean equivalent of this condition result.
     */
    public boolean toBoolean() {
        return BOOLEAN_MAP[ordinal()];
    }

    /**
     * Returns the string representation of this condition result.
     *
     * @return The string representation of his condition result.
     */
    @Override
    public String toString() {
        return resultName;
    }
}
