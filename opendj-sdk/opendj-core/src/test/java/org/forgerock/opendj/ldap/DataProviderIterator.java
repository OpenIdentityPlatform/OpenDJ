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
 * Copyright 2014-2015 ForgeRock AS.
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
