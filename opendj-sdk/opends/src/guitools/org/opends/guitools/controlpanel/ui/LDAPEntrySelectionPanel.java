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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.opends.guitools.controlpanel.ui.nodes.BasicNode;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;

/**
 * A basic panel that contains a browser.  It is used in general in panels that
 * require to provide some DNs of existing entries: we allow the user to launch
 * a browser to select entries.
 *
 */
public class LDAPEntrySelectionPanel extends AbstractBrowseEntriesPanel
{
  private Message title;
  private Filter f;

  private String[] dns;

  /**
   * The values of the filters that will be used when opening the dialog where
   * this panel is contained.  For instance if the filter is set to Filter.USERS
   * the panel will display only users when the dialog appears.
   *
   */
  public enum Filter
  {
    /**
     * Display users.
     */
    USERS,
    /**
     * Display groups.
     */
    GROUPS,
    /**
     * Display Dynamic Groups.
     */
    DYNAMIC_GROUPS,
    /**
     * Display Static Groups.
     */
    STATIC_GROUPS,
    /**
     * Default filter (all entries).
     */
    DEFAULT
  }

  private static final long serialVersionUID = -8140540064410029902L;

  /**
   * Default constructor.
   *
   */
  public LDAPEntrySelectionPanel()
  {
    super();
  }

  /**
   * Updates the tree selection model to allow multiple selection or not.
   * @param multiple whether the tree should allow multiple selection or not.
   */
  public void setMultipleSelection(boolean multiple)
  {
    treePane.getTree().getSelectionModel().setSelectionMode(multiple ?
        TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION :
          TreeSelectionModel.SINGLE_TREE_SELECTION);
  }

  /**
   * {@inheritDoc}
   */
  public void toBeDisplayed(boolean visible)
  {
    super.toBeDisplayed(visible);
    if (visible)
    {
      dns = new String[]{};
    }
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return title;
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    dns = retrieveDNs();
    super.closeClicked();
  }

  /**
   * Returns the selected DN's in an array of Strings.  The array is never
   * <CODE>null</CODE> but might be empty.
   * @return the selected DN's.
   */
  public String[] getDNs()
  {
    return dns;
  }

  private String[] retrieveDNs()
  {
    String[] dns;
    TreePath[] paths = treePane.getTree().getSelectionPaths();
    if (paths != null)
    {
      dns = new String[paths.length];
      for (int i=0; i<paths.length; i++)
      {
        dns[i] = ((BasicNode)paths[i].getLastPathComponent()).getDN();
      }
    }
    else
    {
      dns = new String[]{};
    }
    return dns;
  }

  /**
   * {@inheritDoc}
   */
  public GenericDialog.ButtonType getBrowseButtonType()
  {
    return GenericDialog.ButtonType.OK_CANCEL;
  }

  /**
   * {@inheritDoc}
   */
  protected Component createMainPanel()
  {
    JComponent p = createTreePane();

    final JTree tree = treePane.getTree();
    tree.getSelectionModel().addTreeSelectionListener(
    new TreeSelectionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void valueChanged(TreeSelectionEvent ev)
      {
        TreePath[] paths = tree.getSelectionPaths();
        setEnabledOK((paths != null) && (paths.length > 0));
      }
    });
    MouseListener mouseListener = new MouseAdapter() {
      /**
       * {@inheritDoc}
       */
      public void mousePressed(MouseEvent e) {
        int selRow = tree.getRowForLocation(e.getX(), e.getY());
        if ((selRow != -1) && (e.getClickCount() == 2))
        {
          okClicked();
        }
      }
    };
    tree.addMouseListener(mouseListener);

    JScrollPane treeScroll = Utilities.createScrollPane(p);
    treeScroll.setPreferredSize(
        new Dimension(treeScroll.getPreferredSize().width + 30,
            4 * treeScroll.getPreferredSize().height));

    return treeScroll;
  }

  /**
   * Returns the last filter set with the setFilter method.
   * @return the last filter set with the setFilter method.
   */
  public Filter getFilter()
  {
    return f;
  }

  /**
   * Sets the filter to be used when the panel is displayed.
   * @param filter the filter.
   */
  public void setFilter(Filter filter)
  {
    f = filter;
    switch (f)
    {
    case USERS:
      filterAttribute.setSelectedItem(USER_FILTER);
      super.filter.setText("*");
      break;
    case GROUPS:
      filterAttribute.setSelectedItem(GROUP_FILTER);
      super.filter.setText("*");
      break;
    case DYNAMIC_GROUPS:
      filterAttribute.setSelectedItem(LDAP_FILTER);
      super.filter.setText("objectClass=groupOfURLs");
      break;
    case STATIC_GROUPS:
      filterAttribute.setSelectedItem(LDAP_FILTER);
      super.filter.setText("objectClass=groupOfUniqueNames");
      break;
    case DEFAULT:
      Object o = filterAttribute.getItemAt(0);
      filterAttribute.setSelectedItem(o);
      super.filter.setText("");
      break;
    }
    if (controller != null)
    {
      applyButtonClicked();
    }
  }

  /**
   * Sets the title that will be displayed in the dialog containing this panel.
   * @param title the title.
   */
  public void setTitle(Message title)
  {
    this.title = title;
    if (Utilities.getParentDialog(this) instanceof GenericDialog)
    {
      ((GenericDialog)Utilities.getParentDialog(this)).updateTitle();
    }
  }
}

