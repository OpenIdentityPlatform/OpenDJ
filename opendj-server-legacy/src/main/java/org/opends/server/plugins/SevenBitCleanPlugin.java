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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.plugins;

import static org.opends.messages.PluginMessages.*;
import static org.opends.server.core.BackendConfigManager.NamingContextFilter.PUBLIC;
import static org.opends.server.core.BackendConfigManager.NamingContextFilter.TOP_LEVEL;

import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.AVA;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.meta.PluginCfgDefn;
import org.forgerock.opendj.server.config.server.SevenBitCleanPluginCfg;
import org.forgerock.opendj.server.config.server.PluginCfg;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.Entry;
import org.opends.server.types.LDAPException;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.RawModification;
import org.opends.server.types.operation.PreParseAddOperation;
import org.opends.server.types.operation.PreParseModifyDNOperation;
import org.opends.server.types.operation.PreParseModifyOperation;

/**
 * This class implements a Directory Server plugin that can be used to ensure
 * that the values for a specified set of attributes (optionally, below a
 * specified set of base DNs) are 7-bit clean (i.e., contain only ASCII
 * characters).
 */
public final class SevenBitCleanPlugin
       extends DirectoryServerPlugin<SevenBitCleanPluginCfg>
       implements ConfigurationChangeListener<SevenBitCleanPluginCfg>
{
  /** The bitmask that will be used to make the comparisons. */
  private static final byte MASK = 0x7F;

  /** The current configuration for this plugin. */
  private SevenBitCleanPluginCfg currentConfig;

  /**
   * Creates a new instance of this Directory Server plugin.  Every plugin must
   * implement a default constructor (it is the only one that will be used to
   * create plugins defined in the configuration), and every plugin constructor
   * must call {@code super()} as its first element.
   */
  public SevenBitCleanPlugin()
  {
    super();
  }

  @Override
  public final void initializePlugin(Set<PluginType> pluginTypes,
                                     SevenBitCleanPluginCfg configuration)
         throws ConfigException
  {
    currentConfig = configuration;
    configuration.addSevenBitCleanChangeListener(this);

    // Make sure that the plugin has been enabled for the appropriate types.
    for (PluginType t : pluginTypes)
    {
      switch (t)
      {
        case LDIF_IMPORT:
        case PRE_PARSE_ADD:
        case PRE_PARSE_MODIFY:
        case PRE_PARSE_MODIFY_DN:
          // These are acceptable.
          break;

        default:
          throw new ConfigException(ERR_PLUGIN_7BIT_INVALID_PLUGIN_TYPE.get(t));
      }
    }
  }

  @Override
  public final void finalizePlugin()
  {
    currentConfig.removeSevenBitCleanChangeListener(this);
  }

  @Override
  public final PluginResult.ImportLDIF
               doLDIFImport(LDIFImportConfig importConfig, Entry entry)
  {
    // Get the current configuration for this plugin.
    SevenBitCleanPluginCfg config = currentConfig;

    // Make sure that the entry is within the scope of this plugin.  While
    // processing an LDIF import, we don't have access to the set of public
    // naming contexts defined in the server, so if no explicit set of base DNs
    // is defined, then assume that the entry is in scope.
    if (!isDescendantOfAny(entry.getName(), config.getBaseDN()))
    {
      // The entry is out of scope, so we won't process it.
      return PluginResult.ImportLDIF.continueEntryProcessing();
    }

    // Make sure all configured attributes have clean values.
    for (AttributeType t : config.getAttributeType())
    {
      for (Attribute a : entry.getAllAttributes(t))
      {
        for (ByteString v : a)
        {
          if (!is7BitClean(v))
          {
            LocalizableMessage rejectMessage =
                 ERR_PLUGIN_7BIT_IMPORT_ATTR_NOT_CLEAN.get(a.getAttributeDescription());
            return PluginResult.ImportLDIF.stopEntryProcessing(rejectMessage);
          }
        }
      }
    }

    // If we've gotten here, then everything is acceptable.
    return PluginResult.ImportLDIF.continueEntryProcessing();
  }

  @Override
  public final PluginResult.PreParse
               doPreParse(PreParseAddOperation addOperation)
  {
    // Get the current configuration for this plugin.
    SevenBitCleanPluginCfg config = currentConfig;

    // If the entry is within the scope of this plugin, then make sure all
    // configured attributes have clean values.
    DN entryDN;
    try
    {
      entryDN = DN.valueOf(addOperation.getRawEntryDN());
    }
    catch (LocalizedIllegalArgumentException e)
    {
      return PluginResult.PreParse.stopProcessing(ResultCode.INVALID_DN_SYNTAX,
          ERR_PLUGIN_7BIT_CANNOT_DECODE_DN.get(e.getMessageObject()));
    }

    if (isInScope(config, entryDN))
    {
      for (RawAttribute rawAttr : addOperation.getRawAttributes())
      {
        Attribute a;
        try
        {
          a = rawAttr.toAttribute();
        }
        catch (LDAPException le)
        {
          return PluginResult.PreParse.stopProcessing(
              ResultCode.valueOf(le.getResultCode()),
              ERR_PLUGIN_7BIT_CANNOT_DECODE_ATTR.get(
                  rawAttr.getAttributeType(), le.getErrorMessage()));
        }

        if (!config.getAttributeType().contains(a.getAttributeDescription().getAttributeType()))
        {
          continue;
        }

        for (ByteString v : a)
        {
          if (!is7BitClean(v))
          {
            return PluginResult.PreParse.stopProcessing(
                ResultCode.CONSTRAINT_VIOLATION,
                ERR_PLUGIN_7BIT_MODIFYDN_ATTR_NOT_CLEAN.get(
                    rawAttr.getAttributeType()));
          }
        }
      }
    }

    // If we've gotten here, then everything is acceptable.
    return PluginResult.PreParse.continueOperationProcessing();
  }

  @Override
  public final PluginResult.PreParse
                    doPreParse(PreParseModifyOperation modifyOperation)
  {
    // Get the current configuration for this plugin.
    SevenBitCleanPluginCfg config = currentConfig;

    // If the target entry is within the scope of this plugin, then make sure
    // all values that will be added during the modification will be acceptable.
    DN entryDN;
    try
    {
      entryDN = DN.valueOf(modifyOperation.getRawEntryDN());
    }
    catch (LocalizedIllegalArgumentException e)
    {
      return PluginResult.PreParse.stopProcessing(ResultCode.INVALID_DN_SYNTAX,
          ERR_PLUGIN_7BIT_CANNOT_DECODE_DN.get(e.getMessageObject()));
    }

    if (isInScope(config, entryDN))
    {
      for (RawModification m : modifyOperation.getRawModifications())
      {
        switch (m.getModificationType().asEnum())
        {
          case ADD:
          case REPLACE:
            // These are modification types that we will process.
            break;
          default:
            // This is not a modification type that we will process.
            continue;
        }

        RawAttribute rawAttr = m.getAttribute();
        Attribute a;
        try
        {
          a = rawAttr.toAttribute();
        }
        catch (LDAPException le)
        {
          return PluginResult.PreParse.stopProcessing(
              ResultCode.valueOf(le.getResultCode()),
              ERR_PLUGIN_7BIT_CANNOT_DECODE_ATTR.get(
                  rawAttr.getAttributeType(), le.getErrorMessage()));
        }

        if (!config.getAttributeType().contains(a.getAttributeDescription().getAttributeType()))
        {
          continue;
        }

        for (ByteString v : a)
        {
          if (!is7BitClean(v))
          {
            return PluginResult.PreParse.stopProcessing(
                ResultCode.CONSTRAINT_VIOLATION,
                ERR_PLUGIN_7BIT_MODIFYDN_ATTR_NOT_CLEAN.get(
                    rawAttr.getAttributeType()));
          }
        }
      }
    }

    // If we've gotten here, then everything is acceptable.
    return PluginResult.PreParse.continueOperationProcessing();
  }

  @Override
  public final PluginResult.PreParse
                    doPreParse(PreParseModifyDNOperation modifyDNOperation)
  {
    // Get the current configuration for this plugin.
    SevenBitCleanPluginCfg config = currentConfig;

    // If the target entry is within the scope of this plugin, then make sure
    // all values that will be added during the modification will be acceptable.
    DN entryDN;
    try
    {
      entryDN = DN.valueOf(modifyDNOperation.getRawEntryDN());
    }
    catch (LocalizedIllegalArgumentException e)
    {
      return PluginResult.PreParse.stopProcessing(ResultCode.INVALID_DN_SYNTAX,
          ERR_PLUGIN_7BIT_CANNOT_DECODE_DN.get(e.getMessageObject()));
    }

    if (isInScope(config, entryDN))
    {
      ByteString rawNewRDN = modifyDNOperation.getRawNewRDN();

      RDN newRDN;
      try
      {
        newRDN = RDN.valueOf(rawNewRDN.toString());
      }
      catch (LocalizedIllegalArgumentException e)
      {
        return PluginResult.PreParse.stopProcessing(ResultCode.INVALID_DN_SYNTAX,
            ERR_PLUGIN_7BIT_CANNOT_DECODE_NEW_RDN.get(e.getMessageObject()));
      }

      for (AVA ava : newRDN)
      {
        if (!config.getAttributeType().contains(ava.getAttributeType()))
        {
          continue;
        }

        if (!is7BitClean(ava.getAttributeValue()))
        {
          return PluginResult.PreParse.stopProcessing(
              ResultCode.CONSTRAINT_VIOLATION,
              ERR_PLUGIN_7BIT_MODIFYDN_ATTR_NOT_CLEAN.get(ava.getAttributeName()));
        }
      }
    }

    // If we've gotten here, then everything is acceptable.
    return PluginResult.PreParse.continueOperationProcessing();
  }

  /**
   * Indicates whether the provided DN is within the scope of this plugin.
   *
   * @param  config  The configuration to use when making the determination.
   * @param  dn      The DN for which to make the determination.
   *
   * @return  {@code true} if the provided DN is within the scope of this
   *          plugin, or {@code false} if  not.
   */
  private final boolean isInScope(SevenBitCleanPluginCfg config, DN dn)
  {
    Set<DN> baseDNs = config.getBaseDN();
    if (baseDNs == null || baseDNs.isEmpty())
    {
      baseDNs = DirectoryServer.getInstance().getServerContext().getBackendConfigManager()
          .getNamingContexts(PUBLIC, TOP_LEVEL);
    }
    return isDescendantOfAny(dn, baseDNs);
  }

  private boolean isDescendantOfAny(DN dn, Set<DN> baseDNs)
  {
    if (baseDNs != null)
    {
      for (DN baseDN: baseDNs)
      {
        if (dn.isSubordinateOrEqualTo(baseDN))
        {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Indicates whether the provided value is 7-bit clean.
   *
   * @param  value  The value for which to make the determination.
   *
   * @return {@code true} if the provided value is 7-bit clean, or {@code false}
   *         if it is not.
   */
  private final boolean is7BitClean(ByteSequence value)
  {
    for (int i = 0; i < value.length(); i++)
    {
      byte b = value.byteAt(i);
      if ((b & MASK) != b)
      {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean isConfigurationAcceptable(PluginCfg configuration,
                                           List<LocalizableMessage> unacceptableReasons)
  {
    SevenBitCleanPluginCfg cfg = (SevenBitCleanPluginCfg) configuration;
    return isConfigurationChangeAcceptable(cfg, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
                      SevenBitCleanPluginCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    boolean configAcceptable = true;

    // Ensure that the set of plugin types is acceptable.
    for (PluginCfgDefn.PluginType pluginType : configuration.getPluginType())
    {
      switch (pluginType)
      {
        case LDIFIMPORT:
        case PREPARSEADD:
        case PREPARSEMODIFY:
        case PREPARSEMODIFYDN:
          // These are acceptable.
          break;

        default:
          unacceptableReasons.add(ERR_PLUGIN_7BIT_INVALID_PLUGIN_TYPE.get(pluginType));
          configAcceptable = false;
      }
    }

    return configAcceptable;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
                                 SevenBitCleanPluginCfg configuration)
  {
    currentConfig = configuration;
    return new ConfigChangeResult();
  }
}
