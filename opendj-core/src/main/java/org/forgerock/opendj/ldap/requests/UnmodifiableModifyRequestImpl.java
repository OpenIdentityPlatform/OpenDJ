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
 * Portions copyright 2012-2014 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.requests;

import java.util.Collections;
import java.util.List;

import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.Attributes;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Functions;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldif.ChangeRecordVisitor;
import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;

import com.forgerock.opendj.util.Collections2;

/**
 * Unmodifiable modify request implementation.
 */
final class UnmodifiableModifyRequestImpl extends AbstractUnmodifiableRequest<ModifyRequest>
        implements ModifyRequest {
    UnmodifiableModifyRequestImpl(final ModifyRequest impl) {
        super(impl);
    }

    @Override
    public <R, P> R accept(final ChangeRecordVisitor<R, P> v, final P p) {
        return v.visitChangeRecord(p, this);
    }

    @Override
    public ModifyRequest addModification(final Modification modification) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ModifyRequest addModification(final ModificationType type,
            final String attributeDescription, final Object... values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Modification> getModifications() {
        // We need to make all attributes unmodifiable as well.
        final Function<Modification, Modification, NeverThrowsException> function =
                new Function<Modification, Modification, NeverThrowsException>() {
                    @Override
                    public Modification apply(final Modification value) {
                        final ModificationType type = value.getModificationType();
                        final Attribute attribute = Attributes.unmodifiableAttribute(value.getAttribute());
                        return new Modification(type, attribute);
                    }
                };

        final List<Modification> unmodifiableModifications =
                Collections2.transformedList(impl.getModifications(), function, Functions
                        .<Modification> identityFunction());
        return Collections.unmodifiableList(unmodifiableModifications);
    }

    @Override
    public DN getName() {
        return impl.getName();
    }

    @Override
    public ModifyRequest setName(final DN dn) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ModifyRequest setName(final String dn) {
        throw new UnsupportedOperationException();
    }
}
