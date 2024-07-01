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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package com.forgerock.opendj.util;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import org.forgerock.util.Reject;

/**
 * A small set of values. This set implementation is optimized to use
 * as little memory as possible in the case where there zero or one elements.
 * <p>
 * In addition, any normalization of elements is delayed until the second
 * element is added (normalization may be triggered by invoking
 * {@link Object#hashCode()} or {@link Object#equals(Object)}.
 *
 * @param <E>
 *          The type of elements to be contained in this small set.
 */
public final class SmallSet<E> extends AbstractSet<E> {
    /**
     * The set of elements if there are more than one.
     * <p>
     * It is implemented using a {@link Map} to be able to retrieve quickly
     * the actual value with {@link Map#get(Object)}.
     * <p>
     * See the {@link #get(Object)} and {@link #addOrReplace(Object)} methods.
     */
    private LinkedHashMap<E, E> elements;
    /** The first element. */
    private E firstElement;

    /** Creates a new small set which is initially empty. */
    public SmallSet() {
        // No implementation required.
    }

    /**
     * Creates a new small set from the provided collection.
     *
     * @param c
     *          the collection whose elements are to be placed into this set
     * @throws java.lang.NullPointerException
     *           if the specified collection is null
     */
    public SmallSet(Collection<? extends E> c) {
        addAll(c);
    }

    /**
     * Creates a new small set with an initial capacity.
     *
     * @param initialCapacity
     *          The capacity of the set
     */
    public SmallSet(int initialCapacity) {
        Reject.ifFalse(initialCapacity >= 0);

        if (initialCapacity > 1) {
            elements = new LinkedHashMap<>(initialCapacity);
        }
    }

    @Override
    public boolean add(E e) {
        // Special handling for the first value which avoids potentially expensive normalization.
        if (firstElement == null && elements == null) {
            firstElement = e;
            return true;
        }

        // Create the value set if necessary.
        if (elements == null) {
            if (firstElement.equals(e)) {
                return false;
            }

            elements = new LinkedHashMap<>(2);
            addForbidsReplace(elements, firstElement);
            firstElement = null;
        }

        return addForbidsReplace(elements, e);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        if (elements != null) {
            return addAllForbidsReplace(elements, c);
        }

        if (firstElement != null && !c.isEmpty()) {
            elements = new LinkedHashMap<>(1 + c.size());
            addForbidsReplace(elements, firstElement);
            firstElement = null;
            return addAllForbidsReplace(elements, c);
        }

        // Initially empty.
        switch (c.size()) {
        case 0:
            // Do nothing.
            return false;
        case 1:
            firstElement = c.iterator().next();
            return true;
        default:
            elements = new LinkedHashMap<>(c.size());
            addAllForbidsReplace(elements, c);
            return true;
        }
    }

    private boolean addForbidsReplace(LinkedHashMap<E, E> map, E e) {
        if (map.containsKey(e)) {
            return false;
        }
        return map.put(e, e) == null;
    }

    private boolean addAllForbidsReplace(LinkedHashMap<E, E> map, Collection<? extends E> c) {
        boolean modified = false;
        for (E e : c) {
            modified |= addForbidsReplace(map, e);
        }
        return modified;
    }

    /**
     * Adds an element to this small set or replaces the existing element's value with the provided
     * one.
     * <p>
     * The replacement aspect is useful when the small set uses a "normalized" value to compare
     * elements for equality but stores them as an "actual" value.
     *
     * @param element
     *          the actual element to replace in this small set
     */
    public void addOrReplace(E element) {
        remove(element);
        add(element);
    }

    @Override
    public void clear() {
        firstElement = null;
        elements = null;
    }

    @Override
    public Iterator<E> iterator() {
        if (elements != null) {
            return elements.keySet().iterator();
        } else if (firstElement != null) {
            return new Iterator<E>() {
                private boolean hasNext = true;

                @Override
                public boolean hasNext() {
                    return hasNext;
                }

                @Override
                public E next() {
                    if (!hasNext) {
                        throw new NoSuchElementException();
                    }

                    hasNext = false;
                    return firstElement;
                }

                @Override
                public void remove() {
                    firstElement = null;
                }
            };
        } else {
            return Collections.<E> emptySet().iterator();
        }
    }

    @Override
    public boolean remove(Object o) {
        if (elements != null) {
            // Note: if there is one or zero values left we could stop using the set.
            // However, lets assume that if the set was multi-valued before
            // then it may become multi-valued again.
            return elements.keySet().remove(o);
        }

        if (firstElement != null && firstElement.equals(o)) {
            firstElement = null;
            return true;
        }
        return false;
    }

    @Override
    public boolean contains(Object o) {
        if (elements != null) {
            return elements.containsKey(o);
        }
        return firstElement != null && firstElement.equals(o);
    }

    /**
     * Returns the element in this small set that is equal to the provided element.
     *
     * @param o
     *          the element to look for
     * @return the element in this small set that is equal to the provided element,
     *         or {@code null} if no such element exists
     */
    public E get(Object o) {
        if (elements != null) {
            return elements.get(o);
        }
        return firstElement != null && firstElement.equals(o) ? firstElement : null;
    }

    @Override
    public int size() {
        if (elements != null) {
            return elements.size();
        } else if (firstElement != null) {
            return 1;
        } else {
            return 0;
        }
    }
}
