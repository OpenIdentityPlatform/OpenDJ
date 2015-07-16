/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS.
 */
package org.opends.server.plugins;

import static org.opends.messages.PluginMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.PluginCfgDefn;
import org.opends.server.admin.std.server.PasswordPolicyImportPluginCfg;
import org.opends.server.admin.std.server.PluginCfg;
import org.opends.server.api.AuthenticationPolicy;
import org.opends.server.api.Backend;
import org.opends.server.api.ImportTaskListener;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.api.plugin.PluginType;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PasswordPolicy;
import org.opends.server.core.SubentryPasswordPolicy;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.schema.AuthPasswordSyntax;
import org.opends.server.schema.UserPasswordSyntax;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ByteString;

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
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();



  /** The attribute type used to specify the password policy for an entry. */
  private AttributeType customPolicyAttribute;

  /**
   * The set of attribute types defined in the schema with the auth password
   * syntax.
   */
  private AttributeType[] authPasswordTypes;

  /**
   * The set of attribute types defined in the schema with the user password
   * syntax.
   */
  private AttributeType[] userPasswordTypes;

  /**
   * The set of password storage schemes to use for the various password
   * policies defined in the server.
   */
  private HashMap<DN,PasswordStorageScheme<?>[]> schemesByPolicy;

  /** The default password storage schemes for auth password attributes. */
  private PasswordStorageScheme<?>[] defaultAuthPasswordSchemes;

  /** The default password storage schemes for user password attributes. */
  private PasswordStorageScheme<?>[] defaultUserPasswordSchemes;



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



  /** {@inheritDoc} */
  @Override
  public final void initializePlugin(Set<PluginType> pluginTypes,
                         PasswordPolicyImportPluginCfg configuration)
         throws ConfigException
  {
    configuration.addPasswordPolicyImportChangeListener(this);

    customPolicyAttribute =
         DirectoryServer.getAttributeTypeOrDefault(OP_ATTR_PWPOLICY_POLICY_DN);


    // Make sure that the plugin has been enabled for the appropriate types.
    for (PluginType t : pluginTypes)
    {
      switch (t)
      {
        case LDIF_IMPORT:
          // This is the only acceptable type.
          break;

        default:
          throw new ConfigException(ERR_PLUGIN_PWPIMPORT_INVALID_PLUGIN_TYPE.get(t));
      }
    }


    // Get the set of default password storage schemes for auth password
    // attributes.
    PasswordPolicy defaultPolicy = DirectoryServer.getDefaultPasswordPolicy();
    Set<DN> authSchemeDNs =
         configuration.getDefaultAuthPasswordStorageSchemeDNs();
    if (authSchemeDNs.isEmpty())
    {
      if (defaultPolicy.isAuthPasswordSyntax())
      {
        List<PasswordStorageScheme<?>> schemeList =
             defaultPolicy.getDefaultPasswordStorageSchemes();
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
          LocalizableMessage message = ERR_PLUGIN_PWIMPORT_NO_DEFAULT_AUTH_SCHEMES.get(
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
          throw new ConfigException(ERR_PLUGIN_PWIMPORT_NO_SUCH_DEFAULT_AUTH_SCHEME.get(schemeDN));
        }
        else if (! defaultAuthPasswordSchemes[i].supportsAuthPasswordSyntax())
        {
          throw new ConfigException(ERR_PLUGIN_PWIMPORT_INVALID_DEFAULT_AUTH_SCHEME.get(schemeDN));
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
      if (! defaultPolicy.isAuthPasswordSyntax())
      {
        List<PasswordStorageScheme<?>> schemeList =
             defaultPolicy.getDefaultPasswordStorageSchemes();
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
          LocalizableMessage message = ERR_PLUGIN_PWIMPORT_NO_DEFAULT_USER_SCHEMES.get(
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
          throw new ConfigException(ERR_PLUGIN_PWIMPORT_INVALID_DEFAULT_USER_SCHEME.get(schemeDN));
        }
        i++;
      }
    }

    processImportBegin(null, null);
  }



  /** {@inheritDoc} */
  public void processImportBegin(Backend backend, LDIFImportConfig config)
  {
    // Find the set of attribute types with the auth password and user password
    // syntax defined in the schema.
    HashSet<AttributeType> authPWTypes = new HashSet<>();
    HashSet<AttributeType> userPWTypes = new HashSet<>();
    for (AttributeType t : DirectoryServer.getAttributeTypes().values())
    {
      if (t.getSyntax().getOID().equals(SYNTAX_AUTH_PASSWORD_OID))
      {
        authPWTypes.add(t);
      }
      else if (t.getSyntax().getOID().equals(SYNTAX_USER_PASSWORD_OID))
      {
        userPWTypes.add(t);
      }
    }


    // Get the set of password policies defined in the server and get the
    // attribute types associated with them.
    HashMap<DN,PasswordStorageScheme<?>[]> schemeMap = new HashMap<>();
    for (AuthenticationPolicy ap : DirectoryServer.getAuthenticationPolicies())
    {
      if (ap.isPasswordPolicy())
      {
        PasswordPolicy p = (PasswordPolicy) ap;

        List<PasswordStorageScheme<?>> schemeList = p.getDefaultPasswordStorageSchemes();
        PasswordStorageScheme<?>[] schemeArray =
          new PasswordStorageScheme[schemeList.size()];
        schemeList.toArray(schemeArray);
        schemeMap.put(p.getDN(), schemeArray);
      }
    }


    AttributeType[] authTypesArray = new AttributeType[authPWTypes.size()];
    AttributeType[] userTypesArray = new AttributeType[userPWTypes.size()];
    authPWTypes.toArray(authTypesArray);
    userPWTypes.toArray(userTypesArray);

    schemesByPolicy   = schemeMap;
    authPasswordTypes = authTypesArray;
    userPasswordTypes = userTypesArray;
  }



  /** {@inheritDoc} */
  public void processImportEnd(Backend backend, LDIFImportConfig config,
                               boolean successful)
  {
    // No implementation is required.
  }



  /** {@inheritDoc} */
  @Override
  public final PluginResult.ImportLDIF
               doLDIFImport(LDIFImportConfig importConfig, Entry entry)
  {
    // Check if this entry is a password policy subentry
    // and if so evaluate whether or not its acceptable.
    if ((entry.isSubentry() || entry.isLDAPSubentry()) &&
            entry.isPasswordPolicySubentry())
    {
      try
      {
        new SubentryPasswordPolicy(new SubEntry(entry));
      }
      catch (DirectoryException de)
      {
        logger.traceException(de);

        return PluginResult.ImportLDIF.stopEntryProcessing(
                de.getMessageObject());
      }
    }

    // See if the entry explicitly states the password policy that it should
    // use.  If so, then only use it to perform the encoding.
    List<Attribute> attrList = entry.getAttribute(customPolicyAttribute);
    if (attrList != null)
    {
      DN policyDN = null;
      PasswordPolicy policy = null;
policyLoop:
      for (Attribute a : attrList)
      {
        for (ByteString v : a)
        {
          try
          {
            policyDN = DN.decode(v);
            AuthenticationPolicy authPolicy = DirectoryServer
                .getAuthenticationPolicy(policyDN);
            if (authPolicy == null)
            {
              logger.warn(WARN_PLUGIN_PWIMPORT_NO_SUCH_POLICY, entry.getName(), policyDN);
            }
            else if (authPolicy.isPasswordPolicy())
            {
              policy = (PasswordPolicy) authPolicy;
            }

            break policyLoop;
          }
          catch (DirectoryException de)
          {
            logger.warn(WARN_PLUGIN_PWIMPORT_CANNOT_DECODE_POLICY_DN, entry.getName(), de.getMessageObject());
            break policyLoop;
          }
        }
      }

      if (policy != null)
      {
        PasswordStorageScheme<?>[] schemes = schemesByPolicy.get(policyDN);
        if (schemes != null)
        {
          attrList = entry.getAttribute(policy.getPasswordAttribute());
          if (attrList == null)
          {
            return PluginResult.ImportLDIF.continueEntryProcessing();
          }

          for (Attribute a : attrList)
          {
            AttributeBuilder builder = new AttributeBuilder(a, true);
            boolean gotError = false;

            for (ByteString value : a)
            {
              if (policy.isAuthPasswordSyntax())
              {
                if (!AuthPasswordSyntax.isEncoded(value))
                {
                  try
                  {
                    for (PasswordStorageScheme<?> s : schemes)
                    {
                      builder.add(s.encodeAuthPassword(value));
                    }
                  }
                  catch (Exception e)
                  {
                    logger.traceException(e);

                    logger.error(ERR_PLUGIN_PWPIMPORT_ERROR_ENCODING_PASSWORD,
                        policy.getPasswordAttribute().getNameOrOID(), entry.getName(),
                        stackTraceToSingleLineString(e));
                    gotError = true;
                    break;
                  }
                }
                else
                {
                  builder.add(value);
                }
              }
              else
              {
                if (!UserPasswordSyntax.isEncoded(value))
                {
                  try
                  {
                    for (PasswordStorageScheme<?> s : schemes)
                    {
                      builder.add(s.encodePasswordWithScheme(value));
                    }
                  }
                  catch (Exception e)
                  {
                    logger.traceException(e);

                    logger.error(ERR_PLUGIN_PWPIMPORT_ERROR_ENCODING_PASSWORD,
                        policy.getPasswordAttribute().getNameOrOID(), entry.getName(),
                        stackTraceToSingleLineString(e));
                    gotError = true;
                    break;
                  }
                }
                else
                {
                  builder.add(value);
                }
              }
            }

            if (!gotError)
            {
              entry.replaceAttribute(builder.toAttribute());
            }
          }

          return PluginResult.ImportLDIF.continueEntryProcessing();
        }
      }
    }


    // Iterate through the list of auth password attributes.  If any of them
    // are present and their values are not encoded, then encode them with all
    // appropriate schemes.
    for (AttributeType t : authPasswordTypes)
    {
      attrList = entry.getAttribute(t);
      if (attrList == null || attrList.isEmpty())
      {
        continue;
      }

      for (Attribute a : attrList)
      {
        AttributeBuilder builder = new AttributeBuilder(a, true);
        boolean gotError = false;

        for (ByteString value : a)
        {
          if (!AuthPasswordSyntax.isEncoded(value))
          {
            try
            {
              for (PasswordStorageScheme<?> s : defaultAuthPasswordSchemes)
              {
                builder.add(s.encodeAuthPassword(value));
              }
            }
            catch (Exception e)
            {
              logger.traceException(e);
              logger.error(ERR_PLUGIN_PWPIMPORT_ERROR_ENCODING_PASSWORD,
                  t.getNameOrOID(), entry.getName(), stackTraceToSingleLineString(e));
              gotError = true;
              break;
            }
          }
          else
          {
            builder.add(value);
          }
        }

        if (!gotError)
        {
          entry.replaceAttribute(builder.toAttribute());
        }
      }
    }


    // Iterate through the list of user password attributes.  If any of them
    // are present and their values are not encoded, then encode them with all
    // appropriate schemes.
    for (AttributeType t : userPasswordTypes)
    {
      attrList = entry.getAttribute(t);
      if (attrList == null || attrList.isEmpty())
      {
        continue;
      }

      for (Attribute a : attrList)
      {
        AttributeBuilder builder = new AttributeBuilder(a, true);
        boolean gotError = false;

        for (ByteString value : a)
        {
          if (!UserPasswordSyntax.isEncoded(value))
          {
            try
            {
              for (PasswordStorageScheme<?> s : defaultUserPasswordSchemes)
              {
                builder.add(s.encodePasswordWithScheme(value));
              }
            }
            catch (Exception e)
            {
              logger.traceException(e);
              logger.error(ERR_PLUGIN_PWPIMPORT_ERROR_ENCODING_PASSWORD,
                  t.getNameOrOID(), entry.getName(), stackTraceToSingleLineString(e));
              gotError = true;
              break;
            }
          }
          else
          {
            builder.add(value);
          }
        }

        if (!gotError)
        {
          entry.replaceAttribute(builder.toAttribute());
        }
      }
    }


    return PluginResult.ImportLDIF.continueEntryProcessing();
  }



  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAcceptable(PluginCfg configuration,
                                           List<LocalizableMessage> unacceptableReasons)
  {
    PasswordPolicyImportPluginCfg config =
         (PasswordPolicyImportPluginCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /** {@inheritDoc} */
  public boolean isConfigurationChangeAcceptable(
                      PasswordPolicyImportPluginCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
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
          unacceptableReasons.add(ERR_PLUGIN_PWPIMPORT_INVALID_PLUGIN_TYPE.get(pluginType));
          configAcceptable = false;
      }
    }


    // Get the set of default password storage schemes for auth password
    // attributes.
    Set<DN> authSchemeDNs =
         configuration.getDefaultAuthPasswordStorageSchemeDNs();
    if (authSchemeDNs.isEmpty())
    {
      PasswordStorageScheme<?>[] defaultAuthSchemes =
        new PasswordStorageScheme[1];
      defaultAuthSchemes[0] =
           DirectoryServer.getAuthPasswordStorageScheme(
                AUTH_PASSWORD_SCHEME_NAME_SALTED_SHA_1);
      if (defaultAuthSchemes[0] == null)
      {
        LocalizableMessage message = ERR_PLUGIN_PWIMPORT_NO_DEFAULT_AUTH_SCHEMES.get(
                AUTH_PASSWORD_SCHEME_NAME_SALTED_SHA_1);
        unacceptableReasons.add(message);
        configAcceptable = false;
      }
    }
    else
    {
      PasswordStorageScheme<?>[] defaultAuthSchemes =
           new PasswordStorageScheme[authSchemeDNs.size()];
      int i=0;
      for (DN schemeDN : authSchemeDNs)
      {
        defaultAuthSchemes[i] =
             DirectoryServer.getPasswordStorageScheme(schemeDN);
        if (defaultAuthSchemes[i] == null)
        {
          unacceptableReasons.add(ERR_PLUGIN_PWIMPORT_NO_SUCH_DEFAULT_AUTH_SCHEME.get(schemeDN));
          configAcceptable = false;
        }
        else if (! defaultAuthSchemes[i].supportsAuthPasswordSyntax())
        {
          unacceptableReasons.add(ERR_PLUGIN_PWIMPORT_INVALID_DEFAULT_AUTH_SCHEME.get(schemeDN));
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
      PasswordStorageScheme<?>[] defaultUserSchemes =
        new PasswordStorageScheme[1];
      defaultUserSchemes[0] =
           DirectoryServer.getPasswordStorageScheme(
                toLowerCase(STORAGE_SCHEME_NAME_SALTED_SHA_1));
      if (defaultUserSchemes[0] == null)
      {
        LocalizableMessage message = ERR_PLUGIN_PWIMPORT_NO_DEFAULT_USER_SCHEMES.get(
                STORAGE_SCHEME_NAME_SALTED_SHA_1);
        unacceptableReasons.add(message);
        configAcceptable = false;
      }
    }
    else
    {
      PasswordStorageScheme<?>[] defaultUserSchemes =
           new PasswordStorageScheme[userSchemeDNs.size()];
      int i=0;
      for (DN schemeDN : userSchemeDNs)
      {
        defaultUserSchemes[i] =
             DirectoryServer.getPasswordStorageScheme(schemeDN);
        if (defaultUserSchemes[i] == null)
        {
          unacceptableReasons.add(ERR_PLUGIN_PWIMPORT_INVALID_DEFAULT_USER_SCHEME.get(schemeDN));
          configAcceptable = false;
        }
        i++;
      }
    }


    return configAcceptable;
  }



  /** {@inheritDoc} */
  public ConfigChangeResult applyConfigurationChange(
                                 PasswordPolicyImportPluginCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    // Get the set of default password storage schemes for auth password
    // attributes.
    PasswordPolicy defaultPolicy = DirectoryServer.getDefaultPasswordPolicy();
    PasswordStorageScheme<?>[] defaultAuthSchemes;
    Set<DN> authSchemeDNs =
         configuration.getDefaultAuthPasswordStorageSchemeDNs();
    if (authSchemeDNs.isEmpty())
    {
      if (defaultPolicy.isAuthPasswordSyntax())
      {
        List<PasswordStorageScheme<?>> schemeList =
             defaultPolicy.getDefaultPasswordStorageSchemes();
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
          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
          ccr.addMessage(ERR_PLUGIN_PWIMPORT_NO_DEFAULT_AUTH_SCHEMES.get(
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
          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
          ccr.addMessage(ERR_PLUGIN_PWIMPORT_NO_SUCH_DEFAULT_AUTH_SCHEME.get(schemeDN));
        }
        else if (! defaultAuthSchemes[i].supportsAuthPasswordSyntax())
        {
          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
          ccr.addMessage(ERR_PLUGIN_PWIMPORT_INVALID_DEFAULT_AUTH_SCHEME.get(schemeDN));
        }
        i++;
      }
    }


    // Get the set of default password storage schemes for user password
    // attributes.
    PasswordStorageScheme<?>[] defaultUserSchemes;
    Set<DN> userSchemeDNs =
         configuration.getDefaultUserPasswordStorageSchemeDNs();
    if (userSchemeDNs.isEmpty())
    {
      if (! defaultPolicy.isAuthPasswordSyntax())
      {
        List<PasswordStorageScheme<?>> schemeList =
             defaultPolicy.getDefaultPasswordStorageSchemes();
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
          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
          ccr.addMessage(ERR_PLUGIN_PWIMPORT_NO_DEFAULT_USER_SCHEMES.get(
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
          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
          ccr.addMessage(ERR_PLUGIN_PWIMPORT_INVALID_DEFAULT_USER_SCHEME.get(schemeDN));
        }
        i++;
      }
    }

    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      defaultAuthPasswordSchemes = defaultAuthSchemes;
      defaultUserPasswordSchemes = defaultUserSchemes;
    }

    return ccr;
  }
}
