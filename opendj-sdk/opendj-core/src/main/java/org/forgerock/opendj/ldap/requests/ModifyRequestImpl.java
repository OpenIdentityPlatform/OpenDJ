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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2012-2015 ForgeRock AS.
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
