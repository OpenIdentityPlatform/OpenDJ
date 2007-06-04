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



import static org.opends.server.util.Validator.ensureNotNull;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.EnumSet;



/**
 * IP address property definition.
 */
public final class IPAddressPropertyDefinition extends
    PropertyDefinition<InetAddress> {

  /**
   * An interface for incrementally constructing IP address property
   * definitions.
   */
  public static class Builder extends
      AbstractBuilder<InetAddress, IPAddressPropertyDefinition> {

    // Private constructor
    private Builder(
        AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
      super(d, propertyName);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    protected IPAddressPropertyDefinition buildInstance(
        AbstractManagedObjectDefinition<?, ?> d, String propertyName,
        EnumSet<PropertyOption> options,
        DefaultBehaviorProvider<InetAddress> defaultBehavior) {
      return new IPAddressPropertyDefinition(d, propertyName, options,
          defaultBehavior);
    }

  }



  /**
   * Create a IP address property definition builder.
   *
   * @param d
   *          The managed object definition associated with this
   *          property definition.
   * @param propertyName
   *          The property name.
   * @return Returns the new IP address property definition builder.
   */
  public static Builder createBuilder(
      AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
    return new Builder(d, propertyName);
  }



  // Private constructor.
  private IPAddressPropertyDefinition(
      AbstractManagedObjectDefinition<?, ?> d, String propertyName,
      EnumSet<PropertyOption> options,
      DefaultBehaviorProvider<InetAddress> defaultBehavior) {
    super(d, InetAddress.class, propertyName, options, defaultBehavior);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void validateValue(InetAddress value)
      throws IllegalPropertyValueException {
    ensureNotNull(value);

    // No additional validation required.
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public InetAddress decodeValue(String value)
      throws IllegalPropertyValueStringException {
    ensureNotNull(value);

    try {
      return InetAddress.getByName(value);
    } catch (UnknownHostException e) {
      // TODO: it would be nice to throw the cause.
      throw new IllegalPropertyValueStringException(this, value);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String encodeValue(InetAddress value)
      throws IllegalPropertyValueException {
    return value.getHostName();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p) {
    return v.visitIPAddress(this, p);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public <R, P> R accept(PropertyValueVisitor<R, P> v, InetAddress value, P p) {
    return v.visitIPAddress(this, value, p);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public int compare(InetAddress o1, InetAddress o2) {
    return o1.getHostAddress().compareTo(o2.getHostAddress());
  }
}
