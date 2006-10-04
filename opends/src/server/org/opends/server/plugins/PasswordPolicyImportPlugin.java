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
package org.opends.server.plugins;



import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.LDIFPluginResult;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PasswordPolicy;
import org.opends.server.schema.AuthPasswordSyntax;
import org.opends.server.schema.UserPasswordSyntax;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.LDIFImportConfig;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.PluginMessages.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements a Directory Server plugin that performs various
 * password policy processing during an LDIF import.  In particular, it ensures
 * that all of the password values are properly encoded before they are stored.
 */
public final class PasswordPolicyImportPlugin
       extends DirectoryServerPlugin
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.plugins.PasswordPolicyImportPlugin";



  // The sets of password storage schemes for the auth password attributes.
  private final HashMap<AttributeType,PasswordStorageScheme[]>
                     authPasswordSchemes;

  // The sets of password storage schemes for the user password attributes.
  private final HashMap<AttributeType,PasswordStorageScheme[]>
                     userPasswordSchemes;



  /**
   * Creates a new instance of this Directory Server plugin.  Every plugin must
   * implement a default constructor (it is the only one that will be used to
   * create plugins defined in the configuration), and every plugin constructor
   * must call <CODE>super()</CODE> as its first element.
   */
  public PasswordPolicyImportPlugin()
  {
    super();

    assert debugConstructor(CLASS_NAME);


    // Get the password policies from the Directory Server configuration.  This
    // is done in the constructor to allow the instance variables to be declared
    // "final".
    authPasswordSchemes = new HashMap<AttributeType,PasswordStorageScheme[]>();
    userPasswordSchemes = new HashMap<AttributeType,PasswordStorageScheme[]>();
    for (PasswordPolicy p : DirectoryServer.getPasswordPolicies().values())
    {
      AttributeType t = p.getPasswordAttribute();
      if (p.usesAuthPasswordSyntax())
      {
        PasswordStorageScheme[] schemes = authPasswordSchemes.get(t);
        if (schemes == null)
        {
          CopyOnWriteArrayList<PasswordStorageScheme> defaultSchemes =
               p.getDefaultStorageSchemes();
          schemes = new PasswordStorageScheme[defaultSchemes.size()];
          defaultSchemes.toArray(schemes);
          authPasswordSchemes.put(t, schemes);
        }
        else
        {
          LinkedHashSet<PasswordStorageScheme> newSchemes =
               new LinkedHashSet<PasswordStorageScheme>();
          for (PasswordStorageScheme s : schemes)
          {
            newSchemes.add(s);
          }

          for (PasswordStorageScheme s : p.getDefaultStorageSchemes())
          {
            newSchemes.add(s);
          }

          schemes = new PasswordStorageScheme[newSchemes.size()];
          newSchemes.toArray(schemes);
          authPasswordSchemes.put(t, schemes);
        }
      }
      else
      {
        PasswordStorageScheme[] schemes = userPasswordSchemes.get(t);
        if (schemes == null)
        {
          CopyOnWriteArrayList<PasswordStorageScheme> defaultSchemes =
               p.getDefaultStorageSchemes();
          schemes = new PasswordStorageScheme[defaultSchemes.size()];
          defaultSchemes.toArray(schemes);
          userPasswordSchemes.put(t, schemes);
        }
        else
        {
          LinkedHashSet<PasswordStorageScheme> newSchemes =
               new LinkedHashSet<PasswordStorageScheme>();
          for (PasswordStorageScheme s : schemes)
          {
            newSchemes.add(s);
          }

          for (PasswordStorageScheme s : p.getDefaultStorageSchemes())
          {
            newSchemes.add(s);
          }

          schemes = new PasswordStorageScheme[newSchemes.size()];
          newSchemes.toArray(schemes);
          userPasswordSchemes.put(t, schemes);
        }
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void initializePlugin(Set<PluginType> pluginTypes,
                                     ConfigEntry configEntry)
         throws ConfigException
  {
    assert debugEnter(CLASS_NAME, "initializePlugin",
                      String.valueOf(pluginTypes),
                      String.valueOf(configEntry));


    // Make sure that the plugin has been enabled for the appropriate types.
    for (PluginType t : pluginTypes)
    {
      switch (t)
      {
        case LDIF_IMPORT:
          // This is the only acceptable type.
          break;


        default:
          int msgID = MSGID_PLUGIN_PWPIMPORT_INVALID_PLUGIN_TYPE;
          String message = getMessage(msgID, t.toString());
          throw new ConfigException(msgID, message);
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final LDIFPluginResult doLDIFImport(LDIFImportConfig importConfig,
                                             Entry entry)
  {
    assert debugEnter(CLASS_NAME, "doLDIFImport",
                      String.valueOf(importConfig), String.valueOf(entry));


    // Create a list that we will use to hold new encoded values.
    ArrayList<ByteString> encodedValueList = new ArrayList<ByteString>();


    // Iterate through the list of auth password attributes.  If any of them
    // are present and their values are not encoded, then encode them with all
    // appropriate schemes.
    for (AttributeType t : authPasswordSchemes.keySet())
    {
      List<Attribute> attrList = entry.getAttribute(t);
      if ((attrList == null) || attrList.isEmpty())
      {
        continue;
      }

      PasswordStorageScheme[] schemes = authPasswordSchemes.get(t);
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
              for (PasswordStorageScheme s : schemes)
              {
                encodedValueList.add(s.encodeAuthPassword(value));
              }

              iterator.remove();
            }
            catch (Exception e)
            {
              assert debugException(CLASS_NAME, "doLDIFImport", e);

              int    msgID   = MSGID_PLUGIN_PWPIMPORT_ERROR_ENCODING_PASSWORD;
              String message = getMessage(msgID, t.getNameOrOID(),
                                          String.valueOf(entry.getDN()),
                                          stackTraceToSingleLineString(e));
              logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                       message, msgID);

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
    for (AttributeType t : userPasswordSchemes.keySet())
    {
      List<Attribute> attrList = entry.getAttribute(t);
      if ((attrList == null) || attrList.isEmpty())
      {
        continue;
      }

      PasswordStorageScheme[] schemes = userPasswordSchemes.get(t);
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
              for (PasswordStorageScheme s : schemes)
              {
                encodedValueList.add(s.encodePasswordWithScheme(value));
              }

              iterator.remove();
            }
            catch (Exception e)
            {
              assert debugException(CLASS_NAME, "doLDIFImport", e);

              int    msgID   = MSGID_PLUGIN_PWPIMPORT_ERROR_ENCODING_PASSWORD;
              String message = getMessage(msgID, t.getNameOrOID(),
                                          String.valueOf(entry.getDN()),
                                          stackTraceToSingleLineString(e));
              logError(ErrorLogCategory.PLUGIN, ErrorLogSeverity.SEVERE_ERROR,
                       message, msgID);

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


    return new LDIFPluginResult();
  }
}

