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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.quicksetup.ui;

import org.forgerock.i18n.LocalizableMessage;
import static org.opends.messages.QuickSetupMessages.*;

/** This panel is used to show the application is finished. */
public class FinishedPanel extends ProgressPanel
{
  private static final long serialVersionUID = 8129325068133356170L;

  /**
   * FinishedPanel constructor.
   * @param application Application this panel represents
   */
  public FinishedPanel(GuiApplication application)
  {
    super(application);
  }

  @Override
  protected LocalizableMessage getTitle()
  {
    return INFO_FINISHED_PANEL_TITLE.get();
  }
}
