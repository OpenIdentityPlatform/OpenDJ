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
 * A visitor of relation definitions, in the style of the visitor
 * design pattern. Classes implementing this interface can query
 * relation definitions in a type-safe manner when the kind of
 * relation definition is unknown at compile time. When a visitor is
 * passed to a relation definition's accept method, the corresponding
 * visit method most applicable to that relation definition is
 * invoked.
 *
 * @param <R>
 *          The return type of this visitor's methods. Use
 *          {@link java.lang.Void} for visitors that do not need to
 *          return results.
 * @param <P>
 *          The type of the additional parameter to this visitor's
 *          methods. Use {@link java.lang.Void} for visitors that do
 *          not need an additional parameter.
 */
public interface RelationDefinitionVisitor<R, P> {

  /**
   * Visit an instantiable relation definition.
   *
   * @param rd
   *          The instantiable relation definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  R visitInstantiable(InstantiableRelationDefinition<?, ?> rd, P p);



  /**
   * Visit an optional relation definition.
   *
   * @param rd
   *          The optional relation definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  R visitOptional(OptionalRelationDefinition<?, ?> rd, P p);



  /**
   * Visit a singleton relation definition.
   *
   * @param rd
   *          The singleton relation definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  R visitSingleton(SingletonRelationDefinition<?, ?> rd, P p);

}
