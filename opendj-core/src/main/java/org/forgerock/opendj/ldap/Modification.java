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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import org.forgerock.util.Reject;

/** A modification to be performed on an entry during a Modify operation. */
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
