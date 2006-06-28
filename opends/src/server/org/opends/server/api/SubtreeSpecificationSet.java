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
package org.opends.server.api;

import static org.opends.server.loggers.Debug.debugConstructor;
import static org.opends.server.loggers.Debug.debugEnter;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

import org.opends.server.types.Entry;

/**
 * This class implements the <code>Set</code> interface for
 * {@link org.opends.server.api.SubtreeSpecification}s.
 * <p>
 * It is backed by a <code>HashSet</code> but provides additional
 * functionality, {@link #isWithinScope(Entry)}, for
 * determining whether or not an entry is within the scope of one or
 * more contained <code>SubtreeSpecification</code>s.
 */
public final class SubtreeSpecificationSet extends
    AbstractSet<SubtreeSpecification> {
  // Fully qualified class name for debugging purposes.
  private static final String CLASS_NAME =
       SubtreeSpecificationSet.class.getName();

  // Underlying implementation is simply a set.
  private HashSet<SubtreeSpecification> pimpl;

  /**
   * Constructs a new empty subtree specification set.
   */
  public SubtreeSpecificationSet() {
    assert debugConstructor(CLASS_NAME);

    this.pimpl = new HashSet<SubtreeSpecification>();
  }

  /**
   * Constructs a new subtree specification set containing the
   * elements in the specified collection.
   *
   * @param c
   *          The subtree specification collection whose elements are
   *          to be placed into this set.
   */
  public SubtreeSpecificationSet(
      Collection<? extends SubtreeSpecification> c) {
    assert debugConstructor(CLASS_NAME);

    this.pimpl = new HashSet<SubtreeSpecification>(c);
  }

  /**
   * Returns <code>true</code> if the specified entry is within the
   * scope of a subtree specifications contained in the set.
   *
   * @param entry
   *          The entry to be checked for containment.
   * @return Returns <code>true</code> if the set contains the
   *         specified entry.
   */
  public boolean isWithinScope(Entry entry) {
    assert debugEnter(CLASS_NAME, "contains");

    for (SubtreeSpecification subtreeSpecification : pimpl) {
      if (subtreeSpecification.isWithinScope(entry)) {
        return true;
      }
    }

    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean add(SubtreeSpecification e) {
    assert debugEnter(CLASS_NAME, "add");

    return pimpl.add(e);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Iterator<SubtreeSpecification> iterator() {
    assert debugEnter(CLASS_NAME, "iterator");

    return pimpl.iterator();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean contains(Object o) {
    assert debugEnter(CLASS_NAME, "contains");

    return pimpl.contains(o);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int size() {
    assert debugEnter(CLASS_NAME, "size");

    return pimpl.size();
  }
}
