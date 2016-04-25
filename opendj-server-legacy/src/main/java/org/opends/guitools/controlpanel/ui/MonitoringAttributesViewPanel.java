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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.server.util.CollectionUtils.*;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.guitools.controlpanel.datamodel.MonitoringAttributes;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.event.ScrollPaneBorderListener;
import org.opends.guitools.controlpanel.util.Utilities;

/**
* The panel that allows the user to select which attributes must be displayed
* in the traffic monitoring tables.
*
* @param <T> the type of the objects that this panel manages.  For now it only
* manages String and MonitoringAttribute objects.
*/
class MonitoringAttributesViewPanel<T> extends StatusGenericPanel
{
 private static final long serialVersionUID = 6462932163745559L;

 private final LinkedHashSet<T> selectedAttributes = new LinkedHashSet<>();
 private final Set<T> monitoringAttributes;
 private boolean isCanceled = true;

 /**
  * Note: the order of the checkboxes and the elements in the Attributes
  * enumeration will be the same.
  */
 private JCheckBox[] checkboxes = {};

 private JButton selectAll;
 private JButton selectNone;

 /**
  * Creates an instance of this panel that uses String as attributes.
  * @param attributes the list of possible attributes.
  * @return an instance of this panel that uses String as attributes.
  */
 static MonitoringAttributesViewPanel<String> createStringInstance(LinkedHashSet<String> attributes)
 {
   return new MonitoringAttributesViewPanel<>(attributes);
 }

 /**
  * Creates an instance of this panel that uses MonitoringAttributes as
  * attributes.
  * @param attributes the list of possible attributes.
  * @return an instance of this panel that uses MonitoringAttributes as
  * attributes.
  */
 static MonitoringAttributesViewPanel<MonitoringAttributes>
 createMonitoringAttributesInstance(LinkedHashSet<MonitoringAttributes> attributes)
 {
   return new MonitoringAttributesViewPanel<>(attributes);
 }

 /**
  * Creates an instance of this panel that uses LocalizableMessage as
  * attributes.
  * @param attributes the list of possible attributes.
  * @return an instance of this panel that uses LocalizableMessage as attributes.
  */
 static MonitoringAttributesViewPanel<LocalizableMessage>
 createMessageInstance(LinkedHashSet<LocalizableMessage> attributes)
 {
   return new MonitoringAttributesViewPanel<>(attributes);
 }

 @Override
 public boolean requiresScroll()
 {
   return false;
 }

 /**
  * Default constructor.
  * @param attributes the attributes that will be proposed to the user.
  */
 private MonitoringAttributesViewPanel(LinkedHashSet<T> attributes)
 {
   monitoringAttributes = new LinkedHashSet<>(attributes);
   createLayout();
 }

 /**
  * Sets the attributes that must be selected in this dialog.
  * @param selectedAttributes the selected attributes.
  */
 public void setSelectedAttributes(
     Collection<T> selectedAttributes)
 {
   int i = 0;
   for (T attribute : monitoringAttributes)
   {
     checkboxes[i].setSelected(selectedAttributes.contains(attribute));
     i++;
   }
 }

 /** Creates the layout of the panel (but the contents are not populated here). */
 private void createLayout()
 {
   GridBagConstraints gbc = new GridBagConstraints();
   gbc.fill = GridBagConstraints.HORIZONTAL;
   gbc.gridy = 0;

   gbc.gridwidth = 2;
   gbc.gridx = 0;
   add(Utilities.createPrimaryLabel(INFO_CTRL_PANEL_OPERATION_VIEW_LABEL.get()), gbc);
   gbc.gridy ++;
   gbc.gridwidth = 1;
   gbc.insets.top = 10;

   JPanel checkBoxPanel = new JPanel(new GridBagLayout());
   checkBoxPanel.setOpaque(false);
   JScrollPane scroll = Utilities.createBorderLessScrollBar(checkBoxPanel);
   ScrollPaneBorderListener.createFullBorderListener(scroll);

   checkboxes = new JCheckBox[monitoringAttributes.size()];

   int i = 0;
   for (T attribute : monitoringAttributes)
   {
     LocalizableMessage m = getMessage(attribute);
     checkboxes[i] = Utilities.createCheckBox(m);
     i++;
   }
   selectAll = Utilities.createButton(INFO_CTRL_PANEL_SELECT_ALL_BUTTON.get());
   selectAll.addActionListener(new ActionListener()
   {
     @Override
     public void actionPerformed(ActionEvent ev)
     {
       for (JCheckBox cb : checkboxes)
       {
         cb.setSelected(true);
       }
     }
   });

   selectNone = Utilities.createButton(INFO_CTRL_PANEL_CLEAR_SELECTION_BUTTON.get());
   selectNone.addActionListener(new ActionListener()
   {
     @Override
     public void actionPerformed(ActionEvent ev)
     {
       for (JCheckBox cb : checkboxes)
       {
         cb.setSelected(false);
       }
     }
   });

   gbc.weightx = 1.0;
   gbc.weighty = 1.0;
   gbc.gridheight = 3;
   gbc.fill = GridBagConstraints.BOTH;
   add(scroll, gbc);

   gbc.gridx = 1;
   gbc.weightx = 0.0;
   gbc.weighty = 0.0;
   gbc.insets.left = 10;
   gbc.gridheight = 1;
   add(selectAll, gbc);
   gbc.gridy ++;
   gbc.insets.top = 10;
   add(selectNone, gbc);
   gbc.gridy ++;
   gbc.weighty = 1.0;
   add(Box.createVerticalGlue(), gbc);

   gbc = new GridBagConstraints();
   gbc.gridy = 0;
   gbc.gridwidth = 1;
   int preferredViewHeight = -1;
   for (JCheckBox cb : checkboxes)
   {
     gbc.gridx = 0;
     gbc.weightx = 0.0;
     gbc.anchor = GridBagConstraints.WEST;
     gbc.fill = GridBagConstraints.NONE;
     checkBoxPanel.add(cb, gbc);
     gbc.gridx = 1;
     gbc.weightx = 1.0;
     gbc.fill = GridBagConstraints.HORIZONTAL;
     checkBoxPanel.add(Box.createHorizontalGlue(), gbc);
     gbc.insets.top = 10;
     gbc.gridy ++;
     if (gbc.gridy == 15)
     {
       preferredViewHeight = checkBoxPanel.getPreferredSize().height;
     }
   }
   if (preferredViewHeight < 0)
   {
     preferredViewHeight = checkBoxPanel.getPreferredSize().height;
   }
   gbc.insets = new Insets(0, 0, 0, 0);
   gbc.gridx = 0;
   gbc.gridwidth = 2;
   gbc.fill = GridBagConstraints.VERTICAL;
   gbc.weighty = 1.0;
   checkBoxPanel.add(Box.createVerticalGlue(), gbc);
   scroll.getViewport().setPreferredSize(
       new Dimension(checkBoxPanel.getPreferredSize().width + 15, preferredViewHeight));
 }

 @Override
 public LocalizableMessage getTitle()
 {
   return INFO_CTRL_PANEL_ATTRIBUTE_VIEW_OPTIONS_TITLE.get();
 }

 @Override
 public void configurationChanged(ConfigurationChangeEvent ev)
 {
   // no-op
 }

 @Override
 public Component getPreferredFocusComponent()
 {
   return checkboxes[0];
 }

 @Override
 public void toBeDisplayed(boolean visible)
 {
   if (visible)
   {
     isCanceled = true;
   }
 }

 @Override
 public void okClicked()
 {
   // Check that at least one checkbox is selected.
   selectedAttributes.clear();
   int i = 0;
   for (T attribute : monitoringAttributes)
   {
     if (checkboxes[i].isSelected())
     {
       selectedAttributes.add(attribute);
     }
     i++;
   }
   if (selectedAttributes.isEmpty())
   {
     super.displayErrorDialog(newArrayList(INFO_CTRL_PANEL_NO_OPERATION_SELECTED.get()));
   }
   else
   {
     isCanceled = false;
     super.closeClicked();
   }
 }

 @Override
 public GenericDialog.ButtonType getButtonType()
 {
   return GenericDialog.ButtonType.OK_CANCEL;
 }

 /**
  * Returns <CODE>true</CODE> if the user closed the dialog by cancelling it
  * and <CODE>false</CODE> otherwise.
  * @return <CODE>true</CODE> if the user closed the dialog by cancelling it
  * and <CODE>false</CODE> otherwise.
  */
 public boolean isCanceled()
 {
   return isCanceled;
 }

 /**
  * Returns the list of attributes that the user selected.
  * @return the list of attributes that the user selected.
  */
 public LinkedHashSet<T> getAttributes()
 {
   return selectedAttributes;
 }

 /**
  * Returns the message for the provided attribute.
  * @param attribute the attribute.
  * @return the message for the provided attribute.
  */
 private LocalizableMessage getMessage(T attribute)
 {
   if (attribute instanceof MonitoringAttributes)
   {
     return ((MonitoringAttributes)attribute).getMessage();
   }
   else if (attribute instanceof LocalizableMessage)
   {
     return (LocalizableMessage)attribute;
   }
   else
   {
     return LocalizableMessage.raw(attribute.toString());
   }
 }
}
