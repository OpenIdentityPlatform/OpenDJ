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
package org.opends.server.synchronization.plugin;

import java.util.List;

import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.server.ChangelogServerCfg;
import org.opends.server.admin.std.server.MultimasterSynchronizationProviderCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.synchronization.changelog.Changelog;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.ResultCode;

/**
 * This class is used to create and object that can
 * register in the admin framework as a listener for changes, add and delete
 * on the Changelog Server configuration objects.
 *
 */
public class ChangelogListener
       implements ConfigurationAddListener<ChangelogServerCfg>,
       ConfigurationDeleteListener<ChangelogServerCfg>
{
  Changelog changelog = null;

  /**
   * Build a Changelog Listener from the given Multimaster configuration.
   *
   * @param configuration The configuration that will be used to listen
   *                      for changelog configuration changes.
   *
   * @throws ConfigException if the ChangelogListener can't register for
   *                         listening to changes on the provided configuration
   *                         object.
   */
  public ChangelogListener(
      MultimasterSynchronizationProviderCfg configuration)
      throws ConfigException
  {
    configuration.addChangelogServerAddListener(this);
    configuration.addChangelogServerDeleteListener(this);

    if (configuration.hasChangelogServer())
    {
      ChangelogServerCfg server = configuration.getChangelogServer();
      changelog = new Changelog(server);
    }
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(
      ChangelogServerCfg configuration)
  {
    try
    {
      changelog = new Changelog(configuration);
      return new ConfigChangeResult(ResultCode.SUCCESS, false);
    } catch (ConfigException e)
    {
      // we should never get to this point because the configEntry has
      // already been validated in configAddisAcceptable
      return new ConfigChangeResult(ResultCode.CONSTRAINT_VIOLATION, false);
    }
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
      ChangelogServerCfg configuration, List<String> unacceptableReasons)
  {
    return Changelog.isConfigurationAcceptable(
      configuration, unacceptableReasons);
  }

  /**
   * Shutdown the Changelog servers.
   */
  public void shutdown()
  {
    if (changelog != null)
      changelog.shutdown();
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
      ChangelogServerCfg configuration)
  {
    // There can be only one changelog, just shutdown the changelog
    // currently configured.
    if (changelog != null)
    {
      changelog.shutdown();
    }
    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
      ChangelogServerCfg configuration, List<String> unacceptableReasons)
  {
    return true;
  }
}
