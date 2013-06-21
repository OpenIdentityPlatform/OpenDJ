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
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldif.ChangeRecordVisitor;

/**
 * Unmodifiable modify DN request implementation.
 */
final class UnmodifiableModifyDNRequestImpl extends AbstractUnmodifiableRequest<ModifyDNRequest>
        implements ModifyDNRequest {
    UnmodifiableModifyDNRequestImpl(final ModifyDNRequest impl) {
        super(impl);
    }

    @Override
    public <R, P> R accept(final ChangeRecordVisitor<R, P> v, final P p) {
        return v.visitChangeRecord(p, this);
    }

    @Override
    public DN getName() {
        return impl.getName();
    }

    @Override
    public RDN getNewRDN() {
        return impl.getNewRDN();
    }

    @Override
    public DN getNewSuperior() {
        return impl.getNewSuperior();
    }

    @Override
    public boolean isDeleteOldRDN() {
        return impl.isDeleteOldRDN();
    }

    @Override
    public ModifyDNRequest setDeleteOldRDN(final boolean deleteOldRDN) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ModifyDNRequest setName(final DN dn) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ModifyDNRequest setName(final String dn) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ModifyDNRequest setNewRDN(final RDN rdn) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ModifyDNRequest setNewRDN(final String rdn) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ModifyDNRequest setNewSuperior(final DN dn) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ModifyDNRequest setNewSuperior(final String dn) {
        throw new UnsupportedOperationException();
    }
}
