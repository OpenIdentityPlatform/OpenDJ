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
 * The panel that displays a custom attribute definition.
 *
 */
public class CustomAttributePanel extends StandardAttributePanel
{
  private static final long serialVersionUID = 2850763193735843746L;
  private JButton delete;
  private AttributeType attribute;
  private String attrName;
  private ScrollPaneBorderListener scrollListener;

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_CUSTOM_ATTRIBUTE_TITLE.get();
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
  protected void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    JPanel p = new JPanel(new GridBagLayout());
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
    scrollListener =
      ScrollPaneBorderListener.createBottomBorderListener(scroll);
    add(scroll, gbc);

    gbc.gridy ++;
    gbc.weighty = 0.0;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.NONE;
    gbc.insets = new Insets(10, 10, 10, 10);
    delete = Utilities.createButton(
        INFO_CTRL_PANEL_DELETE_ATTRIBUTE_BUTTON.get());
    delete.setOpaque(false);
    add(delete, gbc);
    delete.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        deleteAttribute();
      }
    });
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
  public void update(AttributeType attr, Schema schema)
  {
    super.update(attr, schema);
    attribute = attr;
    attrName = attribute.getNameOrOID();

    scrollListener.updateBorder();
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(final ConfigurationChangeEvent ev)
  {
    updateErrorPaneIfAuthRequired(ev.getNewDescriptor(),
        INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_FOR_ATTRIBUTE_DELETE.get());
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

  private void deleteAttribute()
  {
    ArrayList<Message> errors = new ArrayList<Message>();
    ProgressDialog dlg = new ProgressDialog(
        Utilities.getParentDialog(this),
        INFO_CTRL_PANEL_DELETE_ATTRIBUTE_TITLE.get(), getInfo());
    ArrayList<ObjectClass> ocsToDelete = new ArrayList<ObjectClass>();
    ArrayList<AttributeType> attrsToDelete = new ArrayList<AttributeType>();
    attrsToDelete.add(attribute);

    DeleteSchemaElementsTask newTask = new DeleteSchemaElementsTask(getInfo(),
        dlg, ocsToDelete, attrsToDelete);
    for (Task task : getInfo().getTasks())
    {
      task.canLaunch(newTask, errors);
    }
    Schema schema = getInfo().getServerDescriptor().getSchema();
    if (schema != null)
    {
      ArrayList<String> childAttributes = new ArrayList<String>();
      for (AttributeType attr : schema.getAttributeTypes().values())
      {
        if (attribute.equals(attr.getSuperiorType()))
        {
          childAttributes.add(attr.getNameOrOID());
        }
      }
      if (!childAttributes.isEmpty())
      {
        errors.add(ERR_CANNOT_DELETE_PARENT_ATTRIBUTE.get(attrName,
            Utilities.getStringFromCollection(childAttributes, ", "),
            attrName));
      }

      ArrayList<String> dependentClasses = new ArrayList<String>();
      for (ObjectClass o : schema.getObjectClasses().values())
      {
        if (o.getRequiredAttributeChain().contains(attribute))
        {
          dependentClasses.add(o.getNameOrOID());
        }
      }
      if (!dependentClasses.isEmpty())
      {
        errors.add(ERR_CANNOT_DELETE_ATTRIBUTE_WITH_DEPENDENCIES.get(
            attrName,
            Utilities.getStringFromCollection(dependentClasses, ", "),
            attrName));
      }
    }
    if (errors.size() == 0)
    {
      Message confirmationMessage =
        INFO_CTRL_PANEL_CONFIRMATION_DELETE_ATTRIBUTE_DETAILS.get(
            attribute.getNameOrOID());
      if (displayConfirmationDialog(
          INFO_CTRL_PANEL_CONFIRMATION_REQUIRED_SUMMARY.get(),
          confirmationMessage))
      {
        launchOperation(newTask,
            INFO_CTRL_PANEL_DELETING_ATTRIBUTE_SUMMARY.get(attrName),
            INFO_CTRL_PANEL_DELETING_ATTRIBUTE_COMPLETE.get(),
            INFO_CTRL_PANEL_DELETING_ATTRIBUTE_SUCCESSFUL.get(attrName),
            ERR_CTRL_PANEL_DELETING_ATTRIBUTE_ERROR_SUMMARY.get(),
            ERR_CTRL_PANEL_DELETING_ATTRIBUTE_ERROR_DETAILS.get(attrName),
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
