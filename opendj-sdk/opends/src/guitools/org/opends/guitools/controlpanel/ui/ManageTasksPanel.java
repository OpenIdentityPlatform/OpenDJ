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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.server.util.StaticUtils.toLowerCase;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.CustomSearchResult;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.datamodel.TaskTableModel;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.task.CancelTaskTask;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.ui.renderer.TaskCellRenderer;
import org.opends.guitools.controlpanel.util.ConfigFromFile;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.quicksetup.util.Utils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.tools.tasks.TaskEntry;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.AttributeValues;
import org.opends.server.types.ByteString;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.OpenDsException;

/**
 * The panel displaying the list of scheduled tasks.
 *
 */
public class ManageTasksPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = -8034784684412532193L;

  private JLabel lNoTasksFound;

  /**
   * Remove task button.
   */
  private JButton cancelTask;
  /**
   * The scroll that contains the list of tasks (actually is a table).
   */
  private JScrollPane tableScroll;
  /**
   * The table of tasks.
   */
  private JTable taskTable;

  /**
   * The model of the table.
   */
  private TaskTableModel tableModel;

  private ManageTasksMenuBar menuBar;

  private MonitoringAttributesViewPanel<Message> operationViewPanel;
  private GenericDialog operationViewDlg;

  private JPanel detailsPanel;
  private JLabel noDetailsLabel;
  // The panel containing all the labels and values of the details.
  private JPanel detailsSubpanel;
  private JLabel logsLabel;
  private JScrollPane logsScroll;
  private JTextArea logs;
  private JLabel noLogsLabel;

  private static final Logger LOG =
    Logger.getLogger(ManageTasksPanel.class.getName());

  /**
   * Default constructor.
   *
   */
  public ManageTasksPanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_TASK_TO_SCHEDULE_LIST_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public boolean requiresScroll()
  {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  public GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.CLOSE;
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    // Nothing to do, it only contains a close button.
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public JMenuBar getMenuBar()
  {
    if (menuBar == null)
    {
      menuBar = new ManageTasksMenuBar(getInfo());
    }
    return menuBar;
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return taskTable;
  }

  /**
   * Returns the selected cancelable tasks in the list.
   * @param onlyCancelable add only the cancelable tasks.
   * @return the selected cancelable tasks in the list.
   */
  private List<TaskEntry> getSelectedTasks(boolean onlyCancelable)
  {
    ArrayList<TaskEntry> tasks = new ArrayList<TaskEntry>();
    int[] rows = taskTable.getSelectedRows();
    for (int row : rows)
    {
      if (row != -1)
      {
        TaskEntry task = tableModel.get(row);
        if (!onlyCancelable || task.isCancelable())
        {
          tasks.add(task);
        }
      }
    }
    return tasks;
  }

  /**
   * Creates the components and lays them in the panel.
   * @param gbc the grid bag constraints to be used.
   */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.WEST;
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 2;
    addErrorPane(gbc);

    gbc.weightx = 0.0;
    gbc.gridy ++;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.weightx = 0.0;
    gbc.fill = GridBagConstraints.NONE;
    gbc.gridwidth = 2;
    gbc.insets.left = 0;
    gbc.gridx = 0;
    gbc.gridy = 0;
    lNoTasksFound = Utilities.createDefaultLabel(
        INFO_CTRL_PANEL_NO_TASKS_FOUND.get());
    gbc.gridy ++;
    gbc.anchor = GridBagConstraints.CENTER;
    gbc.gridheight = 2;
    add(lNoTasksFound, gbc);
    lNoTasksFound.setVisible(false);

    gbc.gridwidth = 1;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.insets.top = 10;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    // Done to provide a good size to the table.
    tableModel = new TaskTableModel()
    {
      private static final long serialVersionUID = 55555512319230987L;

      /**
       * Updates the table model contents and sorts its contents depending on
       * the sort options set by the user.
       */
      public void forceResort()
      {
        Set<String> selectedIds = getSelectedIds();
        super.forceResort();
        setSelectedIds(selectedIds);
      }
    };
    tableModel.setData(createDummyTaskList());
    taskTable =
      Utilities.createSortableTable(tableModel, new TaskCellRenderer());
    taskTable.getSelectionModel().setSelectionMode(
        ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    tableScroll = Utilities.createScrollPane(taskTable);
    add(tableScroll, gbc);
    updateTableSizes();
    int height = taskTable.getPreferredScrollableViewportSize().height;
    add(Box.createVerticalStrut(height), gbc);

    gbc.gridx = 1;
    gbc.gridheight = 1;
    gbc.anchor = GridBagConstraints.EAST;
    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    cancelTask = Utilities.createButton(
        INFO_CTRL_PANEL_CANCEL_TASK_BUTTON_LABEL.get());
    cancelTask.setOpaque(false);
    gbc.insets.left = 10;
    add(cancelTask, gbc);

    gbc.gridy ++;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.VERTICAL;
    add(Box.createVerticalGlue(), gbc);
    cancelTask.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        cancelTaskClicked();
      }
    });

    gbc.gridy ++;
    gbc.gridx = 0;
    gbc.gridwidth = 2;
    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.insets.top = 15;
    gbc.insets.left = 0;
    logsLabel = Utilities.createDefaultLabel(
        INFO_CTRL_PANEL_TASK_LOG_LABEL.get());
    logsLabel.setFont(ColorAndFontConstants.titleFont);
    add(logsLabel, gbc);

    logs = Utilities.createNonEditableTextArea(Message.EMPTY, 5, 50);
    logs.setFont(ColorAndFontConstants.defaultFont);
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1.0;
    gbc.weighty = 0.7;
    gbc.gridy ++;
    gbc.insets.top = 5;
    logsScroll = Utilities.createScrollPane(logs);
    add(logsScroll, gbc);
    height = logs.getPreferredSize().height;
    add(Box.createVerticalStrut(height), gbc);
    logsScroll.setVisible(false);

    gbc.anchor = GridBagConstraints.CENTER;
    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    noLogsLabel =
      Utilities.createDefaultLabel(INFO_CTRL_PANEL_NO_TASK_SELECTED.get());
    add(noLogsLabel, gbc);

    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1.0;
    gbc.weighty = 0.8;
    gbc.gridy ++;
    gbc.insets.left = 0;
    gbc.insets.top = 15;
    createDetailsPanel();
    add(detailsPanel, gbc);

    ListSelectionListener listener = new ListSelectionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void valueChanged(ListSelectionEvent ev)
      {
        tableSelected();
      }
    };
    taskTable.getSelectionModel().addListSelectionListener(listener);
    listener.valueChanged(null);
  }

  /**
   * Creates the details panel.
   *
   */
  private void createDetailsPanel()
  {
    detailsPanel = new JPanel(new GridBagLayout());
    detailsPanel.setOpaque(false);

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 1;
    gbc.gridy = 1;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    JLabel label = Utilities.createDefaultLabel(
        INFO_CTRL_PANEL_TASK_SPECIFIC_DETAILS.get());
    label.setFont(ColorAndFontConstants.titleFont);
    detailsPanel.add(label, gbc);
    gbc.gridy ++;
    gbc.anchor = GridBagConstraints.CENTER;
    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    noDetailsLabel =
      Utilities.createDefaultLabel(INFO_CTRL_PANEL_NO_TASK_SELECTED.get());
    gbc.gridwidth = 2;
    detailsPanel.add(noDetailsLabel, gbc);

    detailsSubpanel = new JPanel(new GridBagLayout());
    detailsSubpanel.setOpaque(false);
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.fill = GridBagConstraints.BOTH;
    detailsPanel.add(Utilities.createBorderLessScrollBar(detailsSubpanel), gbc);

    detailsPanel.add(
        Box.createVerticalStrut(logs.getPreferredSize().height), gbc);
  }

  /**
   * Method called when the table is selected.
   *
   */
  private void tableSelected()
  {
    List<TaskEntry> tasks = getSelectedTasks(true);
    cancelTask.setEnabled(!tasks.isEmpty());

    detailsSubpanel.removeAll();

    tasks = getSelectedTasks(false);

    boolean displayContents = false;
    if (tasks.isEmpty())
    {
      noDetailsLabel.setText(INFO_CTRL_PANEL_NO_TASK_SELECTED.get().toString());
      logsScroll.setVisible(false);
      noLogsLabel.setText(INFO_CTRL_PANEL_NO_TASK_SELECTED.get().toString());
      noLogsLabel.setVisible(true);
    }
    else if (tasks.size() > 1)
    {
      noDetailsLabel.setText(
          INFO_CTRL_PANEL_MULTIPLE_TASKS_SELECTED.get().toString());
      logsScroll.setVisible(false);
      noLogsLabel.setText(
          INFO_CTRL_PANEL_MULTIPLE_TASKS_SELECTED.get().toString());
      noLogsLabel.setVisible(true);
    }
    else
    {
      TaskEntry taskEntry = tasks.iterator().next();
      Map<Message,List<String>> taskSpecificAttrs =
        taskEntry.getTaskSpecificAttributeValuePairs();
      List<Message> lastLogMessages = taskEntry.getLogMessages();
      if (!lastLogMessages.isEmpty())
      {
        StringBuilder sb = new StringBuilder();
        for (Message msg : lastLogMessages)
        {
          if (sb.length() != 0)
          {
            sb.append("\n");
          }
          sb.append(msg);
        }
        logs.setText(sb.toString());
      }
      else
      {
        logs.setText("");
      }
      logsScroll.setVisible(true);
      noLogsLabel.setVisible(false);

      if (taskSpecificAttrs.isEmpty())
      {
        noDetailsLabel.setText(
            INFO_CTRL_PANEL_NO_TASK_SPECIFIC_DETAILS.get().toString());
      }
      else
      {
        displayContents = true;
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets.top = 10;
        for (Message label : taskSpecificAttrs.keySet())
        {
          List<String> values = taskSpecificAttrs.get(label);
          gbc.gridx = 0;
          gbc.insets.left = 10;
          gbc.insets.right = 0;
          detailsSubpanel.add(Utilities.createPrimaryLabel(
              INFO_CTRL_PANEL_OPERATION_NAME_AS_LABEL.get(label.toString())),
              gbc);

          gbc.gridx = 1;
          gbc.insets.right = 10;

          String s = Utils.getStringFromCollection(values, "\n");
          detailsSubpanel.add(
              Utilities.makeHtmlPane(s, ColorAndFontConstants.defaultFont),
              gbc);

          gbc.gridy ++;
        }
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        detailsSubpanel.add(Box.createGlue(), gbc);
      }
    }
    noDetailsLabel.setVisible(!displayContents);
    revalidate();
    repaint();
  }

  /**
   * Creates a list with task descriptors.  This is done simply to have a good
   * initial size for the table.
   * @return a list with bogus task descriptors.
   */
  private Set<TaskEntry> createRandomTasksList()
  {
    Set<TaskEntry> list = new HashSet<TaskEntry>();
    Random r = new Random();
    int numberTasks = r.nextInt(10);
    for (int i= 0; i<numberTasks; i++)
    {
      CustomSearchResult csr =
        new CustomSearchResult("cn=mytask"+i+",cn=tasks");
      String p = "ds-task-";
      String[] attrNames =
      {
          p + "id",
          p + "class-name",
          p + "state",
          p + "scheduled-start-time",
          p + "actual-start-time",
          p + "completion-time",
          p + "dependency-id",
          p + "failed-dependency-action",
          p + "log-message",
          p + "notify-on-error",
          p + "notify-on-completion",
          p + "ds-recurring-task-schedule"
      };
      String[] values =
      {
          "ID",
          "TheClassName",
          "TheState",
          "Schedule Start Time",
          "Actual Start Time",
          "Completion Time",
          "Dependency ID",
          "Failed Dependency Action",
          "Log Message.                              Should be pretty long"+
          "Log Message.                              Should be pretty long"+
          "Log Message.                              Should be pretty long"+
          "Log Message.                              Should be pretty long"+
          "Log Message.                              Should be pretty long",
          "Notify On Error",
          "Notify On Completion",
          "Recurring Task Schedule"
      };
      for (int j=0; j < attrNames.length; j++)
      {
        List<Object> attrValues = new ArrayList<Object>(1);
        attrValues.add(values[j] + r.nextInt());
        csr.set(attrNames[j], attrValues);
      }
      try
      {
        Entry entry = getEntry(csr);
        TaskEntry task = new TaskEntry(entry);
        list.add(task);
      }
      catch (Throwable t)
      {
        LOG.log(Level.SEVERE, "Error getting entry '"+csr.getDN()+"': "+t, t);
      }
    }
    return list;
  }

  /**
   * Creates a list with task descriptors.  This is done simply to have a good
   * initial size for the table.
   * @return a list with bogus task descriptors.
   */
  private Set<TaskEntry> createDummyTaskList()
  {
    Set<TaskEntry> list = new HashSet<TaskEntry>();
    for (int i= 0; i<10; i++)
    {
      CustomSearchResult csr =
        new CustomSearchResult("cn=mytask"+i+",cn=tasks");
      String p = "ds-task-";
      String[] attrNames =
      {
          p + "id",
          p + "class-name",
          p + "state",
          p + "scheduled-start-time",
          p + "actual-start-time",
          p + "completion-time",
          p + "dependency-id",
          p + "failed-dependency-action",
          p + "log-message",
          p + "notify-on-error",
          p + "notify-on-completion",
          p + "ds-recurring-task-schedule"
      };
      String[] values =
      {
          "A very 29-backup - Sun Mar 29 00:00:00 MET 2009",
          "A long task type",
          "A very long task status",
          "Schedule Start Time",
          "Actual Start Time",
          "Completion Time",
          "Dependency ID",
          "Failed Dependency Action",
          "Log Message.                              Should be pretty long\n"+
          "Log Message.                              Should be pretty long\n"+
          "Log Message.                              Should be pretty long\n"+
          "Log Message.                              Should be pretty long\n"+
          "Log Message.                              Should be pretty long\n",
          "Notify On Error",
          "Notify On Completion",
          "Recurring Task Schedule"
      };
      for (int j=0; j < attrNames.length; j++)
      {
        List<Object> attrValues = new ArrayList<Object>(1);
        attrValues.add(values[j]);
        csr.set(attrNames[j], attrValues);
      }
      try
      {
        Entry entry = getEntry(csr);
        TaskEntry task = new TaskEntry(entry);
        list.add(task);
      }
      catch (Throwable t)
      {
        LOG.log(Level.SEVERE, "Error getting entry '"+csr.getDN()+"': "+t, t);
      }
    }
    return list;
  }

  private void cancelTaskClicked()
  {
    ArrayList<Message> errors = new ArrayList<Message>();
    ProgressDialog dlg = new ProgressDialog(
        Utilities.createFrame(),
        Utilities.getParentDialog(this),
        INFO_CTRL_PANEL_CANCEL_TASK_TITLE.get(), getInfo());
    List<TaskEntry> tasks = getSelectedTasks(true);
    CancelTaskTask newTask = new CancelTaskTask(getInfo(), dlg, tasks);
    for (Task task : getInfo().getTasks())
    {
      task.canLaunch(newTask, errors);
    }
    if (errors.size() == 0)
    {
      boolean confirmed = displayConfirmationDialog(
          INFO_CTRL_PANEL_CONFIRMATION_REQUIRED_SUMMARY.get(),
          INFO_CTRL_PANEL_CANCEL_TASK_MSG.get());
      if (confirmed)
      {
        launchOperation(newTask,
            INFO_CTRL_PANEL_CANCELING_TASK_SUMMARY.get(),
            INFO_CTRL_PANEL_CANCELING_TASK_COMPLETE.get(),
            INFO_CTRL_PANEL_CANCELING_TASK_SUCCESSFUL.get(),
            ERR_CTRL_PANEL_CANCELING_TASK_ERROR_SUMMARY.get(),
            ERR_CTRL_PANEL_CANCELING_TASK_ERROR_DETAILS.get(),
            null,
            dlg);
        dlg.setVisible(true);
      }
    }
  }



  /**
   * Gets the Entry object equivalent to the provided CustomSearchResult.
   * The method assumes that the schema in DirectoryServer has been initialized.
   * @param csr the search result.
   * @return the Entry object equivalent to the provided CustomSearchResult.
   * @throws OpenDsException if there is an error parsing the DN or retrieving
   * the attributes definition and objectclasses in the schema of the server.
   * TODO: move somewhere better.
   */
  public static Entry getEntry(CustomSearchResult csr) throws OpenDsException
  {
    DN dn = DN.decode(csr.getDN());
    Map<ObjectClass,String> objectClasses = new HashMap<ObjectClass,String>();
    Map<AttributeType,List<Attribute>> userAttributes =
      new HashMap<AttributeType,List<Attribute>>();
    Map<AttributeType,List<Attribute>> operationalAttributes =
      new HashMap<AttributeType,List<Attribute>>();

    for (String wholeName : csr.getAttributeNames())
    {
      final Attribute attribute = parseAttrDescription(wholeName);
      final String attrName = attribute.getName();
      final String lowerName = toLowerCase(attrName);

      // See if this is an objectclass or an attribute.  Then get the
      // corresponding definition and add the value to the appropriate hash.
      if (lowerName.equals("objectclass"))
      {
        for (Object value : csr.getAttributeValues(attrName))
        {
          String ocName = value.toString().trim();
          String lowerOCName = toLowerCase(ocName);

          ObjectClass objectClass =
            DirectoryServer.getObjectClass(lowerOCName);
          if (objectClass == null)
          {
            objectClass = DirectoryServer.getDefaultObjectClass(ocName);
          }

          objectClasses.put(objectClass, ocName);
        }
      }
      else
      {
        AttributeType attrType = DirectoryServer.getAttributeType(lowerName);
        if (attrType == null)
        {
          attrType = DirectoryServer.getDefaultAttributeType(attrName);
        }

        AttributeBuilder builder = new AttributeBuilder(attribute, true);
        for (Object value : csr.getAttributeValues(attrName))
        {
          ByteString bs;
          if (value instanceof byte[])
          {
            bs = ByteString.wrap((byte[])value);
          }
          else
          {
            bs = ByteString.valueOf(value.toString());
          }
          AttributeValue attributeValue =
            AttributeValues.create(attrType, bs);
          builder.add(attributeValue);
        }
        List<Attribute> attrList = new ArrayList<Attribute>(1);
        attrList.add(builder.toAttribute());

        if (attrType.isOperational())
        {
          operationalAttributes.put(attrType, attrList);
        }
        else
        {
          userAttributes.put(attrType, attrList);
        }
      }
    }

    return new Entry(dn, objectClasses, userAttributes, operationalAttributes);
  }

  /**
   * Parse an AttributeDescription (an attribute type name and its
   * options).
   * TODO: make this method in LDIFReader public.
   *
   * @param attrDescr
   *          The attribute description to be parsed.
   * @return A new attribute with no values, representing the
   *         attribute type and its options.
   */
  private static Attribute parseAttrDescription(String attrDescr)
  {
    AttributeBuilder builder;
    int semicolonPos = attrDescr.indexOf(';');
    if (semicolonPos > 0)
    {
      builder = new AttributeBuilder(attrDescr.substring(0, semicolonPos));
      int nextPos = attrDescr.indexOf(';', semicolonPos + 1);
      while (nextPos > 0)
      {
        String option = attrDescr.substring(semicolonPos + 1, nextPos);
        if (option.length() > 0)
        {
          builder.setOption(option);
          semicolonPos = nextPos;
          nextPos = attrDescr.indexOf(';', semicolonPos + 1);
        }
      }

      String option = attrDescr.substring(semicolonPos + 1);
      if (option.length() > 0)
      {
        builder.setOption(option);
      }
    }
    else
    {
      builder = new AttributeBuilder(attrDescr);
    }

    if(builder.getAttributeType().isBinary())
    {
      //resetting doesn't hurt and returns false.
      builder.setOption("binary");
    }

    return builder.toAttribute();
  }

  /**
   * The main method to test this panel.
   * @param args the arguments.
   */
  public static void main(String[] args)
  {
    // This is a hack to initialize configuration
    new ConfigFromFile();
    final ManageTasksPanel p = new ManageTasksPanel();
    Thread t = new Thread(new Runnable()
    {
      public void run()
      {
        try
        {
          // To let the dialog to be displayed
          Thread.sleep(5000);
        }
        catch (Throwable t)
        {
          t.printStackTrace();
        }
        while (p.isVisible())
        {
          try
          {
            SwingUtilities.invokeLater(new Runnable(){
              public void run()
              {
                Set<TaskEntry> tasks = p.createRandomTasksList();
                p.tableModel.setData(tasks);
                boolean visible = p.tableModel.getRowCount() > 0;
                if (visible)
                {
                  p.updateTableSizes();
                }
                p.tableModel.fireTableDataChanged();
                p.lNoTasksFound.setVisible(!visible);
                p.tableScroll.setVisible(visible);
                p.cancelTask.setVisible(visible);
              }
            });
            Thread.sleep(5000);
          }
          catch (Exception ex)
          {
            ex.printStackTrace();
          }
        }
      }
    });
    t.start();


    SwingUtilities.invokeLater(new Runnable(){
      public void run()
      {
        GenericDialog dlg = new GenericDialog(Utilities.createFrame(), p);
        dlg.setModal(true);
        dlg.pack();
        dlg.setVisible(true);
      }
    });
    t = null;
  }

  /**
   * Displays a dialog allowing the user to select which operations to display.
   *
   */
  private void operationViewClicked()
  {
    if (operationViewDlg == null)
    {
      operationViewPanel = MonitoringAttributesViewPanel.createMessageInstance(
          tableModel.getAllAttributes());
      operationViewDlg = new GenericDialog(Utilities.getFrame(this),
          operationViewPanel);
      Utilities.centerGoldenMean(operationViewDlg,
          Utilities.getParentDialog(this));
      operationViewDlg.setModal(true);
    }
    operationViewPanel.setSelectedAttributes(
        tableModel.getDisplayedAttributes());
    operationViewDlg.setVisible(true);
    if (!operationViewPanel.isCanceled())
    {
      LinkedHashSet<Message> displayedAttributes =
        operationViewPanel.getAttributes();
      setAttributesToDisplay(displayedAttributes);
      updateTableSizes();
    }
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    updateErrorPaneIfServerRunningAndAuthRequired(ev.getNewDescriptor(),
        INFO_CTRL_PANEL_SCHEDULED_TASK_LIST_REQUIRES_SERVER_RUNNING.get(),
        INFO_CTRL_PANEL_SCHEDULED_TASK_LIST_AUTHENTICATION.get());
    ServerDescriptor server = ev.getNewDescriptor();
    final Set<TaskEntry> tasks = server.getTaskEntries();
    boolean changed = tableModel.getRowCount() != tasks.size();
    if (!changed)
    {
      for (int i=0; i<tableModel.getRowCount(); i++)
      {
        if (!tasks.contains(tableModel.get(i)))
        {
          changed = true;
          break;
        }
      }
    }
    if (changed)
    {
      SwingUtilities.invokeLater(new Runnable()
      {
        /**
         * {@inheritDoc}
         */
        public void run()
        {
          Set<String> selectedIds = getSelectedIds();
          tableModel.setData(tasks);
          boolean visible = tableModel.getRowCount() > 0;
          if (visible)
          {
            updateTableSizes();
            setSelectedIds(selectedIds);
          }
          else
          {
            logsLabel.setVisible(false);
            logsScroll.setVisible(false);
          }
          tableModel.fireTableDataChanged();
          lNoTasksFound.setVisible(!visible &&
              !errorPane.isVisible());
          tableScroll.setVisible(visible);
          cancelTask.setVisible(visible);
          detailsPanel.setVisible(visible);
        }
      });
    }
  }

  private void updateTableSizes()
  {
    Utilities.updateTableSizes(taskTable, 5);
    Utilities.updateScrollMode(tableScroll, taskTable);
  }

  private void setAttributesToDisplay(LinkedHashSet<Message> attributes)
  {
    Set<String> selectedIds = getSelectedIds();
    tableModel.setAttributes(attributes);
    tableModel.forceDataStructureChange();
    setSelectedIds(selectedIds);
  }

  /**
   * The specific menu bar of this panel.
   *
   */
  class ManageTasksMenuBar extends MainMenuBar
  {
    private static final long serialVersionUID = 5051878116443370L;

    /**
     * Constructor.
     * @param info the control panel info.
     */
    public ManageTasksMenuBar(ControlPanelInfo info)
    {
      super(info);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addMenus()
    {
      add(createViewMenuBar());
      add(createHelpMenuBar());
    }

    /**
     * Creates the view menu bar.
     * @return the view menu bar.
     */
    @Override
    protected JMenu createViewMenuBar()
    {
      JMenu menu = Utilities.createMenu(
          INFO_CTRL_PANEL_CONNECTION_HANDLER_VIEW_MENU.get(),
          INFO_CTRL_PANEL_CONNECTION_HANDLER_VIEW_MENU_DESCRIPTION.get());
      menu.setMnemonic(KeyEvent.VK_V);
      final JMenuItem viewOperations = Utilities.createMenuItem(
          INFO_CTRL_PANEL_TASK_ATTRIBUTES_VIEW.get());
      menu.add(viewOperations);
      viewOperations.addActionListener(new ActionListener()
      {
        public void actionPerformed(ActionEvent ev)
        {
          operationViewClicked();
        }
      });
      return menu;
    }
  }

  private Set<String> getSelectedIds()
  {
    Set<String> selectedIds = new HashSet<String>();
    int[] indexes = taskTable.getSelectedRows();
    if (indexes != null)
    {
      for (int index : indexes)
      {
        TaskEntry taskEntry = tableModel.get(index);
        selectedIds.add(taskEntry.getId());
      }
    }
    return selectedIds;
  }

  private void setSelectedIds(Set<String> ids)
  {
    taskTable.getSelectionModel().clearSelection();
    for (int i=0; i<tableModel.getRowCount(); i++)
    {
      TaskEntry taskEntry = tableModel.get(i);
      if (ids.contains(taskEntry.getId()))
      {
        taskTable.getSelectionModel().addSelectionInterval(i, i);
      }
    }
  }
}
