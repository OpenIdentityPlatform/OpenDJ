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
 * Portions Copyright 2013-2015 ForgeRock AS.
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
