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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.quicksetup.installer.ui;

import org.forgerock.i18n.LocalizableMessage;

import static org.opends.messages.QuickSetupMessages.*;

import org.opends.server.util.DynamicConstants;
import org.opends.server.util.Platform;

import java.awt.Component;

import org.opends.quicksetup.ui.GuiApplication;
import org.opends.quicksetup.ui.QuickSetupStepPanel;
import org.opends.quicksetup.util.Utils;

/** This panel is used to show a welcome message. */
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

  @Override
  protected LocalizableMessage getTitle()
  {
    return INFO_WELCOME_PANEL_TITLE.get();
  }

  @Override
  protected LocalizableMessage getInstructions()
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
              Platform.JAVA_MINIMUM_VERSION_NUMBER,
              DynamicConstants.DOC_REFERENCE_WIKI,
              DynamicConstants.SHORT_NAME),
          LocalizableMessage.class);
  }

  @Override
  protected Component createInputPanel()
  {
    // No input in this panel
    return null;
  }
}
