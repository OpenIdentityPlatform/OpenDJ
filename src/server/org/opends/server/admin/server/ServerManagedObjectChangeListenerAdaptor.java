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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.admin.server;



import java.util.List;

import org.opends.messages.Message;
import org.opends.server.admin.Configuration;
import org.opends.server.types.ConfigChangeResult;



/**
 * An adaptor class which converts
 * {@link ServerManagedObjectChangeListener} callbacks to
 * {@link ConfigurationChangeListener} callbacks.
 *
 * @param <T>
 *          The type of server managed object that this listener
 *          should be notified about.
 */
final class ServerManagedObjectChangeListenerAdaptor<T extends Configuration>
    implements ServerManagedObjectChangeListener<T> {

  // The underlying change listener.
  private final ConfigurationChangeListener<? super T> listener;



  /**
   * Creates a new server managed object change listener adaptor.
   *
   * @param listener
   *          The underlying change listener.
   */
  public ServerManagedObjectChangeListenerAdaptor(
      ConfigurationChangeListener<? super T> listener) {
    this.listener = listener;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      ServerManagedObject<? extends T> mo) {
    return listener.applyConfigurationChange(mo.getConfiguration());
  }



  /**
   * Gets the configuration change listener associated with this
   * adaptor.
   *
   * @return Returns the configuration change listener associated with
   *         this adaptor.
   */
  public ConfigurationChangeListener<? super T>
  getConfigurationChangeListener() {
    return listener;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      ServerManagedObject<? extends T> mo, List<Message> unacceptableReasons) {
    return listener.isConfigurationChangeAcceptable(mo.getConfiguration(),
        unacceptableReasons);
  }

}
