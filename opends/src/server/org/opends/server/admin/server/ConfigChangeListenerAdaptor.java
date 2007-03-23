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
package org.opends.server.admin.server;



import java.util.LinkedList;
import java.util.List;

import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.DecodingException;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.api.ConfigChangeListener;
import org.opends.server.config.ConfigEntry;
import org.opends.server.types.ConfigChangeResult;



/**
 * An adaptor class which converts {@link ConfigChangeListener}
 * callbacks to strongly typed {@link ConfigurationChangeListener}
 * callbacks.
 *
 * @param <S>
 *          The type of server configuration handled by the change
 *          listener.
 */
final class ConfigChangeListenerAdaptor<S extends Configuration>
    extends AbstractConfigListenerAdaptor implements
    ConfigChangeListener {

  // The managed object path.
  private final ManagedObjectPath path;

  // The managed object definition.
  private final AbstractManagedObjectDefinition<?, S> d;

  // The underlying change listener.
  private final ConfigurationChangeListener<? super S> listener;

  // Cached managed object between accept/apply callbacks.
  private ServerManagedObject<? extends S> cachedManagedObject;



  /**
   * Create a new configuration change listener adaptor.
   *
   * @param path
   *          The managed object path.
   * @param d
   *          The managed object definition.
   * @param listener
   *          The underlying change listener.
   */
  public ConfigChangeListenerAdaptor(ManagedObjectPath path,
      AbstractManagedObjectDefinition<?, S> d,
      ConfigurationChangeListener<? super S> listener) {
    this.path = path;
    this.d = d;
    this.listener = listener;
    this.cachedManagedObject = null;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      ConfigEntry configEntry) {
    // TODO: looking at the ConfigFileHandler implementation reveals
    // that this ConfigEntry will actually be a different object to
    // the one passed in the previous callback (it will have the same
    // content though). This config entry has the correct listener
    // lists.
    cachedManagedObject.setConfigEntry(configEntry);

    return listener.applyConfigurationChange(cachedManagedObject
        .getConfiguration());
  }



  /**
   * {@inheritDoc}
   */
  public boolean configChangeIsAcceptable(ConfigEntry configEntry,
      StringBuilder unacceptableReason) {
    try {
      cachedManagedObject = ServerManagedObject.decode(path, d,
          configEntry);
    } catch (DecodingException e) {
      generateUnacceptableReason(e, unacceptableReason);
      return false;
    }

    List<String> reasons = new LinkedList<String>();
    if (listener.isConfigurationChangeAcceptable(cachedManagedObject
        .getConfiguration(), reasons)) {
      return true;
    } else {
      generateUnacceptableReason(reasons, unacceptableReason);
      return false;
    }
  }



  /**
   * Get the configuiration change listener associated with this
   * adaptor.
   *
   * @return Returns the configuration change listener associated with
   *         this adaptor.
   */
  ConfigurationChangeListener<? super S> getConfigurationChangeListener() {
    return listener;
  }
}
