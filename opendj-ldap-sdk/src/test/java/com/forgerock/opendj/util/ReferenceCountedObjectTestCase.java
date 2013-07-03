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
 *      Portions copyright 2013 ForgeRock AS.
 */

package com.forgerock.opendj.util;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.testng.annotations.Test;

/**
 * This Test Class tests {@link ReferenceCountedObject}.
 */
@SuppressWarnings("javadoc")
public class ReferenceCountedObjectTestCase extends UtilTestCase {

    private interface Impl {
        void destroyInstance(Object instance);

        Object newInstance();
    }

    private final Object object = "Test Object";

    @Test
    public void testAcquire() throws Exception {
        final Impl impl = mock(Impl.class);
        when(impl.newInstance()).thenReturn(object);
        final ReferenceCountedObject<Object> rco = rco(impl);

        // First acquisition should create new instance.
        final ReferenceCountedObject<Object>.Reference ref1 = rco.acquire();
        assertThat(ref1.get()).isSameAs(object);
        verify(impl).newInstance();
        verifyNoMoreInteractions(impl);

        // Second acquisition should just bump the ref count.
        final ReferenceCountedObject<Object>.Reference ref2 = rco.acquire();
        assertThat(ref2.get()).isSameAs(object);
        verifyNoMoreInteractions(impl);

        // First dereference should just decrease the ref count.
        ref1.release();
        verifyNoMoreInteractions(impl);

        // Second dereference should destroy the instance.
        ref2.release();
        verify(impl).destroyInstance(object);
        verifyNoMoreInteractions(impl);
    }

    @Test
    public void testAcquireIfNull() throws Exception {
        final Object otherObject = "Other object";
        final Impl impl = mock(Impl.class);
        when(impl.newInstance()).thenReturn(object);
        final ReferenceCountedObject<Object> rco = rco(impl);
        final ReferenceCountedObject<Object>.Reference ref = rco.acquireIfNull(otherObject);

        verify(impl, never()).newInstance();
        assertThat(ref.get()).isSameAs(otherObject);
        ref.release();
        verifyNoMoreInteractions(impl);
    }

    /**
     * This test attempts to test that finalization works.
     */
    @Test
    public void testFinalization() {
        final Impl impl = mock(Impl.class);
        when(impl.newInstance()).thenReturn(object);
        final ReferenceCountedObject<Object> rco = rco(impl);
        final ReferenceCountedObject<Object>.Reference ref = rco.acquire();
        verify(impl, never()).destroyInstance(object);
        ref.finalize(); // Simulate GC.
        verify(impl).destroyInstance(object);
    }

    /**
     * Test for issue OPENDJ-1049.
     */
    @Test
    public void testReleaseOnceOnly() {
        final Impl impl = mock(Impl.class);
        when(impl.newInstance()).thenReturn(object);
        final ReferenceCountedObject<Object> rco = rco(impl);

        // Create two references.
        final ReferenceCountedObject<Object>.Reference ref1 = rco.acquire();
        final ReferenceCountedObject<Object>.Reference ref2 = rco.acquire();

        /*
         * Now release first reference multiple times and make sure that the
         * resource is not destroyed.
         */
        ref1.release();
        ref1.release();  // Redundant release.
        ref1.finalize(); // Simulate GC.
        verify(impl, never()).destroyInstance(object);

        /*
         * Now release second reference which should cause the resource to be
         * destroyed.
         */
        ref2.release();
        verify(impl).destroyInstance(object);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testStaleReference() throws Exception {
        final Impl impl = mock(Impl.class);
        when(impl.newInstance()).thenReturn(object);
        final ReferenceCountedObject<Object> rco = rco(impl);
        final ReferenceCountedObject<Object>.Reference ref = rco.acquire();
        ref.release();
        ref.get();
    }

    private ReferenceCountedObject<Object> rco(final Impl impl) {
        return new ReferenceCountedObject<Object>() {

            @Override
            protected void destroyInstance(final Object instance) {
                impl.destroyInstance(instance);
            }

            @Override
            protected Object newInstance() {
                return impl.newInstance();
            }
        };
    }
}
