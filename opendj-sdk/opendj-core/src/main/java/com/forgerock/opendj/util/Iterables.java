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
 *      Portions copyright 2013-2015 ForgeRock AS.
 */
package com.forgerock.opendj.util;

import java.util.Collection;
import java.util.Iterator;

import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;

/**
 * Utility methods for manipulating {@link Iterable}s.
 */
public final class Iterables {
    private static abstract class AbstractIterable<M> implements Iterable<M> {
        @Override
        public String toString() {
            return Iterables.toString(this);
        }
    }

    private static final class ArrayIterable<M> extends AbstractIterable<M> {
        private final M[] a;

        /** Constructed via factory methods. */
        private ArrayIterable(final M[] a) {
            this.a = a;
        }

        @Override
        public Iterator<M> iterator() {
            return Iterators.arrayIterator(a);
        }
    }

    private static final class EmptyIterable<M> extends AbstractIterable<M> {
        @Override
        public Iterator<M> iterator() {
            return Iterators.emptyIterator();
        }
    }

    private static final class FilteredIterable<M, P> extends AbstractIterable<M> {
        private final Iterable<M> iterable;
        private final P parameter;
        private final Predicate<? super M, P> predicate;

        /** Constructed via factory methods. */
        private FilteredIterable(final Iterable<M> iterable,
                final Predicate<? super M, P> predicate, final P p) {
            this.iterable = iterable;
            this.predicate = predicate;
            this.parameter = p;
        }

        @Override
        public Iterator<M> iterator() {
            return Iterators.filteredIterator(iterable.iterator(), predicate, parameter);
        }
    }

    private static final class SingletonIterable<M> extends AbstractIterable<M> {
        private final M value;

        /** Constructed via factory methods. */
        private SingletonIterable(final M value) {
            this.value = value;
        }

        @Override
        public Iterator<M> iterator() {
            return Iterators.singletonIterator(value);
        }
    }

    private static final class TransformedIterable<M, N> extends AbstractIterable<N> {
        private final Function<? super M, ? extends N, NeverThrowsException> function;
        private final Iterable<M> iterable;

        /** Constructed via factory methods. */
        private TransformedIterable(final Iterable<M> iterable,
                final Function<? super M, ? extends N, NeverThrowsException> function) {
            this.iterable = iterable;
            this.function = function;
        }

        @Override
        public Iterator<N> iterator() {
            return Iterators.transformedIterator(iterable.iterator(), function);
        }
    }

    private static final class UnmodifiableIterable<M> extends AbstractIterable<M> {
        private final Iterable<M> iterable;

        /** Constructed via factory methods. */
        private UnmodifiableIterable(final Iterable<M> iterable) {
            this.iterable = iterable;
        }

        @Override
        public Iterator<M> iterator() {
            return Iterators.unmodifiableIterator(iterable.iterator());
        }
    }

    private static final Iterable<Object> EMPTY_ITERABLE = new EmptyIterable<>();

    /**
     * Returns an iterable containing the elements of {@code a}. The returned
     * iterable's iterator does not support element removal via the
     * {@code remove()} method.
     *
     * @param <M>
     *            The type of elements contained in {@code a}.
     * @param a
     *            The array of elements.
     * @return An iterable containing the elements of {@code a}.
     */
    public static <M> Iterable<M> arrayIterable(final M[] a) {
        return new ArrayIterable<>(a);
    }

    /**
     * Returns an immutable empty iterable.
     *
     * @param <M>
     *            The required type of the empty iterable.
     * @return An immutable empty iterable.
     */
    @SuppressWarnings("unchecked")
    public static <M> Iterable<M> emptyIterable() {
        return (Iterable<M>) EMPTY_ITERABLE;
    }

    /**
     * Returns a filtered view of {@code iterable} containing only those
     * elements which match {@code predicate}. The returned iterable's iterator
     * supports element removal via the {@code remove()} method subject to any
     * constraints imposed by {@code iterable}.
     *
     * @param <M>
     *            The type of elements contained in {@code iterable}.
     * @param <P>
     *            The type of the additional parameter to the predicate's
     *            {@code matches} method. Use {@link java.lang.Void} for
     *            predicates that do not need an additional parameter.
     * @param iterable
     *            The iterable to be filtered.
     * @param predicate
     *            The predicate.
     * @param p
     *            A predicate specified parameter.
     * @return A filtered view of {@code iterable} containing only those
     *         elements which match {@code predicate}.
     */
    public static <M, P> Iterable<M> filteredIterable(final Iterable<M> iterable,
            final Predicate<? super M, P> predicate, final P p) {
        return new FilteredIterable<>(iterable, predicate, p);
    }

    /**
     * Returns a filtered view of {@code iterable} containing only those
     * elements which match {@code predicate}. The returned iterable's iterator
     * supports element removal via the {@code remove()} method subject to any
     * constraints imposed by {@code iterable}.
     *
     * @param <M>
     *            The type of elements contained in {@code iterable}.
     * @param iterable
     *            The iterable to be filtered.
     * @param predicate
     *            The predicate.
     * @return A filtered view of {@code iterable} containing only those
     *         elements which match {@code predicate}.
     */
    public static <M> Iterable<M> filteredIterable(final Iterable<M> iterable,
            final Predicate<? super M, Void> predicate) {
        return new FilteredIterable<>(iterable, predicate, null);
    }

    /**
     * Returns {@code true} if the provided iterable does not contain any
     * elements.
     *
     * @param iterable
     *            The iterable.
     * @return {@code true} if the provided iterable does not contain any
     *         elements.
     */
    public static boolean isEmpty(final Iterable<?> iterable) {
        if (iterable instanceof Collection) {
            // Fall-through if possible and potentially avoid allocation.
            return ((Collection<?>) iterable).isEmpty();
        } else {
            return !iterable.iterator().hasNext();
        }
    }

    /**
     * Returns an iterable containing the single element {@code value}. The
     * returned iterable's iterator does not support element removal via the
     * {@code remove()} method.
     *
     * @param <M>
     *            The type of the single element {@code value}.
     * @param value
     *            The single element.
     * @return An iterable containing the single element {@code value}.
     */
    public static <M> Iterable<M> singletonIterable(final M value) {
        return new SingletonIterable<>(value);
    }

    /**
     * Returns the number of elements contained in the provided iterable.
     *
     * @param iterable
     *            The iterable.
     * @return The number of elements contained in the provided iterable.
     */
    public static int size(final Iterable<?> iterable) {
        if (iterable instanceof Collection) {
            // Fall-through if possible and potentially benefit from constant time calculation.
            return ((Collection<?>) iterable).size();
        } else {
            final Iterator<?> i = iterable.iterator();
            int sz = 0;
            while (i.hasNext()) {
                i.next();
                sz++;
            }
            return sz;
        }
    }

    /**
     * Returns a string representation of the provided iterable composed of an
     * opening square bracket, followed by each element separated by commas, and
     * then a closing square bracket.
     *
     * @param iterable
     *            The iterable whose string representation is to be returned.
     * @return A string representation of the provided iterable.
     * @see java.util.AbstractCollection#toString()
     */
    public static String toString(final Iterable<?> iterable) {
        if (iterable instanceof Collection) {
            // Fall-through if possible.
            return ((Collection<?>) iterable).toString();
        } else {
            final StringBuilder builder = new StringBuilder();
            boolean firstValue = true;
            builder.append('[');
            for (final Object value : iterable) {
                if (!firstValue) {
                    builder.append(", ");
                }
                builder.append(value);
                firstValue = false;
            }
            builder.append(']');
            return builder.toString();
        }
    }

    /**
     * Returns a view of {@code iterable} whose values have been mapped to
     * elements of type {@code N} using {@code function}. The returned
     * iterable's iterator supports element removal via the {@code remove()}
     * method subject to any constraints imposed by {@code iterable}.
     *
     * @param <M>
     *            The type of elements contained in {@code iterable}.
     * @param <N>
     *            The type of elements contained in the returned iterable.
     * @param iterable
     *            The iterable to be transformed.
     * @param function
     *            The function.
     * @return A view of {@code iterable} whose values have been mapped to
     *         elements of type {@code N} using {@code function}.
     */
    public static <M, N> Iterable<N> transformedIterable(final Iterable<M> iterable,
            final Function<? super M, ? extends N, NeverThrowsException> function) {
        return new TransformedIterable<>(iterable, function);
    }

    /**
     * Returns a read-only view of {@code iterable} whose iterator does not
     * support element removal via the {@code remove()}. Attempts to use the
     * {@code remove()} method will result in a
     * {@code UnsupportedOperationException}.
     *
     * @param <M>
     *            The type of elements contained in {@code iterable}.
     * @param iterable
     *            The iterable to be made read-only.
     * @return A read-only view of {@code iterable} whose iterator does not
     *         support element removal via the {@code remove()}.
     */
    public static <M> Iterable<M> unmodifiableIterable(final Iterable<M> iterable) {
        return new UnmodifiableIterable<>(iterable);
    }

    /** Prevent instantiation. */
    private Iterables() {
        // Do nothing.
    }

}
