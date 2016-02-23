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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions copyright 2012-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import java.util.LinkedList;
import java.util.List;

import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldif.ChangeRecordVisitor;
import org.forgerock.util.Reject;

/**
 * Modify request implementation.
 */
final class ModifyRequestImpl extends AbstractRequestImpl<ModifyRequest> implements ModifyRequest {
    private final List<Modification> changes = new LinkedList<>();
    private DN name;

    ModifyRequestImpl(final DN name) {
        this.name = name;
    }

    ModifyRequestImpl(final ModifyRequest modifyRequest) {
        super(modifyRequest);
        this.name = modifyRequest.getName();

        // Deep copy.
        for (final Modification modification : modifyRequest.getModifications()) {
            final ModificationType type = modification.getModificationType();
            final Attribute attribute = new LinkedAttribute(modification.getAttribute());
            final Modification copy = new Modification(type, attribute);
            this.changes.add(copy);
        }
    }

    @Override
    public <R, P> R accept(final ChangeRecordVisitor<R, P> v, final P p) {
        return v.visitChangeRecord(p, this);
    }

    @Override
    public ModifyRequest addModification(final Modification change) {
        Reject.ifNull(change);
        changes.add(change);
        return this;
    }

    @Override
    public ModifyRequest addModification(final ModificationType type,
            final String attributeDescription, final Object... values) {
        Reject.ifNull(type, attributeDescription);
        Reject.ifNull(values);
        changes.add(new Modification(type, new LinkedAttribute(attributeDescription, values)));
        return this;
    }

    @Override
    public List<Modification> getModifications() {
        return changes;
    }

    @Override
    public DN getName() {
        return name;
    }

    @Override
    public ModifyRequest setName(final DN dn) {
        Reject.ifNull(dn);
        this.name = dn;
        return this;
    }

    @Override
    public ModifyRequest setName(final String dn) {
        Reject.ifNull(dn);
        this.name = DN.valueOf(dn);
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("ModifyRequest(dn=");
        builder.append(getName());
        builder.append(", changes=");
        builder.append(getModifications());
        builder.append(", controls=");
        builder.append(getControls());
        builder.append(")");
        return builder.toString();
    }

    @Override
    ModifyRequest getThis() {
        return this;
    }

}
