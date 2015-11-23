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
 *      Copyright 2014-2015 ForgeRock AS
 */
package org.forgerock.opendj.ldap;

import java.util.Collections;
import java.util.Iterator;

import org.testng.annotations.DataProvider;

/**
 * An iterator implementation that converts an Iterator/Iterable/array to an
 * Iterator suitable to return from a {@link DataProvider} methods.
 */
@SuppressWarnings("javadoc")
public class DataProviderIterator implements Iterator<Object[]> {
    private final Iterator<?> iter;

    public DataProviderIterator(Iterable<?> iterable) {
        this.iter = iterable != null
                ? iterable.iterator()
                : Collections.emptySet().iterator();
    }

    @Override
    public boolean hasNext() {
        return iter.hasNext();
    }

    @Override
    public Object[] next() {
        return new Object[] { iter.next() };
    }

    @Override
    public void remove() {
        iter.remove();
    }
}
