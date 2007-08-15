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
import org.opends.messages.Message;



import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.loggers.ErrorLogger.*;
import java.util.HashMap;
import java.util.Map;

import org.opends.server.api.ConfigChangeListener;
import org.opends.server.api.ConfigDeleteListener;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.messages.AdminMessages;
import org.opends.messages.MessageBuilder;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.ResultCode;
import org.opends.server.util.StaticUtils;



/**
 * A configuration delete listener which detects when a specified
 * entry is removed and, when it is, cleans up any listeners
 * associated with it.
 */
final class CleanerConfigDeleteListener implements ConfigDeleteListener {

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The change listeners.
  private Map<DN, ConfigChangeListener> changeListeners =
    new HashMap<DN, ConfigChangeListener>();

  // The DN of the monitored configuration entry.
  private final DN dn;



  /**
   * Creates a new cleaner configuration change listener which will
   * remove any registered listeners when then configuration entry it
   * is monitoring is removed.
   *
   * @param dn
   *          The DN of the entry to be monitored.
   */
  public CleanerConfigDeleteListener(DN dn) {
    this.dn = dn;
  }



  /**
   * Register a configuration change listener for removal when the
   * monitored entry is removed.
   *
   * @param dn
   *          The name of the entry associated with the configuration
   *          change listener.
   * @param listener
   *          The configuration change listener.
   */
  public void addConfigChangeListener(DN dn, ConfigChangeListener listener) {
    changeListeners.put(dn, listener);
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(ConfigEntry configEntry) {
    // Remove the listeners if the deleted entry is the monitored
    // entry.
    if (configEntry.getDN().equals(dn)) {
      for (Map.Entry<DN, ConfigChangeListener> me :
        changeListeners.entrySet()) {
        ConfigEntry listenerConfigEntry = getConfigEntry(me.getKey());
        if (listenerConfigEntry != null) {
          listenerConfigEntry.deregisterChangeListener(me.getValue());
        }
      }

      // Now remove this listener as we are no longer needed.
      ConfigEntry parentConfigEntry = getConfigEntry(dn.getParent());
      if (parentConfigEntry != null) {
        parentConfigEntry.deregisterDeleteListener(this);
      }
    }

    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }



  /**
   * {@inheritDoc}
   */
  public boolean configDeleteIsAcceptable(ConfigEntry configEntry,
      MessageBuilder unacceptableReason) {
    // Always acceptable.
    return true;
  }



  // Returns the named configuration entry or null if it could not be
  // retrieved.
  private ConfigEntry getConfigEntry(DN dn) {
    try {
      ConfigEntry configEntry = DirectoryServer.getConfigEntry(dn);
      if (configEntry != null) {
        return configEntry;
      } else {
        Message message = AdminMessages.ERR_ADMIN_MANAGED_OBJECT_DOES_NOT_EXIST.
            get(String.valueOf(dn));
        logError(message);
      }
    } catch (ConfigException e) {
      // The dependent entry could not be retrieved.
      if (debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = AdminMessages.ERR_ADMIN_CANNOT_GET_MANAGED_OBJECT.get(
          String.valueOf(dn), StaticUtils.getExceptionMessage(e));
      logError(message);
    }

    return null;
  }
}
