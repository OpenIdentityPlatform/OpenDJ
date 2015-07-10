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

import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeType;

/** Attribute type property definition. */
public final class AttributeTypePropertyDefinition extends
    PropertyDefinition<AttributeType> {

  /**
   * An interface for incrementally constructing attribute type
   * property definitions.
   */
  public static class Builder extends
      AbstractBuilder<AttributeType, AttributeTypePropertyDefinition> {

    /** Private constructor. */
    private Builder(AbstractManagedObjectDefinition<?, ?> d,
        String propertyName) {
      super(d, propertyName);
    }



    /** {@inheritDoc} */
    @Override
    protected AttributeTypePropertyDefinition buildInstance(
        AbstractManagedObjectDefinition<?, ?> d, String propertyName,
        EnumSet<PropertyOption> options,
        AdministratorAction adminAction,
        DefaultBehaviorProvider<AttributeType> defaultBehavior) {
      return new AttributeTypePropertyDefinition(d, propertyName,
          options, adminAction, defaultBehavior);
    }
  }

  /**
   * Flag indicating whether or not attribute type names should be
   * validated against the schema.
   */
  private static boolean isCheckSchema = true;



  /**
   * Create a attribute type property definition builder.
   *
   * @param d
   *          The managed object definition associated with this
   *          property definition.
   * @param propertyName
   *          The property name.
   * @return Returns the new attribute type property definition
   *         builder.
   */
  public static Builder createBuilder(
      AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
    return new Builder(d, propertyName);
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



  /** Private constructor. */
  private AttributeTypePropertyDefinition(
      AbstractManagedObjectDefinition<?, ?> d, String propertyName,
      EnumSet<PropertyOption> options,
      AdministratorAction adminAction,
      DefaultBehaviorProvider<AttributeType> defaultBehavior) {
    super(d, AttributeType.class, propertyName, options,
        adminAction, defaultBehavior);
  }



  /** {@inheritDoc} */
  @Override
  public <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p) {
    return v.visitAttributeType(this, p);
  }



  /** {@inheritDoc} */
  @Override
  public <R, P> R accept(PropertyValueVisitor<R, P> v,
      AttributeType value, P p) {
    return v.visitAttributeType(this, value, p);
  }



  /** {@inheritDoc} */
  @Override
  public int compare(AttributeType o1, AttributeType o2) {
    return o1.getNameOrOID().compareToIgnoreCase(o2.getNameOrOID());
  }



  /** {@inheritDoc} */
  @Override
  public AttributeType decodeValue(String value)
      throws PropertyException {
    ifNull(value);

    String name = value.trim().toLowerCase();
    AttributeType type = isCheckSchema
        ? DirectoryServer.getAttributeType(name)
        : DirectoryServer.getAttributeTypeOrDefault(name);
    if (type == null) {
      throw PropertyException.illegalPropertyValueException(this, value);
    }
    try {
      validateValue(type);
      return type;
    } catch (PropertyException e) {
      throw PropertyException.illegalPropertyValueException(this, value);
    }
  }



  /** {@inheritDoc} */
  @Override
  public String encodeValue(AttributeType value)
      throws PropertyException {
    return value.getNameOrOID();
  }



  /** {@inheritDoc} */
  @Override
  public void validateValue(AttributeType value)
      throws PropertyException {
    ifNull(value);

    // No implementation required.
  }
}
