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
 * Portions Copyright 2014-2015 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.datamodel;

import java.util.ArrayList;

import org.forgerock.i18n.LocalizableMessage;

/**
 * Class containing the different actions for a given category.  For instance
 * for the Category 'Indexes' the Actions are 'Manage Indexes...', 'Verify
 * Indexes...' etc.
 *
 */
public class Category
{
  private LocalizableMessage name;
  private ArrayList<Action> actions = new ArrayList<>();

  /**
   * Returns the name of the category.
   * @return the name of the category.
   */
  public LocalizableMessage getName()
  {
    return name;
  }

  /**
   * Sets the name of the category.
   * @param name the name of the category.
   */
  public void setName(LocalizableMessage name)
  {
    this.name = name;
  }

  /**
   * Returns the actions associated with this category.
   * @return the actions associated with this category.
   */
  public ArrayList<Action> getActions()
  {
    return actions;
  }
}
