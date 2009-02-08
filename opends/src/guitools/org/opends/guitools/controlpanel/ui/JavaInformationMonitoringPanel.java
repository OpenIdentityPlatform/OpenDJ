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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.JTextComponent;

import org.opends.guitools.controlpanel.datamodel.CustomSearchResult;
import org.opends.guitools.controlpanel.datamodel.BasicMonitoringAttributes;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.ui.components.BasicExpander;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.server.util.ServerConstants;

/**
 * The panel displaying the java monitoring information.
 */
public class JavaInformationMonitoringPanel extends GeneralMonitoringPanel
{
  private static final long serialVersionUID = 9031734563799969830L;
  private List<BasicMonitoringAttributes> generalAttributes =
    new ArrayList<BasicMonitoringAttributes>();
  {
    generalAttributes.add(BasicMonitoringAttributes.JVM_VERSION);
    generalAttributes.add(BasicMonitoringAttributes.JVM_VENDOR);
    generalAttributes.add(BasicMonitoringAttributes.JVM_ARCHITECTURE);
    generalAttributes.add(BasicMonitoringAttributes.JVM_ARGUMENTS);
    generalAttributes.add(BasicMonitoringAttributes.CLASS_PATH);
    generalAttributes.add(BasicMonitoringAttributes.JAVA_VERSION);
    generalAttributes.add(BasicMonitoringAttributes.JAVA_VENDOR);
  }
  private List<BasicMonitoringAttributes> extraAttributes =
    new ArrayList<BasicMonitoringAttributes>();
  {
    extraAttributes.add(BasicMonitoringAttributes.CLASS_PATH);
    extraAttributes.add(BasicMonitoringAttributes.JAVA_VERSION);
    extraAttributes.add(BasicMonitoringAttributes.JAVA_VENDOR);
  }
  private ArrayList<JComponent> generalMonitoringComps =
    new ArrayList<JComponent>();
  {
    for (int i=0; i<generalAttributes.size(); i++)
    {
      if ((generalAttributes.get(i) == BasicMonitoringAttributes.CLASS_PATH) ||
          (generalAttributes.get(i) == BasicMonitoringAttributes.JVM_ARGUMENTS))
      {
        JEditorPane pane = new JEditorPane();
        pane.setEditable(false);
        pane.setBorder(new EmptyBorder(0, 0, 0, 0));
        pane.setOpaque(false);
        pane.setFocusCycleRoot(false);
        generalMonitoringComps.add(pane);
      }
      else
      {
        generalMonitoringComps.add(Utilities.createDefaultLabel());
      }
    }
  }

  private List<String> memoryAttributes = new ArrayList<String>();
  private List<JLabel> memoryLabels = new ArrayList<JLabel>();
  private JPanel memoryPanel;

  /**
   * Default constructor.
   */
  public JavaInformationMonitoringPanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return generalMonitoringComps.get(0);
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    JLabel lTitle = Utilities.createTitleLabel(
        INFO_CTRL_PANEL_JAVA_INFORMATION.get());
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.gridwidth = 2;
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.insets.top = 5;
    gbc.insets.bottom = 7;
    add(lTitle, gbc);

    gbc.insets.bottom = 0;
    gbc.insets.top = 10;
    gbc.gridy ++;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.gridwidth = 1;
    for (int i=0; i<generalAttributes.size(); i++)
    {
      if (extraAttributes.contains(generalAttributes.get(i)))
      {
        continue;
      }
      JLabel l = Utilities.createPrimaryLabel(
          getLabel(generalAttributes.get(i)));
      gbc.gridy ++;
      gbc.insets.left = 0;
      gbc.insets.right = 0;
      gbc.gridx = 0;
      gbc.weightx = 0.0;
      gbc.gridwidth = 1;
      gbc.fill = GridBagConstraints.NONE;
      boolean isTextComponent =
        generalMonitoringComps.get(i) instanceof JTextComponent;
      if (isTextComponent)
      {
        gbc.anchor = GridBagConstraints.NORTHWEST;
      }
      else
      {
        gbc.anchor = GridBagConstraints.WEST;
      }
      add(l, gbc);
      gbc.insets.left = 10;
      gbc.gridx = 1;
      if (isTextComponent)
      {
        gbc.insets.right = 10;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        add(generalMonitoringComps.get(i), gbc);
      }
      else
      {
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(generalMonitoringComps.get(i), gbc);
      }
    }

    final BasicExpander extraExpander = new BasicExpander(
        INFO_CTRL_PANEL_EXTRA_JAVA_ATTRIBUTES.get());
    gbc.gridwidth = 2;
    gbc.gridx = 0;
    gbc.weighty = 0.0;
    gbc.insets.left = 0;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridy ++;
    add(extraExpander, gbc);
    final JPanel extraGeneralPanel = new JPanel(new GridBagLayout());
    gbc.insets.left = 15;
    gbc.gridy ++;
    add(extraGeneralPanel, gbc);
    extraGeneralPanel.setOpaque(false);
    extraGeneralPanel.setVisible(false);

    final BasicExpander memoryExpander = new BasicExpander(
        INFO_CTRL_PANEL_JAVA_MEMORY_ATTRIBUTES.get());
    gbc.gridy ++;
    gbc.insets.left = 0;
    add(memoryExpander, gbc);
    memoryPanel = new JPanel(new GridBagLayout());
    gbc.insets.left = 15;
    gbc.gridy ++;
    add(memoryPanel, gbc);
    memoryPanel.setOpaque(false);
    memoryPanel.setVisible(false);

    GridBagConstraints gbc1 = new GridBagConstraints();
    gbc1.fill = GridBagConstraints.HORIZONTAL;
    gbc1.gridy = 0;
    gbc1.gridx = 0;
    gbc1.gridwidth = 1;

    for (int i=0; i<extraAttributes.size(); i++)
    {
      int index = generalAttributes.indexOf(extraAttributes.get(i));
      JLabel l = Utilities.createPrimaryLabel(
          getLabel(extraAttributes.get(i)));
      gbc1.insets.left = 0;
      gbc1.insets.right = 0;
      gbc1.gridx = 0;
      gbc1.weightx = 0.0;
      gbc1.gridwidth = 1;
      gbc1.fill = GridBagConstraints.NONE;
      boolean isTextComponent =
        generalMonitoringComps.get(index) instanceof JTextComponent;
      if (isTextComponent)
      {
        gbc1.anchor = GridBagConstraints.NORTHWEST;
      }
      else
      {
        gbc1.anchor = GridBagConstraints.WEST;
      }
      extraGeneralPanel.add(l, gbc1);
      gbc1.insets.left = 10;
      gbc1.gridx = 1;
      if (isTextComponent)
      {
        gbc1.insets.right = 10;
        gbc1.weightx = 1.0;
        gbc1.fill = GridBagConstraints.BOTH;
        extraGeneralPanel.add(generalMonitoringComps.get(index), gbc1);
      }
      else
      {
        gbc1.weightx = 1.0;
        gbc1.fill = GridBagConstraints.HORIZONTAL;
        extraGeneralPanel.add(generalMonitoringComps.get(index), gbc1);
      }
      gbc1.insets.top = 10;
      gbc1.gridy ++;
    }
    ChangeListener changeListener = new ChangeListener()
    {
      /**
       * {@inheritDoc}
       */
      public void stateChanged(ChangeEvent e)
      {
        extraGeneralPanel.setVisible(extraExpander.isSelected());
      }
    };
    extraExpander.addChangeListener(changeListener);

    changeListener = new ChangeListener()
    {
      /**
       * {@inheritDoc}
       */
      public void stateChanged(ChangeEvent e)
      {
        memoryPanel.setVisible(memoryExpander.isSelected());
      }
    };
    memoryExpander.addChangeListener(changeListener);

    gbc.gridx = 0;
    gbc.gridy ++;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.gridwidth = 2;
    add(Box.createGlue(), gbc);

    setBorder(PANEL_BORDER);
  }

  /**
   * Updates the contents of the panel.
   *
   */
  public void updateContents()
  {
    ServerDescriptor server = null;
    if (getInfo() != null)
    {
      server = getInfo().getServerDescriptor();
    }
    CustomSearchResult csrSystem = null;
    CustomSearchResult csrMemory = null;
    if (server != null)
    {
      csrSystem = server.getSystemInformationMonitor();
      csrMemory = server.getJvmMemoryUsageMonitor();
    }
    if (csrSystem != null)
    {
      for (int i=0 ; i<generalAttributes.size(); i++)
      {
        String value =
          getMonitoringValue(generalAttributes.get(i), csrSystem);
        JComponent l = generalMonitoringComps.get(i);
        if (l instanceof JLabel)
        {
          ((JLabel)l).setText(value);
        }
        else if (l instanceof JTextComponent)
        {
          ((JTextComponent)l).setText(value);
        }
        else
        {
          throw new IllegalStateException("Unexpected component: "+l);
        }
      }
    }
    else
    {
      for (JComponent l : generalMonitoringComps)
      {
        if (l instanceof JLabel)
        {
          ((JLabel)l).setText(NO_VALUE_SET.toString());
        }
        else if (l instanceof JTextComponent)
        {
          ((JTextComponent)l).setText(NO_VALUE_SET.toString());
        }
        else
        {
          throw new IllegalStateException("Unexpected component: "+l);
        }
      }
    }
    if (csrMemory != null)
    {
      if (memoryAttributes.isEmpty())
      {
        Set<String> allNames = csrMemory.getAttributeNames();
        SortedSet<String> sortedNames = new TreeSet<String>();
        for (String attrName : allNames)
        {
          if (!attrName.equalsIgnoreCase(
              ServerConstants.OBJECTCLASS_ATTRIBUTE_TYPE_NAME) &&
              !attrName.equalsIgnoreCase(ServerConstants.ATTR_COMMON_NAME))
          {
            sortedNames.add(attrName);
          }
        }
        memoryAttributes.addAll(sortedNames);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridwidth = 1;

        for (String attrName : memoryAttributes)
        {
          JLabel l = Utilities.createPrimaryLabel(
              INFO_CTRL_PANEL_OPERATION_NAME_AS_LABEL.get(attrName));
          gbc.insets.left = 0;
          gbc.insets.right = 0;
          gbc.gridx = 0;
          gbc.weightx = 0.0;
          gbc.fill = GridBagConstraints.NONE;
          memoryPanel.add(l, gbc);
          gbc.insets.left = 10;
          gbc.gridx = 1;
          gbc.weightx = 1.0;
          gbc.fill = GridBagConstraints.HORIZONTAL;
          JLabel valueLabel = Utilities.createDefaultLabel();
          memoryLabels.add(valueLabel);
          memoryPanel.add(valueLabel, gbc);
          gbc.gridy ++;
          gbc.insets.top = 10;
        }
      }

      for (int i=0; i<memoryAttributes.size() ; i++)
      {
        Object value = Utilities.getFirstMonitoringValue(
            csrMemory,
            memoryAttributes.get(i));
        if (value != null)
        {
          memoryLabels.get(i).setText(value.toString());
        }
        else
        {
          memoryLabels.get(i).setText(NO_VALUE_SET.toString());
        }
      }
    }
    else
    {
      for (JLabel l : memoryLabels)
      {
        l.setText(NO_VALUE_SET.toString());
      }
    }
  }
}
