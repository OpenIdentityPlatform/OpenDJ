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



import java.net.InetAddress;

import org.opends.server.types.AddressMask;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.authorization.dseecompat.Aci;


/**
 * A visitor of property values, in the style of the visitor design
 * pattern. Classes implementing this interface can query a property a
 * value and its associated property definition in a type-safe manner
 * when the kind of property value is unknown at compile time. When a
 * visitor is passed to a property definition's accept method, the
 * corresponding visit method most applicable to that property
 * definition is invoked.
 * <p>
 * Each <code>visitXXX</code> method is provided with a default
 * implementation which calls
 * {@link #visitUnknown(PropertyDefinition, Object, Object)}.
 * Sub-classes can override any or all of the methods to provide their
 * own type-specific behavior.
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
public abstract class PropertyValueVisitor<R, P> {

  /**
   * Default constructor.
   */
  protected PropertyValueVisitor() {
    // No implementation required.
  }



  /**
   * Visit an aggregation property value.
   *
   * @param <C>
   *          The type of client managed object configuration that
   *          this aggregation property definition refers to.
   * @param <S>
   *          The type of server managed object configuration that
   *          this aggregation property definition refers to.
   * @param d
   *          The aggregation property definition to visit.
   * @param v
   *          The property value to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  public <C extends ConfigurationClient, S extends Configuration>
  R visitAggregation(
      AggregationPropertyDefinition<C, S> d, String v, P p) {
    return visitUnknown(d, v, p);
  }



  /**
   * Visit an attribute type.
   *
   * @param d
   *          The attribute type property definition.
   * @param v
   *          The property value to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  public R visitAttributeType(AttributeTypePropertyDefinition d,
      AttributeType v, P p) {
    return visitUnknown(d, v, p);
  }



  /**
   * Visit a boolean.
   *
   * @param d
   *          The boolean property definition.
   * @param v
   *          The property value to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  public R visitBoolean(BooleanPropertyDefinition d, Boolean v, P p) {
    return visitUnknown(d, v, p);
  }



  /**
   * Visit a class.
   *
   * @param d
   *          The class property definition.
   * @param v
   *          The property value to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  public R visitClass(ClassPropertyDefinition d, String v, P p) {
    return visitUnknown(d, v, p);
  }



  /**
   * Visit a DN.
   *
   * @param d
   *          The DN property definition.
   * @param v
   *          The property value to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  public R visitDN(DNPropertyDefinition d, DN v, P p) {
    return visitUnknown(d, v, p);
  }



  /**
   * Visit a duration.
   *
   * @param d
   *          The duration property definition.
   * @param v
   *          The property value to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  public R visitDuration(DurationPropertyDefinition d, Long v, P p) {
    return visitUnknown(d, v, p);
  }



  /**
   * Visit an enumeration.
   *
   * @param <E>
   *          The enumeration that should be used for values of the
   *          property definition.
   * @param v
   *          The property value to visit.
   * @param d
   *          The enumeration property definition.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  public <E extends Enum<E>>
  R visitEnum(EnumPropertyDefinition<E> d, E v, P p) {
    return visitUnknown(d, v, p);
  }



  /**
   * Visit an integer.
   *
   * @param d
   *          The integer property definition.
   * @param v
   *          The property value to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  public R visitInteger(IntegerPropertyDefinition d, Integer v, P p) {
    return visitUnknown(d, v, p);
  }



  /**
   * Visit a IP address.
   *
   * @param d
   *          The IP address property definition.
   * @param v
   *          The property value to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  public R visitIPAddress(IPAddressPropertyDefinition d, InetAddress v, P p) {
    return visitUnknown(d, v, p);
  }



  /**
   * Visit a IP address mask.
   *
   * @param d
   *          The IP address mask property definition.
   * @param v
   *          The property value to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  public R visitIPAddressMask(IPAddressMaskPropertyDefinition d, AddressMask v,
      P p) {
    return visitUnknown(d, v, p);
  }


  /**
   * Visit a dseecompat ACI.
   *
   * @param d
   *          The dseecompat ACI property definition.
   * @param v
   *          The property value to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  public R visitACI(ACIPropertyDefinition d, Aci v,
      P p) {
    return visitUnknown(d, v, p);
  }



  /**
   * Visit a size.
   *
   * @param d
   *          The size property definition.
   * @param v
   *          The property value to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  public R visitSize(SizePropertyDefinition d, Long v, P p) {
    return visitUnknown(d, v, p);
  }



  /**
   * Visit a string.
   *
   * @param d
   *          The string property definition.
   * @param v
   *          The property value to visit.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   */
  public R visitString(StringPropertyDefinition d, String v, P p) {
    return visitUnknown(d, v, p);
  }



  /**
   * Visit an unknown type of property value. Implementations of this
   * method can provide default behavior for unknown types of
   * property.
   * <p>
   * The default implementation of this method throws an
   * {@link UnknownPropertyDefinitionException}. Sub-classes can
   * override this method with their own default behavior.
   *
   * @param <T>
   *          The type of property value to visit.
   * @param d
   *          The property definition.
   * @param v
   *          The property value.
   * @param p
   *          A visitor specified parameter.
   * @return Returns a visitor specified result.
   * @throws UnknownPropertyDefinitionException
   *           Visitor implementations may optionally throw this
   *           exception.
   */
  public <T> R visitUnknown(PropertyDefinition<T> d, T v, P p)
      throws UnknownPropertyDefinitionException {
    throw new UnknownPropertyDefinitionException(d, p);
  }

}
