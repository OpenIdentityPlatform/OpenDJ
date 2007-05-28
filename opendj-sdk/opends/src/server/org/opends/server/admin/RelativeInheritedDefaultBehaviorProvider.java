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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.admin;



/**
 * A default behavior provider which retrieves default values from a
 * parent managed object. It should be used by properties which
 * inherit their default value(s) from properties held in an other
 * managed object.
 *
 * @param <T>
 *          The type of values represented by this provider.
 */
public final class RelativeInheritedDefaultBehaviorProvider<T> implements
    DefaultBehaviorProvider<T> {

  // The type of managed object expected at the relative offset.
  private final AbstractManagedObjectDefinition<?, ?> d;

  // The relative offset (where 1 = parent, 2 = grandparent) of the
  // managed object containing the property.
  private final int offset;

  // The name of the property containing the inherited default values.
  private final String propertyName;



  /**
   * Create a relative inherited default behavior provider associated
   * with a parent managed object.
   *
   * @param d
   *          The type of parent managed object expected at the
   *          relative location.
   * @param propertyName
   *          The name of the property containing the inherited
   *          default values.
   * @param offset
   *          The relative location of the parent managed object
   *          (where 0 is the managed object itself, 1 is the parent,
   *          and 2 is the grand-parent).
   * @throws IllegalArgumentException
   *           If the offset is less than 0.
   */
  @SuppressWarnings("unchecked")
  public RelativeInheritedDefaultBehaviorProvider(
      AbstractManagedObjectDefinition<?, ?> d, String propertyName, int offset)
      throws IllegalArgumentException {
    // We do not decode the property name now because the property
    // might not have been constructed at this point (e.g. when the
    // offset is 0).
    if (offset < 0) {
      throw new IllegalArgumentException("Negative offset");
    }
    this.d = d;
    this.propertyName = propertyName;
    this.offset = offset;
  }



  /**
   * {@inheritDoc}
   */
  public <R, P> R accept(DefaultBehaviorProviderVisitor<T, R, P> v, P p) {
    return v.visitRelativeInherited(this, p);
  }



  /**
   * Get the definition of the parent managed object containing the
   * inherited default values.
   *
   * @return Returns the definition of the parent managed object
   *         containing the inherited default values.
   */
  public AbstractManagedObjectDefinition<?, ?> getManagedObjectDefinition() {
    return d;
  }



  /**
   * Get the absolute path of the managed object containing the
   * property which has the default values.
   *
   * @param path
   *          The path of the current managed object from which the
   *          relative path should be determined.
   * @return Returns the absolute path of the managed object
   *         containing the property which has the default values.
   */
  public ManagedObjectPath getManagedObjectPath(ManagedObjectPath path) {
    return path.parent(offset);
  }



  /**
   * Get the name of the property containing the inherited default
   * values.
   *
   * @return Returns the name of the property containing the inherited
   *         default values.
   */
  public String getPropertyName() {
    return propertyName;
  }



  /**
   * Get the relative location of the parent managed object.
   *
   * @return Returns the relative location of the parent managed
   *         object (where 0 is the managed object itself, 1 is the
   *         parent, and 2 is the grand-parent).
   */
  public int getRelativeOffset() {
    return offset;
  }

}
