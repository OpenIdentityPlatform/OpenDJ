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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.ui;

import java.awt.Component;

import org.opends.quicksetup.util.Utils;

/**
 * This panel is used to show a welcome message.
 *
 */
class InstallWelcomePanel extends QuickSetupStepPanel
{
  private static final long serialVersionUID = 6209217138897900860L;

  /**
   * Default constructor.
   *
   */
  public InstallWelcomePanel()
  {
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  protected String getTitle()
  {
    return getMsg("welcome-panel-title");
  }

  /**
   * {@inheritDoc}
   */
  protected String getInstructions()
  {
    /*
     * We can use org.opends.server.util.DynamicConstants without problems as it
     * has been added to quicksetup.jar during build time.
     */
    String[] args;
    String msgKey;
    if (Utils.isWebStart())
    {
      msgKey = "welcome-panel-webstart-instructions";
      args = new String[3];
      String cmd = Utils.isWindows()?"setup.bat":"setup";
      args[0] = UIFactory.applyFontToHtml(cmd,
          UIFactory.INSTRUCTIONS_MONOSPACE_FONT);
      args[1] = org.opends.server.util.DynamicConstants.COMPACT_VERSION_STRING;
      args[2] = org.opends.server.util.DynamicConstants.BUILD_ID;
    }
    else
    {
      args = new String[2];
      args[0] = org.opends.server.util.DynamicConstants.COMPACT_VERSION_STRING;
      args[1] = org.opends.server.util.DynamicConstants.BUILD_ID;
      msgKey = "welcome-panel-offline-instructions";
    }
    return getMsg(msgKey, args);
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
