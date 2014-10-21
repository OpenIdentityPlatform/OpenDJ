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
 *      Portions copyright 2012-2013 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldif.ChangeRecordVisitor;

import org.forgerock.util.Reject;

/**
 * Modify DN request implementation.
 */
final class ModifyDNRequestImpl extends AbstractRequestImpl<ModifyDNRequest> implements
        ModifyDNRequest {
    private boolean deleteOldRDN;
    private DN name;
    private RDN newRDN;
    private DN newSuperior;

    ModifyDNRequestImpl(final DN name, final RDN newRDN) {
        this.name = name;
        this.newRDN = newRDN;
    }

    ModifyDNRequestImpl(final ModifyDNRequest modifyDNRequest) {
        super(modifyDNRequest);
        this.name = modifyDNRequest.getName();
        this.newSuperior = modifyDNRequest.getNewSuperior();
        this.newRDN = modifyDNRequest.getNewRDN();
        this.deleteOldRDN = modifyDNRequest.isDeleteOldRDN();
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
    public RDN getNewRDN() {
        return newRDN;
    }

    @Override
    public DN getNewSuperior() {
        return newSuperior;
    }

    @Override
    public boolean isDeleteOldRDN() {
        return deleteOldRDN;
    }

    @Override
    public ModifyDNRequestImpl setDeleteOldRDN(final boolean deleteOldRDN) {
        this.deleteOldRDN = deleteOldRDN;
        return this;
    }

    @Override
    public ModifyDNRequest setName(final DN dn) {
        Reject.ifNull(dn);
        this.name = dn;
        return this;
    }

    @Override
    public ModifyDNRequest setName(final String dn) {
        Reject.ifNull(dn);
        this.name = DN.valueOf(dn);
        return this;
    }

    @Override
    public ModifyDNRequest setNewRDN(final RDN rdn) {
        Reject.ifNull(rdn);
        this.newRDN = rdn;
        return this;
    }

    @Override
    public ModifyDNRequest setNewRDN(final String rdn) {
        Reject.ifNull(rdn);
        this.newRDN = RDN.valueOf(rdn);
        return this;
    }

    @Override
    public ModifyDNRequest setNewSuperior(final DN dn) {
        this.newSuperior = dn;
        return this;
    }

    @Override
    public ModifyDNRequest setNewSuperior(final String dn) {
        this.newSuperior = (dn != null) ? DN.valueOf(dn) : null;
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("ModifyDNRequest(name=");
        builder.append(getName());
        builder.append(", newRDN=");
        builder.append(getNewRDN());
        builder.append(", deleteOldRDN=");
        builder.append(isDeleteOldRDN());
        builder.append(", newSuperior=");
        builder.append(getNewSuperior());
        builder.append(", controls=");
        builder.append(getControls());
        builder.append(")");
        return builder.toString();
    }

    @Override
    ModifyDNRequest getThis() {
        return this;
    }

}
