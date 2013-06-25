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
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.server.admin.client.spi;



import java.util.SortedSet;

import org.opends.server.admin.PropertyDefinition;



/**
 * A managed object property comprising of the property's definition
 * and its set of values.
 * <p>
 * The property stores the values in a sorted set in which values are
 * compared using the comparator defined by the property definition.
 * <p>
 * The property keeps track of whether or not its pending set of
 * values differs from its active values.
 *
 * @param <T>
 *          The type of the property.
 */
public interface Property<T> {

  /**
   * Get an immutable set view of this property's active values.
   *
   * @return Returns an immutable set view of this property's active
   *         values. An empty set indicates that there are no active
   *         values, and any default values are applicable.
   */
  SortedSet<T> getActiveValues();



  /**
   * Get an immutable set view of this property's default values.
   *
   * @return Returns an immutable set view of this property's default
   *         values. An empty set indicates that there are no default
   *         values.
   */
  SortedSet<T> getDefaultValues();



  /**
   * Get an immutable set view of this property's effective values.
   *
   * @return Returns an immutable set view of this property's
   *         effective values.
   */
  SortedSet<T> getEffectiveValues();



  /**
   * Get an immutable set view of this property's pending values.
   * <p>
   * Immediately after construction, the pending values matches the
   * active values.
   *
   * @return Returns an immutable set view of this property's pending
   *         values. An empty set indicates that there are no pending
   *         values, and any default values are applicable.
   */
  SortedSet<T> getPendingValues();



  /**
   * Get the property definition associated with this property.
   *
   * @return Returns the property definition associated with this
   *         property.
   */
  PropertyDefinition<T> getPropertyDefinition();



  /**
   * Determines whether or not this property contains any pending
   * values.
   *
   * @return Returns <code>true</code> if this property does not
   *         contain any pending values.
   */
  boolean isEmpty();



  /**
   * Determines whether or not this property has been modified since
   * it was constructed. In other words, whether or not the set of
   * pending values differs from the set of active values.
   *
   * @return Returns <code>true</code> if this property has been
   *         modified since it was constructed.
   */
  boolean isModified();



  /**
   * Determines whether or not this property contains any active
   * values.
   *
   * @return Returns <code>true</code> if this property does not
   *         contain any active values.
   */
  boolean wasEmpty();
}
