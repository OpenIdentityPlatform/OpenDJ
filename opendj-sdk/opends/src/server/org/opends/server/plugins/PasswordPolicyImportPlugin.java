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
package org.opends.server.plugins;
import org.opends.messages.Message;



import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.PluginCfgDefn;
import org.opends.server.admin.std.server.PasswordPolicyImportPluginCfg;
import org.opends.server.admin.std.server.PluginCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.ImportTaskListener;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.LDIFPluginResult;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PasswordPolicy;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.schema.AuthPasswordSyntax;
import org.opends.server.schema.UserPasswordSyntax;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.ResultCode;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.messages.PluginMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements a Directory Server plugin that performs various
 * password policy processing during an LDIF import.  In particular, it ensures
 * that all of the password values are properly encoded before they are stored.
 */
public final class PasswordPolicyImportPlugin
       extends DirectoryServerPlugin<PasswordPolicyImportPluginCfg>
       implements ConfigurationChangeListener<PasswordPolicyImportPluginCfg>,
                  ImportTaskListener
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  // The attribute type used to specify the password policy for an entry.
  private AttributeType customPolicyAttribute;

  // The set of attribute types defined in the schema with the auth password
  // syntax.
  private AttributeType[] authPasswordTypes;

  // The set of attribute types defined in the schema with the user password
  // syntax.
  private AttributeType[] userPasswordTypes;

  // The set of password storage schemes to use for the various password
  // policies defined in the server.
  private HashMap<DN,PasswordStorageScheme[]> schemesByPolicy;

  // The default password storage schemes for auth password attributes.
  private PasswordStorageScheme[] defaultAuthPasswordSchemes;

  // The default password storage schemes for user password attributes.
  private PasswordStorageScheme[] defaultUserPasswordSchemes;



  /**
   * Creates a new instance of this Directory Server plugin.  Every plugin must
   * implement a default constructor (it is the only one that will be used to
   * create plugins defined in the configuration), and every plugin constructor
   * must call {@code super()} as its first element.
   */
  public PasswordPolicyImportPlugin()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void initializePlugin(Set<PluginType> pluginTypes,
                         PasswordPolicyImportPluginCfg configuration)
         throws ConfigException
  {
    configuration.addPasswordPolicyImportChangeListener(this);

    customPolicyAttribute =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_POLICY_DN, true);


    // Make sure that the plugin has been enabled for the appropriate types.
    for (PluginType t : pluginTypes)
    {
      switch (t)
      {
        case LDIF_IMPORT:
          // This is the only acceptable type.
          break;


        default:
          Message message =
              ERR_PLUGIN_PWPIMPORT_INVALID_PLUGIN_TYPE.get(t.toString());
          throw new ConfigException(message);
      }
    }


    // Get the set of default password storage schemes for auth password
    // attributes.
    PasswordPolicy defaultPolicy = DirectoryServer.getDefaultPasswordPolicy();
    Set<DN> authSchemeDNs =
         configuration.getDefaultAuthPasswordStorageSchemeDNs();
    if (authSchemeDNs.isEmpty())
    {
      if (defaultPolicy.usesAuthPasswordSyntax())
      {
        CopyOnWriteArrayList<PasswordStorageScheme> schemeList =
             defaultPolicy.getDefaultStorageSchemes();
        defaultAuthPasswordSchemes =
             new PasswordStorageScheme[schemeList.size()];
        schemeList.toArray(defaultAuthPasswordSchemes);
      }
      else
      {
        defaultAuthPasswordSchemes = new PasswordStorageScheme[1];
        defaultAuthPasswordSchemes[0] =
             DirectoryServer.getAuthPasswordStorageScheme(
                  AUTH_PASSWORD_SCHEME_NAME_SALTED_SHA_1);
        if (defaultAuthPasswordSchemes[0] == null)
        {
          Message message = ERR_PLUGIN_PWIMPORT_NO_DEFAULT_AUTH_SCHEMES.get(
              AUTH_PASSWORD_SCHEME_NAME_SALTED_SHA_1);
          throw new ConfigException(message);
        }
      }
    }
    else
    {
      defaultAuthPasswordSchemes =
           new PasswordStorageScheme[authSchemeDNs.size()];
      int i=0;
      for (DN schemeDN : authSchemeDNs)
      {
        defaultAuthPasswordSchemes[i] =
             DirectoryServer.getPasswordStorageScheme(schemeDN);
        if (defaultAuthPasswordSchemes[i] == null)
        {
          Message message =
              ERR_PLUGIN_PWIMPORT_NO_SUCH_DEFAULT_AUTH_SCHEME.get(
                   String.valueOf(schemeDN));
          throw new ConfigException(message);
        }
        else if (! defaultAuthPasswordSchemes[i].supportsAuthPasswordSyntax())
        {
          Message message =
              ERR_PLUGIN_PWIMPORT_INVALID_DEFAULT_AUTH_SCHEME.get(
                   String.valueOf(schemeDN));
          throw new ConfigException(message);
        }
        i++;
      }
    }


    // Get the set of default password storage schemes for user password
    // attributes.
    Set<DN> userSchemeDNs =
         configuration.getDefaultUserPasswordStorageSchemeDNs();
    if (userSchemeDNs.isEmpty())
    {
      if (! defaultPolicy.usesAuthPasswordSyntax())
      {
        CopyOnWriteArrayList<PasswordStorageScheme> schemeList =
             defaultPolicy.getDefaultStorageSchemes();
        defaultUserPasswordSchemes =
             new PasswordStorageScheme[schemeList.size()];
        schemeList.toArray(defaultUserPasswordSchemes);
      }
      else
      {
        defaultUserPasswordSchemes = new PasswordStorageScheme[1];
        defaultUserPasswordSchemes[0] =
             DirectoryServer.getPasswordStorageScheme(
                  toLowerCase(STORAGE_SCHEME_NAME_SALTED_SHA_1));
        if (defaultUserPasswordSchemes[0] == null)
        {
          Message message = ERR_PLUGIN_PWIMPORT_NO_DEFAULT_USER_SCHEMES.get(
              STORAGE_SCHEME_NAME_SALTED_SHA_1);
          throw new ConfigException(message);
        }
      }
    }
    else
    {
      defaultUserPasswordSchemes =
           new PasswordStorageScheme[userSchemeDNs.size()];
      int i=0;
      for (DN schemeDN : userSchemeDNs)
      {
        defaultUserPasswordSchemes[i] =
             DirectoryServer.getPasswordStorageScheme(schemeDN);
        if (defaultUserPasswordSchemes[i] == null)
        {
          Message message =
              ERR_PLUGIN_PWIMPORT_INVALID_DEFAULT_USER_SCHEME.get(
                   String.valueOf(schemeDN));
          throw new ConfigException(message);
        }
        i++;
      }
    }

    processImportBegin(null, null);
  }



  /**
   * {@inheritDoc}
   */
  public void processImportBegin(Backend backend, LDIFImportConfig config)
  {
    // Find the set of attribute types with the auth password and user password
    // syntax defined in the schema.
    HashSet<AttributeType> authPWTypes = new HashSet<AttributeType>();
    HashSet<AttributeType> userPWTypes = new HashSet<AttributeType>();
    for (AttributeType t : DirectoryServer.getAttributeTypes().values())
    {
      if (t.getSyntaxOID().equals(SYNTAX_AUTH_PASSWORD_OID))
      {
        authPWTypes.add(t);
      }
      else if (t.getSyntaxOID().equals(SYNTAX_USER_PASSWORD_OID))
      {
        userPWTypes.add(t);
      }
    }


    // Get the set of password policies defined in the server and get the
    // attribute types associated with them.
    HashMap<DN,PasswordStorageScheme[]> schemeMap =
         new HashMap<DN,PasswordStorageScheme[]>();
    for (PasswordPolicy p : DirectoryServer.getPasswordPolicies())
    {
      CopyOnWriteArrayList<PasswordStorageScheme> schemeList =
           p.getDefaultStorageSchemes();
      PasswordStorageScheme[] schemeArray =
           new PasswordStorageScheme[schemeList.size()];
      schemeList.toArray(schemeArray);
      schemeMap.put(p.getConfigEntryDN(), schemeArray);
    }


    AttributeType[] authTypesArray = new AttributeType[authPWTypes.size()];
    AttributeType[] userTypesArray = new AttributeType[userPWTypes.size()];
    authPWTypes.toArray(authTypesArray);
    userPWTypes.toArray(userTypesArray);

    schemesByPolicy   = schemeMap;
    authPasswordTypes = authTypesArray;
    userPasswordTypes = userTypesArray;
  }



  /**
   * {@inheritDoc}
   */
  public void processImportEnd(Backend backend, LDIFImportConfig config,
                               boolean successful)
  {
    // No implementation is required.
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final LDIFPluginResult doLDIFImport(LDIFImportConfig importConfig,
                                             Entry entry)
  {
    // Create a list that we will use to hold new encoded values.
    ArrayList<ByteString> encodedValueList = new ArrayList<ByteString>();


    // See if the entry explicitly states the password policy that it should
    //  use.  If so, then only use it to perform the encoding.
    List<Attribute> attrList = entry.getAttribute(customPolicyAttribute);
    if (attrList != null)
    {
      DN policyDN = null;
      PasswordPolicy policy = null;
policyLoop:
      for (Attribute a : attrList)
      {
        for (AttributeValue v : a.getValues())
        {
          try
          {
            policyDN = DN.decode(v.getValue());
            policy = DirectoryServer.getPasswordPolicy(policyDN);
            if (policy == null)
            {
              Message message = WARN_PLUGIN_PWIMPORT_NO_SUCH_POLICY.get(
                  String.valueOf(entry.getDN()), String.valueOf(policyDN));
              logError(message);
            }
            break policyLoop;
          }
          catch (DirectoryException de)
          {
            Message message = WARN_PLUGIN_PWIMPORT_CANNOT_DECODE_POLICY_DN.get(
                String.valueOf(entry.getDN()), de.getMessageObject());
            logError(message);
            break policyLoop;
          }
        }
      }

      if (policy != null)
      {
        PasswordStorageScheme[] schemes = schemesByPolicy.get(policyDN);
        if (schemes != null)
        {
          attrList = entry.getAttribute(policy.getPasswordAttribute());
          if (attrList == null)
          {
            return LDIFPluginResult.SUCCESS;
          }

          for (Attribute a : attrList)
          {
            encodedValueList.clear();

            LinkedHashSet<AttributeValue> values = a.getValues();
            Iterator<AttributeValue> iterator = values.iterator();
            while (iterator.hasNext())
            {
              AttributeValue v = iterator.next();
              ByteString value = v.getValue();

              if (policy.usesAuthPasswordSyntax())
              {
                if (! AuthPasswordSyntax.isEncoded(value))
                {
                  try
                  {
                    for (PasswordStorageScheme s : schemes)
                    {
                      encodedValueList.add(s.encodeAuthPassword(value));
                    }

                    iterator.remove();
                  }
                  catch (Exception e)
                  {
                    if (debugEnabled())
                    {
                      TRACER.debugCaught(DebugLogLevel.ERROR, e);
                    }

                    Message message =
                      ERR_PLUGIN_PWPIMPORT_ERROR_ENCODING_PASSWORD.
                          get(policy.getPasswordAttribute().getNameOrOID(),
                              String.valueOf(entry.getDN()),
                              stackTraceToSingleLineString(e));
                    logError(message);

                    encodedValueList.clear();
                    break;
                  }
                }
              }
              else
              {
                if (! UserPasswordSyntax.isEncoded(value))
                {
                  try
                  {
                    for (PasswordStorageScheme s : schemes)
                    {
                      encodedValueList.add(s.encodePasswordWithScheme(value));
                    }

                    iterator.remove();
                  }
                  catch (Exception e)
                  {
                    if (debugEnabled())
                    {
                      TRACER.debugCaught(DebugLogLevel.ERROR, e);
                    }

                    Message message =
                      ERR_PLUGIN_PWPIMPORT_ERROR_ENCODING_PASSWORD.
                          get(policy.getPasswordAttribute().getNameOrOID(),
                              String.valueOf(entry.getDN()),
                              stackTraceToSingleLineString(e));
                    logError(message);

                    encodedValueList.clear();
                    break;
                  }
                }
              }
            }

            for (ByteString s : encodedValueList)
            {
              values.add(new AttributeValue(policy.getPasswordAttribute(), s));
            }
          }

          return LDIFPluginResult.SUCCESS;
        }
      }
    }


    // Iterate through the list of auth password attributes.  If any of them
    // are present and their values are not encoded, then encode them with all
    // appropriate schemes.
    for (AttributeType t : authPasswordTypes)
    {
      attrList = entry.getAttribute(t);
      if ((attrList == null) || attrList.isEmpty())
      {
        continue;
      }

      for (Attribute a : attrList)
      {
        encodedValueList.clear();

        LinkedHashSet<AttributeValue> values = a.getValues();
        Iterator<AttributeValue> iterator = values.iterator();
        while (iterator.hasNext())
        {
          AttributeValue v = iterator.next();
          ByteString value = v.getValue();
          if (! AuthPasswordSyntax.isEncoded(value))
          {
            try
            {
              for (PasswordStorageScheme s : defaultAuthPasswordSchemes)
              {
                encodedValueList.add(s.encodeAuthPassword(value));
              }

              iterator.remove();
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }

              Message message = ERR_PLUGIN_PWPIMPORT_ERROR_ENCODING_PASSWORD.
                  get(t.getNameOrOID(), String.valueOf(entry.getDN()),
                      stackTraceToSingleLineString(e));
              logError(message);

              encodedValueList.clear();
              break;
            }
          }
        }

        for (ByteString s : encodedValueList)
        {
          values.add(new AttributeValue(t, s));
        }
      }
    }


    // Iterate through the list of user password attributes.  If any of them
    // are present and their values are not encoded, then encode them with all
    // appropriate schemes.
    for (AttributeType t : userPasswordTypes)
    {
      attrList = entry.getAttribute(t);
      if ((attrList == null) || attrList.isEmpty())
      {
        continue;
      }

      for (Attribute a : attrList)
      {
        encodedValueList.clear();

        LinkedHashSet<AttributeValue> values = a.getValues();
        Iterator<AttributeValue> iterator = values.iterator();
        while (iterator.hasNext())
        {
          AttributeValue v = iterator.next();
          ByteString value = v.getValue();
          if (! UserPasswordSyntax.isEncoded(value))
          {
            try
            {
              for (PasswordStorageScheme s : defaultUserPasswordSchemes)
              {
                encodedValueList.add(s.encodePasswordWithScheme(value));
              }

              iterator.remove();
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }

              Message message = ERR_PLUGIN_PWPIMPORT_ERROR_ENCODING_PASSWORD.
                  get(t.getNameOrOID(), String.valueOf(entry.getDN()),
                      stackTraceToSingleLineString(e));
              logError(message);

              encodedValueList.clear();
              break;
            }
          }
        }

        for (ByteString s : encodedValueList)
        {
          values.add(new AttributeValue(t, s));
        }
      }
    }


    return LDIFPluginResult.SUCCESS;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isConfigurationAcceptable(PluginCfg configuration,
                                           List<Message> unacceptableReasons)
  {
    PasswordPolicyImportPluginCfg config =
         (PasswordPolicyImportPluginCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      PasswordPolicyImportPluginCfg configuration,
                      List<Message> unacceptableReasons)
  {
    boolean configAcceptable = true;

    // Ensure that the set of plugin types contains only LDIF import.
    for (PluginCfgDefn.PluginType pluginType : configuration.getPluginType())
    {
      switch (pluginType)
      {
        case LDIFIMPORT:
          // This is the only acceptable type.
          break;


        default:
          Message message = ERR_PLUGIN_PWPIMPORT_INVALID_PLUGIN_TYPE.get(
                  pluginType.toString());
          unacceptableReasons.add(message);
          configAcceptable = false;
      }
    }


    // Get the set of default password storage schemes for auth password
    // attributes.
    Set<DN> authSchemeDNs =
         configuration.getDefaultAuthPasswordStorageSchemeDNs();
    if (authSchemeDNs.isEmpty())
    {
      PasswordStorageScheme[] defaultAuthSchemes = new PasswordStorageScheme[1];
      defaultAuthSchemes[0] =
           DirectoryServer.getAuthPasswordStorageScheme(
                AUTH_PASSWORD_SCHEME_NAME_SALTED_SHA_1);
      if (defaultAuthSchemes[0] == null)
      {
        Message message = ERR_PLUGIN_PWIMPORT_NO_DEFAULT_AUTH_SCHEMES.get(
                AUTH_PASSWORD_SCHEME_NAME_SALTED_SHA_1);
        unacceptableReasons.add(message);
        configAcceptable = false;
      }
    }
    else
    {
      PasswordStorageScheme[] defaultAuthSchemes =
           new PasswordStorageScheme[authSchemeDNs.size()];
      int i=0;
      for (DN schemeDN : authSchemeDNs)
      {
        defaultAuthSchemes[i] =
             DirectoryServer.getPasswordStorageScheme(schemeDN);
        if (defaultAuthSchemes[i] == null)
        {
          Message message =
              ERR_PLUGIN_PWIMPORT_NO_SUCH_DEFAULT_AUTH_SCHEME.get(
                   String.valueOf(schemeDN));
          unacceptableReasons.add(message);
          configAcceptable = false;
        }
        else if (! defaultAuthSchemes[i].supportsAuthPasswordSyntax())
        {
          Message message =
              ERR_PLUGIN_PWIMPORT_INVALID_DEFAULT_AUTH_SCHEME.get(
                   String.valueOf(schemeDN));
          unacceptableReasons.add(message);
          configAcceptable = false;
        }
        i++;
      }
    }


    // Get the set of default password storage schemes for user password
    // attributes.
    Set<DN> userSchemeDNs =
         configuration.getDefaultUserPasswordStorageSchemeDNs();
    if (userSchemeDNs.isEmpty())
    {
      PasswordStorageScheme[] defaultUserSchemes = new PasswordStorageScheme[1];
      defaultUserSchemes[0] =
           DirectoryServer.getPasswordStorageScheme(
                toLowerCase(STORAGE_SCHEME_NAME_SALTED_SHA_1));
      if (defaultUserSchemes[0] == null)
      {
        Message message = ERR_PLUGIN_PWIMPORT_NO_DEFAULT_USER_SCHEMES.get(
                STORAGE_SCHEME_NAME_SALTED_SHA_1);
        unacceptableReasons.add(message);
        configAcceptable = false;
      }
    }
    else
    {
      PasswordStorageScheme[] defaultUserSchemes =
           new PasswordStorageScheme[userSchemeDNs.size()];
      int i=0;
      for (DN schemeDN : userSchemeDNs)
      {
        defaultUserSchemes[i] =
             DirectoryServer.getPasswordStorageScheme(schemeDN);
        if (defaultUserSchemes[i] == null)
        {
          Message message = ERR_PLUGIN_PWIMPORT_INVALID_DEFAULT_USER_SCHEME.get(
                                 String.valueOf(schemeDN));
          unacceptableReasons.add(message);
          configAcceptable = false;
        }
        i++;
      }
    }


    return configAcceptable;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
                                 PasswordPolicyImportPluginCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();


    // Get the set of default password storage schemes for auth password
    // attributes.
    PasswordPolicy defaultPolicy = DirectoryServer.getDefaultPasswordPolicy();
    PasswordStorageScheme[] defaultAuthSchemes;
    Set<DN> authSchemeDNs =
         configuration.getDefaultAuthPasswordStorageSchemeDNs();
    if (authSchemeDNs.isEmpty())
    {
      if (defaultPolicy.usesAuthPasswordSyntax())
      {
        CopyOnWriteArrayList<PasswordStorageScheme> schemeList =
             defaultPolicy.getDefaultStorageSchemes();
        defaultAuthSchemes =
             new PasswordStorageScheme[schemeList.size()];
        schemeList.toArray(defaultAuthSchemes);
      }
      else
      {
        defaultAuthSchemes = new PasswordStorageScheme[1];
        defaultAuthSchemes[0] =
             DirectoryServer.getAuthPasswordStorageScheme(
                  AUTH_PASSWORD_SCHEME_NAME_SALTED_SHA_1);
        if (defaultAuthSchemes[0] == null)
        {
          resultCode = DirectoryServer.getServerErrorResultCode();

          messages.add(ERR_PLUGIN_PWIMPORT_NO_DEFAULT_AUTH_SCHEMES.get(
                  AUTH_PASSWORD_SCHEME_NAME_SALTED_SHA_1));
        }
      }
    }
    else
    {
      defaultAuthSchemes = new PasswordStorageScheme[authSchemeDNs.size()];
      int i=0;
      for (DN schemeDN : authSchemeDNs)
      {
        defaultAuthSchemes[i] =
             DirectoryServer.getPasswordStorageScheme(schemeDN);
        if (defaultAuthSchemes[i] == null)
        {
          resultCode = DirectoryServer.getServerErrorResultCode();

          messages.add(
               ERR_PLUGIN_PWIMPORT_NO_SUCH_DEFAULT_AUTH_SCHEME.get(
                    String.valueOf(schemeDN)));
        }
        else if (! defaultAuthSchemes[i].supportsAuthPasswordSyntax())
        {
          resultCode = DirectoryServer.getServerErrorResultCode();

          messages.add(
               ERR_PLUGIN_PWIMPORT_INVALID_DEFAULT_AUTH_SCHEME.get(
                    String.valueOf(schemeDN)));
        }
        i++;
      }
    }


    // Get the set of default password storage schemes for user password
    // attributes.
    PasswordStorageScheme[] defaultUserSchemes;
    Set<DN> userSchemeDNs =
         configuration.getDefaultUserPasswordStorageSchemeDNs();
    if (userSchemeDNs.isEmpty())
    {
      if (! defaultPolicy.usesAuthPasswordSyntax())
      {
        CopyOnWriteArrayList<PasswordStorageScheme> schemeList =
             defaultPolicy.getDefaultStorageSchemes();
        defaultUserSchemes =
             new PasswordStorageScheme[schemeList.size()];
        schemeList.toArray(defaultUserSchemes);
      }
      else
      {
        defaultUserSchemes = new PasswordStorageScheme[1];
        defaultUserSchemes[0] = DirectoryServer.getPasswordStorageScheme(
                  toLowerCase(STORAGE_SCHEME_NAME_SALTED_SHA_1));
        if (defaultUserSchemes[0] == null)
        {
          resultCode = DirectoryServer.getServerErrorResultCode();

          messages.add(ERR_PLUGIN_PWIMPORT_NO_DEFAULT_USER_SCHEMES.get(
                  STORAGE_SCHEME_NAME_SALTED_SHA_1));
        }
      }
    }
    else
    {
      defaultUserSchemes = new PasswordStorageScheme[userSchemeDNs.size()];
      int i=0;
      for (DN schemeDN : userSchemeDNs)
      {
        defaultUserSchemes[i] =
             DirectoryServer.getPasswordStorageScheme(schemeDN);
        if (defaultUserSchemes[i] == null)
        {
          resultCode = DirectoryServer.getServerErrorResultCode();

          messages.add(ERR_PLUGIN_PWIMPORT_INVALID_DEFAULT_USER_SCHEME.get(
                            String.valueOf(schemeDN)));
        }
        i++;
      }
    }

    if (resultCode == ResultCode.SUCCESS)
    {
      defaultAuthPasswordSchemes = defaultAuthSchemes;
      defaultUserPasswordSchemes = defaultUserSchemes;
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

