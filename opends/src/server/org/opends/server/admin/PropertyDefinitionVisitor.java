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
 * <p>
 * Each <code>visitXXX</code> method is provided with a default
 * implementation which calls
 * {@link #visitUnknown(PropertyDefinition, Object)}. Sub-classes can
 * override any or all of the methods to provide their own
 * type-specific behavior.
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
public abstract class PropertyDefinitionVisitor<R, P> {

  /**
   * Default constructor.
   */
  protected PropertyDefinitionVisitor() {
    // No implementation required.
  }



  /**
   * Visit a dseecompat Global ACI property definition.
   *
   * @param pd
   *          The Global ACI property definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  public R visitACI(ACIPropertyDefinition pd, P p) {
    return visitUnknown(pd, p);
  }



  /**
   * Visit an aggregation property definition.
   *
   * @param <C>
   *          The type of client managed object configuration that
   *          this aggregation property definition refers to.
   * @param <S>
   *          The type of server managed object configuration that
   *          this aggregation property definition refers to.
   * @param pd
   *          The aggregation property definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  public <C extends ConfigurationClient, S extends Configuration>
  R visitAggregation(AggregationPropertyDefinition<C, S> pd, P p) {
    return visitUnknown(pd, p);
  }



  /**
   * Visit an attribute type property definition.
   *
   * @param pd
   *          The attribute type property definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  public R visitAttributeType(AttributeTypePropertyDefinition pd, P p) {
    return visitUnknown(pd, p);
  }



  /**
   * Visit a boolean property definition.
   *
   * @param pd
   *          The boolean property definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  public R visitBoolean(BooleanPropertyDefinition pd, P p) {
    return visitUnknown(pd, p);
  }



  /**
   * Visit a class property definition.
   *
   * @param pd
   *          The class property definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  public R visitClass(ClassPropertyDefinition pd, P p) {
    return visitUnknown(pd, p);
  }



  /**
   * Visit a DN property definition.
   *
   * @param pd
   *          The DN property definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  public R visitDN(DNPropertyDefinition pd, P p) {
    return visitUnknown(pd, p);
  }



  /**
   * Visit a duration property definition.
   *
   * @param pd
   *          The duration property definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  public R visitDuration(DurationPropertyDefinition pd, P p) {
    return visitUnknown(pd, p);
  }



  /**
   * Visit an enumeration property definition.
   *
   * @param <E>
   *          The enumeration that should be used for values of the
   *          property definition.
   * @param pd
   *          The enumeration property definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  public <E extends Enum<E>> R visitEnum(EnumPropertyDefinition<E> pd, P p) {
    return visitUnknown(pd, p);
  }



  /**
   * Visit an integer property definition.
   *
   * @param pd
   *          The integer property definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  public R visitInteger(IntegerPropertyDefinition pd, P p) {
    return visitUnknown(pd, p);
  }



  /**
   * Visit a IP address property definition.
   *
   * @param pd
   *          The IP address property definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  public R visitIPAddress(IPAddressPropertyDefinition pd, P p) {
    return visitUnknown(pd, p);
  }



  /**
   * Visit a IP address mask property definition.
   *
   * @param pd
   *          The IP address mask property definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  public R visitIPAddressMask(IPAddressMaskPropertyDefinition pd, P p) {
    return visitUnknown(pd, p);
  }


  /**
   * Visit a size property definition.
   *
   * @param pd
   *          The size property definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  public R visitSize(SizePropertyDefinition pd, P p) {
    return visitUnknown(pd, p);
  }



  /**
   * Visit a string property definition.
   *
   * @param pd
   *          The string property definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  public R visitString(StringPropertyDefinition pd, P p) {
    return visitUnknown(pd, p);
  }



  /**
   * Visit an unknown type of property definition. Implementations of
   * this method can provide default behavior for unknown property
   * definition types.
   * <p>
   * The default implementation of this method throws an
   * {@link UnknownPropertyDefinitionException}. Sub-classes can
   * override this method with their own default behavior.
   *
   * @param <T>
   *          The type of the underlying property.
   * @param pd
   *          The property definition to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   * @throws UnknownPropertyDefinitionException
   *           Visitor implementations may optionally throw this
   *           exception.
   */
  public <T> R visitUnknown(PropertyDefinition<T> pd, P p)
      throws UnknownPropertyDefinitionException {
    throw new UnknownPropertyDefinitionException(pd, p);
  }

}
