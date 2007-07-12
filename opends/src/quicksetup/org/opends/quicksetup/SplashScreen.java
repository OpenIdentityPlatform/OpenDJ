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

package org.opends.quicksetup;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Toolkit;
import java.awt.Window;

import javax.swing.SwingUtilities;

import org.opends.quicksetup.i18n.ResourceProvider;
import org.opends.quicksetup.ui.Utilities;

/**
 * This is the class that displays a splash screen and in the background it will
 * create QuickSetup object.
 *
 * The main method of this class is directly called by the Java Web Start
 * mechanism to launch the JWS setup.
 *
 * This class tries to minimize the time to be displayed. So it does the loading
 * of the setup class in runtime once we already have displayed the splash
 * screen. This is why the quickSetup variable is of type Object.
 *
 * This class can be reused by simply overwriting the methods
 * constructApplication() and displayApplication().
 */
public class SplashScreen extends Window
{
  private static final long serialVersionUID = 8918803902867388766L;

  private Image image;

  private Object quickSetup;

  private Class<?> quickSetupClass;

  // Constant for the display of the splash screen
  private static final int MIN_SPLASH_DISPLAY = 3000;

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
   * {@inheritDoc}
   */
  public void update(Graphics g)
  {
    paint(g);
  }

  /**
   * {@inheritDoc}
   */
  public void paint(Graphics g)
  {
    g.drawImage(image, 0, 0, this);
  }

  /**
   * Protected constructor to force to use the main method.
   *
   */
  protected SplashScreen()
  {
    super(new Frame());
    try
    {
      image = getSplashImage();
      MediaTracker mt = new MediaTracker(this);
      mt.addImage(image, 0);
      mt.waitForID(0);

      int width = image.getWidth(this);
      int height = image.getHeight(this);
      setPreferredSize(new Dimension(width, height));
      setSize(width, height);
      Utilities.centerOnScreen(this);

    } catch (Exception ex)
    {
      ex.printStackTrace(); // Bug
    }
  }

  /**
   * The method used to display the splash screen.  It will also call create
   * the application associated with this SplashScreen and display it.
   * It can be called from the event thread and outside the event thread.
   * @param args arguments to be passed to the method QuickSetup.initialize
   */
  protected void display(String[] args)
  {
    if (SwingUtilities.isEventDispatchThread())
    {
      final String[] fArgs = args;
      Thread t = new Thread(new Runnable()
      {
        public void run()
        {
          mainOutsideEventThread(fArgs);
        }
      });
      t.start();
    } else
    {
      mainOutsideEventThread(args);
    }
  }

  /**
   * This method creates the image directly instead of using UIFactory to reduce
   * class loading.
   * @return the splash image.
   */
  private Image getSplashImage()
  {
    String resource = ResourceProvider.getInstance().getMsg("splash-icon");
    resource = "org/opends/quicksetup/" + resource;
    return Toolkit.getDefaultToolkit().createImage(
        this.getClass().getClassLoader().getResource(resource));
  }

  /**
   * This is basically the method that is execute in SplashScreen.main but it
   * it assumes that is being called outside the event thread.
   *
   * @param args arguments to be passed to the method QuickSetup.initialize.
   */
  private void mainOutsideEventThread(String[] args)
  {
    displaySplashScreen();
    long splashDisplayStartTime = System.currentTimeMillis();
    constructApplication(args);
    sleepIfNecessary(splashDisplayStartTime);
    disposeSplashScreen();
    displayApplication();
  }

  /**
   * This methods displays the splash screen.
   * This method assumes that is being called outside the event thread.
   */
  private void displaySplashScreen()
  {
    try
    {
      SwingUtilities.invokeAndWait(new Runnable()
      {
        public void run()
        {
          setVisible(true);
        }
      });
    } catch (Exception ex)
    {
      ex.printStackTrace();
    }
  }

  /**
   * This methods constructs the objects before displaying them.
   * This method assumes that is being called outside the event thread.
   * This method can be overwritten by subclasses to construct other objects
   * different than the Quick Setup.
   * @param args arguments passed in the main of this class.
   */
  protected void constructApplication(String[] args)
  {
    try
    {
      quickSetupClass = Class.forName("org.opends.quicksetup.ui.QuickSetup");
      quickSetup = quickSetupClass.newInstance();
      quickSetupClass.getMethod("initialize", new Class[]
        { String[].class }).invoke(quickSetup, new Object[]
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
   * This method displays the QuickSetup dialog.
   * @see org.opends.quicksetup.ui.QuickSetup#display
   * This method assumes that is being called outside the event thread.
   * This method can be overwritten by subclasses to construct other objects
   * different than the Quick Setup.
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
            quickSetupClass.getMethod("display").invoke(quickSetup);
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
      // do nothing;
    }
  }

  /**
   * Disposes the splash screen.
   * This method assumes that is being called outside the event thread.
   */
  private void disposeSplashScreen()
  {
    try
    {
      SwingUtilities.invokeAndWait(new Runnable()
      {
        public void run()
        {
          setVisible(false);
          dispose();
        }
      });
    } catch (Exception ex)
    {
      // do nothing;
    }
  }

  /**
   * This method just executes an sleep depending on how long the splash
   * screen has been displayed.  The idea of calling this method is to have the
   * splash screen displayed a minimum time (specified by
   * MIN_SPLASH_DISPLAY).
   * @param splashDisplayStartTime the time in milliseconds when the splash
   * screen started displaying.
   */
  private void sleepIfNecessary(long splashDisplayStartTime)
  {
    long t2 = System.currentTimeMillis();

    long sleepTime = MIN_SPLASH_DISPLAY - (t2 - splashDisplayStartTime);

    if (sleepTime > 0)
    {
      try
      {
        Thread.sleep(sleepTime);
      } catch (Exception ex)
      {
        // do nothing;
      }
    }
  }
}
