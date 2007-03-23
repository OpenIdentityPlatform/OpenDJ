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

package org.opends.server.admin;



import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;



/**
 * A class for retrieving internationalized resource properties
 * associated with a managed object definition.
 */
public final class ManagedObjectDefinitionI18NResource {

  // Mapping from definition to resource bundle.
  private final Map<AbstractManagedObjectDefinition, ResourceBundle> resources;

  // The resource name prefix.
  private final String prefix;



  /**
   * Creates a new internationalized resource instance which can be
   * used to retrieve the localized descriptions for the managed
   * objects and their associated properties and relations.
   *
   * @return Returns the I18N resource instance.
   */
  public static ManagedObjectDefinitionI18NResource create() {
    return new ManagedObjectDefinitionI18NResource("admin.messages");
  }



  /**
   * Creates a new internationalized resource instance for the named
   * profile.
   *
   * @param profile
   *          The name of the profile.
   * @return Returns the I18N resource instance for the named profile.
   */
  public static ManagedObjectDefinitionI18NResource createForProfile(
      String profile) {
    return new ManagedObjectDefinitionI18NResource("admin.profiles."
        + profile);
  }



  // Private constructor.
  private ManagedObjectDefinitionI18NResource(String prefix) {
    this.resources =
      new HashMap<AbstractManagedObjectDefinition, ResourceBundle>();
    this.prefix = prefix;
  }



  /**
   * Get the internationalized message associated with the specified
   * key in the default locale.
   *
   * @param d
   *          The managed object definition.
   * @param key
   *          The resource key.
   * @return Returns the internationalized message associated with the
   *         specified key in the default locale.
   * @throws MissingResourceException
   *           If the key was not found.
   */
  public String getMessage(AbstractManagedObjectDefinition d,
      String key) throws MissingResourceException {
    return getMessage(d, key, Locale.getDefault(), (String[]) null);
  }



  /**
   * Get the parameterized internationalized message associated with
   * the specified key in the default locale.
   *
   * @param d
   *          The managed object definition.
   * @param key
   *          The resource key.
   * @param args
   *          Arguments that should be inserted into the retrieved
   *          message.
   * @return Returns the internationalized message associated with the
   *         specified key in the default locale.
   * @throws MissingResourceException
   *           If the key was not found.
   */
  public String getMessage(AbstractManagedObjectDefinition d,
      String key, String... args) throws MissingResourceException {
    return getMessage(d, key, Locale.getDefault(), args);
  }



  /**
   * Get the internationalized message associated with the specified
   * key and locale.
   *
   * @param d
   *          The managed object definition.
   * @param key
   *          The resource key.
   * @param locale
   *          The locale.
   * @return Returns the internationalized message associated with the
   *         specified key and locale.
   * @throws MissingResourceException
   *           If the key was not found.
   */
  public String getMessage(AbstractManagedObjectDefinition d,
      String key, Locale locale) throws MissingResourceException {
    return getMessage(d, key, locale, (String[]) null);
  }



  /**
   * Get the parameterized internationalized message associated with
   * the specified key and locale.
   *
   * @param d
   *          The managed object definition.
   * @param key
   *          The resource key.
   * @param locale
   *          The locale.
   * @param args
   *          Arguments that should be inserted into the retrieved
   *          message.
   * @return Returns the internationalized message associated with the
   *         specified key and locale.
   * @throws MissingResourceException
   *           If the key was not found.
   */
  public String getMessage(AbstractManagedObjectDefinition d,
      String key, Locale locale, String... args)
      throws MissingResourceException {
    ResourceBundle resource = getResourceBundle(d, locale);

    if (args == null) {
      return resource.getString(key);
    } else {
      MessageFormat mf = new MessageFormat(resource.getString(key));
      return mf.format(args);
    }
  }



  // Retrieve the resource bundle associated with a managed object and
  // locale,
  // lazily loading it if necessary.
  private synchronized ResourceBundle getResourceBundle(
      AbstractManagedObjectDefinition d, Locale locale)
      throws MissingResourceException {
    ResourceBundle r = resources.get(d);

    if (r == null) {
      // Load the resource file.
      String baseName = prefix + "." + d.getClass().getName();
      r = ResourceBundle.getBundle(baseName, locale,
          ClassLoaderProvider.getInstance().getClassLoader());

      // Cache the resource.
      resources.put(d, r);
    }

    return r;
  }
}
