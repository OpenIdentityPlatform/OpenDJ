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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.quicksetup.util;
import org.forgerock.i18n.LocalizableMessage;

import javax.swing.JFrame;

import org.opends.quicksetup.ui.WebBrowserErrorDialog;
import org.opends.quicksetup.ui.QuickSetupStepPanel;

/**
 * This class is used to try to launch a URL in the user web browser.
 *
 * The class extends SwingWorker and tries to launch the URL using
 * a WebBrowserLauncher object in the construct method.
 * If there is a problem launching the user's browser, this class will display
 * a WebBrowserErrorDialog to allow the user to copy to the system clipboard the
 * URL we wanted to display.
 *
 * When is finished (successfully or unsuccessfully) it notifies the
 * QuickSetupStepPanel passed in the constructor.
 */
public class URLWorker extends BackgroundTask<Object>
{
  private QuickSetupStepPanel panel;

  private String url;

  /**
   * Constructs a URLWorker.
   * @param panel the panel that created this URLWorker and to which we will
   * notify when we are over.
   * @param url the url to be displayed.
   */
  public URLWorker(QuickSetupStepPanel panel, String url)
  {
    this.panel = panel;
    this.url = url;
  }

  @Override
  public Object processBackgroundTask() throws WebBrowserException
  {
    try
    {
      WebBrowserLauncher.openURL(url);
    } catch (Throwable t)
    {
      // TODO: i18n
      throw new WebBrowserException(url, LocalizableMessage.raw("Bug: throwable"), t);
    }
    return null;
  }

  @Override
  public void backgroundTaskCompleted(Object returnValue,
      Throwable throwable)
  {
    WebBrowserException ex = (WebBrowserException) throwable;
    if (ex != null)
    {
      WebBrowserErrorDialog dlg =
          new WebBrowserErrorDialog((JFrame) panel.getMainWindow(), ex);
      dlg.setModal(false);
      dlg.packAndShow();
    }
    // Notify to the panel that the worker has finished.
    panel.urlWorkerFinished(this);
  }

  /**
   * Returns the URL that we are trying to launch in the users browser.
   * @return the URL that we are trying to launch in the users browser.
   */
  public String getURL()
  {
    return url;
  }
}
