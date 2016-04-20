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
 * Portions Copyright 2013-2016 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.task.AddToGroupTask;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.ui.nodes.BrowserNodeInfo;
import org.opends.guitools.controlpanel.ui.nodes.DndBrowserNodes;
import org.opends.guitools.controlpanel.util.BackgroundTask;
import org.opends.guitools.controlpanel.util.Utilities;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.util.ServerConstants;

/** The dialog that is displayed when we want to add entries to a set of groups. */
public class AddToGroupPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = 1837745944604435848L;
  private JTextArea groups;
  private JTextArea entries;
  private JScrollPane scrollEntries;
  private JLabel lEntries = Utilities.createDefaultLabel();
  private JLabel lGroups = Utilities.createDefaultLabel();
  private LinkedHashSet<DN> dns = new LinkedHashSet<>();

  private GenericDialog browseGroupDlg;
  private LDAPEntrySelectionPanel browseGroupPanel;

  /** Default constructor. */
  public AddToGroupPanel()
  {
    super();
    createLayout();
  }

  /**
   * Sets the entries we want to add to groups.
   * @param dns the DN of the entries we want to add to groups.
   */
  public void setEntriesToAdd(Set<DN> dns)
  {
    ArrayList<String> sDns = new ArrayList<>();
    for (DN dn : dns)
    {
      sDns.add(dn.toString());
    }
    if (dns.size() > 5)
    {
      entries.setText(Utilities.getStringFromCollection(sDns, "\n"));
      scrollEntries.setVisible(true);
      lEntries.setVisible(false);
    }
    else
    {
      lEntries.setText("<html>"+Utilities.applyFont(
          "<li>"+Utilities.getStringFromCollection(sDns, "<li>"),
          ColorAndFontConstants.defaultFont));
      scrollEntries.setVisible(false);
      lEntries.setVisible(true);
    }
    this.dns.clear();
    this.dns.addAll(dns);
    packParentDialog();
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return groups;
  }

  @Override
  public void okClicked()
  {
    final ArrayList<LocalizableMessage> errors = new ArrayList<>();
    BackgroundTask<Void> worker = new BackgroundTask<Void>()
    {
      @Override
      public Void processBackgroundTask()
      {
        try
        {
          Thread.sleep(2000);
        }
        catch (Throwable t)
        {
        }
        updateErrors(errors);
        return null;
      }
      @Override
      public void backgroundTaskCompleted(Void returnValue, Throwable t)
      {
        if (t != null)
        {
          errors.add(ERR_CTRL_PANEL_UNEXPECTED_DETAILS.get(t));
        }
        displayMainPanel();
        setEnabledCancel(true);
        setEnabledOK(true);
        handleErrorsAndLaunchTask(errors);
      }
    };
    displayMessage(INFO_CTRL_PANEL_CHECKING_SUMMARY.get());
    setEnabledCancel(false);
    setEnabledOK(false);
    worker.startBackgroundTask();
  }

  @Override
  public LocalizableMessage getTitle()
  {
    return INFO_CTRL_PANEL_ADD_TO_GROUP_TITLE.get();
  }

  @Override
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
  }

  /** Creates the layout of the panel (but the contents are not populated here). */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    gbc.gridwidth = 2;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    JLabel l = Utilities.createDefaultLabel(
        INFO_CTRL_PANEL_ADD_TO_GROUP_ENTRIES_LABEL.get());
    add(l, gbc);
    gbc.insets.top = 5;
    entries = Utilities.createNonEditableTextArea(LocalizableMessage.EMPTY, 6, 40);
    scrollEntries = Utilities.createScrollPane(entries);
    gbc.weighty = 0.1;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridy ++;
    add(scrollEntries, gbc);
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets.top = 0;
    add(lEntries, gbc);

    gbc.insets.top = 10;
    gbc.gridy ++ ;
    lGroups.setText(INFO_CTRL_PANEL_ADD_TO_GROUP_GROUPS_LABEL.get().toString());
    add(lGroups, gbc);
    gbc.insets.top = 5;
    gbc.gridwidth = 1;
    groups = Utilities.createTextArea(LocalizableMessage.EMPTY, 8, 40);
    JScrollPane scrollGroups = Utilities.createScrollPane(groups);
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridy ++;
    add(scrollGroups, gbc);
    gbc.gridx ++;
    gbc.insets.left = 5;
    gbc.weightx = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    JButton browse = Utilities.createButton(
        INFO_CTRL_PANEL_ADD_GROUPS_BUTTON_LABEL.get());
    gbc.anchor = GridBagConstraints.NORTH;
    add(browse, gbc);
    browse.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        browseGroupsClicked();
      }
    });

    DropTargetListener dropTargetlistener = new DropTargetListener()
    {
      @Override
      public void dragEnter(DropTargetDragEvent e)
      {
      }

      @Override
      public void dragExit(DropTargetEvent e)
      {
      }

      @Override
      public void dragOver(DropTargetDragEvent e)
      {
      }

      @Override
      public void dropActionChanged(DropTargetDragEvent e)
      {
      }

      @Override
      public void drop(DropTargetDropEvent e)
      {
        try {
          Transferable tr = e.getTransferable();

          //flavor not supported, reject drop
          if (!tr.isDataFlavorSupported(DndBrowserNodes.INFO_FLAVOR))
          {
            e.rejectDrop();
          }

          //cast into appropriate data type
          DndBrowserNodes nodes =
            (DndBrowserNodes) tr.getTransferData(DndBrowserNodes.INFO_FLAVOR);

          StringBuilder sb = new StringBuilder();
          sb.append(groups.getText());
          for (BrowserNodeInfo node : nodes.getNodes())
          {
            if (sb.length() > 0)
            {
              sb.append("\n");
            }
            sb.append(node.getNode().getDN());
          }
          groups.setText(sb.toString());
          groups.setCaretPosition(sb.length());

          e.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
          e.getDropTargetContext().dropComplete(true);
        }
        catch (IOException | UnsupportedFlavorException io)
        {
          e.rejectDrop();
        }
      }
    };
    new DropTarget(groups, dropTargetlistener);
  }

  private void browseGroupsClicked()
  {
    if (browseGroupDlg == null)
    {
      browseGroupPanel = new LDAPEntrySelectionPanel();
      browseGroupPanel.setTitle(INFO_CTRL_PANEL_CHOOSE_GROUP_TITLE.get());
      browseGroupPanel.setFilter(
          LDAPEntrySelectionPanel.Filter.STATIC_GROUPS);
      browseGroupPanel.setMultipleSelection(true);
      browseGroupPanel.setInfo(getInfo());
      browseGroupDlg = new GenericDialog(Utilities.getFrame(this),
          browseGroupPanel);
      Utilities.centerGoldenMean(browseGroupDlg,
          Utilities.getParentDialog(this));
      browseGroupDlg.setModal(true);
    }
    browseGroupDlg.setVisible(true);
    String[] dns = browseGroupPanel.getDNs();
    if (dns.length > 0)
    {
      StringBuilder sb = new StringBuilder();
      sb.append(groups.getText());
      for (String dn : dns)
      {
        if (sb.length() > 0)
        {
          sb.append("\n");
        }
        sb.append(dn);
      }
      groups.setText(sb.toString());
      groups.setCaretPosition(sb.length());
    }
  }

  private void updateErrors(List<LocalizableMessage> errors)
  {
    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        setPrimaryValid(lGroups);
      }
    });

    String[] grs = groups.getText().split("\n");
    boolean oneGroupDefined = false;
    for (String groupDn : grs)
    {
      groupDn = groupDn.trim();
      if (groupDn.length() > 0)
      {
        try
        {
          DN.valueOf(groupDn);
          if (!entryExists(groupDn))
          {
            errors.add(
                ERR_CTRL_PANEL_GROUP_COULD_NOT_BE_FOUND.get(groupDn));
          }
          else if (!hasObjectClass(groupDn, ServerConstants.OC_GROUP_OF_NAMES,
            ServerConstants.OC_GROUP_OF_ENTRIES,
            ServerConstants.OC_GROUP_OF_UNIQUE_NAMES))
          {
            errors.add(ERR_CTRL_PANEL_NOT_A_STATIC_GROUP.get(groupDn));
          }
          else
          {
            oneGroupDefined = true;
          }
        }
        catch (LocalizedIllegalArgumentException e)
        {
          errors.add(INFO_CTRL_PANEL_INVALID_DN_DETAILS.get(groupDn, e.getMessageObject()));
        }
      }
    }
    if (!oneGroupDefined && errors.isEmpty())
    {
      errors.add(ERR_CTRL_PANEL_GROUP_NOT_PROVIDED.get());
    }

    if (!errors.isEmpty())
    {
      SwingUtilities.invokeLater(new Runnable()
      {
        @Override
        public void run()
        {
          setPrimaryInvalid(lGroups);
        }
      });
    }
  }

  private void handleErrorsAndLaunchTask(ArrayList<LocalizableMessage> errors)
  {
    if (errors.isEmpty())
    {
      ProgressDialog dlg = new ProgressDialog(
          Utilities.createFrame(),
          Utilities.getParentDialog(this),
          INFO_CTRL_PANEL_ADD_TO_GROUP_TITLE.get(), getInfo());
      LinkedHashSet<DN> groupDns = new LinkedHashSet<>();
      for (String groupDn : groups.getText().split("\n"))
      {
        groupDn = groupDn.trim();
        if (groupDn.length() > 0)
        {
          groupDns.add(DN.valueOf(groupDn));
        }
      }

      try
      {
        AddToGroupTask newTask =
          new AddToGroupTask(getInfo(), dlg, dns, groupDns);
        for (Task task : getInfo().getTasks())
        {
          task.canLaunch(newTask, errors);
        }
        if (errors.isEmpty())
        {
          launchOperation(newTask,
              INFO_CTRL_PANEL_ADDING_TO_GROUP_SUMMARY.get(),
              INFO_CTRL_PANEL_ADDING_TO_GROUP_SUCCESSFUL_SUMMARY.get(),
              INFO_CTRL_PANEL_ADDING_TO_GROUP_SUCCESSFUL_DETAILS.get(),
              ERR_CTRL_PANEL_ADDING_TO_GROUP_ERROR_SUMMARY.get(),
              ERR_CTRL_PANEL_ADDING_TO_GROUP_ERROR_DETAILS.get(),
              null,
              dlg);
          dlg.setVisible(true);
          Utilities.getParentDialog(this).setVisible(false);
        }
      }
      catch (Throwable t)
      {
        // Unexpected error: getEntry() should work after calling checkSyntax
        throw new RuntimeException("Unexpected error: "+t, t);
      }
    }
    if (!errors.isEmpty())
    {
      displayErrorDialog(errors);
    }
  }
}
