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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.task;

import static org.opends.messages.AdminToolMessages.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import javax.naming.ldap.InitialLdapContext;
import javax.swing.SwingUtilities;

import org.opends.guitools.controlpanel.datamodel.AbstractIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.VLVIndexDescriptor;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.guitools.controlpanel.util.ConfigReader;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;
import org.opends.server.admin.client.ldap.LDAPManagementContext;
import org.opends.server.admin.std.client.LocalDBBackendCfgClient;
import org.opends.server.admin.std.client.RootCfgClient;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.cli.CommandBuilder;

/**
 * The task that is launched when an index must be deleted.
 */
public class DeleteIndexTask extends Task
{
  private Set<String> backendSet;
  private ArrayList<AbstractIndexDescriptor> indexesToDelete =
    new ArrayList<AbstractIndexDescriptor>();
  private ArrayList<AbstractIndexDescriptor> deletedIndexes =
    new ArrayList<AbstractIndexDescriptor>();

  /**
   * Constructor of the task.
   * @param info the control panel information.
   * @param dlg the progress dialog where the task progress will be displayed.
   * @param indexesToDelete the indexes that must be deleted.
   */
  public DeleteIndexTask(ControlPanelInfo info, ProgressDialog dlg,
      ArrayList<AbstractIndexDescriptor> indexesToDelete)
  {
    super(info, dlg);
    backendSet = new HashSet<String>();
    for (AbstractIndexDescriptor index : indexesToDelete)
    {
      backendSet.add(index.getBackend().getBackendID());
    }
    this.indexesToDelete.addAll(indexesToDelete);
  }

  /**
   * {@inheritDoc}
   */
  public Type getType()
  {
    return Type.DELETE_INDEX;
  }

  /**
   * {@inheritDoc}
   */
  public Set<String> getBackends()
  {
    return backendSet;
  }

  /**
   * {@inheritDoc}
   */
  public Message getTaskDescription()
  {
    if (backendSet.size() == 1)
    {
      return INFO_CTRL_PANEL_DELETE_INDEX_TASK_DESCRIPTION.get(
      Utilities.getStringFromCollection(backendSet, ", "));
    }
    else
    {
      return INFO_CTRL_PANEL_DELETE_INDEX_IN_BACKENDS_TASK_DESCRIPTION.get(
          Utilities.getStringFromCollection(backendSet, ", "));
    }
  }

  /**
   * {@inheritDoc}
   */
  public boolean canLaunch(Task taskToBeLaunched,
      Collection<Message> incompatibilityReasons)
  {
    boolean canLaunch = true;
    if (state == State.RUNNING)
    {
      // All the operations are incompatible if they apply to this
      // backend for safety.  This is a short operation so the limitation
      // has not a lot of impact.
      Set<String> backends =
        new TreeSet<String>(taskToBeLaunched.getBackends());
      backends.retainAll(getBackends());
      if (backends.size() > 0)
      {
        incompatibilityReasons.add(getIncompatibilityMessage(this,
            taskToBeLaunched));
        canLaunch = false;
      }
    }
    return canLaunch;
  }

  /**
   * Update the configuration in the server.
   * @throws OpenDsException if an error occurs.
   */
  private void updateConfiguration() throws OpenDsException
  {
    boolean configHandlerUpdated = false;
    final int totalNumber = indexesToDelete.size();
    int numberDeleted = 0;
    try
    {
      if (!isServerRunning())
      {
        configHandlerUpdated = true;
        getInfo().stopPooling();
        if (getInfo().mustDeregisterConfig())
        {
          DirectoryServer.deregisterBaseDN(DN.decode("cn=config"));
        }
        DirectoryServer.getInstance().initializeConfiguration(
            org.opends.server.extensions.ConfigFileHandler.class.getName(),
            ConfigReader.configFile);
        getInfo().setMustDeregisterConfig(true);
      }
      boolean isFirst = true;
      for (final AbstractIndexDescriptor index : indexesToDelete)
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
        if (isServerRunning())
        {
          SwingUtilities.invokeLater(new Runnable()
          {
            public void run()
            {
              StringBuilder sb = new StringBuilder();
              sb.append(getConfigCommandLineName(index));
              Collection<String> args =
                getObfuscatedCommandLineArguments(
                    getDSConfigCommandLineArguments(index));
              args.removeAll(getConfigCommandLineArguments());
              for (String arg : args)
              {
                sb.append(" "+CommandBuilder.escapeValue(arg));
              }
              getProgressDialog().appendProgressHtml(Utilities.applyFont(
                  INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_DELETE_INDEX.get()+
                  "<br><b>"+sb.toString()+"</b><br><br>",
                  ColorAndFontConstants.progressFont));
            }
          });
        }
        SwingUtilities.invokeLater(new Runnable()
        {
          public void run()
          {
            if (isVLVIndex(index))
            {
              getProgressDialog().appendProgressHtml(
                  Utilities.getProgressWithPoints(
                      INFO_CTRL_PANEL_DELETING_VLV_INDEX.get(
                          index.getName()),
                      ColorAndFontConstants.progressFont));
            }
            else
            {
              getProgressDialog().appendProgressHtml(
                  Utilities.getProgressWithPoints(
                      INFO_CTRL_PANEL_DELETING_INDEX.get(
                          index.getName()),
                      ColorAndFontConstants.progressFont));
            }
          }
        });
        if (isServerRunning())
        {
          deleteIndex(getInfo().getDirContext(), index);
        }
        else
        {
          deleteIndex(index);
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
        deletedIndexes.add(index);
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
   * Returns <CODE>true</CODE> if the index is a VLV index and
   * <CODE>false</CODE> otherwise.
   * @param index the index.
   * @return <CODE>true</CODE> if the index is a VLV index and
   * <CODE>false</CODE> otherwise.
   */
  private boolean isVLVIndex(AbstractIndexDescriptor index)
  {
    return index instanceof VLVIndexDescriptor;
  }

  /**
   * Deletes an index.  The code assumes that the server is not running
   * and that the configuration file can be edited.
   * @param index the index to be deleted.
   * @throws OpenDsException if an error occurs.
   */
  private void deleteIndex(AbstractIndexDescriptor index) throws OpenDsException
  {
    if (isVLVIndex(index))
    {
      String dn = Utilities.getRDNString("ds-cfg-name", index.getName())+
      ",cn=VLV Index,"+Utilities.getRDNString("ds-cfg-backend-id",
          index.getBackend().getBackendID())+",cn=Backends,cn=config";
      DirectoryServer.getConfigHandler().deleteEntry(DN.decode(dn), null);
    }
    else
    {
      String dn = Utilities.getRDNString("ds-cfg-attribute", index.getName())+
      ",cn=Index,"+Utilities.getRDNString("ds-cfg-backend-id",
          index.getBackend().getBackendID())+",cn=Backends,cn=config";
      DirectoryServer.getConfigHandler().deleteEntry(DN.decode(dn), null);
    }
  }

  /**
   * Deletes an index.  The code assumes that the server is running
   * and that the provided connection is active.
   * @param index the index to be deleted.
   * @param ctx the connection to the server.
   * @throws OpenDsException if an error occurs.
   */
  private void deleteIndex(InitialLdapContext ctx,
      AbstractIndexDescriptor index) throws OpenDsException
  {
    ManagementContext mCtx = LDAPManagementContext.createFromContext(
        JNDIDirContextAdaptor.adapt(ctx));
    RootCfgClient root = mCtx.getRootConfiguration();
    LocalDBBackendCfgClient backend =
      (LocalDBBackendCfgClient)root.getBackend(
          index.getBackend().getBackendID());
    if (isVLVIndex(index))
    {
      backend.removeLocalDBVLVIndex(index.getName());
    }
    else
    {
      backend.removeLocalDBIndex(index.getName());
    }
    backend.commit();
  }

  /**
   * {@inheritDoc}
   */
  protected String getCommandLinePath()
  {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  protected ArrayList<String> getCommandLineArguments()
  {
    return new ArrayList<String>();
  }

  /**
   * Returns the path of the command line to be used to delete the specified
   * index.
   * @param index the index to be deleted.
   * @return the path of the command line to be used to delete the specified
   * index.
   */
  private String getConfigCommandLineName(AbstractIndexDescriptor index)
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
   * {@inheritDoc}
   */
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
    finally
    {
      for (AbstractIndexDescriptor index : deletedIndexes)
      {
        getInfo().unregisterModifiedIndex(index);
      }
    }
  }

  /**
   * Return the dsconfig arguments required to delete an index.
   * @param index the index to be deleted.
   * @return the dsconfig arguments required to delete an index.
   */
  private ArrayList<String> getDSConfigCommandLineArguments(
      AbstractIndexDescriptor index)
  {
    ArrayList<String> args = new ArrayList<String>();
    if (isVLVIndex(index))
    {
      args.add("delete-local-db-vlv-index");
    }
    else
    {
      args.add("delete-local-db-index");
    }
    args.add("--backend-name");
    args.add(index.getBackend().getBackendID());

    args.add("--index-name");
    args.add(index.getName());

    args.addAll(getConnectionCommandLineArguments());
    args.add("--no-prompt");
    args.add(getNoPropertiesFileArgument());
    return args;
  }
}
