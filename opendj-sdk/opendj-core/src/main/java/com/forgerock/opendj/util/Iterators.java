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

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;

/**
 * Utility methods for manipulating {@link Iterator}s.
 */
public final class Iterators {
    private static final class ArrayIterator<M> implements Iterator<M> {
        private int i;
        private final M[] a;

        /** Constructed via factory methods. */
        private ArrayIterator(final M[] a) {
            this.a = a;
        }

        /** {@inheritDoc} */
        public boolean hasNext() {
            return i < a.length;
        }

        /** {@inheritDoc} */
        public M next() {
            if (hasNext()) {
                return a[i++];
            } else {
                throw new NoSuchElementException();
            }
        }

        /** {@inheritDoc} */
        public void remove() {
            throw new UnsupportedOperationException();
        }

    }

    private static final class EmptyIterator<M> implements Iterator<M> {
        /** {@inheritDoc} */
        public boolean hasNext() {
            return false;
        }

        /** {@inheritDoc} */
        public M next() {
            throw new NoSuchElementException();
        }

        /** {@inheritDoc} */
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FilteredIterator<M, P> implements Iterator<M> {

        private boolean hasNextMustIterate = true;
        private final Iterator<M> iterator;
        private M next;

        private final P parameter;
        private final Predicate<? super M, P> predicate;

        /** Constructed via factory methods. */
        private FilteredIterator(final Iterator<M> iterator,
                final Predicate<? super M, P> predicate, final P p) {
            this.iterator = iterator;
            this.predicate = predicate;
            this.parameter = p;
        }

        /** {@inheritDoc} */
        public boolean hasNext() {
            if (hasNextMustIterate) {
                hasNextMustIterate = false;
                while (iterator.hasNext()) {
                    next = iterator.next();
                    if (predicate.matches(next, parameter)) {
                        return true;
                    }
                }
                next = null;
                return false;
            } else {
                return next != null;
            }
        }

        /** {@inheritDoc} */
        public M next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            hasNextMustIterate = true;
            return next;
        }

        /** {@inheritDoc} */
        public void remove() {
            iterator.remove();
        }

    }

    private static final class SingletonIterator<M> implements Iterator<M> {
        private M value;

        /** Constructed via factory methods. */
        private SingletonIterator(final M value) {
            this.value = value;
        }

        /** {@inheritDoc} */
        public boolean hasNext() {
            return value != null;
        }

        /** {@inheritDoc} */
        public M next() {
            if (value != null) {
                final M tmp = value;
                value = null;
                return tmp;
            } else {
                throw new NoSuchElementException();
            }
        }

        /** {@inheritDoc} */
        public void remove() {
            throw new UnsupportedOperationException();
        }

    }

    private static final class TransformedIterator<M, N> implements Iterator<N> {

        private final Function<? super M, ? extends N, NeverThrowsException> function;
        private final Iterator<M> iterator;

        /** Constructed via factory methods. */
        private TransformedIterator(final Iterator<M> iterator,
                final Function<? super M, ? extends N, NeverThrowsException> function) {
            this.iterator = iterator;
            this.function = function;
        }

        /** {@inheritDoc} */
        public boolean hasNext() {
            return iterator.hasNext();
        }

        /** {@inheritDoc} */
        public N next() {
            return function.apply(iterator.next());
        }

        /** {@inheritDoc} */
        public void remove() {
            iterator.remove();
        }

    }

    private static final class UnmodifiableIterator<M> implements Iterator<M> {
        private final Iterator<M> iterator;

        private UnmodifiableIterator(final Iterator<M> iterator) {
            this.iterator = iterator;
        }

        /** {@inheritDoc} */
        public boolean hasNext() {
            return iterator.hasNext();
        }

        /** {@inheritDoc} */
        public M next() {
            return iterator.next();
        }

        /** {@inheritDoc} */
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static final Iterator<Object> EMPTY_ITERATOR = new EmptyIterator<>();

    /**
     * Returns an iterator over the elements contained in {@code a}. The
     * returned iterator does not support element removal via the
     * {@code remove()} method.
     *
     * @param <M>
     *            The type of elements contained in {@code a}.
     * @param a
     *            The array of elements to be returned by the iterator.
     * @return An iterator over the elements contained in {@code a}.
     */
    public static <M> Iterator<M> arrayIterator(final M[] a) {
        return new ArrayIterator<>(a);
    }

    /**
     * Returns an immutable empty iterator.
     *
     * @param <M>
     *            The required type of the empty iterator.
     * @return An immutable empty iterator.
     */
    @SuppressWarnings("unchecked")
    public static <M> Iterator<M> emptyIterator() {
        return (Iterator<M>) EMPTY_ITERATOR;
    }

    /**
     * Returns a filtered view of {@code iterator} containing only those
     * elements which match {@code predicate}. The returned iterator supports
     * element removal via the {@code remove()} method subject to any
     * constraints imposed by {@code iterator}.
     *
     * @param <M>
     *            The type of elements contained in {@code iterator}.
     * @param <P>
     *            The type of the additional parameter to the predicate's
     *            {@code matches} method. Use {@link java.lang.Void} for
     *            predicates that do not need an additional parameter.
     * @param iterator
     *            The iterator to be filtered.
     * @param predicate
     *            The predicate.
     * @param p
     *            A predicate specified parameter.
     * @return A filtered view of {@code iterator} containing only those
     *         elements which match {@code predicate}.
     */
    public static <M, P> Iterator<M> filteredIterator(final Iterator<M> iterator,
            final Predicate<? super M, P> predicate, final P p) {
        return new FilteredIterator<>(iterator, predicate, p);
    }

    /**
     * Returns a filtered view of {@code iterator} containing only those
     * elements which match {@code predicate}. The returned iterator supports
     * element removal via the {@code remove()} method subject to any
     * constraints imposed by {@code iterator}.
     *
     * @param <M>
     *            The type of elements contained in {@code iterator}.
     * @param iterator
     *            The iterator to be filtered.
     * @param predicate
     *            The predicate.
     * @return A filtered view of {@code iterator} containing only those
     *         elements which match {@code predicate}.
     */
    public static <M> Iterator<M> filteredIterator(final Iterator<M> iterator,
            final Predicate<? super M, Void> predicate) {
        return new FilteredIterator<>(iterator, predicate, null);
    }

    /**
     * Returns an iterator containing the single element {@code value}. The
     * returned iterator does not support element removal via the
     * {@code remove()} method.
     *
     * @param <M>
     *            The type of the single element {@code value}.
     * @param value
     *            The single element to be returned by the iterator.
     * @return An iterator containing the single element {@code value}.
     */
    public static <M> Iterator<M> singletonIterator(final M value) {
        return new SingletonIterator<>(value);
    }

    /**
     * Returns a view of {@code iterator} whose values have been mapped to
     * elements of type {@code N} using {@code function}. The returned iterator
     * supports element removal via the {@code remove()} method subject to any
     * constraints imposed by {@code iterator}.
     *
     * @param <M>
     *            The type of elements contained in {@code iterator}.
     * @param <N>
     *            The type of elements contained in the returned iterator.
     * @param iterator
     *            The iterator to be transformed.
     * @param function
     *            The function.
     * @return A view of {@code iterator} whose values have been mapped to
     *         elements of type {@code N} using {@code function}.
     */
    public static <M, N> Iterator<N> transformedIterator(final Iterator<M> iterator,
            final Function<? super M, ? extends N, NeverThrowsException> function) {
        return new TransformedIterator<>(iterator, function);
    }

    /**
     * Returns a read-only view of {@code iterator} which does not support
     * element removal via the {@code remove()}. Attempts to use the
     * {@code remove()} method will result in a
     * {@code UnsupportedOperationException}.
     *
     * @param <M>
     *            The type of elements contained in {@code iterator}.
     * @param iterator
     *            The iterator to be made read-only.
     * @return A read-only view of {@code iterator} which does not support
     *         element removal via the {@code remove()}.
     */
    public static <M> Iterator<M> unmodifiableIterator(final Iterator<M> iterator) {
        return new UnmodifiableIterator<>(iterator);
    }

    /** Prevent instantiation. */
    private Iterators() {
        // Do nothing.
    }

}
