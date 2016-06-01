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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.types;

import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.ExtendedOperationHandler;
import org.opends.server.api.SASLMechanismHandler;
import org.opends.server.api.ServerShutdownListener;
import org.opends.server.core.DirectoryServer;

import com.forgerock.opendj.util.OperatingSystem;

/**
 * This interface defines a set of methods that may be used by
 * third-party code to obtain information about the core Directory
 * Server configuration and the instances of various kinds of
 * components that have registered themselves with the server.
 * <BR><BR>
 * Note that this interface is not intended to be implemented by any
 * third-party code.  It is merely used to control which elements are
 * intended for use by external classes.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class DirectoryConfig
{
  /**
   * Retrieves a reference to the Directory Server crypto manager.
   *
   * @return  A reference to the Directory Server crypto manager.
   */
  public static CryptoManager getCryptoManager()
  {
    return DirectoryServer.getCryptoManager();
  }

  /**
   * Retrieves the operating system on which the Directory Server is
   * running.
   *
   * @return  The operating system on which the Directory Server is
   *          running.
   */
  public static OperatingSystem getOperatingSystem()
  {
    return DirectoryServer.getOperatingSystem();
  }

  /**
   * Retrieves the path to the root directory for this instance of the
   * Directory Server.
   *
   * @return  The path to the root directory for this instance of the
   *          Directory Server.
  */
  public static String getServerRoot()
  {
    return DirectoryServer.getServerRoot();
  }

  /**
   * Retrieves the time that the Directory Server was started, in
   * milliseconds since the epoch.
   *
   * @return  The time that the Directory Server was started, in
   *          milliseconds since the epoch.
   */
  public static long getStartTime()
  {
    return DirectoryServer.getStartTime();
  }

  /**
   * Retrieves the time that the Directory Server was started,
   * formatted in UTC.
   *
   * @return  The time that the Directory Server was started,
   *          formatted in UTC.
   */
  public static String getStartTimeUTC()
  {
    return DirectoryServer.getStartTimeUTC();
  }

  /**
   * Retrieves a reference to the Directory Server schema.
   *
   * @return  A reference to the Directory Server schema.
   */
  public static Schema getSchema()
  {
    return DirectoryServer.getSchema();
  }

  /**
   * Registers the provided alert generator with the Directory Server.
   *
   * @param  alertGenerator  The alert generator to register.
   */
  public static void registerAlertGenerator(
                                AlertGenerator alertGenerator)
  {
    DirectoryServer.registerAlertGenerator(alertGenerator);
  }

  /**
   * Deregisters the provided alert generator with the Directory
   * Server.
   *
   * @param  alertGenerator  The alert generator to deregister.
   */
  public static void deregisterAlertGenerator(
                                AlertGenerator alertGenerator)
  {
    DirectoryServer.deregisterAlertGenerator(alertGenerator);
  }

  /**
   * Sends an alert notification with the provided information.
   *
   * @param  generator     The alert generator that created the alert.
   * @param  alertType     The alert type name for this alert.
   * @param  alertMessage  A message (possibly <CODE>null</CODE>) that
   *                       can provide more information about this
   *                       alert.
   */
  public static void
       sendAlertNotification(AlertGenerator generator,
                             String alertType,
                             LocalizableMessage alertMessage)
  {
    DirectoryServer.sendAlertNotification(generator, alertType,
            alertMessage);
  }

  /**
   * Retrieves the result code that should be used when the Directory
   * Server encounters an internal server error.
   *
   * @return  The result code that should be used when the Directory
   *          Server encounters an internal server error.
   */
  public static ResultCode getServerErrorResultCode()
  {
    return DirectoryServer.getServerErrorResultCode();
  }

  /**
   * Retrieves the entry with the requested DN.  It will first
   * determine which backend should be used for this DN and will then
   * use that backend to retrieve the entry.  The caller must already
   * hold the appropriate lock on the specified entry.
   *
   * @param  entryDN  The DN of the entry to retrieve.
   *
   * @return  The requested entry, or <CODE>null</CODE> if it does not
   *          exist.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to retrieve the entry.
   */
  public static Entry getEntry(DN entryDN)
         throws DirectoryException
  {
    return DirectoryServer.getEntry(entryDN);
  }

  /**
   * Indicates whether the specified entry exists in the Directory
   * Server.  The caller is not required to hold any locks when
   * invoking this method.
   *
   * @param  entryDN  The DN of the entry for which to make the
   *                  determination.
   *
   * @return  <CODE>true</CODE> if the specified entry exists in one
   *          of the backends, or <CODE>false</CODE> if it does not.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to make the determination.
   */
  public static boolean entryExists(DN entryDN)
         throws DirectoryException
  {
    return DirectoryServer.entryExists(entryDN);
  }

  /**
   * Retrieves the set of OIDs for the supported controls registered
   * with the Directory Server.
   *
   * @return  The set of OIDS for the supported controls registered
   *          with the Directory Server.
   */
  public static Set<String> getSupportedControls()
  {
    return DirectoryServer.getSupportedControls();
  }

  /**
   * Indicates whether the specified OID is registered with the
   * Directory Server as a supported control.
   *
   * @param  controlOID  The OID of the control for which to make the
   *                     determination.
   *
   * @return  <CODE>true</CODE> if the specified OID is registered
   *          with the server as a supported control, or
   *          <CODE>false</CODE> if not.
   */
  public static boolean isSupportedControl(String controlOID)
  {
    return DirectoryServer.isSupportedControl(controlOID);
  }

  /**
   * Registers the provided OID as a supported control for the
   * Directory Server.  This will have no effect if the specified
   * control OID is already present in the list of supported controls.
   *
   * @param  controlOID  The OID of the control to register as a
   *                     supported control.
   */
  public static void registerSupportedControl(String controlOID)
  {
    DirectoryServer.registerSupportedControl(controlOID);
  }

  /**
   * Deregisters the provided OID as a supported control for the
   * Directory Server.  This will have no effect if the specified
   * control OID is not present in the list of supported controls.
   *
   * @param  controlOID  The OID of the control to deregister as a
   *                     supported control.
   */
  public static void
       deregisterSupportedControl(String controlOID)
  {
    DirectoryServer.deregisterSupportedControl(controlOID);
  }

  /**
   * Retrieves the set of OIDs for the supported features registered
   * with the Directory Server.
   *
   * @return  The set of OIDs for the supported features registered
   *          with the Directory Server.
   */
  public static Set<String> getSupportedFeatures()
  {
    return DirectoryServer.getSupportedFeatures();
  }

  /**
   * Indicates whether the specified OID is registered with the
   * Directory Server as a supported feature.
   *
   * @param  featureOID  The OID of the feature for which to make the
   *                     determination.
   *
   * @return  <CODE>true</CODE> if the specified OID is registered
   *          with the server as a supported feature, or
   *          <CODE>false</CODE> if not.
   */
  public static boolean isSupportedFeature(String featureOID)
  {
    return DirectoryServer.isSupportedFeature(featureOID);
  }

  /**
   * Registers the provided OID as a supported feature for the
   * Directory Server.  This will have no effect if the specified
   * feature OID is already present in the list of supported features.
   *
   * @param  featureOID  The OID of the feature to register as a
   *                     supported feature.
   */
  public static void registerSupportedFeature(String featureOID)
  {
    DirectoryServer.registerSupportedFeature(featureOID);
  }

  /**
   * Deregisters the provided OID as a supported feature for the
   * Directory Server.  This will have no effect if the specified
   * feature OID is not present in the list of supported features.
   *
   * @param  featureOID  The OID of the feature to deregister as a
   *                     supported feature.
   */
  public static void
       deregisterSupportedFeature(String featureOID)
  {
    DirectoryServer.deregisterSupportedFeature(featureOID);
  }

  /**
   * Retrieves the handler for the extended operation for the provided
   * extended operation OID.
   *
   * @param  oid  The OID of the extended operation to retrieve.
   *
   * @return  The handler for the specified extended operation, or
   *          <CODE>null</CODE> if there is none.
   */
  public static ExtendedOperationHandler<?> getExtendedOperationHandler(String oid)
  {
    return DirectoryServer.getExtendedOperationHandler(oid);
  }

  /**
   * Registers the provided extended operation handler with the
   * Directory Server.
   *
   * @param  oid      The OID for the extended operation to register.
   * @param  handler  The extended operation handler to register with
   *                  the Directory Server.
   */
  public static void registerSupportedExtension(String oid, ExtendedOperationHandler<?> handler)
  {
    DirectoryServer.registerSupportedExtension(oid, handler);
  }

  /**
   * Deregisters the provided extended operation handler with the
   * Directory Server.
   *
   * @param  oid  The OID for the extended operation to deregister.
   */
  public static void deregisterSupportedExtension(String oid)
  {
    DirectoryServer.deregisterSupportedExtension(oid);
  }

  /**
   * Retrieves the handler for the specified SASL mechanism.
   *
   * @param  name  The name of the SASL mechanism to retrieve.
   *
   * @return  The handler for the specified SASL mechanism, or
   *          <CODE>null</CODE> if there is none.
   */
  public static SASLMechanismHandler<?> getSASLMechanismHandler(String name)
  {
    return DirectoryServer.getSASLMechanismHandler(name);
  }

  /**
   * Registers the provided SASL mechanism handler with the Directory
   * Server.
   *
   * @param  name     The name of the SASL mechanism to be registered.
   * @param  handler  The SASL mechanism handler to register with the
   *                  Directory Server.
   */
  public static void registerSASLMechanismHandler(String name, SASLMechanismHandler<?> handler)
  {
    DirectoryServer.registerSASLMechanismHandler(name, handler);
  }

  /**
   * Deregisters the provided SASL mechanism handler with the
   * Directory Server.
   *
   * @param  name  The name of the SASL mechanism to be deregistered.
   */
  public static void deregisterSASLMechanismHandler(String name)
  {
    DirectoryServer.deregisterSASLMechanismHandler(name);
  }

  /**
   * Registers the provided shutdown listener with the Directory
   * Server so that it will be notified when the server shuts down.
   *
   * @param  listener  The shutdown listener to register with the
   *                   Directory Server.
   */
  public static void
       registerShutdownListener(ServerShutdownListener listener)
  {
    DirectoryServer.registerShutdownListener(listener);
  }

  /**
   * Deregisters the provided shutdown listener with the Directory
   * Server.
   *
   * @param  listener  The shutdown listener to deregister with the
   *                   Directory Server.
   */
  public static void
       deregisterShutdownListener(ServerShutdownListener listener)
  {
    DirectoryServer.deregisterShutdownListener(listener);
  }

  /**
   * Retrieves the full version string for the Directory Server.
   *
   * @return  The full version string for the Directory Server.
   */
  public static String getVersionString()
  {
    return DirectoryServer.getVersionString();
  }
}
