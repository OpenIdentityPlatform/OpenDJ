/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
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
    public EnumEvalResult evaluate(AciEvalContext evalCtx);
}
