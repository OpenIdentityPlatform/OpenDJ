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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.task;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.server.config.ConfigConstants.ATTR_BACKEND_BASE_DN;

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
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.server.config.client.PluggableBackendCfgClient;
import org.forgerock.opendj.server.config.client.ReplicationDomainCfgClient;
import org.forgerock.opendj.server.config.client.ReplicationSynchronizationProviderCfgClient;
import org.forgerock.opendj.server.config.client.RootCfgClient;
import org.forgerock.opendj.server.config.server.ReplicationDomainCfg;
import org.forgerock.opendj.server.config.server.ReplicationSynchronizationProviderCfg;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.server.config.ConfigurationHandler;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.OpenDsException;

/** The task used to delete a set of base DNs or backends. */
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

  @Override
  public Type getType()
  {
    return !baseDNsToDelete.isEmpty() ? Type.DELETE_BASEDN : Type.DELETE_BACKEND;
  }

  @Override
  public Set<String> getBackends()
  {
    return backendSet;
  }

  @Override
  public LocalizableMessage getTaskDescription()
  {
    StringBuilder sb = new StringBuilder();

    if (!baseDNsToDelete.isEmpty())
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

  @Override
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
  private void updateConfiguration() throws Exception
  {
    boolean configHandlerUpdated = false;
    final int totalNumber = baseDNsToDelete.size() + backendsToDelete.size();
    int numberDeleted = 0;
    try
    {
      if (!isServerRunning())
      {
        configHandlerUpdated = true;
        stopPoolingAndInitializeConfiguration();
      }
      boolean isFirst = true;
      for (final Set<BaseDNDescriptor> baseDNs : baseDNsToDelete.values())
      {
        if (!isFirst)
        {
          SwingUtilities.invokeLater(new Runnable()
          {
            @Override
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
            @Override
            public void run()
            {
              List<String> args =
                getObfuscatedCommandLineArguments(
                    getDSConfigCommandLineArguments(baseDNs));
              args.removeAll(getConfigCommandLineArguments());
              printEquivalentCommandLine(getConfigCommandLinePath(), args,
                  INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_DELETE_BASE_DN.get());
            }
          });
        }
        SwingUtilities.invokeLater(new Runnable()
        {
          @Override
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
          deleteBaseDNs(getInfo().getConnection(), baseDNs);
        }
        else
        {
          deleteBaseDNs(baseDNs);
        }
        numberDeleted ++;
        final int fNumberDeleted = numberDeleted;
        SwingUtilities.invokeLater(new Runnable()
        {
          @Override
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
            @Override
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
            @Override
            public void run()
            {
              List<String> args =
                getObfuscatedCommandLineArguments(
                    getDSConfigCommandLineArguments(backend));
              args.removeAll(getConfigCommandLineArguments());
              printEquivalentCommandLine(getConfigCommandLinePath(), args,
                  INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_DELETE_BACKEND.get());
            }
          });
        }
        SwingUtilities.invokeLater(new Runnable()
        {
          @Override
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
          deleteBackend(getInfo().getConnection(), backend);
        }
        else
        {
          deleteBackend(backend);
        }
        numberDeleted ++;
        final int fNumberDeleted = numberDeleted;
        SwingUtilities.invokeLater(new Runnable()
        {
          @Override
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
        startPoolingAndInitializeConfiguration();
      }
    }
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
    DN dn = DN.valueOf("ds-cfg-backend-id=" + backendName + ",cn=Backends,cn=config");
    updateConfigEntryWithAttribute(dn, ATTR_BACKEND_BASE_DN, newBaseDNs);
  }

  /** Update a config entry with the provided attribute parameters. */
  private void updateConfigEntryWithAttribute(DN entryDn, String attrName, List<DN> newBaseDNs)
      throws DirectoryException, ConfigException
  {
    ConfigurationHandler configHandler = DirectoryServer.getConfigurationHandler();
    final Entry configEntry = configHandler.getEntry(entryDn);
    final Entry newEntry = LinkedHashMapEntry.deepCopyOfEntry(configEntry);
    AttributeType attrType = Schema.getDefaultSchema().getAttributeType(
        attrName, CoreSchema.getDirectoryStringSyntax());
    newEntry.replaceAttribute(new LinkedAttribute(AttributeDescription.create(attrType), newBaseDNs));
    configHandler.replaceEntry(configEntry, newEntry);
  }

  /**
   * Deletes a set of base DNs.  The code assumes that the server is running
   * and that the provided connection is active.
   * @param baseDNs the list of base DNs.
   * @param connWrapper the connection to the server.
   * @throws OpenDsException if an error occurs.
   */
  private void deleteBaseDNs(ConnectionWrapper connWrapper,
      Set<BaseDNDescriptor> baseDNs) throws Exception
  {
    RootCfgClient root = connWrapper.getRootConfiguration();
    PluggableBackendCfgClient backend =
      (PluggableBackendCfgClient)root.getBackend(
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
    DN dn = DN.valueOf("ds-cfg-backend-id" + "=" + backend.getBackendID() + ",cn=Backends,cn=config");
    Utilities.deleteConfigSubtree(DirectoryServer.getConfigurationHandler(), dn);
  }

  /**
   * Deletes a backend.  The code assumes that the server is running
   * and that the provided connection is active.
   * @param backend the backend to be deleted.
   * @param connWrapper the connection to the server.
   * @throws OpenDsException if an error occurs.
   */
  private void deleteBackend(ConnectionWrapper connWrapper,
      BackendDescriptor backend) throws Exception
  {
    RootCfgClient root = connWrapper.getRootConfiguration();
    root.removeBackend(backend.getBackendID());
    root.commit();
  }

  @Override
  protected String getCommandLinePath()
  {
    return null;
  }

  @Override
  protected ArrayList<String> getCommandLineArguments()
  {
    return new ArrayList<>();
  }

  /**
   * Returns the path of the command line to be used.
   *
   * @return the path of the command line to be used
   */
  private String getConfigCommandLinePath()
  {
    if (isServerRunning())
    {
      return getCommandLinePath("dsconfig");
    }
    return null;
  }

  @Override
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
  throws Exception
  {
    if (baseDN.getType() == BaseDNDescriptor.Type.REPLICATED)
    {
      final AtomicReference<String> domainName = new AtomicReference<>();

      try
      {
        if (isServerRunning())
        {
          ConnectionWrapper connWrapper = getInfo().getConnection();
          RootCfgClient root = connWrapper.getRootConfiguration();
          ReplicationSynchronizationProviderCfgClient sync = null;
          try
          {
            sync = (ReplicationSynchronizationProviderCfgClient)
            root.getSynchronizationProvider("Multimaster Synchronization");
          }
          catch (Exception oe)
          {
            // Ignore this one
          }
          if (sync != null)
          {
            String[] domains = sync.listReplicationDomains();
            if (domains != null)
            {
              for (String dName : domains)
              {
                ReplicationDomainCfgClient domain = sync.getReplicationDomain(dName);
                if (baseDN.getDn().equals(domain.getBaseDN()))
                {
                  domainName.set(dName);
                  sync.removeReplicationDomain(dName);
                  sync.commit();
                  break;
                }
              }
            }
          }
        }
        else
        {
          RootCfg root = DirectoryServer.getInstance().getServerContext().getRootConfig();
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
              for (String dName : domains)
              {
                ReplicationDomainCfg domain = sync.getReplicationDomain(dName);
                DN dn = domain.getBaseDN();
                if (dn.equals(baseDN.getDn()))
                {
                  domainName.set(dName);
                  DN entryDN = domain.dn();
                  Utilities.deleteConfigSubtree(DirectoryServer.getConfigurationHandler(), entryDN);
                  break;
                }
              }
            }
          }
        }
      }
      finally
      {
        // This is not super clean, but this way we calculate the domain name only once.
        if (isServerRunning() && domainName.get() != null)
        {
          SwingUtilities.invokeLater(new Runnable()
          {
            @Override
            public void run()
            {
              List<String> args =
                getObfuscatedCommandLineArguments(
                    getCommandLineArgumentsToDisableReplication(domainName.get()));
              args.removeAll(getConfigCommandLineArguments());
              args.add(getNoPropertiesFileArgument());
              printEquivalentCommandLine(getConfigCommandLinePath(), args,
                  INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_DELETE_DOMAIN.get(baseDN.getDn()));
              }
          });
        }
        SwingUtilities.invokeLater(new Runnable()
        {
          @Override
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
        @Override
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
