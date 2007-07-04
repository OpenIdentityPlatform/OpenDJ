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

package org.opends.quicksetup.installer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

import javax.naming.ldap.InitialLdapContext;

import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.i18n.ResourceProvider;
import org.opends.quicksetup.webstart.JnlpProperties;
import org.opends.quicksetup.util.Utils;
import org.opends.server.admin.DefaultBehaviorException;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.ldap.LDAPManagementContext;
import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;
import org.opends.server.admin.std.client.*;
import org.opends.server.admin.std.meta.*;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.messages.CoreMessages;
import org.opends.server.messages.ReplicationMessages;
import org.opends.server.tools.ConfigureDS;
import org.opends.server.tools.ConfigureWindowsService;
import org.opends.server.tools.ImportLDIF;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.ObjectClass;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.StaticUtils;

/**
 * This is the only class that uses classes in org.opends.server (excluding the
 * case of DynamicConstants, SetupUtils, OperatingSystem and CertificateManager
 * which are already included in quicksetup.jar).
 *
 * Important note: do not include references to this class until OpenDS.jar has
 * been loaded. These classes must be loaded during Runtime.
 * The code is written in a way that when we execute the code that uses these
 * classes the required jar files are already loaded. However these jar files
 * are not necessarily loaded when we create this class.
 */
public class InstallerHelper implements JnlpProperties {
  private static final Logger LOG = Logger.getLogger(
      InstallerHelper.class.getName());

  private static final int MAX_ID_VALUE = Short.MAX_VALUE;
  private static final String DOMAIN_BASE_NAME = "domain ";

  /**
   * Invokes the method ConfigureDS.configMain with the provided parameters.
   * @param args the arguments to be passed to ConfigureDS.configMain.
   * @return the return code of the ConfigureDS.configMain method.
   * @throws ApplicationException if something goes wrong.
   * @see org.opends.server.tools.ConfigureDS#configMain(String[]).
   */
  public int invokeConfigureServer(String[] args) throws ApplicationException {
    return ConfigureDS.configMain(args);
  }

  /**
   * Invokes the method ImportLDIF.mainImportLDIF with the provided parameters.
   * @param args the arguments to be passed to ImportLDIF.mainImportLDIF.
   * @return the return code of the ImportLDIF.mainImportLDIF method.
   * @throws org.opends.quicksetup.ApplicationException if something goes wrong.
   * @see org.opends.server.tools.ImportLDIF#mainImportLDIF(String[]).
   */
  public int invokeImportLDIF(String[] args) throws ApplicationException {
    return ImportLDIF.mainImportLDIF(args);
  }

  /**
   * Returns the Message ID that corresponds to a successfully started server.
   * @return the Message ID that corresponds to a successfully started server.
   */
  public String getStartedId()
  {
    return String.valueOf(CoreMessages.MSGID_DIRECTORY_SERVER_STARTED);
  }

  /**
   * This methods enables this server as a Windows service.
   * @throws ApplicationException if something goes wrong.
   */
  public void enableWindowsService() throws ApplicationException {
    int code = ConfigureWindowsService.enableService(System.out, System.err);

    String errorMessage = getMsg("error-enabling-windows-service");

    switch (code) {
      case
        ConfigureWindowsService.SERVICE_ENABLE_SUCCESS:
        break;
      case
        ConfigureWindowsService.SERVICE_ALREADY_ENABLED:
        break;
      default:
        throw new ApplicationException(
                ApplicationException.Type.WINDOWS_SERVICE_ERROR,
                errorMessage, null);
    }
  }

  /**
   * This method disables this server as a Windows service.
   * @throws ApplicationException if something goes worong.
   */
  public void disableWindowsService() throws ApplicationException
  {
    int code = ConfigureWindowsService.disableService(System.out, System.err);
    if (code == ConfigureWindowsService.SERVICE_DISABLE_ERROR) {
      throw new ApplicationException(
              ApplicationException.Type.WINDOWS_SERVICE_ERROR,
              getMsg("error-disabling-windows-service"), null);
    }
  }

  private String getThrowableMsg(String key, Throwable t)
  {
    return getThrowableMsg(key, null, t);
  }

  private String getThrowableMsg(String key, String[] args, Throwable t)
  {
    return Utils.getThrowableMsg(ResourceProvider.getInstance(), key, args, t);
  }

  /**
   * Creates a template LDIF file with an entry that has as dn the provided
   * baseDn.
   * @param baseDn the dn of the entry that will be created in the LDIF file.
   * @return the File object pointing to the created temporary file.
   * @throws ApplicationException if something goes wrong.
   */
  public File createBaseEntryTempFile(String baseDn)
          throws ApplicationException {
    File ldifFile;
    try
    {
      ldifFile = File.createTempFile("opends-base-entry", ".ldif");
      ldifFile.deleteOnExit();
    } catch (IOException ioe)
    {
      String failedMsg = getThrowableMsg("error-creating-temp-file", null, ioe);
      throw new ApplicationException(
          ApplicationException.Type.FILE_SYSTEM_ERROR, failedMsg, ioe);
    }

    try
    {
      LDIFExportConfig exportConfig = new LDIFExportConfig(
          ldifFile.getAbsolutePath(), ExistingFileBehavior.OVERWRITE);

      LDIFWriter writer = new LDIFWriter(exportConfig);

      DN dn = DN.decode(baseDn);
      Entry entry = StaticUtils.createEntry(dn);

      writer.writeEntry(entry);
      writer.close();
    } catch (DirectoryException de) {
      throw new ApplicationException(
              ApplicationException.Type.CONFIGURATION_ERROR,
              getThrowableMsg("error-importing-ldif", null, de), de);
    } catch (LDIFException le) {
      throw new ApplicationException(
              ApplicationException.Type.CONFIGURATION_ERROR,
              getThrowableMsg("error-importing-ldif", null, le), le);
    } catch (IOException ioe) {
      throw new ApplicationException(
              ApplicationException.Type.CONFIGURATION_ERROR,
              getThrowableMsg("error-importing-ldif", null, ioe), ioe);
    } catch (Throwable t) {
      throw new ApplicationException(
              ApplicationException.Type.BUG, getThrowableMsg(
              "bug-msg", t), t);
    }
    return ldifFile;
  }

  /**
   * Configures the replication on a given server.
   * @param remoteCtx the conection to the server where we want to configure
   * the replication.
   * @param dns the suffix base dns for which we want to configure the
   * replication.
   * @param replicationServers a Map where the key value is the base dn and
   * the value is the list of replication servers for that base dn (or domain).
   * @param replicationPort the replicationPort of the server that is being
   * configured (it might not exist and the user specified it in the setup).
   * @param serverDisplay the server display.
   * @param usedReplicationServerIds the list of replication server ids that
   * are already used.
   * @param usedServerIds the list of server ids (domain ids) that
   * are already used.
   * @throws ApplicationException if something goes wrong.
   * @return a ConfiguredReplication object describing what has been configured.
   */
  public ConfiguredReplication configureReplication(
      InitialLdapContext remoteCtx, Set<String> dns,
      Map<String,Set<String>> replicationServers,
      int replicationPort, String serverDisplay,
      Set<Integer> usedReplicationServerIds, Set<Integer> usedServerIds)
  throws ApplicationException
  {
    boolean synchProviderCreated;
    boolean synchProviderEnabled;
    boolean replicationServerCreated;
    try
    {
      ManagementContext mCtx = LDAPManagementContext.createFromContext(
          JNDIDirContextAdaptor.adapt(remoteCtx));
      RootCfgClient root = mCtx.getRootConfiguration();

      /*
       * Configure Synchronization plugin.
       */
      MultimasterSynchronizationProviderCfgClient sync = null;
      try
      {
        sync = (MultimasterSynchronizationProviderCfgClient)
        root.getSynchronizationProvider("Multimaster Synchronization");
      }
      catch (ManagedObjectNotFoundException monfe)
      {
        // It does not exist.
      }
      if (sync == null)
      {
        MultimasterSynchronizationProviderCfgDefn provider =
          MultimasterSynchronizationProviderCfgDefn.getInstance();
        sync = root.createSynchronizationProvider(provider,
            "Multimaster Synchronization",
            new ArrayList<DefaultBehaviorException>());
        sync.setJavaImplementationClass(
            "org.opends.server.replication.plugin.MultimasterReplication");
        sync.setEnabled(Boolean.TRUE);
        synchProviderCreated = true;
        synchProviderEnabled = false;
      }
      else
      {
        synchProviderCreated = false;
        if (!sync.isEnabled())
        {
          sync.setEnabled(Boolean.TRUE);
          synchProviderEnabled = true;
        }
        else
        {
          synchProviderEnabled = false;
        }
      }
      sync.commit();

      /*
       * Configure the replication server.
       */
      ReplicationServerCfgClient replicationServer = null;

      if (!sync.hasReplicationServer())
      {
        int id = getReplicationId(usedReplicationServerIds);
        usedReplicationServerIds.add(id);
        replicationServer = sync.createReplicationServer(
            ReplicationServerCfgDefn.getInstance(),
            new ArrayList<DefaultBehaviorException>());
        replicationServer.setReplicationServerId(id);
        replicationServer.setReplicationPort(replicationPort);
        replicationServerCreated = true;
      }
      else
      {
        replicationServer = sync.getReplicationServer();
        usedReplicationServerIds.add(
            replicationServer.getReplicationServerId());
        replicationServerCreated = false;
      }

      Set<String> servers = replicationServer.getReplicationServer();
      if (servers == null)
      {
        servers = new HashSet<String>();
      }
      Set<String> oldServers = new HashSet<String>();
      oldServers.addAll(servers);
      for (Set<String> rs : replicationServers.values())
      {
        servers.addAll(rs);
      }

      replicationServer.setReplicationServer(servers);
      replicationServer.commit();

      Set<String> newReplicationServers = new HashSet<String>();
      newReplicationServers.addAll(servers);
      newReplicationServers.removeAll(oldServers);

      /*
       * Create the domains
       */
      String[] domainNames = sync.listMultimasterDomains();
      if (domainNames == null)
      {
        domainNames = new String[]{};
      }
      Set<ConfiguredDomain> domainsConf = new HashSet<ConfiguredDomain>();
      MultimasterDomainCfgClient[] domains =
        new MultimasterDomainCfgClient[domainNames.length];
      for (int i=0; i<domains.length; i++)
      {
        domains[i] = sync.getMultimasterDomain(domainNames[i]);
      }
      for (String dn : dns)
      {
        MultimasterDomainCfgClient domain = null;
        boolean isCreated;
        String domainName = null;
        for (int i=0; i<domains.length && (domain == null); i++)
        {
          if (Utils.areDnsEqual(dn,
              domains[i].getReplicationDN().toString()))
          {
            domain = domains[i];
            domainName = domainNames[i];
          }
        }
        if (domain == null)
        {
          int domainId = getReplicationId(usedServerIds);
          usedServerIds.add(domainId);
          domainName = getDomainName(domainNames, domainId);
          domain = sync.createMultimasterDomain(
              MultimasterDomainCfgDefn.getInstance(), domainName,
              new ArrayList<DefaultBehaviorException>());
          domain.setServerId(domainId);
          domain.setReplicationDN(DN.decode(dn));
          isCreated = true;
        }
        else
        {
          isCreated = false;
        }
        oldServers = domain.getReplicationServer();
        if (oldServers == null)
        {
          oldServers = new TreeSet<String>();
        }
        servers = replicationServers.get(dn);
        domain.setReplicationServer(servers);
        usedServerIds.add(domain.getServerId());

        domain.commit();
        servers.removeAll(oldServers);
        ConfiguredDomain domainConf = new ConfiguredDomain(domainName,
            isCreated, servers);
        domainsConf.add(domainConf);
      }
      return new ConfiguredReplication(synchProviderCreated,
          synchProviderEnabled, replicationServerCreated, newReplicationServers,
          domainsConf);
    }
    catch (Throwable t)
    {
      String errorMessage = getMsg("error-configuring-remote-generic",
          serverDisplay, t.toString());
      throw new ApplicationException(
          ApplicationException.Type.CONFIGURATION_ERROR, errorMessage, t);
    }
  }

  /**
   * Configures the replication on a given server.
   * @param remoteCtx the conection to the server where we want to configure
   * the replication.
   * @param replConf the object describing what was configured.
   * @param serverDisplay the server display.
   * @throws ApplicationException if something goes wrong.
   */
  public void unconfigureReplication(
      InitialLdapContext remoteCtx, ConfiguredReplication replConf,
      String serverDisplay)
  throws ApplicationException
  {
    try
    {
      ManagementContext mCtx = LDAPManagementContext.createFromContext(
          JNDIDirContextAdaptor.adapt(remoteCtx));
      RootCfgClient root = mCtx.getRootConfiguration();

      /*
       * Unconfigure Synchronization plugin.
       */
      if (replConf.isSynchProviderCreated())
      {
        MultimasterSynchronizationProviderCfgClient sync = null;
        try
        {
          root.removeSynchronizationProvider("Multimaster Synchronization");
        }
        catch (ManagedObjectNotFoundException monfe)
        {
          // It does not exist.
        }
      }
      else
      {
        try
        {
          MultimasterSynchronizationProviderCfgClient sync =
            (MultimasterSynchronizationProviderCfgClient)
            root.getSynchronizationProvider("Multimaster Synchronization");
          if (replConf.isSynchProviderEnabled())
          {
            sync.setEnabled(Boolean.FALSE);
          }
          if (replConf.isReplicationServerCreated())
          {
            sync.removeReplicationServer();
          }
          else if (sync.hasReplicationServer())
          {
            ReplicationServerCfgClient replicationServer =
              sync.getReplicationServer();
            Set<String> replServers = replicationServer.getReplicationServer();
            if (replServers != null)
            {
              replServers.removeAll(replConf.getNewReplicationServers());
              replicationServer.commit();
            }
          }
          for (ConfiguredDomain domain : replConf.getDomainsConf())
          {
            if (domain.isCreated())
            {
              sync.removeMultimasterDomain(domain.getDomainName());
            }
            else
            {
              try
              {
                MultimasterDomainCfgClient d =
                  sync.getMultimasterDomain(domain.getDomainName());
                Set<String> replServers = d.getReplicationServer();
                if (replServers != null)
                {
                  replServers.removeAll(domain.getAddedReplicationServers());
                  d.commit();
                }
              }
              catch (ManagedObjectNotFoundException monfe)
              {
                // It does not exist.
              }
            }
          }
          sync.commit();
        }
        catch (ManagedObjectNotFoundException monfe)
        {
          // It does not exist.
        }
      }
    }
    catch (Throwable t)
    {
      String errorMessage = getMsg("error-configuring-remote-generic",
          serverDisplay, t.toString());
      throw new ApplicationException(
          ApplicationException.Type.CONFIGURATION_ERROR, errorMessage, t);
    }
  }

  /**
   * For the given state provided by a Task tells if the task is done or not.
   * @param sState the String representing the task state.
   * @return <CODE>true</CODE> if the task is done and <CODE>false</CODE>
   * otherwise.
   */
  public boolean isDone(String sState)
  {
    TaskState state = TaskState.fromString(sState);
    return TaskState.isDone(state);
  }

  /**
   * For the given state provided by a Task tells if the task is successful or
   * not.
   * @param sState the String representing the task state.
   * @return <CODE>true</CODE> if the task is successful and <CODE>false</CODE>
   * otherwise.
   */
  public boolean isSuccessful(String sState)
  {
    TaskState state = TaskState.fromString(sState);
    return TaskState.isSuccessful(state);
  }

  /**
   * For the given state provided by a Task tells if the task is complete with
   * errors or not.
   * @param sState the String representing the task state.
   * @return <CODE>true</CODE> if the task is complete with errors and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isCompletedWithErrors(String sState)
  {
    TaskState state = TaskState.fromString(sState);
    return state == TaskState.COMPLETED_WITH_ERRORS;
  }

  /**
   * For the given state provided by a Task tells if the task is stopped by
   * error or not.
   * @param sState the String representing the task state.
   * @return <CODE>true</CODE> if the task is stopped by error and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isStoppedByError(String sState)
  {
    TaskState state = TaskState.fromString(sState);
    return state == TaskState.STOPPED_BY_ERROR;
  }

  /**
   * Tells whether the provided log message corresponds to a peers not found
   * error during the initialization of a replica or not.
   * @param logMsg the log message.
   * @return <CODE>true</CODE> if the log message corresponds to a peers not
   * found error during initialization and <CODE>false</CODE> otherwise.
   */
  public boolean isPeersNotFoundError(String logMsg)
  {
    return logMsg.indexOf(
        "="+ReplicationMessages.MSGID_NO_REACHABLE_PEER_IN_THE_DOMAIN) != -1;
  }
  private void addConfigEntry(ConfigFileHandler configFileHandler, DN dn,
      String[] ocs, String[] attributeNames, String[][] attributeValues)
  throws DirectoryException
  {
    HashMap<ObjectClass,String> objectClasses =
      new HashMap<ObjectClass,String>();
    HashMap<AttributeType,List<Attribute>> userAttributes =
      new HashMap<AttributeType,List<Attribute>>();
    HashMap<AttributeType,List<Attribute>> operationalAttributes =
      new HashMap<AttributeType,List<Attribute>>();

    for (int j=0; j<ocs.length; j++)
    {
      String ocName = ocs[j];
      ObjectClass objectClass = DirectoryServer.getObjectClass(ocName);
      if (objectClass == null)
      {
        objectClass = DirectoryServer.getDefaultObjectClass(ocName);
      }
      objectClasses.put(objectClass, ocName);
    }
    for (int j=0; j<attributeNames.length; j++)
    {
      String attrName = attributeNames[j];
      AttributeType attrType = DirectoryServer.getAttributeType(attrName);
      if (attrType == null)
      {
        attrType = DirectoryServer.getDefaultAttributeType(attrName);
      }
      String[] attrValues = attributeValues[j];
      LinkedHashSet<AttributeValue> valueSet =
        new LinkedHashSet<AttributeValue>();
      for (int k=0; k<attrValues.length; k++)
      {
        AttributeValue attributeValue = new AttributeValue(attrType,
            attrValues[k]);
        valueSet.add(attributeValue);
      }
      ArrayList<Attribute> attrList = new ArrayList<Attribute>();
      attrList.add(new Attribute(attrType, attrName, null, valueSet));
      userAttributes.put(attrType, attrList);
    }
    Entry entry = new Entry(dn, objectClasses, userAttributes,
        operationalAttributes);
    configFileHandler.addEntry(entry, null);
  }

  private int getReplicationId(Set<Integer> usedIds)
  {
    Random r = new Random();
    int id = 0;
    while ((id == 0) || usedIds.contains(id))
    {
      id = r.nextInt(MAX_ID_VALUE);
    }
    return id;
  }

  private String getMsg(String key, String ... args)
  {
    return ResourceProvider.getInstance().getMsg(key, args);
  }

  private String getDomainName(String[] existingDomains, int newDomainId)
  {
    String domainName = DOMAIN_BASE_NAME+newDomainId;
    boolean nameExists = true;
    int j = 0;
    while (nameExists)
    {
      boolean found = false;
      for (int i=0; i<existingDomains.length && !found; i++)
      {
        found = existingDomains[i].equalsIgnoreCase(domainName);
      }
      if (found)
      {
        domainName = DOMAIN_BASE_NAME+newDomainId+"-"+j;
      }
      else
      {
        nameExists = false;
      }
      j++;
    }
    return domainName;
  }
}

/**
 * A class describing a replication domain.
 *
 */
class DomainEntry
{
  private String name;
  private int replicationId;
  private String baseDn;
  private Set<String> replicationServers;
  /**
   * The constructor of the domain entry.
   * @param name the name of the domain.
   * @param replicationId the replicationId of the domain.
   * @param baseDn the base dn of the domain.
   * @param replicationServers the list of replication servers for the domain.
   */
  public DomainEntry(String name, int replicationId, String baseDn,
      Set<String> replicationServers)
  {
    this.name = name;
    this.replicationId = replicationId;
    this.baseDn = baseDn;
    this.replicationServers = replicationServers;
  }
  /**
   * Returns the base dn of the domain.
   * @return the base dn of the domain.
   */
  public String getBaseDn()
  {
    return baseDn;
  }
  /**
   * Returns the name of the domain.
   * @return the name of the domain.
   */
  public String getName()
  {
    return name;
  }
  /**
   * Returns the replication Id of the domain.
   * @return the replication Id of the domain.
   */
  public int getReplicationId()
  {
    return replicationId;
  }
  /**
   * Returns the list of replication servers of the domain.
   * @return the list of replication servers of the domain.
   */
  public Set<String> getReplicationServers()
  {
    return replicationServers;
  }
}
