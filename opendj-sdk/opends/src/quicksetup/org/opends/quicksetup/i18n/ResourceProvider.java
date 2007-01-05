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

package org.opends.quicksetup.i18n;

import java.net.URL;
import java.net.URLClassLoader;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

import org.opends.quicksetup.util.Utils;

/**
 * This class is used to retrieve localized messages from Resources.properties
 * files.  The locale used currently is the default Locale of the JVM.
 *
 */
public class ResourceProvider
{
  private ResourceBundle bundle;

  private static ResourceProvider instance;

  private static final String BUNDLE_NAME =
      "org.opends.quicksetup.resources.Resources";

  /**
   * This constructor is protected to be able to subclass.
   *
   */
  protected ResourceProvider()
  {
  }

  /**
   * Provides an instance of the ResourceProvider.  The instance is unique for
   * this process (which implies that during the process lifetime we can only
   * have messages in one language).
   *
   * @return an instance of ResourceProvider.
   */
  public static ResourceProvider getInstance()
  {
    if (instance == null)
    {
      instance = new ResourceProvider();
    }
    return instance;
  }

  /**
   * Gets a localized message for a key value.  In  the properties file we have
   * something of type:
   * key=value
   * @param key the key in the properties file.
   * @return the value associated to the key in the properties file.
   * @throws IllegalArgumentException if the key could not be found in the
   * properties file.
   */
  public String getMsg(String key) throws IllegalArgumentException
  {
    String msg;
    try
    {
      msg = getResourceBundle().getString(key);

    } catch (java.util.MissingResourceException e)
    {
      // The less brutal alternative here is to do msg = key instead
      // of
      // throwing an exception but this helps being strict with
      // resources
      // (so I prefer to keep it as it is at least at the beginning)
      throw new IllegalArgumentException("Unknown Resource Bundle key: " +
          key);
    }
    return msg;
  }

  /**
   * Gets a localized message for a key value.  In  the properties file we have
   * something of type:
   * key=value
   *
   * For instance if we pass as key "mykey" and as arguments {"value1"} and
   * in the properties file we have:
   * mykey=value with argument {0}.
   *
   * This method will return "value with argument value1".
   *
   * @param key the key in the properties file.
   * @param args the arguments to be passed to generate the resulting value.
   * @return the value associated to the key in the properties file.
   * @throws IllegalArgumentException if the key could not be found in the
   * properties file.
   */
  public String getMsg(String key, String[] args)
  throws IllegalArgumentException
  {
    String msg;
    try
    {
      String pattern = getResourceBundle().getString(key);
      MessageFormat mf = new MessageFormat(pattern);

      msg = mf.format(args);
    } catch (java.util.MissingResourceException e)
    {
      // The less brutal alternative here is to do msg = key instead
      // of
      // throwing an exception but this helps being strict with
      // resources
      // (so I prefer to keep it as it is at least at the beginning)
      throw new IllegalArgumentException("Unknown Resource Bundle key: " +
          key);
    }

    return msg;
  }

  /**
   * Indicates which is the Locale that will be used to determine the language
   * of the messages.
   *
   * @return the Locale that will be used to determine the language of
   * the messages.
   */
  public Locale getLocale()
  {
    return Locale.getDefault();
  }

  /**
   * The ResourceBundle that will be used to get the localized messages.
   * @return the ResourceBundle that will be used to get the localized
   * messages.
   */
  private ResourceBundle getResourceBundle()
  {
    if (bundle == null)
    {
      try
      {
        if (Utils.isWebStart())
        {
          /*
           * Construct a URLClassLoader using only the jar of quicksetup.jar
           * when we are using Web Start. If we use the current classloader all
           * the jars (including those marked as lazy) will be downloaded.
           */
          URL[] urls = new URL[]
            { getQuickSetupJarURL() };
          bundle =
              ResourceBundle.getBundle(BUNDLE_NAME, getLocale(),
                  new URLClassLoader(urls));
        } else
        {
          bundle =
              ResourceBundle.getBundle(BUNDLE_NAME, getLocale(), this
                  .getClass().getClassLoader());
        }
      } catch (java.util.MissingResourceException e)
      {
        throw new IllegalStateException("Could not retrieve Resource Bundle: "
            + BUNDLE_NAME, e);

      }
    }

    return bundle;
  }

  /**
   * Returns the URL of the Jar file that was used to retrieve this class.
   * @return the URL of the Jar file that was used to retrieve this class.
   */
  private URL getQuickSetupJarURL()
  {
    return this.getClass().getProtectionDomain().getCodeSource().getLocation();
  }
}
