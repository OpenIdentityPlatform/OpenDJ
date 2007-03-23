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

import java.util.EnumSet;

import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeType;



/**
 * Attribute type property definition.
 */
public final class AttributeTypePropertyDefinition extends
    AbstractPropertyDefinition<AttributeType> {

  /**
   * An interface for incrementally constructing attribute type
   * property definitions.
   */
  public static class Builder extends
      AbstractBuilder<AttributeType, AttributeTypePropertyDefinition> {

    // Private constructor
    private Builder(String propertyName) {
      super(propertyName);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    protected AttributeTypePropertyDefinition buildInstance(
        String propertyName, EnumSet<PropertyOption> options,
        DefaultBehaviorProvider<AttributeType> defaultBehavior) {
      return new AttributeTypePropertyDefinition(propertyName,
          options, defaultBehavior);
    }
  }

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = 4622133184170201490L;

  // Flag indicating whether or not attribute type names should be
  // validated against the schema.
  private static boolean isCheckSchema = true;



  /**
   * Create a attribute type property definition builder.
   *
   * @param propertyName
   *          The property name.
   * @return Returns the new attribute type property definition
   *         builder.
   */
  public static Builder createBuilder(String propertyName) {
    return new Builder(propertyName);
  }



  /**
   * Determines whether or not attribute type names should be
   * validated against the schema.
   *
   * @return Returns <code>true</code> if attribute type names
   *         should be validated against the schema.
   */
  public static boolean isCheckSchema() {
    return isCheckSchema;
  }



  /**
   * Specify whether or not attribute type names should be validated
   * against the schema.
   * <p>
   * By default validation is switched on.
   *
   * @param value
   *          <code>true</code> if attribute type names should be
   *          validated against the schema.
   */
  public static void setCheckSchema(boolean value) {
    isCheckSchema = value;
  }



  // Private constructor.
  private AttributeTypePropertyDefinition(String propertyName,
      EnumSet<PropertyOption> options,
      DefaultBehaviorProvider<AttributeType> defaultBehavior) {
    super(AttributeType.class, propertyName, options,
        defaultBehavior);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p) {
    return v.visitAttributeType(this, p);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public int compare(AttributeType o1, AttributeType o2) {
    return o1.getNameOrOID().compareToIgnoreCase(o2.getNameOrOID());
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public AttributeType decodeValue(String value)
      throws IllegalPropertyValueStringException {
    ensureNotNull(value);

    String name = value.trim().toLowerCase();
    AttributeType type = DirectoryServer.getAttributeType(name,
        !isCheckSchema);

    if (type == null) {
      throw new IllegalPropertyValueStringException(this, value);
    } else {
      try {
        validateValue(type);
        return type;
      } catch (IllegalPropertyValueException e) {
        throw new IllegalPropertyValueStringException(this, value);
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void validateValue(AttributeType value)
      throws IllegalPropertyValueException {
    ensureNotNull(value);

    // No implementation required.
  }
}
