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
 *      Portions Copyright 2011-2015 ForgeRock AS
 */

package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.QuickSetupMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.adapter.server3x.Converters;
import org.forgerock.opendj.config.LDAPProfile;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.IndexTypeDescriptor;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.event.BrowseActionListener;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.task.OfflineUpdateException;
import org.opends.guitools.controlpanel.task.OnlineUpdateException;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.ui.renderer.CustomListCellRenderer;
import org.opends.guitools.controlpanel.util.ConfigReader;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.installer.InstallerHelper;
import org.opends.quicksetup.util.Utils;
import org.opends.server.admin.AdminException;
import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;
import org.opends.server.admin.client.ldap.LDAPManagementContext;
import org.opends.server.admin.std.client.BackendCfgClient;
import org.opends.server.admin.std.client.BackendIndexCfgClient;
import org.opends.server.admin.std.client.LocalDBBackendCfgClient;
import org.opends.server.admin.std.client.LocalDBIndexCfgClient;
import org.opends.server.admin.std.client.PluggableBackendCfgClient;
import org.opends.server.admin.std.client.RootCfgClient;
import org.opends.server.admin.std.meta.BackendCfgDefn;
import org.opends.server.admin.std.meta.BackendIndexCfgDefn;
import org.opends.server.admin.std.meta.BackendIndexCfgDefn.IndexType;
import org.opends.server.admin.std.meta.LocalDBIndexCfgDefn;
import org.opends.server.backends.jeb.RemoveOnceLocalDBBackendIsPluggable;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.tools.BackendCreationHelper;
import org.opends.server.tools.BackendCreationHelper.DefaultIndex;
import org.opends.server.tools.BackendTypeHelper;
import org.opends.server.tools.BackendTypeHelper.BackendTypeUIAdapter;
import org.opends.server.tools.ImportLDIF;
import org.opends.server.tools.LDAPModify;
import org.opends.server.tools.makeldif.MakeLDIF;
import org.opends.server.types.DN;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.SetupUtils;

import com.forgerock.opendj.cli.CommandBuilder;

/**
 * The class that appears when the user clicks on 'New Base DN'.
 */
public class NewBaseDNPanel extends StatusGenericPanel
{
  private static final int MAX_ENTRIES_NUMBER_GENERATED = 1000;
  private static final int MAX_ENTRIES_NUMBER_GENERATED_LOCAL = 20000;
  private static final long serialVersionUID = -2680821576362341119L;
  private static final LocalizableMessage NEW_BACKEND_TEXT = INFO_CTRL_PANEL_NEW_BACKEND_LABEL.get();

  private JComboBox<?> backends;
  private JComboBox<BackendTypeUIAdapter> backendTypes;
  private JTextField newBackend;
  private JTextField baseDN;
  private JRadioButton onlyCreateBaseEntry;
  private JRadioButton leaveDatabaseEmpty;
  private JRadioButton importDataFromLDIF;
  private JRadioButton importAutomaticallyGenerated;
  private JTextField path;
  private JTextField numberOfEntries;
  private JLabel lRemoteFileHelp;
  private JButton browseImportPath;

  private JLabel lBackend;
  private JLabel lDirectoryBaseDN;
  private JLabel lPath;
  private JLabel lNumberOfEntries;
  private JLabel lDirectoryData;
  private JLabel lNewBackendType;

  private DocumentListener documentListener;

  /** Default constructor. */
  public NewBaseDNPanel()
  {
    super();
    createLayout();
  }

  /** {@inheritDoc} */
  @Override
  public LocalizableMessage getTitle()
  {
    return INFO_CTRL_PANEL_NEW_BASE_DN_TITLE.get();
  }

  /** {@inheritDoc} */
  @Override
  public Component getPreferredFocusComponent()
  {
    return baseDN;
  }

  /** {@inheritDoc} */
  @Override
  public void toBeDisplayed(boolean visible)
  {
    if (visible)
    {
      documentListener.changedUpdate(null);
    }
  }

  /** Creates the layout of the panel (but the contents are not populated here). */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    addErrorPanel(gbc);
    addBackendLabel(gbc);
    addBackendNamesComboBox(gbc);
    addNewBackendName(gbc);
    addNewBackendTypeLabel(gbc);
    addNewBackendTypeComboBox(gbc);
    addBaseDNLabel(gbc);
    addBaseDNTextField(gbc);
    addBaseDNInlineHelp(gbc);
    addDirectoryDataLabel(gbc);
    addImportDataChoiceSection(gbc);
    addBottomGlue(gbc);
  }

  private void addErrorPanel(GridBagConstraints gbc)
  {
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 3;
    addErrorPane(gbc);
  }

  private void addBackendLabel(GridBagConstraints gbc)
  {
    gbc.anchor = GridBagConstraints.WEST;
    gbc.weightx = 0.0;
    gbc.gridwidth = 1;
    gbc.gridy++;
    gbc.fill = GridBagConstraints.NONE;
    lBackend = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_BACKEND_LABEL.get());
    add(lBackend, gbc);
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private void addBackendNamesComboBox(GridBagConstraints gbc)
  {
    gbc.insets.left = 10;
    gbc.gridx = 1;
    backends = Utilities.createComboBox();
    backends.setModel(new DefaultComboBoxModel(new Object[] { "bogus", NEW_BACKEND_TEXT }));
    backends.setRenderer(new CustomListCellRenderer(backends));
    backends.addItemListener(new IgnoreItemListener(backends));
    gbc.gridwidth = 1;
    add(backends, gbc);
  }

  private void addNewBackendTypeLabel(GridBagConstraints gbc)
  {
    gbc.insets.top = 10;
    gbc.gridx = 0;
    gbc.gridy++;
    gbc.insets.left = 0;
    gbc.gridwidth = 1;
    lNewBackendType = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_BASE_DN_NEW_BACKEND_TYPE_LABEL.get());
    add(lNewBackendType, gbc);
    addBackendNameChangeListener(lNewBackendType);
  }

  @SuppressWarnings("unchecked")
  private void addNewBackendTypeComboBox(GridBagConstraints gbc)
  {
    gbc.insets.left = 10;
    gbc.gridx = 1;
    gbc.gridwidth = 1;
    final BackendTypeHelper backendTypeHelper = new BackendTypeHelper();
    backendTypes = Utilities.createComboBox();
    backendTypes.setModel(new DefaultComboBoxModel<>(backendTypeHelper.getBackendTypeUIAdaptors()));
    backendTypes.setRenderer(new CustomListCellRenderer(backendTypes));
    backendTypes.addItemListener(new IgnoreItemListener(backendTypes));
    add(backendTypes, gbc);
    addBackendNameChangeListener(backendTypes);
  }

  private void addNewBackendName(GridBagConstraints gbc)
  {
    gbc.gridx = 2;
    newBackend = Utilities.createTextField();
    newBackend.setColumns(18);
    add(newBackend, gbc);
    addBackendNameChangeListener(newBackend);
  }

  private void addBackendNameChangeListener(final JComponent component)
  {
    ItemListener comboListener = new ItemListener()
    {
      @Override
      public void itemStateChanged(ItemEvent ev)
      {
        Object o = backends.getSelectedItem();
        component.setVisible(NEW_BACKEND_TEXT.equals(o));
      }
    };
    backends.addItemListener(comboListener);
    comboListener.itemStateChanged(null);
  }

  private void addBaseDNLabel(GridBagConstraints gbc)
  {
    gbc.insets.top = 10;
    gbc.gridx = 0;
    gbc.gridy++;
    gbc.insets.left = 0;
    gbc.gridwidth = 1;
    lDirectoryBaseDN = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_BASE_DN_LABEL.get());
    add(lDirectoryBaseDN, gbc);
  }

  private void addBaseDNTextField(GridBagConstraints gbc)
  {
    gbc.gridx = 1;
    gbc.insets.left = 10;
    gbc.gridwidth = 2;
    baseDN = Utilities.createTextField();
    documentListener = new DocumentListener()
    {
      @Override
      public void changedUpdate(DocumentEvent ev)
      {
        String text = baseDN.getText().trim();
        setEnabledOK(text != null && text.length() > 0 && !errorPane.isVisible());
      }

      @Override
      public void removeUpdate(DocumentEvent ev)
      {
        changedUpdate(ev);
      }

      @Override
      public void insertUpdate(DocumentEvent ev)
      {
        changedUpdate(ev);
      }
    };
    baseDN.getDocument().addDocumentListener(documentListener);
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(baseDN, gbc);
  }

  private void addBaseDNInlineHelp(GridBagConstraints gbc)
  {
    gbc.gridy++;
    gbc.anchor = GridBagConstraints.EAST;
    gbc.insets.top = 3;
    JLabel inlineHelp = Utilities.createInlineHelpLabel(INFO_CTRL_PANEL_BASE_DN_EXAMPLE.get());
    add(inlineHelp, gbc);
  }

  private void addDirectoryDataLabel(GridBagConstraints gbc)
  {
    gbc.gridx = 0;
    gbc.gridy++;
    gbc.insets.left = 0;
    gbc.insets.top = 10;
    gbc.gridwidth = 1;
    gbc.weightx = 0.0;
    lDirectoryData = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_DIRECTORY_DATA_LABEL.get());
    add(lDirectoryData, gbc);
  }

  private void addImportDataChoiceSection(GridBagConstraints gbc)
  {
    onlyCreateBaseEntry = Utilities.createRadioButton(INFO_CTRL_PANEL_ONLY_CREATE_BASE_ENTRY_LABEL.get());
    onlyCreateBaseEntry.setSelected(false);

    gbc.insets.left = 10;
    gbc.gridx = 1;
    gbc.gridwidth = 2;
    add(onlyCreateBaseEntry, gbc);

    leaveDatabaseEmpty = Utilities.createRadioButton(INFO_CTRL_PANEL_LEAVE_DATABASE_EMPTY_LABEL.get());
    leaveDatabaseEmpty.setSelected(false);

    gbc.gridy++;
    gbc.gridwidth = 2;
    gbc.insets.top = 5;
    add(leaveDatabaseEmpty, gbc);

    importDataFromLDIF = Utilities.createRadioButton(INFO_CTRL_PANEL_IMPORT_FROM_LDIF_LABEL.get());
    importDataFromLDIF.setSelected(false);

    gbc.gridy++;
    gbc.gridwidth = 2;
    add(importDataFromLDIF, gbc);

    gbc.gridy++;
    gbc.gridwidth = 2;
    gbc.insets.left = 30;
    add(createPathPanel(), gbc);

    importAutomaticallyGenerated =
        Utilities.createRadioButton(INFO_CTRL_PANEL_IMPORT_AUTOMATICALLY_GENERATED_LABEL.get());
    importAutomaticallyGenerated.setOpaque(false);
    importAutomaticallyGenerated.setSelected(false);

    gbc.gridy++;
    gbc.gridwidth = 2;
    gbc.insets.left = 10;
    add(importAutomaticallyGenerated, gbc);

    gbc.gridy++;
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
      /** {@inheritDoc} */
      @Override
      public void stateChanged(ChangeEvent ev)
      {
        browseImportPath.setEnabled(importDataFromLDIF.isSelected());
        lPath.setEnabled(importDataFromLDIF.isSelected());
        lRemoteFileHelp.setEnabled(importDataFromLDIF.isSelected());
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
  }

  /** {@inheritDoc} */
  @Override
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    ServerDescriptor desc = ev.getNewDescriptor();
    final SortedSet<String> sortedBackends = new TreeSet<>();
    for (BackendDescriptor backend : desc.getBackends())
    {
      if (!backend.isConfigBackend())
      {
        sortedBackends.add(backend.getBackendID());
      }
    }

    List<Object> newElements = new ArrayList<Object>(sortedBackends);
    if (!sortedBackends.isEmpty())
    {
      newElements.add(COMBO_SEPARATOR);
    }
    newElements.add(NEW_BACKEND_TEXT);
    super.updateComboBoxModel(newElements, (DefaultComboBoxModel<?>) backends.getModel());
    updateErrorPaneAndOKButtonIfAuthRequired(desc,
        isLocal() ? INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_FOR_CREATE_BASE_DN.get()
                  : INFO_CTRL_PANEL_CANNOT_CONNECT_TO_REMOTE_DETAILS.get(desc.getHostname()));
    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        lRemoteFileHelp.setVisible(!isLocal());
        browseImportPath.setVisible(isLocal());
      }
    });
  }

  private JPanel createPathPanel()
  {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridwidth = 1;
    gbc.gridy = 0;
    gbc.gridx = 0;
    lPath = Utilities.createDefaultLabel(INFO_CTRL_PANEL_IMPORT_LDIF_PATH_LABEL.get());
    panel.add(lPath, gbc);

    gbc.gridx = 1;
    gbc.insets.left = 10;
    path = Utilities.createTextField();
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(path, gbc);
    browseImportPath = Utilities.createButton(INFO_CTRL_PANEL_BROWSE_BUTTON_LABEL.get());
    browseImportPath.addActionListener(
        new BrowseActionListener(path, BrowseActionListener.BrowseType.OPEN_LDIF_FILE, this));
    gbc.gridx = 2;
    gbc.weightx = 0.0;
    panel.add(browseImportPath, gbc);

    gbc.gridy++;
    gbc.gridx = 1;
    lRemoteFileHelp = Utilities.createInlineHelpLabel(INFO_CTRL_PANEL_REMOTE_SERVER_PATH.get());
    gbc.insets.top = 3;
    gbc.insets.left = 10;
    panel.add(lRemoteFileHelp, gbc);

    return panel;
  }

  private JPanel createNumberOfUsersPanel()
  {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.weightx = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    lNumberOfEntries = Utilities.createDefaultLabel(INFO_CTRL_PANEL_NUMBER_OF_USER_ENTRIES_LABEL.get());
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

  /** {@inheritDoc} */
  @Override
  public void cancelClicked()
  {
    resetLabelAsValid();
    super.cancelClicked();
  }

  private void resetLabelAsValid()
  {
    setPrimaryValid(lBackend);
    setPrimaryValid(lDirectoryBaseDN);
    setPrimaryValid(lDirectoryData);
    setSecondaryValid(lPath);
    setSecondaryValid(lNumberOfEntries);
  }

  /** {@inheritDoc} */
  @Override
  protected void checkOKButtonEnable()
  {
    documentListener.changedUpdate(null);
  }

  /** {@inheritDoc} */
  @Override
  public void okClicked()
  {
    resetLabelAsValid();

    final Set<LocalizableMessage> errors = new LinkedHashSet<>();
    final ServerDescriptor desc = getInfo().getServerDescriptor();
    final Set<BackendDescriptor> existingBackends = desc.getBackends();

    final String backendName = validateBackendName(existingBackends, errors);
    final String dn = validateBaseDN(backendName, existingBackends, errors);
    validateImportLDIFFilePath(errors);
    validateAutomaticallyGenerated(errors);

    if (errors.isEmpty())
    {
      final ProgressDialog progressDialog = new ProgressDialog(
          Utilities.createFrame(), Utilities.getParentDialog(this), getTitle(), getInfo());
      final NewBaseDNTask newTask = new NewBaseDNTask(getInfo(), progressDialog);
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

    if (!errors.isEmpty())
    {
      displayErrorDialog(errors);
    }
  }

  /** Returns the existing or the new backend name, once user have clicked on 'OK' button. */
  private String validateBackendName(
      final Set<BackendDescriptor> existingBackends, final Set<LocalizableMessage> errors)
  {
    final Object selectedItem = backends.getSelectedItem();
    if (!selectedItem.equals(NEW_BACKEND_TEXT))
    {
      return selectedItem.toString();
    }

    final String backendName = newBackend.getText().trim();
    if (backendName.length() == 0)
    {
      errors.add(ERR_NEW_BACKEND_NAME_REQUIRED.get());
      setPrimaryInvalid(lBackend);
      return backendName;
    }

    // Check that the backend is not already defined.
    for (BackendDescriptor backend : existingBackends)
    {
      if (backendName.equalsIgnoreCase(backend.getBackendID()))
      {
        errors.add(ERR_BACKEND_ALREADY_EXISTS.get(backendName));
        setPrimaryInvalid(lBackend);
      }
    }

    return backendName;
  }

  private String validateBaseDN(final String backendName, final Set<BackendDescriptor> existingBackends,
      final Set<LocalizableMessage> errors)
  {
    String dn = baseDN.getText();
    if (dn.trim().length() == 0)
    {
      errors.add(ERR_NEW_BASE_DN_VALUE_REQUIRED.get());
      setPrimaryInvalid(lDirectoryBaseDN);
      return dn;
    }

    try
    {
      final DN theDN = DN.valueOf(dn);
      for (final BackendDescriptor backend : existingBackends)
      {
        for (final BaseDNDescriptor baseDN : backend.getBaseDns())
        {
          if (baseDN.getDn().equals(theDN))
          {
            return invalidBaseDNValue(dn, ERR_BASE_DN_ALREADY_EXISTS.get(dn), errors);
          }
          else if (baseDN.getDn().isAncestorOf(theDN) && backendName.equalsIgnoreCase(backend.getBackendID()))
          {
            return invalidBaseDNValue(dn, ERR_BASE_DN_ANCESTOR_EXISTS.get(baseDN.getDn()), errors);
          }
          else if (theDN.isAncestorOf(baseDN.getDn()) && backendName.equalsIgnoreCase(backend.getBackendID()))
          {
            return invalidBaseDNValue(dn, ERR_BASE_DN_DN_IS_ANCESTOR_OF.get(baseDN.getDn()), errors);
          }
        }
      }
    }
    catch (OpenDsException oe)
    {
      errors.add(INFO_CTRL_PANEL_INVALID_DN_DETAILS.get(dn, oe.getMessageObject()));
      setPrimaryInvalid(lDirectoryBaseDN);
    }

    return dn;
  }

  /** Mark the provided base DN as invalid with the provided reason and return it. */
  private String invalidBaseDNValue(final String dn, final LocalizableMessage errorMsg,
      final Set<LocalizableMessage> errors)
  {
    errors.add(errorMsg);
    setPrimaryInvalid(lDirectoryBaseDN);
    return dn;
  }

  private void validateImportLDIFFilePath(final Set<LocalizableMessage> errors)
  {
    // TODO: what happens with sub-suffixes?
    if (importDataFromLDIF.isSelected())
    {
      String ldifPath = path.getText();
      if (ldifPath == null || "".equals(ldifPath.trim()))
      {
        errors.add(INFO_NO_LDIF_PATH.get());
        setSecondaryInvalid(lPath);
      }
      else if (isLocal() && !Utils.fileExists(ldifPath))
      {
        errors.add(INFO_LDIF_FILE_DOES_NOT_EXIST.get());
        setSecondaryInvalid(lPath);
      }
    }
  }

  private void validateAutomaticallyGenerated(final Set<LocalizableMessage> errors)
  {
    if (importAutomaticallyGenerated.isSelected())
    {
      final int minValue = 1;
      final int maxValue = isLocal() ? MAX_ENTRIES_NUMBER_GENERATED_LOCAL : MAX_ENTRIES_NUMBER_GENERATED;
      final LocalizableMessage errorMsg = ERR_NUMBER_OF_ENTRIES_INVALID.get(minValue, maxValue);
      if (!checkIntValue(errors, numberOfEntries.getText(), minValue, maxValue, errorMsg))
      {
        setSecondaryInvalid(lNumberOfEntries);
      }
    }
  }

  private String getBackendName()
  {
    Object backendName = backends.getSelectedItem();
    if (NEW_BACKEND_TEXT.equals(backendName))
    {
      return newBackend.getText().trim();
    }
    else if (backendName != null)
    {
      return backendName.toString();
    }

    return null;
  }

  private BackendTypeUIAdapter getSelectedBackendType()
  {
    return (BackendTypeUIAdapter) backendTypes.getSelectedItem();
  }

  private boolean isNewBackend()
  {
    return NEW_BACKEND_TEXT.equals(backends.getSelectedItem());
  }

  /** The task in charge of creating the base DN (and if required, the backend). */
  protected class NewBaseDNTask extends Task
  {
    private final Set<String> backendSet;
    private final String newBaseDN;
    private int progressAfterConfigurationUpdate = -1;

    /**
     * The constructor of the task.
     *
     * @param info
     *          the control panel info.
     * @param dlg
     *          the progress dialog that shows the progress of the task.
     */
    public NewBaseDNTask(ControlPanelInfo info, ProgressDialog dlg)
    {
      super(info, dlg);
      backendSet = new HashSet<>();
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

    /** {@inheritDoc} */
    @Override
    public Type getType()
    {
      return Type.NEW_BASEDN;
    }

    /** {@inheritDoc} */
    @Override
    public LocalizableMessage getTaskDescription()
    {
      return INFO_CTRL_PANEL_NEW_BASE_DN_TASK_DESCRIPTION.get(newBaseDN, backendSet.iterator().next());
    }

    /** {@inheritDoc} */
    @Override
    public boolean canLaunch(Task taskToBeLaunched, Collection<LocalizableMessage> incompatibilityReasons)
    {
      boolean canLaunch = true;
      if (state == State.RUNNING && runningOnSameServer(taskToBeLaunched))
      {
        // All the operations are incompatible if they apply to this backend.
        Set<String> backends = new TreeSet<>(taskToBeLaunched.getBackends());
        backends.retainAll(getBackends());
        if (!backends.isEmpty())
        {
          incompatibilityReasons.add(getIncompatibilityMessage(this, taskToBeLaunched));
          canLaunch = false;
        }
      }
      return canLaunch;
    }

    private String getDataCommandLineToDisplay()
    {
      StringBuilder sb = new StringBuilder();
      sb.append(getDataCommandLineName());
      Collection<String> args = getObfuscatedCommandLineArguments(getDataCommandLineArguments(path.getText(), false));
      args.removeAll(getConfigCommandLineArguments());
      for (String arg : args)
      {
        sb.append(" ").append(CommandBuilder.escapeValue(arg));
      }
      return sb.toString();
    }

    private String getDataCommandLineName()
    {
      if (!leaveDatabaseEmpty.isSelected())
      {
        return getCommandLinePath(isLocal() ? "import-ldif" : "ldapmodify");
      }

      return null;
    }

    /**
     * Returns the arguments of the command-line that can be used to generate
     * the data.
     *
     * @param ldifFile
     *          the LDIF file.
     * @param useTemplate
     *          whether to use a template or not.
     * @return the arguments of the command-line that can be used to generate
     *         the data.
     */
    private List<String> getDataCommandLineArguments(String ldifFile, boolean useTemplate)
    {
      List<String> args = new ArrayList<>();
      if (!leaveDatabaseEmpty.isSelected())
      {
        if (isLocal())
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
        }
        else
        {
          // If we are not local, we use ldapmodify to update the contents.
          args.add("-a");
          args.add("-f");
          args.add(ldifFile);
        }
        args.addAll(getConnectionCommandLineArguments(true, !isLocal()));

        if (isServerRunning() && isLocal())
        {
          args.addAll(getConfigCommandLineArguments());
        }

        args.add(getNoPropertiesFileArgument());
      }

      return args;
    }

    private void updateConfigurationOnline() throws OpenDsException
    {
      SwingUtilities.invokeLater(new Runnable()
      {
        @Override
        public void run()
        {
          List<String> args = getObfuscatedCommandLineArguments(getDSConfigCommandLineArguments());
          args.removeAll(getConfigCommandLineArguments());
          printEquivalentCommandLine(
              getConfigCommandLineFullPath(), args, INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_CREATE_BASE_DN.get());
        }
      });

      performTask();
      printTaskDone();
      if (isNewBackend())
      {
        createAdditionalIndexes();
      }
      refreshProgressBar();
    }

    private void updateConfigurationOffline() throws OpenDsException
    {
      boolean configHandlerUpdated = false;
      try
      {
        getInfo().stopPooling();
        if (getInfo().mustDeregisterConfig())
        {
          DirectoryServer.deregisterBaseDN(DN.valueOf("cn=config"));
        }
        DirectoryServer.getInstance().initializeConfiguration(
            ConfigFileHandler.class.getName(), ConfigReader.configFile);
        getInfo().setMustDeregisterConfig(true);
        configHandlerUpdated = true;

        performTask();
        printTaskDone();
        refreshProgressBar();
      }
      finally
      {
        if (configHandlerUpdated)
        {
          DirectoryServer.getInstance().initializeConfiguration(ConfigReader.configClassName, ConfigReader.configFile);
          getInfo().startPooling();
        }
      }
    }

    private void printCreateNewBackendProgress(final String backendName) throws OpenDsException
    {
      SwingUtilities.invokeLater(new Runnable()
      {
        @Override
        public void run()
        {
          LocalizableMessage message = INFO_CTRL_PANEL_CREATING_BACKEND_PROGRESS.get(backendName, newBaseDN);
          getProgressDialog().appendProgressHtml(
              Utilities.getProgressWithPoints(message, ColorAndFontConstants.progressFont));
        }
      });
    }

    private void performTask() throws OpenDsException
    {
      final String backendName = getBackendName();
      if (isNewBackend())
      {
        printCreateNewBackendProgress(backendName);
        createBackend(backendName);
      }
      else
      {
        printCreateNewBaseDNProgress(backendName);
        addNewBaseDN(backendName);
      }
    }

    private void createBackend(String backendName) throws OpenDsException
    {
      if (!isServerRunning())
      {
        createBackendOffline(backendName);
        return;
      }

      //FIXME GB This could be replaced by a call to BackendCreationHelper.createBackend(...)
      // once the new configuration framework migration will be done
      final RootCfgClient root = getRootConfigurationClient();
      final BackendCfgClient backend =
          root.createBackend(getSelectedBackendType().getLegacyConfigurationFrameworkBackend(), backendName, null);
      backend.setEnabled(true);
      backend.setBaseDN(Collections.singleton(DN.valueOf(newBaseDN)));
      backend.setBackendId(backendName);
      backend.setWritabilityMode(BackendCfgDefn.WritabilityMode.ENABLED);
      backend.commit();
    }

    private void createBackendOffline(String backendName) throws OpenDsException
    {
      try
      {
        Set<org.forgerock.opendj.ldap.DN> baseDN = Collections.singleton(Converters.from(DN.valueOf(newBaseDN)));
        BackendCreationHelper.createBackendOffline(backendName, baseDN, getSelectedBackendType().getBackend());
      }
      catch (Exception e)
      {
        throw new OfflineUpdateException(ERROR_CTRL_PANEL_CREATE_NEW_BACKEND.get(backendName, e.getMessage()), e);
      }
    }

    private RootCfgClient getRootConfigurationClient()
    {
      final JNDIDirContextAdaptor jndiContext = JNDIDirContextAdaptor.adapt(getInfo().getDirContext());
      return LDAPManagementContext.createFromContext(jndiContext).getRootConfiguration();
    }

    private void addNewBaseDN(String backendName) throws OpenDsException
    {
      if (!isServerRunning())
      {
        addNewBaseDNOffline(backendName);
        return;
      }

      final BackendCfgClient backend = getRootConfigurationClient().getBackend(backendName);
      final Set<DN> baseDNs = backend.getBaseDN();
      baseDNs.add(DN.valueOf(newBaseDN));
      backend.setBaseDN(baseDNs);
      backend.commit();
    }

    private void addNewBaseDNOffline(String backendName) throws OpenDsException
    {
      try
      {
        getInfo().initializeConfigurationFramework();
        final File config = Installation.getLocal().getCurrentConfigurationFile();
        final LDAPProfile profile = LDAPProfile.getInstance();
        try (org.forgerock.opendj.config.client.ManagementContext context =
            org.forgerock.opendj.config.client.ldap.LDAPManagementContext.newLDIFManagementContext(config, profile))
        {
          final org.forgerock.opendj.server.config.client.BackendCfgClient backend =
              context.getRootConfiguration().getBackend(backendName);
          final SortedSet<org.forgerock.opendj.ldap.DN> baseDNs = backend.getBaseDN();
          baseDNs.add(org.forgerock.opendj.ldap.DN.valueOf(newBaseDN));
          backend.setBaseDN(baseDNs);
          backend.commit();
        }
      }
      catch (Exception e)
      {
        throw new OfflineUpdateException(LocalizableMessage.raw(e.getMessage()), e);
      }
    }

    private void createAdditionalIndexes() throws OpenDsException
    {
      final String backendName = getBackendName();
      displayCreateAdditionalIndexesDsConfigCmdLine();
      final RootCfgClient root = getRootConfigurationClient();
      if (isLocalDBBackend())
      {
        addJEDefaultIndexes((LocalDBBackendCfgClient) root.getBackend(backendName));
      }
      else
      {
        addBackendDefaultIndexes((PluggableBackendCfgClient) root.getBackend(backendName));
      }
      displayCreateAdditionalIndexesDone();
    }

    @RemoveOnceLocalDBBackendIsPluggable
    private void addJEDefaultIndexes(final LocalDBBackendCfgClient jeBackendCfgClient) throws AdminException
    {
      for (DefaultIndex defaultIndex : BackendCreationHelper.DEFAULT_INDEXES)
      {
        final LocalDBIndexCfgClient jeIndex =
            jeBackendCfgClient.createLocalDBIndex(LocalDBIndexCfgDefn.getInstance(), defaultIndex.getName(), null);

        final List<LocalDBIndexCfgDefn.IndexType> indexTypes = new LinkedList<>();
        indexTypes.add(LocalDBIndexCfgDefn.IndexType.EQUALITY);
        if (defaultIndex.shouldCreateSubstringIndex())
        {
          indexTypes.add(LocalDBIndexCfgDefn.IndexType.SUBSTRING);
        }
        jeIndex.setIndexType(indexTypes);
        jeIndex.commit();
      }
    }

    private void addBackendDefaultIndexes(PluggableBackendCfgClient backendCfgClient) throws AdminException
    {
      for (DefaultIndex defaultIndex : BackendCreationHelper.DEFAULT_INDEXES)
      {
        final BackendIndexCfgClient index = backendCfgClient.createBackendIndex(
            BackendIndexCfgDefn.getInstance(), defaultIndex.getName(), null);

        final List<IndexType> indexTypes = new LinkedList<>();
        indexTypes.add(IndexType.EQUALITY);
        if (defaultIndex.shouldCreateSubstringIndex())
        {
          indexTypes.add(IndexType.SUBSTRING);
        }
        index.setIndexType(indexTypes);
        index.commit();
      }
    }

    private void printCreateNewBaseDNProgress(final String backendName) throws OpenDsException
    {
      SwingUtilities.invokeLater(new Runnable()
      {
        @Override
        public void run()
        {
          LocalizableMessage message = INFO_CTRL_PANEL_CREATING_BASE_DN_PROGRESS.get(newBaseDN, backendName);
          getProgressDialog().appendProgressHtml(
              Utilities.getProgressWithPoints(message, ColorAndFontConstants.progressFont));
        }
      });
    }

    private void printTaskDone()
    {
      SwingUtilities.invokeLater(new Runnable()
      {
        @Override
        public void run()
        {
          getProgressDialog().appendProgressHtml(
              Utilities.getProgressDone(ColorAndFontConstants.progressFont) + "<br><br>");
        }
      });
    }

    private void refreshProgressBar()
    {
      if (progressAfterConfigurationUpdate > 0)
      {
        SwingUtilities.invokeLater(new Runnable()
        {
          @Override
          public void run()
          {
            getProgressDialog().getProgressBar().setIndeterminate(false);
            getProgressDialog().getProgressBar().setValue(progressAfterConfigurationUpdate);
          }
        });
      }
    }

    private void displayCreateAdditionalIndexesDsConfigCmdLine()
    {
      final List<List<String>> argsArray = new ArrayList<>();
      for (DefaultIndex defaultIndex : BackendCreationHelper.DEFAULT_INDEXES)
      {
        argsArray.add(getCreateIndexCommandLineArguments(defaultIndex));
      }

      final StringBuilder sb = new StringBuilder();
      for (List<String> args : argsArray)
      {
        sb.append(getEquivalentCommandLine(getCommandLinePath("dsconfig"), getObfuscatedCommandLineArguments(args)));
        sb.append("<br><br>");
      }

      SwingUtilities.invokeLater(new Runnable()
      {
        @Override
        public void run()
        {
          getProgressDialog().appendProgressHtml(Utilities.applyFont(
              INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_CREATE_ADDITIONAL_INDEXES.get()
              + "<br><br><b>" + sb + "</b>", ColorAndFontConstants.progressFont));
          getProgressDialog().appendProgressHtml(Utilities.getProgressWithPoints(
              INFO_CTRL_PANEL_CREATING_ADDITIONAL_INDEXES_PROGRESS.get(), ColorAndFontConstants.progressFont));
        }
      });
    }

    private List<String> getCreateIndexCommandLineArguments(final DefaultIndex defaultIndex)
    {
      final List<String> args = new ArrayList<>();
      args.add(isLocalDBBackend() ? "create-local-db-index" : "create-backend-index");
      args.add("--backend-name");
      args.add(getBackendName());
      args.add("--type");
      args.add("generic");
      args.add("--index-name");
      args.add(defaultIndex.getName());
      args.add("--set");
      args.add("index-type:" + IndexTypeDescriptor.EQUALITY.toBackendIndexType());
      if (defaultIndex.shouldCreateSubstringIndex())
      {
        args.add("--set");
        args.add("index-type:" + IndexTypeDescriptor.SUBSTRING.toBackendIndexType());
      }
      args.addAll(getConnectionCommandLineArguments());
      args.add(getNoPropertiesFileArgument());
      args.add("--no-prompt");

      return args;
    }

    private void displayCreateAdditionalIndexesDone()
    {
      SwingUtilities.invokeLater(new Runnable()
      {
        @Override
        public void run()
        {
          getProgressDialog().appendProgressHtml(
              Utilities.getProgressDone(ColorAndFontConstants.progressFont) + "<br><br>");
        }
      });
    }

    @RemoveOnceLocalDBBackendIsPluggable
    private boolean isLocalDBBackend()
    {
      return getSelectedBackendType().getBackend()
          instanceof org.forgerock.opendj.server.config.meta.LocalDBBackendCfgDefn;
    }

    /**
     * Creates the data in the new base DN.
     *
     * @throws OpenDsException
     *           if there is an error importing contents.
     * @throws IOException
     *           if there is an err
     */
    private void updateData() throws OpenDsException, IOException
    {
      final boolean leaveEmpty = leaveDatabaseEmpty.isSelected();
      final boolean createBaseEntry = onlyCreateBaseEntry.isSelected();
      final boolean importLDIF = importDataFromLDIF.isSelected();
      final boolean generateData = !leaveEmpty && !createBaseEntry && !importLDIF;
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
            @Override
            public void run()
            {
              progressDialog.appendProgressHtml(Utilities.applyFont("Equivalent command line:<br><b>" + cmdLine
                  + "</b><br><br>", ColorAndFontConstants.progressFont));
            }
          });
        }
        else if (createBaseEntry)
        {
          SwingUtilities.invokeLater(new Runnable()
          {
            @Override
            public void run()
            {
              progressDialog.appendProgressHtml(Utilities.getProgressWithPoints(
                  INFO_PROGRESS_CREATING_BASE_ENTRY.get(newBaseDN), ColorAndFontConstants.progressFont));
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
            @Override
            public void run()
            {
              if (isLocal())
              {
                progressDialog.appendProgressHtml(Utilities.applyFont(
                    INFO_PROGRESS_IMPORT_AUTOMATICALLY_GENERATED.get(nEntries).toString(),
                    ColorAndFontConstants.progressFont) + "<br>");
              }
              else
              {
                getProgressDialog().appendProgressHtml(Utilities.getProgressWithPoints(
                    INFO_PROGRESS_IMPORT_AUTOMATICALLY_GENERATED_REMOTE.get(nEntries),
                    ColorAndFontConstants.progressFont));
              }
            }
          });

          File f = SetupUtils.createTemplateFile(newBaseDN, Integer.parseInt(nEntries));
          if (!isLocal())
          {
            File tempFile = File.createTempFile("opendj-control-panel", ".ldif");
            tempFile.deleteOnExit();
            ldifFile = tempFile.getAbsolutePath();

            // Create the LDIF file locally using make-ldif
            List<String> makeLDIFArgs = new ArrayList<>();
            makeLDIFArgs.add("--templateFile");
            makeLDIFArgs.add(f.getAbsolutePath());
            makeLDIFArgs.add("--ldifFile");
            makeLDIFArgs.add(ldifFile);
            makeLDIFArgs.add("--randomSeed");
            makeLDIFArgs.add("0");
            makeLDIFArgs.add("--resourcePath");

            File makeLDIFPath = new File(Installation.getLocal().getConfigurationDirectory(), "MakeLDIF");
            makeLDIFArgs.add(makeLDIFPath.getAbsolutePath());
            makeLDIFArgs.addAll(getConfigCommandLineArguments());

            MakeLDIF makeLDIF = new MakeLDIF();
            String[] array = new String[makeLDIFArgs.size()];
            makeLDIFArgs.toArray(array);
            returnCode = makeLDIF.makeLDIFMain(array, false, false, outPrintStream, errorPrintStream);
            f.delete();

            if (returnCode != 0)
            {
              throw new OnlineUpdateException(ERR_CTRL_PANEL_ERROR_CREATING_NEW_DATA_LDIF.get(returnCode), null);
            }
          }
          else
          {
            ldifFile = f.getAbsolutePath();
          }
        }

        List<String> arguments = getDataCommandLineArguments(ldifFile, generateData);
        String[] args = new String[arguments.size()];
        arguments.toArray(args);
        if (createBaseEntry || !isLocal())
        {
          outPrintStream.setNotifyListeners(false);
          errorPrintStream.setNotifyListeners(false);
        }
        try
        {
          if (isServerRunning())
          {
            if (isLocal() || importLDIF)
            {
              returnCode = ImportLDIF.mainImportLDIF(args, false, outPrintStream, errorPrintStream);
            }
            else
            {
              returnCode = LDAPModify.mainModify(args, false, outPrintStream, errorPrintStream);
            }
          }
          else
          {
            returnCode = executeCommandLine(getDataCommandLineName(), args);
          }
        }
        finally
        {
          outPrintStream.setNotifyListeners(true);
          errorPrintStream.setNotifyListeners(true);
        }

        if (returnCode != 0)
        {
          state = State.FINISHED_WITH_ERROR;
        }
        else
        {
          if (createBaseEntry || (!isLocal() && generateData))
          {
            SwingUtilities.invokeLater(new Runnable()
            {
              @Override
              public void run()
              {
                progressDialog.appendProgressHtml(Utilities.getProgressDone(ColorAndFontConstants.progressFont));
              }
            });
          }
          state = State.FINISHED_SUCCESSFULLY;
        }
      }
    }

    /** {@inheritDoc} */
    @Override
    protected String getCommandLinePath()
    {
      return null;
    }

    /** {@inheritDoc} */
    @Override
    protected List<String> getCommandLineArguments()
    {
      return new ArrayList<>();
    }

    private String getConfigCommandLineFullPath()
    {
      return isServerRunning() ? getCommandLinePath("dsconfig") : null;
    }

    private List<String> getDSConfigCommandLineArguments()
    {
      List<String> args = new ArrayList<>();
      if (isServerRunning())
      {
        if (isNewBackend())
        {
          args.add("create-backend");
          args.add("--backend-name");
          args.add(getBackendName());
          args.add("--set");
          args.add("base-dn:" + newBaseDN);
          args.add("--set");
          args.add("enabled:true");
          args.add("--type");
          args.add(BackendTypeHelper.filterSchemaBackendName(getSelectedBackendType().getBackend().getName()));
        }
        else
        {
          args.add("set-backend-prop");
          args.add("--backend-name");
          args.add(getBackendName());
          args.add("--add");
          args.add("base-dn:" + newBaseDN);
        }
        args.addAll(getConnectionCommandLineArguments());
        args.add(getNoPropertiesFileArgument());
        args.add("--no-prompt");
      }
      return args;
    }

    /** {@inheritDoc} */
    @Override
    public void runTask()
    {
      state = State.RUNNING;
      lastException = null;

      try
      {
        if (isServerRunning())
        {
          updateConfigurationOnline();
        }
        else
        {
          updateConfigurationOffline();
        }
        updateData();
      }
      catch (Throwable t)
      {
        lastException = t;
        state = State.FINISHED_WITH_ERROR;
      }
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> getBackends()
    {
      return backendSet;
    }
  }
}
