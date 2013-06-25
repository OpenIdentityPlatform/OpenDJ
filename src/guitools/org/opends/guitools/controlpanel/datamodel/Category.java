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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.datamodel;

import java.util.ArrayList;

import org.opends.messages.Message;

/**
 * Class containing the different actions for a given category.  For instance
 * for the Category 'Indexes' the Actions are 'Manage Indexes...', 'Verify
 * Indexes...' etc.
 *
 */
public class Category
{
  private Message name;
  private ArrayList<Action> actions = new ArrayList<Action>();

  /**
   * Returns the name of the category.
   * @return the name of the category.
   */
  public Message getName()
  {
    return name;
  }

  /**
   * Sets the name of the category.
   * @param name the name of the category.
   */
  public void setName(Message name)
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
