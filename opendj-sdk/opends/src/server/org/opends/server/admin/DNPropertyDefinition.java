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

import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;



/**
 * DN property definition.
 */
public final class DNPropertyDefinition extends
    AbstractPropertyDefinition<DN> {

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = -380704355977504890L;

  // Optional base DN which all valid values must be immediately
  // subordinate to.
  private final DN baseDN;



  /**
   * An interface for incrementally constructing DN property
   * definitions.
   */
  public static class Builder extends
      AbstractBuilder<DN, DNPropertyDefinition> {

    // Optional base DN which all valid values must be immediately
    // subordinate to.
    private DN baseDN = null;



    // Private constructor
    private Builder(String propertyName) {
      super(propertyName);
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
          setBaseDN(DN.decode(baseDN));
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



    /**
     * {@inheritDoc}
     */
    @Override
    protected DNPropertyDefinition buildInstance(String propertyName,
        EnumSet<PropertyOption> options,
        DefaultBehaviorProvider<DN> defaultBehavior) {
      return new DNPropertyDefinition(propertyName, options,
          defaultBehavior, baseDN);
    }
  }



  /**
   * Create a DN property definition builder.
   *
   * @param propertyName
   *          The property name.
   * @return Returns the new boolean property definition builder.
   */
  public static Builder createBuilder(String propertyName) {
    return new Builder(propertyName);
  }



  // Private constructor.
  private DNPropertyDefinition(String propertyName,
      EnumSet<PropertyOption> options,
      DefaultBehaviorProvider<DN> defaultBehavior, DN baseDN) {
    super(DN.class, propertyName, options, defaultBehavior);
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



  /**
   * {@inheritDoc}
   */
  @Override
  public void validateValue(DN value)
      throws IllegalPropertyValueException {
    ensureNotNull(value);

    if (baseDN != null) {
      DN parent = value.getParent();

      if (parent == null) {
        parent = DN.nullDN();
      }

      if (!parent.equals(baseDN)) {
        throw new IllegalPropertyValueException(this, value);
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public DN decodeValue(String value)
      throws IllegalPropertyValueStringException {
    ensureNotNull(value);

    try {
      DN dn = DN.decode(value);
      validateValue(dn);
      return dn;
    } catch (DirectoryException e) {
      throw new IllegalPropertyValueStringException(this, value);
    } catch (IllegalPropertyValueException e) {
      throw new IllegalPropertyValueStringException(this, value);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p) {
    return v.visitDN(this, p);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public int compare(DN o1, DN o2) {
    return o1.compareTo(o2);
  }
}
