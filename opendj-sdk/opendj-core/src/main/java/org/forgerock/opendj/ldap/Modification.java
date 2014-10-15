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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 */

package org.forgerock.opendj.ldap;

import org.forgerock.util.Reject;

/**
 * A modification to be performed on an entry during a Modify operation.
 */
public final class Modification {
    private final ModificationType modificationType;
    private final Attribute attribute;

    /**
     * Creates a new modification having the provided modification type and
     * attribute values to be updated. Note that while the returned
     * {@code Modification} is immutable, the underlying attribute may not be.
     * The following code ensures that the returned {@code Modification} is
     * fully immutable:
     *
     * <pre>
     * Modification change = new Modification(modificationType, Attributes
     *         .unmodifiableAttribute(attribute));
     * </pre>
     *
     * @param modificationType
     *            The type of modification to be performed.
     * @param attribute
     *            The the attribute containing the values to be modified.
     */
    public Modification(final ModificationType modificationType, final Attribute attribute) {
        Reject.ifNull(modificationType, attribute);
        this.modificationType = modificationType;
        this.attribute = attribute;
    }

    /**
     * Returns the attribute containing the values to be modified.
     *
     * @return The the attribute containing the values to be modified.
     */
    public Attribute getAttribute() {
        return attribute;
    }

    /**
     * Returns the type of modification to be performed.
     *
     * @return The type of modification to be performed.
     */
    public ModificationType getModificationType() {
        return modificationType;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("Modification(modificationType=");
        builder.append(modificationType);
        builder.append(", attributeDescription=");
        builder.append(attribute.getAttributeDescriptionAsString());
        builder.append(", attributeValues={");
        boolean firstValue = true;
        for (final ByteString value : attribute) {
            if (!firstValue) {
                builder.append(", ");
            }
            builder.append(value);
            firstValue = false;
        }
        builder.append("})");
        return builder.toString();
    }

}
