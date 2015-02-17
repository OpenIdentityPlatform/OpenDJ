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
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.authorization.dseecompat;

/**
 * This interface represents a keyword  bind rule class
 * that can evaluate an evaluation context. It defines a single
 * function that each of the keyword functions implement (ip, dns,
 * roledn, groupdn, ...)
 */
public interface KeywordBindRule
{
    /**
     * Evaluate a bind rule using the passed in context.
     * @param evalCtx An evaluation context to use in the evaluation.
     * @return An enumeration evaluation result.
     */
    EnumEvalResult evaluate(AciEvalContext evalCtx);

    /**
     * Appends a string representation of this object to the provided buffer.
     *
     * @param buffer
     *          The buffer into which a string representation of this object
     *          should be appended.
     */
    void toString(StringBuilder buffer);
}
