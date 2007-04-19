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
import java.util.HashMap;
import java.util.Map;



/**
 * Boolean property definition.
 */
public final class BooleanPropertyDefinition extends
    AbstractPropertyDefinition<Boolean> {

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = -3615113733243221668L;

  /**
   * Mapping used for parsing boolean values. This mapping is more flexible than
   * the standard boolean string parser and supports common true/false synonyms
   * used in configuration.
   */
  private static final Map<String, Boolean> VALUE_MAP;
  static {
    VALUE_MAP = new HashMap<String, Boolean>();

    VALUE_MAP.put("0", Boolean.FALSE);
    VALUE_MAP.put("no", Boolean.FALSE);
    VALUE_MAP.put("off", Boolean.FALSE);
    VALUE_MAP.put("false", Boolean.FALSE);
    VALUE_MAP.put("disable", Boolean.FALSE);
    VALUE_MAP.put("disabled", Boolean.FALSE);

    VALUE_MAP.put("1", Boolean.TRUE);
    VALUE_MAP.put("yes", Boolean.TRUE);
    VALUE_MAP.put("on", Boolean.TRUE);
    VALUE_MAP.put("true", Boolean.TRUE);
    VALUE_MAP.put("enable", Boolean.TRUE);
    VALUE_MAP.put("enabled", Boolean.TRUE);
  }



  /**
   * An interface for incrementally constructing boolean property definitions.
   */
  public static class Builder extends
      AbstractBuilder<Boolean, BooleanPropertyDefinition> {

    // Private constructor
    private Builder(
        AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
      super(d, propertyName);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    protected BooleanPropertyDefinition buildInstance(
        AbstractManagedObjectDefinition<?, ?> d, String propertyName,
        EnumSet<PropertyOption> options,
        DefaultBehaviorProvider<Boolean> defaultBehavior) {
      return new BooleanPropertyDefinition(d, propertyName, options,
          defaultBehavior);
    }

  }



  /**
   * Create a boolean property definition builder.
   *
   * @param d
   *          The managed object definition associated with this
   *          property definition.
   * @param propertyName
   *          The property name.
   * @return Returns the new boolean property definition builder.
   */
  public static Builder createBuilder(
      AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
    return new Builder(d, propertyName);
  }



  // Private constructor.
  private BooleanPropertyDefinition(
      AbstractManagedObjectDefinition<?, ?> d, String propertyName,
      EnumSet<PropertyOption> options,
      DefaultBehaviorProvider<Boolean> defaultBehavior) {
    super(d, Boolean.class, propertyName, options, defaultBehavior);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void validateValue(Boolean value)
      throws IllegalPropertyValueException {
    ensureNotNull(value);

    // No additional validation required.
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Boolean decodeValue(String value)
      throws IllegalPropertyValueStringException {
    ensureNotNull(value);

    String nvalue = value.trim().toLowerCase();
    Boolean b = VALUE_MAP.get(nvalue);

    if (b == null) {
      throw new IllegalPropertyValueStringException(this, value);
    } else {
      return b;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p) {
    return v.visitBoolean(this, p);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public int compare(Boolean o1, Boolean o2) {
    return o1.compareTo(o2);
  }
}
