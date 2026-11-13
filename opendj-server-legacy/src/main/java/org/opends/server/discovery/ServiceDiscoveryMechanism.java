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
 * Copyright 2016 ForgeRock AS.
 * Portions Copyright 2025-2026 3A Systems, LLC.
 */
package org.opends.server.discovery;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.ServiceDiscoveryMechanismCfg;
import org.opends.server.core.ServerContext;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Maintains a set of {@code Partition}s keeping it up to date according to a specific
 * discovery mechanism.
 *
 * @param <C> the configuration for the Service Discovery
 */
public abstract class ServiceDiscoveryMechanism<C extends ServiceDiscoveryMechanismCfg>
        implements Closeable,AutoCloseable {

   public volatile C cfg;
   private final Set<ServiceDiscoveryChangeListener> listeners = new CopyOnWriteArraySet<>();
  /**
   * Returns the name of the mechanism.
   *
   * @return the name of the mechanism
   */
  String getName(){
    return cfg.name();
  }

  /**
   * Returns whether the provided configuration is correct for the mechanism.
   * It should be possible to call this method on an uninitialized mechanism to check
   * the configuration for correctness.
   *
   * @param configuration the configuration to check
   * @param unacceptableReasons the list of reasons the configuration is not acceptable
   * @param serverContext the server context of this Directory Server instance
   * @return if the provided configuration is correct for the mechanism.
   */
  boolean isConfigurationAcceptable(C configuration, List<LocalizableMessage> unacceptableReasons, ServerContext serverContext){
    return true;
  }

  /**
   * Initializes the mechanism with the provided configuration.
   *
   * @param configuration the configuration for initialization
   * @param serverContext the server context for this Directory Server instance
   */
  void initializeMechanism(C configuration, ServerContext serverContext){
    this.cfg=configuration;
  }

  /**
   * Frees any resources in use, mechanism will not be used anymore afterwards.
   */
  void finalizeMechanism(){

  }

  /**
   * Registers a listener to be notified when changes in the service occur.
   *
   * @param listener the listener to register for notifications
   * @return true if registration was successful
   */
  public boolean registerChangeListener(ServiceDiscoveryChangeListener listener){
    return listeners.add(listener);
  }

  /**
   * De-registers a listener from notifications on service changes.
   *
   * @param listener the listener to de-register
   */
  public void deregisterChangeListener(ServiceDiscoveryChangeListener listener){
    listeners.remove(listener);
  }

  protected final void notifyChangeListeners() {
    for (ServiceDiscoveryChangeListener listener : listeners) {
      listener.serviceChanged(this);
    }
  }

    volatile Partition partition=new Partition(null, Collections.emptyList(),Collections.emptyList());
  /**
   * Returns the partitions.
   * <p>
   * {@link Partition} will only contain servers that are known to expose
   *
   * @return the partitions that can serve the provided base DNs
   */
  public Partition getPartition() {
      return partition;
  };

  /** Returns the servers suitable for an operation targeting the given remote DN. */
  public Partition getPartition(DN target) {
      return getPartition();
  }

  @Override
  public void close() throws IOException {

  }
}
