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
package org.opends.server.admin;



/**
 * An interface for determining the default behavior of a property. A
 * property exhibits default behavior when it has no values defined.
 * There are four different types of default behavior:
 * <ol>
 * <li>there is no default behavior - e.g. leaving a "description"
 * unset has no side-effects. This default behavior is represented
 * using the {@link UndefinedDefaultBehaviorProvider} implementation
 * <li>the property defaults to one or more real values of the
 * property. This default behavior is represented using the
 * {@link DefinedDefaultBehaviorProvider} implementation
 * <li>the property defaults to some special behavior that cannot be
 * represented using real property values. This default behavior is
 * represented using the {@link AliasDefaultBehaviorProvider}
 * implementation
 * <li>the property inherits its values from property held in another
 * managed object (e.g. the parent managed object). This default
 * behavior is represented using the
 * {@link AbsoluteInheritedDefaultBehaviorProvider} and
 * {@link RelativeInheritedDefaultBehaviorProvider} implementations.
 * </ol>
 * An application can perform actions based on the type of the default
 * behavior by implementing the {@link DefaultBehaviorProviderVisitor}
 * interface.
 *
 * @param <T>
 *          The type of values represented by this provider.
 */
public abstract class DefaultBehaviorProvider<T> {

  /**
   * Creates a new default behavior provider.
   */
  protected DefaultBehaviorProvider() {
    // No implementation required.
  }



  /**
   * Apply a visitor to this default behavior provider.
   *
   * @param <R>
   *          The return type of the visitor's methods.
   * @param <P>
   *          The type of the additional parameters to the visitor's
   *          methods.
   * @param v
   *          The default behavior visitor.
   * @param p
   *          Optional additional visitor parameter.
   * @return Returns a result as specified by the visitor.
   */
  public abstract <R, P>
  R accept(DefaultBehaviorProviderVisitor<T, R, P> v, P p);



  /**
   * Performs any run-time initialization required by this default
   * behavior provider. This may include resolving managed object
   * paths and property names.
   * <p>
   * The default implementation is to do nothing.
   *
   * @throws Exception
   *           If this default behavior provider could not be
   *           initialized.
   */
  protected void initialize() throws Exception {
    // Default implementation is to do nothing.
  }

}
