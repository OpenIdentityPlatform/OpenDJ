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

package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.QuickSetupMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.ldap.InitialLdapContext;
import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.event.BrowseActionListener;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.task.OfflineUpdateException;
import org.opends.guitools.controlpanel.task.OnlineUpdateException;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.ui.renderer.CustomListCellRenderer;
import org.opends.guitools.controlpanel.util.ConfigReader;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.quicksetup.installer.InstallerHelper;
import org.opends.quicksetup.util.Utils;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;
import org.opends.server.admin.client.ldap.LDAPManagementContext;
import org.opends.server.admin.std.client.LocalDBBackendCfgClient;
import org.opends.server.admin.std.client.RootCfgClient;
import org.opends.server.admin.std.meta.BackendCfgDefn;
import org.opends.server.admin.std.meta.LocalDBBackendCfgDefn;
import org.opends.server.config.ConfigConstants;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.DNConfigAttribute;
import org.opends.server.core.DirectoryServer;
import org.opends.server.tools.ImportLDIF;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.SetupUtils;
import org.opends.server.util.cli.CommandBuilder;

/**
 * The clss that appears when the user clicks on 'New Base DN'.
 *
 */
public class NewBaseDNPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = -2680821576362341119L;
  private JComboBox backends;
  private JTextField newBackend;
  private JTextField baseDN;
  private JRadioButton onlyCreateBaseEntry;
  private JRadioButton leaveDatabaseEmpty;
  private JRadioButton importDataFromLDIF;
  private JRadioButton importAutomaticallyGenerated;
  private JTextField path;
  private JTextField numberOfEntries;
  private JButton browseImportPath;

  private JLabel lBackend;
  private JLabel lDirectoryBaseDN;
  private JLabel lPath;
  private JLabel lNumberOfEntries;
  private JLabel lDirectoryData;

  private DocumentListener documentListener;

  private final Message NEW_BACKEND = INFO_CTRL_PANEL_NEW_BACKEND_LABEL.get();

  /**
   * The default constructor.
   *
   */
  public NewBaseDNPanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_NEW_BASE_DN_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return baseDN;
  }

  /**
   * {@inheritDoc}
   */
  public void toBeDisplayed(boolean visible)
  {
    if (visible)
    {
      documentListener.changedUpdate(null);
    }
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();

    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 3;
    addErrorPane(gbc);

    gbc.anchor = GridBagConstraints.WEST;
    gbc.weightx = 0.0;
    gbc.gridwidth = 1;
    gbc.gridy ++;
    gbc.fill = GridBagConstraints.NONE;
    lBackend = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_BACKEND_LABEL.get());
    add(lBackend, gbc);
    gbc.insets.left = 10;
    gbc.gridx = 1;
    backends = Utilities.createComboBox();
    backends.setModel(new DefaultComboBoxModel(new Object[]{"bogus",
        NEW_BACKEND}));
    backends.setRenderer(new CustomListCellRenderer(backends));
    backends.addItemListener(new IgnoreItemListener(backends));
    gbc.gridwidth = 1;
    add(backends, gbc);
    newBackend = Utilities.createTextField();
    newBackend.setColumns(25);
    gbc.gridx = 2;
    add(newBackend, gbc);
    ItemListener comboListener = new ItemListener()
    {
      /**
       * {@inheritDoc}
       */
      public void itemStateChanged(ItemEvent ev)
      {
        Object o = backends.getSelectedItem();
        newBackend.setEnabled(NEW_BACKEND.equals(o));
      }
    };
    backends.addItemListener(comboListener);
    comboListener.itemStateChanged(null);

    gbc.insets.top = 10;
    gbc.gridx = 0;
    gbc.gridy ++;
    gbc.insets.left = 0;
    gbc.gridwidth = 1;
    lDirectoryBaseDN =
      Utilities.createPrimaryLabel(INFO_CTRL_PANEL_BASE_DN_LABEL.get());
    add(lDirectoryBaseDN, gbc);

    gbc.gridx = 1;
    gbc.insets.left = 10;
    gbc.gridwidth = 2;
    baseDN = Utilities.createTextField();
    documentListener = new DocumentListener()
    {
      /**
       * {@inheritDoc}
       */
      public void changedUpdate(DocumentEvent ev)
      {
        String text = baseDN.getText().trim();
        setEnabledOK((text != null) && (text.length() > 0) &&
            !errorPane.isVisible());
      }

      /**
       * {@inheritDoc}
       */
      public void removeUpdate(DocumentEvent ev)
      {
        changedUpdate(ev);
      }

      /**
       * {@inheritDoc}
       */
      public void insertUpdate(DocumentEvent ev)
      {
        changedUpdate(ev);
      }
    };
    baseDN.getDocument().addDocumentListener(documentListener);
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(baseDN, gbc);
    gbc.gridy ++;
    gbc.anchor = GridBagConstraints.EAST;
    gbc.insets.top = 3;
    JLabel inlineHelp =
      Utilities.createInlineHelpLabel(INFO_CTRL_PANEL_BASE_DN_EXAMPLE.get());
    add(inlineHelp, gbc);

    gbc.gridx = 0;
    gbc.gridy ++;
    gbc.insets.left = 0;
    gbc.insets.top = 10;
    gbc.gridwidth = 1;
    gbc.weightx = 0.0;
    lDirectoryData = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_DIRECTORY_DATA_LABEL.get());
    add(lDirectoryData, gbc);

    onlyCreateBaseEntry = Utilities.createRadioButton(
        INFO_CTRL_PANEL_ONLY_CREATE_BASE_ENTRY_LABEL.get());
    onlyCreateBaseEntry.setSelected(false);

    gbc.insets.left = 10;
    gbc.gridx = 1;
    gbc.gridwidth = 2;
    add(onlyCreateBaseEntry, gbc);

    leaveDatabaseEmpty = Utilities.createRadioButton(
        INFO_CTRL_PANEL_LEAVE_DATABASE_EMPTY_LABEL.get());
    leaveDatabaseEmpty.setSelected(false);

    gbc.gridy ++;
    gbc.gridwidth = 2;
    gbc.insets.top = 5;
    add(leaveDatabaseEmpty, gbc);

    importDataFromLDIF = Utilities.createRadioButton(
        INFO_CTRL_PANEL_IMPORT_FROM_LDIF_LABEL.get());
    importDataFromLDIF.setSelected(false);

    gbc.gridy ++;
    gbc.gridwidth = 2;
    add(importDataFromLDIF, gbc);

    gbc.gridy ++;
    gbc.gridwidth = 2;
    gbc.insets.left = 30;
    add(createPathPanel(), gbc);

    importAutomaticallyGenerated = Utilities.createRadioButton(
        INFO_CTRL_PANEL_IMPORT_AUTOMATICALLY_GENERATED_LABEL.get());
    importAutomaticallyGenerated.setOpaque(false);
    importAutomaticallyGenerated.setSelected(false);

    gbc.gridy ++;
    gbc.gridwidth = 2;
    gbc.insets.left = 10;
    add(importAutomaticallyGenerated, gbc);

    gbc.gridy ++;
    gbc.gridwidth = 2;
    gbc.insets.left = 30;
    add(createNumberOfUsersPanel(), gbc);

    ButtonGroup group = new ButtonGroup();
    group.add(onlyCreateBaseEntry);
    group.add(leaveDatabaseEmpty);
    group.add(importDataFromLDIF);
    group.add(importAutomaticallyGenerated);

    ChangeListener listener = new ChangeListener()
    {
      /**
       * {@inheritDoc}
       */
      public void stateChanged(ChangeEvent ev)
      {
        browseImportPath.setEnabled(importDataFromLDIF.isSelected());
        lPath.setEnabled(importDataFromLDIF.isSelected());
        numberOfEntries.setEnabled(importAutomaticallyGenerated.isSelected());
        lNumberOfEntries.setEnabled(importAutomaticallyGenerated.isSelected());
      }
    };
    Enumeration<AbstractButton> buttons = group.getElements();
    while (buttons.hasMoreElements())
    {
      buttons.nextElement().addChangeListener(listener);
    }
    onlyCreateBaseEntry.setSelected(true);
    listener.stateChanged(null);

    addBottomGlue(gbc);
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    ServerDescriptor desc = ev.getNewDescriptor();
    final SortedSet<String> sortedBackends = new TreeSet<String>();
    for (BackendDescriptor backend : desc.getBackends())
    {
      if (!backend.isConfigBackend())
      {
        sortedBackends.add(backend.getBackendID());
      }
    }
    ArrayList<Object> newElements = new ArrayList<Object>();
    newElements.addAll(sortedBackends);
    if (sortedBackends.size() > 0)
    {
      newElements.add(COMBO_SEPARATOR);
    }
    newElements.add(NEW_BACKEND);
    super.updateComboBoxModel(newElements,
        ((DefaultComboBoxModel)backends.getModel()));
    updateErrorPaneAndOKButtonIfAuthRequired(getInfo().getServerDescriptor(),
        INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_FOR_CREATE_BASE_DN.get());
  }

  private JPanel createPathPanel()
  {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridwidth = 1;
    lPath = Utilities.createDefaultLabel(
        INFO_CTRL_PANEL_IMPORT_LDIF_PATH_LABEL.get());
    panel.add(lPath, gbc);

    gbc.gridx = 1;
    gbc.insets.left = 10;
    path = Utilities.createTextField();
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(path, gbc);
    browseImportPath =
      Utilities.createButton(INFO_CTRL_PANEL_BROWSE_BUTTON_LABEL.get());
    browseImportPath.addActionListener(
        new BrowseActionListener(path,
            BrowseActionListener.BrowseType.OPEN_LDIF_FILE,  this));
    gbc.gridx = 2;
    gbc.weightx = 0.0;
    panel.add(browseImportPath, gbc);

    return panel;
  }

  private JPanel createNumberOfUsersPanel()
  {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.weightx = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    lNumberOfEntries = Utilities.createDefaultLabel(
        INFO_CTRL_PANEL_NUMBER_OF_USER_ENTRIES_LABEL.get());
    panel.add(lNumberOfEntries, gbc);

    gbc.gridx = 1;
    gbc.insets.left = 10;
    numberOfEntries = Utilities.createTextField("2000", 6);
    panel.add(numberOfEntries, gbc);

    gbc.gridx = 2;
    gbc.insets.left = 0;
    gbc.weightx = 1.0;
    panel.add(Box.createHorizontalGlue(), gbc);

    return panel;
  }

  /**
   * {@inheritDoc}
   */
  public void cancelClicked()
  {
    setPrimaryValid(lBackend);
    setPrimaryValid(lDirectoryBaseDN);
    setPrimaryValid(lDirectoryData);
    setSecondaryValid(lPath);
    setSecondaryValid(lNumberOfEntries);
    super.cancelClicked();
  }


  /**
   * {@inheritDoc}
   */
  protected void checkOKButtonEnable()
  {
    documentListener.changedUpdate(null);
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    setPrimaryValid(lBackend);
    setPrimaryValid(lDirectoryBaseDN);
    setPrimaryValid(lDirectoryData);
    setSecondaryValid(lPath);
    setSecondaryValid(lNumberOfEntries);
    final LinkedHashSet<Message> errors = new LinkedHashSet<Message>();

    ServerDescriptor desc = getInfo().getServerDescriptor();

    Set<BackendDescriptor> backendObjects = desc.getBackends();

    Object o = backends.getSelectedItem();
    String backendName = String.valueOf(o);
    if (o == null)
    {
      errors.add(ERR_CTRL_PANEL_NO_BACKENDS_SELECTED.get());
      setPrimaryInvalid(lBackend);
    }
    else if (o.equals(NEW_BACKEND))
    {
      backendName = newBackend.getText().trim();
      if (backendName.length() == 0)
      {
        errors.add(ERR_NEW_BACKEND_NAME_REQUIRED.get());
        setPrimaryInvalid(lBackend);
      }
      else
      {
        // Check that the backend is not already defined.
        for (BackendDescriptor backend : backendObjects)
        {
          if (backendName.equalsIgnoreCase(backend.getBackendID()))
          {
            errors.add(ERR_BACKEND_ALREADY_EXISTS.get(backendName));
            setPrimaryInvalid(lBackend);
            break;
          }
        }
      }
    }

    String dn = baseDN.getText();
    if (dn.trim().length() == 0)
    {
      errors.add(ERR_NEW_BASE_DN_VALUE_REQUIRED.get());
      setPrimaryInvalid(lDirectoryBaseDN);
    }
    else
    {
      try
      {
        DN theDN = DN.decode(dn);
        // Check that the DN is not defined.
        boolean baseDNAlreadyDefined = false;
        for (BackendDescriptor backend : backendObjects)
        {
          for (BaseDNDescriptor baseDN : backend.getBaseDns())
          {
            if (baseDN.getDn().equals(theDN))
            {
              errors.add(ERR_BASE_DN_ALREADY_EXISTS.get(dn));
              setPrimaryInvalid(lDirectoryBaseDN);
              baseDNAlreadyDefined = true;
              break;
            }
            else if (baseDN.getDn().isAncestorOf(theDN))
            {
              if (backendName.equalsIgnoreCase(backend.getBackendID()))
              {
                errors.add(ERR_BASE_DN_ANCESTOR_EXISTS.get(
                    baseDN.getDn().toString()));
                setPrimaryInvalid(lDirectoryBaseDN);
                baseDNAlreadyDefined = true;
                break;
              }
            }
            else if (theDN.isAncestorOf(baseDN.getDn()))
            {
              if (backendName.equalsIgnoreCase(backend.getBackendID()))
              {
                errors.add(ERR_BASE_DN_DN_IS_ANCESTOR_OF.get(
                    baseDN.getDn().toString()));
                setPrimaryInvalid(lDirectoryBaseDN);
                baseDNAlreadyDefined = true;
                break;
              }
            }
          }
          if (baseDNAlreadyDefined)
          {
            break;
          }
        }
      }
      catch (OpenDsException oe)
      {
        errors.add(INFO_CTRL_PANEL_INVALID_DN_DETAILS.get(dn,
            oe.getMessageObject().toString()));
        setPrimaryInvalid(lDirectoryBaseDN);
      }
    }
    // TODO: what happens with sub-suffixes?

    if (importDataFromLDIF.isSelected())
    {
      String ldifPath = path.getText();
      if ((ldifPath == null) || (ldifPath.trim().equals("")))
      {
        errors.add(INFO_NO_LDIF_PATH.get());
        setSecondaryInvalid(lPath);
      } else if (!Utils.fileExists(ldifPath))
      {
        errors.add(INFO_LDIF_FILE_DOES_NOT_EXIST.get());
        setSecondaryInvalid(lPath);
      }
    }

    if (importAutomaticallyGenerated.isSelected())
    {
      String nEntries = numberOfEntries.getText();
      int minValue = 1;
      int maxValue = 20000;
      Message errMsg = ERR_NUMBER_OF_ENTRIES_INVALID.get(minValue, maxValue);
      checkIntValue(errors, nEntries, minValue, maxValue, errMsg);
    }

    if (errors.isEmpty())
    {
      ProgressDialog progressDialog = new ProgressDialog(
          Utilities.getParentDialog(this), getTitle(), getInfo());
      NewBaseDNTask newTask = new NewBaseDNTask(getInfo(), progressDialog);
      for (Task task : getInfo().getTasks())
      {
        task.canLaunch(newTask, errors);
      }
      if (errors.isEmpty())
      {
        launchOperation(newTask,
            INFO_CTRL_PANEL_CREATING_BASE_DN_SUMMARY.get(dn),
            INFO_CTRL_PANEL_CREATING_BASE_DN_COMPLETE.get(),
            INFO_CTRL_PANEL_CREATING_BASE_DN_SUCCESSFUL.get(dn),
            ERR_CTRL_PANEL_CREATING_BASE_DN_ERROR_SUMMARY.get(dn),
            null,
            ERR_CTRL_PANEL_CREATING_BASE_DN_ERROR_DETAILS,
            progressDialog);
        progressDialog.setVisible(true);
        baseDN.setText("");
        baseDN.grabFocus();
        Utilities.getParentDialog(this).setVisible(false);
      }
    }
    if (errors.size() > 0)
    {
      displayErrorDialog(errors);
    }
  }

  private String getBackendName()
  {
    Object backendName = backends.getSelectedItem();
    if (NEW_BACKEND.equals(backendName))
    {
      return newBackend.getText().trim();
    }
    else if (backendName != null)
    {
      return backendName.toString();
    }
    else
    {
      return null;
    }
  }

  private boolean isNewBackend()
  {
    return NEW_BACKEND.equals(backends.getSelectedItem());
  }

  /**
   * The task in charge of creating the base DN (and if required, the backend).
   *
   */
  protected class NewBaseDNTask extends Task
  {
    Set<String> backendSet;
    private String newBaseDN;
    private int progressAfterConfigurationUpdate = -1;

    /**
     * The constructor of the task.
     * @param info the control panel info.
     * @param dlg the progress dialog that shows the progress of the task.
     */
    public NewBaseDNTask(ControlPanelInfo info, ProgressDialog dlg)
    {
      super(info, dlg);
      backendSet = new HashSet<String>();
      backendSet.add(getBackendName());
      newBaseDN = baseDN.getText();

      if (onlyCreateBaseEntry.isSelected())
      {
        progressAfterConfigurationUpdate = 40;
      }
      else if (leaveDatabaseEmpty.isSelected())
      {
        progressAfterConfigurationUpdate = 90;
      }
      else if (importAutomaticallyGenerated.isSelected())
      {
        int nEntries = Integer.parseInt(numberOfEntries.getText().trim());
        if (nEntries < 500)
        {
          progressAfterConfigurationUpdate = 30;
        }
        else if (nEntries < 3000)
        {
          progressAfterConfigurationUpdate = 15;
        }
        else
        {
          progressAfterConfigurationUpdate = 5;
        }
      }
    }

    /**
     * {@inheritDoc}
     */
    public Type getType()
    {
      return Type.NEW_BASEDN;
    }

    /**
     * {@inheritDoc}
     */
    public Message getTaskDescription()
    {
      return INFO_CTRL_PANEL_NEW_BASE_DN_TASK_DESCRIPTION.get(newBaseDN,
      backendSet.iterator().next());
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
        // backend.
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
     * Returns the equivalent command-line to generate the data.
     * @return the equivalent command-line to generate the data.
     */
    private String getDataCommandLineToDisplay()
    {
      StringBuilder sb = new StringBuilder();
      sb.append(getDataCommandLineName());
      Collection<String> args = getObfuscatedCommandLineArguments(
            getDataCommandLineArguments(path.getText(), false));
      args.removeAll(getConfigCommandLineArguments());
      for (String arg : args)
      {
        sb.append(" "+CommandBuilder.escapeValue(arg));
      }
      return sb.toString();
    }

    /**
     * Returns the path of the command-line to be used to generate the data.
     * @return the path of the command-line to be used to generate the data.
     */
    private String getDataCommandLineName()
    {
      String cmdLineName;
      if (!leaveDatabaseEmpty.isSelected())
      {
        cmdLineName = getCommandLinePath("import-ldif");
      }
      else
      {
        cmdLineName = null;
      }
      return cmdLineName;
    }

    /**
     * Returns the arguments of the command-line that can be used to generate
     * the data.
     * @param ldifFile the LDIF file.
     * @param useTemplate whether to use a template or not.
     * @return the arguments of the command-line that can be used to generate
     * the data.
     */
    private ArrayList<String> getDataCommandLineArguments(String ldifFile,
        boolean useTemplate)
    {
      ArrayList<String> args = new ArrayList<String>();
      if (!leaveDatabaseEmpty.isSelected())
      {
        if (!useTemplate)
        {
          args.add("--ldifFile");
          args.add(ldifFile);
        }
        else
        {
          args.add("--templateFile");
          args.add(ldifFile);
          args.add("--randomSeed");
          args.add("0");
        }
        args.add("--backendID");
        args.add(getBackendName());
        args.add("--append");

        args.addAll(getConnectionCommandLineArguments());

        if (isServerRunning())
        {
          args.addAll(getConfigCommandLineArguments());
        }

        args.add(getNoPropertiesFileArgument());
      }
      return args;
    }

    private void updateConfiguration() throws OpenDsException
    {
      boolean configHandlerUpdated = false;
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
        else
        {
          SwingUtilities.invokeLater(new Runnable()
          {
            /**
             * {@inheritDoc}
             */
            public void run()
            {
              StringBuilder sb = new StringBuilder();
              sb.append(getConfigCommandLineFullPath());
              Collection<String> args =
                getObfuscatedCommandLineArguments(
                    getDSConfigCommandLineArguments());
              args.removeAll(getConfigCommandLineArguments());
              for (String arg : args)
              {
                sb.append(" "+CommandBuilder.escapeValue(arg));
              }
              getProgressDialog().appendProgressHtml(Utilities.applyFont(
                  INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_CREATE_BASE_DN.get()+
                  "<br><b>"+sb.toString()+"</b><br><br>",
                  ColorAndFontConstants.progressFont));
            }
          });
        }
        if (isNewBackend())
        {
          SwingUtilities.invokeLater(new Runnable()
          {
            /**
             * {@inheritDoc}
             */
            public void run()
            {
              Message msg = INFO_CTRL_PANEL_CREATING_BACKEND_PROGRESS.get(
                  getBackendName(), newBaseDN);
              getProgressDialog().appendProgressHtml(
                  Utilities.getProgressWithPoints(msg,
                  ColorAndFontConstants.progressFont));
            }
          });
          if (isServerRunning())
          {
            createBackend(getInfo().getDirContext(), getBackendName(),
                newBaseDN);
          }
          else
          {
            createBackend(getBackendName(), newBaseDN);
            createAdditionalIndexes(getBackendName());
          }
        }
        else
        {
          SwingUtilities.invokeLater(new Runnable()
          {
            /**
             * {@inheritDoc}
             */
            public void run()
            {
              Message msg = INFO_CTRL_PANEL_CREATING_BASE_DN_PROGRESS.get(
                  newBaseDN, getBackendName());
              getProgressDialog().appendProgressHtml(
                  Utilities.getProgressWithPoints(msg,
                  ColorAndFontConstants.progressFont));
            }
          });
          if (isServerRunning())
          {
            addBaseDN(getInfo().getDirContext(), getBackendName(), newBaseDN);
          }
          else
          {
            addBaseDN(getBackendName(), newBaseDN);
          }
        }
        SwingUtilities.invokeLater(new Runnable()
        {
          /**
           * {@inheritDoc}
           */
          public void run()
          {
            getProgressDialog().appendProgressHtml(
                Utilities.getProgressDone(ColorAndFontConstants.progressFont)+
            "<br><br>");
          }
        });

        if (isNewBackend() && isServerRunning())
        {
          // Create additional indexes and display the equivalent command.
          // Everything is done in the method createAdditionalIndexes
          createAdditionalIndexes(getInfo().getDirContext(), getBackendName());
        }

        if (progressAfterConfigurationUpdate > 0)
        {
          SwingUtilities.invokeLater(new Runnable()
          {
            /**
             * {@inheritDoc}
             */
            public void run()
            {
              getProgressDialog().getProgressBar().setIndeterminate(false);
              getProgressDialog().getProgressBar().setValue(
                  progressAfterConfigurationUpdate);
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
     * Creates the data in the new base DN.
     * @throws OpenDsException if there is an error importing contents.
     * @throws IOException if there is an err
     */
    private void updateData() throws OpenDsException, IOException
    {
      final boolean leaveEmpty = leaveDatabaseEmpty.isSelected();
      final boolean createBaseEntry = onlyCreateBaseEntry.isSelected();
      final boolean importLDIF = importDataFromLDIF.isSelected();
      final boolean generateData = !leaveEmpty && !createBaseEntry &&
      !importLDIF;
      final String nEntries = numberOfEntries.getText();
      final String ldif = path.getText();
      if (leaveEmpty)
      {
        state = State.FINISHED_SUCCESSFULLY;
      }
      else
      {
        final ProgressDialog progressDialog = getProgressDialog();
        String ldifFile;
        if (importLDIF)
        {
          ldifFile = ldif;
          final String cmdLine = getDataCommandLineToDisplay();
          SwingUtilities.invokeLater(new Runnable()
          {
            public void run()
            {
              progressDialog.appendProgressHtml(Utilities.applyFont(
                  "Equivalent command line:<br><b>"+cmdLine+"</b><br><br>",
                  ColorAndFontConstants.progressFont));
            }
          });
        }
        else if (createBaseEntry)
        {
          SwingUtilities.invokeLater(new Runnable()
          {
            public void run()
            {
              progressDialog.appendProgressHtml(Utilities.getProgressWithPoints(
                  INFO_PROGRESS_CREATING_BASE_ENTRY.get(newBaseDN),
                  ColorAndFontConstants.progressFont));
            }
          });
          InstallerHelper helper = new InstallerHelper();
          File f = helper.createBaseEntryTempFile(newBaseDN);
          ldifFile = f.getAbsolutePath();
        }
        else
        {
          SwingUtilities.invokeLater(new Runnable()
          {
            public void run()
            {
               progressDialog.appendProgressHtml(Utilities.applyFont(
                   INFO_PROGRESS_IMPORT_AUTOMATICALLY_GENERATED.get(nEntries).
                   toString(), ColorAndFontConstants.progressFont)+"<br>");
            }
          });
          File f = SetupUtils.createTemplateFile(newBaseDN,
              Integer.parseInt(nEntries));
          ldifFile = f.getAbsolutePath();
        }
        ArrayList<String> arguments = getDataCommandLineArguments(ldifFile,
            generateData);

        String[] args = new String[arguments.size()];

        arguments.toArray(args);
        if (createBaseEntry)
        {
          outPrintStream.setNotifyListeners(false);
          errorPrintStream.setNotifyListeners(false);
        }
        try
        {
          if (isServerRunning())
          {
            returnCode = ImportLDIF.mainImportLDIF(args, false, outPrintStream,
                errorPrintStream);
          }
          else
          {
            returnCode = executeCommandLine(getDataCommandLineName(), args);
          }
        }
        finally
        {
          if (createBaseEntry)
          {
            outPrintStream.setNotifyListeners(true);
            errorPrintStream.setNotifyListeners(true);
          }
        }

        if (returnCode != 0)
        {
          state = State.FINISHED_WITH_ERROR;
        }
        else
        {
          if (createBaseEntry)
          {
            SwingUtilities.invokeLater(new Runnable()
            {
              public void run()
              {
                progressDialog.appendProgressHtml(
                    Utilities.getProgressDone(
                        ColorAndFontConstants.progressFont));
              }
            });
          }
          state = State.FINISHED_SUCCESSFULLY;
        }
      }
    }

    private void createBackend(InitialLdapContext ctx, String backendName,
        String baseDN) throws OpenDsException
    {
      ManagementContext mCtx = LDAPManagementContext.createFromContext(
          JNDIDirContextAdaptor.adapt(ctx));
      RootCfgClient root = mCtx.getRootConfiguration();
      LocalDBBackendCfgDefn provider = LocalDBBackendCfgDefn.getInstance();
      LocalDBBackendCfgClient backend = root.createBackend(provider,
          backendName, null);
      backend.setEnabled(true);
      Set<DN> baseDNs = new HashSet<DN>();
      baseDNs.add(DN.decode(baseDN));
      backend.setBaseDN(baseDNs);
      backend.setBackendId(backendName);
      backend.setWritabilityMode(BackendCfgDefn.WritabilityMode.ENABLED);
      backend.commit();
    }

    private String getBackendLdif(String backendName)
    {
      String dn = Utilities.getRDNString("ds-cfg-backend-id", backendName)+
      ",cn=Backends,cn=config";
      String ldif = Utilities.makeLdif(
          "dn: "+dn,
          "objectClass: top",
          "objectClass: ds-cfg-backend",
          "objectClass: ds-cfg-local-db-backend",
          "ds-cfg-base-dn: "+newBaseDN,
          "ds-cfg-enabled: true",
          "ds-cfg-writability-mode: enabled",
          "ds-cfg-java-class: " +
          org.opends.server.backends.jeb.BackendImpl.class.getName(),
          "ds-cfg-backend-id: " + backendName,
          "ds-cfg-db-directory: db",
          "",
          "dn: cn=Index,"+dn,
          "objectClass: top",
          "objectClass: ds-cfg-branch",
          "cn: Index",
          "",
          "dn: ds-cfg-attribute=aci,cn=Index,"+dn,
          "objectClass: ds-cfg-local-db-index",
          "objectClass: top",
          "ds-cfg-attribute: aci",
          "ds-cfg-index-type: presence",
          "",
          "dn: ds-cfg-attribute=ds-sync-hist,cn=Index,"+dn,
          "objectClass: ds-cfg-local-db-index",
          "objectClass: top",
          "ds-cfg-attribute: ds-sync-hist",
          "ds-cfg-index-type: ordering",
          "",
          "dn: ds-cfg-attribute=entryUUID,cn=Index,"+dn,
          "objectClass: ds-cfg-local-db-index",
          "objectClass: top",
          "ds-cfg-attribute: entryUUID",
          "ds-cfg-index-type: equality",
          "",
          "dn: ds-cfg-attribute=objectClass,cn=Index,"+dn,
          "objectClass: ds-cfg-local-db-index",
          "objectClass: top",
          "ds-cfg-attribute: objectClass",
          "ds-cfg-index-type: equality"
      );
      return ldif;
    }

    private String getAdditionalIndexLdif(String backendName)
    {
      String dn = "ds-cfg-backend-id="+backendName+",cn=Backends,cn=config";
      String ldif = Utilities.makeLdif(
          "dn: ds-cfg-attribute=cn,cn=Index,"+dn,
          "objectClass: ds-cfg-local-db-index",
          "objectClass: top",
          "ds-cfg-attribute: cn",
          "ds-cfg-index-type: equality",
          "ds-cfg-index-type: substring",
          "",
          "dn: ds-cfg-attribute=givenName,cn=Index,"+dn,
          "objectClass: ds-cfg-local-db-index",
          "objectClass: top",
          "ds-cfg-attribute: givenName",
          "ds-cfg-index-type: equality",
          "ds-cfg-index-type: substring",
          "",
          "dn: ds-cfg-attribute=mail,cn=Index,"+dn,
          "objectClass: ds-cfg-local-db-index",
          "objectClass: top",
          "ds-cfg-attribute: mail",
          "ds-cfg-index-type: equality",
          "ds-cfg-index-type: substring",
          "",
          "dn: ds-cfg-attribute=member,cn=Index,"+dn,
          "objectClass: ds-cfg-local-db-index",
          "objectClass: top",
          "ds-cfg-attribute: member",
          "ds-cfg-index-type: equality",
          "",
          "dn: ds-cfg-attribute=sn,cn=Index,"+dn,
          "objectClass: ds-cfg-local-db-index",
          "objectClass: top",
          "ds-cfg-attribute: sn",
          "ds-cfg-index-type: equality",
          "ds-cfg-index-type: substring",
          "",
          "dn: ds-cfg-attribute=telephoneNumber,cn=Index,"+dn,
          "objectClass: ds-cfg-local-db-index",
          "objectClass: top",
          "ds-cfg-attribute: telephoneNumber",
          "ds-cfg-index-type: equality",
          "ds-cfg-index-type: substring",
          "",
          "dn: ds-cfg-attribute=uid,cn=Index,"+dn,
          "objectClass: ds-cfg-local-db-index",
          "objectClass: top",
          "ds-cfg-attribute: uid",
          "ds-cfg-index-type: equality",
          "",
          "dn: ds-cfg-attribute=uniqueMember,cn=Index,"+dn,
          "objectClass: ds-cfg-local-db-index",
          "objectClass: top",
          "ds-cfg-attribute: uniqueMember",
          "ds-cfg-index-type: equality"
      );
      return ldif;
    }

    private void createBackend(String backendName, String baseDN)
    throws OpenDsException
    {
      LDIFImportConfig ldifImportConfig = null;
      try
      {
        String ldif = getBackendLdif(backendName);

        ldifImportConfig = new LDIFImportConfig(new StringReader(ldif));
        LDIFReader reader = new LDIFReader(ldifImportConfig);
        Entry backendConfigEntry;
        while ((backendConfigEntry = reader.readEntry()) != null)
        {
          DirectoryServer.getConfigHandler().addEntry(backendConfigEntry, null);
        }
        DirectoryServer.getConfigHandler().writeUpdatedConfig();
      }
      catch (IOException ioe)
      {
        throw new OfflineUpdateException(
            ERR_CTRL_PANEL_ERROR_UPDATING_CONFIGURATION.get(ioe.toString()),
            ioe);
      }
      finally
      {
        if (ldifImportConfig != null)
        {
          ldifImportConfig.close();
        }
      }
    }

    private void createAdditionalIndexes(String backendName)
    throws OpenDsException
    {
      LDIFImportConfig ldifImportConfig = null;
      try
      {
        String ldif = getAdditionalIndexLdif(backendName);

        ldifImportConfig = new LDIFImportConfig(new StringReader(ldif));
        LDIFReader reader = new LDIFReader(ldifImportConfig);
        Entry indexEntry;
        while ((indexEntry = reader.readEntry()) != null)
        {
          DirectoryServer.getConfigHandler().addEntry(indexEntry, null);
        }
        DirectoryServer.getConfigHandler().writeUpdatedConfig();
      }
      catch (IOException ioe)
      {
        throw new OfflineUpdateException(
            ERR_CTRL_PANEL_ERROR_UPDATING_CONFIGURATION.get(ioe.toString()),
            ioe);
      }
      finally
      {
        if (ldifImportConfig != null)
        {
          ldifImportConfig.close();
        }
      }
    }

    private void createAdditionalIndexes(InitialLdapContext ctx,
        String backendName) throws OpenDsException
    {
      ArrayList<ArrayList<String>> argsArray =
        new ArrayList<ArrayList<String>>();
      ArrayList<String> dns = new ArrayList<String>();
      ArrayList<Attributes> attributes = new ArrayList<Attributes>();

      // Instead of adding indexes using management framework, use this approach
      // so that we have to define the additional indexes only in the method
      // getBackendLdif.
      String ldif = getAdditionalIndexLdif(backendName);
      LDIFImportConfig ldifImportConfig = null;
      try
      {
        ldifImportConfig = new LDIFImportConfig(new StringReader(ldif));
        LDIFReader reader = new LDIFReader(ldifImportConfig);
        Entry indexEntry;
        while ((indexEntry = reader.readEntry()) != null)
        {
          ArrayList<String> args = new ArrayList<String>();
          args.add("create-local-db-index");
          args.add("--backend-name");
          args.add(backendName);
          args.add("--type");
          args.add("generic");

          argsArray.add(args);
          Attributes attrs = new BasicAttributes();

          BasicAttribute oc = new BasicAttribute("objectClass");
          Iterator<AttributeValue> it =
            indexEntry.getObjectClassAttribute().iterator();

          while (it.hasNext())
          {
            oc.add(it.next().getValue().toString());
          }
          attrs.put(oc);

          List<org.opends.server.types.Attribute> odsAttrs =
            indexEntry.getAttributes();
          for (org.opends.server.types.Attribute odsAttr : odsAttrs)
          {
            String attrName = odsAttr.getName();
            BasicAttribute attr = new BasicAttribute(attrName);
            it = odsAttr.iterator();
            while (it.hasNext())
            {
              attr.add(it.next().getValue().toString());
            }
            attrs.put(attr);

            if (attrName.equalsIgnoreCase("ds-cfg-attribute"))
            {
              args.add("--index-name");
              AttributeValue value =
                odsAttr.iterator().next();
              args.add(value.getValue().toString());
            }
            else if (attrName.equalsIgnoreCase("ds-cfg-index-type"))
            {
              it = odsAttr.iterator();
              while (it.hasNext())
              {
                args.add("--set");
                args.add("index-type:"+it.next().getValue().toString());
              }
            }
          }
          args.addAll(getConnectionCommandLineArguments());
          args.add(getNoPropertiesFileArgument());
          args.add("--no-prompt");

          dns.add(indexEntry.getDN().toString());
          attributes.add(attrs);
        }

        StringBuilder sb = new StringBuilder();
        for (List<String> args : argsArray)
        {
          sb.append(getCommandLinePath("dsconfig"));
          args = getObfuscatedCommandLineArguments(args);
          for (String arg : args)
          {
            sb.append(" "+CommandBuilder.escapeValue(arg));
          }
          sb.append("<br><br>");
        }
        final String cmdLines = sb.toString();
        SwingUtilities.invokeLater(new Runnable()
        {
          public void run()
          {
            getProgressDialog().appendProgressHtml(Utilities.applyFont(
             INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_CREATE_ADDITIONAL_INDEXES.get()+
             "<br><br><b>"+cmdLines+"</b>",
             ColorAndFontConstants.progressFont));
            getProgressDialog().appendProgressHtml(
                Utilities.getProgressWithPoints(
                    INFO_CTRL_PANEL_CREATING_ADDITIONAL_INDEXES_PROGRESS.get(),
                    ColorAndFontConstants.progressFont));
          }
        });

        for (int i=0; i<dns.size(); i++)
        {
          ctx.createSubcontext(dns.get(i), attributes.get(i));
        }

        SwingUtilities.invokeLater(new Runnable()
        {
          public void run()
          {
            getProgressDialog().appendProgressHtml(
                Utilities.getProgressDone(ColorAndFontConstants.progressFont)+
                "<br><br>");
          }
        });
      }
      catch (Throwable t)
      {
        throw new OnlineUpdateException(
            ERR_CTRL_PANEL_ERROR_UPDATING_CONFIGURATION.get(t.toString()), t);
      }
      finally
      {
        if (ldifImportConfig != null)
        {
          ldifImportConfig.close();
        }
      }
    }

    private void addBaseDN(String backendName, String baseDN)
    throws OpenDsException
    {
      LinkedList<DN> baseDNs = new LinkedList<DN>();
      for (BackendDescriptor backend :
        getInfo().getServerDescriptor().getBackends())
      {
        if (backend.getBackendID().equalsIgnoreCase(backendName))
        {
          for (BaseDNDescriptor b : backend.getBaseDns())
          {
            baseDNs.add(b.getDn());
          }
          break;
        }
      }
      baseDNs.add(DN.decode(baseDN));

      String dn = Utilities.getRDNString("ds-cfg-backend-id", backendName)+
      ",cn=Backends,cn=config";
      ConfigEntry configEntry =
        DirectoryServer.getConfigHandler().getConfigEntry(DN.decode(dn));

      DNConfigAttribute baseDNAttr =
        new DNConfigAttribute(
            ConfigConstants.ATTR_BACKEND_BASE_DN,
            INFO_CONFIG_BACKEND_ATTR_DESCRIPTION_BASE_DNS.get(),
            true, true, false, baseDNs);
      configEntry.putConfigAttribute(baseDNAttr);
      DirectoryServer.getConfigHandler().writeUpdatedConfig();
    }

    private void addBaseDN(InitialLdapContext ctx, String backendName,
        String baseDN) throws OpenDsException
    {
      ManagementContext mCtx = LDAPManagementContext.createFromContext(
          JNDIDirContextAdaptor.adapt(ctx));
      RootCfgClient root = mCtx.getRootConfiguration();
      LocalDBBackendCfgClient backend =
        (LocalDBBackendCfgClient)root.getBackend(backendName);

      Set<DN> baseDNs = backend.getBaseDN();
      DN dn = DN.decode(baseDN);
      baseDNs.add(dn);
      backend.setBaseDN(baseDNs);
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
     * Returns the configuration command-line full path.
     * @return the configuration command-line full path.
     */
    private String getConfigCommandLineFullPath()
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
     * Returns the configuration command-line arguments.
     * @return the configuration command-line arguments.
     */
    private ArrayList<String> getDSConfigCommandLineArguments()
    {
      ArrayList<String> args = new ArrayList<String>();
      if (isServerRunning())
      {
        if (isNewBackend())
        {
          args.add("create-backend");
          args.add("--backend-name");
          args.add(getBackendName());
          args.add("--set");
          args.add("base-dn:"+newBaseDN);
          args.add("--set");
          args.add("enabled:true");
          args.add("--type");
          args.add("local-db");
        }
        else
        {
          args.add("set-backend-prop");
          args.add("--backend-name");
          args.add(getBackendName());
          args.add("--add");
          args.add("base-dn:"+newBaseDN);
        }
        args.addAll(getConnectionCommandLineArguments());
        args.add(getNoPropertiesFileArgument());
        args.add("--no-prompt");
      }
      return args;
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
        updateData();
      }
      catch (Throwable t)
      {
        lastException = t;
        state = State.FINISHED_WITH_ERROR;
      }
    }

    /**
     * {@inheritDoc}
     */
    public Set<String> getBackends()
    {
      return backendSet;
    }
  };
}