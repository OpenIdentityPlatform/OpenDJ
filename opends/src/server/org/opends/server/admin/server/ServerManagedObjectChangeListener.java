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
 *      Portions Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.admin.server;



import org.opends.messages.Message;

import java.util.List;

import org.opends.server.admin.Configuration;
import org.opends.server.types.ConfigChangeResult;



/**
 * This interface defines the methods that a Directory Server
 * configurable component should implement if it wishes to be able to
 * receive notifications when a its associated server managed object
 * is changed.
 *
 * @param <T>
 *          The type of server managed object that this listener
 *          should be notified about.
 */
public interface ServerManagedObjectChangeListener<T extends Configuration> {

  /**
   * Indicates whether the proposed change to the server managed
   * object is acceptable to this change listener.
   *
   * @param mo
   *          The new server managed object containing the changes.
   * @param unacceptableReasons
   *          A list that can be used to hold messages about why the
   *          provided server managed object is not acceptable.
   * @return Returns <code>true</code> if the proposed change is
   *         acceptable, or <code>false</code> if it is not.
   */
  public boolean isConfigurationChangeAcceptable(
      ServerManagedObject<? extends T> mo, List<Message> unacceptableReasons);



  /**
   * Applies the server managed object changes to this change
   * listener.
   *
   * @param mo
   *          The new server managed object containing the changes.
   * @return Returns information about the result of changing the
   *         server managed object.
   */
  public ConfigChangeResult applyConfigurationChange(
      ServerManagedObject<? extends T> mo);
}
