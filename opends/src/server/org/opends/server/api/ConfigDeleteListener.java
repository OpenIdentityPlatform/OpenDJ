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
package org.opends.server.api;



import org.opends.server.config.ConfigEntry;
import org.opends.server.types.ConfigChangeResult;



/**
 * This interface defines the methods that a Directory Server
 * component should implement if it wishes to be able to receive
 * notification if entries below a configuration entry are removed.
 */
public interface ConfigDeleteListener
{
  /**
   * Indicates whether it is acceptable to remove the provided
   * configuration entry.
   *
   * @param  configEntry         The configuration entry that will be
   *                             removed from the configuration.
   * @param  unacceptableReason  A buffer to which this method can
   *                             append a human-readable message
   *                             explaining why the proposed delete is
   *                             not acceptable.
   *
   * @return  <CODE>true</CODE> if the proposed entry may be removed
   *          from the configuration, or <CODE>false</CODE> if not.
   */
  public boolean configDeleteIsAcceptable(ConfigEntry configEntry,
                      StringBuilder unacceptableReason);



  /**
   * Attempts to apply a new configuration based on the provided
   * deleted entry.
   *
   * @param  configEntry  The new configuration entry that has been
   *                      deleted.
   *
   * @return  Information about the result of processing the
   *          configuration change.
   */
  public ConfigChangeResult applyConfigurationDelete(
                                 ConfigEntry configEntry);
}

