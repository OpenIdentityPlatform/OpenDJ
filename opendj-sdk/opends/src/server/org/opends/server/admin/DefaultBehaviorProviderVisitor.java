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
 * A visitor of default behavior providers, in the style of the visitor design
 * pattern. Classes implementing this interface can query default behavior
 * providers in a type-safe manner when the kind of default behavior provider
 * is unknown at compile time. When a visitor is passed to a default behavior
 * provider's accept method, the corresponding visit method most applicable to
 * that default behavior provider is invoked.
 *
 * @param <T>
 *          The type of values represented by the default value provider.
 * @param <R>
 *          The return type of this visitor's methods. Use
 *          {@link java.lang.Void} for visitors that do not need to return
 *          results.
 * @param <P>
 *          The type of the additional parameter to this visitor's methods. Use
 *          {@link java.lang.Void} for visitors that do not need an additional
 *          parameter.
 */
public interface DefaultBehaviorProviderVisitor<T, R, P> {

  /**
   * Visit an absolute inherited default behavior provider.
   *
   * @param d
   *          The absolute inherited default behavior provider to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  R visitAbsoluteInherited(AbsoluteInheritedDefaultBehaviorProvider<T> d, P p);



  /**
   * Visit an alias default behavior provider.
   *
   * @param d
   *          The alias default behavior provider to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  R visitAlias(AliasDefaultBehaviorProvider<T> d, P p);



  /**
   * Visit an defined default behavior provider.
   *
   * @param d
   *          The defined default behavior provider to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  R visitDefined(DefinedDefaultBehaviorProvider<T> d, P p);



  /**
   * Visit a relative inherited default behavior provider.
   *
   * @param d
   *          The relative inherited default behavior provider to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  R visitRelativeInherited(RelativeInheritedDefaultBehaviorProvider<T> d, P p);



  /**
   * Visit an undefined default behavior provider.
   *
   * @param d
   *          The undefined default behavior provider to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  R visitUndefined(UndefinedDefaultBehaviorProvider<T> d, P p);

}
