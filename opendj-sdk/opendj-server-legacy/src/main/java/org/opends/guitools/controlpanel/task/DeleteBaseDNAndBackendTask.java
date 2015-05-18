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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */

package org.opends.guitools.controlpanel.task;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.ConfigMessages.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.naming.ldap.InitialLdapContext;
import javax.swing.SwingUtilities;

import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.guitools.controlpanel.util.ConfigReader;
import org.opends.guitools.controlpanel.util.Utilities;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;
import org.opends.server.admin.client.ldap.LDAPManagementContext;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.client.*;
import org.opends.server.admin.std.server.ReplicationDomainCfg;
import org.opends.server.admin.std.server.ReplicationSynchronizationProviderCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.config.ConfigConstants;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.DNConfigAttribute;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;
import org.opends.server.types.OpenDsException;

/**
 * The task used to delete a set of base DNs or backends.
 *
 */
public class DeleteBaseDNAndBackendTask extends Task
{
  private Set<String> backendSet;
  private Map<String, Set<BaseDNDescriptor>> baseDNsToDelete = new HashMap<>();
  private ArrayList<BackendDescriptor> backendsToDelete = new ArrayList<>();

  /**
   * Constructor of the task.
   * @param info the control panel information.
   * @param dlg the progress dialog where the task progress will be displayed.
   * @param backendsToDelete the backends to delete.
   * @param baseDNsToDelete the base DNs to delete.
   */
  public DeleteBaseDNAndBackendTask(ControlPanelInfo info, ProgressDialog dlg,
      Collection<BackendDescriptor> backendsToDelete,
      Collection<BaseDNDescriptor> baseDNsToDelete)
  {
    super(info, dlg);
    backendSet = new HashSet<>();
    for (BackendDescriptor backend : backendsToDelete)
    {
      backendSet.add(backend.getBackendID());
    }
    for (BaseDNDescriptor baseDN : baseDNsToDelete)
    {
      backendSet.add(baseDN.getBackend().getBackendID());
    }
    for (BaseDNDescriptor baseDN : baseDNsToDelete)
    {
      String backendID = baseDN.getBackend().getBackendID();
      Set<BaseDNDescriptor> set = this.baseDNsToDelete.get(backendID);
      if (set == null)
      {
        set = new HashSet<>();
        this.baseDNsToDelete.put(backendID, set);
      }
      set.add(baseDN);
    }
    ArrayList<String> indirectBackendsToDelete = new ArrayList<>();
    for (Set<BaseDNDescriptor> set : this.baseDNsToDelete.values())
    {
      BackendDescriptor backend = set.iterator().next().getBackend();
      if (set.size() == backend.getBaseDns().size())
      {
        // All of the suffixes must be deleted.
        indirectBackendsToDelete.add(backend.getBackendID());
        this.backendsToDelete.add(backend);
      }
    }
    for (String backendID : indirectBackendsToDelete)
    {
      this.baseDNsToDelete.remove(backendID);
    }
    this.backendsToDelete.addAll(backendsToDelete);
  }

  /** {@inheritDoc} */
  public Type getType()
  {
    if (baseDNsToDelete.size() > 0)
    {
      return Type.DELETE_BASEDN;
    }
    else
    {
      return Type.DELETE_BACKEND;
    }
  }

  /** {@inheritDoc} */
  public Set<String> getBackends()
  {
    return backendSet;
  }

  /** {@inheritDoc} */
  public LocalizableMessage getTaskDescription()
  {
    StringBuilder sb = new StringBuilder();

    if (baseDNsToDelete.size() > 0)
    {
      ArrayList<String> dns = new ArrayList<>();
      for (Set<BaseDNDescriptor> set : baseDNsToDelete.values())
      {
        for (BaseDNDescriptor baseDN : set)
        {
          dns.add(baseDN.getDn().toString());
        }
      }
      if (dns.size() == 1)
      {
        String dn = dns.iterator().next();
        sb.append(INFO_CTRL_PANEL_DELETE_BASE_DN_DESCRIPTION.get(dn));
      }
      else
      {
        ArrayList<String> quotedDns = new ArrayList<>();
        for (String dn : dns)
        {
          quotedDns.add("'"+dn+"'");
        }
        sb.append(INFO_CTRL_PANEL_DELETE_BASE_DNS_DESCRIPTION.get(
        Utilities.getStringFromCollection(quotedDns, ", ")));
      }
    }

    if (!backendsToDelete.isEmpty())
    {
      if (sb.length() > 0)
      {
        sb.append("  ");
      }
      if (backendsToDelete.size() == 1)
      {
        sb.append(INFO_CTRL_PANEL_DELETE_BACKEND_DESCRIPTION.get(
            backendsToDelete.iterator().next().getBackendID()));
      }
      else
      {
        ArrayList<String> ids = new ArrayList<>();
        for (BackendDescriptor backend : backendsToDelete)
        {
          ids.add(backend.getBackendID());
        }
        sb.append(INFO_CTRL_PANEL_DELETE_BACKENDS_DESCRIPTION.get(
        Utilities.getStringFromCollection(ids, ", ")));
      }
    }
    return LocalizableMessage.raw(sb.toString());
  }

  /** {@inheritDoc} */
  public boolean canLaunch(Task taskToBeLaunched,
      Collection<LocalizableMessage> incompatibilityReasons)
  {
    boolean canLaunch = true;
    if (state == State.RUNNING && runningOnSameServer(taskToBeLaunched))
    {
      // All the operations are incompatible if they apply to this
      // backend for safety.  This is a short operation so the limitation
      // has not a lot of impact.
      Set<String> backends = new TreeSet<>(taskToBeLaunched.getBackends());
      backends.retainAll(getBackends());
      if (!backends.isEmpty())
      {
        incompatibilityReasons.add(
            getIncompatibilityMessage(this, taskToBeLaunched));
        canLaunch = false;
      }
    }
    return canLaunch;
  }

  /**
   * Update the configuration in the server.
   * @throws OpenDsException if an error occurs.
   */
  private void updateConfiguration() throws OpenDsException, ConfigException
  {
    boolean configHandlerUpdated = false;
    final int totalNumber = baseDNsToDelete.size() + backendsToDelete.size();
    int numberDeleted = 0;
    try
    {
      if (!isServerRunning())
      {
        configHandlerUpdated = true;
        getInfo().stopPooling();
        if (getInfo().mustDeregisterConfig())
        {
          DirectoryServer.deregisterBaseDN(DN.valueOf("cn=config"));
        }
        DirectoryServer.getInstance().initializeConfiguration(
            org.opends.server.extensions.ConfigFileHandler.class.getName(),
            ConfigReader.configFile);
        getInfo().setMustDeregisterConfig(true);
      }
      boolean isFirst = true;
      for (final Set<BaseDNDescriptor> baseDNs : baseDNsToDelete.values())
      {
        if (!isFirst)
        {
          SwingUtilities.invokeLater(new Runnable()
          {
            public void run()
            {
              getProgressDialog().appendProgressHtml("<br><br>");
            }
          });
        }
        isFirst = false;

        for (BaseDNDescriptor baseDN : baseDNs)
        {
          disableReplicationIfRequired(baseDN);
        }

        if (isServerRunning())
        {
          SwingUtilities.invokeLater(new Runnable()
          {
            public void run()
            {
              List<String> args =
                getObfuscatedCommandLineArguments(
                    getDSConfigCommandLineArguments(baseDNs));
              args.removeAll(getConfigCommandLineArguments());
              printEquivalentCommandLine(getConfigCommandLinePath(baseDNs),
                  args, INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_DELETE_BASE_DN.get());
            }
          });
        }
        SwingUtilities.invokeLater(new Runnable()
        {
          public void run()
          {
            if (baseDNs.size() == 1)
            {
              String dn = baseDNs.iterator().next().getDn().toString();
              getProgressDialog().appendProgressHtml(
                  Utilities.getProgressWithPoints(
                      INFO_CTRL_PANEL_DELETING_BASE_DN.get(dn),
                      ColorAndFontConstants.progressFont));
            }
            else
            {
              ArrayList<String> dns = new ArrayList<>();
              for (BaseDNDescriptor baseDN : baseDNs)
              {
                dns.add("'" + baseDN.getDn() + "'");
              }
              getProgressDialog().appendProgressHtml(
                  Utilities.getProgressWithPoints(
                      INFO_CTRL_PANEL_DELETING_BASE_DNS.get(
                      Utilities.getStringFromCollection(dns, ", ")),
                      ColorAndFontConstants.progressFont));
            }
          }
        });
        if (isServerRunning())
        {
          deleteBaseDNs(getInfo().getDirContext(), baseDNs);
        }
        else
        {
          deleteBaseDNs(baseDNs);
        }
        numberDeleted ++;
        final int fNumberDeleted = numberDeleted;
        SwingUtilities.invokeLater(new Runnable()
        {
          public void run()
          {
            getProgressDialog().getProgressBar().setIndeterminate(false);
            getProgressDialog().getProgressBar().setValue(
                (fNumberDeleted * 100) / totalNumber);
            getProgressDialog().appendProgressHtml(
                Utilities.getProgressDone(ColorAndFontConstants.progressFont));
          }
        });
      }
      for (final BackendDescriptor backend : backendsToDelete)
      {
        if (!isFirst)
        {
          SwingUtilities.invokeLater(new Runnable()
          {
            public void run()
            {
              getProgressDialog().appendProgressHtml("<br><br>");
            }
          });
        }
        for (BaseDNDescriptor baseDN : backend.getBaseDns())
        {
          disableReplicationIfRequired(baseDN);
        }
        isFirst = false;
        if (isServerRunning())
        {
          SwingUtilities.invokeLater(new Runnable()
          {
            public void run()
            {
              List<String> args =
                getObfuscatedCommandLineArguments(
                    getDSConfigCommandLineArguments(backend));
              args.removeAll(getConfigCommandLineArguments());
              printEquivalentCommandLine(getConfigCommandLinePath(backend),
                 args, INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_DELETE_BACKEND.get());
            }
          });
        }
        SwingUtilities.invokeLater(new Runnable()
        {
          public void run()
          {
            getProgressDialog().appendProgressHtml(
                  Utilities.getProgressWithPoints(
                      INFO_CTRL_PANEL_DELETING_BACKEND.get(
                          backend.getBackendID()),
                      ColorAndFontConstants.progressFont));
          }
        });
        if (isServerRunning())
        {
          deleteBackend(getInfo().getDirContext(), backend);
        }
        else
        {
          deleteBackend(backend);
        }
        numberDeleted ++;
        final int fNumberDeleted = numberDeleted;
        SwingUtilities.invokeLater(new Runnable()
        {
          public void run()
          {
            getProgressDialog().getProgressBar().setIndeterminate(false);
            getProgressDialog().getProgressBar().setValue(
                (fNumberDeleted * 100) / totalNumber);
            getProgressDialog().appendProgressHtml(
                Utilities.getProgressDone(ColorAndFontConstants.progressFont));
          }
        });
      }
    }
    finally
    {
      if (configHandlerUpdated)
      {
        DirectoryServer.getInstance().initializeConfiguration(
            ConfigReader.configClassName, ConfigReader.configFile);
        getInfo().startPooling();
      }
    }
  }

  /**
   * Returns the DN in the configuration for a given backend.
   * @param backend the backend.
   * @return the backend configuration entry DN.
   */
  private String getDN(BackendDescriptor backend)
  {
    return Utilities.getRDNString("ds-cfg-backend-id",
        backend.getBackendID())+",cn=Backends,cn=config";
  }

  /**
   * Deletes a set of base DNs.  The code assumes that the server is not running
   * and that the configuration file can be edited.
   * @param baseDNs the list of base DNs.
   * @throws OpenDsException if an error occurs.
   */
  private void deleteBaseDNs(Set<BaseDNDescriptor> baseDNs)
  throws OpenDsException, ConfigException
  {
    BackendDescriptor backend = baseDNs.iterator().next().getBackend();

    SortedSet<DN> oldBaseDNs = new TreeSet<>();
    for (BaseDNDescriptor baseDN : backend.getBaseDns())
    {
      oldBaseDNs.add(baseDN.getDn());
    }
    LinkedList<DN> newBaseDNs = new LinkedList<>(oldBaseDNs);
    ArrayList<DN> dnsToRemove = new ArrayList<>();
    for (BaseDNDescriptor baseDN : baseDNs)
    {
      dnsToRemove.add(baseDN.getDn());
    }
    newBaseDNs.removeAll(dnsToRemove);

    String backendName = backend.getBackendID();
    String dn = Utilities.getRDNString("ds-cfg-backend-id", backendName)+
    ",cn=Backends,cn=config";
    ConfigEntry configEntry =
      DirectoryServer.getConfigHandler().getConfigEntry(DN.valueOf(dn));

    DNConfigAttribute baseDNAttr =
      new DNConfigAttribute(
          ConfigConstants.ATTR_BACKEND_BASE_DN,
          INFO_CONFIG_BACKEND_ATTR_DESCRIPTION_BASE_DNS.get(),
          true, true, false, newBaseDNs);
    configEntry.putConfigAttribute(baseDNAttr);
    DirectoryServer.getConfigHandler().writeUpdatedConfig();
  }

  /**
   * Deletes a set of base DNs.  The code assumes that the server is running
   * and that the provided connection is active.
   * @param baseDNs the list of base DNs.
   * @param ctx the connection to the server.
   * @throws OpenDsException if an error occurs.
   */
  private void deleteBaseDNs(InitialLdapContext ctx,
      Set<BaseDNDescriptor> baseDNs) throws OpenDsException
  {
    ManagementContext mCtx = LDAPManagementContext.createFromContext(
        JNDIDirContextAdaptor.adapt(ctx));
    RootCfgClient root = mCtx.getRootConfiguration();
    LocalDBBackendCfgClient backend =
      (LocalDBBackendCfgClient)root.getBackend(
          baseDNs.iterator().next().getBackend().getBackendID());
    SortedSet<DN> oldBaseDNs = backend.getBaseDN();
    SortedSet<DN> newBaseDNs = new TreeSet<>(oldBaseDNs);
    ArrayList<DN> dnsToRemove = new ArrayList<>();
    for (BaseDNDescriptor baseDN : baseDNs)
    {
      dnsToRemove.add(baseDN.getDn());
    }
    newBaseDNs.removeAll(dnsToRemove);
    backend.setBaseDN(newBaseDNs);
    backend.commit();
  }

  /**
   * Deletes a backend.  The code assumes that the server is not running
   * and that the configuration file can be edited.
   * @param backend the backend to be deleted.
   * @throws OpenDsException if an error occurs.
   */
  private void deleteBackend(BackendDescriptor backend) throws OpenDsException, ConfigException
  {
    String dn = getDN(backend);
    Utilities.deleteConfigSubtree(
        DirectoryServer.getConfigHandler(), DN.valueOf(dn));
  }

  /**
   * Deletes a backend.  The code assumes that the server is running
   * and that the provided connection is active.
   * @param backend the backend to be deleted.
   * @param ctx the connection to the server.
   * @throws OpenDsException if an error occurs.
   */
  private void deleteBackend(InitialLdapContext ctx,
      BackendDescriptor backend) throws OpenDsException
  {
    ManagementContext mCtx = LDAPManagementContext.createFromContext(
        JNDIDirContextAdaptor.adapt(ctx));
    RootCfgClient root = mCtx.getRootConfiguration();
    root.removeBackend(backend.getBackendID());
    root.commit();
  }

  /** {@inheritDoc} */
  protected String getCommandLinePath()
  {
    return null;
  }

  /** {@inheritDoc} */
  protected ArrayList<String> getCommandLineArguments()
  {
    return new ArrayList<>();
  }

  /**
   * Returns the path of the command line to be used to delete the specified
   * backend.
   * @param backend the backend to be deleted.
   * @return the path of the command line to be used to delete the specified
   * backend.
   */
  private String getConfigCommandLinePath(BackendDescriptor backend)
  {
    if (isServerRunning())
    {
      return getCommandLinePath("dsconfig");
    }
    else
    {
      return null;
    }
  }

  /**
   * Returns the path of the command line to be used to delete the specified
   * base DNs.
   * @param baseDNs the base DNs to be deleted.
   * @return the path of the command line to be used to delete the specified
   * base DNs.
   */
  private String getConfigCommandLinePath(Set<BaseDNDescriptor> baseDNs)
  {
    if (isServerRunning())
    {
      return getCommandLinePath("dsconfig");
    }
    else
    {
      return null;
    }
  }

  /** {@inheritDoc} */
  public void runTask()
  {
    state = State.RUNNING;
    lastException = null;

    try
    {
      updateConfiguration();
      state = State.FINISHED_SUCCESSFULLY;
    }
    catch (Throwable t)
    {
      lastException = t;
      state = State.FINISHED_WITH_ERROR;
    }
  }

  /**
   * Return the dsconfig arguments required to delete a set of base DNs.
   * @param baseDNs the base DNs to be deleted.
   * @return the dsconfig arguments required to delete a set of base DNs.
   */
  private ArrayList<String> getDSConfigCommandLineArguments(
      Set<BaseDNDescriptor> baseDNs)
  {
    ArrayList<String> args = new ArrayList<>();
    if (isServerRunning())
    {
      args.add("set-backend-prop");
      args.add("--backend-name");
      args.add(baseDNs.iterator().next().getBackend().getBackendID());
      args.add("--remove");
      for (BaseDNDescriptor baseDN : baseDNs)
      {
        args.add("base-dn:" + baseDN.getDn());
      }
      args.addAll(getConnectionCommandLineArguments());
      args.add("--no-prompt");
    }
    return args;
  }

  /**
   * Return the dsconfig arguments required to delete a backend.
   * @param backend the backend to be deleted.
   * @return the dsconfig arguments required to delete a backend.
   */
  private ArrayList<String> getDSConfigCommandLineArguments(
      BackendDescriptor backend)
  {
    ArrayList<String> args = new ArrayList<>();
    args.add("delete-backend");
    args.add("--backend-name");
    args.add(backend.getBackendID());

    args.addAll(getConnectionCommandLineArguments());
    args.add("--no-prompt");
    return args;
  }

  /**
   * Disables replication if required: if the deleted base DN is replicated,
   * update the replication configuration to remove any reference to it.
   * @param baseDN the base DN that is going to be removed.
   * @throws OpenDsException if an error occurs.
   */
  private void disableReplicationIfRequired(final BaseDNDescriptor baseDN)
  throws OpenDsException, ConfigException
  {
    if (baseDN.getType() == BaseDNDescriptor.Type.REPLICATED)
    {
      final String[] domainName = {null};

      try
      {
        if (isServerRunning())
        {
          InitialLdapContext ctx = getInfo().getDirContext();
          ManagementContext mCtx = LDAPManagementContext.createFromContext(
              JNDIDirContextAdaptor.adapt(ctx));
          RootCfgClient root = mCtx.getRootConfiguration();
          ReplicationSynchronizationProviderCfgClient sync = null;
          try
          {
            sync = (ReplicationSynchronizationProviderCfgClient)
            root.getSynchronizationProvider("Multimaster Synchronization");
          }
          catch (OpenDsException oe)
          {
            // Ignore this one
          }
          if (sync != null)
          {
            String[] domains = sync.listReplicationDomains();
            if (domains != null)
            {
              for (int i=0; i<domains.length; i++)
              {
                ReplicationDomainCfgClient domain =
                  sync.getReplicationDomain(domains[i]);
                DN dn = domain.getBaseDN();
                if (dn.equals(baseDN.getDn()))
                {
                  domainName[0] = domains[i];
                  sync.removeReplicationDomain(domains[i]);
                  sync.commit();
                  break;
                }
              }
            }
          }
        }
        else
        {
          RootCfg root =
            ServerManagementContext.getInstance().getRootConfiguration();
          ReplicationSynchronizationProviderCfg sync = null;
          try
          {
            sync = (ReplicationSynchronizationProviderCfg)
            root.getSynchronizationProvider("Multimaster Synchronization");
          }
          catch (ConfigException oe)
          {
            // Ignore this one
          }
          if (sync != null)
          {
            String[] domains = sync.listReplicationDomains();
            if (domains != null)
            {
              for (int i=0; i<domains.length; i++)
              {
                ReplicationDomainCfg domain =
                  sync.getReplicationDomain(domains[i]);
                DN dn = domain.getBaseDN();
                if (dn.equals(baseDN.getDn()))
                {
                  domainName[0] = domains[i];
                  DN entryDN = domain.dn();
                  Utilities.deleteConfigSubtree(
                      DirectoryServer.getConfigHandler(), entryDN);
                  break;
                }
              }
            }
          }
        }
      }
      finally
      {
        // This is not super clean, but this way we calculate the domain name
        // only once.
        if (isServerRunning() && (domainName[0] != null))
        {
          SwingUtilities.invokeLater(new Runnable()
          {
            public void run()
            {
              List<String> args =
                getObfuscatedCommandLineArguments(
                    getCommandLineArgumentsToDisableReplication(domainName[0]));
              args.removeAll(getConfigCommandLineArguments());
              args.add(getNoPropertiesFileArgument());
              printEquivalentCommandLine(
                  getConfigCommandLinePath(baseDN.getBackend()),
                  args, INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_DELETE_DOMAIN.get(baseDN.getDn()));
              }
          });
        }
        SwingUtilities.invokeLater(new Runnable()
        {
          public void run()
          {
            getProgressDialog().appendProgressHtml(
                Utilities.getProgressWithPoints(
                    INFO_CTRL_PANEL_DELETING_DOMAIN.get(baseDN.getDn()),
                    ColorAndFontConstants.progressFont));
          }
        });
      }
      SwingUtilities.invokeLater(new Runnable()
      {
        public void run()
        {
          getProgressDialog().appendProgressHtml(
              Utilities.getProgressDone(ColorAndFontConstants.progressFont)+
              "<br>");
        }
      });
    }
  }

  /**
   * Return the dsconfig arguments required to delete a replication domain.
   * @param domainName the name of the domain to be deleted.
   * @return the dsconfig arguments required to delete a replication domain.
   */
  private ArrayList<String> getCommandLineArgumentsToDisableReplication(
      String domainName)
  {
    ArrayList<String> args = new ArrayList<>();
    args.add("delete-replication-domain");
    args.add("--provider-name");
    args.add("Multimaster Synchronization");
    args.add("--domain-name");
    args.add(domainName);
    args.addAll(getConnectionCommandLineArguments());
    args.add("--no-prompt");
    return args;
  }
}

