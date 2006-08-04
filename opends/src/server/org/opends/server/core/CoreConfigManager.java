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
package org.opends.server.core;



import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.IdentityMapper;
import org.opends.server.config.BooleanConfigAttribute;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.DNConfigAttribute;
import org.opends.server.config.IntegerConfigAttribute;
import org.opends.server.config.IntegerWithUnitConfigAttribute;
import org.opends.server.config.MultiChoiceConfigAttribute;
import org.opends.server.types.AcceptRejectWarn;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ResultCode;
import org.opends.server.types.WritabilityMode;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines a utility that will be used to manage the configuration
 * for the core Directory Server.  It will perform the necessary initialization
 * of these settings when the server is first started, and then will manage any
 * changes to them while the server is running.
 */
public class CoreConfigManager
       implements ConfigurableComponent
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.core.CoreConfigManager";



  /**
   * The set of time units that will be used for the appropriate attributes.
   */
  private static final LinkedHashMap<String,Double> timeUnits =
       new LinkedHashMap<String,Double>();



  // The DN of the associated configuration entry.
  private DN configEntryDN;



  static
  {
    timeUnits.put(TIME_UNIT_SECONDS_ABBR, 1D);
    timeUnits.put(TIME_UNIT_SECONDS_FULL, 1D);
    timeUnits.put(TIME_UNIT_MINUTES_ABBR, 60D);
    timeUnits.put(TIME_UNIT_MINUTES_FULL, 60D);
    timeUnits.put(TIME_UNIT_HOURS_ABBR, (double) (60 * 60));
    timeUnits.put(TIME_UNIT_HOURS_FULL, (double) (60 * 60));
    timeUnits.put(TIME_UNIT_DAYS_ABBR, (double) (60 * 60 * 24));
    timeUnits.put(TIME_UNIT_DAYS_FULL, (double) (60 * 60 * 24));
    timeUnits.put(TIME_UNIT_WEEKS_ABBR, (double) (60 * 60 * 24 * 7));
    timeUnits.put(TIME_UNIT_WEEKS_FULL, (double) (60 * 60 * 24 * 7));
  }



  /**
   * Creates a new instance of this core config manager.
   */
  public CoreConfigManager()
  {
    assert debugConstructor(CLASS_NAME);

    // No implementation is required.
  }



  /**
   * Initializes the configuration attributes associated with the Directory
   * Server core.  This should only be called at Directory Server startup.
   *
   * @throws  ConfigException  If a critical configuration problem prevents the
   *                           coreinitialization from succeeding.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the core server that is not related to
   *                                   the configuration.
   */
  public void initializeCoreConfig()
         throws ConfigException, InitializationException
  {
    assert debugEnter(CLASS_NAME, "initializeCoreConfig");


    // Get the configuration root entry, since all the attributes we care about
    // should be contained in it.
    ConfigEntry configRoot =
         DirectoryServer.getConfigHandler().getConfigRootEntry();
    configEntryDN = configRoot.getDN();


    // Determine whether to perform schema checking.  By default, it will be
    // enabled.
    boolean checkSchema = true;

    int msgID = MSGID_CONFIG_CORE_DESCRIPTION_CHECK_SCHEMA;
    BooleanConfigAttribute checkSchemaStub =
         new BooleanConfigAttribute(ATTR_CHECK_SCHEMA, getMessage(msgID),
                                    false);
    try
    {
      BooleanConfigAttribute checkSchemaAttr =
           (BooleanConfigAttribute)
           configRoot.getConfigAttribute(checkSchemaStub);
      if (checkSchemaAttr == null)
      {
        // This is fine -- just use the default value.
        DirectoryServer.setCheckSchema(checkSchema);
      }
      else
      {
        // Use the value from the attribute that we read.
        DirectoryServer.setCheckSchema(checkSchemaAttr.pendingValue());
      }
    }
    catch (Exception e)
    {
      // An error occurred, but this should not be considered fatal.  Log an
      // error message and use the default.
      assert debugException(CLASS_NAME, "initializeCoreConfig", e);

      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_CONFIG_CORE_INVALID_CHECK_SCHEMA,
               String.valueOf(configRoot.getDN()), String.valueOf(e));

      DirectoryServer.setCheckSchema(checkSchema);
    }



    // Determine whether to allow attribute name exceptions.  By default, we
    // will not.
    boolean allowAttributeNameExceptions = false;

    msgID = MSGID_CONFIG_CORE_DESCRIPTION_ALLOW_ATTR_EXCEPTIONS;
    BooleanConfigAttribute allowExceptionsStub =
         new BooleanConfigAttribute(ATTR_ALLOW_ATTRIBUTE_NAME_EXCEPTIONS,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute allowExceptionsAttr =
           (BooleanConfigAttribute)
           configRoot.getConfigAttribute(allowExceptionsStub);
      if (allowExceptionsAttr == null)
      {
        // This is fine -- just use the default value.
        DirectoryServer.setAllowAttributeNameExceptions(
                             allowAttributeNameExceptions);
      }
      else
      {
        // Use the value from the attribute that we read.
        DirectoryServer.setAllowAttributeNameExceptions(
             allowExceptionsAttr.pendingValue());
      }
    }
    catch (Exception e)
    {
      // An error occurred, but this should not be considered fatal.  Log an
      // error message and use the default.
      assert debugException(CLASS_NAME, "initializeCoreConfig", e);

      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_CONFIG_CORE_INVALID_ALLOW_EXCEPTIONS,
               String.valueOf(configRoot.getDN()), String.valueOf(e));

      DirectoryServer.setAllowAttributeNameExceptions(
                           allowAttributeNameExceptions);
    }


    // Determine whether to automatically add missing RDN attributes for add
    // operations.
    boolean addMissingRDNAttributes = false;
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_ADD_MISSING_RDN_ATTRS;
    BooleanConfigAttribute addRDNStub =
         new BooleanConfigAttribute(ATTR_ADD_MISSING_RDN_ATTRS,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute addRDNAttr =
           (BooleanConfigAttribute) configRoot.getConfigAttribute(addRDNStub);
      if (addRDNAttr == null)
      {
        // This is fine -- just use the default value.
        DirectoryServer.setAddMissingRDNAttributes(addMissingRDNAttributes);
      }
      else
      {
        // Use the value from the attribute we read.
        DirectoryServer.setAddMissingRDNAttributes(addRDNAttr.pendingValue());
      }
    }
    catch (Exception e)
    {
      // An error occurred, but this should not be considered fatal.  Log an
      // error message and use the default.
      assert debugException(CLASS_NAME, "initializeCoreConfig", e);

      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_CONFIG_CORE_INVALID_ADD_MISSING_RDN_ATTRS,
               String.valueOf(configRoot.getDN()), String.valueOf(e));

      DirectoryServer.setAddMissingRDNAttributes(addMissingRDNAttributes);
    }


    // Determine the result code to use for internal server errors.
    ResultCode serverErrorResultCode = ResultCode.OTHER;
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_SERVER_ERROR_RESULT_CODE;
    IntegerConfigAttribute serverErrorStub =
         new IntegerConfigAttribute(ATTR_SERVER_ERROR_RESULT_CODE,
                                    getMessage(msgID), false, false, false,
                                    true, 1, false, 0);
    try
    {
      IntegerConfigAttribute serverErrorAttr =
           (IntegerConfigAttribute)
           configRoot.getConfigAttribute(serverErrorStub);
      if (serverErrorAttr == null)
      {
        // This is fine -- just use the default value.
        DirectoryServer.setServerErrorResultCode(serverErrorResultCode);
      }
      else
      {
        // Get the integer value from the provided attribute and then convert
        // that to a result code.
        int resultCodeValue = serverErrorAttr.pendingIntValue();
        serverErrorResultCode = ResultCode.valueOf(resultCodeValue);
        DirectoryServer.setServerErrorResultCode(serverErrorResultCode);
      }
    }
    catch (Exception e)
    {
      // An error occurred, but this should not be considered fatal.  Log an
      // error message and use the default.
      assert debugException(CLASS_NAME, "initializeCoreConfig", e);

      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_CONFIG_CORE_INVALID_SERVER_ERROR_RESULT_CODE,
               String.valueOf(configRoot.getDN()), String.valueOf(e));

      DirectoryServer.setServerErrorResultCode(serverErrorResultCode);
    }


    // Determine how to handle attributes that do not conform to the associated
    // syntax.  By default, we will log a warning but the value will be
    // accepted.
    AcceptRejectWarn syntaxEnforcementPolicy = AcceptRejectWarn.WARN;
    HashSet<String> syntaxBehaviorValues = new HashSet<String>(3);
    syntaxBehaviorValues.add(AcceptRejectWarn.ACCEPT.toString());
    syntaxBehaviorValues.add(AcceptRejectWarn.REJECT.toString());
    syntaxBehaviorValues.add(AcceptRejectWarn.WARN.toString());

    msgID = MSGID_CONFIG_CORE_DESCRIPTION_INVALID_SYNTAX_BEHAVIOR;
    MultiChoiceConfigAttribute syntaxPolicyStub =
         new MultiChoiceConfigAttribute(ATTR_INVALID_SYNTAX_BEHAVIOR,
                                        getMessage(msgID), true, false, false,
                                        syntaxBehaviorValues);
    try
    {
      MultiChoiceConfigAttribute syntaxPolicyAttr =
           (MultiChoiceConfigAttribute)
           configRoot.getConfigAttribute(syntaxPolicyStub);
      if (syntaxPolicyAttr == null)
      {
        // This is fine -- just use the default value.
        DirectoryServer.setSyntaxEnforcementPolicy(syntaxEnforcementPolicy);
      }
      else
      {
        // Use the value from the attribute that we read.
        syntaxEnforcementPolicy =
             AcceptRejectWarn.policyForName(syntaxPolicyAttr.pendingValue());
        if (syntaxEnforcementPolicy == null)
        {
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_ERROR,
                   MSGID_CONFIG_CORE_INVALID_ENFORCE_STRICT_SYNTAX,
                   String.valueOf(configRoot.getDN()),
                   String.valueOf(syntaxPolicyAttr.pendingValue()));

          syntaxEnforcementPolicy = AcceptRejectWarn.WARN;
        }

        DirectoryServer.setSyntaxEnforcementPolicy(syntaxEnforcementPolicy);
      }
    }
    catch (Exception e)
    {
      // An error occurred, but this should not be considered fatal.  Log an
      // error message and use the default.
      assert debugException(CLASS_NAME, "initializeCoreConfig", e);

      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_CONFIG_CORE_INVALID_ENFORCE_STRICT_SYNTAX,
               String.valueOf(configRoot.getDN()), String.valueOf(e));

      DirectoryServer.setSyntaxEnforcementPolicy(syntaxEnforcementPolicy);
    }


    // Determine how to handle entries that do not have exactly one structural
    // objectclass.  By default, we will log a warning but the entry will be
    // accepted.
    AcceptRejectWarn structuralClassPolicy = AcceptRejectWarn.WARN;
    HashSet<String> structuralClassValues = new HashSet<String>(3);
    structuralClassValues.add(AcceptRejectWarn.ACCEPT.toString());
    structuralClassValues.add(AcceptRejectWarn.REJECT.toString());
    structuralClassValues.add(AcceptRejectWarn.WARN.toString());

    msgID = MSGID_CONFIG_CORE_DESCRIPTION_STRUCTURAL_CLASS_BEHAVIOR;
    MultiChoiceConfigAttribute structuralClassStub =
         new MultiChoiceConfigAttribute(ATTR_SINGLE_STRUCTURAL_CLASS_BEHAVIOR,
                                        getMessage(msgID), true, false, false,
                                        structuralClassValues);
    try
    {
      MultiChoiceConfigAttribute structuralClassAttr =
           (MultiChoiceConfigAttribute)
           configRoot.getConfigAttribute(structuralClassStub);
      if (structuralClassAttr == null)
      {
        // This is fine -- just use the default value.
        DirectoryServer.setSingleStructuralObjectClassPolicy(
                             structuralClassPolicy);
      }
      else
      {
        // Use the value from the attribute that we read.
        structuralClassPolicy =
             AcceptRejectWarn.policyForName(structuralClassAttr.pendingValue());
        if (structuralClassPolicy == null)
        {
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_ERROR,
                   MSGID_CONFIG_CORE_INVALID_STRUCTURAL_CLASS_BEHAVIOR,
                   String.valueOf(configRoot.getDN()),
                   String.valueOf(structuralClassAttr.pendingValue()));

          structuralClassPolicy = AcceptRejectWarn.WARN;
        }

        DirectoryServer.setSingleStructuralObjectClassPolicy(
                             structuralClassPolicy);
      }
    }
    catch (Exception e)
    {
      // An error occurred, but this should not be considered fatal.  Log an
      // error message and use the default.
      assert debugException(CLASS_NAME, "initializeCoreConfig", e);

      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_CONFIG_CORE_INVALID_STRUCTURAL_CLASS_BEHAVIOR,
               String.valueOf(configRoot.getDN()), String.valueOf(e));

      DirectoryServer.setSingleStructuralObjectClassPolicy(
                           structuralClassPolicy);
    }


    // Determine the maximum number of client connections that should be allowed
    // at any time.
    long maxAllowedConnections = -1;

    msgID = MSGID_CONFIG_CORE_DESCRIPTION_MAX_ALLOWED_CONNECTIONS;
    IntegerConfigAttribute maxConnsStub =
         new IntegerConfigAttribute(ATTR_MAX_ALLOWED_CONNS, getMessage(msgID),
                                    true, false, false, true, -1, false, 0);
    try
    {
      IntegerConfigAttribute maxConnsAttr =
           (IntegerConfigAttribute) configRoot.getConfigAttribute(maxConnsStub);
      if (maxConnsAttr == null)
      {
        // This is fine -- just use the default value.
        DirectoryServer.setMaxAllowedConnections(maxAllowedConnections);
      }
      else
      {
        // Use the value from the attribute that we read.
        DirectoryServer.setMaxAllowedConnections(maxConnsAttr.activeValue());
      }
    }
    catch (Exception e)
    {
      // An error occurred, but this should not be considered fatal.  Log an
      // error message and use the default.
      assert debugException(CLASS_NAME, "initializeCoreConfig", e);

      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_CONFIG_CORE_INVALID_MAX_ALLOWED_CONNECTIONS,
               configRoot.getDN(), String.valueOf(e));

      DirectoryServer.setMaxAllowedConnections(maxAllowedConnections);
    }


    // Determine whether to send a response to operations that have been
    // abandoned.
    boolean notifyAbandonedOperations = false;

    msgID = MSGID_CONFIG_CORE_DESCRIPTION_NOTIFY_ABANDONED_OPERATIONS;
    BooleanConfigAttribute notifyAbandonedStub =
         new BooleanConfigAttribute(ATTR_NOTIFY_ABANDONED_OPS,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute notifyAbandonedAttr =
           (BooleanConfigAttribute)
           configRoot.getConfigAttribute(notifyAbandonedStub);
      if (notifyAbandonedAttr == null)
      {
        // This is fine -- just use the default value.
        DirectoryServer.setNotifyAbandonedOperations(notifyAbandonedOperations);
      }
      else
      {
        // Use the value from the attribute that we read.
        DirectoryServer.setNotifyAbandonedOperations(
                             notifyAbandonedAttr.activeValue());
      }
    }
    catch (Exception e)
    {
      // An error occurred, but this should not be considered fatal.  Log an
      // error message and use the default.
      assert debugException(CLASS_NAME, "initializeCoreConfig", e);

      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_CONFIG_CORE_INVALID_NOTIFY_ABANDONED_OPERATIONS,
               configRoot.getDN(), String.valueOf(e));

      DirectoryServer.setNotifyAbandonedOperations(notifyAbandonedOperations);
    }


    // Determine the DN of the proxied authorization identity mapper.
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_PROXY_MAPPER_DN;
    DNConfigAttribute proxyMapperStub =
         new DNConfigAttribute(ATTR_PROXY_MAPPER_DN, getMessage(msgID), false,
                               false, false);
    try
    {
      DNConfigAttribute proxyMapperAttr =
           (DNConfigAttribute) configRoot.getConfigAttribute(proxyMapperStub);
      if (proxyMapperAttr == null)
      {
        // This is fine -- we just won't use a mapper.
        DirectoryServer.setProxiedAuthorizationIdentityMapperDN(null);
      }
      else
      {
        // Set the identity mapper DN without any verification.  We can't check
        // whether it's valid at this point because the identity mappers haven't
        // been initialized yet.
        DirectoryServer.setProxiedAuthorizationIdentityMapperDN(
             proxyMapperAttr.activeValue());
      }
    }
    catch (Exception e)
    {
      // An error occurred, but this should not be considered fatal.  Log an
      // error message and use the default.
      assert debugException(CLASS_NAME, "initializeCoreConfig", e);

      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_CONFIG_CORE_INVALID_PROXY_MAPPER_DN, configRoot.getDN(),
               String.valueOf(e));

      DirectoryServer.setProxiedAuthorizationIdentityMapperDN(null);
    }


    // Determine the default server size limit.
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_SIZE_LIMIT;
    IntegerConfigAttribute sizeLimitStub =
         new IntegerConfigAttribute(ATTR_SIZE_LIMIT, getMessage(msgID), true,
                                    false, false, true, 0, true,
                                    Integer.MAX_VALUE);
    try
    {
      IntegerConfigAttribute sizeLimitAttr =
           (IntegerConfigAttribute)
           configRoot.getConfigAttribute(sizeLimitStub);
      if (sizeLimitAttr == null)
      {
        DirectoryServer.setSizeLimit(DEFAULT_SIZE_LIMIT);
      }
      else
      {
        DirectoryServer.setSizeLimit(sizeLimitAttr.activeIntValue());
      }
    }
    catch (Exception e)
    {
      // An error occurred, but this should not be considered fatal.  Log an
      // error message and use the default.
      assert debugException(CLASS_NAME, "initializeCoreConfig", e);

      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_CONFIG_CORE_INVALID_SIZE_LIMIT, configRoot.getDN(),
               String.valueOf(e));

      DirectoryServer.setSizeLimit(DEFAULT_SIZE_LIMIT);
    }


    // Determine the default server time limit.
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_TIME_LIMIT;
    IntegerWithUnitConfigAttribute timeLimitStub =
         new IntegerWithUnitConfigAttribute(ATTR_TIME_LIMIT, getMessage(msgID),
                                            false, timeUnits, true, 0, true,
                                            Integer.MAX_VALUE);
    try
    {
      IntegerWithUnitConfigAttribute timeLimitAttr =
           (IntegerWithUnitConfigAttribute)
           configRoot.getConfigAttribute(timeLimitStub);
      if (timeLimitAttr == null)
      {
        DirectoryServer.setTimeLimit(DEFAULT_TIME_LIMIT);
      }
      else
      {
        DirectoryServer.setTimeLimit(
             (int) timeLimitAttr.activeCalculatedValue());
      }
    }
    catch (Exception e)
    {
      // An error occurred, but this should not be considered fatal.  Log an
      // error message and use the default.
      assert debugException(CLASS_NAME, "initializeCoreConfig", e);

      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_CONFIG_CORE_INVALID_TIME_LIMIT, configRoot.getDN(),
               String.valueOf(e));

      DirectoryServer.setTimeLimit(DEFAULT_TIME_LIMIT);
    }


    // Determine the writability mode for the Directory Server.  By default, it
    // will be writable.
    WritabilityMode writabilityMode = WritabilityMode.ENABLED;
    HashSet<String> writabilityModes = new HashSet<String>(3);
    writabilityModes.add(WritabilityMode.ENABLED.toString());
    writabilityModes.add(WritabilityMode.DISABLED.toString());
    writabilityModes.add(WritabilityMode.INTERNAL_ONLY.toString());

    msgID = MSGID_CONFIG_CORE_DESCRIPTION_WRITABILITY_MODE;
    MultiChoiceConfigAttribute writabilityStub =
         new MultiChoiceConfigAttribute(ATTR_WRITABILITY_MODE,
                                        getMessage(msgID), true, false, false,
                                        writabilityModes);
    try
    {
      MultiChoiceConfigAttribute writabilityAttr =
           (MultiChoiceConfigAttribute)
           configRoot.getConfigAttribute(writabilityStub);
      if (writabilityAttr == null)
      {
        // This is fine -- just use the default value.
        DirectoryServer.setWritabilityMode(writabilityMode);
      }
      else
      {
        // Use the value from the attribute that we read.
        writabilityMode =
             WritabilityMode.modeForName(writabilityAttr.pendingValue());
        if (writabilityMode == null)
        {
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_ERROR,
                   MSGID_CONFIG_CORE_INVALID_WRITABILITY_MODE,
                   String.valueOf(configRoot.getDN()),
                   String.valueOf(writabilityAttr.pendingValue()));

          writabilityMode = WritabilityMode.DISABLED;
        }

        DirectoryServer.setWritabilityMode(writabilityMode);
      }
    }
    catch (Exception e)
    {
      // An error occurred, but this should not be considered fatal.  Log an
      // error message and use the default.
      assert debugException(CLASS_NAME, "initializeCoreConfig", e);

      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_CONFIG_CORE_INVALID_WRITABILITY_MODE,
               String.valueOf(configRoot.getDN()), String.valueOf(e));

      DirectoryServer.setWritabilityMode(writabilityMode);
    }


    // Determine whether simple binds with a DN will also be required to have a
    // password.
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_BIND_WITH_DN_REQUIRES_PW;
    BooleanConfigAttribute requirePWStub =
         new BooleanConfigAttribute(ATTR_BIND_WITH_DN_REQUIRES_PW,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute requirePWAttr =
           (BooleanConfigAttribute)
           configRoot.getConfigAttribute(requirePWStub);
      if (requirePWAttr == null)
      {
        DirectoryServer.setBindWithDNRequiresPassword(
                             DEFAULT_BIND_WITH_DN_REQUIRES_PW);
      }
      else
      {
        DirectoryServer.setBindWithDNRequiresPassword(
                             requirePWAttr.activeValue());
      }
    }
    catch (Exception e)
    {
      // An error occurred, but this should not be considered fatal.  Log an
      // error message and use the default.
      assert debugException(CLASS_NAME, "initializeCoreConfig", e);

      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_CONFIG_CORE_INVALID_BIND_WITH_DN_REQUIRES_PW,
               String.valueOf(configRoot.getDN()), String.valueOf(e));

      DirectoryServer.setBindWithDNRequiresPassword(
                           DEFAULT_BIND_WITH_DN_REQUIRES_PW);
    }


    // Get the DN of the default password policy configuration entry.  We can't
    // check its validity yet because the password policies will not have been
    // loaded from the configuration.  Instead, the validity check will occur
    // when the password policies are loaded.
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_DEFAULT_PWPOLICY_DN;
    DNConfigAttribute defaultPWPolicyStub =
         new DNConfigAttribute(ATTR_DEFAULT_PWPOLICY_DN, getMessage(msgID),
                               true, false, false);
    try
    {
      DNConfigAttribute defaultPWPolicyAttr =
           (DNConfigAttribute)
           configRoot.getConfigAttribute(defaultPWPolicyStub);
      if (defaultPWPolicyAttr == null)
      {
        msgID = MSGID_CONFIG_CORE_NO_DEFAULT_PWPOLICY;
        String message = getMessage(msgID, configRoot.getDN());
        throw new ConfigException(msgID, message);
      }
      else
      {
        DirectoryServer.setDefaultPasswordPolicyDN(
                             defaultPWPolicyAttr.activeValue());
      }
    }
    catch (ConfigException ce)
    {
      throw ce;
    }
    catch (Exception e)
    {
      // An error occurred, but this should not be considered fatal.  Log an
      // error message and use the default.
      assert debugException(CLASS_NAME, "initializeCoreConfig", e);

      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_CONFIG_CORE_INVALID_DEFAULT_PWPOLICY_DN,
               String.valueOf(configRoot.getDN()), String.valueOf(e));

      DirectoryServer.setBindWithDNRequiresPassword(
                           DEFAULT_BIND_WITH_DN_REQUIRES_PW);
    }


    DirectoryServer.registerConfigurableComponent(this);
  }



  /**
   * Retrieves the DN of the configuration entry with which this component is
   * associated.
   *
   * @return  The DN of the configuration entry with which this component is
   *          associated.
   */
  public DN getConfigurableComponentEntryDN()
  {
    assert debugEnter(CLASS_NAME, "getConfigurableComponentEntryDN");

    return configEntryDN;
  }



  /**
   * Retrieves the set of configuration attributes that are associated with this
   * configurable component.
   *
   * @return  The set of configuration attributes that are associated with this
   *          configurable component.
   */
  public List<ConfigAttribute> getConfigurationAttributes()
  {
    assert debugEnter(CLASS_NAME, "getConfigurationAttributes");

    LinkedList<ConfigAttribute> attrs = new LinkedList<ConfigAttribute>();

    ConfigEntry configEntry;
    try
    {
      configEntry =
           DirectoryServer.getConfigHandler().getConfigEntry(configEntryDN);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "getConfigurationAttributes", e);

      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.MILD_ERROR,
               MSGID_CONFIG_CANNOT_GET_CONFIG_ENTRY, configEntryDN.toString(),
               String.valueOf(e));
      return attrs;
    }


    // See if the entry indicates whether to perform schema checking.
    int msgID = MSGID_CONFIG_CORE_DESCRIPTION_CHECK_SCHEMA;
    BooleanConfigAttribute checkSchemaStub =
         new BooleanConfigAttribute(ATTR_CHECK_SCHEMA, getMessage(msgID),
                                    false);
    try
    {
      BooleanConfigAttribute checkSchemaAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(checkSchemaStub);
      if (checkSchemaAttr == null)
      {
        checkSchemaStub.setValue(DirectoryServer.checkSchema());
        checkSchemaAttr = checkSchemaStub;
      }

      attrs.add(checkSchemaAttr);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "getConfigurationAttributes", e);

      // An error occurred.  Log a message and continue.
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.MILD_ERROR,
               MSGID_CONFIG_CORE_INVALID_CHECK_SCHEMA,
               configEntryDN.toString(), String.valueOf(e));
    }


    // See if the entry indicates whether to allow attribute name exceptions.
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_ALLOW_ATTR_EXCEPTIONS;
    BooleanConfigAttribute allowExceptionsStub =
         new BooleanConfigAttribute(ATTR_ALLOW_ATTRIBUTE_NAME_EXCEPTIONS,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute allowExceptionsAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(allowExceptionsStub);
      if (allowExceptionsAttr == null)
      {
        allowExceptionsStub.setValue(
             DirectoryServer.allowAttributeNameExceptions());
        allowExceptionsAttr = allowExceptionsStub;
      }

      attrs.add(allowExceptionsAttr);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "getConfigurationAttributes", e);

      // An error occurred.  Log a message and continue.
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.MILD_ERROR,
               MSGID_CONFIG_CORE_INVALID_ALLOW_EXCEPTIONS,
               configEntryDN.toString(), String.valueOf(e));
    }


    // See if the entry indicates whether to add missing RDN attributes.
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_ADD_MISSING_RDN_ATTRS;
    BooleanConfigAttribute addRDNStub =
         new BooleanConfigAttribute(ATTR_ADD_MISSING_RDN_ATTRS,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute addRDNAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(addRDNStub);
      if (addRDNAttr == null)
      {
        addRDNStub.setValue(DirectoryServer.addMissingRDNAttributes());
        addRDNAttr = allowExceptionsStub;
      }

      attrs.add(addRDNAttr);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "getConfigurationAttributes", e);

      // An error occurred.  Log a message and continue.
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.MILD_ERROR,
               MSGID_CONFIG_CORE_INVALID_ADD_MISSING_RDN_ATTRS,
               configEntryDN.toString(), String.valueOf(e));
    }


    // See if the entry specifies the result code to use for internal server
    // errors.
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_SERVER_ERROR_RESULT_CODE;
    IntegerConfigAttribute serverErrorStub =
         new IntegerConfigAttribute(ATTR_SERVER_ERROR_RESULT_CODE,
                                    getMessage(msgID), false, false, false,
                                    true, 1, false, 0);
    try
    {
      IntegerConfigAttribute serverErrorAttr =
           (IntegerConfigAttribute)
           configEntry.getConfigAttribute(serverErrorStub);
      if (serverErrorAttr == null)
      {
        int value = DirectoryServer.getServerErrorResultCode().getIntValue();
        serverErrorStub.setValue(value);
        serverErrorAttr = serverErrorStub;
      }

      attrs.add(serverErrorAttr);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "getConfigurationAttributes", e);

      // An error occurred.  Log a message and continue.
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.MILD_ERROR,
               MSGID_CONFIG_CORE_INVALID_SERVER_ERROR_RESULT_CODE,
               configEntryDN.toString(), String.valueOf(e));
    }


    // See if the entry indicates how to handle syntax enforcement.
    HashSet<String> syntaxBehaviorValues = new HashSet<String>(3);
    syntaxBehaviorValues.add(AcceptRejectWarn.ACCEPT.toString());
    syntaxBehaviorValues.add(AcceptRejectWarn.REJECT.toString());
    syntaxBehaviorValues.add(AcceptRejectWarn.WARN.toString());

    msgID = MSGID_CONFIG_CORE_DESCRIPTION_INVALID_SYNTAX_BEHAVIOR;
    MultiChoiceConfigAttribute syntaxPolicyStub =
         new MultiChoiceConfigAttribute(ATTR_INVALID_SYNTAX_BEHAVIOR,
                                        getMessage(msgID), true, false, false,
                                        syntaxBehaviorValues);
    try
    {
      MultiChoiceConfigAttribute syntaxPolicyAttr =
           (MultiChoiceConfigAttribute)
           configEntry.getConfigAttribute(syntaxPolicyStub);
      if (syntaxPolicyAttr == null)
      {
        syntaxPolicyStub.setValue(
             String.valueOf(DirectoryServer.getSyntaxEnforcementPolicy()));
        syntaxPolicyAttr = syntaxPolicyStub;
      }

      attrs.add(syntaxPolicyAttr);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "getConfigurationAttributes", e);

      // An error occurred.  Log a message and continue.
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.MILD_ERROR,
               MSGID_CONFIG_CORE_INVALID_ENFORCE_STRICT_SYNTAX,
               configEntryDN.toString(), String.valueOf(e));
    }


    // See if the entry indicates how to handle entries with an invalid number
    // of structural objectclasses.
    HashSet<String> structuralClassValues = new HashSet<String>(3);
    structuralClassValues.add(AcceptRejectWarn.ACCEPT.toString());
    structuralClassValues.add(AcceptRejectWarn.REJECT.toString());
    structuralClassValues.add(AcceptRejectWarn.WARN.toString());

    msgID = MSGID_CONFIG_CORE_DESCRIPTION_STRUCTURAL_CLASS_BEHAVIOR;
    MultiChoiceConfigAttribute structuralClassStub =
         new MultiChoiceConfigAttribute(ATTR_SINGLE_STRUCTURAL_CLASS_BEHAVIOR,
                                        getMessage(msgID), true, false, false,
                                        structuralClassValues);
    try
    {
      MultiChoiceConfigAttribute structuralClassAttr =
           (MultiChoiceConfigAttribute)
           configEntry.getConfigAttribute(structuralClassStub);
      if (structuralClassAttr == null)
      {
        structuralClassStub.setValue(String.valueOf(
             DirectoryServer.getSingleStructuralObjectClassPolicy()));
        structuralClassAttr = structuralClassStub;
      }

      attrs.add(structuralClassAttr);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "getConfigurationAttributes", e);

      // An error occurred.  Log a message and continue.
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.MILD_ERROR,
               MSGID_CONFIG_CORE_INVALID_STRUCTURAL_CLASS_BEHAVIOR,
               configEntryDN.toString(), String.valueOf(e));
    }


    // Determine the maximum number of client connections to allow at any given
    // time.
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_MAX_ALLOWED_CONNECTIONS;
    IntegerConfigAttribute maxConnsStub =
         new IntegerConfigAttribute(ATTR_MAX_ALLOWED_CONNS, getMessage(msgID),
                                    true, false, false, true, -1, false, 0);
    try
    {
      IntegerConfigAttribute maxConnsAttr =
           (IntegerConfigAttribute)
           configEntry.getConfigAttribute(maxConnsStub);
      if (maxConnsAttr == null)
      {
        maxConnsStub.setValue(DirectoryServer.getMaxAllowedConnections());
        maxConnsAttr = maxConnsStub;
      }

      attrs.add(maxConnsAttr);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "getConfigurationAttributes", e);

      // An error occurred.  Log a message and continue.
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.MILD_ERROR,
               MSGID_CONFIG_CORE_INVALID_MAX_ALLOWED_CONNECTIONS,
               configEntryDN.toString(), String.valueOf(e));
    }


    // Determine whether to send a response to abandoned operations.
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_NOTIFY_ABANDONED_OPERATIONS;
    BooleanConfigAttribute notifyAbandonedStub =
         new BooleanConfigAttribute(ATTR_NOTIFY_ABANDONED_OPS,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute notifyAbandonedAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(notifyAbandonedStub);
      if (notifyAbandonedAttr == null)
      {
        notifyAbandonedStub.setValue(
             DirectoryServer.notifyAbandonedOperations());
        notifyAbandonedAttr = notifyAbandonedStub;
      }

      attrs.add(notifyAbandonedAttr);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "getConfigurationAttributes", e);

      // An error occurred.  Log a message and continue.
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.MILD_ERROR,
               MSGID_CONFIG_CORE_INVALID_NOTIFY_ABANDONED_OPERATIONS,
               configEntryDN.toString(), String.valueOf(e));
    }


    // Add the DN of the proxied auth identity mapper config entry.
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_PROXY_MAPPER_DN;
    DNConfigAttribute proxyMapperStub =
         new DNConfigAttribute(ATTR_PROXY_MAPPER_DN, getMessage(msgID), false,
                               false, false);
    try
    {
      DNConfigAttribute proxyMapperAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(proxyMapperStub);
      if (proxyMapperAttr == null)
      {
        proxyMapperStub.setValue(
             DirectoryServer.getProxiedAuthorizationIdentityMapperDN());
        proxyMapperAttr = proxyMapperStub;
      }

      attrs.add(proxyMapperAttr);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "getConfigurationAttributes", e);

      // An error occurred.  Log a message and continue.
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.MILD_ERROR,
               MSGID_CONFIG_CORE_INVALID_PROXY_MAPPER_DN,
               configEntryDN.toString(), String.valueOf(e));
    }


    // Add the server size limit.
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_SIZE_LIMIT;
    IntegerConfigAttribute sizeLimitStub =
         new IntegerConfigAttribute(ATTR_SIZE_LIMIT, getMessage(msgID), false,
                                    false, false, true, 0, true,
                                    Integer.MAX_VALUE);
    try
    {
      IntegerConfigAttribute sizeLimitAttr =
           (IntegerConfigAttribute)
           configEntry.getConfigAttribute(sizeLimitStub);
      if (sizeLimitAttr == null)
      {
        sizeLimitStub.setValue(DirectoryServer.getSizeLimit());
        sizeLimitAttr = sizeLimitStub;
      }

      attrs.add(sizeLimitAttr);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "getConfigurationAttributes", e);

      // An error occurred.  Log a message and continue.
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.MILD_ERROR,
               MSGID_CONFIG_CORE_INVALID_SIZE_LIMIT,
               configEntryDN.toString(), String.valueOf(e));
    }


    // Add the server time limit.
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_TIME_LIMIT;
    IntegerWithUnitConfigAttribute timeLimitStub =
         new IntegerWithUnitConfigAttribute(ATTR_TIME_LIMIT, getMessage(msgID),
                                            false, timeUnits, true, 0, true,
                                            Integer.MAX_VALUE);
    try
    {
      IntegerWithUnitConfigAttribute timeLimitAttr =
           (IntegerWithUnitConfigAttribute)
           configEntry.getConfigAttribute(timeLimitStub);
      if (timeLimitAttr == null)
      {
        timeLimitStub.setValue(DirectoryServer.getTimeLimit(),
                               TIME_UNIT_SECONDS_FULL);
        timeLimitAttr = timeLimitStub;
      }

      attrs.add(timeLimitAttr);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "getConfigurationAttributes", e);

      // An error occurred.  Log a message and continue.
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.MILD_ERROR,
               MSGID_CONFIG_CORE_INVALID_TIME_LIMIT,
               configEntryDN.toString(), String.valueOf(e));
    }


    // Add the server writability mode.
    HashSet<String> writabilityModes = new HashSet<String>(3);
    writabilityModes.add(WritabilityMode.ENABLED.toString());
    writabilityModes.add(WritabilityMode.DISABLED.toString());
    writabilityModes.add(WritabilityMode.INTERNAL_ONLY.toString());

    msgID = MSGID_CONFIG_CORE_DESCRIPTION_WRITABILITY_MODE;
    MultiChoiceConfigAttribute writabilityStub =
         new MultiChoiceConfigAttribute(ATTR_WRITABILITY_MODE,
                                        getMessage(msgID), true, false, false,
                                        writabilityModes);
    try
    {
      MultiChoiceConfigAttribute writabilityAttr =
           (MultiChoiceConfigAttribute)
           configEntry.getConfigAttribute(writabilityStub);
      if (writabilityAttr == null)
      {
        writabilityStub.setValue(String.valueOf(
             DirectoryServer.getWritabilityMode()));
        writabilityAttr = writabilityStub;
      }

      attrs.add(writabilityAttr);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "getConfigurationAttributes", e);

      // An error occurred.  Log a message and continue.
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.MILD_ERROR,
               MSGID_CONFIG_CORE_INVALID_WRITABILITY_MODE,
               configEntryDN.toString(), String.valueOf(e));
    }


    // Add the bind with DN requires password attribute.
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_BIND_WITH_DN_REQUIRES_PW;
    BooleanConfigAttribute requirePWStub =
         new BooleanConfigAttribute(ATTR_BIND_WITH_DN_REQUIRES_PW,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute requirePWAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(requirePWStub);
      if (requirePWAttr == null)
      {
        requirePWStub.setValue(DirectoryServer.bindWithDNRequiresPassword());
        requirePWAttr = requirePWStub;
      }

      attrs.add(requirePWAttr);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "getConfigurationAttributes", e);

      // An error occurred.  Log a message and continue.
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.MILD_ERROR,
               MSGID_CONFIG_CORE_INVALID_BIND_WITH_DN_REQUIRES_PW,
               configEntryDN.toString(), String.valueOf(e));
    }


    // Add the default password policy configuration entry DN.
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_DEFAULT_PWPOLICY_DN;
    DNConfigAttribute defaultPWPolicyStub =
         new DNConfigAttribute(ATTR_DEFAULT_PWPOLICY_DN, getMessage(msgID),
                               true, false, false);
    try
    {
      DNConfigAttribute defaultPWPolicyAttr =
           (DNConfigAttribute)
           configEntry.getConfigAttribute(defaultPWPolicyStub);
      if (defaultPWPolicyAttr == null)
      {
        defaultPWPolicyStub.setValue(
             DirectoryServer.getDefaultPasswordPolicyDN());
        defaultPWPolicyAttr = defaultPWPolicyStub;
      }

      attrs.add(defaultPWPolicyAttr);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "getConfigurationAttributes", e);

      // An error occurred.  Log a message and continue.
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.MILD_ERROR,
               MSGID_CONFIG_CORE_INVALID_DEFAULT_PWPOLICY_DN,
               configEntryDN.toString(), String.valueOf(e));
    }


    return attrs;
  }



  /**
   * Indicates whether the provided configuration entry has an acceptable
   * configuration for this component.  If it does not, then detailed
   * information about the problem(s) should be added to the provided list.
   *
   * @param  configEntry          The configuration entry for which to make the
   *                              determination.
   * @param  unacceptableReasons  A list that can be used to hold messages about
   *                              why the provided entry does not have an
   *                              acceptable configuration.
   *
   * @return  <CODE>true</CODE> if the provided entry has an acceptable
   *          configuration for this component, or <CODE>false</CODE> if not.
   */
  public boolean hasAcceptableConfiguration(ConfigEntry configEntry,
                                            List<String> unacceptableReasons)
  {
    assert debugEnter(CLASS_NAME, "hasAcceptableConfiguration",
                      String.valueOf(configEntry),
                      String.valueOf(unacceptableReasons));


    // Start out assuming that the configuration is valid.
    boolean configIsAcceptable = true;


    // See if the entry indicates whether to allow perform schema checking.  If
    // so, then make sure that its value is acceptable.
    int msgID = MSGID_CONFIG_CORE_DESCRIPTION_CHECK_SCHEMA;
    BooleanConfigAttribute checkSchemaStub =
         new BooleanConfigAttribute(ATTR_CHECK_SCHEMA, getMessage(msgID),
                                    false);
    try
    {
      BooleanConfigAttribute checkSchemaAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(checkSchemaStub);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_CONFIG_CORE_INVALID_CHECK_SCHEMA;
      unacceptableReasons.add(getMessage(msgID, configEntry.getDN().toString(),
                                         String.valueOf(e)));
      configIsAcceptable = false;
    }


    // See if the entry indicates whether to allow attribute name exceptions.
    // If so, then make sure that its value is acceptable.
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_ALLOW_ATTR_EXCEPTIONS;
    BooleanConfigAttribute allowExceptionsStub =
         new BooleanConfigAttribute(ATTR_ALLOW_ATTRIBUTE_NAME_EXCEPTIONS,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute allowExceptionsAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(allowExceptionsStub);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_CONFIG_CORE_INVALID_ALLOW_EXCEPTIONS;
      unacceptableReasons.add(getMessage(msgID, configEntry.getDN().toString(),
                                         String.valueOf(e)));
      configIsAcceptable = false;
    }


    // See if the entry indicates whether to add missing RDN attributes.  If so,
    // then make sure that its value is acceptable.
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_ADD_MISSING_RDN_ATTRS;
    BooleanConfigAttribute addRDNStub =
         new BooleanConfigAttribute(ATTR_ADD_MISSING_RDN_ATTRS,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute addRDNAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(addRDNStub);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_CONFIG_CORE_INVALID_ADD_MISSING_RDN_ATTRS;
      unacceptableReasons.add(getMessage(msgID, configEntry.getDN().toString(),
                                         String.valueOf(e)));
      configIsAcceptable = false;
    }


    // See if the entry specifies the result code for internal server errors.
    // If so, then make sure that its value is acceptable.
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_SERVER_ERROR_RESULT_CODE;
    IntegerConfigAttribute serverErrorStub =
         new IntegerConfigAttribute(ATTR_SERVER_ERROR_RESULT_CODE,
                                    getMessage(msgID), false, false, false,
                                    true, 1, false, 0);
    try
    {
      IntegerConfigAttribute serverErrorAttr =
           (IntegerConfigAttribute)
           configEntry.getConfigAttribute(serverErrorStub);
      if (serverErrorAttr != null)
      {
        serverErrorAttr.pendingIntValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_CONFIG_CORE_INVALID_SERVER_ERROR_RESULT_CODE;
      unacceptableReasons.add(getMessage(msgID, configEntry.getDN().toString(),
                                         String.valueOf(e)));
      configIsAcceptable = false;
    }


    // See if the entry contains a policy to indicate how to handle syntax
    // enforcement.  If so, then make sure that its value is acceptable.
    HashSet<String> syntaxBehaviorValues = new HashSet<String>(3);
    syntaxBehaviorValues.add(AcceptRejectWarn.ACCEPT.toString());
    syntaxBehaviorValues.add(AcceptRejectWarn.REJECT.toString());
    syntaxBehaviorValues.add(AcceptRejectWarn.WARN.toString());

    msgID = MSGID_CONFIG_CORE_DESCRIPTION_INVALID_SYNTAX_BEHAVIOR;
    MultiChoiceConfigAttribute syntaxPolicyStub =
         new MultiChoiceConfigAttribute(ATTR_INVALID_SYNTAX_BEHAVIOR,
                                        getMessage(msgID), true, false, false,
                                        syntaxBehaviorValues);
    try
    {
      MultiChoiceConfigAttribute syntaxPolicyAttr =
           (MultiChoiceConfigAttribute)
           configEntry.getConfigAttribute(syntaxPolicyStub);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_CONFIG_CORE_INVALID_ENFORCE_STRICT_SYNTAX;
      unacceptableReasons.add(getMessage(msgID, configEntry.getDN().toString(),
                                         String.valueOf(e)));
      configIsAcceptable = false;
    }


    // See if the entry contains a policy to indicate how to handle single
    // structural class enforcement.  If so, then make sure that its value is
    // acceptable.
    HashSet<String> structuralClassValues = new HashSet<String>(3);
    structuralClassValues.add(AcceptRejectWarn.ACCEPT.toString());
    structuralClassValues.add(AcceptRejectWarn.REJECT.toString());
    structuralClassValues.add(AcceptRejectWarn.WARN.toString());

    msgID = MSGID_CONFIG_CORE_DESCRIPTION_STRUCTURAL_CLASS_BEHAVIOR;
    MultiChoiceConfigAttribute structuralClassStub =
         new MultiChoiceConfigAttribute(ATTR_SINGLE_STRUCTURAL_CLASS_BEHAVIOR,
                                        getMessage(msgID), true, false, false,
                                        structuralClassValues);
    try
    {
      MultiChoiceConfigAttribute structuralClassAttr =
           (MultiChoiceConfigAttribute)
           configEntry.getConfigAttribute(structuralClassStub);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_CONFIG_CORE_INVALID_STRUCTURAL_CLASS_BEHAVIOR;
      unacceptableReasons.add(getMessage(msgID, configEntry.getDN().toString(),
                                         String.valueOf(e)));
      configIsAcceptable = false;
    }


    // See if the entry contains a value for the maximum number of allowed
    // client connections.  If so, then make sure that its value is acceptable.
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_MAX_ALLOWED_CONNECTIONS;
    IntegerConfigAttribute maxConnsStub =
         new IntegerConfigAttribute(ATTR_MAX_ALLOWED_CONNS, getMessage(msgID),
                                    true, false, false, true, -1, false, 0);
    try
    {
      IntegerConfigAttribute maxConnsAttr =
           (IntegerConfigAttribute)
           configEntry.getConfigAttribute(maxConnsStub);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_CONFIG_CORE_INVALID_MAX_ALLOWED_CONNECTIONS;
      unacceptableReasons.add(getMessage(msgID, configEntry.getDN().toString(),
                                         String.valueOf(e)));
      configIsAcceptable = false;
    }


    // See if the entry indicates whether to send a response to abandoned
    // operations.  If so, then make sure that its value is acceptable.
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_NOTIFY_ABANDONED_OPERATIONS;
    BooleanConfigAttribute notifyAbandonedStub =
         new BooleanConfigAttribute(ATTR_NOTIFY_ABANDONED_OPS,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute notifyAbandonedAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(notifyAbandonedStub);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_CONFIG_CORE_INVALID_NOTIFY_ABANDONED_OPERATIONS;
      unacceptableReasons.add(getMessage(msgID, configEntry.getDN().toString(),
                                         String.valueOf(e)));
      configIsAcceptable = false;
    }


    // See if the entry specifies the DN of the proxied auth identity mapper.
    // If so, then make sure it's valid.
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_PROXY_MAPPER_DN;
    DNConfigAttribute proxyMapperStub =
         new DNConfigAttribute(ATTR_PROXY_MAPPER_DN, getMessage(msgID), false,
                               false, false);
    try
    {
      DNConfigAttribute proxyMapperAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(proxyMapperStub);
      if (proxyMapperAttr != null)
      {
        DN mapperDN = proxyMapperAttr.pendingValue();

        IdentityMapper mapper = DirectoryServer.getIdentityMapper(mapperDN);
        if (mapper == null)
        {
          msgID = MSGID_CONFIG_CORE_NO_PROXY_MAPPER_FOR_DN;
          unacceptableReasons.add(getMessage(msgID,
                                             String.valueOf(mapperDN),
                                             String.valueOf(configEntryDN)));
          configIsAcceptable = false;
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_CONFIG_CORE_INVALID_PROXY_MAPPER_DN;
      unacceptableReasons.add(getMessage(msgID, configEntry.getDN().toString(),
                                         String.valueOf(e)));
      configIsAcceptable = false;
    }


    // See if the entry specifies the server size limit.  If so, them make sure
    // it's valid.
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_SIZE_LIMIT;
    IntegerConfigAttribute sizeLimitStub =
         new IntegerConfigAttribute(ATTR_SIZE_LIMIT, getMessage(msgID), false,
                                    false, false, true, 0, true,
                                    Integer.MAX_VALUE);
    try
    {
      IntegerConfigAttribute sizeLimitAttr =
           (IntegerConfigAttribute)
           configEntry.getConfigAttribute(sizeLimitStub);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_CONFIG_CORE_INVALID_SIZE_LIMIT;
      unacceptableReasons.add(getMessage(msgID, configEntry.getDN().toString(),
                                         String.valueOf(e)));
      configIsAcceptable = false;
    }


    // See if the entry specifies the server time limit.  If so, them make sure
    // it's valid.
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_TIME_LIMIT;
    IntegerWithUnitConfigAttribute timeLimitStub =
         new IntegerWithUnitConfigAttribute(ATTR_TIME_LIMIT, getMessage(msgID),
                                            false,timeUnits, true, 0, true,
                                            Integer.MAX_VALUE);
    try
    {
      IntegerWithUnitConfigAttribute timeLimitAttr =
           (IntegerWithUnitConfigAttribute)
           configEntry.getConfigAttribute(timeLimitStub);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_CONFIG_CORE_INVALID_TIME_LIMIT;
      unacceptableReasons.add(getMessage(msgID, configEntry.getDN().toString(),
                                         String.valueOf(e)));
      configIsAcceptable = false;
    }


    // See if the entry specifies the writability mode.  If so, then make sure
    // it's valid.
    HashSet<String> writabilityModes = new HashSet<String>(3);
    writabilityModes.add(WritabilityMode.ENABLED.toString());
    writabilityModes.add(WritabilityMode.DISABLED.toString());
    writabilityModes.add(WritabilityMode.INTERNAL_ONLY.toString());

    msgID = MSGID_CONFIG_CORE_DESCRIPTION_WRITABILITY_MODE;
    MultiChoiceConfigAttribute writabilityStub =
         new MultiChoiceConfigAttribute(ATTR_WRITABILITY_MODE,
                                        getMessage(msgID), true, false, false,
                                        writabilityModes);
    try
    {
      MultiChoiceConfigAttribute writabilityAttr =
           (MultiChoiceConfigAttribute)
           configEntry.getConfigAttribute(writabilityStub);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_CONFIG_CORE_INVALID_WRITABILITY_MODE;
      unacceptableReasons.add(getMessage(msgID, configEntry.getDN().toString(),
                                         String.valueOf(e)));
      configIsAcceptable = false;
    }


    // See if the entry specifies how to handle binds with a DN but no password.
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_BIND_WITH_DN_REQUIRES_PW;
    BooleanConfigAttribute requirePWStub =
         new BooleanConfigAttribute(ATTR_BIND_WITH_DN_REQUIRES_PW,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute requirePWAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(requirePWStub);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_CONFIG_CORE_INVALID_BIND_WITH_DN_REQUIRES_PW;
      unacceptableReasons.add(getMessage(msgID, configEntry.getDN().toString(),
                                         String.valueOf(e)));
      configIsAcceptable = false;
    }


    // Get the DN of the default password policy configuration entry.  It must
    // be provided, and the DN must be associated with a valid password policy.
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_DEFAULT_PWPOLICY_DN;
    DNConfigAttribute defaultPWPolicyStub =
         new DNConfigAttribute(ATTR_DEFAULT_PWPOLICY_DN, getMessage(msgID),
                               true, false, false);
    try
    {
      DNConfigAttribute defaultPWPolicyAttr =
           (DNConfigAttribute)
           configEntry.getConfigAttribute(defaultPWPolicyStub);
      if (defaultPWPolicyAttr == null)
      {
        msgID = MSGID_CONFIG_CORE_NO_DEFAULT_PWPOLICY;
        unacceptableReasons.add(getMessage(msgID, configEntry.getDN()));

        configIsAcceptable = false;
      }
      else
      {
        PasswordPolicy p =
             DirectoryServer.getPasswordPolicy(
                  defaultPWPolicyAttr.pendingValue());
        if (p == null)
        {
          msgID = MSGID_CONFIG_CORE_NO_SUCH_PWPOLICY;
          unacceptableReasons.add(getMessage(msgID,
               String.valueOf(defaultPWPolicyAttr.pendingValue())));

          configIsAcceptable = false;
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      // An error occurred, so the provided value must not be valid.
      msgID = MSGID_CONFIG_CORE_INVALID_DEFAULT_PWPOLICY_DN;
      unacceptableReasons.add(getMessage(msgID, configEntry.getDN().toString(),
                                         String.valueOf(e)));
      configIsAcceptable = false;
    }


    return configIsAcceptable;
  }



  /**
   * Makes a best-effort attempt to apply the configuration contained in the
   * provided entry.  Information about the result of this processing should be
   * added to the provided message list.  Information should always be added to
   * this list if a configuration change could not be applied.  If detailed
   * results are requested, then information about the changes applied
   * successfully (and optionally about parameters that were not changed) should
   * also be included.
   *
   * @param  configEntry      The entry containing the new configuration to
   *                          apply for this component.
   * @param  detailedResults  Indicates whether detailed information about the
   *                          processing should be added to the list.
   *
   * @return  Information about the result of the configuration update.
   */
  public ConfigChangeResult applyNewConfiguration(ConfigEntry configEntry,
                                                  boolean detailedResults)
  {
    assert debugEnter(CLASS_NAME, "applyNewConfiguration",
                      String.valueOf(configEntry),
                      String.valueOf(detailedResults));

    ArrayList<String> resultMessages      = new ArrayList<String>();
    boolean           adminActionRequired = false;
    ResultCode        resultCode          = ResultCode.SUCCESS;


    // Determine whether to perform schema checking.  By default, we will.
    boolean checkSchema = true;

    int msgID = MSGID_CONFIG_CORE_DESCRIPTION_CHECK_SCHEMA;
    BooleanConfigAttribute checkSchemaStub =
         new BooleanConfigAttribute(ATTR_CHECK_SCHEMA, getMessage(msgID),
                                    false);
    try
    {
      BooleanConfigAttribute checkSchemaAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(checkSchemaStub);
      if (checkSchemaAttr == null)
      {
        // This is fine -- just use the default value.
      }
      else
      {
        // Use the value from the attribute that we read.
        checkSchema = checkSchemaAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      // An error occurred, so we will not allow this configuration change to
      // take place.
      resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
      resultMessages.add(getMessage(MSGID_CONFIG_CORE_INVALID_CHECK_SCHEMA,
                                    configEntry.getDN().toString(),
                                    String.valueOf(e)));
    }


    // Determine whether to allow attribute name exceptions.  By default, we
    // will not.
    boolean allowAttributeNameExceptions = false;

    msgID = MSGID_CONFIG_CORE_DESCRIPTION_ALLOW_ATTR_EXCEPTIONS;
    BooleanConfigAttribute allowExceptionsStub =
         new BooleanConfigAttribute(ATTR_ALLOW_ATTRIBUTE_NAME_EXCEPTIONS,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute allowExceptionsAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(allowExceptionsStub);
      if (allowExceptionsAttr == null)
      {
        // This is fine -- just use the default value.
      }
      else
      {
        // Use the value from the attribute that we read.
        allowAttributeNameExceptions = allowExceptionsAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      // An error occurred, so we will not allow this configuration change to
      // take place.
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
      }

      resultMessages.add(getMessage(MSGID_CONFIG_CORE_INVALID_ALLOW_EXCEPTIONS,
                                    configEntry.getDN().toString(),
                                    String.valueOf(e)));
    }


    // Determine whether to add missing RDN attributes.  By default we will not.
    boolean addMissingRDNAttributes = false;
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_ADD_MISSING_RDN_ATTRS;
    BooleanConfigAttribute addRDNStub =
         new BooleanConfigAttribute(ATTR_ADD_MISSING_RDN_ATTRS,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute addRDNAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(addRDNStub);
      if (addRDNAttr == null)
      {
        // This is fine -- just use the default value.
      }
      else
      {
        // Use the value from the attribute that we read.
        addMissingRDNAttributes = addRDNAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      // An error occurred, so we will not allow this configuration change to
      // take place.
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
      }

      resultMessages.add(getMessage(
                              MSGID_CONFIG_CORE_INVALID_ADD_MISSING_RDN_ATTRS,
                              configEntry.getDN().toString(),
                              String.valueOf(e)));
    }


    // Determine the result code to use for internal server errors.
    ResultCode serverErrorResultCode = ResultCode.OTHER;
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_SERVER_ERROR_RESULT_CODE;
    IntegerConfigAttribute serverErrorStub =
         new IntegerConfigAttribute(ATTR_SERVER_ERROR_RESULT_CODE,
                                    getMessage(msgID), false, false, false,
                                    true, 1, false, 0);
    try
    {
      IntegerConfigAttribute serverErrorAttr =
           (IntegerConfigAttribute)
           configEntry.getConfigAttribute(serverErrorStub);
      if (serverErrorAttr == null)
      {
        // This is fine -- just use the default value.
        DirectoryServer.setServerErrorResultCode(serverErrorResultCode);
      }
      else
      {
        // Get the integer value from the provided attribute and then convert
        // that to a result code.
        int resultCodeValue = serverErrorAttr.pendingIntValue();
        serverErrorResultCode = ResultCode.valueOf(resultCodeValue);
        DirectoryServer.setServerErrorResultCode(serverErrorResultCode);
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      // An error occurred, so we will not allow this configuration change to
      // take place.
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
      }

      resultMessages.add(getMessage(
           MSGID_CONFIG_CORE_INVALID_SERVER_ERROR_RESULT_CODE,
           configEntry.getDN().toString(), String.valueOf(e)));
    }


    // Determine how to handle attributes that do not conform to the associated
    // syntax.  By default, we will log a warning but the value will be
    // accepted.
    AcceptRejectWarn syntaxEnforcementPolicy = AcceptRejectWarn.WARN;
    HashSet<String> syntaxBehaviorValues = new HashSet<String>(3);
    syntaxBehaviorValues.add(AcceptRejectWarn.ACCEPT.toString());
    syntaxBehaviorValues.add(AcceptRejectWarn.REJECT.toString());
    syntaxBehaviorValues.add(AcceptRejectWarn.WARN.toString());

    msgID = MSGID_CONFIG_CORE_DESCRIPTION_INVALID_SYNTAX_BEHAVIOR;
    MultiChoiceConfigAttribute syntaxPolicyStub =
         new MultiChoiceConfigAttribute(ATTR_INVALID_SYNTAX_BEHAVIOR,
                                        getMessage(msgID), true, false, false,
                                        syntaxBehaviorValues);
    try
    {
      MultiChoiceConfigAttribute syntaxPolicyAttr =
           (MultiChoiceConfigAttribute)
           configEntry.getConfigAttribute(syntaxPolicyStub);
      if (syntaxPolicyAttr == null)
      {
        // This is fine -- just use the default value.
      }
      else
      {
        // Use the value from the attribute that we read.
        syntaxEnforcementPolicy =
             AcceptRejectWarn.policyForName(syntaxPolicyAttr.pendingValue());
        if (syntaxEnforcementPolicy == null)
        {
          resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
          resultMessages.add(getMessage(
               MSGID_CONFIG_CORE_INVALID_ENFORCE_STRICT_SYNTAX,
               String.valueOf(configEntry.getDN()),
               String.valueOf(syntaxPolicyAttr.pendingValue())));
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      // An error occurred, so we will not allow this configuration change to
      // take place.
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
      }

      resultMessages.add(getMessage(
           MSGID_CONFIG_CORE_INVALID_ENFORCE_STRICT_SYNTAX,
           String.valueOf(configEntry.getDN()), String.valueOf(e)));
    }


    // Determine how to handle entries that do not have exactly one structural
    // objectclass.  By default, we will log a warning but the entry will be
    // accepted.
    AcceptRejectWarn structuralClassPolicy = AcceptRejectWarn.WARN;
    HashSet<String> structuralClassValues = new HashSet<String>(3);
    structuralClassValues.add(AcceptRejectWarn.ACCEPT.toString());
    structuralClassValues.add(AcceptRejectWarn.REJECT.toString());
    structuralClassValues.add(AcceptRejectWarn.WARN.toString());

    msgID = MSGID_CONFIG_CORE_DESCRIPTION_STRUCTURAL_CLASS_BEHAVIOR;
    MultiChoiceConfigAttribute structuralClassStub =
         new MultiChoiceConfigAttribute(ATTR_SINGLE_STRUCTURAL_CLASS_BEHAVIOR,
                                        getMessage(msgID), true, false, false,
                                        structuralClassValues);
    try
    {
      MultiChoiceConfigAttribute structuralClassAttr =
           (MultiChoiceConfigAttribute)
           configEntry.getConfigAttribute(structuralClassStub);
      if (structuralClassAttr == null)
      {
        // This is fine -- just use the default value.
      }
      else
      {
        // Use the value from the attribute that we read.
        structuralClassPolicy =
             AcceptRejectWarn.policyForName(structuralClassAttr.pendingValue());
        if (structuralClassPolicy == null)
        {
          resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
          resultMessages.add(getMessage(
               MSGID_CONFIG_CORE_INVALID_STRUCTURAL_CLASS_BEHAVIOR,
               String.valueOf(configEntry.getDN()),
               String.valueOf(structuralClassAttr.pendingValue())));
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      // An error occurred, so we will not allow this configuration change to
      // take place.
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
      }

      resultMessages.add(getMessage(
           MSGID_CONFIG_CORE_INVALID_STRUCTURAL_CLASS_BEHAVIOR,
           String.valueOf(configEntry.getDN()), String.valueOf(e)));
    }


    // Determine the maximum number of client connections that should be allowed
    // at any time.
    long maxAllowedConnections = -1;

    msgID = MSGID_CONFIG_CORE_DESCRIPTION_MAX_ALLOWED_CONNECTIONS;
    IntegerConfigAttribute maxConnsStub =
         new IntegerConfigAttribute(ATTR_MAX_ALLOWED_CONNS, getMessage(msgID),
                                    true, false, false, true, -1, false, 0);
    try
    {
      IntegerConfigAttribute maxConnsAttr =
           (IntegerConfigAttribute)
           configEntry.getConfigAttribute(maxConnsStub);
      if (maxConnsAttr == null)
      {
        // This is fine -- just use the default value.
      }
      else
      {
        // Use the value from the attribute that we read.
        maxAllowedConnections = maxConnsAttr.activeValue();
      }
    }
    catch (Exception e)
    {
      // An error occurred, but this should not be considered fatal.  Log an
      // error message and use the default.
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
      }

      resultMessages.add(getMessage(
                              MSGID_CONFIG_CORE_INVALID_MAX_ALLOWED_CONNECTIONS,
                              configEntry.getDN(), String.valueOf(e)));
    }


    // Determine whether to send a response to abandoned operations.  By
    // default, we will not.
    boolean notifyAbandonedOperations = false;

    msgID = MSGID_CONFIG_CORE_DESCRIPTION_NOTIFY_ABANDONED_OPERATIONS;
    BooleanConfigAttribute notifyAbandonedStub =
         new BooleanConfigAttribute(ATTR_NOTIFY_ABANDONED_OPS,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute notifyAbandonedAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(notifyAbandonedStub);
      if (notifyAbandonedAttr == null)
      {
        // This is fine -- just use the default value.
      }
      else
      {
        // Use the value from the attribute that we read.
        notifyAbandonedOperations = notifyAbandonedAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      // An error occurred, so we will not allow this configuration change to
      // take place.
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
      }

      resultMessages.add(getMessage(
           MSGID_CONFIG_CORE_INVALID_NOTIFY_ABANDONED_OPERATIONS,
           configEntry.getDN().toString(), String.valueOf(e)));
    }


    // Get the DN of the proxied authorization identity mapper.
    DN proxyMapperDN =
            DirectoryServer.getProxiedAuthorizationIdentityMapperDN();
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_PROXY_MAPPER_DN;
    DNConfigAttribute proxyMapperStub =
         new DNConfigAttribute(ATTR_PROXY_MAPPER_DN, getMessage(msgID), false,
                               false, false);
    try
    {
      DNConfigAttribute proxyMapperAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(proxyMapperStub);
      if (proxyMapperAttr == null)
      {
        proxyMapperDN = null;
      }
      else
      {
        proxyMapperDN = proxyMapperAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      // An error occurred, so we will not allow this configuration change to
      // take place.
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
      }

      msgID = MSGID_CONFIG_CORE_INVALID_PROXY_MAPPER_DN;
      resultMessages.add(getMessage(msgID, configEntry.getDN().toString(),
                                    String.valueOf(e)));
    }


    // Get the server size limit.
    int sizeLimit = DEFAULT_SIZE_LIMIT;
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_SIZE_LIMIT;
    IntegerConfigAttribute sizeLimitStub =
         new IntegerConfigAttribute(ATTR_SIZE_LIMIT, getMessage(msgID), false,
                                    false, false, true, 0, true,
                                    Integer.MAX_VALUE);
    try
    {
      IntegerConfigAttribute sizeLimitAttr =
           (IntegerConfigAttribute)
           configEntry.getConfigAttribute(sizeLimitStub);
      if (sizeLimitAttr != null)
      {
        sizeLimit = sizeLimitAttr.pendingIntValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      // An error occurred, so we will not allow this configuration change to
      // take place.
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
      }

      msgID = MSGID_CONFIG_CORE_INVALID_SIZE_LIMIT;
      resultMessages.add(getMessage(msgID, configEntry.getDN().toString(),
                                    String.valueOf(e)));
    }


    // Get the server time limit.
    int timeLimit = DEFAULT_TIME_LIMIT;
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_TIME_LIMIT;
    IntegerWithUnitConfigAttribute timeLimitStub =
         new IntegerWithUnitConfigAttribute(ATTR_TIME_LIMIT, getMessage(msgID),
                                            false, timeUnits, true, 0, true,
                                            Integer.MAX_VALUE);
    try
    {
      IntegerWithUnitConfigAttribute timeLimitAttr =
           (IntegerWithUnitConfigAttribute)
           configEntry.getConfigAttribute(timeLimitStub);
      if (timeLimitAttr != null)
      {
        timeLimit = (int) timeLimitAttr.pendingCalculatedValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      // An error occurred, so we will not allow this configuration change to
      // take place.
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
      }

      msgID = MSGID_CONFIG_CORE_INVALID_TIME_LIMIT;
      resultMessages.add(getMessage(msgID, configEntry.getDN().toString(),
                                    String.valueOf(e)));
    }


    // Get the server writability mode.
    HashSet<String> writabilityModes = new HashSet<String>(3);
    writabilityModes.add(WritabilityMode.ENABLED.toString());
    writabilityModes.add(WritabilityMode.DISABLED.toString());
    writabilityModes.add(WritabilityMode.INTERNAL_ONLY.toString());

    WritabilityMode writabilityMode = WritabilityMode.ENABLED;
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_WRITABILITY_MODE;
    MultiChoiceConfigAttribute writabilityStub =
         new MultiChoiceConfigAttribute(ATTR_WRITABILITY_MODE,
                                        getMessage(msgID), true, false, false,
                                        writabilityModes);
    try
    {
      MultiChoiceConfigAttribute writabilityAttr =
           (MultiChoiceConfigAttribute)
           configEntry.getConfigAttribute(writabilityStub);
      if (writabilityAttr != null)
      {
        writabilityMode =
             WritabilityMode.modeForName(writabilityAttr.pendingValue());
        if (writabilityMode == null)
        {
          msgID = MSGID_CONFIG_CORE_INVALID_WRITABILITY_MODE;
          resultMessages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                        writabilityAttr.pendingValue()));

          if (resultCode == ResultCode.SUCCESS)
          {
            resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
          }
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      // An error occurred, so we will not allow this configuration change to
      // take place.
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
      }

      msgID = MSGID_CONFIG_CORE_INVALID_WRITABILITY_MODE;
      resultMessages.add(getMessage(msgID, configEntry.getDN().toString(),
                                    String.valueOf(e)));
    }


    // Get the configuration for handling binds with a DN but no password.
    boolean bindWithDNRequiresPW = DEFAULT_BIND_WITH_DN_REQUIRES_PW;
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_BIND_WITH_DN_REQUIRES_PW;
    BooleanConfigAttribute requirePWStub =
         new BooleanConfigAttribute(ATTR_BIND_WITH_DN_REQUIRES_PW,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute requirePWAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(requirePWStub);
      if (requirePWAttr != null)
      {
        bindWithDNRequiresPW = requirePWAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      // An error occurred, so we will not allow this configuration change to
      // take place.
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
      }

      msgID = MSGID_CONFIG_CORE_INVALID_BIND_WITH_DN_REQUIRES_PW;
      resultMessages.add(getMessage(msgID, configEntry.getDN().toString(),
                                    String.valueOf(e)));
    }


    // Ge the DN of the default password policy configuration entry.
    DN defaultPWPolicyDN = null;
    msgID = MSGID_CONFIG_CORE_DESCRIPTION_DEFAULT_PWPOLICY_DN;
    DNConfigAttribute defaultPWPolicyStub =
         new DNConfigAttribute(ATTR_DEFAULT_PWPOLICY_DN, getMessage(msgID),
                               true, false, false);
    try
    {
      DNConfigAttribute defaultPWPolicyAttr =
           (DNConfigAttribute)
           configEntry.getConfigAttribute(defaultPWPolicyStub);
      if (defaultPWPolicyAttr == null)
      {
        msgID = MSGID_CONFIG_CORE_NO_DEFAULT_PWPOLICY;
        resultMessages.add(getMessage(msgID, String.valueOf(configEntryDN)));

        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = ResultCode.OBJECTCLASS_VIOLATION;
        }
      }
      else
      {
        defaultPWPolicyDN = defaultPWPolicyAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      // An error occurred, so we will not allow this configuration change to
      // take place.
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
      }

      msgID = MSGID_CONFIG_CORE_INVALID_DEFAULT_PWPOLICY_DN;
      resultMessages.add(getMessage(msgID, configEntry.getDN().toString(),
                                    String.valueOf(e)));
    }


    // If the result is successful, then apply the changes.
    if (resultCode == ResultCode.SUCCESS)
    {
      DirectoryServer.setCheckSchema(checkSchema);
      if (detailedResults)
      {
        resultMessages.add(getMessage(MSGID_CONFIG_SET_ATTRIBUTE,
                                      ATTR_CHECK_SCHEMA,
                                      String.valueOf(checkSchema),
                                      configEntryDN.toString()));
      }


      DirectoryServer.setAllowAttributeNameExceptions(
                           allowAttributeNameExceptions);
      if (detailedResults)
      {
        resultMessages.add(getMessage(MSGID_CONFIG_SET_ATTRIBUTE,
                                ATTR_ALLOW_ATTRIBUTE_NAME_EXCEPTIONS,
                                String.valueOf(allowAttributeNameExceptions),
                                configEntryDN.toString()));
      }


      DirectoryServer.setAddMissingRDNAttributes(addMissingRDNAttributes);
      if (detailedResults)
      {
        resultMessages.add(getMessage(MSGID_CONFIG_SET_ATTRIBUTE,
                                ATTR_ADD_MISSING_RDN_ATTRS,
                                String.valueOf(addMissingRDNAttributes),
                                configEntryDN.toString()));
      }


      DirectoryServer.setServerErrorResultCode(serverErrorResultCode);
      if (detailedResults)
      {
        resultMessages.add(getMessage(MSGID_CONFIG_SET_ATTRIBUTE,
                                ATTR_SERVER_ERROR_RESULT_CODE,
                                String.valueOf(serverErrorResultCode),
                                configEntryDN.toString()));
      }


      DirectoryServer.setSyntaxEnforcementPolicy(syntaxEnforcementPolicy);
      if (detailedResults)
      {
        resultMessages.add(getMessage(MSGID_CONFIG_SET_ATTRIBUTE,
                                ATTR_INVALID_SYNTAX_BEHAVIOR,
                                String.valueOf(syntaxEnforcementPolicy),
                                configEntryDN.toString()));
      }


      DirectoryServer.setSingleStructuralObjectClassPolicy(
                           structuralClassPolicy);
      if (detailedResults)
      {
        resultMessages.add(getMessage(MSGID_CONFIG_SET_ATTRIBUTE,
                                ATTR_SINGLE_STRUCTURAL_CLASS_BEHAVIOR,
                                String.valueOf(structuralClassPolicy),
                                configEntryDN.toString()));
      }


      DirectoryServer.setMaxAllowedConnections(maxAllowedConnections);
      if (detailedResults)
      {
        resultMessages.add(getMessage(MSGID_CONFIG_SET_ATTRIBUTE,
                                ATTR_MAX_ALLOWED_CONNS,
                                String.valueOf(maxAllowedConnections),
                                configEntryDN.toString()));
      }


      DirectoryServer.setNotifyAbandonedOperations(notifyAbandonedOperations);
      if (detailedResults)
      {
        resultMessages.add(getMessage(MSGID_CONFIG_SET_ATTRIBUTE,
                                ATTR_NOTIFY_ABANDONED_OPS,
                                String.valueOf(notifyAbandonedOperations),
                                configEntryDN.toString()));
      }


      DirectoryServer.setProxiedAuthorizationIdentityMapperDN(proxyMapperDN);
      if (detailedResults)
      {
        resultMessages.add(getMessage(MSGID_CONFIG_SET_ATTRIBUTE,
                                ATTR_PROXY_MAPPER_DN,
                                String.valueOf(proxyMapperDN),
                                configEntryDN.toString()));
      }


      DirectoryServer.setSizeLimit(sizeLimit);
      if (detailedResults)
      {
        resultMessages.add(getMessage(MSGID_CONFIG_SET_ATTRIBUTE,
                                      ATTR_SIZE_LIMIT,
                                      String.valueOf(sizeLimit),
                                      configEntryDN.toString()));
      }


      DirectoryServer.setTimeLimit(timeLimit);
      if (detailedResults)
      {
        resultMessages.add(getMessage(MSGID_CONFIG_SET_ATTRIBUTE,
                                      ATTR_TIME_LIMIT,
                                      String.valueOf(timeLimit),
                                      configEntryDN.toString()));
      }


      DirectoryServer.setWritabilityMode(writabilityMode);
      if (detailedResults)
      {
        resultMessages.add(getMessage(MSGID_CONFIG_SET_ATTRIBUTE,
                                      ATTR_WRITABILITY_MODE,
                                      String.valueOf(writabilityMode),
                                      configEntryDN.toString()));
      }


      DirectoryServer.setBindWithDNRequiresPassword(bindWithDNRequiresPW);
      if (detailedResults)
      {
        resultMessages.add(getMessage(MSGID_CONFIG_SET_ATTRIBUTE,
                                      ATTR_BIND_WITH_DN_REQUIRES_PW,
                                      String.valueOf(bindWithDNRequiresPW),
                                      configEntryDN.toString()));
      }


      DirectoryServer.setDefaultPasswordPolicyDN(defaultPWPolicyDN);
      if (detailedResults)
      {
        resultMessages.add(getMessage(MSGID_CONFIG_SET_ATTRIBUTE,
                                      ATTR_DEFAULT_PWPOLICY_DN,
                                      String.valueOf(defaultPWPolicyDN),
                                      configEntryDN.toString()));
      }
    }


    return new ConfigChangeResult(resultCode, adminActionRequired,
                                  resultMessages);
  }
}

