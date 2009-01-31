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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.server.admin;

import java.util.EnumSet;
import org.opends.server.api.ExtensibleMatchingRule;

import org.opends.server.core.DirectoryServer;
import static org.opends.server.util.Validator.ensureNotNull;

/**
 * Extensible Matching Rule Type Propertiy Definition.
 */
public final class ExtensibleMatchingRuleTypePropertyDefinition
        extends PropertyDefinition<ExtensibleMatchingRule>
{

  /**
   * An interface for incrementally constructing attribute type
   * property definitions.
   */
  public static class Builder extends
      AbstractBuilder<ExtensibleMatchingRule,
                                ExtensibleMatchingRuleTypePropertyDefinition> {

    // Private constructor
    private Builder(AbstractManagedObjectDefinition<?, ?> d,
        String propertyName) {
      super(d, propertyName);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    protected ExtensibleMatchingRuleTypePropertyDefinition buildInstance(
        AbstractManagedObjectDefinition<?, ?> d, String propertyName,
        EnumSet<PropertyOption> options,
        AdministratorAction adminAction,
        DefaultBehaviorProvider<ExtensibleMatchingRule> defaultBehavior) {
      return new ExtensibleMatchingRuleTypePropertyDefinition(d, propertyName,
          options, adminAction, defaultBehavior);
    }
  }


  /**
   * Create an extensible matching rule type property definition builder.
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
   * Creates a new insantce of this class.
   */
  private ExtensibleMatchingRuleTypePropertyDefinition(
      AbstractManagedObjectDefinition<?, ?> d, String propertyName,
      EnumSet<PropertyOption> options,
      AdministratorAction adminAction,
      DefaultBehaviorProvider<ExtensibleMatchingRule> defaultBehavior) {
    super(d, ExtensibleMatchingRule.class, propertyName, options,
        adminAction, defaultBehavior);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p) {
    return v.visitExtensibleMatchingRuleType(this, p);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public <R, P> R accept(PropertyValueVisitor<R, P> v,
      ExtensibleMatchingRule value, P p) {
    return v.visitExtensibleMatchingRuleType(this, value, p);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public int compare(ExtensibleMatchingRule o1, ExtensibleMatchingRule o2) {
    return o1.getOID().compareToIgnoreCase(o2.getOID());
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public ExtensibleMatchingRule decodeValue(String value)
      throws IllegalPropertyValueStringException {
    ensureNotNull(value);

    String name = value.trim().toLowerCase();
    //Check if the name is a valid Matching rule OID or a Locale value.
    ExtensibleMatchingRule rule =
            DirectoryServer.getExtensibleMatchingRule(name);

    if (rule == null) {
      throw new IllegalPropertyValueStringException(this, value);
    } else {
      try {
        validateValue(rule);
        return rule;
      } catch (IllegalPropertyValueException e) {
        throw new IllegalPropertyValueStringException(this, value);
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String encodeValue(ExtensibleMatchingRule value)
      throws IllegalPropertyValueException {
    return value.getNameOrOID();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void validateValue(ExtensibleMatchingRule value)
      throws IllegalPropertyValueException {
    ensureNotNull(value);

    // No implementation required.
  }
}