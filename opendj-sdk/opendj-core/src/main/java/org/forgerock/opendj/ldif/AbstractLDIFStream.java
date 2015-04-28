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
 *      Portions copyright 2011-2015 ForgeRock AS
 */

package org.forgerock.opendj.ldif;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Matcher;
import org.forgerock.opendj.ldap.schema.AttributeType;

/**
 * Common LDIF reader/writer functionality.
 */
abstract class AbstractLDIFStream {
    final Set<AttributeDescription> excludeAttributes = new HashSet<>();
    final Set<DN> excludeBranches = new HashSet<>();
    final List<Matcher> excludeFilters = new LinkedList<>();
    boolean excludeOperationalAttributes;
    boolean excludeUserAttributes;
    final Set<AttributeDescription> includeAttributes = new HashSet<>();
    final Set<DN> includeBranches = new HashSet<>();
    final List<Matcher> includeFilters = new LinkedList<>();

    AbstractLDIFStream() {
        // Nothing to do.
    }

    final boolean isAttributeExcluded(final AttributeDescription attributeDescription) {
        // Let explicit include override more general exclude.
        if (!excludeAttributes.isEmpty() && excludeAttributes.contains(attributeDescription)) {
            return true;
        } else if (!includeAttributes.isEmpty()) {
            return !includeAttributes.contains(attributeDescription);
        } else {
            final AttributeType type = attributeDescription.getAttributeType();
            return (excludeOperationalAttributes && type.isOperational())
                    || (excludeUserAttributes && !type.isOperational());
        }
    }

    final boolean isBranchExcluded(final DN dn) {
        if (!excludeBranches.isEmpty()) {
            for (final DN excludeBranch : excludeBranches) {
                if (excludeBranch.isSuperiorOrEqualTo(dn)) {
                    return true;
                }
            }
        }
        if (!includeBranches.isEmpty()) {
            for (final DN includeBranch : includeBranches) {
                if (includeBranch.isSuperiorOrEqualTo(dn)) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    final boolean isEntryExcluded(final Entry entry) {
        if (!excludeFilters.isEmpty()) {
            for (final Matcher excludeFilter : excludeFilters) {
                if (excludeFilter.matches(entry).toBoolean()) {
                    return true;
                }
            }
        }
        if (!includeFilters.isEmpty()) {
            for (final Matcher includeFilter : includeFilters) {
                if (includeFilter.matches(entry).toBoolean()) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

}
