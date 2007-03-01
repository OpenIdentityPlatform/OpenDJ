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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.synchronization.plugin;

import java.util.HashMap;
import java.util.Map;

import org.opends.server.api.ConfigAddListener;
import org.opends.server.api.ConfigChangeListener;
import org.opends.server.api.ConfigDeleteListener;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.synchronization.changelog.Changelog;
import org.opends.server.synchronization.common.LogMessages;
import org.opends.server.types.DN;
import org.opends.server.core.DeleteOperation;
import org.opends.server.types.DirectoryException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Entry;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.Operation;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SynchronizationProviderResult;

import static org.opends.server.synchronization.common.LogMessages.*;

/**
 * This class is used to load the Synchronization code inside the JVM
 * and to trigger initialization of the synchronization.
 *
 * It also extends the SynchronizationProvider class in order to have some
 * synchronization code running during the operation process
 * as pre-op, conflictRsolution, and post-op.
 */
public class MultimasterSynchronization extends SynchronizationProvider
       implements ConfigAddListener, ConfigDeleteListener, ConfigChangeListener
{
  static String CHANGELOG_DN = "cn=Changelog Server," +
    "cn=Multimaster Synchronization, cn=Synchronization Providers, cn=config";
  static String SYNCHRONIZATION_CLASS =
    "ds-cfg-synchronization-provider-config";

  private DN changelogConfigEntryDn = null;
  private Changelog changelog = null;
  private static Map<DN, SynchronizationDomain> domains =
    new HashMap<DN, SynchronizationDomain>() ;


  /**
   * {@inheritDoc}
   */
  public void initializeSynchronizationProvider(ConfigEntry configEntry)
  throws ConfigException
  {
    LogMessages.registerMessages();

    configEntry.registerAddListener(this);
    configEntry.registerDeleteListener(this);

    /*
     * Read changelog server the changelog configuration entry
     */
    try
    {
      changelogConfigEntryDn = DN.decode(CHANGELOG_DN);
      ConfigEntry config =
        DirectoryServer.getConfigEntry(changelogConfigEntryDn);
      /*
       * If there is no such entry, this process must not be a changelog server
       */
      if (config != null)
      {
        changelog = new Changelog(config);
      }
    } catch (DirectoryException e)
    {
      /* never happens */
      throw new ConfigException(MSGID_SYNC_INVALID_DN,
      "Invalid Changelog configuration DN");
    }

    /*
     * Parse the list of entries below configEntry,
     * create one synchronization domain for each child
     */
    for (ConfigEntry domainEntry : configEntry.getChildren().values())
    {
      if (domainEntry.hasObjectClass(SYNCHRONIZATION_CLASS))
      {
        createNewSynchronizationDomain(domainEntry);
      }
    }
  }

  /**
   * Indicates whether the configuration entry that will result from a proposed
   * modification is acceptable to this change listener.
   *
   * @param  configEntry         The configuration entry that will result from
   *                             the requested update.
   * @param  unacceptableReason  A buffer to which this method can append a
   *                             human-readable message explaining why the
   *                             proposed change is not acceptable.
   *
   * @return  <CODE>true</CODE> if the proposed entry contains an acceptable
   *          configuration, or <CODE>false</CODE> if it does not.
   */
  public boolean configChangeIsAcceptable(ConfigEntry configEntry,
                                          StringBuilder unacceptableReason)
  {
    return false; // TODO :NYI
  }

  /**
   * Attempts to apply a new configuration to this Directory Server component
   * based on the provided changed entry.
   *
   * @param  configEntry  The configuration entry that containing the updated
   *                      configuration for this component.
   *
   * @return  Information about the result of processing the configuration
   *          change.
   */
  public ConfigChangeResult applyConfigurationChange(ConfigEntry configEntry)
  {
    // TODO implement this method
    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }

  /**
   * {@inheritDoc}
   */
  public boolean configAddIsAcceptable(ConfigEntry configEntry,
      StringBuilder unacceptableReason)
  {
    // Check if the added entry is the changelog config entry
    try
    {
      if (configEntry.getDN().equals(DN.decode(CHANGELOG_DN)))
      {
        return Changelog.checkConfigEntry(configEntry, unacceptableReason);
      }
    } catch (DirectoryException e)
    {
      /* never happens */
       unacceptableReason.append("Invalid Changelog configuration DN");
       return false;
    }

    // otherwise it must be a Synchronization domain, check for
    // presence of the Synchronization configuration object class
    if (configEntry.hasObjectClass(SYNCHRONIZATION_CLASS))
    {
      return SynchronizationDomain.checkConfigEntry(configEntry,
          unacceptableReason);
    }

    return false;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(ConfigEntry configEntry)
  {
    // check if the entry is the changelog configuration entry
    if (configEntry.getDN().equals(changelogConfigEntryDn))
    {
      try
      {
        changelog = new Changelog(configEntry);
        return new ConfigChangeResult(ResultCode.SUCCESS, false);
      } catch (ConfigException e)
      {
        // we should never get to this point because the configEntry has
        // already been validated in configAddisAcceptable
        return new ConfigChangeResult(ResultCode.SUCCESS, false);
      }
    }

    // otherwise it must be a synchronization domain, check for
    // presence of the Synchronization configuration object class
    if (configEntry.hasObjectClass(SYNCHRONIZATION_CLASS))
    {
      try
      {
        createNewSynchronizationDomain(configEntry);
        return new ConfigChangeResult(ResultCode.SUCCESS, false);
      } catch (ConfigException e)
      {
        // we should never get to this point because the configEntry has
        // already been validated in configAddisAcceptable
        return new ConfigChangeResult(ResultCode.SUCCESS, false);
      }
    }

    // we should never get to this point because the configEntry has
    // already been validated in configAddisAcceptable
    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }

  /**
   * Creates a New Synchronization domain from its configEntry, do the
   * necessary initialization and starts it so that it is
   * fully operational when this method returns.
   * @param configEntry The entry whith the configuration of this domain.
   * @throws ConfigException When the configuration is not valid.
   */
  private void createNewSynchronizationDomain(ConfigEntry configEntry)
          throws ConfigException
  {
    SynchronizationDomain domain;
    domain = new SynchronizationDomain(configEntry);
    domains.put(domain.getBaseDN(), domain);
    domain.start();
  }

  /**
   * {@inheritDoc}
   */
  public boolean configDeleteIsAcceptable(ConfigEntry configEntry,
      StringBuilder unacceptableReason)
  {

    // TODO Auto-generated method stub
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(ConfigEntry configEntry)
  {
    // TODO Auto-generated method stub
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public void doPostOperation(AddOperation addOperation)
  {
    DN dn = addOperation.getEntryDN();
    genericPostOperation(addOperation, dn);
  }


  /**
   * {@inheritDoc}
   */
  public void doPostOperation(DeleteOperation deleteOperation)
  {
    DN dn = deleteOperation.getEntryDN();
    genericPostOperation(deleteOperation, dn);
  }

  /**
   * {@inheritDoc}
   */
  public void doPostOperation(ModifyDNOperation modifyDNOperation)
  {
    DN dn = modifyDNOperation.getEntryDN();
    genericPostOperation(modifyDNOperation, dn);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void doPostOperation(ModifyOperation modifyOperation)
  {
    DN dn = modifyOperation.getEntryDN();
    genericPostOperation(modifyOperation, dn);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SynchronizationProviderResult handleConflictResolution(
                                                ModifyOperation modifyOperation)
  {
    SynchronizationDomain domain =
      findDomain(modifyOperation.getEntryDN(), modifyOperation);
    if (domain == null)
      return new SynchronizationProviderResult(true);

    return domain.handleConflictResolution(modifyOperation);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SynchronizationProviderResult handleConflictResolution(
      AddOperation addOperation) throws DirectoryException
  {
    SynchronizationDomain domain =
      findDomain(addOperation.getEntryDN(), addOperation);
    if (domain == null)
      return new SynchronizationProviderResult(true);

    return domain.handleConflictResolution(addOperation);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SynchronizationProviderResult handleConflictResolution(
      DeleteOperation deleteOperation) throws DirectoryException
  {
    SynchronizationDomain domain =
      findDomain(deleteOperation.getEntryDN(), deleteOperation);
    if (domain == null)
      return new SynchronizationProviderResult(true);

    return domain.handleConflictResolution(deleteOperation);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SynchronizationProviderResult handleConflictResolution(
      ModifyDNOperation modifyDNOperation) throws DirectoryException
  {
    SynchronizationDomain domain =
      findDomain(modifyDNOperation.getEntryDN(), modifyDNOperation);
    if (domain == null)
      return new SynchronizationProviderResult(true);

    return domain.handleConflictResolution(modifyDNOperation);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SynchronizationProviderResult
      doPreOperation(ModifyOperation modifyOperation)
  {
    DN operationDN = modifyOperation.getEntryDN();
    SynchronizationDomain domain = findDomain(operationDN, modifyOperation);

    if ((domain == null) || (!domain.solveConflict()))
      return new SynchronizationProviderResult(true);

    Historical historicalInformation = (Historical)
                            modifyOperation.getAttachment(HISTORICAL);
    if (historicalInformation == null)
    {
      Entry entry = modifyOperation.getModifiedEntry();
      historicalInformation = Historical.load(entry);
      modifyOperation.setAttachment(HISTORICAL, historicalInformation);
    }

    historicalInformation.generateState(modifyOperation);

    return new SynchronizationProviderResult(true);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SynchronizationProviderResult doPreOperation(
      DeleteOperation deleteOperation) throws DirectoryException
  {
    return new SynchronizationProviderResult(true);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SynchronizationProviderResult doPreOperation(
      ModifyDNOperation modifyDNOperation) throws DirectoryException
  {
    return new SynchronizationProviderResult(true);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SynchronizationProviderResult doPreOperation(AddOperation addOperation)
  {
    SynchronizationDomain domain =
      findDomain(addOperation.getEntryDN(), addOperation);
    if (domain == null)
      return new SynchronizationProviderResult(true);

    if (!addOperation.isSynchronizationOperation())
      domain.doPreOperation(addOperation);

    return new SynchronizationProviderResult(true);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void finalizeSynchronizationProvider()
  {
    // shutdown all the Synchronization domains
    for (SynchronizationDomain domain : domains.values())
    {
      domain.shutdown();
    }

    // shutdown the Changelog Service if necessary
    if (changelog != null)
      changelog.shutdown();
  }

  /**
   * Finds the Synchronization domain for a given DN.
   *
   * @param dn The DN for which the domain must be returned.
   * @return The Synchronization domain for this DN.
   */
  private static SynchronizationDomain findDomain(DN dn, Operation op)
  {
    /*
     * Don't run the special synchronization code on Operation that are
     * specifically marked as don't synchronize.
     */
    if (op.dontSynchronize())
      return null;

    SynchronizationDomain domain = null;
    DN temp = dn;
    do
    {
      domain = domains.get(temp);
      temp = temp.getParentDNInSuffix();
      if (temp == null)
      {
        break;
      }
    } while (domain == null);

    return domain;
  }

  /**
   * Generic code for all the postOperation entry point.
   *
   * @param operation The Operation for which the post-operation is called.
   * @param dn The Dn for which the post-operation is called.
   */
  private void genericPostOperation(Operation operation, DN dn)
  {
    SynchronizationDomain domain = findDomain(dn, operation);
    if (domain == null)
      return;

    domain.synchronize(operation);

    return;
  }

}


