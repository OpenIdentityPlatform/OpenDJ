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



import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.messages.MessageHandler.*;

import org.opends.server.api.ConfigChangeListener;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.messages.AdminMessages;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ResultCode;
import org.opends.server.util.StaticUtils;



/**
 * A configuration change listener which can be used to notify a
 * change listener when modifications are made to configuration
 * entries that it depends upon.
 */
final class DependencyConfigChangeListener implements ConfigChangeListener {

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The DN of the dependent configuration entry.
  private final DN dependentDN;

  // The dependent configuration change listener adaptor.
  private final ConfigChangeListenerAdaptor<?> dependentListener;



  /**
   * Creates a new dependency configuration change listener which will
   * notify the dependent listener whenever the configuration entry
   * that this listener monitors is modified.
   *
   * @param dependentDN
   *          The DN of the dependent configuration entry.
   * @param dependentListener
   *          The dependent configuration change listener adaptor.
   */
  public DependencyConfigChangeListener(DN dependentDN,
      ConfigChangeListenerAdaptor<?> dependentListener) {
    this.dependentDN = dependentDN;
    this.dependentListener = dependentListener;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(ConfigEntry configEntry) {
    ConfigEntry dependentConfigEntry = getConfigEntry(dependentDN);
    if (dependentConfigEntry != null) {
      return dependentListener.applyConfigurationChange(dependentConfigEntry,
          configEntry);
    } else {
      // The dependent entry was not found.
      configEntry.deregisterChangeListener(this);
      return new ConfigChangeResult(ResultCode.SUCCESS, false);
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean configChangeIsAcceptable(ConfigEntry configEntry,
      StringBuilder unacceptableReason) {
    ConfigEntry dependentConfigEntry = getConfigEntry(dependentDN);
    if (dependentConfigEntry != null) {
      return dependentListener.configChangeIsAcceptable(dependentConfigEntry,
          unacceptableReason, configEntry);
    } else {
      // The dependent entry was not found.
      configEntry.deregisterChangeListener(this);
      return true;
    }
  }



  // Returns the named configuration entry or null if it could not be
  // retrieved.
  private ConfigEntry getConfigEntry(DN dn) {
    try {
      ConfigEntry configEntry = DirectoryServer.getConfigEntry(dn);
      if (configEntry != null) {
        return configEntry;
      } else {
        int msgID = AdminMessages.MSGID_ADMIN_MANAGED_OBJECT_DOES_NOT_EXIST;
        String message = getMessage(msgID, String.valueOf(dn));
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.MILD_ERROR,
            message, msgID);
      }
    } catch (ConfigException e) {
      // The dependent entry could not be retrieved.
      if (debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = AdminMessages.MSGID_ADMIN_CANNOT_GET_MANAGED_OBJECT;
      String message = getMessage(msgID, String.valueOf(dn), StaticUtils
          .getExceptionMessage(e));
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.MILD_ERROR,
          message, msgID);
    }

    return null;
  }

}
