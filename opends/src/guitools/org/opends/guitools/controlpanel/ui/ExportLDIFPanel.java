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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import static org.opends.messages.QuickSetupMessages.INFO_NO_LDIF_PATH;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.event.BrowseActionListener;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.tools.ExportLDIF;

/**
 * The panel where the user can export the contents of the server to an LDIF
 * file.
 *
 */
public class ExportLDIFPanel extends InclusionExclusionPanel
{
 private static final long serialVersionUID = 2256902594454214644L;
  private JComboBox backends;
  private JTextField file;
  private JCheckBox overwrite;
  private JCheckBox compressData;
  private JCheckBox encryptData;
  private JCheckBox generateSignedHash;
  private JCheckBox wrapText;
  private JTextField wrapColumn;

  private JLabel lBackend;
  private JLabel lNoBackendsFound;
  private JLabel lFile;
  private JLabel lExportOptions;
  private JCheckBox excludeOperationalAttrs;

  private DocumentListener documentListener;

  /**
   * Default constructor.
   *
   */
  public ExportLDIFPanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_EXPORT_LDIF_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return file;
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
    gbc.gridwidth = 4;
    addErrorPane(gbc);

    gbc.anchor = GridBagConstraints.WEST;
    gbc.weightx = 0.0;
    gbc.gridy ++;
    gbc.gridwidth = 1;
    gbc.fill = GridBagConstraints.NONE;
    lBackend = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_BACKEND_LABEL.get());
    add(lBackend, gbc);
    gbc.insets.left = 10;
    gbc.gridx = 1;
    backends = Utilities.createComboBox();
    backends.setModel(new DefaultComboBoxModel(new String[]{}));
    gbc.gridwidth = 3;
    add(backends, gbc);
    lNoBackendsFound = Utilities.createDefaultLabel(
        INFO_CTRL_PANEL_NO_BACKENDS_FOUND_LABEL.get());
    add(lNoBackendsFound, gbc);
    lNoBackendsFound.setVisible(false);
    gbc.insets.top = 10;

    gbc.gridx = 0;
    gbc.gridy ++;
    gbc.insets.left = 0;
    gbc.gridwidth = 1;
    lFile = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_EXPORT_TO_FILE_LABEL.get());
    add(lFile, gbc);

    gbc.gridx = 1;
    gbc.insets.left = 10;
    gbc.gridwidth = 2;
    file = Utilities.createTextField();
    documentListener = new DocumentListener()
    {
      /**
       * {@inheritDoc}
       */
      public void changedUpdate(DocumentEvent ev)
      {
        String text = file.getText().trim();
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
    file.getDocument().addDocumentListener(documentListener);
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(file, gbc);
    JButton bBrowse = Utilities.createButton(
        INFO_CTRL_PANEL_BROWSE_BUTTON_LABEL.get());
    bBrowse.addActionListener(
        new BrowseActionListener(file,
            BrowseActionListener.BrowseType.CREATE_LDIF_FILE,  this));
    gbc.gridx = 3;
    gbc.gridwidth = 1;
    gbc.weightx = 0.0;
    bBrowse.setOpaque(false);
    add(bBrowse, gbc);
    gbc.gridx = 1;
    gbc.gridy ++;
    gbc.insets.left = 30;
    gbc.insets.top = 5;
    gbc.gridwidth = 3;
    overwrite =
      Utilities.createCheckBox(INFO_CTRL_PANEL_EXPORT_OVERWRITE_LABEL.get());
    overwrite.setOpaque(false);
    add(overwrite, gbc);

    gbc.gridx = 0;
    gbc.gridy ++;
    gbc.insets.left = 0;
    gbc.insets.top = 10;
    gbc.gridwidth = 1;
    lExportOptions =
      Utilities.createPrimaryLabel(INFO_CTRL_PANEL_EXPORT_OPTIONS.get());
    add(lExportOptions, gbc);

    compressData = Utilities.createCheckBox(
        INFO_CTRL_PANEL_COMPRESS_DATA_LABEL.get());
    compressData.setSelected(false);

    gbc.insets.left = 10;
    gbc.gridx = 1;
    gbc.gridwidth = 3;
    add(compressData, gbc);

    encryptData = Utilities.createCheckBox(
        INFO_CTRL_PANEL_ENCRYPT_DATA_LABEL.get());

    /*
    gbc.gridy ++;
    gbc.insets.top = 5;
    add(encryptData, gbc);
*/
    generateSignedHash = Utilities.createCheckBox(
        INFO_CTRL_PANEL_EXPORT_GENERATE_SIGNED_HASH.get());

    encryptData.addChangeListener(new ChangeListener()
    {
      /**
       * {@inheritDoc}
       */
      public void stateChanged(ChangeEvent ev)
      {
        generateSignedHash.setEnabled(encryptData.isSelected());
      }
    });
    encryptData.setSelected(false);
    generateSignedHash.setEnabled(false);

    /*
    gbc.gridy ++;
    gbc.insets.left = 30;
    add(generateSignedHash, gbc);
*/
    wrapText = Utilities.createCheckBox(INFO_CTRL_PANEL_EXPORT_WRAP_TEXT.get());
    wrapText.setOpaque(false);
    gbc.insets.left = 10;
    gbc.insets.top = 10;
    gbc.gridy ++;
    gbc.gridwidth = 1;
    add(wrapText, gbc);

    gbc.insets.left = 5;
    gbc.gridx = 2;
    wrapColumn = Utilities.createTextField("80", 4);
    gbc.fill = GridBagConstraints.NONE;
    add(wrapColumn, gbc);
    gbc.fill = GridBagConstraints.HORIZONTAL;

    wrapText.addChangeListener(new ChangeListener()
    {
      /**
       * {@inheritDoc}
       */
      public void stateChanged(ChangeEvent ev)
      {
        wrapColumn.setEnabled(wrapText.isSelected());
      }
    });
    wrapColumn.setEnabled(false);
    wrapText.setSelected(false);

    gbc.insets.top = 10;
    gbc.insets.left = 0;
    gbc.gridy ++;
    gbc.gridx = 0;
    gbc.gridwidth = 4;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    excludeOperationalAttrs = Utilities.createCheckBox(
        INFO_CTRL_PANEL_EXCLUDE_OPERATIONAL_ATTRIBUTES.get());
    excludeOperationalAttrs.setOpaque(false);
    add(createDataExclusionOptions(new JLabel[]{null},
        new Component[]{excludeOperationalAttrs}), gbc);
    gbc.gridy ++;
    gbc.insets.top = 15;
    add(createDataInclusionOptions(new JLabel[]{}, new Component[]{}), gbc);
    addBottomGlue(gbc);
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    updateSimpleBackendComboBoxModel(backends, lNoBackendsFound,
        ev.getNewDescriptor());

    updateErrorPaneAndOKButtonIfAuthRequired(getInfo().getServerDescriptor(),
        INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_FOR_EXPORT.get());
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
    setPrimaryValid(lFile);
    setPrimaryValid(lExportOptions);
    final LinkedHashSet<Message> errors = new LinkedHashSet<Message>();

    String backendName = (String)backends.getSelectedItem();
    if (backendName == null)
    {
      errors.add(ERR_CTRL_PANEL_NO_BACKEND_SELECTED.get());
      setPrimaryInvalid(lBackend);
    }

    String ldifPath = file.getText();
    if ((ldifPath == null) || (ldifPath.trim().equals("")))
    {
      errors.add(INFO_NO_LDIF_PATH.get());
      setPrimaryInvalid(lFile);
    }
    else
    {
      File f = new File(ldifPath);
      if (f.isDirectory())
      {
        errors.add(ERR_CTRL_PANEL_EXPORT_DIRECTORY_PROVIDED.get(ldifPath));
      }
    }

    if (wrapText.isSelected())
    {
      String cols = wrapColumn.getText();
      int minValue = 1;
      int maxValue = 1000;
      Message errMsg = ERR_CTRL_PANEL_INVALID_WRAP_COLUMN.get(minValue,
      maxValue);
      checkIntValue(errors, cols, minValue, maxValue, errMsg);
    }

    updateIncludeExclude(errors, backendName);

    if (errors.isEmpty())
    {
      ProgressDialog progressDialog = new ProgressDialog(
          Utilities.getParentDialog(this), getTitle(), getInfo());
      ExportTask newTask = new ExportTask(getInfo(), progressDialog);
      for (Task task : getInfo().getTasks())
      {
        task.canLaunch(newTask, errors);
      }
      boolean confirmed = true;
      if (errors.isEmpty())
      {
        File f = new File(ldifPath);
        if (overwrite.isSelected() && f.exists())
        {
          confirmed = displayConfirmationDialog(
              INFO_CTRL_PANEL_CONFIRMATION_REQUIRED_SUMMARY.get(),
              INFO_CTRL_PANEL_CONFIRMATION_EXPORT_LDIF_DETAILS.get(ldifPath));
        }
      }
      if ((errors.isEmpty()) && confirmed)
      {
        launchOperation(newTask,
            INFO_CTRL_PANEL_EXPORTING_LDIF_SUMMARY.get(
                backends.getSelectedItem().toString()),
            INFO_CTRL_PANEL_EXPORTING_LDIF_SUCCESSFUL_SUMMARY.get(),
            INFO_CTRL_PANEL_EXPORTING_LDIF_SUCCESSFUL_DETAILS.get(),
            ERR_CTRL_PANEL_EXPORTING_LDIF_ERROR_SUMMARY.get(),
            null,
            ERR_CTRL_PANEL_EXPORTING_LDIF_ERROR_DETAILS,
            progressDialog);
        progressDialog.setVisible(true);
        Utilities.getParentDialog(this).setVisible(false);
      }
    }
    if (errors.size() > 0)
    {
      displayErrorDialog(errors);
    }
  }

  /**
   * {@inheritDoc}
   */
  public void cancelClicked()
  {
    setPrimaryValid(lBackend);
    setPrimaryValid(lFile);
    setPrimaryValid(lExportOptions);
    super.cancelClicked();
  }

  /**
   * The class that performs the export.
   *
   */
  protected class ExportTask extends InclusionExclusionTask
  {
    private Set<String> backendSet;
    private String fileName;
    /**
     * The constructor of the task.
     * @param info the control panel info.
     * @param dlg the progress dialog that shows the progress of the task.
     */
    public ExportTask(ControlPanelInfo info, ProgressDialog dlg)
    {
      super(info, dlg);
      backendSet = new HashSet<String>();
      backendSet.add((String)backends.getSelectedItem());
      fileName = file.getText();
    }

    /**
     * {@inheritDoc}
     */
    public Type getType()
    {
      return Type.EXPORT_LDIF;
    }

    /**
     * {@inheritDoc}
     */
    public Message getTaskDescription()
    {
      return INFO_CTRL_PANEL_EXPORT_TASK_DESCRIPTION.get(
          backendSet.iterator().next(), fileName);
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
     * {@inheritDoc}
     */
    public void runTask()
    {
      state = State.RUNNING;
      lastException = null;
      try
      {
        ArrayList<String> arguments = getCommandLineArguments();

        String[] args = new String[arguments.size()];

        arguments.toArray(args);
        if (isServerRunning())
        {
          returnCode = ExportLDIF.mainExportLDIF(args, false, outPrintStream,
              errorPrintStream);
        }
        else
        {
          returnCode = executeCommandLine(getCommandLinePath(), args);
        }
        if (returnCode != 0)
        {
          state = State.FINISHED_WITH_ERROR;
        }
        else
        {
          state = State.FINISHED_SUCCESSFULLY;
        }
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

    /**
     * {@inheritDoc}
     */
    protected ArrayList<String> getCommandLineArguments()
    {
      ArrayList<String> args = new ArrayList<String>();
      args.add("--ldifFile");
      args.add(fileName);
      args.add("--backendID");
      args.add(backendSet.iterator().next());

      if (!overwrite.isSelected())
      {
        args.add("--appendToLDIF");
      }

      if (compressData.isSelected())
      {
        args.add("--compress");
      }
      if (wrapText.isSelected())
      {
        args.add("--wrapColumn");
        args.add(wrapColumn.getText().trim());
      }
      if (excludeOperationalAttrs.isSelected())
      {
        args.add("--excludeOperational");
      }

      args.addAll(super.getCommandLineArguments());

      if (isServerRunning())
      {
        args.addAll(getConfigCommandLineArguments());
      }
      args.add(getNoPropertiesFileArgument());

      return args;
    }

    /**
     * {@inheritDoc}
     */
    protected String getCommandLinePath()
    {
      return getCommandLinePath("export-ldif");
    }
  };
}
