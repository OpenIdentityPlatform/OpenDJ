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



import java.util.List;

import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;

import static org.opends.server.loggers.Debug.*;



/**
 * This class defines an interface that may be implemented by
 * Directory Server components that may be reconfigured on-the-fly
 * either over a Directory Server protocol like LDAP or through JMX.
 */
public interface ConfigurableComponent
{
  /**
   * Retrieves the DN of the configuration entry with which this
   * component is associated.
   *
   * @return  The DN of the configuration entry with which this
   *          component is associated.
   */
  public DN getConfigurableComponentEntryDN();



  /**
   * Retrieves the set of configuration attributes that are associated
   * with this configurable component.
   *
   * @return  The set of configuration attributes that are associated
   *          with this configurable component.
   */
  public List<ConfigAttribute> getConfigurationAttributes();



  /**
   * Indicates whether the provided configuration entry has an
   * acceptable configuration for this component.  If it does not,
   * then detailed information about the problem(s) should be added to
   * the provided list.
   *
   * @param  configEntry          The configuration entry for which to
   *                              make the determination.
   * @param  unacceptableReasons  A list that can be used to hold
   *                              messages about why the provided
   *                              entry does not have an acceptable
   *                              configuration.
   *
   * @return  <CODE>true</CODE> if the provided entry has an
   *          acceptable configuration for this component, or
   *          <CODE>false</CODE> if not.
   */
  public boolean hasAcceptableConfiguration(ConfigEntry configEntry,
                      List<String> unacceptableReasons);



  /**
   * Makes a best-effort attempt to apply the configuration contained
   * in the provided entry.  Information about the result of this
   * processing should be added to the provided message list.
   * Information should always be added to this list if a
   * configuration change could not be applied.  If detailed results
   * are requested, then information about the changes applied
   * successfully (and optionally about parameters that were not
   * changed) should also be included.
   *
   * @param  configEntry      The entry containing the new
   *                          configuration to apply for this
   *                          component.
   * @param  detailedResults  Indicates whether detailed information
   *                          about the processing should be added to
   *                          the list.
   *
   * @return  Information about the result of the configuration
   *          update.
   */
  public ConfigChangeResult
              applyNewConfiguration(ConfigEntry configEntry,
                                    boolean detailedResults);
}

