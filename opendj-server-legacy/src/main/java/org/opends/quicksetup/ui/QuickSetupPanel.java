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
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.opends.quicksetup.ui;

import java.awt.Component;
import java.awt.Frame;
import java.awt.Window;

import javax.swing.JPanel;

/** This class is an abstract class that provides some commodity methods. */
abstract class QuickSetupPanel extends JPanel
{
  private static final long serialVersionUID = 2096518919339628055L;

  private final GuiApplication application;
  private QuickSetup quickSetup;

  /**
   * The basic constructor to be called by the subclasses.
   * @param application Application this panel represents
   */
  protected QuickSetupPanel(GuiApplication application)
  {
    super();
    this.application = application;
    setOpaque(false);
  }

  /**
   * Sets the instance of <code>QuickSetup</code> acting as controller.
   * @param qs QuickSetup instance
   */
  void setQuickSetup(QuickSetup qs) {
    this.quickSetup = qs;
  }

  /**
   * Gets the instance of <code>QuickSetup</code> acting as controller.
   * @return QuickSetup instance
   */
  protected QuickSetup getQuickSetup() {
    return this.quickSetup;
  }

  /**
   * Returns the frame or window containing this panel.
   * @return the frame or window containing this panel.
   */
  public Component getMainWindow()
  {
    Component w = null;
    Component c = this;

    while (w == null)
    {
      if (c instanceof Frame || c instanceof Window)
      {
        w = c;
      }
      if (c.getParent() == null)
      {
        w = c;
      } else
      {
        c = c.getParent();
      }
    }

    return w;
  }

  /**
   * Gets the application this panel represents.
   * @return GuiApplication this panel represents
   */
  protected GuiApplication getApplication() {
    return this.application;
  }
}
