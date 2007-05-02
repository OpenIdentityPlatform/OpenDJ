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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.statuspanel.i18n;

import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * This class is used to retrieve localized messages from Resources.properties
 * files.  This class extends org.opends.quicksetup.i18n.ResourceProvider so
 * that it first looks for properties in the Resources.properties located in
 * the org.opends.quicksetup.resources package and then if they are not there
 * looks for properties in org.opends.statuspanel.resources.
 *
 * This is done to avoid duplication of properties between the setup and the
 * status panel.
 *
 */
public class ResourceProvider
extends org.opends.quicksetup.i18n.ResourceProvider
{
  private ResourceBundle bundle;

  private static ResourceProvider instance;

  private static final String BUNDLE_NAME =
      "org.opends.statuspanel.resources.Resources";

  private ResourceProvider()
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
      /* First try to quick setup resource provider as it contains most of
       * the labels.
       */
      msg = super.getMsg(key);
    }
    catch (Exception ex)
    {
      /* Now try with the status panel specific resources.
       */
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
  public String getMsg(String key, String... args)
  throws IllegalArgumentException
  {
    String msg;
    try
    {
      /* First try to quick setup resource provider as it contains most of
       * the labels.
       */
      msg = super.getMsg(key, args);
    }
    catch (Exception ex)
    {
      /* Now try with the status panel specific resources.
       */
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
    }
    return msg;
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
        bundle =
          ResourceBundle.getBundle(BUNDLE_NAME, getLocale(), this
              .getClass().getClassLoader());
      } catch (java.util.MissingResourceException e)
      {
        throw new IllegalStateException("Could not retrieve Resource Bundle: "
            + BUNDLE_NAME, e);

      }
    }

    return bundle;
  }
}

