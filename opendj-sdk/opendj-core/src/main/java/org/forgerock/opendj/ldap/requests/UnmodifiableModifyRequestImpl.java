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
 *      Portions copyright 2012-2014 ForgeRock AS.
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
