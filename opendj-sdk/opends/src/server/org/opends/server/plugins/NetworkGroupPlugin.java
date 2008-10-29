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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.plugins;



import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.PluginCfgDefn;
import org.opends.server.admin.std.server.NetworkGroupPluginCfg;
import org.opends.server.admin.std.server.PluginCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.*;
import org.opends.server.config.ConfigException;
import org.opends.server.core.networkgroups.NetworkGroup;
import org.opends.server.types.AuthenticationType;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.DN;
import org.opends.server.types.ResultCode;

import org.opends.server.types.operation.PreParseAddOperation;
import org.opends.server.types.operation.PreParseBindOperation;
import org.opends.server.types.operation.PreParseCompareOperation;
import org.opends.server.types.operation.PreParseDeleteOperation;
import org.opends.server.types.operation.PreParseExtendedOperation;
import org.opends.server.types.operation.PreParseModifyOperation;
import org.opends.server.types.operation.PreParseModifyDNOperation;
import org.opends.server.types.operation.PreParseSearchOperation;
import org.opends.server.types.operation.PreParseUnbindOperation;
import org.opends.server.types.operation.PostResponseBindOperation;
import org.opends.server.types.operation.PostResponseExtendedOperation;
import org.opends.server.types.operation.PreParseOperation;
import static org.opends.messages.PluginMessages.*;
import static org.opends.server.util.ServerConstants.*;


/**
 * This class implements a Directory Server plugin that will evaluate
 * the appropriate network group for each client connection.
 * A network group enforces specific resource limits.
 */
public final class NetworkGroupPlugin
       extends DirectoryServerPlugin<NetworkGroupPluginCfg>
       implements ConfigurationChangeListener<NetworkGroupPluginCfg>
{
  // The current configuration for this plugin.
  private NetworkGroupPluginCfg currentConfig;



  /**
   * Creates a new instance of this Directory Server plugin.  Every plugin must
   * implement a default constructor (it is the only one that will be used to
   * create plugins defined in the configuration), and every plugin constructor
   * must call <CODE>super()</CODE> as its first element.
   */
  public NetworkGroupPlugin()
  {
    super();


  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public final void initializePlugin(Set<PluginType> pluginTypes,
                                     NetworkGroupPluginCfg configuration)
         throws ConfigException
  {
    currentConfig = configuration;

    // Make sure that the plugin has been enabled for the appropriate types.
    for (PluginType t : pluginTypes)
    {
      switch (t)
      {
        case POST_CONNECT:
        case PRE_PARSE_ADD:
        case PRE_PARSE_BIND:
        case PRE_PARSE_COMPARE:
        case PRE_PARSE_DELETE:
        case PRE_PARSE_EXTENDED:
        case PRE_PARSE_MODIFY:
        case PRE_PARSE_MODIFY_DN:
        case PRE_PARSE_SEARCH:
        case PRE_PARSE_UNBIND:
        case POST_RESPONSE_BIND:
        case POST_RESPONSE_EXTENDED:
          // These are acceptable
          break;
        default:
          Message message =
              ERR_PLUGIN_NETWORKGROUP_INVALID_PLUGIN_TYPE.get(t.toString());
          throw new ConfigException(message);
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public final void finalizePlugin()
  {
  }

  /**
   * Performs resource limits checks and request filtering policy checks.
   *
   * @param connection The client connection on which the operation is done
   * @param operation The operation to be performed
   * @param fullCheck boolean indicating whether all the resource limit checks
   *        must be performed or only a limited set
   * @param messages The list of error messages returned during the checking
   */
  private boolean checkNetworkGroup(
          ClientConnection connection,
          PreParseOperation operation,
          boolean fullCheck,
          ArrayList<Message> messages)
  {
    if (!connection.getNetworkGroup().checkResourceLimits(
            connection, operation, fullCheck, messages)) {
      return false;
    }
    if (operation != null) {
      if (!connection.getNetworkGroup().checkRequestFilteringPolicy(
              operation, messages)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Sets the network group and checks resource limits + request
   * filtering policy.
   *
   * @param connection The client connection on which the operation is
   * executed
   */
  private boolean setAndCheckNetworkGroup(
          ClientConnection connection,
          PreParseOperation operation,
          ArrayList<Message> messages)
  {
    boolean fullCheck = false;
    if (connection.mustEvaluateNetworkGroup(operation)) {
        NetworkGroup ng = NetworkGroup.findMatchingNetworkGroup(connection);
        if (ng != connection.getNetworkGroup()) {
          connection.setNetworkGroup(ng);
          fullCheck = true;
        }
        connection.mustEvaluateNetworkGroup(false);
    }

    return (checkNetworkGroup(connection, operation, fullCheck, messages));
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public final PluginResult.PostConnect
               doPostConnect(ClientConnection clientConnection)
  {
    ArrayList<Message> messages = new ArrayList<Message>();
    if (setAndCheckNetworkGroup(clientConnection, null, messages)) {
      return PluginResult.PostConnect.continueConnectProcessing();
    } else {
      return PluginResult.PostConnect.disconnectClient(
              DisconnectReason.ADMIN_LIMIT_EXCEEDED, true, messages.get(0));
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public PluginResult.PreParse
       doPreParse(PreParseAddOperation addOperation) {
    ArrayList<Message> messages = new ArrayList<Message>();
    ClientConnection connection = addOperation.getClientConnection();
    if (setAndCheckNetworkGroup(connection, addOperation, messages)) {
      return PluginResult.PreParse.continueOperationProcessing();
    } else {
      return PluginResult.PreParse.stopProcessing(
              ResultCode.ADMIN_LIMIT_EXCEEDED, messages.get(0));
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public PluginResult.PreParse
       doPreParse(PreParseBindOperation bindOperation) {
    ArrayList<Message> messages = new ArrayList<Message>();
    ClientConnection connection = bindOperation.getClientConnection();
    boolean fullCheck = false;

    if (connection.mustEvaluateNetworkGroup(bindOperation)) {
      DN dn;
      try {
        dn = DN.decode(bindOperation.getRawBindDN());
      } catch (DirectoryException ex) {
        return PluginResult.PreParse.stopProcessing(ResultCode.OPERATIONS_ERROR,
                ex.getMessageObject());
      }
      AuthenticationType authType = bindOperation.getAuthenticationType();

      NetworkGroup ng = NetworkGroup.findBindMatchingNetworkGroup(connection,
            dn, authType, connection.isSecure());

      if (ng != connection.getNetworkGroup()) {
        connection.setNetworkGroup(ng);
        fullCheck = true;
      }
      connection.mustEvaluateNetworkGroup(false);
    }
    if (!checkNetworkGroup(connection, bindOperation, fullCheck, messages)) {
      return PluginResult.PreParse.stopProcessing(
              ResultCode.ADMIN_LIMIT_EXCEEDED, messages.get(0));
    }
    return PluginResult.PreParse.continueOperationProcessing();
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public PluginResult.PreParse
       doPreParse(PreParseCompareOperation compareOperation) {
    ArrayList<Message> messages = new ArrayList<Message>();
    ClientConnection connection = compareOperation.getClientConnection();
    if (setAndCheckNetworkGroup(connection, compareOperation, messages)) {
      return PluginResult.PreParse.continueOperationProcessing();
    } else {
      return PluginResult.PreParse.stopProcessing(
              ResultCode.ADMIN_LIMIT_EXCEEDED, messages.get(0));
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public PluginResult.PreParse
       doPreParse(PreParseDeleteOperation deleteOperation) {
    ArrayList<Message> messages = new ArrayList<Message>();
    ClientConnection connection = deleteOperation.getClientConnection();
    if (setAndCheckNetworkGroup(connection, deleteOperation, messages)) {
      return PluginResult.PreParse.continueOperationProcessing();
    } else {
      return PluginResult.PreParse.stopProcessing(
              ResultCode.ADMIN_LIMIT_EXCEEDED, messages.get(0));
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public PluginResult.PreParse
       doPreParse(PreParseExtendedOperation extendedOperation) {
    ArrayList<Message> messages = new ArrayList<Message>();
    ClientConnection connection = extendedOperation.getClientConnection();
    if (setAndCheckNetworkGroup(connection, extendedOperation, messages)) {
      return PluginResult.PreParse.continueOperationProcessing();
    } else {
      return PluginResult.PreParse.stopProcessing(
              ResultCode.ADMIN_LIMIT_EXCEEDED, messages.get(0));
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public PluginResult.PreParse
       doPreParse(PreParseModifyOperation modifyOperation) {
    ArrayList<Message> messages = new ArrayList<Message>();
    ClientConnection connection = modifyOperation.getClientConnection();
    if (setAndCheckNetworkGroup(connection, modifyOperation, messages)) {
      return PluginResult.PreParse.continueOperationProcessing();
    } else {
      return PluginResult.PreParse.stopProcessing(
              ResultCode.ADMIN_LIMIT_EXCEEDED, messages.get(0));
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public PluginResult.PreParse
       doPreParse(PreParseModifyDNOperation modifyDNOperation) {
    ArrayList<Message> messages = new ArrayList<Message>();
    ClientConnection connection = modifyDNOperation.getClientConnection();
    if (setAndCheckNetworkGroup(connection, modifyDNOperation, messages)) {
      return PluginResult.PreParse.continueOperationProcessing();
    } else {
      return PluginResult.PreParse.stopProcessing(
              ResultCode.ADMIN_LIMIT_EXCEEDED, messages.get(0));
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public PluginResult.PreParse
       doPreParse(PreParseSearchOperation searchOperation) {
    ArrayList<Message> messages = new ArrayList<Message>();
    ClientConnection connection = searchOperation.getClientConnection();
    if (setAndCheckNetworkGroup(connection, searchOperation, messages)) {
      return PluginResult.PreParse.continueOperationProcessing();
    } else {
      return PluginResult.PreParse.stopProcessing(
              ResultCode.ADMIN_LIMIT_EXCEEDED, messages.get(0));
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public PluginResult.PreParse
       doPreParse(PreParseUnbindOperation unbindOperation) {
    ClientConnection connection = unbindOperation.getClientConnection();
    connection.mustEvaluateNetworkGroup(true);
    return PluginResult.PreParse.continueOperationProcessing();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public PluginResult.PostResponse
       doPostResponse(PostResponseBindOperation bindOperation) {
    if (bindOperation.getResultCode() != ResultCode.SUCCESS) {
      bindOperation.getClientConnection().mustEvaluateNetworkGroup(true);
    }
    return PluginResult.PostResponse.continueOperationProcessing();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public PluginResult.PostResponse
       doPostResponse(PostResponseExtendedOperation extendedOperation) {
    if ((extendedOperation.getRequestOID().equals(OID_START_TLS_REQUEST))
    && (extendedOperation.getResultCode() == ResultCode.SUCCESS)) {
      extendedOperation.getClientConnection().mustEvaluateNetworkGroup(true);
    }
    return PluginResult.PostResponse.continueOperationProcessing();
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isConfigurationAcceptable(PluginCfg configuration,
                                           List<Message> unacceptableReasons)
  {
    NetworkGroupPluginCfg cfg = (NetworkGroupPluginCfg) configuration;
    return isConfigurationChangeAcceptable(cfg, unacceptableReasons);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      NetworkGroupPluginCfg configuration,
                      List<Message> unacceptableReasons)
  {
    boolean configAcceptable = true;

    // Ensure that the set of plugin types contains only LDIF import and
    // pre-operation add.
    for (PluginCfgDefn.PluginType pluginType : configuration.getPluginType())
    {
      switch (pluginType)
      {
        case POSTCONNECT:
        case PREPARSEADD:
        case PREPARSEBIND:
        case PREPARSECOMPARE:
        case PREPARSEDELETE:
        case PREPARSEEXTENDED:
        case PREPARSEMODIFY:
        case PREPARSEMODIFYDN:
        case PREPARSESEARCH:
        case PREPARSEUNBIND:
        case POSTRESPONSEBIND:
        case POSTRESPONSEEXTENDED:
          // These are acceptable.
          break;


        default:
          Message message = ERR_PLUGIN_NETWORKGROUP_INVALID_PLUGIN_TYPE.get(
                  pluginType.toString());
          unacceptableReasons.add(message);
          configAcceptable = false;
      }
    }

    return configAcceptable;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
                                 NetworkGroupPluginCfg configuration)
  {
    currentConfig = configuration;
    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }
}
