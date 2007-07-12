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



import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;

import org.opends.server.api.ConfigAddListener;
import org.opends.server.api.ConfigDeleteListener;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.ResultCode;



/**
 * A configuration add listener which will monitor a parent entry to
 * see when a specified child entry has been added. When the child
 * entry is added the add listener will automatically register its
 * "delayed" add or delete listener.
 */
final class DelayedConfigAddListener implements ConfigAddListener {

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The name of the parent entry.
  private final DN parent;

  // The name of the subordinate entry which should have an add or
  // delete listener registered with it when it is created.
  private final DN child;

  // The add listener to be registered with the subordinate entry when
  // it is added (or null if a delete listener should be registered).
  private final ConfigAddListener delayedAddListener;

  // The delete listener to be registered with the subordinate entry
  // when it is added (or null if an add listener should be
  // registered).
  private final ConfigDeleteListener delayedDeleteListener;



  /**
   * Create a new delayed add listener which will register an add
   * listener with the specified entry when it is added.
   *
   * @param child
   *          The name of the subordinate entry which should have an
   *          add listener registered with it when it is created.
   * @param addListener
   *          The add listener to be added to the subordinate entry
   *          when it is added.
   */
  public DelayedConfigAddListener(DN child, ConfigAddListener addListener) {
    this.parent = child.getParent();
    this.child = child;
    this.delayedAddListener = addListener;
    this.delayedDeleteListener = null;
  }



  /**
   * Create a new delayed add listener which will register a delete
   * listener with the specified entry when it is added.
   *
   * @param child
   *          The name of the subordinate entry which should have a
   *          delete listener registered with it when it is created.
   * @param deleteListener
   *          The delete listener to be added to the subordinate entry
   *          when it is added.
   */
  public DelayedConfigAddListener(DN child,
      ConfigDeleteListener deleteListener) {
    this.parent = child.getParent();
    this.child = child;
    this.delayedAddListener = null;
    this.delayedDeleteListener = deleteListener;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(ConfigEntry configEntry) {
    if (configEntry.getDN().equals(child)) {
      // The subordinate entry matched our criteria so register the
      // listener(s).
      if (delayedAddListener != null) {
        configEntry.registerAddListener(delayedAddListener);
      }

      if (delayedDeleteListener != null) {
        configEntry.registerDeleteListener(delayedDeleteListener);
      }

      // We are no longer needed.
      try {
        ConfigEntry myEntry = DirectoryServer.getConfigEntry(parent);
        if (myEntry != null) {
          myEntry.deregisterAddListener(this);
        }
      } catch (ConfigException e) {
        if (debugEnabled()) {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        // Ignore this error as it implies that this listener has
        // already been deregistered.
      }
    }

    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }



  /**
   * {@inheritDoc}
   */
  public boolean configAddIsAcceptable(ConfigEntry configEntry,
      StringBuilder unacceptableReason) {
    // Always acceptable.
    return true;
  }



  /**
   * Gets the delayed add listener.
   * <p>
   * This method is provided for unit-testing.
   *
   * @return Returns the delayed add listener, or <code>null</code>
   *         if this listener is delaying a delete listener.
   */
  ConfigAddListener getDelayedAddListener() {
    return delayedAddListener;
  }



  /**
   * Gets the delayed delete listener.
   * <p>
   * This method is provided for unit-testing.
   *
   * @return Returns the delayed delete listener, or <code>null</code>
   *         if this listener is delaying a add listener.
   */
  ConfigDeleteListener getDelayedDeleteListener() {
    return delayedDeleteListener;
  }

}
