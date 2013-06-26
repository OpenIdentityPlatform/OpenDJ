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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */

package org.opends.quicksetup.installer.ui;

import org.opends.messages.Message;
import static org.opends.messages.QuickSetupMessages.*;

import org.opends.server.util.DynamicConstants;

import java.awt.Component;

import org.opends.quicksetup.ui.GuiApplication;
import org.opends.quicksetup.ui.QuickSetupStepPanel;
import org.opends.quicksetup.util.Utils;

/**
 * This panel is used to show a welcome message.
 *
 */
public class InstallWelcomePanel extends QuickSetupStepPanel
{
  private static final long serialVersionUID = 6209217138897900860L;

  /**
   * Default constructor.
   * @param app Application this panel represents
   */
  public InstallWelcomePanel(GuiApplication app)
  {
    super(app);
  }

  /**
   * {@inheritDoc}
   */
  protected Message getTitle()
  {
    return INFO_WELCOME_PANEL_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  protected Message getInstructions()
  {
    /*
     * We can use org.opends.server.util.DynamicConstants without problems as it
     * has been added to quicksetup.jar during build time.
     */
    return Utils.getCustomizedObject(
          "INFO_WELCOME_PANEL_OFFLINE_INSTRUCTIONS",
          INFO_WELCOME_PANEL_OFFLINE_INSTRUCTIONS.get(
              DynamicConstants.SHORT_NAME,
              DynamicConstants.SHORT_NAME,
              DynamicConstants.DOC_REFERENCE_WIKI,
              DynamicConstants.SHORT_NAME),
          Message.class);
  }

  /**
   * {@inheritDoc}
   */
  protected Component createInputPanel()
  {
    // No input in this panel
    return null;
  }
}
