/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.datamodel;

/** Class used in the combo box models. It is used to have special rendering in the combo boxes. */
public class CategorizedComboBoxElement
{
  private Object value;
  private Type type;
  private int hashCode;

  /** The type of the element. */
  public enum Type
  {
    /**
     * Category type (in a combo box containing base DNs the backends are of
     * type category, for instance).
     */
    CATEGORY,
    /** Regular type. */
    REGULAR
  }

  /**
   * Constructor.
   * @param value the value of the element.
   * @param type the type of the element.
   */
  public CategorizedComboBoxElement(Object value, Type type)
  {
    this.value = value;
    this.type = type;
    this.hashCode = this.value.hashCode() + this.type.hashCode();
  }

  /**
   * Returns the value.
   * @return the value.
   */
  public Object getValue()
  {
    return value;
  }

  /**
   * Returns the type of the element.
   * @return the type of the element.
   */
  public Type getType()
  {
    return type;
  }

  @Override
  public boolean equals(Object o)
  {
    if (o instanceof CategorizedComboBoxElement)
    {
      CategorizedComboBoxElement desc = (CategorizedComboBoxElement)o;
      return desc.getType() == getType()
          && getValue().equals(desc.getValue());
    }
    return false;
  }

  @Override
  public int hashCode()
  {
    return hashCode;
  }
}
