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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.ui;

import java.awt.Component;
import java.awt.Frame;
import java.awt.Window;

import javax.swing.JPanel;

import org.opends.quicksetup.i18n.ResourceProvider;

/**
 * This class is an abstract class that provides some commodity methods.
 *
 */
abstract class QuickSetupPanel extends JPanel
{
  private static final long serialVersionUID = 2096518919339628055L;

  /**
   * The basic constructor to be called by the subclasses.
   *
   */
  protected QuickSetupPanel()
  {
    super();
    setOpaque(false);
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
   * Returns a localized message for a key value.  In  the properties file we
   * have something of type:
   * key=value
   *
   * @see ResourceProvider#getMsg(String)
   * @param key the key in the properties file.
   * @return the value associated to the key in the properties file.
   * properties file.
   */
  protected String getMsg(String key)
  {
    return getI18n().getMsg(key);
  }

  /**
   * Returns a localized message for a key value.  In  the properties file we
   * have something of type:
   * key=value
   *
   * For instance if we pass as key "mykey" and as arguments {"value1"} and
   * in the properties file we have:
   * mykey=value with argument {0}.
   *
   * This method will return "value with argument value1".
   * @see ResourceProvider#getMsg(String, String[])
   * @param key the key in the properties file.
   * @param args the arguments to be passed to generate the resulting value.
   * @return the value associated to the key in the properties file.
   */
  protected String getMsg(String key, String[] args)
  {
    return getI18n().getMsg(key, args);
  }

  /**
   * Returns a ResourceProvider instance.
   * @return a ResourceProvider instance.
   */
  private ResourceProvider getI18n()
  {
    return ResourceProvider.getInstance();
  }
}
