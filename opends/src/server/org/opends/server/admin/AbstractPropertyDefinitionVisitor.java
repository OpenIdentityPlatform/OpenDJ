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
 * A skeletal implementation of a property definition visitor. Each
 * <code>visitXXX</code> method is provided with a default
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
public abstract class AbstractPropertyDefinitionVisitor<R, P>
    implements PropertyDefinitionVisitor<R, P> {

  /**
   * Default constructor.
   */
  protected AbstractPropertyDefinitionVisitor() {
    // No implementation required.
  }



  /**
   * {@inheritDoc}
   */
  public R visitAttributeType(AttributeTypePropertyDefinition d, P p) {
    return visitUnknown(d, p);
  }



  /**
   * {@inheritDoc}
   */
  public R visitBoolean(BooleanPropertyDefinition d, P p) {
    return visitUnknown(d, p);
  }



  /**
   * {@inheritDoc}
   */
  public R visitClass(ClassPropertyDefinition d, P p) {
    return visitUnknown(d, p);
  }



  /**
   * {@inheritDoc}
   */
  public R visitDN(DNPropertyDefinition d, P p) {
    return visitUnknown(d, p);
  }



  /**
   * {@inheritDoc}
   */
  public R visitDuration(DurationPropertyDefinition d, P p) {
    return visitUnknown(d, p);
  }



  /**
   * {@inheritDoc}
   */
  public R visitInteger(IntegerPropertyDefinition d, P p) {
    return visitUnknown(d, p);
  }



  /**
   * {@inheritDoc}
   */
  public R visitIPAddress(IPAddressPropertyDefinition d, P p) {
    return visitUnknown(d, p);
  }



  /**
   * {@inheritDoc}
   */
  public R visitIPAddressMask(IPAddressMaskPropertyDefinition d, P p) {
    return visitUnknown(d, p);
  }



  /**
   * {@inheritDoc}
   */
  public R visitSize(SizePropertyDefinition d, P p) {
    return visitUnknown(d, p);
  }



  /**
   * {@inheritDoc}
   */
  public R visitString(StringPropertyDefinition d, P p) {
    return visitUnknown(d, p);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation of this method is throw an
   * {@link UnknownPropertyDefinitionException}. Sub-classes can
   * override this method with their own default behavior.
   */
  public R visitUnknown(PropertyDefinition d, P p)
      throws UnknownPropertyDefinitionException {
    throw new UnknownPropertyDefinitionException(d, p);
  }



  /**
   * {@inheritDoc}
   */
  public R visitEnum(EnumPropertyDefinition<?> d, P p) {
    return visitUnknown(d, p);
  }

}
