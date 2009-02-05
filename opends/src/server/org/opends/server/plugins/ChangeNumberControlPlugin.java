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

import java.util.List;
import java.util.Set;

import java.util.TreeSet;
import java.io.IOException;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.PluginCfgDefn;
import org.opends.server.admin.std.server.ChangeNumberControlPluginCfg;
import org.opends.server.admin.std.server.PluginCfg;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.config.ConfigException;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.protocol.OperationContext;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.Control;
import org.opends.server.types.ResultCode;
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

  // The current configuration for this plugin.
  private ChangeNumberControlPluginCfg currentConfig;

  /**
   * The control used by this plugin.
   */
  public static class ChangeNumberControl extends Control
  {
    private ChangeNumber cn;

    /**
     * Constructs a new change number control.
     *
     * @param  isCritical   Indicates whether support for this control should be
     *                      considered a critical part of the server processing.
     * @param cn          The change number.
     */
    public ChangeNumberControl(boolean isCritical, ChangeNumber cn)
    {
      super(OID_CSN_CONTROL, isCritical);
      this.cn = cn;
    }

    /**
     * Writes this control's value to an ASN.1 writer. The value (if any) must
     * be written as an ASN1OctetString.
     *
     * @param writer The ASN.1 writer to use.
     * @throws IOException If a problem occurs while writing to the stream.
     */
    protected void writeValue(ASN1Writer writer) throws IOException {
      writer.writeOctetString(cn.toString());
    }

    /**
     * Retrieves the change number.
     *
     * @return The change number.
     */
    public ChangeNumber getChangeNumber()
    {
      return cn;
    }
  }

  /**
   * Creates a new instance of this Directory Server plugin.  Every plugin must
   * implement a default constructor (it is the only one that will be used to
   * create plugins defined in the configuration), and every plugin constructor
   * must call <CODE>super()</CODE> as its first element.
   */
  public ChangeNumberControlPlugin()
  {
    super();
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public final void initializePlugin(Set<PluginType> pluginTypes,
                                     ChangeNumberControlPluginCfg configuration)
         throws ConfigException
  {
    currentConfig = configuration;
    configuration.addChangeNumberControlChangeListener(this);
    Set<PluginType> types = new TreeSet<PluginType>();

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
          Message message =
              ERR_PLUGIN_CHANGE_NUMBER_INVALID_PLUGIN_TYPE.get(t.toString());
          throw new ConfigException(message);
      }
    }
    if (types.size() != 4) {
      StringBuffer expected = new StringBuffer();
      expected.append(PluginType.POST_OPERATION_ADD.toString());
      expected.append(", ");
      expected.append(PluginType.POST_OPERATION_DELETE.toString());
      expected.append(", ");
      expected.append(PluginType.POST_OPERATION_MODIFY.toString());
      expected.append(", ");
      expected.append(PluginType.POST_OPERATION_MODIFY_DN.toString());

      StringBuffer found = new StringBuffer();
      boolean first = true;
      for (PluginType t : types) {
        if (first) {
          first = false;
        } else {
          found.append(", ");
        }
        found.append(t.toString());
      }

      Message message = ERR_PLUGIN_CHANGE_NUMBER_INVALID_PLUGIN_TYPE_LIST.get(
              found.toString(), expected.toString());
          throw new ConfigException(message);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void finalizePlugin()
  {
    currentConfig.removeChangeNumberControlChangeListener(this);
  }


 /**
   * {@inheritDoc}
   */
  @Override()
  public final PluginResult.PostOperation
       doPostOperation(PostOperationAddOperation addOperation)
  {
    processCsnControl(addOperation);

    // We shouldn't ever need to return a non-success result.
    return PluginResult.PostOperation.continueOperationProcessing();
  }

   /**
   * {@inheritDoc}
   */
  @Override()
  public final PluginResult.PostOperation
       doPostOperation(PostOperationDeleteOperation deleteOperation)
  {
    processCsnControl(deleteOperation);

    // We shouldn't ever need to return a non-success result.
    return PluginResult.PostOperation.continueOperationProcessing();
  }

   /**
   * {@inheritDoc}
   */
  @Override()
  public final PluginResult.PostOperation
       doPostOperation(PostOperationModifyOperation modifyOperation)
  {
    processCsnControl(modifyOperation);

    // We shouldn't ever need to return a non-success result.
    return PluginResult.PostOperation.continueOperationProcessing();
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public final PluginResult.PostOperation
       doPostOperation(PostOperationModifyDNOperation modifyDNOperation)
  {
    processCsnControl(modifyDNOperation);

    // We shouldn't ever need to return a non-success result.
    return PluginResult.PostOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isConfigurationAcceptable(PluginCfg configuration,
                                           List<Message> unacceptableReasons)
  {
    ChangeNumberControlPluginCfg cfg =
        (ChangeNumberControlPluginCfg) configuration;
    return isConfigurationChangeAcceptable(cfg, unacceptableReasons);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      ChangeNumberControlPluginCfg configuration,
      List<Message> unacceptableReasons)
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
          Message message = ERR_PLUGIN_CHANGE_NUMBER_INVALID_PLUGIN_TYPE.get(
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
                                 ChangeNumberControlPluginCfg configuration)
  {
    currentConfig = configuration;
    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }

  /**
   * Retrieves the Change number from the synchronization context
   * and sets teh control respsonse in the operation.
   *
   * @param operation the operation
   */
  private void processCsnControl(PostOperationOperation operation) {
    List<Control> requestControls = operation.getRequestControls();
    if ((requestControls != null) && (! requestControls.isEmpty())) {
      for (Control c : requestControls) {
        if (c.getOID().equals(OID_CSN_CONTROL)) {
          OperationContext ctx = (OperationContext)
            operation.getAttachment(OperationContext.SYNCHROCONTEXT);
          if (ctx != null) {
            ChangeNumber cn = ctx.getChangeNumber();
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

