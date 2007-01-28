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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.statuspanel;

import javax.swing.SwingUtilities;

/**
 * This class will display a splash screen and in the background it will
 * create The StatusPanel object.
 *
 * This class just extends the org.opends.quicksetup.SplashScreen by
 * overwritting the construct and display methods.
 */
public class SplashScreen extends org.opends.quicksetup.SplashScreen
{
  private static final long serialVersionUID = 4472839063380302713L;

  private static Object statusPanel;

  private static Class<?> statusPanelClass;

  /**
   * The main method for this class.
   * It can be called from the event thread and outside the event thread.
   * @param args arguments to be passed to the method QuickSetup.initialize
   */
  public static void main(String[] args)
  {
    SplashScreen screen = new SplashScreen();
    screen.display(args);
  }

  /**
   * This methods constructs the StatusPanel object.
   * This method assumes that is being called outside the event thread.
   * @param args arguments to be passed to the method StatusPanel.initialize.
   */
  protected void constructApplication(String[] args)
  {
    try
    {
      statusPanelClass = Class.forName(
          "org.opends.statuspanel.StatusPanelController");
      statusPanel = statusPanelClass.newInstance();
      statusPanelClass.getMethod("initialize", new Class[]
        { String[].class }).invoke(statusPanel, new Object[]
        { args });
    } catch (Exception e)
    {
      InternalError error =
          new InternalError("Failed to invoke initialize method");
      error.initCause(e);
      throw error;
    }
  }

  /**
   * This method displays the StatusPanel dialog.
   * @see StatusPanelController.display.
   * This method assumes that is being called outside the event thread.
   */
  protected void displayApplication()
  {
    try
    {
      SwingUtilities.invokeAndWait(new Runnable()
      {
        public void run()
        {
          try
          {
            statusPanelClass.getMethod("display").invoke(statusPanel);
          } catch (Exception e)
          {
            InternalError error =
                new InternalError("Failed to invoke display method");
            error.initCause(e);
            throw error;
          }
        }
      });
    } catch (Exception ex)
    {
    }
  }
}
