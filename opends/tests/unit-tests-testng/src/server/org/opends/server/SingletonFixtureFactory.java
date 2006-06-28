/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */

package org.opends.server;

/**
 * A fixture factory that guarantees that at most only one instance of a
 * fixture is instantiated. The singleton factory uses reference
 * counting to guarantee that the fixture instance is torn down at the
 * correct time.
 *
 * @param <T> The type of fixture managed by this factory.
 */
public final class SingletonFixtureFactory<T> implements FixtureFactory<T> {
  // The underlying fixture factory.
  private FixtureFactory<T> pimpl;

  // The underlying fixture instance.
  private T instance;

  // Reference count used to determine when tearDown can be performed.
  private int refCount;

  /**
   * Create a new singleton fixture factory.
   *
   * @param pimpl
   *          The underlying fixture factory.
   */
  public SingletonFixtureFactory(FixtureFactory<T> pimpl) {
    this.pimpl = pimpl;
    this.instance = null;
    this.refCount = 0;
  }

  /**
   * {@inheritDoc}
   */
  public T setUp() throws Exception {
    if (refCount == 0) {
      instance = pimpl.setUp();
    }

    refCount++;
    return instance;
  }

  /**
   * {@inheritDoc}
   */
  public void tearDown() throws Exception {
    if (refCount <= 0) {
      throw new IllegalStateException("SingletonFixtureFactory tearDown "
          + "called more often than setUp");
    }

    refCount--;

    if (refCount == 0) {
      pimpl.tearDown();
      instance = null;
    }
  }
}
