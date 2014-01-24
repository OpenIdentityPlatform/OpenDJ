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
 *      Portions Copyright 2014 ForgeRock AS
 */

package org.opends.guitools.controlpanel.datamodel;

import org.opends.guitools.controlpanel.ui.StatusGenericPanel;
import org.forgerock.i18n.LocalizableMessage;

/**
 * The class that is used by the different action buttons on the left side of
 * the main ControlPanel dialog.
 *
 */
public class Action
{
  private LocalizableMessage name;

  private Class<? extends StatusGenericPanel> associatedPanel;

  /**
   * Returns the name of the action.
   * @return the name of the action.
   */
  public LocalizableMessage getName()
  {
    return name;
  }

  /**
   * Sets the name of the action.
   * @param name the name of the action.
   */
  public void setName(LocalizableMessage name)
  {
    this.name = name;
  }

  /**
   * Returns the class of the panel that is associated with this action
   * (for instance the NewBaseDNPanel class is associated with the 'New
   * Base DN' action.
   * @return the class of the panel that is associated with this action.
   */
  public Class<? extends StatusGenericPanel> getAssociatedPanelClass()
  {
    return associatedPanel;
  }

  /**
   * Sets the class of the panel that is associated with this action.
   * @param associatedPanel the class of the panel that is associated with this
   * action.
   */
  public void setAssociatedPanel(
      Class<? extends StatusGenericPanel> associatedPanel)
  {
    this.associatedPanel = associatedPanel;
  }
}
