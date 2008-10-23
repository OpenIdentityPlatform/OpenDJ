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

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.event.ScrollPaneBorderListener;
import org.opends.guitools.controlpanel.task.DeleteSchemaElementsTask;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.types.AttributeType;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.Schema;

/**
 * The panel that displays a custom object class definition.
 *
 */
public class CustomObjectClassPanel extends StandardObjectClassPanel
{
  private static final long serialVersionUID = 2105520588901380L;
  private JButton delete;
  private ObjectClass objectClass;
  private String ocName;
  private ScrollPaneBorderListener scrollListener;

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_CUSTOM_OBJECTCLASS_TITLE.get();
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
  protected void createLayout()
  {
    JPanel p = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    p.setOpaque(false);
    p.setBorder(PANEL_BORDER);
    super.createBasicLayout(p, gbc);
    gbc = new GridBagConstraints();
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridx = 0;
    gbc.gridy = 0;
    JScrollPane scroll = Utilities.createBorderLessScrollBar(p);
    scrollListener = new ScrollPaneBorderListener(scroll);
    add(scroll, gbc);

    gbc.gridy ++;
    gbc.weighty = 0.0;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.NONE;
    gbc.insets = new Insets(10, 10, 10, 10);
    delete = Utilities.createButton(
        INFO_CTRL_PANEL_DELETE_OBJECTCLASS_BUTTON.get());
    delete.setOpaque(false);
    add(delete, gbc);
    delete.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        deleteObjectclass();
      }
    });
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(final ConfigurationChangeEvent ev)
  {
    updateErrorPaneIfAuthRequired(ev.getNewDescriptor(),
        INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_FOR_OBJECTCLASS_DELETE.get());
    SwingUtilities.invokeLater(new Runnable()
    {
      /**
       * {@inheritDoc}
       */
      public void run()
      {
        delete.setEnabled(!authenticationRequired(ev.getNewDescriptor()));
      }
    });
  }

  /**
   * {@inheritDoc}
   */
  public void update(ObjectClass oc, Schema schema)
  {
    super.update(oc, schema);
    objectClass = oc;
    if (objectClass != null)
    {
      ocName = objectClass.getNameOrOID();
    }
    scrollListener.updateBorder();
  }

  private void deleteObjectclass()
  {
    ArrayList<Message> errors = new ArrayList<Message>();
    ProgressDialog dlg = new ProgressDialog(
        Utilities.getParentDialog(this),
        INFO_CTRL_PANEL_DELETE_OBJECTCLASS_TITLE.get(), getInfo());
    ArrayList<ObjectClass> ocsToDelete = new ArrayList<ObjectClass>();
    ocsToDelete.add(objectClass);
    ArrayList<AttributeType> attrsToDelete = new ArrayList<AttributeType>();

    DeleteSchemaElementsTask newTask = new DeleteSchemaElementsTask(getInfo(),
        dlg, ocsToDelete, attrsToDelete);
    for (Task task : getInfo().getTasks())
    {
      task.canLaunch(newTask, errors);
    }
    Schema schema = getInfo().getServerDescriptor().getSchema();
    if (schema != null)
    {
      ArrayList<String> childClasses = new ArrayList<String>();
      for (ObjectClass o : schema.getObjectClasses().values())
      {
        if (objectClass.equals(o.getSuperiorClass()))
        {
          childClasses.add(o.getNameOrOID());
        }
      }
      if (!childClasses.isEmpty())
      {
        errors.add(ERR_CANNOT_DELETE_PARENT_OBJECTCLASS.get(ocName,
            Utilities.getStringFromCollection(childClasses, ", "), ocName));
      }
    }
    if (errors.size() == 0)
    {
      Message confirmationMessage =
        INFO_CTRL_PANEL_CONFIRMATION_DELETE_OBJECTCLASS_DETAILS.get(
            ocName);
      if (displayConfirmationDialog(
          INFO_CTRL_PANEL_CONFIRMATION_REQUIRED_SUMMARY.get(),
          confirmationMessage))
      {
        launchOperation(newTask,
            INFO_CTRL_PANEL_DELETING_OBJECTCLASS_SUMMARY.get(ocName),
            INFO_CTRL_PANEL_DELETING_OBJECTCLASS_COMPLETE.get(),
            INFO_CTRL_PANEL_DELETING_OBJECTCLASS_SUCCESSFUL.get(ocName),
            ERR_CTRL_PANEL_DELETING_OBJECTCLASS_ERROR_SUMMARY.get(),
            ERR_CTRL_PANEL_DELETING_OBJECTCLASS_ERROR_DETAILS.get(ocName),
            null,
            dlg);
        dlg.setVisible(true);
      }
    }
    else
    {
      displayErrorDialog(errors);
    }
  }
}
