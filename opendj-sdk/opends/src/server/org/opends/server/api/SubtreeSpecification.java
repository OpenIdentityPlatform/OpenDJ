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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.api;

import org.opends.server.types.Entry;

/**
 * Generic subtree specification interface.
 */
public abstract class SubtreeSpecification {

  /**
   * Create a new subtree specification.
   */
  protected SubtreeSpecification() {
    // No implementation required.
  }

  /**
   * Determine if an entry is within the scope of the subtree
   * specification.
   *
   * @param entry
   *          The entry.
   * @return Returns <code>true</code> if the entry is within the
   *         scope of the subtree specification, or <code>false</code>
   *         otherwise.
   */
  public abstract boolean isWithinScope(Entry entry);

  /**
   * {@inheritDoc}
   */
  @Override
  public abstract boolean equals(Object obj);

  /**
   * {@inheritDoc}
   */
  @Override
  public abstract int hashCode();

  /**
   * Append the string representation of the subtree specification to
   * the provided string builder.
   *
   * @param builder
   *          The string builder.
   * @return The string builder.
   */
  public abstract StringBuilder toString(StringBuilder builder);

  /**
   * {@inheritDoc}
   */
  @Override
  public final String toString() {
    StringBuilder builder = new StringBuilder();
    return toString(builder).toString();
  }
}
