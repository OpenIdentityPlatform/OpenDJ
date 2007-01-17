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
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.api.Backend;
import org.opends.server.api.ConfigAddListener;
import org.opends.server.api.ConfigChangeListener;
import org.opends.server.api.ConfigDeleteListener;
import org.opends.server.api.ConfigHandler;
import org.opends.server.api.ConfigurableComponent;
import org.opends.server.config.BooleanConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.DNConfigAttribute;
import org.opends.server.config.MultiChoiceConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.WritabilityMode;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a utility that will be used to manage the configuration
 * for the set of backends defined in the Directory Server.  It will perform
 * the necessary initialization of those backends when the server is first
 * started, and then will manage any changes to them while the server is
 * running.
 */
public class BackendConfigManager
       implements ConfigChangeListener, ConfigAddListener, ConfigDeleteListener
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.core.BackendConfigManager";



  // The mapping between configuration entry DNs and their corresponding
  // backend implementations.
  private ConcurrentHashMap<DN,Backend> registeredBackends;

  // The DN of the associated configuration entry.
  private DN configEntryDN;



  /**
   * Creates a new instance of this backend config manager.
   */
  public BackendConfigManager()
  {
    assert debugConstructor(CLASS_NAME);

    // No implementation is required.
  }



  /**
   * Initializes the configuration associated with the Directory Server
   * backends.  This should only be called at Directory Server startup.
   *
   * @throws  ConfigException  If a critical configuration problem prevents the
   *                           backend initialization from succeeding.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the backends that is not related to the
   *                                   server configuration.
   */
  public void initializeBackendConfig()
         throws ConfigException, InitializationException
  {
    assert debugEnter(CLASS_NAME, "initializeBackendConfig");


    registeredBackends = new ConcurrentHashMap<DN,Backend>();



    // Get the configuration entry that is at the root of all the backends in
    // the server.
    ConfigEntry backendRoot;
    try
    {
      configEntryDN = DN.decode(DN_BACKEND_BASE);
      backendRoot   = DirectoryServer.getConfigEntry(configEntryDN);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeBackendConfig", e);

      int    msgID   = MSGID_CONFIG_BACKEND_CANNOT_GET_CONFIG_BASE;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new ConfigException(msgID, message, e);
    }


    // If the configuration root entry is null, then assume it doesn't exist.
    // In that case, then fail.  At least that entry must exist in the
    // configuration, even if there are no backends defined below it.
    if (backendRoot == null)
    {
      int    msgID   = MSGID_CONFIG_BACKEND_BASE_DOES_NOT_EXIST;
      String message = getMessage(msgID);
      throw new ConfigException(msgID, message);
    }


    // Register as an add and delete listener for the base entry so that we can
    // be notified if new backends are added or existing backends are removed.
    backendRoot.registerAddListener(this);
    backendRoot.registerDeleteListener(this);


    // Iterate through the set of immediate children below the backend config
    // root.
    for (ConfigEntry backendEntry : backendRoot.getChildren().values())
    {
      DN backendDN = backendEntry.getDN();


      // Register as a change listener for this backend entry so that we will
      // be notified of any changes that may be made to it.
      backendEntry.registerChangeListener(this);


      // Check to see if this entry appears to contain a backend configuration.
      // If not, log a warning and skip it.
      try
      {
        SearchFilter backendFilter =
             SearchFilter.createFilterFromString("(objectClass=" + OC_BACKEND +
                                                 ")");
        if (! backendFilter.matchesEntry(backendEntry.getEntry()))
        {
          int msgID = MSGID_CONFIG_BACKEND_ENTRY_DOES_NOT_HAVE_BACKEND_CONFIG;
          String message = getMessage(msgID, String.valueOf(backendDN));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          continue;
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "initializeBackendConfig", e);

        int msgID = MSGID_CONFIG_BACKEND_ERROR_INTERACTING_WITH_BACKEND_ENTRY;
        String message = getMessage(msgID, String.valueOf(backendDN),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        continue;
      }


      // See if the entry contains an attribute that indicates whether the
      // backend should be enabled.  If it does not, or if it is not set to
      // "true", then skip it.
      int msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_ENABLED;
      BooleanConfigAttribute enabledStub =
           new BooleanConfigAttribute(ATTR_BACKEND_ENABLED, getMessage(msgID),
                                      false);
      try
      {
        BooleanConfigAttribute enabledAttr =
             (BooleanConfigAttribute)
             backendEntry.getConfigAttribute(enabledStub);
        if (enabledAttr == null)
        {
          // The attribute is not present, so this backend will be disabled.
          // Log a message and continue.
          msgID = MSGID_CONFIG_BACKEND_NO_ENABLED_ATTR;
          String message = getMessage(msgID, String.valueOf(backendDN));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          continue;
        }
        else if (! enabledAttr.activeValue())
        {
          // The backend is explicitly disabled.  Log a mild warning and
          // continue.
          msgID = MSGID_CONFIG_BACKEND_DISABLED;
          String message = getMessage(msgID, String.valueOf(backendDN));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.INFORMATIONAL, message, msgID);
          continue;
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "initializeBackendConfig", e);

        msgID = MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_ENABLED_STATE;
        String message = getMessage(msgID, String.valueOf(backendDN),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        continue;
      }


      // See if the entry contains an attribute that specifies the backend ID.
      // If it does not, then log an error and skip it.
      msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_BACKEND_ID;
      String backendID = null;
      StringConfigAttribute idStub =
           new StringConfigAttribute(ATTR_BACKEND_ID, getMessage(msgID),
                                     true, false, true);
      try
      {
        StringConfigAttribute idAttr =
             (StringConfigAttribute) backendEntry.getConfigAttribute(idStub);
        if (idAttr == null)
        {
          msgID = MSGID_CONFIG_BACKEND_NO_BACKEND_ID;
          String message = getMessage(msgID, String.valueOf(backendDN));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          continue;
        }
        else
        {
          backendID = idAttr.activeValue();

          // If there is already a backend registered with the specified ID,
          // then log an error and skip it.
          if (DirectoryServer.hasBackend(backendID))
          {
            msgID = MSGID_CONFIG_BACKEND_DUPLICATE_BACKEND_ID;
            String message = getMessage(msgID, backendID,
                                        String.valueOf(backendDN));
            logError(ErrorLogCategory.CONFIGURATION,
                     ErrorLogSeverity.SEVERE_WARNING, message, msgID);
            continue;
          }
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "initializeBackendConfig", e);

        msgID = MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_BACKEND_ID;
        String message = getMessage(msgID, String.valueOf(backendDN),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        continue;
      }


      // Get the writability mode for this backend.  It must be provided.
      LinkedHashSet<String> writabilityModes = new LinkedHashSet<String>(3);
      writabilityModes.add(WritabilityMode.ENABLED.toString());
      writabilityModes.add(WritabilityMode.DISABLED.toString());
      writabilityModes.add(WritabilityMode.INTERNAL_ONLY.toString());

      msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_WRITABILITY;
      WritabilityMode writabilityMode = null;
      MultiChoiceConfigAttribute writabilityStub =
           new MultiChoiceConfigAttribute(ATTR_BACKEND_WRITABILITY_MODE,
                                          getMessage(msgID), true, false, false,
                                          writabilityModes);
      try
      {
        MultiChoiceConfigAttribute writabilityAttr =
             (MultiChoiceConfigAttribute)
             backendEntry.getConfigAttribute(writabilityStub);
        if (writabilityAttr == null)
        {
          msgID = MSGID_CONFIG_BACKEND_NO_WRITABILITY_MODE;
          String message = getMessage(msgID, String.valueOf(backendDN));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_ERROR, message, msgID);
          continue;
        }
        else
        {
          writabilityMode =
               WritabilityMode.modeForName(writabilityAttr.activeValue());
          if (writabilityMode == null)
          {
            msgID = MSGID_CONFIG_BACKEND_INVALID_WRITABILITY_MODE;
            String message =
                 getMessage(msgID, String.valueOf(backendDN),
                            String.valueOf(writabilityAttr.activeValue()));
            logError(ErrorLogCategory.CONFIGURATION,
                     ErrorLogSeverity.SEVERE_ERROR, message, msgID);
            continue;
          }
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "initializeBackendConfig", e);

        msgID = MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_WRITABILITY;
        String message = getMessage(msgID, String.valueOf(backendDN),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        continue;
      }


      // See if the entry contains an attribute that specifies the base DNs for
      // the backend.  If it does not, then log an error and skip it.
      msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_BASE_DNS;
      DN[] baseDNs = null;
      DNConfigAttribute baseDNStub =
           new DNConfigAttribute(ATTR_BACKEND_BASE_DN, getMessage(msgID),
                                 true, true, true);
      try
      {
        DNConfigAttribute baseDNAttr =
             (DNConfigAttribute) backendEntry.getConfigAttribute(baseDNStub);
        if (baseDNAttr == null)
        {
          msgID = MSGID_CONFIG_BACKEND_NO_BASE_DNS;
          String message = getMessage(msgID, String.valueOf(backendDN));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_ERROR, message, msgID);
          continue;
        }
        else
        {
          List<DN> dnList = baseDNAttr.activeValues();
          baseDNs = new DN[dnList.size()];
          dnList.toArray(baseDNs);
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "initializeBackendConfig", e);

        msgID = MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_BASE_DNS;
        String message = getMessage(msgID, String.valueOf(backendDN),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        continue;
      }


      // See if the entry contains an attribute that specifies the class name
      // for the backend implementation.  If it does, then load it and make sure
      // that it's a valid backend implementation.  There is no such attribute,
      // the specified class cannot be loaded, or it does not contain a valid
      // backend implementation, then log an error and skip it.
      String className;
      msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_CLASS;
      StringConfigAttribute classStub =
           new StringConfigAttribute(ATTR_BACKEND_CLASS, getMessage(msgID),
                                     true, false, true);
      try
      {
        StringConfigAttribute classAttr =
             (StringConfigAttribute) backendEntry.getConfigAttribute(classStub);
        if (classAttr == null)
        {
          msgID = MSGID_CONFIG_BACKEND_NO_CLASS_ATTR;
          String message = getMessage(msgID, String.valueOf(backendDN));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_ERROR, message, msgID);
          continue;
        }
        else
        {
          className = classAttr.activeValue();
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "initializeBackendConfig", e);

        msgID = MSGID_CONFIG_BACKEND_CANNOT_GET_CLASS;
        String message = getMessage(msgID, String.valueOf(backendDN),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        continue;
      }

      Backend backend;
      try
      {
        // FIXME -- Should we use a custom class loader for this?
        Class backendClass = Class.forName(className);
        backend = (Backend) backendClass.newInstance();
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "initializeBackendConfig", e);

        msgID = MSGID_CONFIG_BACKEND_CANNOT_INSTANTIATE;
        String message = getMessage(msgID, String.valueOf(className),
                                    String.valueOf(backendDN),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        continue;
      }


      // If this backend is a configuration manager, then we don't want to do
      // any more with it because the configuration will have already been
      // started.
      if (backend instanceof ConfigHandler)
      {
        continue;
      }


      // Set the backend ID and writability mode for this backend.
      backend.setBackendID(backendID);
      backend.setWritabilityMode(writabilityMode);


      // Acquire a shared lock on this backend.  This will prevent operations
      // like LDIF import or restore from occurring while the backend is active.
      try
      {
        String lockFile = LockFileManager.getBackendLockFileName(backend);
        StringBuilder failureReason = new StringBuilder();
        if (! LockFileManager.acquireSharedLock(lockFile, failureReason))
        {
          msgID = MSGID_CONFIG_BACKEND_CANNOT_ACQUIRE_SHARED_LOCK;
          String message = getMessage(msgID, backendID,
                                      String.valueOf(failureReason));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          // FIXME -- Do we need to send an admin alert?
          continue;
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "initializeBackendConfig", e);

        msgID = MSGID_CONFIG_BACKEND_CANNOT_ACQUIRE_SHARED_LOCK;
        String message = getMessage(msgID, backendID,
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.CONFIGURATION,
                 ErrorLogSeverity.SEVERE_WARNING, message, msgID);
        // FIXME -- Do we need to send an admin alert?
        continue;
      }


      // Perform the necessary initialization for the backend entry.
      try
      {
        backend.initializeBackend(backendEntry, baseDNs);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "initializeBackendConfig", e);

        msgID = MSGID_CONFIG_BACKEND_CANNOT_INITIALIZE;
        String message = getMessage(msgID, String.valueOf(className),
                                    String.valueOf(backendDN),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);

        try
        {
          String lockFile = LockFileManager.getBackendLockFileName(backend);
          StringBuilder failureReason = new StringBuilder();
          if (! LockFileManager.releaseLock(lockFile, failureReason))
          {
            msgID = MSGID_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK;
            message = getMessage(msgID, backendID,
                                 String.valueOf(failureReason));
            logError(ErrorLogCategory.CONFIGURATION,
                     ErrorLogSeverity.SEVERE_WARNING, message, msgID);
            // FIXME -- Do we need to send an admin alert?
          }
        }
        catch (Exception e2)
        {
          assert debugException(CLASS_NAME, "initializeBackendConfig", e2);

          msgID = MSGID_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK;
          message = getMessage(msgID, backendID,
                               stackTraceToSingleLineString(e2));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          // FIXME -- Do we need to send an admin alert?
        }

        continue;
      }


      // Register the backend with the server.
      try
      {
        DirectoryServer.registerBackend(backend);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "initializeBackendConfig", e);

        msgID = MSGID_CONFIG_BACKEND_CANNOT_REGISTER_BACKEND;
        String message = getMessage(msgID, backendID,
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        // FIXME -- Do we need to send an admin alert?
      }


      // Put this backend in the hash so that we will be able to find it if it
      // is altered.
      registeredBackends.put(backendDN, backend);
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
    assert debugEnter(CLASS_NAME, "configChangeIsAcceptable",
                      String.valueOf(configEntry), "java.lang.StringBuilder");

    DN backendDN = configEntry.getDN();


    // Check to see if this entry appears to contain a backend configuration.
    // If not, log a warning and skip it.
    try
    {
      SearchFilter backendFilter =
           SearchFilter.createFilterFromString("(objectClass=" + OC_BACKEND +
                                               ")");
      if (! backendFilter.matchesEntry(configEntry.getEntry()))
      {
        int msgID = MSGID_CONFIG_BACKEND_ENTRY_DOES_NOT_HAVE_BACKEND_CONFIG;
        unacceptableReason.append(getMessage(msgID, String.valueOf(backendDN)));
        return false;
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "configChangeIsAcceptable", e);

      int msgID = MSGID_CONFIG_BACKEND_ERROR_INTERACTING_WITH_BACKEND_ENTRY;
      unacceptableReason.append(getMessage(msgID, String.valueOf(backendDN),
                                           stackTraceToSingleLineString(e)));
      return false;
    }


    // See if the entry contains an attribute that indicates whether the
    // backend should be enabled.  If it does not, or if it is not set to
    // "true", then skip it.
    int msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_ENABLED;
    BooleanConfigAttribute enabledStub =
         new BooleanConfigAttribute(ATTR_BACKEND_ENABLED, getMessage(msgID),
                                    false);
    try
    {
      BooleanConfigAttribute enabledAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(enabledStub);
      if (enabledAttr == null)
      {
        // The attribute is not present, so this backend will be disabled.
        msgID = MSGID_CONFIG_BACKEND_NO_ENABLED_ATTR;
        unacceptableReason.append(getMessage(msgID, String.valueOf(backendDN)));
        return false;
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "configChangeIsAcceptable", e);

      msgID = MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_ENABLED_STATE;
      unacceptableReason.append(getMessage(msgID, String.valueOf(backendDN),
                                           stackTraceToSingleLineString(e)));
      return false;
    }


    // See if the entry contains an attribute that specifies the backend ID.  If
    // it does not, then reject it.
    msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_BACKEND_ID;
    StringConfigAttribute idStub =
         new StringConfigAttribute(ATTR_BACKEND_ID, getMessage(msgID), true,
                                   false, true);
    try
    {
      StringConfigAttribute idAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(idStub);
      if (idAttr == null)
      {
        // The attribute is not present.  We will not allow this.
        msgID = MSGID_CONFIG_BACKEND_NO_BACKEND_ID;
        unacceptableReason.append(getMessage(msgID, String.valueOf(backendDN)));
        return false;
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "configChangeIsAcceptable", e);

      msgID = MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_BACKEND_ID;
      unacceptableReason.append(getMessage(msgID, String.valueOf(backendDN),
                                           stackTraceToSingleLineString(e)));
      return false;
    }


    // See if the entry contains an attribute that specifies the writability
    // mode.  If it does not, then reject it.
    LinkedHashSet<String> writabilityModes = new LinkedHashSet<String>(3);
    writabilityModes.add(WritabilityMode.ENABLED.toString());
    writabilityModes.add(WritabilityMode.DISABLED.toString());
    writabilityModes.add(WritabilityMode.INTERNAL_ONLY.toString());
    msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_WRITABILITY;
    MultiChoiceConfigAttribute writabilityStub =
         new MultiChoiceConfigAttribute(ATTR_BACKEND_WRITABILITY_MODE,
                                        getMessage(msgID), true, false, false,
                                        writabilityModes);
    try
    {
      MultiChoiceConfigAttribute writabilityAttr =
           (MultiChoiceConfigAttribute)
           configEntry.getConfigAttribute(writabilityStub);
      if (writabilityAttr == null)
      {
        msgID = MSGID_CONFIG_BACKEND_NO_WRITABILITY_MODE;
        unacceptableReason.append(getMessage(msgID, String.valueOf(backendDN)));
        return false;
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "configChangeIsAcceptable", e);

      msgID = MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_WRITABILITY;
      unacceptableReason.append(getMessage(msgID, String.valueOf(backendDN),
                                           stackTraceToSingleLineString(e)));
      return false;
    }


    // See if the entry contains an attribute that specifies the set of base DNs
    // for the backend.  If it does not, then skip it.
    msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_BASE_DNS;
    DNConfigAttribute baseDNStub =
         new DNConfigAttribute(ATTR_BACKEND_BASE_DN, getMessage(msgID), true,
                               true, true);
    try
    {
      DNConfigAttribute baseDNAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(baseDNStub);
      if (baseDNAttr == null)
      {
        // The attribute is not present.  We will not allow this.
        msgID = MSGID_CONFIG_BACKEND_NO_BASE_DNS;
        unacceptableReason.append(getMessage(msgID, String.valueOf(backendDN)));
        return false;
      }

      // See if the backend is registered with the server.  If it is, then
      // see what's changed and whether those changes are acceptable.
      Backend backend = registeredBackends.get(configEntryDN);
      if (backend != null)
      {
        LinkedHashSet<DN> removedDNs = new LinkedHashSet<DN>();
        for (DN dn : backend.getBaseDNs())
        {
          removedDNs.add(dn);
        }

        LinkedHashSet<DN> addedDNs = new LinkedHashSet<DN>();
        for (DN dn : baseDNAttr.pendingValues())
        {
          addedDNs.add(dn);
        }

        Iterator<DN> iterator = removedDNs.iterator();
        while (iterator.hasNext())
        {
          DN dn = iterator.next();
          if (addedDNs.remove(dn))
          {
            iterator.remove();
          }
        }

        for (DN dn : addedDNs)
        {
          try
          {
            DirectoryServer.registerBaseDN(dn, backend, false, true);
          }
          catch (DirectoryException de)
          {
            assert debugException(CLASS_NAME, "configChangeIsAcceptable", de);

            unacceptableReason.append(de.getMessage());
            return false;
          }
        }

        for (DN dn : removedDNs)
        {
          try
          {
            DirectoryServer.deregisterBaseDN(dn, true);
          }
          catch (DirectoryException de)
          {
            assert debugException(CLASS_NAME, "configChangeIsAcceptable", de);

            unacceptableReason.append(de.getMessage());
            return false;
          }
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "configChangeIsAcceptable", e);

      msgID = MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_BASE_DNS;
      unacceptableReason.append(getMessage(msgID, String.valueOf(backendDN),
                                stackTraceToSingleLineString(e)));
      return false;
    }


    // See if the entry contains an attribute that specifies the class name
    // for the backend implementation.  If it does, then load it and make sure
    // that it's a valid backend implementation.  There is no such attribute,
    // the specified class cannot be loaded, or it does not contain a valid
    // backend implementation, then log an error and skip it.
    String className;
    msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_CLASS;
    StringConfigAttribute classStub =
         new StringConfigAttribute(ATTR_BACKEND_CLASS, getMessage(msgID),
                                   true, false, true);
    try
    {
      StringConfigAttribute classAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(classStub);
      if (classAttr == null)
      {
        msgID = MSGID_CONFIG_BACKEND_NO_CLASS_ATTR;
        unacceptableReason.append(getMessage(msgID, String.valueOf(backendDN)));
        return false;
      }
      else
      {
        className = classAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "configChangeIsAcceptable", e);

      msgID = MSGID_CONFIG_BACKEND_CANNOT_GET_CLASS;
      unacceptableReason.append(getMessage(msgID, String.valueOf(backendDN),
                                           stackTraceToSingleLineString(e)));
      return false;
    }

    try
    {
      // FIXME -- Should we use a custom class loader for this?
      Class backendClass = Class.forName(className);
      if (! Backend.class.isAssignableFrom(backendClass))
      {
        msgID = MSGID_CONFIG_BACKEND_CLASS_NOT_BACKEND;
        unacceptableReason.append(getMessage(msgID, String.valueOf(className),
                                             String.valueOf(backendDN)));
        return false;
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "configChangeIsAcceptable", e);

      msgID = MSGID_CONFIG_BACKEND_CANNOT_INSTANTIATE;
      unacceptableReason.append(getMessage(msgID, String.valueOf(className),
                                           String.valueOf(backendDN),
                                           stackTraceToSingleLineString(e)));
      return false;
    }


    // If we've gotten to this point, then it is acceptable as far as we are
    // concerned.  If it is unacceptable according to the configuration for that
    // backend, then the backend itself will need to make that determination.
    return true;
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
    assert debugEnter(CLASS_NAME, "applyConfigurationChange",
                      String.valueOf(configEntry));

    DN                backendDN           = configEntry.getDN();
    Backend           backend             = registeredBackends.get(backendDN);
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Check to see if this entry appears to contain a backend configuration.
    // If not, log a warning and skip it.
    try
    {
      SearchFilter backendFilter =
           SearchFilter.createFilterFromString("(objectClass=" + OC_BACKEND +
                                               ")");
      if (! backendFilter.matchesEntry(configEntry.getEntry()))
      {
        int msgID = MSGID_CONFIG_BACKEND_ENTRY_DOES_NOT_HAVE_BACKEND_CONFIG;
        messages.add(getMessage(msgID, String.valueOf(backendDN)));
        resultCode = ResultCode.UNWILLING_TO_PERFORM;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyConfigurationChange", e);

      int msgID = MSGID_CONFIG_BACKEND_ERROR_INTERACTING_WITH_BACKEND_ENTRY;
      messages.add(getMessage(msgID, String.valueOf(backendDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // See if the entry contains an attribute that indicates whether the
    // backend should be enabled.
    boolean needToEnable = false;
    int msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_ENABLED;
    BooleanConfigAttribute enabledStub =
         new BooleanConfigAttribute(ATTR_BACKEND_ENABLED, getMessage(msgID),
                                    false);
    try
    {
      BooleanConfigAttribute enabledAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(enabledStub);
      if (enabledAttr == null)
      {
        // The attribute is not present.  We won't allow this.
        msgID = MSGID_CONFIG_BACKEND_NO_ENABLED_ATTR;
        messages.add(getMessage(msgID, String.valueOf(backendDN)));
        resultCode = ResultCode.UNWILLING_TO_PERFORM;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }
      else if (enabledAttr.pendingValue())
      {
        // The backend is marked as enabled.  See if that is already true.
        if (backend == null)
        {
          needToEnable = true;
        }
        else
        {
          // It's already enabled, so we don't need to do anything.
        }
      }
      else
      {
        // The backend is marked as disabled.  See if that is already true.
        if (backend != null)
        {
          // It isn't disabled, so we will do so now and deregister it from the
          // Directory Server.
          registeredBackends.remove(backendDN);
          DirectoryServer.deregisterBackend(backend);
          backend.finalizeBackend();

          // Remove the shared lock for this backend.
          try
          {
            String lockFile = LockFileManager.getBackendLockFileName(backend);
            StringBuilder failureReason = new StringBuilder();
            if (! LockFileManager.releaseLock(lockFile, failureReason))
            {
              msgID = MSGID_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK;
              String message = getMessage(msgID, backend.getBackendID(),
                                          String.valueOf(failureReason));
              logError(ErrorLogCategory.CONFIGURATION,
                       ErrorLogSeverity.SEVERE_WARNING, message, msgID);
              // FIXME -- Do we need to send an admin alert?
            }
          }
          catch (Exception e2)
          {
            assert debugException(CLASS_NAME, "applyConfigurationChange", e2);

            msgID = MSGID_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK;
            String message = getMessage(msgID, backend.getBackendID(),
                                        stackTraceToSingleLineString(e2));
            logError(ErrorLogCategory.CONFIGURATION,
                     ErrorLogSeverity.SEVERE_WARNING, message, msgID);
            // FIXME -- Do we need to send an admin alert?
          }

          return new ConfigChangeResult(resultCode, adminActionRequired,
                                        messages);
        }
        else
        {
          // It's already disabled, so we don't need to do anything.
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyConfigurationChange", e);

      msgID = MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_ENABLED_STATE;
      messages.add(getMessage(msgID, String.valueOf(backendDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // See if the entry contains an attribute that specifies the backend ID for
    // the backend.
    String backendID = null;
    msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_BACKEND_ID;
    StringConfigAttribute idStub =
         new StringConfigAttribute(ATTR_BACKEND_ID, getMessage(msgID), true,
                                   false, true);
    try
    {
      StringConfigAttribute idAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(idStub);
      if (idAttr == null)
      {
        // The attribute is not present.  We won't allow this.
        msgID = MSGID_CONFIG_BACKEND_NO_BACKEND_ID;
        messages.add(getMessage(msgID, String.valueOf(backendDN)));
        resultCode = ResultCode.UNWILLING_TO_PERFORM;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }
      else
      {
        backendID = idAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyConfigurationChange", e);

      msgID = MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_BACKEND_ID;
      messages.add(getMessage(msgID, String.valueOf(backendDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // See if the entry contains an attribute that specifies the writability
    // mode.
    LinkedHashSet<String> writabilityModes = new LinkedHashSet<String>(3);
    writabilityModes.add(WritabilityMode.ENABLED.toString());
    writabilityModes.add(WritabilityMode.DISABLED.toString());
    writabilityModes.add(WritabilityMode.INTERNAL_ONLY.toString());

    msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_WRITABILITY;
    WritabilityMode writabilityMode = null;
    MultiChoiceConfigAttribute writabilityStub =
         new MultiChoiceConfigAttribute(ATTR_BACKEND_WRITABILITY_MODE,
                                        getMessage(msgID), true, false, false,
                                        writabilityModes);
    try
    {
      MultiChoiceConfigAttribute writabilityAttr =
           (MultiChoiceConfigAttribute)
           configEntry.getConfigAttribute(writabilityStub);
      if (writabilityStub == null)
      {
        msgID = MSGID_CONFIG_BACKEND_NO_WRITABILITY_MODE;
        messages.add(getMessage(msgID, String.valueOf(backendDN)));

        resultCode = ResultCode.UNWILLING_TO_PERFORM;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }
      else
      {
        writabilityMode =
             WritabilityMode.modeForName(writabilityAttr.activeValue());
        if (writabilityMode == null)
        {
          msgID = MSGID_CONFIG_BACKEND_INVALID_WRITABILITY_MODE;
          messages.add(getMessage(msgID, String.valueOf(backendDN),
                            String.valueOf(writabilityAttr.activeValue())));
          resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
          return new ConfigChangeResult(resultCode, adminActionRequired,
                                        messages);
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyConfigurationChange", e);

      msgID = MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_WRITABILITY;
      messages.add(getMessage(msgID, String.valueOf(backendDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // See if the entry contains an attribute that specifies the base DNs for
    // the backend.
    DN[] baseDNs = null;
    msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_BASE_DNS;
    DNConfigAttribute baseDNStub =
         new DNConfigAttribute(ATTR_BACKEND_BASE_DN, getMessage(msgID), true,
                               true, true);
    try
    {
      DNConfigAttribute baseDNAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(baseDNStub);
      if (baseDNAttr == null)
      {
        // The attribute is not present.  We won't allow this.
        msgID = MSGID_CONFIG_BACKEND_NO_BASE_DNS;
        messages.add(getMessage(msgID, String.valueOf(backendDN)));
        resultCode = ResultCode.UNWILLING_TO_PERFORM;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }
      else
      {
        List<DN> baseList = baseDNAttr.pendingValues();
        baseDNs = new DN[baseList.size()];
        baseList.toArray(baseDNs);
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyConfigurationChange", e);

      msgID = MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_BASE_DNS;
      messages.add(getMessage(msgID, String.valueOf(backendDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // See if the entry contains an attribute that specifies the class name
    // for the backend implementation.  If it does, then load it and make sure
    // that it's a valid backend implementation.  There is no such attribute,
    // the specified class cannot be loaded, or it does not contain a valid
    // backend implementation, then log an error and skip it.
    String className;
    msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_CLASS;
    StringConfigAttribute classStub =
         new StringConfigAttribute(ATTR_BACKEND_CLASS, getMessage(msgID),
                                   true, false, true);
    try
    {
      StringConfigAttribute classAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(classStub);
      if (classAttr == null)
      {
        msgID = MSGID_CONFIG_BACKEND_NO_CLASS_ATTR;
        messages.add(getMessage(msgID, String.valueOf(backendDN)));
        resultCode = ResultCode.UNWILLING_TO_PERFORM;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }
      else
      {
        className = classAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyConfigurationChange", e);

      msgID = MSGID_CONFIG_BACKEND_CANNOT_GET_CLASS;
      messages.add(getMessage(msgID, String.valueOf(backendDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired,
                                    messages);
    }


    // See if this backend is currently active and if so if the name of the
    // class is the same.
    if (backend != null)
    {
      if (! className.equals(backend.getClass().getName()))
      {
        // It is not the same.  Try to load it and see if it is a valid backend
        // implementation.
        try
        {
          // FIXME -- Should we use a custom class loader for this?
          Class backendClass = Class.forName(className);
          if (Backend.class.isAssignableFrom(backendClass))
          {
            // It appears to be a valid backend class.  We'll return that the
            // change is successful, but indicate that some administrative
            // action is required.
            msgID = MSGID_CONFIG_BACKEND_ACTION_REQUIRED_TO_CHANGE_CLASS;
            messages.add(getMessage(msgID, String.valueOf(backendDN),
                                    backend.getClass().getName(), className));
            adminActionRequired = true;
            return new ConfigChangeResult(resultCode, adminActionRequired,
                                          messages);
          }
          else
          {
            // It is not a valid backend class.  This is an error.
            msgID = MSGID_CONFIG_BACKEND_CLASS_NOT_BACKEND;
            messages.add(getMessage(msgID, String.valueOf(className),
                                    String.valueOf(backendDN)));
            resultCode = ResultCode.CONSTRAINT_VIOLATION;
            return new ConfigChangeResult(resultCode, adminActionRequired,
                                          messages);
          }
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "applyConfigurationChange", e);

          msgID = MSGID_CONFIG_BACKEND_CANNOT_INSTANTIATE;
          messages.add(getMessage(msgID, String.valueOf(className),
                                  String.valueOf(backendDN),
                                  stackTraceToSingleLineString(e)));
          resultCode = DirectoryServer.getServerErrorResultCode();
          return new ConfigChangeResult(resultCode, adminActionRequired,
                                        messages);
        }
      }
    }


    // If we've gotten here, then that should mean that we need to enable the
    // backend.  Try to do so.
    if (needToEnable)
    {
      try
      {
        // FIXME -- Should we use a custom class loader for this?
        Class backendClass = Class.forName(className);
        backend = (Backend) backendClass.newInstance();
      }
      catch (Exception e)
      {
        // It is not a valid backend class.  This is an error.
        msgID = MSGID_CONFIG_BACKEND_CLASS_NOT_BACKEND;
        messages.add(getMessage(msgID, String.valueOf(className),
                                String.valueOf(backendDN)));
        resultCode = ResultCode.CONSTRAINT_VIOLATION;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }


      // Set the backend ID and writability mode for this backend.
      backend.setBackendID(backendID);
      backend.setWritabilityMode(writabilityMode);


      // Acquire a shared lock on this backend.  This will prevent operations
      // like LDIF import or restore from occurring while the backend is active.
      try
      {
        String lockFile = LockFileManager.getBackendLockFileName(backend);
        StringBuilder failureReason = new StringBuilder();
        if (! LockFileManager.acquireSharedLock(lockFile, failureReason))
        {
          msgID = MSGID_CONFIG_BACKEND_CANNOT_ACQUIRE_SHARED_LOCK;
          String message = getMessage(msgID, backendID,
                                      String.valueOf(failureReason));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          // FIXME -- Do we need to send an admin alert?

          resultCode = ResultCode.CONSTRAINT_VIOLATION;
          adminActionRequired = true;
          messages.add(message);
          return new ConfigChangeResult(resultCode, adminActionRequired,
                                        messages);
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "applyConfigurationChange", e);

        msgID = MSGID_CONFIG_BACKEND_CANNOT_ACQUIRE_SHARED_LOCK;
        String message = getMessage(msgID, backendID,
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.CONFIGURATION,
                 ErrorLogSeverity.SEVERE_WARNING, message, msgID);
        // FIXME -- Do we need to send an admin alert?

        resultCode = ResultCode.CONSTRAINT_VIOLATION;
        adminActionRequired = true;
        messages.add(message);
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }


      try
      {
        backend.initializeBackend(configEntry, baseDNs);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "applyConfigurationChange", e);

        msgID = MSGID_CONFIG_BACKEND_CANNOT_INITIALIZE;
        messages.add(getMessage(msgID, String.valueOf(className),
                                String.valueOf(backendDN),
                                stackTraceToSingleLineString(e)));
        resultCode = DirectoryServer.getServerErrorResultCode();

        try
        {
          String lockFile = LockFileManager.getBackendLockFileName(backend);
          StringBuilder failureReason = new StringBuilder();
          if (! LockFileManager.releaseLock(lockFile, failureReason))
          {
            msgID = MSGID_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK;
            String message = getMessage(msgID, backendID,
                                        String.valueOf(failureReason));
            logError(ErrorLogCategory.CONFIGURATION,
                     ErrorLogSeverity.SEVERE_WARNING, message, msgID);
            // FIXME -- Do we need to send an admin alert?
          }
        }
        catch (Exception e2)
        {
          assert debugException(CLASS_NAME, "applyConfigurationChange", e2);

          msgID = MSGID_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK;
          String message = getMessage(msgID, backendID,
                                      stackTraceToSingleLineString(e2));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          // FIXME -- Do we need to send an admin alert?
        }

        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }

      // Register the backend with the server.
      try
      {
        DirectoryServer.registerBackend(backend);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "applyConfigurationChange", e);

        msgID = MSGID_CONFIG_BACKEND_CANNOT_REGISTER_BACKEND;
        String message = getMessage(msgID, backendID,
                                    stackTraceToSingleLineString(e));

        resultCode = DirectoryServer.getServerErrorResultCode();
        messages.add(message);

        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        // FIXME -- Do we need to send an admin alert?

        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }


      registeredBackends.put(backendDN, backend);
    }
    else if ((resultCode == ResultCode.SUCCESS) && (backend != null))
    {
      // The backend is already enabled, so we may need to apply a
      // configuration change.  Check to see if the writability mode has been
      // changed.
      if (writabilityMode != backend.getWritabilityMode())
      {
        backend.setWritabilityMode(writabilityMode);
      }
    }


    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * Indicates whether the configuration entry that will result from a proposed
   * add is acceptable to this add listener.
   *
   * @param  configEntry         The configuration entry that will result from
   *                             the requested add.
   * @param  unacceptableReason  A buffer to which this method can append a
   *                             human-readable message explaining why the
   *                             proposed entry is not acceptable.
   *
   * @return  <CODE>true</CODE> if the proposed entry contains an acceptable
   *          configuration, or <CODE>false</CODE> if it does not.
   */
  public boolean configAddIsAcceptable(ConfigEntry configEntry,
                                       StringBuilder unacceptableReason)
  {
    assert debugEnter(CLASS_NAME, "configAddIsAcceptable",
                      String.valueOf(configEntry), "java.lang.StringBuilder");


    DN backendDN = configEntry.getDN();


    // Check to see if this entry appears to contain a backend configuration.
    // If not then fail.
    try
    {
      SearchFilter backendFilter =
           SearchFilter.createFilterFromString("(objectClass=" + OC_BACKEND +
                                               ")");
      if (! backendFilter.matchesEntry(configEntry.getEntry()))
      {
        int msgID = MSGID_CONFIG_BACKEND_ENTRY_DOES_NOT_HAVE_BACKEND_CONFIG;
        unacceptableReason.append(getMessage(msgID, String.valueOf(backendDN)));
        return false;
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "configAddIsAcceptable", e);

      int msgID = MSGID_CONFIG_BACKEND_ERROR_INTERACTING_WITH_BACKEND_ENTRY;
      unacceptableReason.append(getMessage(msgID, String.valueOf(backendDN),
                                           stackTraceToSingleLineString(e)));
      return false;
    }


    // See if the entry contains an attribute that indicates whether the
    // backend should be enabled.  If it does not, or if it is not set to
    // "true", then skip it.
    int msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_ENABLED;
    BooleanConfigAttribute enabledStub =
         new BooleanConfigAttribute(ATTR_BACKEND_ENABLED, getMessage(msgID),
                                    false);
    try
    {
      BooleanConfigAttribute enabledAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(enabledStub);
      if (enabledAttr == null)
      {
        // The attribute is not present.  We will not allow this.
        msgID = MSGID_CONFIG_BACKEND_NO_ENABLED_ATTR;
        unacceptableReason.append(getMessage(msgID, String.valueOf(backendDN)));
        return false;
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "configAddIsAcceptable", e);

      msgID = MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_ENABLED_STATE;
      unacceptableReason.append(getMessage(msgID, String.valueOf(backendDN),
                                stackTraceToSingleLineString(e)));
      return false;
    }


    // See if the entry contains an attribute that specifies the backend ID.  If
    // it does not, then skip it.
    msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_BACKEND_ID;
    StringConfigAttribute idStub =
         new StringConfigAttribute(ATTR_BACKEND_ID, getMessage(msgID), true,
                                   false, true);
    try
    {
      StringConfigAttribute idAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(idStub);
      if (idAttr == null)
      {
        // The attribute is not present.  We will not allow this.
        msgID = MSGID_CONFIG_BACKEND_NO_BACKEND_ID;
        unacceptableReason.append(getMessage(msgID, String.valueOf(backendDN)));
        return false;
      }
      else
      {
        String backendID = idAttr.activeValue();
        if (DirectoryServer.hasBackend(backendID))
        {
          msgID = MSGID_CONFIG_BACKEND_DUPLICATE_BACKEND_ID;
          unacceptableReason.append(getMessage(msgID,
                                               String.valueOf(backendDN)));
          return false;
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "configAddIsAcceptable", e);

      msgID = MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_BACKEND_ID;
      unacceptableReason.append(getMessage(msgID, String.valueOf(backendDN),
                                stackTraceToSingleLineString(e)));
      return false;
    }


    // See if the entry contains an attribute that specifies the writability
    // mode.  If it does not, then reject it.
    LinkedHashSet<String> writabilityModes = new LinkedHashSet<String>(3);
    writabilityModes.add(WritabilityMode.ENABLED.toString());
    writabilityModes.add(WritabilityMode.DISABLED.toString());
    writabilityModes.add(WritabilityMode.INTERNAL_ONLY.toString());
    msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_WRITABILITY;
    MultiChoiceConfigAttribute writabilityStub =
         new MultiChoiceConfigAttribute(ATTR_BACKEND_WRITABILITY_MODE,
                                        getMessage(msgID), true, false, false,
                                        writabilityModes);
    try
    {
      MultiChoiceConfigAttribute writabilityAttr =
           (MultiChoiceConfigAttribute)
           configEntry.getConfigAttribute(writabilityStub);
      if (writabilityAttr == null)
      {
        msgID = MSGID_CONFIG_BACKEND_NO_WRITABILITY_MODE;
        unacceptableReason.append(getMessage(msgID, String.valueOf(backendDN)));
        return false;
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "configAddIsAcceptable", e);

      msgID = MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_WRITABILITY;
      unacceptableReason.append(getMessage(msgID, String.valueOf(backendDN),
                                           stackTraceToSingleLineString(e)));
      return false;
    }


    // See if the entry contains an attribute that specifies the set of base DNs
    // for the backend.  If it does not, then skip it.
    List<DN> baseDNs = null;
    msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_BASE_DNS;
    DNConfigAttribute baseDNStub =
         new DNConfigAttribute(ATTR_BACKEND_BASE_DN, getMessage(msgID), true,
                               true, true);
    try
    {
      DNConfigAttribute baseDNAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(baseDNStub);
      if (baseDNAttr == null)
      {
        // The attribute is not present.  We will not allow this.
        msgID = MSGID_CONFIG_BACKEND_NO_BASE_DNS;
        unacceptableReason.append(getMessage(msgID, String.valueOf(backendDN)));
        return false;
      }
      else
      {
        baseDNs = baseDNAttr.pendingValues();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "configAddIsAcceptable", e);

      msgID = MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_BASE_DNS;
      unacceptableReason.append(getMessage(msgID, String.valueOf(backendDN),
                                stackTraceToSingleLineString(e)));
      return false;
    }


    // See if the entry contains an attribute that specifies the class name
    // for the backend implementation.  If it does, then load it and make sure
    // that it's a valid backend implementation.  There is no such attribute,
    // the specified class cannot be loaded, or it does not contain a valid
    // backend implementation, then log an error and skip it.
    String className;
    msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_CLASS;
    StringConfigAttribute classStub =
         new StringConfigAttribute(ATTR_BACKEND_CLASS, getMessage(msgID),
                                   true, false, true);
    try
    {
      StringConfigAttribute classAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(classStub);
      if (classAttr == null)
      {
        msgID = MSGID_CONFIG_BACKEND_NO_CLASS_ATTR;
        unacceptableReason.append(getMessage(msgID, String.valueOf(backendDN)));
        return false;
      }
      else
      {
        className = classAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "configAddIsAcceptable", e);

      msgID = MSGID_CONFIG_BACKEND_CANNOT_GET_CLASS;
      unacceptableReason.append(getMessage(msgID, String.valueOf(backendDN),
                                stackTraceToSingleLineString(e)));
      return false;
    }

    Backend backend;
    try
    {
      // FIXME -- Should we use a custom class loader for this?
      Class backendClass = Class.forName(className);
      backend = (Backend) backendClass.newInstance();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "configAddIsAcceptable", e);

      msgID = MSGID_CONFIG_BACKEND_CANNOT_INSTANTIATE;
      unacceptableReason.append(getMessage(msgID, String.valueOf(className),
                                String.valueOf(backendDN),
                                stackTraceToSingleLineString(e)));
      return false;
    }


    // If the backend is a configurable component, then make sure that its
    // configuration is valid.
    if (backend instanceof ConfigurableComponent)
    {
      ConfigurableComponent cc = (ConfigurableComponent) backend;
      LinkedList<String> errorMessages = new LinkedList<String>();
      if (! cc.hasAcceptableConfiguration(configEntry, errorMessages))
      {
        if (errorMessages.isEmpty())
        {
          msgID = MSGID_CONFIG_BACKEND_UNACCEPTABLE_CONFIG;
          unacceptableReason.append(getMessage(msgID,
                                               String.valueOf(configEntryDN)));
        }
        else
        {
          Iterator<String> iterator = errorMessages.iterator();
          unacceptableReason.append(iterator.next());
          while (iterator.hasNext())
          {
            unacceptableReason.append("  ");
            unacceptableReason.append(iterator.next());
          }
        }

        return false;
      }
    }


    // Make sure that all of the base DNs are acceptable for use in the server.
    for (DN baseDN : baseDNs)
    {
      try
      {
        DirectoryServer.registerBaseDN(baseDN, backend, false, true);
      }
      catch (DirectoryException de)
      {
        unacceptableReason.append(de.getMessage());
        return false;
      }
      catch (Exception e)
      {
        unacceptableReason.append(stackTraceToSingleLineString(e));
        return false;
      }
    }


    // If we've gotten to this point, then it is acceptable as far as we are
    // concerned.  If it is unacceptable according to the configuration for that
    // backend, then the backend itself will need to make that determination.
    return true;
  }



  /**
   * Attempts to apply a new configuration based on the provided added entry.
   *
   * @param  configEntry  The new configuration entry that contains the
   *                      configuration to apply.
   *
   * @return  Information about the result of processing the configuration
   *          change.
   */
  public ConfigChangeResult applyConfigurationAdd(ConfigEntry configEntry)
  {
    assert debugEnter(CLASS_NAME, "applyConfigurationAdd",
                      String.valueOf(configEntry));


    DN                backendDN           = configEntry.getDN();
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Register as a change listener for this backend entry so that we will
    // be notified of any changes that may be made to it.
    configEntry.registerChangeListener(this);


    // Check to see if this entry appears to contain a backend configuration.
    // If not then fail.
    try
    {
      SearchFilter backendFilter =
           SearchFilter.createFilterFromString("(objectClass=" + OC_BACKEND +
                                               ")");
      if (! backendFilter.matchesEntry(configEntry.getEntry()))
      {
        int msgID = MSGID_CONFIG_BACKEND_ENTRY_DOES_NOT_HAVE_BACKEND_CONFIG;
        messages.add(getMessage(msgID, String.valueOf(backendDN)));
        resultCode = ResultCode.UNWILLING_TO_PERFORM;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyConfigurationAdd", e);

      int msgID = MSGID_CONFIG_BACKEND_ERROR_INTERACTING_WITH_BACKEND_ENTRY;
      messages.add(getMessage(msgID, String.valueOf(backendDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // See if the entry contains an attribute that indicates whether the
    // backend should be enabled.  If it does not, or if it is not set to
    // "true", then skip it.
    int msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_ENABLED;
    BooleanConfigAttribute enabledStub =
         new BooleanConfigAttribute(ATTR_BACKEND_ENABLED, getMessage(msgID),
                                    false);
    try
    {
      BooleanConfigAttribute enabledAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(enabledStub);
      if (enabledAttr == null)
      {
        // The attribute is not present, so this backend will be disabled.  We
        // will log a message to indicate that it won't be enabled and return.
        msgID = MSGID_CONFIG_BACKEND_NO_ENABLED_ATTR;
        String message = getMessage(msgID, String.valueOf(backendDN));
        logError(ErrorLogCategory.CONFIGURATION,
                 ErrorLogSeverity.SEVERE_WARNING, message, msgID);
        messages.add(message);
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }
      else if (! enabledAttr.activeValue())
      {
        // The backend is explicitly disabled.  We will log a message to
        // indicate that it won't be enabled and return.
        msgID = MSGID_CONFIG_BACKEND_DISABLED;
        String message = getMessage(msgID, String.valueOf(backendDN));
        logError(ErrorLogCategory.CONFIGURATION,
                 ErrorLogSeverity.INFORMATIONAL, message, msgID);
        messages.add(message);
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyConfigurationAdd", e);

      msgID = MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_ENABLED_STATE;
      messages.add(getMessage(msgID, String.valueOf(backendDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // See if the entry contains an attribute that specifies the backend ID.  If
    // it does not, then skip it.
    String backendID;
    msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_BACKEND_ID;
    StringConfigAttribute idStub =
         new StringConfigAttribute(ATTR_BACKEND_ID, getMessage(msgID), true,
                                   false, true);
    try
    {
      StringConfigAttribute idAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(idStub);
      if (idAttr == null)
      {
        msgID = MSGID_CONFIG_BACKEND_NO_BACKEND_ID;
        String message = getMessage(msgID, String.valueOf(backendDN));
        logError(ErrorLogCategory.CONFIGURATION,
                 ErrorLogSeverity.SEVERE_WARNING, message, msgID);
        messages.add(message);
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }
      else
      {
        backendID = idAttr.pendingValue();
        if (DirectoryServer.hasBackend(backendID))
        {
          msgID = MSGID_CONFIG_BACKEND_DUPLICATE_BACKEND_ID;
          String message = getMessage(msgID, String.valueOf(backendDN));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          messages.add(message);
          return new ConfigChangeResult(resultCode, adminActionRequired,
                                        messages);
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyConfigurationAdd", e);

      msgID = MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_BACKEND_ID;
      messages.add(getMessage(msgID, String.valueOf(backendDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // See if the entry contains an attribute that specifies the writability
    // mode.
    LinkedHashSet<String> writabilityModes = new LinkedHashSet<String>(3);
    writabilityModes.add(WritabilityMode.ENABLED.toString());
    writabilityModes.add(WritabilityMode.DISABLED.toString());
    writabilityModes.add(WritabilityMode.INTERNAL_ONLY.toString());

    msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_WRITABILITY;
    WritabilityMode writabilityMode = null;
    MultiChoiceConfigAttribute writabilityStub =
         new MultiChoiceConfigAttribute(ATTR_BACKEND_WRITABILITY_MODE,
                                        getMessage(msgID), true, false, false,
                                        writabilityModes);
    try
    {
      MultiChoiceConfigAttribute writabilityAttr =
           (MultiChoiceConfigAttribute)
           configEntry.getConfigAttribute(writabilityStub);
      if (writabilityAttr == null)
      {
        msgID = MSGID_CONFIG_BACKEND_NO_WRITABILITY_MODE;
        messages.add(getMessage(msgID, String.valueOf(backendDN)));

        resultCode = ResultCode.UNWILLING_TO_PERFORM;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }
      else
      {
        writabilityMode =
             WritabilityMode.modeForName(writabilityAttr.activeValue());
        if (writabilityMode == null)
        {
          msgID = MSGID_CONFIG_BACKEND_INVALID_WRITABILITY_MODE;
          messages.add(getMessage(msgID, String.valueOf(backendDN),
                            String.valueOf(writabilityAttr.activeValue())));
          resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
          return new ConfigChangeResult(resultCode, adminActionRequired,
                                        messages);
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyConfigurationAdd", e);

      msgID = MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_WRITABILITY;
      messages.add(getMessage(msgID, String.valueOf(backendDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // See if the entry contains an attribute that specifies the base DNs for
    // the entry.  If it does not, then skip it.
    DN[] baseDNs = null;
    msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_BASE_DNS;
    DNConfigAttribute baseDNStub =
         new DNConfigAttribute(ATTR_BACKEND_BASE_DN, getMessage(msgID),
                               true, true, true);
    try
    {
      DNConfigAttribute baseDNAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(baseDNStub);
      if (baseDNAttr == null)
      {
        msgID = MSGID_CONFIG_BACKEND_NO_BASE_DNS;
        String message = getMessage(msgID, String.valueOf(backendDN));
        logError(ErrorLogCategory.CONFIGURATION,
                 ErrorLogSeverity.SEVERE_WARNING, message, msgID);
        messages.add(message);
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }
      else
      {
        List<DN> dnList = baseDNAttr.pendingValues();
        baseDNs = new DN[dnList.size()];
        dnList.toArray(baseDNs);
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyConfigurationAdd", e);

      msgID = MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_BASE_DNS;
      messages.add(getMessage(msgID, String.valueOf(backendDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // See if the entry contains an attribute that specifies the class name
    // for the backend implementation.  If it does, then load it and make sure
    // that it's a valid backend implementation.  There is no such attribute,
    // the specified class cannot be loaded, or it does not contain a valid
    // backend implementation, then log an error and skip it.
    String className;
    msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_CLASS;
    StringConfigAttribute classStub =
         new StringConfigAttribute(ATTR_BACKEND_CLASS, getMessage(msgID),
                                   true, false, true);
    try
    {
      StringConfigAttribute classAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(classStub);
      if (classAttr == null)
      {
        msgID = MSGID_CONFIG_BACKEND_NO_CLASS_ATTR;
        messages.add(getMessage(msgID, String.valueOf(backendDN)));
        resultCode = ResultCode.UNWILLING_TO_PERFORM;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }
      else
      {
        className = classAttr.activeValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyConfigurationAdd", e);

      msgID = MSGID_CONFIG_BACKEND_CANNOT_GET_CLASS;
      messages.add(getMessage(msgID, String.valueOf(backendDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    Backend backend;
    try
    {
      // FIXME -- Should we use a custom class loader for this?
      Class backendClass = Class.forName(className);
      backend = (Backend) backendClass.newInstance();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyConfigurationAdd", e);

      msgID = MSGID_CONFIG_BACKEND_CANNOT_INSTANTIATE;
      messages.add(getMessage(msgID, String.valueOf(className),
                              String.valueOf(backendDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired,
                                    messages);
    }


    // Set the backend ID and writability mode for this backend.
    backend.setBackendID(backendID);
    backend.setWritabilityMode(writabilityMode);


    // Acquire a shared lock on this backend.  This will prevent operations
    // like LDIF import or restore from occurring while the backend is active.
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.acquireSharedLock(lockFile, failureReason))
      {
        msgID = MSGID_CONFIG_BACKEND_CANNOT_ACQUIRE_SHARED_LOCK;
        String message = getMessage(msgID, backendID,
                                    String.valueOf(failureReason));
        logError(ErrorLogCategory.CONFIGURATION,
                 ErrorLogSeverity.SEVERE_WARNING, message, msgID);
        // FIXME -- Do we need to send an admin alert?

        resultCode = ResultCode.CONSTRAINT_VIOLATION;
        adminActionRequired = true;
        messages.add(message);
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyConfigurationAdd", e);

      msgID = MSGID_CONFIG_BACKEND_CANNOT_ACQUIRE_SHARED_LOCK;
      String message = getMessage(msgID, backendID,
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.CONFIGURATION,
               ErrorLogSeverity.SEVERE_WARNING, message, msgID);
      // FIXME -- Do we need to send an admin alert?

      resultCode = ResultCode.CONSTRAINT_VIOLATION;
      adminActionRequired = true;
      messages.add(message);
      return new ConfigChangeResult(resultCode, adminActionRequired,
                                    messages);
    }


    // Perform the necessary initialization for the backend entry.
    try
    {
      backend.initializeBackend(configEntry, baseDNs);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyConfigurationAdd", e);

      msgID = MSGID_CONFIG_BACKEND_CANNOT_INITIALIZE;
      messages.add(getMessage(msgID, String.valueOf(className),
                              String.valueOf(backendDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();

      try
      {
        String lockFile = LockFileManager.getBackendLockFileName(backend);
        StringBuilder failureReason = new StringBuilder();
        if (! LockFileManager.releaseLock(lockFile, failureReason))
        {
          msgID = MSGID_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK;
          String message = getMessage(msgID, backendID,
                                      String.valueOf(failureReason));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          // FIXME -- Do we need to send an admin alert?
        }
      }
      catch (Exception e2)
      {
        assert debugException(CLASS_NAME, "applyConfigurationAdd", e2);

        msgID = MSGID_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK;
        String message = getMessage(msgID, backendID,
                                    stackTraceToSingleLineString(e2));
        logError(ErrorLogCategory.CONFIGURATION,
                 ErrorLogSeverity.SEVERE_WARNING, message, msgID);
        // FIXME -- Do we need to send an admin alert?
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // At this point, the backend should be online.  Add it as one of the
    // registered backends for this backend config manager.
    try
    {
      DirectoryServer.registerBackend(backend);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyConfigurationAdd", e);

      msgID = MSGID_CONFIG_BACKEND_CANNOT_REGISTER_BACKEND;
      String message = getMessage(msgID, backendID,
                                  stackTraceToSingleLineString(e));

      resultCode = DirectoryServer.getServerErrorResultCode();
      messages.add(message);

      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
      // FIXME -- Do we need to send an admin alert?

      return new ConfigChangeResult(resultCode, adminActionRequired,
                                    messages);
    }

    registeredBackends.put(backendDN, backend);
    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * Indicates whether it is acceptable to remove the provided configuration
   * entry.
   *
   * @param  configEntry         The configuration entry that will be removed
   *                             from the configuration.
   * @param  unacceptableReason  A buffer to which this method can append a
   *                             human-readable message explaining why the
   *                             proposed delete is not acceptable.
   *
   * @return  <CODE>true</CODE> if the proposed entry may be removed from the
   *          configuration, or <CODE>false</CODE> if not.
   */
  public boolean configDeleteIsAcceptable(ConfigEntry configEntry,
                                          StringBuilder unacceptableReason)
  {
    assert debugEnter(CLASS_NAME, "configDeleteIsAcceptable",
                      String.valueOf(configEntry), "java.lang.StringBuilder");


    DN backendDN = configEntry.getDN();


    // See if this backend config manager has a backend registered with the
    // provided DN.  If not, then we don't care if the entry is deleted.  If we
    // do know about it, then that means that it is enabled and we will not
    // allow removing a backend that is enabled.
    Backend backend = registeredBackends.get(backendDN);
    if (backend == null)
    {
      return true;
    }


    // See if the backend has any subordinate backends.  If so, then it is not
    // acceptable to remove it.  Otherwise, it should be fine.
    Backend[] subBackends = backend.getSubordinateBackends();
    if ((subBackends == null) || (subBackends.length == 0))
    {
      return true;
    }
    else
    {
      int msgID = MSGID_CONFIG_BACKEND_CANNOT_REMOVE_BACKEND_WITH_SUBORDINATES;
      unacceptableReason.append(getMessage(msgID, String.valueOf(backendDN)));
      return false;
    }
  }



  /**
   * Attempts to apply a new configuration based on the provided deleted entry.
   *
   * @param  configEntry  The new configuration entry that has been deleted.
   *
   * @return  Information about the result of processing the configuration
   *          change.
   */
  public ConfigChangeResult applyConfigurationDelete(ConfigEntry configEntry)
  {
    assert debugEnter(CLASS_NAME, "applyConfigurationDelete",
                      String.valueOf(configEntry));


    DN                backendDN           = configEntry.getDN();
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // See if this backend config manager has a backend registered with the
    // provided DN.  If not, then we don't care if the entry is deleted.
    Backend backend = registeredBackends.get(backendDN);
    if (backend == null)
    {
      return new ConfigChangeResult(resultCode, adminActionRequired,
                                    messages);
    }


    // See if the backend has any subordinate backends.  If so, then it is not
    // acceptable to remove it.  Otherwise, it should be fine.
    Backend[] subBackends = backend.getSubordinateBackends();
    if ((subBackends == null) || (subBackends.length == 0))
    {
      registeredBackends.remove(backendDN);

      try
      {
        backend.finalizeBackend();
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "applyConfigurationDelete", e);
      }

      DirectoryServer.deregisterBackend(backend);

      return new ConfigChangeResult(resultCode, adminActionRequired,
                                    messages);
    }
    else
    {
      int msgID = MSGID_CONFIG_BACKEND_CANNOT_REMOVE_BACKEND_WITH_SUBORDINATES;
      messages.add(getMessage(msgID, String.valueOf(backendDN)));
      resultCode = resultCode.UNWILLING_TO_PERFORM;
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }
  }
}

