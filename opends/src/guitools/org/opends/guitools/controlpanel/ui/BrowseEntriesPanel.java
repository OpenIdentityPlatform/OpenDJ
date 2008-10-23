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
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceContext;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;

import javax.naming.ldap.InitialLdapContext;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.event.EntryReadErrorEvent;
import org.opends.guitools.controlpanel.task.DeleteEntryTask;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.ui.components.CustomTree;
import org.opends.guitools.controlpanel.ui.nodes.BasicNode;
import org.opends.guitools.controlpanel.ui.nodes.BrowserNodeInfo;
import org.opends.guitools.controlpanel.ui.nodes.DndBrowserNodes;
import org.opends.guitools.controlpanel.util.LDAPEntryReader;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.OpenDsException;
import org.opends.server.types.Schema;
import org.opends.server.util.ServerConstants;

/**
 * The pane that is displayed when the user clicks on 'Browse Entries...'.
 * It contains its own menu bar with all the actions to edit the entries.
 *
 */
public class BrowseEntriesPanel extends AbstractBrowseEntriesPanel
{
  private static final long serialVersionUID = 1308129251140541645L;

  private BrowseMenuBar menuBar;

  private JPopupMenu popup;
  private JMenuItem popupDeleteMenuItem;
  private JMenuItem popupCopyDNMenuItem;
  private JMenuItem popupAddToGroupMenuItem;
  private JMenuItem popupNewEntryFromLDIFMenuItem;
  private JMenuItem popupNewUserMenuItem;
  private JMenuItem popupNewGroupMenuItem;
  private JMenuItem popupNewOUMenuItem;
  private JMenuItem popupNewOrganizationMenuItem;
  private JMenuItem popupNewDomainMenuItem;
  private JMenuItem popupResetUserPasswordMenuItem;

  private LDAPEntryPanel entryPane;

  private GenericDialog resetUserPasswordDlg;
  private ResetUserPasswordPanel resetUserPasswordPanel;

  private GenericDialog addToGroupDlg;
  private AddToGroupPanel addToGroupPanel;

  private GenericDialog deleteBaseDNDlg;
  private GenericDialog deleteBackendDlg;

  private GenericDialog newUserDlg;
  private NewUserPanel newUserPanel;

  private GenericDialog newGroupDlg;
  private NewGroupPanel newGroupPanel;

  private GenericDialog newOUDlg;
  private NewOrganizationalUnitPanel newOUPanel;

  private GenericDialog newOrganizationDlg;
  private NewOrganizationPanel newOrganizationPanel;

  private GenericDialog newDomainDlg;
  private NewDomainPanel newDomainPanel;

  private GenericDialog newEntryFromLDIFDlg;
  private NewEntryFromLDIFPanel newEntryFromLDIFPanel;

  private boolean ignoreTreeSelectionEvents = false;

  private ArrayList<LDAPEntryReader> entryReaderQueue =
    new ArrayList<LDAPEntryReader>();

  /**
   * {@inheritDoc}
   */
  public JMenuBar getMenuBar()
  {
    if (menuBar == null)
    {
      menuBar = new BrowseMenuBar(getInfo());
      menuBar.deleteMenuItem.setEnabled(false);
    }
    return menuBar;
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_MANAGE_ENTRIES_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public GenericDialog.ButtonType getBrowseButtonType()
  {
    return GenericDialog.ButtonType.CLOSE;
  }

  /**
   * {@inheritDoc}
   */
  protected void createBrowserController(ControlPanelInfo info)
  {
    super.createBrowserController(info);
    entryPane.setController(controller);
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
  }

  /**
   * {@inheritDoc}
   */
  protected Component createMainPanel()
  {
    JSplitPane pane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    pane.setOpaque(true); //content panes must be opaque

    JComponent p = createTreePane();

    JTree tree = treePane.getTree();
    addDragAndDropListener(tree);
    addTreeSelectionListener(tree);

    JScrollPane treeScroll = Utilities.createScrollPane(p);
    treeScroll.setPreferredSize(
        new Dimension(treeScroll.getPreferredSize().width + 30,
            4 * treeScroll.getPreferredSize().height));
    pane.setDividerLocation(treeScroll.getPreferredSize().width);

    entryPane = new LDAPEntryPanel();


//  Create a split pane with the two scroll panes in it.
    pane.setLeftComponent(treeScroll);
    pane.setRightComponent(entryPane);
    pane.setResizeWeight(0.0);
    entryPane.setPreferredSize(
        new Dimension((treeScroll.getPreferredSize().width * 5) / 2,
            treeScroll.getPreferredSize().height));

    entryPane.setBorder(treeScroll.getBorder());

    addPopupMenu();

    return pane;
  }

  /**
   * Adds the tree selection listener.
   * @param tree the tree to which the listeners are added.
   */
  private void addTreeSelectionListener(JTree tree)
  {
    TreeSelectionListener treeSelectionListener = new TreeSelectionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void valueChanged(TreeSelectionEvent ev)
      {
        if (ignoreTreeSelectionEvents)
        {
          return;
        }
        TreePath path = null;
        TreePath[] paths = treePane.getTree().getSelectionPaths();
        if (entryPane.mustCheckUnsavedChanges())
        {
          ignoreTreeSelectionEvents = true;
          treePane.getTree().setSelectionPath(entryPane.getTreePath());
          switch (entryPane.checkUnsavedChanges())
          {
          case DO_NOT_SAVE:
            break;
          case SAVE:
            break;
          case CANCEL:
            ignoreTreeSelectionEvents = false;
            return;
          }
          if (paths != null)
          {
            treePane.getTree().setSelectionPaths(paths);
          }
          else
          {
            treePane.getTree().clearSelection();
          }
          ignoreTreeSelectionEvents = false;
        }
        if ((paths != null) && (paths.length == 1))
        {
          path = paths[0];
        }

//      Update menu items
        boolean enableDelete = false;
        if ((paths != null) && (paths.length > 0))
        {
          enableDelete = true;
          for (TreePath p : paths)
          {
            BasicNode n = (BasicNode)p.getLastPathComponent();
            enableDelete = entryPane.canDelete(n.getDN());
            if (!enableDelete)
            {
              break;
            }
          }
        }
        popupDeleteMenuItem.setEnabled(enableDelete);
        menuBar.deleteMenuItem.setEnabled(enableDelete);

        boolean enableCopyDN = (paths != null) && (paths.length > 0);
        popupCopyDNMenuItem.setEnabled(enableCopyDN);
        menuBar.copyDNMenuItem.setEnabled(enableCopyDN);

        boolean enableAddToGroup = enableCopyDN;
        popupAddToGroupMenuItem.setEnabled(enableAddToGroup);
        menuBar.addToGroupMenuItem.setEnabled(enableAddToGroup);

        boolean enableResetPassword = path != null;
        if (enableResetPassword)
        {
          BasicNode node = (BasicNode)path.getLastPathComponent();
          enableResetPassword = hasUserPassword(node.getObjectClassValues());
        }
        popupResetUserPasswordMenuItem.setEnabled(enableResetPassword);
        menuBar.resetPasswordMenuItem.setEnabled(enableResetPassword);

//      Assume that if we cannot delete, we cannot create a new path
        boolean enableNewEntry = (path != null) && enableDelete;
        popupNewUserMenuItem.setEnabled(enableNewEntry);
        menuBar.newUserMenuItem.setEnabled(enableNewEntry);

        popupNewGroupMenuItem.setEnabled(enableNewEntry);
        menuBar.newGroupMenuItem.setEnabled(enableNewEntry);

        popupNewOUMenuItem.setEnabled(enableNewEntry);
        menuBar.newOUMenuItem.setEnabled(enableNewEntry);

        popupNewOrganizationMenuItem.setEnabled(enableNewEntry);
        menuBar.newOrganizationMenuItem.setEnabled(enableNewEntry);

        popupNewDomainMenuItem.setEnabled(enableNewEntry);
        menuBar.newDomainMenuItem.setEnabled(enableNewEntry);

        updateRightPane(paths);
      }
    };
    tree.getSelectionModel().addTreeSelectionListener(treeSelectionListener);
  }

  /**
   * Adds a drag and drop listener to a tree.
   * @param tree the tree to which the listener is added.
   */
  private void addDragAndDropListener(JTree tree)
  {
    final DragSource dragSource = DragSource.getDefaultDragSource();
    final DragSourceListener dragSourceListener = new DragSourceListener()
    {
      /**
       * {@inheritDoc}
       */
      public void dragDropEnd(DragSourceDropEvent dsde)
      {
      }

      /**
       * {@inheritDoc}
       */
      public void dragEnter(DragSourceDragEvent dsde)
      {
        DragSourceContext context = dsde.getDragSourceContext();
        int dropAction = dsde.getDropAction();
        if ((dropAction & DnDConstants.ACTION_COPY) != 0)
        {
          context.setCursor(DragSource.DefaultCopyDrop);
        }
        else if ((dropAction & DnDConstants.ACTION_MOVE) != 0)
        {
          context.setCursor(DragSource.DefaultMoveDrop);
        }
        else
        {
          context.setCursor(DragSource.DefaultCopyNoDrop);
        }
      }

      /**
       * {@inheritDoc}
       */
      public void dragOver(DragSourceDragEvent dsde)
      {
      }

      /**
       * {@inheritDoc}
       */
      public void dropActionChanged(DragSourceDragEvent dsde)
      {
      }

      /**
       * {@inheritDoc}
       */
      public void dragExit(DragSourceEvent dsde)
      {
      }
    };
    final DragGestureListener dragGestureListener = new DragGestureListener()
    {
      /**
       * {@inheritDoc}
       */
      public void dragGestureRecognized(DragGestureEvent e)
      {
        //Get the selected node
        JTree tree = treePane.getTree();
        TreePath[] paths = tree.getSelectionPaths();
        if (paths != null)
        {
          BrowserNodeInfo[] nodes = new BrowserNodeInfo[paths.length];
          DndBrowserNodes dndNodes = new DndBrowserNodes();
          for (int i=0; i<paths.length; i++)
          {
            BrowserNodeInfo node = controller.getNodeInfoFromPath(paths[i]);
            nodes[i] = node;
          }
          dndNodes.setParent(tree);
          dndNodes.setNodes(nodes);
          //Select the appropriate cursor;
          Cursor cursor = DragSource.DefaultCopyNoDrop;
          // begin the drag
          dragSource.startDrag(e, cursor, dndNodes, dragSourceListener);
        }
      }
    };
    dragSource.createDefaultDragGestureRecognizer(tree,  //DragSource
        DnDConstants.ACTION_COPY_OR_MOVE, //specifies valid actions
        dragGestureListener
    );
  }

  /**
   * {@inheritDoc}
   */
  public void setInfo(ControlPanelInfo info)
  {
    super.setInfo(info);
    entryPane.setInfo(info);
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    final ServerDescriptor desc = ev.getNewDescriptor();

    updateMenus(desc);

    super.configurationChanged(ev);
  }

  /**
   * Returns <CODE>true</CODE> if the provided object classes allow (or require
   * the userPassword attribute).
   * @param ocs the object classes.
   * @return <CODE>true</CODE> if the provided object classes allow (or require
   * the userPassword attribute) and <CODE>false</CODE> otherwise.
   */
  private boolean hasUserPassword(String[] ocs)
  {
    boolean hasUserPassword = false;
    Schema schema = getInfo().getServerDescriptor().getSchema();
    if ((ocs != null) && (schema != null))
    {
      AttributeType attr = schema.getAttributeType(
          ServerConstants.ATTR_USER_PASSWORD);
      for (String oc : ocs)
      {
        ObjectClass objectClass = schema.getObjectClass(oc);
        if ((objectClass != null) && (attr != null))
        {
          if (objectClass.isRequiredOrOptional(attr))
          {
            hasUserPassword = true;
            break;
          }
        }
      }
    }
    return hasUserPassword;
  }

  /**
   * Updates the menus with the provided server descriptor.
   * @param desc the server descriptor.
   */
  private void updateMenus(ServerDescriptor desc)
  {
    menuBar.newEntryFromLDIFMenuItem.setEnabled(desc.isAuthenticated());
  }

  /**
   * Updates the contents of the right pane with the selected tree paths.
   * @param paths the selected tree paths.
   */
  private void updateRightPane(TreePath[] paths)
  {
    TreePath path = null;
    if ((paths != null) && (paths.length == 1))
    {
      path = paths[0];
    }
    BasicNode node = null;
    if (path != null)
    {
      node = (BasicNode)path.getLastPathComponent();
    }
    if (node != null)
    {
      String dn = node.getDN();
      try
      {
        InitialLdapContext ctx =
          controller.findConnectionForDisplayedEntry(node);
        Schema schema = getInfo().getServerDescriptor().getSchema();
        LDAPEntryReader reader = new LDAPEntryReader(dn, ctx, schema);
        reader.addEntryReadListener(entryPane);
        cleanupReaderQueue();
        // Required to update the browser controller properly if the entry is
        // deleted.
        entryPane.setTreePath(path);
        reader.startBackgroundTask();
        entryReaderQueue.add(reader);
      }
      catch (Throwable t)
      {
        EntryReadErrorEvent ev = new EntryReadErrorEvent(this, dn, t);
        entryPane.entryReadError(ev);
      }
    }
    else
    {
      cleanupReaderQueue();
      if ((paths != null) && (paths.length > 1))
      {
        entryPane.multipleEntriesSelected();
      }
      else
      {
        entryPane.noEntrySelected();
      }
    }
  }

  /**
   * Cleans up the reader queue (the queue where we store the entries that we
   * must read).
   *
   */
  private void cleanupReaderQueue()
  {
    ArrayList<LDAPEntryReader> toRemove = new ArrayList<LDAPEntryReader>();
    for (LDAPEntryReader r : entryReaderQueue)
    {
      if (r.isOver())
      {
        toRemove.add(r);
      }
      else
      {
        r.interrupt();
      }
    }
    entryReaderQueue.removeAll(toRemove);
  }

  /**
   * Adds a pop up menu to the tree.
   *
   */
  private void addPopupMenu()
  {
    popup = new JPopupMenu();

    popupNewUserMenuItem = Utilities.createMenuItem(
        INFO_CTRL_PANEL_NEW_USER_MENU.get());
    popupNewUserMenuItem.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        newUser();
      }
    });
    popupNewUserMenuItem.setEnabled(false);
    popup.add(popupNewUserMenuItem);

    popupNewGroupMenuItem = Utilities.createMenuItem(
        INFO_CTRL_PANEL_NEW_GROUP_MENU.get());
    popupNewGroupMenuItem.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        newGroup();
      }
    });
    popupNewGroupMenuItem.setEnabled(false);
    popup.add(popupNewGroupMenuItem);

    popupNewOUMenuItem = Utilities.createMenuItem(
        INFO_CTRL_PANEL_NEW_ORGANIZATIONAL_UNIT_MENU.get());
    popupNewOUMenuItem.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        newOrganizationalUnit();
      }
    });
    popupNewOUMenuItem.setEnabled(false);
    popup.add(popupNewOUMenuItem);

    popupNewOrganizationMenuItem = Utilities.createMenuItem(
        INFO_CTRL_PANEL_NEW_ORGANIZATION_MENU.get());
    popupNewOrganizationMenuItem.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        newOrganization();
      }
    });
    popupNewOrganizationMenuItem.setEnabled(false);
    popup.add(popupNewOrganizationMenuItem);

    popupNewDomainMenuItem = Utilities.createMenuItem(
        INFO_CTRL_PANEL_NEW_DOMAIN_MENU.get());
    popupNewDomainMenuItem.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        newDomain();
      }
    });
    popupNewDomainMenuItem.setEnabled(false);
    popup.add(popupNewDomainMenuItem);

    popupNewEntryFromLDIFMenuItem = Utilities.createMenuItem(
        INFO_CTRL_PANEL_NEW_FROM_LDIF_MENU.get());
    popupNewEntryFromLDIFMenuItem.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        newEntryFromLDIF();
      }
    });
    popup.add(popupNewEntryFromLDIFMenuItem);

    popup.add(new JSeparator());
    popupResetUserPasswordMenuItem = Utilities.createMenuItem(
        INFO_CTRL_PANEL_RESET_USER_PASSWORD_MENU.get());
    popupResetUserPasswordMenuItem.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        resetUserPassword();
      }
    });

    popup.add(popupResetUserPasswordMenuItem);
    popupResetUserPasswordMenuItem.setEnabled(false);

    popupAddToGroupMenuItem = Utilities.createMenuItem(
        INFO_CTRL_PANEL_ADD_TO_GROUP_MENU.get());
    popupAddToGroupMenuItem.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        addToGroup();
      }
    });
    popup.add(popupAddToGroupMenuItem);
    popupAddToGroupMenuItem.setEnabled(false);

    popupCopyDNMenuItem = Utilities.createMenuItem(
        INFO_CTRL_PANEL_COPY_DN_MENU.get());
    popupCopyDNMenuItem.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        copyDN();
      }
    });
    popup.add(new JSeparator());
    popup.add(popupCopyDNMenuItem);
    popupCopyDNMenuItem.setEnabled(false);
    popup.add(new JSeparator());

    popupDeleteMenuItem = Utilities.createMenuItem(
        INFO_CTRL_PANEL_DELETE_ENTRY_MENU.get());
    popupDeleteMenuItem.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        deleteClicked();
      }
    });
    popup.add(popupDeleteMenuItem);
    popupDeleteMenuItem.setEnabled(false);

    popup.setOpaque(true);

    ((CustomTree)treePane.getTree()).setPopupMenu(popup);
  }

  private void resetUserPassword()
  {
    if (resetUserPasswordDlg == null)
    {
      resetUserPasswordPanel = new ResetUserPasswordPanel();
      resetUserPasswordPanel.setInfo(getInfo());
      resetUserPasswordDlg = new GenericDialog(Utilities.getFrame(this),
          resetUserPasswordPanel);
      Utilities.centerGoldenMean(resetUserPasswordDlg,
          Utilities.getParentDialog(this));
    }
    TreePath[] paths = treePane.getTree().getSelectionPaths();
    if ((paths != null) && (paths.length == 1))
    {
      TreePath path = paths[0];
      BasicNode node = (BasicNode)path.getLastPathComponent();
      resetUserPasswordPanel.setValue(node, controller);
      resetUserPasswordDlg.setVisible(true);
    }
  }

  private void deleteBaseDN()
  {
    if (deleteBaseDNDlg == null)
    {
      DeleteBaseDNPanel panel = new DeleteBaseDNPanel();
      panel.setInfo(getInfo());
      deleteBaseDNDlg = new GenericDialog(Utilities.getFrame(this), panel);
      Utilities.centerGoldenMean(deleteBaseDNDlg,
          Utilities.getParentDialog(this));
    }
    deleteBaseDNDlg.setVisible(true);
  }

  private void deleteBackend()
  {
    if (deleteBackendDlg == null)
    {
      DeleteBackendPanel panel = new DeleteBackendPanel();
      panel.setInfo(getInfo());
      deleteBackendDlg = new GenericDialog(Utilities.getFrame(this), panel);
      Utilities.centerGoldenMean(deleteBackendDlg,
          Utilities.getParentDialog(this));
    }
    deleteBackendDlg.setVisible(true);
  }

  private void newUser()
  {
    if (newUserDlg == null)
    {
      newUserPanel = new NewUserPanel();
      newUserPanel.setInfo(getInfo());
      newUserDlg = new GenericDialog(Utilities.getFrame(this), newUserPanel);
      Utilities.centerGoldenMean(newUserDlg,
          Utilities.getParentDialog(this));
    }
    TreePath[] paths = treePane.getTree().getSelectionPaths();
    BasicNode parentNode = null;
    if ((paths != null) && (paths.length == 1))
    {
      TreePath path = paths[0];
      parentNode = (BasicNode)path.getLastPathComponent();
    }
    newUserPanel.setParent(parentNode, controller);
    newUserDlg.setVisible(true);
  }

  private void newGroup()
  {
    if (newGroupDlg == null)
    {
      newGroupPanel = new NewGroupPanel();
      newGroupPanel.setInfo(getInfo());
      /* First argument:  Component to associate the target with
       * Second argument: DropTargetListener
       */
      newGroupDlg = new GenericDialog(Utilities.getFrame(this), newGroupPanel);
      Utilities.centerGoldenMean(newGroupDlg,
          Utilities.getParentDialog(this));
    }
    TreePath[] paths = treePane.getTree().getSelectionPaths();
    BasicNode parentNode = null;
    if ((paths != null) && (paths.length == 1))
    {
      TreePath path = paths[0];
      parentNode = (BasicNode)path.getLastPathComponent();
    }
    newGroupPanel.setParent(parentNode, controller);
    newGroupDlg.setVisible(true);
  }

  private void newOrganizationalUnit()
  {
    if (newOUDlg == null)
    {
      newOUPanel = new NewOrganizationalUnitPanel();
      newOUPanel.setInfo(getInfo());
      newOUDlg = new GenericDialog(Utilities.getFrame(this), newOUPanel);
      Utilities.centerGoldenMean(newOUDlg,
          Utilities.getParentDialog(this));
    }
    TreePath[] paths = treePane.getTree().getSelectionPaths();
    BasicNode parentNode = null;
    if ((paths != null) && (paths.length == 1))
    {
      TreePath path = paths[0];
      parentNode = (BasicNode)path.getLastPathComponent();
    }
    newOUPanel.setParent(parentNode, controller);
    newOUDlg.setVisible(true);
  }

  private void newOrganization()
  {
    if (newOrganizationDlg == null)
    {
      newOrganizationPanel = new NewOrganizationPanel();
      newOrganizationPanel.setInfo(getInfo());
      newOrganizationDlg = new GenericDialog(Utilities.getFrame(this),
          newOrganizationPanel);
      Utilities.centerGoldenMean(newOrganizationDlg,
          Utilities.getParentDialog(this));
    }
    TreePath[] paths = treePane.getTree().getSelectionPaths();
    BasicNode parentNode = null;
    if ((paths != null) && (paths.length == 1))
    {
      TreePath path = paths[0];
      parentNode = (BasicNode)path.getLastPathComponent();
    }
    newOrganizationPanel.setParent(parentNode, controller);
    newOrganizationDlg.setVisible(true);
  }

  private void newDomain()
  {
    if (newDomainDlg == null)
    {
      newDomainPanel = new NewDomainPanel();
      newDomainPanel.setInfo(getInfo());
      newDomainDlg =
        new GenericDialog(Utilities.getFrame(this), newDomainPanel);
      Utilities.centerGoldenMean(newDomainDlg,
          Utilities.getParentDialog(this));
    }
    TreePath[] paths = treePane.getTree().getSelectionPaths();
    BasicNode parentNode = null;
    if ((paths != null) && (paths.length == 1))
    {
      TreePath path = paths[0];
      parentNode = (BasicNode)path.getLastPathComponent();
    }
    newDomainPanel.setParent(parentNode, controller);
    newDomainDlg.setVisible(true);
  }

  private void newEntryFromLDIF()
  {
    if (newEntryFromLDIFDlg == null)
    {
      newEntryFromLDIFPanel = new NewEntryFromLDIFPanel();
      newEntryFromLDIFPanel.setInfo(getInfo());
      newEntryFromLDIFDlg = new GenericDialog(Utilities.getFrame(this),
          newEntryFromLDIFPanel);
      Utilities.centerGoldenMean(newEntryFromLDIFDlg,
          Utilities.getParentDialog(this));
    }
    TreePath[] paths = treePane.getTree().getSelectionPaths();
    BasicNode parentNode = null;
    if ((paths != null) && (paths.length == 1))
    {
      TreePath path = paths[0];
      parentNode = (BasicNode)path.getLastPathComponent();
    }
    newEntryFromLDIFPanel.setParent(parentNode, controller);
    newEntryFromLDIFDlg.setVisible(true);
  }

  private void deleteClicked()
  {
    ArrayList<Message> errors = new ArrayList<Message>();
    TreePath[] paths = treePane.getTree().getSelectionPaths();

    if ((paths != null) && (paths.length > 0))
    {
      ProgressDialog dlg = new ProgressDialog(
          Utilities.getParentDialog(this),
          INFO_CTRL_PANEL_DELETE_SELECTED_ENTRIES_TITLE.get(), getInfo());
      DeleteEntryTask newTask = new DeleteEntryTask(getInfo(), dlg, paths,
          controller);
      for (Task task : getInfo().getTasks())
      {
        task.canLaunch(newTask, errors);
      }
      if (errors.size() == 0)
      {
        if (displayConfirmationDialog(
            INFO_CTRL_PANEL_CONFIRMATION_REQUIRED_SUMMARY.get(),
            INFO_CTRL_PANEL_DELETE_ENTRIES_CONFIRMATION_DETAILS.get()))
        {
          launchOperation(newTask,
              INFO_CTRL_PANEL_DELETING_ENTRIES_SUMMARY.get(),
              INFO_CTRL_PANEL_DELETING_ENTRIES_COMPLETE.get(),
              INFO_CTRL_PANEL_DELETING_ENTRIES_SUCCESSFUL.get(),
              ERR_CTRL_PANEL_DELETING_ENTRIES_ERROR_SUMMARY.get(),
              ERR_CTRL_PANEL_DELETING_ENTRIES_ERROR_DETAILS.get(),
              null,
              dlg);
          dlg.setVisible(true);
        }
      }
    }
  }

  private void copyDN()
  {
    ClipboardOwner owner = new ClipboardOwner()
    {
      /**
       * {@inheritDoc}
       */
      public void lostOwnership( Clipboard aClipboard,
          Transferable aContents) {
        //do nothing
      }
    };
    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    TreePath[] paths = treePane.getTree().getSelectionPaths();
    if (paths != null)
    {
      StringBuilder sb = new StringBuilder();
      for (TreePath path : paths)
      {
        BasicNode node = (BasicNode)path.getLastPathComponent();
        if (sb.length() > 0)
        {
          sb.append("\n");
        }
        sb.append(node.getDN());
      }
      StringSelection stringSelection = new StringSelection(sb.toString());
      clipboard.setContents(stringSelection, owner);
    }
  }

  private void addToGroup()
  {
    TreePath[] paths = treePane.getTree().getSelectionPaths();
    if (paths != null)
    {
      LinkedHashSet<DN> dns = new LinkedHashSet<DN>();
      for (TreePath path : paths)
      {
        BasicNode node = (BasicNode)path.getLastPathComponent();
        try
        {
          dns.add(DN.decode(node.getDN()));
        }
        catch (OpenDsException ode)
        {
          throw new IllegalStateException(
              "Unexpected error decoding dn. Details: "+ode.getMessageObject(),
              ode);
        }
      }
      if (addToGroupDlg == null)
      {
        addToGroupPanel = new AddToGroupPanel();
        addToGroupPanel.setInfo(getInfo());
        addToGroupDlg = new GenericDialog(Utilities.getFrame(this),
            addToGroupPanel);
        Utilities.centerGoldenMean(addToGroupDlg,
            Utilities.getParentDialog(this));
      }
      addToGroupPanel.setEntriesToAdd(dns);
      addToGroupDlg.setVisible(true);
    }
  }

  private void newWindow()
  {
    BrowseEntriesPanel panel = new BrowseEntriesPanel();
    panel.setDisposeOnClose(true);
    panel.setInfo(getInfo());
    GenericDialog dlg = new GenericDialog(Utilities.getFrame(this),
        panel);

    Utilities.centerGoldenMean(dlg, Utilities.getFrame(this));

    dlg.setVisible(true);
  }

  /**
   * The specific menu bar of this panel.
   *
   */
  class BrowseMenuBar extends GenericMenuBar
  {
    private static final long serialVersionUID = 505187832236882370L;
    JMenuItem deleteMenuItem;
    JMenuItem copyDNMenuItem;
    JMenuItem addToGroupMenuItem;
    JMenuItem resetPasswordMenuItem;
    JMenuItem newUserMenuItem;
    JMenuItem newGroupMenuItem;
    JMenuItem newOUMenuItem;
    JMenuItem newOrganizationMenuItem;
    JMenuItem newDomainMenuItem;
    JMenuItem newEntryFromLDIFMenuItem;

    /**
     * Constructor.
     * @param info the control panel info.
     */
    public BrowseMenuBar(ControlPanelInfo info)
    {
      super(info);
      add(createFileMenuBar());
      add(createEntriesMenuBar());
      add(createViewMenuBar());
      add(createHelpMenuBar());
    }

    /**
     * Creates the file menu bar.
     * @return the file menu bar.
     */
    private JMenu createFileMenuBar()
    {
      JMenu menu = Utilities.createMenu(INFO_CTRL_PANEL_FILE_MENU.get(),
          INFO_CTRL_PANEL_FILE_MENU_DESCRIPTION.get());
      menu.setMnemonic(KeyEvent.VK_F);
      JMenuItem newWindow = Utilities.createMenuItem(
          INFO_CTRL_PANEL_NEW_BROWSER_WINDOW_MENU.get());
      newWindow.addActionListener(new ActionListener()
      {
        /**
         * {@inheritDoc}
         */
        public void actionPerformed(ActionEvent ev)
        {
          newWindow();
        }
      });
      menu.add(newWindow);
      menu.add(new JSeparator());
      JMenuItem close = Utilities.createMenuItem(
          INFO_CTRL_PANEL_CLOSE_MENU.get());
      close.addActionListener(new ActionListener()
      {
        /**
         * {@inheritDoc}
         */
        public void actionPerformed(ActionEvent ev)
        {
          closeClicked();
        }
      });
      menu.add(close);
      return menu;
    }

    /**
     * Creates the view menu bar.
     * @return the view menu bar.
     */
    protected JMenu createViewMenuBar()
    {
      JMenu menu = Utilities.createMenu(
          INFO_CTRL_PANEL_VIEW_MENU.get(),
          INFO_CTRL_PANEL_VIEW_MENU_DESCRIPTION.get());
      menu.setMnemonic(KeyEvent.VK_V);
      Message[] labels = {
          INFO_CTRL_PANEL_SIMPLIFIED_VIEW_MENU.get(),
          INFO_CTRL_PANEL_ATTRIBUTE_VIEW_MENU.get(),
          INFO_CTRL_PANEL_LDIF_VIEW_MENU.get()
      };
      final LDAPEntryPanel.View[] views = {
          LDAPEntryPanel.View.SIMPLIFIED_VIEW,
          LDAPEntryPanel.View.ATTRIBUTE_VIEW,
          LDAPEntryPanel.View.LDIF_VIEW
      };
      final JRadioButtonMenuItem[] menus =
        new JRadioButtonMenuItem[labels.length];
      ButtonGroup group = new ButtonGroup();
      for (int i=0; i<labels.length; i++)
      {
        menus[i] = new JRadioButtonMenuItem(labels[i].toString());
        menu.add(menus[i]);
        group.add(menus[i]);
      }
      ActionListener listener = new ActionListener()
      {
        private boolean ignoreEvents;
        private JRadioButtonMenuItem lastSelected = menus[0];
        /**
         * {@inheritDoc}
         */
        public void actionPerformed(ActionEvent ev)
        {
          if (ignoreEvents)
          {
            return;
          }
          for (int i=0; i<menus.length; i++)
          {
            if (menus[i].isSelected())
            {
              ignoreEvents = true;
              lastSelected.setSelected(true);
              if (entryPane.mustCheckUnsavedChanges())
              {
                switch (entryPane.checkUnsavedChanges())
                {
                case DO_NOT_SAVE:
                  break;
                case SAVE:
                  break;
                case CANCEL:
                  ignoreEvents = false;
                  return;
                }
              }
              lastSelected = menus[i];
              menus[i].setSelected(true);
              entryPane.setView(views[i]);
              ignoreEvents = false;
              break;
            }
          }
        }
      };
      for (int i=0; i<labels.length; i++)
      {
        menus[i].addActionListener(listener);
      }
      menus[0].setSelected(true);
      return menu;
    }

    /**
     * Creates the entries menu bar.
     * @return the entries menu bar.
     */
    protected JMenu createEntriesMenuBar()
    {
      JMenu menu = Utilities.createMenu(
          INFO_CTRL_PANEL_ENTRIES_MENU.get(),
          INFO_CTRL_PANEL_ENTRIES_MENU_DESCRIPTION.get());
      menu.setMnemonic(KeyEvent.VK_E);

      newUserMenuItem = Utilities.createMenuItem(
          INFO_CTRL_PANEL_NEW_USER_MENU.get());
      newUserMenuItem.addActionListener(new ActionListener()
      {
        /**
         * {@inheritDoc}
         */
        public void actionPerformed(ActionEvent ev)
        {
          newUser();
        }
      });
      newUserMenuItem.setEnabled(false);
      menu.add(newUserMenuItem);

      newGroupMenuItem = Utilities.createMenuItem(
          INFO_CTRL_PANEL_NEW_GROUP_MENU.get());
      newGroupMenuItem.addActionListener(new ActionListener()
      {
        /**
         * {@inheritDoc}
         */
        public void actionPerformed(ActionEvent ev)
        {
          newGroup();
        }
      });
      newGroupMenuItem.setEnabled(false);
      menu.add(newGroupMenuItem);

      newOUMenuItem = Utilities.createMenuItem(
          INFO_CTRL_PANEL_NEW_ORGANIZATIONAL_UNIT_MENU.get());
      newOUMenuItem.addActionListener(new ActionListener()
      {
        /**
         * {@inheritDoc}
         */
        public void actionPerformed(ActionEvent ev)
        {
          newOrganizationalUnit();
        }
      });
      newOUMenuItem.setEnabled(false);
      menu.add(newOUMenuItem);

      newOrganizationMenuItem = Utilities.createMenuItem(
          INFO_CTRL_PANEL_NEW_ORGANIZATION_MENU.get());
      newOrganizationMenuItem.addActionListener(new ActionListener()
      {
        /**
         * {@inheritDoc}
         */
        public void actionPerformed(ActionEvent ev)
        {
          newOrganization();
        }
      });
      newOrganizationMenuItem.setEnabled(false);
      menu.add(newOrganizationMenuItem);

      newDomainMenuItem = Utilities.createMenuItem(
          INFO_CTRL_PANEL_NEW_DOMAIN_MENU.get());
      newDomainMenuItem.addActionListener(new ActionListener()
      {
        /**
         * {@inheritDoc}
         */
        public void actionPerformed(ActionEvent ev)
        {
          newDomain();
        }
      });
      newDomainMenuItem.setEnabled(false);
      menu.add(newDomainMenuItem);

      newEntryFromLDIFMenuItem = Utilities.createMenuItem(
          INFO_CTRL_PANEL_NEW_FROM_LDIF_MENU.get());
      newEntryFromLDIFMenuItem.addActionListener(new ActionListener()
      {
        /**
         * {@inheritDoc}
         */
        public void actionPerformed(ActionEvent ev)
        {
          newEntryFromLDIF();
        }
      });
      menu.add(newEntryFromLDIFMenuItem);
      menu.add(new JSeparator());
      resetPasswordMenuItem = Utilities.createMenuItem(
          INFO_CTRL_PANEL_RESET_USER_PASSWORD_MENU.get());
      resetPasswordMenuItem.addActionListener(new ActionListener()
      {
        /**
         * {@inheritDoc}
         */
        public void actionPerformed(ActionEvent ev)
        {
          resetUserPassword();
        }
      });
      resetPasswordMenuItem.setEnabled(false);
      menu.add(resetPasswordMenuItem);

      addToGroupMenuItem = Utilities.createMenuItem(
          INFO_CTRL_PANEL_ADD_TO_GROUP_MENU.get());
      addToGroupMenuItem.addActionListener(new ActionListener()
      {
        /**
         * {@inheritDoc}
         */
        public void actionPerformed(ActionEvent ev)
        {
          addToGroup();
        }
      });
      addToGroupMenuItem.setEnabled(false);
      menu.add(addToGroupMenuItem);

      menu.add(new JSeparator());
      copyDNMenuItem = Utilities.createMenuItem(
          INFO_CTRL_PANEL_COPY_DN_MENU.get());
      copyDNMenuItem.addActionListener(new ActionListener()
      {
        /**
         * {@inheritDoc}
         */
        public void actionPerformed(ActionEvent ev)
        {
          copyDN();
        }
      });
      copyDNMenuItem.setEnabled(false);
      menu.add(copyDNMenuItem);
      menu.add(new JSeparator());
      deleteMenuItem = Utilities.createMenuItem(
          INFO_CTRL_PANEL_DELETE_ENTRY_MENU.get());
      deleteMenuItem.addActionListener(new ActionListener()
      {
        /**
         * {@inheritDoc}
         */
        public void actionPerformed(ActionEvent ev)
        {
          deleteClicked();
        }
      });
      deleteMenuItem.setEnabled(false);
      menu.add(deleteMenuItem);
      menu.add(new JSeparator());
      JMenuItem deleteBaseDNMenuItem = Utilities.createMenuItem(
          INFO_CTRL_PANEL_DELETE_BASE_DN_MENU.get());
      deleteBaseDNMenuItem.addActionListener(new ActionListener()
      {
        /**
         * {@inheritDoc}
         */
        public void actionPerformed(ActionEvent ev)
        {
          deleteBaseDN();
        }
      });
      menu.add(deleteBaseDNMenuItem);

      JMenuItem deleteBackendMenuItem = Utilities.createMenuItem(
          INFO_CTRL_PANEL_DELETE_BACKEND_MENU.get());
      deleteBackendMenuItem.addActionListener(new ActionListener()
      {
        /**
         * {@inheritDoc}
         */
        public void actionPerformed(ActionEvent ev)
        {
          deleteBackend();
        }
      });
      menu.add(deleteBackendMenuItem);
      return menu;
    }
  }
}
