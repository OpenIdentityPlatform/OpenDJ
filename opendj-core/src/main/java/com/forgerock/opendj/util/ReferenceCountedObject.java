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
 *      Copyright 2013 ForgeRock AS.
 */

package com.forgerock.opendj.util;


/**
 * An object which is lazily created when first referenced, and destroyed when
 * the last reference is released.
 *
 * @param <T>
 *            The type of referenced object.
 */
public abstract class ReferenceCountedObject<T> {

    /**
     * A reference to the reference counted object which will automatically be
     * released during garbage collection.
     */
    public final class Reference {
        /**
         * The value will be accessed by the finalizer thread so it needs to be
         * volatile in order to ensure that updates are published.
         */
        private volatile T value;

        private Reference(final T value) {
            this.value = value;
        }

        /**
         * Returns the referenced object.
         *
         * @return The referenced object.
         * @throws NullPointerException
         *             If the referenced object has already been released.
         */
        public T get() {
            if (value == null) {
                throw new NullPointerException(); // Fail-fast.
            }
            return value;
        }

        /**
         * Decrements the reference count for the reference counted object if
         * this reference refers to the reference counted instance. If the
         * reference count drops to zero then the referenced object will be
         * destroyed.
         */
        public void release() {
            T instanceToRelease = null;
            synchronized (lock) {
                if (value != null) {
                    if (instance == value && --refCount == 0) {
                        // This was the last reference.
                        instanceToRelease = value;
                        instance = null;
                    }

                    /*
                     * Force NPE for subsequent get() attempts and prevent
                     * multiple releases.
                     */
                    value = null;
                }
            }
            if (instanceToRelease != null) {
                destroyInstance(instanceToRelease);
            }
        }

        /**
         * Provide a finalizer because reference counting is intended for
         * expensive rarely created resources which should not be accidentally
         * left around.
         */
        @Override
        protected void finalize() {
            release();
        }
    }

    private T instance;
    private final Object lock = new Object();
    private int refCount;

    /**
     * Creates a new referenced object whose reference count is initially zero.
     */
    protected ReferenceCountedObject() {
        // Nothing to do.
    }

    /**
     * Returns a reference to the reference counted object.
     *
     * @return A reference to the reference counted object.
     */
    public final Reference acquire() {
        synchronized (lock) {
            if (refCount++ == 0) {
                assert instance == null;
                instance = newInstance();
            }
            assert instance != null;
            return new Reference(instance);
        }
    }

    /**
     * Returns a reference to the provided object or, if it is {@code null}, a
     * reference to the reference counted object.
     *
     * @param value
     *            The object to be referenced, or {@code null} if the reference
     *            counted object should be used.
     * @return A reference to the provided object or, if it is {@code null}, a
     *         reference to the reference counted object.
     */
    public final Reference acquireIfNull(final T value) {
        return value != null ? new Reference(value) : acquire();
    }

    /**
     * Invoked when a reference is released and the reference count will become
     * zero. Implementations should release any resources associated with the
     * resource and should not return until the resources have been released.
     *
     * @param instance
     *            The instance to be destroyed.
     */
    protected abstract void destroyInstance(T instance);

    /**
     * Invoked when a reference is acquired and the current reference count is
     * zero. Implementations should create a new instance as fast as possible.
     *
     * @return The new instance.
     */
    protected abstract T newInstance();

}
