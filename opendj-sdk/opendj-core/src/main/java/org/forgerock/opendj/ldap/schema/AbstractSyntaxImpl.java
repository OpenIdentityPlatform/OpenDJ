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
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2016 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.schema;

/**
 * This class defines the set of methods and structures that must be implemented
 * to define a new attribute syntax.
 */
abstract class AbstractSyntaxImpl implements SyntaxImpl {
    AbstractSyntaxImpl() {
        // Nothing to do.
    }

    @Override
    public String getApproximateMatchingRule() {
        return null;
    }

    @Override
    public String getEqualityMatchingRule() {
        return null;
    }

    @Override
    public String getOrderingMatchingRule() {
        return null;
    }

    @Override
    public String getSubstringMatchingRule() {
        return null;
    }

    @Override
    public boolean isBEREncodingRequired() {
        return false;
    }
}
