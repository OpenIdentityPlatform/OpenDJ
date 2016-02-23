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
 * Portions copyright 2012 ForgeRock AS.
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
