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

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.SortableTableModel;
import org.opends.guitools.controlpanel.event.BrowseActionListener;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.ui.components.LabelWithHelpIcon;
import org.opends.guitools.controlpanel.ui.renderer.AttributeCellEditor;
import org.opends.guitools.controlpanel.ui.renderer.LDAPEntryTableCellRenderer;
import org.opends.guitools.controlpanel.util.BackgroundTask;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.util.Utils;
import org.opends.server.tools.JavaPropertiesTool;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.SetupUtils;

/**
 * The panel where the user can specify the java arguments and java home to be
 * used in the command-lines.
 *
 */
public class JavaPropertiesPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = -7886215660289880597L;
  private JTextField javaHome;
  private JRadioButton useOpenDSJavaHome;
  private JRadioButton useSpecifiedJavaHome;
  private JButton browse;
  private JLabel lJavaHome;

  private JRadioButton useOpenDSJavaArgs;
  private JRadioButton useSpecifiedJavaArgs;
  private JLabel lJavaArgs;
  private JTable argumentsTable;
  private JavaArgumentsTableModel argumentsTableModel;
  private JScrollPane argumentsScroll;

  private AttributeCellEditor editor;

  private JLabel lInitContents;

  private JCheckBox showAll;

  private Set<JavaArgumentsDescriptor> readJavaArguments =
    new HashSet<JavaArgumentsDescriptor>();

  private Set<JavaArgumentsDescriptor> currentJavaArguments =
    new HashSet<JavaArgumentsDescriptor>();

  private Set<String> allScriptNames =
    new HashSet<String>();
  {
    String[] names =
    {
        "start-ds", "import-ldif.offline", "backup.online", "base64",
        "create-rc-script", "dsconfig", "dsreplication", "dsframework",
        "export-ldif.online", "import-ldif.online", "ldapcompare",
        "ldapdelete", "ldapmodify", "ldappasswordmodify", "ldapsearch",
        "list-backends", "manage-account", "manage-tasks", "restore.online",
        "stop-ds", "status", "control-panel", "uninstall", "setup",
        "backup.offline", "encode-password", "export-ldif.offline",
        "ldif-diff", "ldifmodify", "ldifsearch", "make-ldif",
        "rebuild-index", "restore.offline", "upgrade",
        "verify-index", "dbtest"
    };
    for (String name : names)
    {
      allScriptNames.add(name);
    }
  }

  private Set<String> relevantScriptNames =
    new HashSet<String>();

  {
    String[] relevantNames =
    {
        "start-ds", "import-ldif.offline", "backup.offline",
        "export-ldif.offline",
        "ldif-diff", "make-ldif", "rebuild-index", "restore.offline",
        "verify-index", "dbtest"
    };
    for (String name : relevantNames)
    {
      relevantScriptNames.add(name);
    }
  }

  private String readJavaHome;
  private boolean readUseOpenDSJavaHome;
  private boolean readUseOpenDSJavaArgs;

  private boolean firstDisplay = true;

  private Message READING_JAVA_SETTINGS =
    INFO_CTRL_PANEL_READING_JAVA_SETTINGS_SUMMARY.get();

  JComponent[] comps;

  /**
   * Default constructor.
   *
   */
  public JavaPropertiesPanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_JAVA_PROPERTIES_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Component getPreferredFocusComponent()
  {
    return javaHome;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean requiresScroll()
  {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setInfo(ControlPanelInfo info)
  {
    super.setInfo(info);
    if (editor != null)
    {
      editor.setInfo(info);
    }
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();

    lJavaHome = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_JAVA_HOME_LABEL.get());
    useOpenDSJavaHome = Utilities.createRadioButton(Message.EMPTY);
    useOpenDSJavaHome.setOpaque(false);
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 0.0;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.gridwidth = 1;
    add(lJavaHome, gbc);
    gbc.insets.left = 10;
    gbc.gridx ++;
    add(useOpenDSJavaHome, gbc);
    gbc.gridwidth = 3;
    gbc.gridx ++;
    LabelWithHelpIcon useOpenDSJavaHomeLabel =
      new LabelWithHelpIcon(INFO_CTRL_PANEL_USE_OPENDS_JAVA_HOME.get(),
          INFO_CTRL_PANEL_USE_OPENDS_JAVA_HOME_HELP.get());
    gbc.insets.left = 0;
    add(useOpenDSJavaHomeLabel, gbc);


    gbc.gridx = 1;
    gbc.gridy ++;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 0.0;
    gbc.insets.top = 10;
    gbc.gridwidth = 1;
    useSpecifiedJavaHome = Utilities.createRadioButton(Message.EMPTY);
    useSpecifiedJavaHome.setOpaque(false);
    LabelWithHelpIcon useSpecifiedJavaHomeLabel = new LabelWithHelpIcon(
        INFO_CTRL_PANEL_USE_SPECIFIED_OPENDS_JAVA_HOME.get(),
        INFO_CTRL_PANEL_USE_SPECIFIED_OPENDS_JAVA_HOME_HELP.get());
    gbc.insets.left = 10;
    add(useSpecifiedJavaHome, gbc);
    gbc.gridx ++;
    gbc.insets.left = 0;
    add(useSpecifiedJavaHomeLabel, gbc);
    gbc.gridx ++;
    javaHome = Utilities.createTextField();
    gbc.weightx = 1.0;
    gbc.insets.left = 5;
    add(javaHome, gbc);
    gbc.weightx = 0.0;
    browse = Utilities.createButton(INFO_CTRL_PANEL_BROWSE_BUTTON_LABEL.get());
    browse.addActionListener(
        new BrowseActionListener(javaHome,
            BrowseActionListener.BrowseType.LOCATION_DIRECTORY,  this));
    browse.setOpaque(false);
    gbc.gridx ++;
    add(browse, gbc);

    ButtonGroup group = new ButtonGroup();
    group.add(useSpecifiedJavaHome);
    group.add(useOpenDSJavaHome);

    gbc.insets.top = 10;
    gbc.insets.left = 0;
    gbc.gridx = 0;
    gbc.gridwidth = 5;
    gbc.gridy ++;
    add(new JSeparator(), gbc);

    gbc.gridy ++;
    JPanel p = new JPanel(new GridBagLayout());
    p.setOpaque(false);
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    add(p, gbc);

    gbc.insets.top = 10;
    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    gbc.gridx = 0;
    gbc.gridy = 0;

    lJavaArgs = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_JAVA_ARGUMENTS_LABEL.get());
    useSpecifiedJavaArgs = Utilities.createRadioButton(Message.EMPTY);
    useSpecifiedJavaArgs.setOpaque(false);
    useOpenDSJavaArgs = Utilities.createRadioButton(Message.EMPTY);
    useOpenDSJavaArgs.setOpaque(false);
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 0.0;
    gbc.insets.top = 10;
    p.add(lJavaArgs, gbc);
    gbc.insets.left = 10;
    gbc.gridx ++;
    gbc.gridwidth = 1;
    p.add(useOpenDSJavaArgs, gbc);
    gbc.gridx ++;
    LabelWithHelpIcon useOpenDSJavaArgsLabel = new LabelWithHelpIcon(
        INFO_CTRL_PANEL_USE_OPENDS_JAVA_ARGS.get(),
        INFO_CTRL_PANEL_USE_OPENDS_JAVA_ARGS_HELP.get());
    gbc.insets.left = 0;
    p.add(useOpenDSJavaArgsLabel, gbc);


    gbc.gridx = 1;
    gbc.gridy ++;
    gbc.insets.top = 10;
    gbc.insets.left = 10;
    p.add(useSpecifiedJavaArgs, gbc);
    gbc.gridx ++;
    LabelWithHelpIcon useSpecifiedJavaArgsLabel = new LabelWithHelpIcon(
        INFO_CTRL_PANEL_USE_SPECIFIED_OPENDS_JAVA_ARGS.get(),
        INFO_CTRL_PANEL_USE_SPECIFIED_OPENDS_JAVA_ARGS_HELP.get());
    gbc.insets.left = 0;
    p.add(useSpecifiedJavaArgsLabel, gbc);

    group = new ButtonGroup();
    group.add(useSpecifiedJavaArgs);
    group.add(useOpenDSJavaArgs);

    argumentsTableModel = new JavaArgumentsTableModel();
    LDAPEntryTableCellRenderer renderer = new LDAPEntryTableCellRenderer();
    argumentsTable = Utilities.createSortableTable(argumentsTableModel,
        renderer);
    editor = new AttributeCellEditor();
    if (getInfo() != null)
    {
      editor.setInfo(getInfo());
    }
    argumentsTable.getColumnModel().getColumn(1).setCellEditor(editor);
    renderer.setTable(argumentsTable);

    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.gridx = 1;
    gbc.insets.top = 10;
    gbc.gridy ++;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridwidth = 2;
    argumentsScroll = Utilities.createScrollPane(argumentsTable);
    p.add(argumentsScroll, gbc);
    lInitContents = Utilities.createDefaultLabel(READING_JAVA_SETTINGS);
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.CENTER;
    p.add(lInitContents, gbc);
    lInitContents.setVisible(false);
    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1.0;
    gbc.anchor = GridBagConstraints.WEST;

    showAll =
      Utilities.createCheckBox(INFO_CTRL_PANEL_DISPLAY_ALL_COMMAND_LINES.get());
    showAll.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        editor.stopCellEditing();
        currentJavaArguments = getCurrentJavaArguments();
        argumentsTableModel.setData(filterJavaArguments(currentJavaArguments));
        Utilities.updateTableSizes(argumentsTable, 7);
      }
    });

    gbc.gridy ++;
    gbc.insets.top = 5;
    p.add(showAll, gbc);

    JLabel inlineHelp = Utilities.createInlineHelpLabel(
        INFO_CTRL_PANEL_ONLINE_COMMAND_HELP.get());
    gbc.insets.top = 3;
    gbc.gridy ++;
    p.add(inlineHelp, gbc);

    inlineHelp = Utilities.createInlineHelpLabel(
        INFO_CTRL_PANEL_OFFLINE_COMMAND_HELP.get());
    gbc.gridy ++;
    p.add(inlineHelp, gbc);

    // Just to create space.
    Set<JavaArgumentsDescriptor> fakeArguments =
      new HashSet<JavaArgumentsDescriptor>();
    fakeArguments.add(
        new JavaArgumentsDescriptor("start-ds", "-server -XM256j"));
    fakeArguments.add(
        new JavaArgumentsDescriptor("stop-ds", "-client"));
    fakeArguments.add(
        new JavaArgumentsDescriptor("import-ds.online", "-server"));
    fakeArguments.add(
        new JavaArgumentsDescriptor("import-ds.offline", "-server"));
    fakeArguments.add(
        new JavaArgumentsDescriptor("export-ds.online", "-server"));

    argumentsTableModel.setData(fakeArguments);
    Utilities.updateTableSizes(argumentsTable, 7);

    comps = new JComponent[] {
        javaHome, useOpenDSJavaHome, useSpecifiedJavaHome, browse,
        useOpenDSJavaArgs, useSpecifiedJavaArgs
    };
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void toBeDisplayed(boolean visible)
  {
    if (visible && (firstDisplay || !updatedByUser()))
    {
      firstDisplay = false;
      initContents();
    }
  }

  /**
   * Returns the names of all the command-line that can be displayed by this
   * panel.
   * @return the names of all the command-line that can be displayed by this
   * panel.
   */
  protected Set<String> getAllCommandLineNames()
  {
    return allScriptNames;
  }

  /**
   * Returns the names of the most important command-line to be displayed by
   * this panel.
   * @return the names of the most important command-line to be displayed by
   * this panel.
   */
  protected Set<String> getRelevantCommandLineNames()
  {
    return relevantScriptNames;
  }



  /**
   * Returns <CODE>true</CODE> if the user updated the contents and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the user updated the contents and
   * <CODE>false</CODE> otherwise.
   */
  private boolean updatedByUser()
  {
    boolean updatedByUser = !javaHome.getText().equals(readJavaHome) ||
    useOpenDSJavaHome.isSelected() != readUseOpenDSJavaHome ||
    useOpenDSJavaArgs.isSelected() != readUseOpenDSJavaArgs ||
    !readJavaArguments.equals(getCurrentJavaArguments());

    return updatedByUser;
  }

  /**
   * Returns the java arguments specified by the user.
   * @return the java arguments specified by the user.
   */
  private Set<JavaArgumentsDescriptor> getCurrentJavaArguments()
  {
    HashSet<JavaArgumentsDescriptor> args =
      new HashSet<JavaArgumentsDescriptor>(currentJavaArguments);

    HashSet<JavaArgumentsDescriptor> tableArgs =
      new HashSet<JavaArgumentsDescriptor>();
    for (int i=0; i<argumentsTableModel.getRowCount(); i++)
    {
      tableArgs.add(argumentsTableModel.getJavaArguments(i));
    }
    for (JavaArgumentsDescriptor arg : tableArgs)
    {
      JavaArgumentsDescriptor foundJavaArgument = null;
      for (JavaArgumentsDescriptor arg1 : args)
      {
        if (arg1.getCommandName().equals(arg.getCommandName()))
        {
          foundJavaArgument = arg1;
          break;
        }
      }
      if (foundJavaArgument != null)
      {
        args.remove(foundJavaArgument);
      }
      args.add(arg);
    }
    return args;
  }


  /**
   * Filters the provided list of java arguments depending on the showing
   * options (basically whether the 'Show All Command-lines' is selected or
   * not).
   * @param args the list of java arguments.
   * @return a list of filtered java arguments (the ones that must be displayed
   * in the table).
   */
  private Set<JavaArgumentsDescriptor> filterJavaArguments(
      Set<JavaArgumentsDescriptor> args)
  {
    if (showAll.isSelected())
    {
      return args;
    }
    else
    {
      Set<JavaArgumentsDescriptor> filteredArgs =
        new HashSet<JavaArgumentsDescriptor>();
      for (String relevantName : getRelevantCommandLineNames())
      {
        for (JavaArgumentsDescriptor arg : args)
        {
          if (arg.getCommandName().equals(relevantName))
          {
            filteredArgs.add(arg);
            break;
          }
        }
      }
      return filteredArgs;
    }
  }

  /**
   * Inits the contents of the table in the background.
   *
   */
  private void initContents()
  {
    disableComponents();

    BackgroundTask<Void> worker = new BackgroundTask<Void>()
    {
      /**
       * {@inheritDoc}
       */
      @Override
      public Void processBackgroundTask() throws Throwable
      {
        String propertiesFile = getPropertiesFile();
        Properties properties = new Properties();
        BufferedReader reader = null;
        try
        {
          reader = new BufferedReader(new FileReader(propertiesFile));
          JavaPropertiesTool.updateProperties(reader, properties);
        }
        catch (Throwable t)
        {
        }
        finally
        {
          if (reader != null)
          {
            try
            {
              reader.close();
            }
            catch (Throwable t)
            {
            }
          }
        }

        String v = properties.getProperty("overwrite-env-java-home");
        readUseOpenDSJavaHome =
          (v == null) || ("false".equalsIgnoreCase(v.trim()));
        v = properties.getProperty("overwrite-env-java-args");
        readUseOpenDSJavaArgs =
          (v == null) || ("false".equalsIgnoreCase(v.trim()));

        readJavaHome = properties.getProperty("default.java-home");
        if (readJavaHome == null)
        {
          for (String script : getAllCommandLineNames())
          {
            readJavaHome = properties.getProperty(script+".java-home");
            if (readJavaHome != null)
            {
              break;
            }
          }
        }

        readJavaArguments.clear();
        for (String script : getAllCommandLineNames())
        {
          v = properties.getProperty(script+".java-args");
          if (v != null)
          {
            readJavaArguments.add(new JavaArgumentsDescriptor(script, v));
          }
          else
          {
            readJavaArguments.add(new JavaArgumentsDescriptor(script, ""));
          }
        }

        return null;
      }
      /**
       * {@inheritDoc}
       */
      @Override
      public void backgroundTaskCompleted(Void returnValue,
          Throwable t)
      {
        if (t == null)
        {
          javaHome.setText(readJavaHome);
          useOpenDSJavaHome.setSelected(readUseOpenDSJavaHome);
          useSpecifiedJavaHome.setSelected(!readUseOpenDSJavaHome);
          useOpenDSJavaArgs.setSelected(readUseOpenDSJavaArgs);
          useSpecifiedJavaArgs.setSelected(!readUseOpenDSJavaArgs);
          currentJavaArguments.clear();
          currentJavaArguments.addAll(readJavaArguments);
          argumentsTableModel.setData(
              filterJavaArguments(currentJavaArguments));
          Utilities.updateTableSizes(argumentsTable, 7);
        }
        else
        {
          String arg;
          if (t instanceof OpenDsException)
          {
            arg = ((OpenDsException)t).getMessageObject().toString();
          }
          else
          {
            arg = t.toString();
          }
          Message title =
            ERR_CTRL_PANEL_ERR_READING_JAVA_SETTINGS_SUMMARY.get();
          Message details =
            ERR_CTRL_PANEL_READING_JAVA_SETTINGS_DETAILS.get(arg);
          updateErrorPane(errorPane, title,
              ColorAndFontConstants.errorTitleFont, details,
              errorPane.getFont());
          packParentDialog();
          errorPane.setVisible(true);
        }
        enableComponents();
      }
    };
    worker.startBackgroundTask();
  }

  /**
   * Disables all the components.  This is used when we are reading the
   * java settings in the background.
   *
   */
  private void disableComponents()
  {
    setEnabledOK(false);
    lInitContents.setVisible(true);
    argumentsScroll.setVisible(false);
    for (JComponent comp : comps)
    {
      comp.setEnabled(false);
    }
  }

  /**
   * Enables all the components.  This is used when we are reading the
   * java settings in the background.
   *
   */
  private void enableComponents()
  {
    for (JComponent comp : comps)
    {
      comp.setEnabled(true);
    }
    lInitContents.setVisible(false);
    argumentsScroll.setVisible(true);
    setEnabledOK(true);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void okClicked()
  {
    editor.stopCellEditing();

    ArrayList<Message> errors = new ArrayList<Message>();
    String f = javaHome.getText().trim();
    if (f.length() > 0)
    {
      File file = new File(f);
      if (!file.exists())
      {
        errors.add(ERR_CTRL_PANEL_JAVA_PATH_DOES_NOT_EXIST.get(f));
      }
      else if (!file.isDirectory())
      {
        errors.add(ERR_CTRL_PANEL_JAVA_PATH_NOT_A_DIRECTORY.get(f));
      }
      else
      {
        File javaFile = getJavaFile(file);
        if (!javaFile.exists())
        {
          errors.add(ERR_CTRL_PANEL_JAVA_BINARY_NOT_FOUND.get(
              javaFile.toString()));
        }
      }
    }
    if (errors.size() == 0)
    {
      final Set<String> providedArguments = new HashSet<String>();
      for (JavaArgumentsDescriptor cmd : getCurrentJavaArguments())
      {
        String args = cmd.getJavaArguments().trim();
        if (args.length() > 0)
        {
          providedArguments.add(args);
        }
      }
      if (!providedArguments.isEmpty())
      {
        disableComponents();
        lInitContents.setText(
            INFO_CTRL_PANEL_CHECKING_JAVA_ARGUMENTS_SUMMARY.get().toString());
        BackgroundTask<Set<String>> worker =
          new BackgroundTask<Set<String>>()
        {
          private String jvm;
          @Override
          public Set<String> processBackgroundTask() throws Throwable
          {
            Set<String> notWorkingArgs = new HashSet<String>();
            jvm = javaHome.getText();
            if (jvm.trim().length() == 0)
            {
              jvm = System.getProperty("java.home");
              if ((jvm == null) || (jvm.length() == 0))
              {
                jvm = System.getenv(SetupUtils.OPENDS_JAVA_HOME);
              }
            }

            String installPath = getInfo().getServerDescriptor().
            getInstallPath().getAbsolutePath();
            for (String arg : providedArguments)
            {
              if (!Utils.supportsOption(arg, jvm, installPath))
              {
                notWorkingArgs.add(arg);
              }
            }

            return notWorkingArgs;
          }
          /**
           * {@inheritDoc}
           */
          @Override
          public void backgroundTaskCompleted(Set<String> returnValue,
              Throwable t)
          {
            if (t == null)
            {
              boolean confirm = true;
              if (!returnValue.isEmpty())
              {
                File javaFile = getJavaFile(new File(jvm));
                Message confirmationMessage =
                  INFO_CTRL_PANEL_CONFIRM_NOT_WORKING_ARGUMENTS_DETAILS.get(
                      javaFile.toString(),
                      Utilities.getStringFromCollection(returnValue, "<br>-"));
                confirm = displayConfirmationDialog(
                    INFO_CTRL_PANEL_CONFIRMATION_REQUIRED_SUMMARY.get(),
                    confirmationMessage);
              }
              if (confirm)
              {
                launchTask();
              }
            }
            else
            {
              String arg;
              if (t instanceof OpenDsException)
              {
                arg = ((OpenDsException)t).getMessageObject().toString();
              }
              else
              {
                arg = t.toString();
              }
              Message title =
                ERR_CTRL_PANEL_ERROR_CHECKING_JAVA_SETTINGS_SUMMARY.get();
              Message details =
                ERR_CTRL_PANEL_ERROR_CHECKING_JAVA_SETTINGS_DETAILS.get(arg);
              updateErrorPane(errorPane, title,
                  ColorAndFontConstants.errorTitleFont, details,
                  errorPane.getFont());
              packParentDialog();
              errorPane.setVisible(true);
            }
            enableComponents();
            lInitContents.setText(READING_JAVA_SETTINGS.toString());
          }
        };
        worker.startBackgroundTask();
        return;
      }
    }
    if (errors.size() == 0)
    {
      launchTask();
    }
    else
    {
      displayErrorDialog(errors);
    }
  }

  /**
   * Returns the java binary (the executable) for a given java home.
   * @param javaHome the java home.
   * @return the java binary (the executable) for the provided java home.
   */
  private File getJavaFile(File javaHome)
  {
    File javaFile = new File(javaHome, "bin");
    if (Utilities.isWindows())
    {
      javaFile = new File(javaFile, "java.exe");
    }
    else
    {
      javaFile = new File(javaFile, "java");
    }
    return javaFile;
  }

  private void launchTask()
  {
    ArrayList<Message> errors = new ArrayList<Message>();
    ProgressDialog dlg = new ProgressDialog(
        Utilities.getParentDialog(this),
        INFO_CTRL_PANEL_JAVA_PROPERTIES_TITLE.get(),
        getInfo());
    JavaPropertiesTask newTask = new JavaPropertiesTask(getInfo(), dlg);
    for (Task task : getInfo().getTasks())
    {
      task.canLaunch(newTask, errors);
    }
    if (errors.size() == 0)
    {
      launchOperation(newTask,
          INFO_CTRL_PANEL_UPDATING_JAVA_SETTINGS_SUMMARY.get(),
          INFO_CTRL_PANEL_UPDATING_JAVA_SETTINGS_COMPLETE.get(),
          INFO_CTRL_PANEL_UPDATING_JAVA_SETTINGS_SUCCESSFUL.get(),
          ERR_CTRL_PANEL_UPDATING_JAVA_SETTINGS_ERROR_SUMMARY.get(),
          ERR_CTRL_PANEL_UPDATING_JAVA_SETTINGS_ERROR_DETAILS.get(),
          ERR_CTRL_PANEL_UPDATING_JAVA_SETTINGS_ERROR_CODE,
          dlg);
      dlg.setVisible(true);
      Utilities.getParentDialog(this).setVisible(false);
      readJavaHome = javaHome.getText();
      readUseOpenDSJavaHome = useOpenDSJavaHome.isSelected();
      readUseOpenDSJavaArgs = useOpenDSJavaArgs.isSelected();
      readJavaArguments = getCurrentJavaArguments();
    }
    else
    {
      displayErrorDialog(errors);
    }
  }

  /**
   * Returns the file containing the java properties.
   * @return the file containing the java properties.
   */
  private String getPropertiesFile()
  {
    String installPath = getInfo().getServerDescriptor().
      getInstancePath().getAbsolutePath();
    String propertiesFile =  Utils.getPath(
      Utilities.getInstanceRootDirectory(installPath).getAbsolutePath(),
      Installation.RELATIVE_JAVA_PROPERTIES_FILE);
    return propertiesFile;
  }

  /**
   * Class containing the command-name and the associated java
   * arguments.
   *
   */
  private class JavaArgumentsDescriptor
  {
    private String commandName;
    private String javaArguments;
    private int hashCode;
    private String toString;
    /**
     * Constructor of the arguments descriptor.
     * @param commandName the command-line name.
     * @param javaArguments the java arguments.
     */
    public JavaArgumentsDescriptor(String commandName, String javaArguments)
    {
      this.commandName = commandName;
      this.javaArguments = javaArguments;
      hashCode = commandName.hashCode() + javaArguments.hashCode();
      toString = commandName+ ": " +javaArguments;
    }

    /**
     * Returns the command-line name.
     * @return the command-line name.
     */
    public String getCommandName()
    {
      return commandName;
    }
    /**
     * Returns the java arguments associated with the command-line.
     * @return the java arguments associated with the command-line.
     */
    public String getJavaArguments()
    {
      return javaArguments;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode()
    {
      return hashCode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
      return toString;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o)
    {
      boolean equals = o == this;
      if (!equals)
      {
        if (o instanceof JavaArgumentsDescriptor)
        {
          equals =
            commandName.equals(((JavaArgumentsDescriptor)o).getCommandName()) &&
          javaArguments.equals(((JavaArgumentsDescriptor)o).getJavaArguments());
        }
      }
      return equals;
    }
  }

  /**
   * The table model used to display the java arguments.
   *
   */
  protected class JavaArgumentsTableModel extends SortableTableModel
  implements Comparator<JavaArgumentsDescriptor>
  {
    private static final long serialVersionUID = 8288418995255677560L;
    private Set<JavaArgumentsDescriptor> data =
      new HashSet<JavaArgumentsDescriptor>();
    private ArrayList<String[]> dataArray =
      new ArrayList<String[]>();
    private ArrayList<JavaArgumentsDescriptor> argsArray =
      new ArrayList<JavaArgumentsDescriptor>();
    private final String[] COLUMN_NAMES = new String[] {
        getHeader(INFO_CTRL_PANEL_COMMAND_LINE_NAME_COLUMN.get(), 40),
        getHeader(INFO_CTRL_PANEL_JAVA_ARGUMENTS_COLUMN.get(), 40)};
    private int sortColumn = 0;
    private boolean sortAscending = true;

    /**
     * Sets the data for this table model.
     * @param newData the data for this table model.
     */
    public void setData(Set<JavaArgumentsDescriptor> newData)
    {
      if (!newData.equals(data))
      {
        data.clear();
        data.addAll(newData);
        updateDataArray();
        fireTableDataChanged();
      }
    }

    /**
     * Compares two java argument descriptors.
     * @param desc1 the first java argument descriptor.
     * @param desc2 the second java argument descriptor.
     * @return 1 if in terms of comparison the first element goes higher than
     * the second one.  Returns 0 if both elements are equal in terms of
     * comparison.  Returns -1 if the second element goes higher than the first
     * one.
     */
    public int compare(JavaArgumentsDescriptor desc1,
        JavaArgumentsDescriptor desc2)
    {
      int result;
      int[] possibleResults = {
          desc1.getCommandName().compareTo(desc2.getCommandName()),
          desc1.getJavaArguments().compareTo(desc2.getJavaArguments())};
      result = possibleResults[sortColumn];
      if (result == 0)
      {
        for (int i : possibleResults)
        {
          if (i != 0)
          {
            result = i;
            break;
          }
        }
      }
      if (!sortAscending)
      {
        result = -result;
      }
      return result;
    }

    /**
     * Updates the table model contents and sorts its contents depending on the
     * sort options set by the user.
     */
    @Override
    public void forceResort()
    {
      updateDataArray();
      fireTableDataChanged();
    }



    /**
     * {@inheritDoc}
     */
    public int getColumnCount()
    {
      return COLUMN_NAMES.length;
    }

    /**
     * {@inheritDoc}
     */
    public int getRowCount()
    {
      return dataArray.size();
    }

    /**
     * {@inheritDoc}
     */
    public Object getValueAt(int row, int col)
    {
      return dataArray.get(row)[col];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getColumnName(int col) {
      return COLUMN_NAMES[col];
    }

    /**
     * Returns the java argument descriptor in the provided row.
     * @param row the row number.
     * @return the java argument descriptor in the provided row.
     */
    public JavaArgumentsDescriptor getJavaArguments(int row)
    {
      return argsArray.get(row);
    }


    /**
     * Returns whether the sort is ascending or descending.
     * @return <CODE>true</CODE> if the sort is ascending and <CODE>false</CODE>
     * otherwise.
     */
    @Override
    public boolean isSortAscending()
    {
      return sortAscending;
    }

    /**
     * Sets whether to sort ascending of descending.
     * @param sortAscending whether to sort ascending or descending.
     */
    @Override
    public void setSortAscending(boolean sortAscending)
    {
      this.sortAscending = sortAscending;
    }

    /**
     * Returns the column index used to sort.
     * @return the column index used to sort.
     */
    @Override
    public int getSortColumn()
    {
      return sortColumn;
    }

    /**
     * Sets the column index used to sort.
     * @param sortColumn column index used to sort..
     */
    @Override
    public void setSortColumn(int sortColumn)
    {
      this.sortColumn = sortColumn;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCellEditable(int row, int col) {
      if (col == 0)
      {
        return false;
      } else
      {
        return true;
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setValueAt(Object value, int row, int col)
    {
      dataArray.get(row)[col] = (String)value;
      JavaArgumentsDescriptor currentArg = argsArray.get(row);
      JavaArgumentsDescriptor newArg =
        new JavaArgumentsDescriptor(currentArg.getCommandName(), (String)value);
      argsArray.set(row, newArg);
      data.remove(currentArg);
      data.add(newArg);
      fireTableCellUpdated(row, col);
    }

    private void updateDataArray()
    {
      TreeSet<JavaArgumentsDescriptor> sortedSet =
        new TreeSet<JavaArgumentsDescriptor>(this);
      sortedSet.addAll(data);
      dataArray.clear();
      argsArray.clear();
      for (JavaArgumentsDescriptor arg : sortedSet)
      {
        String[] s = getLine(arg);
        dataArray.add(s);
        argsArray.add(arg);
      }
    }

    /**
     * Returns an array of String with the String representation of the cells
     * in the table.
     * @param desc the java argument descriptor for which we want to get the
     * cells.
     * @return an array of String with the String representation of the cells
     * in the table.
     */
    protected String[] getLine(JavaArgumentsDescriptor desc)
    {
      String cmd = desc.getCommandName();
      if (cmd.equalsIgnoreCase("start-ds"))
      {
        cmd = INFO_CTRL_PANEL_SERVER_RUNTIME_CELL.get(
            desc.getCommandName()).toString();
      }
      else if (cmd.endsWith(".online"))
      {
        int index = cmd.lastIndexOf(".online");
        cmd = INFO_CTRL_PANEL_ONLINE_COMMAND_LINE_CELL.get(
            cmd.substring(0, index)).toString();
      }
      else if (desc.getCommandName().endsWith(".offline"))
      {
        int index = cmd.lastIndexOf(".offline");
        cmd = INFO_CTRL_PANEL_OFFLINE_COMMAND_LINE_CELL.get(
            cmd.substring(0, index)).toString();
      }
      return new String[] {cmd, desc.getJavaArguments()};
    }
  }

  /**
   * The task in charge of updating the java properties.
   *
   */
  protected class JavaPropertiesTask extends Task
  {
    private Set<String> backendSet;
    private String defaultJavaHome;
    private boolean overwriteOpenDSJavaHome;
    private boolean overwriteOpenDSJavaArgs;
    Set<JavaArgumentsDescriptor> arguments =
      new HashSet<JavaArgumentsDescriptor>();

    /**
     * The constructor of the task.
     * @param info the control panel info.
     * @param dlg the progress dialog that shows the progress of the task.
     */
    public JavaPropertiesTask(ControlPanelInfo info, ProgressDialog dlg)
    {
      super(info, dlg);
      backendSet = new HashSet<String>();
      defaultJavaHome = javaHome.getText().trim();
      overwriteOpenDSJavaHome = useSpecifiedJavaHome.isSelected();
      overwriteOpenDSJavaArgs = useSpecifiedJavaArgs.isSelected();
      arguments = getCurrentJavaArguments();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Type getType()
    {
      return Type.JAVA_SETTINGS_UPDATE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getBackends()
    {
      return backendSet;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Message getTaskDescription()
    {
      return INFO_CTRL_PANEL_UPDATE_JAVA_SETTINGS_TASK_DESCRIPTION.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canLaunch(Task taskToBeLaunched,
        Collection<Message> incompatibilityReasons)
    {
      boolean canLaunch = true;
      if (!isServerRunning())
      {
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
      }
      return canLaunch;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getCommandLinePath()
    {
      return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ArrayList<String> getCommandLineArguments()
    {
      return new ArrayList<String>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void runTask()
    {
      state = State.RUNNING;
      lastException = null;

      try
      {
        returnCode = updateJavaSettings();
        if (returnCode == 0)
        {
          state = State.FINISHED_SUCCESSFULLY;
        }
        else
        {
          state = State.FINISHED_WITH_ERROR;
        }
      }
      catch (Throwable t)
      {
        lastException = t;
        state = State.FINISHED_WITH_ERROR;
      }
    }

    private int updateJavaSettings() throws IOException
    {
      final String propertiesFile = getPropertiesFile();
      ArrayList<String> commentLines = new ArrayList<String>();
      BufferedReader reader = null;
      try
      {
        reader = new BufferedReader(new FileReader(propertiesFile));
        String line;
        while ((line = reader.readLine()) != null)
        {
          String trimmedLine = line.trim();
          if (trimmedLine.startsWith("#") || (trimmedLine.length() == 0))
          {
            commentLines.add(line);
          }
          else
          {
            break;
          }
        }
      }
      catch (IOException ioe)
      {
        // Not critical.
      }
      finally
      {
        if (reader != null)
        {
          try
          {
            reader.close();
          }
          catch (Throwable t)
          {
          }
        }
      }

      BufferedWriter writer = null;
      try
      {
        writer = new BufferedWriter(new FileWriter(propertiesFile, false));
        for (String comment : commentLines)
        {
          writer.write(comment);
          writer.newLine();
        }
        writer.newLine();
        writer.write("overwrite-env-java-home="+overwriteOpenDSJavaHome);
        writer.newLine();
        writer.write("overwrite-env-java-args="+overwriteOpenDSJavaArgs);
        writer.newLine();
        writer.newLine();
        if ((defaultJavaHome != null) && (defaultJavaHome.length() > 0))
        {
          writer.write("default.java-home="+defaultJavaHome);
          writer.newLine();
          writer.newLine();
        }
        for (JavaArgumentsDescriptor desc : arguments)
        {
          String args = desc.getJavaArguments();
          if (args.trim().length() > 0)
          {
            writer.newLine();
            writer.write(desc.getCommandName()+".java-args="+args);
          }
        }
      }
      finally
      {
        if (writer != null)
        {
          try
          {
            writer.close();
          }
          catch (Throwable t)
          {
          }
        }
      }
      SwingUtilities.invokeLater(new Runnable()
      {
        /**
         * {@inheritDoc}
         */
        public void run()
        {
          getProgressDialog().appendProgressHtml(Utilities.applyFont(
              INFO_CTRL_PANEL_EQUIVALENT_ACTION_TO_UPDATE_JAVA_PROPERTIES.get(
                  propertiesFile, getCommandLinePath("dsjavaproperties")).
                  toString(),
              ColorAndFontConstants.progressFont));
        }
      });

      // Launch the script
      String[] args =
      {
          "--quiet"
      };

      return JavaPropertiesTool.mainCLI(args);
    }
  }
}
