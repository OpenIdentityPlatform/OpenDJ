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
import org.opends.messages.Message;
import org.opends.server.types.DN;
import org.opends.server.types.OpenDsException;

/**
 * The dialog that is displayed when we want to add entries to a set of groups.
 * @author jvergara
 *
 */
public class AddToGroupPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = 1837745944604435848L;
  private JTextArea groups;
  private JTextArea entries;
  private JScrollPane scrollEntries;
  private JLabel lEntries = Utilities.createDefaultLabel();
  private JLabel lGroups = Utilities.createDefaultLabel();
  private LinkedHashSet<DN> dns = new LinkedHashSet<DN>();

  private GenericDialog browseGroupDlg;
  private LDAPEntrySelectionPanel browseGroupPanel;

  /**
   * Default constructor.
   *
   */
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
    ArrayList<String> sDns = new ArrayList<String>();
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

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return groups;
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    final ArrayList<Message> errors = new ArrayList<Message>();
    BackgroundTask<Void> worker = new BackgroundTask<Void>()
    {
      /**
       * {@inheritDoc}
       */
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
      /**
       * {@inheritDoc}
       */
      public void backgroundTaskCompleted(Void returnValue, Throwable t)
      {
        if (t != null)
        {
          errors.add(ERR_CTRL_PANEL_UNEXPECTED_DETAILS.get(t.toString()));
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

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_ADD_TO_GROUP_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
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
    entries = Utilities.createNonEditableTextArea(Message.EMPTY, 6, 40);
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
    groups = Utilities.createTextArea(Message.EMPTY, 8, 40);
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
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        browseGroupsClicked();
      }
    });

    DropTargetListener dropTargetlistener = new DropTargetListener()
    {
      /**
       * {@inheritDoc}
       */
      public void dragEnter(DropTargetDragEvent e)
      {
      }

      /**
       * {@inheritDoc}
       */
      public void dragExit(DropTargetEvent e)
      {
      }

      /**
       * {@inheritDoc}
       */
      public void dragOver(DropTargetDragEvent e)
      {
      }

      /**
       * {@inheritDoc}
       */
      public void dropActionChanged(DropTargetDragEvent e)
      {
      }

      /**
       * {@inheritDoc}
       */
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
        catch (IOException io)
        {
          e.rejectDrop();
        }
        catch (UnsupportedFlavorException ufe)
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

  private void updateErrors(List<Message> errors)
  {
    SwingUtilities.invokeLater(new Runnable()
    {
      /**
       * {@inheritDoc}
       */
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
          DN.decode(groupDn);
          if (!entryExists(groupDn))
          {
            errors.add(
                ERR_CTRL_PANEL_GROUP_COULD_NOT_BE_FOUND.get(groupDn));
          }
          else if (!hasObjectClass(groupDn, "groupOfUniqueNames"))
          {
            errors.add(ERR_CTRL_PANEL_NOT_A_STATIC_GROUP.get(groupDn));
          }
          else
          {
            oneGroupDefined = true;
          }
        }
        catch (OpenDsException ode)
        {
          errors.add(INFO_CTRL_PANEL_INVALID_DN_DETAILS.get(groupDn,
              ode.getMessageObject().toString()));
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
        /**
         * {@inheritDoc}
         */
        public void run()
        {
          setPrimaryInvalid(lGroups);
        }
      });
    }
  }

  private void handleErrorsAndLaunchTask(ArrayList<Message> errors)
  {
    if (errors.size() == 0)
    {
      ProgressDialog dlg = new ProgressDialog(
          Utilities.getParentDialog(this),
          INFO_CTRL_PANEL_ADD_TO_GROUP_TITLE.get(), getInfo());
      LinkedHashSet<DN> groupDns = new LinkedHashSet<DN>();
      String[] grs = groups.getText().split("\n");
      try
      {
        for (String groupDn : grs)
        {
          groupDn = groupDn.trim();
          if (groupDn.length() > 0)
          {
            groupDns.add(DN.decode(groupDn));
          }
        }
      }
      catch (OpenDsException ode)
      {
        throw new IllegalStateException(
            "Unexpected error decoding dn. Details: "+ode.getMessageObject(),
            ode);
      }
      try
      {
        AddToGroupTask newTask =
          new AddToGroupTask(getInfo(), dlg, dns, groupDns);
        for (Task task : getInfo().getTasks())
        {
          task.canLaunch(newTask, errors);
        }
        if (errors.size() == 0)
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
        throw new IllegalStateException("Unexpected error: "+t, t);
      }
    }
    if (errors.size() > 0)
    {
      displayErrorDialog(errors);
    }
  }
}
