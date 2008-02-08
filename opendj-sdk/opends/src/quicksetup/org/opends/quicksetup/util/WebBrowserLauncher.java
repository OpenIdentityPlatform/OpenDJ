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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.util;
import org.opends.messages.Message;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * This is a very basic class used to launch the user web browser.
 *
 */
public class WebBrowserLauncher
{
  /**
   * Tries to launch the user web browser with a given URL.
   * @param url the url to be used to launch the web browser.
   * @throws WebBrowserException if launching failed.
   */
  public static void openURL(String url) throws WebBrowserException
  {
    try
    {
      if (Utils.isMacOS())
      {
        Class<?> fileMgr = Class.forName("com.apple.eio.FileManager");
        Method openURL = fileMgr.getDeclaredMethod("openURL", new Class[]
          { String.class });
        openURL.invoke(null, new Object[]
          { url });

      } else if (Utils.isWindows())
      {
        String[] cmd = {"rundll32", "url.dll,FileProtocolHandler", url};
        Runtime.getRuntime().exec(cmd);

      } else
      {
        // assume Unix or Linux
        String[] browsers =
              { "firefox", "opera", "konqueror", "epiphany", "mozilla",
                  "netscape" };
        String browser = null;
        for (int count = 0; count < browsers.length && browser == null;
        count++)
        {
          if (Runtime.getRuntime().exec(new String[]
            { "which", browsers[count] }).waitFor() == 0)
          {
            browser = browsers[count];
          }
        }

        if (browser == null)
        {
          throw new WebBrowserException(url, // TODO: i18n
                  Message.raw("Could not find web browser"),
                  null);
        } else
        {
          Runtime.getRuntime().exec(new String[]
            { browser, url });
        }
      }
    } catch (ClassNotFoundException cnfe)
    {
      throw new WebBrowserException(url, // TODO: i18n
              Message.raw("Class Not Found Exception"), cnfe);
    } catch (IOException ioe)
    {
      throw new WebBrowserException(url, // TODO: i18n
              Message.raw("IO Exception"), ioe);
    } catch (InterruptedException ie)
    {
      throw new WebBrowserException(url, // TODO: i18n
              Message.raw("Interrupted Exception"), ie);
    } catch (NoSuchMethodException nsme)
    {
      throw new WebBrowserException(url, // TODO: i18n
              Message.raw("No Such Method Exception"), nsme);
    } catch (InvocationTargetException ite)
    {
      throw new WebBrowserException(url, // TODO: i18n
              Message.raw("Invocation Target Exception"), ite);
    } catch (IllegalAccessException iae)
    {
      throw new WebBrowserException(url, // TODO: i18n
              Message.raw("Illegal Access Exception"), iae);
    }
  }
}
