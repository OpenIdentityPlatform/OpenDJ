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
 * Portions copyright 2012-2013 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldif.ChangeRecordVisitor;

import org.forgerock.util.Reject;

/**
 * Delete request implementation.
 */
final class DeleteRequestImpl extends AbstractRequestImpl<DeleteRequest> implements DeleteRequest {
    private DN name;

    DeleteRequestImpl(final DeleteRequest deleteRequest) {
        super(deleteRequest);
        this.name = deleteRequest.getName();
    }

    DeleteRequestImpl(final DN name) {
        this.name = name;
    }

    @Override
    public <R, P> R accept(final ChangeRecordVisitor<R, P> v, final P p) {
        return v.visitChangeRecord(p, this);
    }

    @Override
    public DN getName() {
        return name;
    }

    @Override
    public DeleteRequest setName(final DN dn) {
        Reject.ifNull(dn);
        this.name = dn;
        return this;
    }

    @Override
    public DeleteRequest setName(final String dn) {
        Reject.ifNull(dn);
        this.name = DN.valueOf(dn);
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("DeleteRequest(name=");
        builder.append(getName());
        builder.append(", controls=");
        builder.append(getControls());
        builder.append(")");
        return builder.toString();
    }

    @Override
    DeleteRequest getThis() {
        return this;
    }

}
