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
 * Portions Copyright 2013-2014 ForgeRock AS.
 */

package org.opends.quicksetup.util;


import static com.forgerock.opendj.util.OperatingSystem.isWindows;
import static com.forgerock.opendj.util.OperatingSystem.isMacOS;
import org.forgerock.i18n.LocalizableMessage;

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
      if (isMacOS())
      {
        Class<?> fileMgr = Class.forName("com.apple.eio.FileManager");
        Method openURL = fileMgr.getDeclaredMethod("openURL", new Class[]
          { String.class });
        openURL.invoke(null, url);

      } else if (isWindows())
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
                  LocalizableMessage.raw("Could not find web browser"),
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
              LocalizableMessage.raw("Class Not Found Exception"), cnfe);
    } catch (IOException ioe)
    {
      throw new WebBrowserException(url, // TODO: i18n
              LocalizableMessage.raw("IO Exception"), ioe);
    } catch (InterruptedException ie)
    {
      throw new WebBrowserException(url, // TODO: i18n
              LocalizableMessage.raw("Interrupted Exception"), ie);
    } catch (NoSuchMethodException nsme)
    {
      throw new WebBrowserException(url, // TODO: i18n
              LocalizableMessage.raw("No Such Method Exception"), nsme);
    } catch (InvocationTargetException ite)
    {
      throw new WebBrowserException(url, // TODO: i18n
              LocalizableMessage.raw("Invocation Target Exception"), ite);
    } catch (IllegalAccessException iae)
    {
      throw new WebBrowserException(url, // TODO: i18n
              LocalizableMessage.raw("Illegal Access Exception"), iae);
    }
  }
}
