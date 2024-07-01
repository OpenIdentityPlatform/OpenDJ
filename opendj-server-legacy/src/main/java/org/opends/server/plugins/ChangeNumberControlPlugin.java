/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.plugins;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.meta.PluginCfgDefn;
import org.forgerock.opendj.server.config.server.ChangeNumberControlPluginCfg;
import org.forgerock.opendj.server.config.server.PluginCfg;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.OperationContext;
import org.opends.server.types.Control;
import org.opends.server.types.operation.PostOperationAddOperation;
import org.opends.server.types.operation.PostOperationDeleteOperation;
import org.opends.server.types.operation.PostOperationModifyDNOperation;
import org.opends.server.types.operation.PostOperationModifyOperation;
import org.opends.server.types.operation.PostOperationOperation;

import static org.opends.messages.PluginMessages.*;
import static org.opends.server.util.ServerConstants.*;

/**
 * This class implements a Directory Server plugin that will add the
 * replication CSN to a response whenever the CSN control is received.
 */
public final class ChangeNumberControlPlugin
       extends DirectoryServerPlugin<ChangeNumberControlPluginCfg>
       implements ConfigurationChangeListener<ChangeNumberControlPluginCfg>
{

  /** The current configuration for this plugin. */
  private ChangeNumberControlPluginCfg currentConfig;

  /** The control used by this plugin. */
  public static class ChangeNumberControl extends Control
  {
    private CSN csn;

    /**
     * Constructs a new change number control.
     *
     * @param isCritical Indicates whether support for this control should be
     *                   considered a critical part of the server processing.
     * @param csn        The CSN.
     */
    public ChangeNumberControl(boolean isCritical, CSN csn)
    {
      super(OID_CSN_CONTROL, isCritical);
      this.csn = csn;
    }

    @Override
    protected void writeValue(ASN1Writer writer) throws IOException {
      writer.writeOctetString(csn.toString());
    }

    /**
     * Retrieves the CSN.
     *
     * @return The CSN.
     */
    public CSN getCSN()
    {
      return csn;
    }
  }

  /**
   * Creates a new instance of this Directory Server plugin. Every plugin must
   * implement a default constructor (it is the only one that will be used to
   * create plugins defined in the configuration), and every plugin constructor
   * must call <CODE>super()</CODE> as its first element.
   */
  public ChangeNumberControlPlugin()
  {
    super();
  }

  @Override
  public final void initializePlugin(Set<PluginType> pluginTypes,
                                     ChangeNumberControlPluginCfg configuration)
         throws ConfigException
  {
    currentConfig = configuration;
    configuration.addChangeNumberControlChangeListener(this);
    Set<PluginType> types = new TreeSet<>();

    // Make sure that the plugin has been enabled for the appropriate types.
    for (PluginType t : pluginTypes)
    {
      switch (t)
      {
        case POST_OPERATION_ADD:
        case POST_OPERATION_DELETE:
        case POST_OPERATION_MODIFY:
        case POST_OPERATION_MODIFY_DN:
          // These are acceptable.
          types.add(t);
          break;

        default:
          throw new ConfigException(ERR_PLUGIN_CHANGE_NUMBER_INVALID_PLUGIN_TYPE.get(t));
      }
    }
    if (types.size() != 4) {
      StringBuilder expected = new StringBuilder();
      expected.append(PluginType.POST_OPERATION_ADD);
      expected.append(", ");
      expected.append(PluginType.POST_OPERATION_DELETE);
      expected.append(", ");
      expected.append(PluginType.POST_OPERATION_MODIFY);
      expected.append(", ");
      expected.append(PluginType.POST_OPERATION_MODIFY_DN);

      StringBuilder found = new StringBuilder();
      boolean first = true;
      for (PluginType t : types) {
        if (first) {
          first = false;
        } else {
          found.append(", ");
        }
        found.append(t);
      }

      throw new ConfigException(ERR_PLUGIN_CHANGE_NUMBER_INVALID_PLUGIN_TYPE_LIST.get(
          found, expected));
    }
  }

  @Override
  public final void finalizePlugin()
  {
    currentConfig.removeChangeNumberControlChangeListener(this);
  }

  @Override
  public final PluginResult.PostOperation
       doPostOperation(PostOperationAddOperation addOperation)
  {
    processCsnControl(addOperation);

    // We shouldn't ever need to return a non-success result.
    return PluginResult.PostOperation.continueOperationProcessing();
  }

  @Override
  public final PluginResult.PostOperation
       doPostOperation(PostOperationDeleteOperation deleteOperation)
  {
    processCsnControl(deleteOperation);

    // We shouldn't ever need to return a non-success result.
    return PluginResult.PostOperation.continueOperationProcessing();
  }

  @Override
  public final PluginResult.PostOperation
       doPostOperation(PostOperationModifyOperation modifyOperation)
  {
    processCsnControl(modifyOperation);

    // We shouldn't ever need to return a non-success result.
    return PluginResult.PostOperation.continueOperationProcessing();
  }

  @Override
  public final PluginResult.PostOperation
       doPostOperation(PostOperationModifyDNOperation modifyDNOperation)
  {
    processCsnControl(modifyDNOperation);

    // We shouldn't ever need to return a non-success result.
    return PluginResult.PostOperation.continueOperationProcessing();
  }

  @Override
  public boolean isConfigurationAcceptable(PluginCfg configuration,
                                           List<LocalizableMessage> unacceptableReasons)
  {
    ChangeNumberControlPluginCfg cfg =
        (ChangeNumberControlPluginCfg) configuration;
    return isConfigurationChangeAcceptable(cfg, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
      ChangeNumberControlPluginCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    boolean configAcceptable = true;

    // Ensure that the set of plugin types contains only pre-operation add,
    // pre-operation modify, and pre-operation modify DN.
    for (PluginCfgDefn.PluginType pluginType : configuration.getPluginType())
    {
      switch (pluginType)
      {
        case POSTOPERATIONADD:
        case POSTOPERATIONDELETE:
        case POSTOPERATIONMODIFY:
        case POSTOPERATIONMODIFYDN:
          // These are acceptable.
          break;


        default:
          unacceptableReasons.add(ERR_PLUGIN_CHANGE_NUMBER_INVALID_PLUGIN_TYPE.get(pluginType));
          configAcceptable = false;
      }
    }

    return configAcceptable;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
                                 ChangeNumberControlPluginCfg configuration)
  {
    currentConfig = configuration;
    return new ConfigChangeResult();
  }

  /**
   * Retrieves the CSN from the synchronization context and sets the control
   * response in the operation.
   *
   * @param operation the operation
   */
  private void processCsnControl(PostOperationOperation operation) {
    List<Control> requestControls = operation.getRequestControls();
    if (requestControls != null && ! requestControls.isEmpty()) {
      for (Control c : requestControls) {
        if (c.getOID().equals(OID_CSN_CONTROL)) {
          OperationContext ctx = (OperationContext)
            operation.getAttachment(OperationContext.SYNCHROCONTEXT);
          if (ctx != null) {
            CSN cn = ctx.getCSN();
            if (cn != null) {
              Control responseControl =
                  new ChangeNumberControl(c.isCritical(), cn);
              operation.getResponseControls().add(responseControl);
            }
          }
          break;
        }
      }
    }
  }
}

