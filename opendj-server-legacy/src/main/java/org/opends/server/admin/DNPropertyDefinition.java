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

import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;



/**
 * DN property definition.
 */
public final class DNPropertyDefinition extends PropertyDefinition<DN> {

  /**
   * Optional base DN which all valid values must be immediately
   * subordinate to.
   */
  private final DN baseDN;



  /**
   * An interface for incrementally constructing DN property
   * definitions.
   */
  public static class Builder extends
      AbstractBuilder<DN, DNPropertyDefinition> {

    /**
     * Optional base DN which all valid values must be immediately
     * subordinate to.
     */
    private DN baseDN;



    /** Private constructor. */
    private Builder(
        AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
      super(d, propertyName);
    }



    /**
     * Set the base DN which all valid values must be immediately
     * subordinate to. By default there is no based DN.
     *
     * @param baseDN
     *          The string representation of the base DN.
     * @throws IllegalArgumentException
     *           If the provided string is not a valid DN string
     *           representation.
     */
    public void setBaseDN(String baseDN)
        throws IllegalArgumentException {
      if (baseDN == null) {
        setBaseDN((DN) null);
      } else {
        try {
          setBaseDN(DN.valueOf(baseDN));
        } catch (DirectoryException e) {
          throw new IllegalArgumentException(e);
        }
      }
    }



    /**
     * Set the base DN which all valid values must be immediately
     * subordinate to. By default there is no based DN.
     *
     * @param baseDN
     *          The base DN.
     */
    public void setBaseDN(DN baseDN) {
      this.baseDN = baseDN;
    }



    /** {@inheritDoc} */
    @Override
    protected DNPropertyDefinition buildInstance(
        AbstractManagedObjectDefinition<?, ?> d, String propertyName,
        EnumSet<PropertyOption> options,
        AdministratorAction adminAction,
        DefaultBehaviorProvider<DN> defaultBehavior) {
      return new DNPropertyDefinition(d, propertyName, options,
          adminAction, defaultBehavior, baseDN);
    }
  }



  /**
   * Create a DN property definition builder.
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



  /** Private constructor. */
  private DNPropertyDefinition(
      AbstractManagedObjectDefinition<?, ?> d, String propertyName,
      EnumSet<PropertyOption> options,
      AdministratorAction adminAction,
      DefaultBehaviorProvider<DN> defaultBehavior, DN baseDN) {
    super(d, DN.class, propertyName, options, adminAction, defaultBehavior);
    this.baseDN = baseDN;
  }



  /**
   * Get the base DN which all valid values must be immediately
   * subordinate to, or <code>null</code> if there is no based DN.
   *
   * @return Returns the base DN which all valid values must be
   *         immediately subordinate to.
   */
  public DN getBaseDN() {
    return baseDN;
  }



  /** {@inheritDoc} */
  @Override
  public void validateValue(DN value)
      throws PropertyException {
    ifNull(value);

    if (baseDN != null) {
      DN parent = value.parent();

      if (parent == null) {
        parent = DN.rootDN();
      }

      if (!parent.equals(baseDN)) {
        throw PropertyException.illegalPropertyValueException(this, value);
      }
    }
  }



  /** {@inheritDoc} */
  @Override
  public DN decodeValue(String value)
      throws PropertyException {
    ifNull(value);

    try {
      DN dn = DN.valueOf(value);
      validateValue(dn);
      return dn;
    } catch (DirectoryException | PropertyException e) {
      throw PropertyException.illegalPropertyValueException(this, value);
    }
  }



  /** {@inheritDoc} */
  @Override
  public <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p) {
    return v.visitDN(this, p);
  }



  /** {@inheritDoc} */
  @Override
  public <R, P> R accept(PropertyValueVisitor<R, P> v, DN value, P p) {
    return v.visitDN(this, value, p);
  }



  /** {@inheritDoc} */
  @Override
  public int compare(DN o1, DN o2) {
    return o1.compareTo(o2);
  }
}
