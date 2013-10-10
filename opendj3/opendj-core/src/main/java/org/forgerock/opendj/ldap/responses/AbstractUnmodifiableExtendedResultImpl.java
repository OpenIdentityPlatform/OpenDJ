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
 */

package org.forgerock.opendj.ldap.responses;

import org.forgerock.opendj.ldap.ByteString;

/**
 * An abstract unmodifiable Extended result which can be used as the basis for
 * implementing new unmodifiable Extended operations.
 *
 * @param <S>
 *            The type of Extended result.
 */
abstract class AbstractUnmodifiableExtendedResultImpl<S extends ExtendedResult> extends
        AbstractUnmodifiableResultImpl<S> implements ExtendedResult {
    AbstractUnmodifiableExtendedResultImpl(final S impl) {
        super(impl);
    }

    @Override
    public String getOID() {
        return impl.getOID();
    }

    @Override
    public ByteString getValue() {
        return impl.getValue();
    }

    @Override
    public boolean hasValue() {
        return impl.hasValue();
    }
}
