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
 * A default behavior provider which retrieves default values from a managed
 * object in an abolute location. It should be used by properties which inherit
 * their default value(s) from properties held in an other managed object.
 *
 * @param <T>
 *          The type of values represented by this provider.
 */
public final class AbsoluteInheritedDefaultBehaviorProvider<T> implements
    DefaultBehaviorProvider<T> {

  // The absolute path to the managed object containing the property.
  private final ManagedObjectPath path;

  // The name of the property containing the inherited default values.
  private final String propertyName;



  /**
   * Create an absolute inherited default behavior provider associated with the
   * managed object at the specified absolute location.
   *
   * @param path
   *          The absolute location of the managed object.
   * @param propertyName
   *          The name of the property containing the inherited default values.
   */
  public AbsoluteInheritedDefaultBehaviorProvider(ManagedObjectPath path,
      String propertyName) {
    this.path = path;
    this.propertyName = propertyName;
  }



  /**
   * {@inheritDoc}
   */
  public <R, P> R accept(DefaultBehaviorProviderVisitor<T, R, P> v, P p) {
    return v.visitAbsoluteInherited(this, p);
  }



  /**
   * Get the absolute path of the managed object containing the property which
   * has the default values.
   *
   * @return Returns the absolute path of the managed object containing the
   *         property which has the default values.
   */
  public ManagedObjectPath getManagedObjectPath() {
    return path;
  }



  /**
   * Get the name of the property containing the inherited default values.
   *
   * @return Returns the name of the property containing the inherited default
   *         values.
   */
  public String getPropertyName() {
    return propertyName;
  }

}
