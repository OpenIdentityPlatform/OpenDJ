/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */

package org.opends.server.admin;



import static org.forgerock.util.Reject.ifNull;

import java.util.EnumSet;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.AddressMask;



/**
 * IP address mask property definition.
 */
public final class IPAddressMaskPropertyDefinition extends
    PropertyDefinition<AddressMask> {

  /**
   * An interface for incrementally constructing IP address mask property
   * definitions.
   */
  public static class Builder extends
      AbstractBuilder<AddressMask, IPAddressMaskPropertyDefinition> {

    /** Private constructor. */
    private Builder(
        AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
      super(d, propertyName);
    }



    /** {@inheritDoc} */
    @Override
    protected IPAddressMaskPropertyDefinition buildInstance(
        AbstractManagedObjectDefinition<?, ?> d,
        String propertyName, EnumSet<PropertyOption> options,
        AdministratorAction adminAction,
        DefaultBehaviorProvider<AddressMask> defaultBehavior) {
      return new IPAddressMaskPropertyDefinition(d, propertyName, options,
          adminAction, defaultBehavior);
    }

  }



  /**
   * Create a IP address mask property definition builder.
   *
   * @param d
   *          The managed object definition associated with this
   *          property definition.
   * @param propertyName
   *          The property name.
   * @return Returns the new IP address mask property definition builder.
   */
  public static Builder createBuilder(
      AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
    return new Builder(d, propertyName);
  }



  /** Private constructor. */
  private IPAddressMaskPropertyDefinition(
      AbstractManagedObjectDefinition<?, ?> d, String propertyName,
      EnumSet<PropertyOption> options,
      AdministratorAction adminAction,
      DefaultBehaviorProvider<AddressMask> defaultBehavior) {
    super(d, AddressMask.class, propertyName, options, adminAction,
        defaultBehavior);
  }



  /** {@inheritDoc} */
  @Override
  public void validateValue(AddressMask value)
      throws PropertyException {
    ifNull(value);

    // No additional validation required.
  }



  /** {@inheritDoc} */
  @Override
  public AddressMask decodeValue(String value)
      throws PropertyException {
    ifNull(value);

    try {
      return AddressMask.valueOf(value);
    } catch (LocalizedIllegalArgumentException e) {
      // TODO: it would be nice to throw the cause.
      throw PropertyException.illegalPropertyValueException(this, value);
    }
  }



  /** {@inheritDoc} */
  @Override
  public <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p) {
    return v.visitIPAddressMask(this, p);
  }



  /** {@inheritDoc} */
  @Override
  public <R, P> R accept(PropertyValueVisitor<R, P> v, AddressMask value, P p) {
    return v.visitIPAddressMask(this, value, p);
  }



  /** {@inheritDoc} */
  @Override
  public int compare(AddressMask o1, AddressMask o2) {
    return o1.toString().compareTo(o2.toString());
  }
}
