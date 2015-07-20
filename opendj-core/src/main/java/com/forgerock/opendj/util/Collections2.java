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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS.
 */
package com.forgerock.opendj.util;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;

/**
 * Additional {@code Collection} based utility methods.
 */
public final class Collections2 {
    private static class TransformedCollection<M, N, C extends Collection<M>> extends
            AbstractCollection<N> implements Collection<N> {

        protected final C collection;

        protected final Function<? super M, ? extends N, NeverThrowsException> funcMtoN;

        protected final Function<? super N, ? extends M, NeverThrowsException> funcNtoM;

        protected TransformedCollection(final C collection,
                final Function<? super M, ? extends N, NeverThrowsException> funcMtoN,
                final Function<? super N, ? extends M, NeverThrowsException> funcNtoM) {
            this.collection = collection;
            this.funcMtoN = funcMtoN;
            this.funcNtoM = funcNtoM;
        }

        /** {@inheritDoc} */
        @Override
        public boolean add(final N e) {
            return collection.add(funcNtoM.apply(e));
        }

        /** {@inheritDoc} */
        @Override
        public void clear() {
            collection.clear();
        }

        /** {@inheritDoc} */
        @Override
        @SuppressWarnings("unchecked")
        public boolean contains(final Object o) {
            return collection.contains(funcNtoM.apply((N) o));
        }

        /** {@inheritDoc} */
        @Override
        public boolean isEmpty() {
            return collection.isEmpty();
        }

        /** {@inheritDoc} */
        @Override
        public Iterator<N> iterator() {
            return Iterators.transformedIterator(collection.iterator(), funcMtoN);
        }

        /** {@inheritDoc} */
        @Override
        @SuppressWarnings("unchecked")
        public boolean remove(final Object o) {
            return collection.remove(funcNtoM.apply((N) o));
        }

        /** {@inheritDoc} */
        @Override
        public int size() {
            return collection.size();
        }

    }

    private static final class TransformedList<M, N> extends
            TransformedCollection<M, N, List<M>> implements List<N> {

        private TransformedList(final List<M> list,
                final Function<? super M, ? extends N, NeverThrowsException> funcMtoN,
                final Function<? super N, ? extends M, NeverThrowsException> funcNtoM) {
            super(list, funcMtoN, funcNtoM);
        }

        /** {@inheritDoc} */
        @Override
        public void add(final int index, final N element) {
            collection.add(index, funcNtoM.apply(element));
        }

        /** {@inheritDoc} */
        @Override
        public boolean addAll(final int index, final Collection<? extends N> c) {
            // We cannot transform c here due to type-safety.
            boolean result = false;
            for (final N e : c) {
                result |= add(e);
            }
            return result;
        }

        /** {@inheritDoc} */
        @Override
        public N get(final int index) {
            return funcMtoN.apply(collection.get(index));
        }

        /** {@inheritDoc} */
        @Override
        @SuppressWarnings("unchecked")
        public int indexOf(final Object o) {
            return collection.indexOf(funcNtoM.apply((N) o));
        }

        /** {@inheritDoc} */
        @Override
        @SuppressWarnings("unchecked")
        public int lastIndexOf(final Object o) {
            return collection.lastIndexOf(funcNtoM.apply((N) o));
        }

        /** {@inheritDoc} */
        @Override
        public ListIterator<N> listIterator() {
            return listIterator(0);
        }

        /** {@inheritDoc} */
        @Override
        public ListIterator<N> listIterator(final int index) {
            final ListIterator<M> iterator = collection.listIterator(index);
            return new ListIterator<N>() {

                @Override
                public void add(final N e) {
                    iterator.add(funcNtoM.apply(e));
                }

                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public boolean hasPrevious() {
                    return iterator.hasPrevious();
                }

                @Override
                public N next() {
                    return funcMtoN.apply(iterator.next());
                }

                @Override
                public int nextIndex() {
                    return iterator.nextIndex();
                }

                @Override
                public N previous() {
                    return funcMtoN.apply(iterator.previous());
                }

                @Override
                public int previousIndex() {
                    return iterator.previousIndex();
                }

                @Override
                public void remove() {
                    iterator.remove();
                }

                @Override
                public void set(final N e) {
                    iterator.set(funcNtoM.apply(e));
                }

            };
        }

        /** {@inheritDoc} */
        @Override
        public N remove(final int index) {
            return funcMtoN.apply(collection.remove(index));
        }

        /** {@inheritDoc} */
        @Override
        public N set(final int index, final N element) {
            final M result = collection.set(index, funcNtoM.apply(element));
            return funcMtoN.apply(result);
        }

        /** {@inheritDoc} */
        @Override
        public List<N> subList(final int fromIndex, final int toIndex) {
            final List<M> subList = collection.subList(fromIndex, toIndex);
            return new TransformedList<>(subList, funcMtoN, funcNtoM);
        }

    }

    /**
     * Returns a view of {@code collection} whose values have been mapped to
     * elements of type {@code N} using {@code funcMtoN}. The returned
     * collection supports all operations.
     *
     * @param <M>
     *            The type of elements contained in {@code collection}.
     * @param <N>
     *            The type of elements contained in the returned collection.
     * @param collection
     *            The collection to be transformed.
     * @param funcMtoN
     *            A function which maps values of type {@code M} to values of
     *            type {@code N}. This function will be used when retrieving
     *            values from {@code collection}.
     * @param funcNtoM
     *            A function which maps values of type {@code N} to values of
     *            type {@code M}. This function will be used when performing
     *            queries and adding values to {@code collection} .
     * @return A view of {@code collection} whose values have been mapped to
     *         elements of type {@code N} using {@code funcMtoN}.
     */
    public static <M, N> Collection<N> transformedCollection(final Collection<M> collection,
            final Function<? super M, ? extends N, NeverThrowsException> funcMtoN,
            final Function<? super N, ? extends M, NeverThrowsException> funcNtoM) {
        return new TransformedCollection<>(collection, funcMtoN, funcNtoM);
    }

    /**
     * Returns a view of {@code list} whose values have been mapped to elements
     * of type {@code N} using {@code funcMtoN}. The returned list supports all
     * operations.
     *
     * @param <M>
     *            The type of elements contained in {@code list}.
     * @param <N>
     *            The type of elements contained in the returned list.
     * @param list
     *            The list to be transformed.
     * @param funcMtoN
     *            A function which maps values of type {@code M} to values of
     *            type {@code N}. This function will be used when retrieving
     *            values from {@code list}.
     * @param funcNtoM
     *            A function which maps values of type {@code N} to values of
     *            type {@code M}. This function will be used when performing
     *            queries and adding values to {@code list} .
     * @return A view of {@code list} whose values have been mapped to elements
     *         of type {@code N} using {@code funcMtoN}.
     */
    public static <M, N> List<N> transformedList(final List<M> list,
            final Function<? super M, ? extends N, NeverThrowsException> funcMtoN,
            final Function<? super N, ? extends M, NeverThrowsException> funcNtoM) {
        return new TransformedList<>(list, funcMtoN, funcNtoM);
    }

    /** Prevent instantiation. */
    private Collections2() {
        // Do nothing.
    }

}
