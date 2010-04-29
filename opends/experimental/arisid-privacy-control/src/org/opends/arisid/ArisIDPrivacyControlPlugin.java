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
 *      Copyright 2010 Sun Microsystems, Inc.
 */
package org.opends.arisid;



import java.util.Set;

import org.opends.arisid.server.ArisidPrivacyControlPluginCfg;
import org.opends.messages.Message;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.config.ConfigException;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.operation.*;



/**
 * IGF ArisID Privacy Control Plugin implementation.
 */
public final class ArisIDPrivacyControlPlugin extends
    DirectoryServerPlugin<ArisidPrivacyControlPluginCfg>
{
  /**
   * {@inheritDoc}
   */
  @Override
  public PluginResult.PreOperation doPreOperation(
      PreOperationAddOperation addOperation)
      throws CanceledOperationException
  {
    handleOperation(addOperation);
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public PluginResult.PreOperation doPreOperation(
      PreOperationCompareOperation compareOperation)
      throws CanceledOperationException
  {
    handleOperation(compareOperation);
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public PluginResult.PreOperation doPreOperation(
      PreOperationDeleteOperation deleteOperation)
      throws CanceledOperationException
  {
    handleOperation(deleteOperation);
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public PluginResult.PreOperation doPreOperation(
      PreOperationExtendedOperation extendedOperation)
      throws CanceledOperationException
  {
    handleOperation(extendedOperation);
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public PluginResult.PreOperation doPreOperation(
      PreOperationModifyDNOperation modifyDNOperation)
      throws CanceledOperationException
  {
    handleOperation(modifyDNOperation);
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public PluginResult.PreOperation doPreOperation(
      PreOperationModifyOperation modifyOperation)
      throws CanceledOperationException
  {
    handleOperation(modifyOperation);
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public PluginResult.PreOperation doPreOperation(
      PreOperationSearchOperation searchOperation)
      throws CanceledOperationException
  {
    handleOperation(searchOperation);
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializePlugin(Set<PluginType> pluginTypes,
      ArisidPrivacyControlPluginCfg configuration)
      throws ConfigException
  {
    // This plugin may only be used as a pre-operation plugin.
    for (final PluginType t : pluginTypes)
    {
      switch (t)
      {
      case PRE_OPERATION_ADD:
      case PRE_OPERATION_COMPARE:
      case PRE_OPERATION_DELETE:
      case PRE_OPERATION_EXTENDED:
      case PRE_OPERATION_MODIFY:
      case PRE_OPERATION_MODIFY_DN:
      case PRE_OPERATION_SEARCH:
        // This is fine.
        break;
      default:
        throw new ConfigException(Message.raw("Invalid plugin type "
            + t + " for the IGF plugin."));
      }
    }

  }



  private void handleOperation(PreOperationOperation op)
  {
    try
    {
      final ArisIDPrivacyControl control = op
          .getRequestControl(ArisIDPrivacyControl.DECODER);
      if (control != null)
      {
        ErrorLogger.logError(Message.raw(control.toString()));
      }
    }
    catch (final Exception e)
    {
      ErrorLogger.logError(Message
          .raw("Unable to decode the IGF privacy" + " control:  " + e));
    }
  }
}
