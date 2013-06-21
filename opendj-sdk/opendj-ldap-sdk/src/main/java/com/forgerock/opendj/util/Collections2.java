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
 */

package com.forgerock.opendj.util;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.forgerock.opendj.ldap.Function;

/**
 * Additional {@code Collection} based utility methods.
 */
public final class Collections2 {
    private static class TransformedCollection<M, N, P, C extends Collection<M>> extends
            AbstractCollection<N> implements Collection<N> {

        protected final C collection;

        protected final Function<? super M, ? extends N, P> funcMtoN;

        protected final Function<? super N, ? extends M, P> funcNtoM;

        protected final P p;

        protected TransformedCollection(final C collection,
                final Function<? super M, ? extends N, P> funcMtoN,
                final Function<? super N, ? extends M, P> funcNtoM, final P p) {
            this.collection = collection;
            this.funcMtoN = funcMtoN;
            this.funcNtoM = funcNtoM;
            this.p = p;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean add(final N e) {
            return collection.add(funcNtoM.apply(e, p));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void clear() {
            collection.clear();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @SuppressWarnings("unchecked")
        public boolean contains(final Object o) {
            final N tmp = (N) o;
            return collection.contains(funcNtoM.apply(tmp, p));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isEmpty() {
            return collection.isEmpty();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Iterator<N> iterator() {
            return Iterators.transformedIterator(collection.iterator(), funcMtoN, p);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @SuppressWarnings("unchecked")
        public boolean remove(final Object o) {
            final N tmp = (N) o;
            return collection.remove(funcNtoM.apply(tmp, p));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int size() {
            return collection.size();
        }

    }

    private static final class TransformedList<M, N, P> extends
            TransformedCollection<M, N, P, List<M>> implements List<N> {

        private TransformedList(final List<M> list,
                final Function<? super M, ? extends N, P> funcMtoN,
                final Function<? super N, ? extends M, P> funcNtoM, final P p) {
            super(list, funcMtoN, funcNtoM, p);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void add(final int index, final N element) {
            collection.add(index, funcNtoM.apply(element, p));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean addAll(final int index, final Collection<? extends N> c) {
            // We cannot transform c here due to type-safety.
            boolean result = false;
            for (final N e : c) {
                result |= add(e);
            }
            return result;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public N get(final int index) {
            return funcMtoN.apply(collection.get(index), p);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @SuppressWarnings("unchecked")
        public int indexOf(final Object o) {
            final N tmp = (N) o;
            return collection.indexOf(funcNtoM.apply(tmp, p));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @SuppressWarnings("unchecked")
        public int lastIndexOf(final Object o) {
            final N tmp = (N) o;
            return collection.lastIndexOf(funcNtoM.apply(tmp, p));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ListIterator<N> listIterator() {
            return listIterator(0);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ListIterator<N> listIterator(final int index) {
            final ListIterator<M> iterator = collection.listIterator(index);
            return new ListIterator<N>() {

                @Override
                public void add(final N e) {
                    iterator.add(funcNtoM.apply(e, p));
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
                    return funcMtoN.apply(iterator.next(), p);
                }

                @Override
                public int nextIndex() {
                    return iterator.nextIndex();
                }

                @Override
                public N previous() {
                    return funcMtoN.apply(iterator.previous(), p);
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
                    iterator.set(funcNtoM.apply(e, p));
                }

            };
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public N remove(final int index) {
            return funcMtoN.apply(collection.remove(index), p);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public N set(final int index, final N element) {
            final M result = collection.set(index, funcNtoM.apply(element, p));
            return funcMtoN.apply(result, p);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public List<N> subList(final int fromIndex, final int toIndex) {
            final List<M> subList = collection.subList(fromIndex, toIndex);
            return new TransformedList<M, N, P>(subList, funcMtoN, funcNtoM, p);
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
     * @param <P>
     *            The type of the additional parameter to the function's
     *            {@code apply} method. Use {@link java.lang.Void} for functions
     *            that do not need an additional parameter.
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
     * @param p
     *            A predicate specified parameter.
     * @return A view of {@code collection} whose values have been mapped to
     *         elements of type {@code N} using {@code funcMtoN}.
     */
    public static <M, N, P> Collection<N> transformedCollection(final Collection<M> collection,
            final Function<? super M, ? extends N, P> funcMtoN,
            final Function<? super N, ? extends M, P> funcNtoM, final P p) {
        return new TransformedCollection<M, N, P, Collection<M>>(collection, funcMtoN, funcNtoM, p);
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
            final Function<? super M, ? extends N, Void> funcMtoN,
            final Function<? super N, ? extends M, Void> funcNtoM) {
        return new TransformedCollection<M, N, Void, Collection<M>>(collection, funcMtoN, funcNtoM,
                null);
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
     * @param <P>
     *            The type of the additional parameter to the function's
     *            {@code apply} method. Use {@link java.lang.Void} for functions
     *            that do not need an additional parameter.
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
     * @param p
     *            A predicate specified parameter.
     * @return A view of {@code list} whose values have been mapped to elements
     *         of type {@code N} using {@code funcMtoN}.
     */
    public static <M, N, P> List<N> transformedList(final List<M> list,
            final Function<? super M, ? extends N, P> funcMtoN,
            final Function<? super N, ? extends M, P> funcNtoM, final P p) {
        return new TransformedList<M, N, P>(list, funcMtoN, funcNtoM, p);
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
            final Function<? super M, ? extends N, Void> funcMtoN,
            final Function<? super N, ? extends M, Void> funcNtoM) {
        return new TransformedList<M, N, Void>(list, funcMtoN, funcNtoM, null);
    }

    // Prevent instantiation
    private Collections2() {
        // Do nothing.
    }

}
