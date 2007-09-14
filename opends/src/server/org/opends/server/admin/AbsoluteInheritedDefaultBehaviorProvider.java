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
 * managed object in an absolute location. It should be used by
 * properties which inherit their default value(s) from properties
 * held in an other managed object.
 *
 * @param <T>
 *          The type of values represented by this provider.
 */
public final class AbsoluteInheritedDefaultBehaviorProvider<T> extends
    DefaultBehaviorProvider<T> {

  // The absolute path to the managed object containing the property.
  private ManagedObjectPath<?, ?> path = null;

  // The string representation of the managed object path specifying
  // the absolute location of the managed object.
  private final String pathString;

  // The name of the property containing the inherited default values.
  private final String propertyName;



  /**
   * Create an absolute inherited default behavior provider associated
   * with the managed object at the specified absolute location.
   *
   * @param pathString
   *          The string representation of the managed object path
   *          specifying the absolute location of the managed object.
   * @param propertyName
   *          The name of the property containing the inherited
   *          default values.
   */
  public AbsoluteInheritedDefaultBehaviorProvider(String pathString,
      String propertyName) {
    this.pathString = pathString;
    this.propertyName = propertyName;
  }



  /**
   * {@inheritDoc}
   */
  public <R, P> R accept(DefaultBehaviorProviderVisitor<T, R, P> v, P p) {
    return v.visitAbsoluteInherited(this, p);
  }



  /**
   * Get the definition of the parent managed object containing the
   * inherited default values.
   *
   * @return Returns the definition of the parent managed object
   *         containing the inherited default values.
   */
  public AbstractManagedObjectDefinition<?, ?> getManagedObjectDefinition() {
    return path.getManagedObjectDefinition();
  }



  /**
   * Get the absolute path of the managed object containing the
   * property which has the default values.
   *
   * @return Returns the absolute path of the managed object
   *         containing the property which has the default values.
   */
  public ManagedObjectPath<?, ?> getManagedObjectPath() {
    return path;
  }



  /**
   * Gets the name of the property containing the inherited default
   * values.
   *
   * @return Returns the name of the property containing the inherited
   *         default values.
   */
  public String getPropertyName() {
    return propertyName;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  protected void initialize() throws Exception {
    // Decode the path.
    path = ManagedObjectPath.valueOf(pathString);
  }

}
