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
 * A visitor of property definitions, in the style of the visitor
 * design pattern. Classes implementing this interface can query
 * property definitions in a type-safe manner when the kind of
 * property definition is unknown at compile time. When a visitor is
 * passed to a property definition's accept method, the corresponding
 * visit method most applicable to that property definition is
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
public interface PropertyDefinitionVisitor<R, P> {

  /**
   * Visit an attribute type property definition.
   *
   * @param d
   *          The attribute type property definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  R visitAttributeType(AttributeTypePropertyDefinition d, P p);



  /**
   * Visit a boolean property definition.
   *
   * @param d
   *          The boolean property definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  R visitBoolean(BooleanPropertyDefinition d, P p);



  /**
   * Visit a class property definition.
   *
   * @param d
   *          The class property definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  R visitClass(ClassPropertyDefinition d, P p);



  /**
   * Visit a DN property definition.
   *
   * @param d
   *          The DN property definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  R visitDN(DNPropertyDefinition d, P p);



  /**
   * Visit a duration property definition.
   *
   * @param d
   *          The duration property definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  R visitDuration(DurationPropertyDefinition d, P p);



  /**
   * Visit an integer property definition.
   *
   * @param d
   *          The integer property definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  R visitInteger(IntegerPropertyDefinition d, P p);



  /**
   * Visit a IP address property definition.
   *
   * @param d
   *          The IP address property definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  R visitIPAddress(IPAddressPropertyDefinition d, P p);



  /**
   * Visit a IP address mask property definition.
   *
   * @param d
   *          The IP address mask property definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  R visitIPAddressMask(IPAddressMaskPropertyDefinition d, P p);



  /**
   * Visit a size property definition.
   *
   * @param d
   *          The size property definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  R visitSize(SizePropertyDefinition d, P p);



  /**
   * Visit a string property definition.
   *
   * @param d
   *          The string property definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  R visitString(StringPropertyDefinition d, P p);



  /**
   * Visit an unknown type of property definition. Implementations of
   * this method can provide default behavior for unknown property
   * definition types.
   *
   * @param d
   *          The property definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   * @throws UnknownPropertyDefinitionException
   *           Visitor implementations may optionally throw this
   *           exception.
   */
  R visitUnknown(PropertyDefinition d, P p)
      throws UnknownPropertyDefinitionException;



  /**
   * Visit an enumeration property definition.
   *
   * @param d
   *          The enumeration property definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  R visitEnum(EnumPropertyDefinition<?> d, P p);

}
