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
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.Collection;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.JTextComponent;

import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.ui.components.BasicExpander;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.quicksetup.util.Utils;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.types.DN;
import org.opends.server.types.LDAPException;

/**
 * Abstract class used to refactor some code used by the import LDIF and export
 * LDIF panels.
 *
 */
public abstract class InclusionExclusionPanel extends StatusGenericPanel
{
  /**
   * The DNs to exclude.
   */
  protected JTextArea dnsToExclude;
  /**
   * The attributes to exclude.
   */
  protected JTextField attributesToExclude;
  /**
   * The exclusion filter.
   */
  protected JTextField exclusionFilter;
  /**
   * The DNs to include.
   */
  protected JTextArea dnsToInclude;
  /**
   * The attributes to include.
   */
  protected JTextField attributesToInclude;
  /**
   * The inclusion filter.
   */
  protected JTextField inclusionFilter;

  /**
   * The DNs to include.
   */
  protected JLabel lDnsToInclude;
  /**
   * The attributes to include.
   */
  protected JLabel lAttributesToInclude;
  /**
   * The inclusion filter label.
   */
  protected JLabel lInclusionFilter;
  /**
   * The DNs to exclude label.
   */
  protected JLabel lDnsToExclude;
  /**
   * The attributes to exclude label.
   */
  protected JLabel lAttributesToExclude;
  /**
   * The exclusion filter label.
   */
  protected JLabel lExclusionFilter;

  /**
   * {@inheritDoc}
   */
  public void cancelClicked()
  {
    setPrimaryValid(lDnsToInclude);
    setPrimaryValid(lAttributesToInclude);
    setPrimaryValid(lInclusionFilter);
    setPrimaryValid(lDnsToExclude);
    setPrimaryValid(lAttributesToExclude);
    setPrimaryValid(lExclusionFilter);
    super.cancelClicked();
  }

  /**
   * A commodity method that layouts a set of components.
   * @param extraComponentLabels the labels.
   * @param extraComponents the components.
   * @return the panel containing the labels and the components.
   */
  protected Component createDataInclusionOptions(
      final JLabel[] extraComponentLabels,
      final Component[] extraComponents)
  {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.weightx = 1.0;
    gbc.gridwidth = 2;
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    int labelInsetLeft = 15;
    final BasicExpander expander =
      new BasicExpander(INFO_CTRL_PANEL_DATA_INCLUSION_OPTIONS.get());
    panel.add(expander, gbc);

    gbc.gridy ++;
    lDnsToInclude =
      Utilities.createPrimaryLabel(INFO_CTRL_PANEL_DNS_TO_INCLUDE.get());
    gbc.insets.left = labelInsetLeft;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.insets.top = 10;
    gbc.gridwidth = 1;
    gbc.weightx = 0.0;
    panel.add(lDnsToInclude, gbc);

    gbc.gridx = 1;
    gbc.weightx = 1.0;
    gbc.insets.left = 10;
    dnsToInclude = Utilities.createTextArea(Message.EMPTY, 5, 25);
    final JScrollPane scrollDns = Utilities.createScrollPane(dnsToInclude);
    panel.add(scrollDns, gbc);

    gbc.insets.top = 2;
    gbc.gridy ++;
    final JLabel lDnsExplanation = Utilities.createInlineHelpLabel(
        INFO_CTRL_PANEL_SEPARATE_DNS_LINE_BREAK.get());
    panel.add(lDnsExplanation, gbc);

    gbc.gridy ++;
    gbc.gridx = 0;
    gbc.weightx = 0.0;
    lAttributesToInclude = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_ATTRIBUTES_TO_INCLUDE.get());
    gbc.insets.left = labelInsetLeft;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.insets.top = 10;
    gbc.gridwidth = 1;
    panel.add(lAttributesToInclude, gbc);

    gbc.gridx = 1;
    gbc.weightx = 1.0;
    gbc.insets.left = 10;
    gbc.weightx = 1.0;
    attributesToInclude = Utilities.createMediumTextField();
    panel.add(attributesToInclude, gbc);

    gbc.insets.top = 2;
    gbc.gridy ++;
    final JLabel lAttributesExplanation = Utilities.createInlineHelpLabel(
        INFO_CTRL_PANEL_SEPARATE_ATTRIBUTES_COMMA.get());
    panel.add(lAttributesExplanation, gbc);

    gbc.gridy ++;
    gbc.gridx = 0;
    lInclusionFilter = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_INCLUSION_FILTER.get());
    gbc.insets.left = labelInsetLeft;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.insets.top = 10;
    gbc.gridwidth = 1;
    gbc.weightx = 0.0;
    panel.add(lInclusionFilter, gbc);

    gbc.gridx = 1;
    gbc.weightx = 1.0;
    gbc.insets.left = 10;
    inclusionFilter = Utilities.createMediumTextField();
    panel.add(inclusionFilter, gbc);

    addExtraComponents(panel, extraComponentLabels, extraComponents, gbc,
        labelInsetLeft);

    ChangeListener changeListener = new ChangeListener()
    {
      /**
       * {@inheritDoc}
       */
      public void stateChanged(ChangeEvent e)
      {
        lDnsToInclude.setVisible(expander.isSelected());
        scrollDns.setVisible(expander.isSelected());
        lDnsExplanation.setVisible(expander.isSelected());
        lAttributesToInclude.setVisible(expander.isSelected());
        attributesToInclude.setVisible(expander.isSelected());
        lAttributesExplanation.setVisible(expander.isSelected());
        lInclusionFilter.setVisible(expander.isSelected());
        inclusionFilter.setVisible(expander.isSelected());
        expanderStateChanged(expander, extraComponentLabels, extraComponents);
        packParentDialog();
      }
    };
    expander.addChangeListener(changeListener);
    expander.setSelected(false);
    changeListener.stateChanged(null);

    return panel;
  }

  /**
   * A commodity method that layouts a set of components.
   * @param extraComponentLabels the labels.
   * @param extraComponents the components.
   * @return the panel containing the labels and the components.
   */
  protected Component createDataExclusionOptions(
      final JLabel[] extraComponentLabels,
      final Component[] extraComponents)
  {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.weightx = 1.0;
    gbc.gridwidth = 2;
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    int labelInsetLeft = 15;
    final BasicExpander expander =
      new BasicExpander(INFO_CTRL_PANEL_DATA_EXCLUSION_OPTIONS.get());
    panel.add(expander, gbc);

    gbc.gridy ++;
    lDnsToExclude =
      Utilities.createPrimaryLabel(INFO_CTRL_PANEL_DNS_TO_EXCLUDE.get());
    gbc.insets.left = labelInsetLeft;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.insets.top = 10;
    gbc.gridwidth = 1;
    gbc.weightx = 0.0;
    panel.add(lDnsToExclude, gbc);

    gbc.gridx = 1;
    gbc.weightx = 1.0;
    gbc.insets.left = 10;
    dnsToExclude = Utilities.createTextArea(Message.EMPTY, 5, 0);
    final JScrollPane scrollDns = Utilities.createScrollPane(dnsToExclude);
    panel.add(scrollDns, gbc);

    gbc.insets.top = 2;
    gbc.gridy ++;
    final JLabel lDnsExplanation = Utilities.createInlineHelpLabel(
        INFO_CTRL_PANEL_SEPARATE_DNS_LINE_BREAK.get());
    panel.add(lDnsExplanation, gbc);

    gbc.gridy ++;
    gbc.gridx = 0;
    gbc.weightx = 0.0;
    lAttributesToExclude = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_ATTRIBUTES_TO_EXCLUDE.get());
    gbc.insets.left = labelInsetLeft;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.insets.top = 10;
    gbc.gridwidth = 1;
    panel.add(lAttributesToExclude, gbc);

    gbc.gridx = 1;
    gbc.weightx = 1.0;
    gbc.insets.left = 10;
    gbc.weightx = 1.0;
    attributesToExclude = Utilities.createTextField();
    panel.add(attributesToExclude, gbc);

    gbc.insets.top = 2;
    gbc.gridy ++;
    final JLabel lAttributesExplanation = Utilities.createInlineHelpLabel(
        INFO_CTRL_PANEL_SEPARATE_ATTRIBUTES_COMMA.get());
    panel.add(lAttributesExplanation, gbc);

    gbc.gridy ++;
    gbc.gridx = 0;
    lExclusionFilter = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_EXCLUSION_FILTER.get());
    gbc.insets.left = labelInsetLeft;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.insets.top = 10;
    gbc.gridwidth = 1;
    gbc.weightx = 0.0;
    panel.add(lExclusionFilter, gbc);

    gbc.gridx = 1;
    gbc.weightx = 1.0;
    gbc.insets.left = 10;
    exclusionFilter = Utilities.createTextField();
    panel.add(exclusionFilter, gbc);

    addExtraComponents(panel, extraComponentLabels, extraComponents, gbc,
        labelInsetLeft);

    ChangeListener changeListener = new ChangeListener()
    {
      /**
       * {@inheritDoc}
       */
      public void stateChanged(ChangeEvent e)
      {
        lDnsToExclude.setVisible(expander.isSelected());
        scrollDns.setVisible(expander.isSelected());
        lDnsExplanation.setVisible(expander.isSelected());
        lAttributesToExclude.setVisible(expander.isSelected());
        attributesToExclude.setVisible(expander.isSelected());
        lAttributesExplanation.setVisible(expander.isSelected());
        lExclusionFilter.setVisible(expander.isSelected());
        exclusionFilter.setVisible(expander.isSelected());
        expanderStateChanged(expander, extraComponentLabels, extraComponents);
      }
    };
    expander.addChangeListener(changeListener);
    expander.setSelected(false);
    changeListener.stateChanged(null);

    return panel;
  }

  private void addExtraComponents(JPanel panel, JLabel[] extraComponentLabels,
      Component[] extraComponents, GridBagConstraints gbc, int labelInsetLeft)
  {
    for (int i=0; i<extraComponentLabels.length; i++)
    {
      if (extraComponentLabels[i] == null)
      {
        gbc.gridy ++;
        gbc.gridx = 0;
        gbc.insets.left = labelInsetLeft;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets.top = 10;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        panel.add(extraComponents[i], gbc);
      }
      else
      {
        gbc.gridy ++;
        gbc.gridx = 0;
        gbc.insets.left = labelInsetLeft;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets.top = 10;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        panel.add(extraComponentLabels[i], gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.insets.left = 10;
        panel.add(extraComponents[i], gbc);
      }
    }
  }

  private void expanderStateChanged(BasicExpander expander,
      JLabel[] extraComponentLabels,
      Component[] extraComponents)
  {
    for (JLabel comp : extraComponentLabels)
    {
      if (comp != null)
      {
        comp.setVisible(expander.isSelected());
      }
    }
    for (Component comp : extraComponents)
    {
      comp.setVisible(expander.isSelected());
    }
  }


  /**
   * Updates a list of errors in the include and exclude subpanels.
   * @param errors the list of errors to be updated.
   * @param backendName the name of the backend where the operation associated
   * with the panel applies (used to generate the error messages).
   */
  protected void updateIncludeExclude(Collection<Message> errors,
      String backendName)
  {
    updateErrors(lDnsToInclude, dnsToInclude, lAttributesToInclude,
        attributesToInclude, lInclusionFilter, inclusionFilter, errors,
        backendName);
    updateErrors(lDnsToExclude, dnsToExclude, lAttributesToExclude,
        attributesToExclude, lExclusionFilter, exclusionFilter, errors,
        backendName);
  }


  private void updateErrors(JLabel lDns, JTextComponent dns, JLabel lAttributes,
      JTextComponent attributes, JLabel lFilter, JTextComponent filter,
      Collection<Message> errors, String backendName)
  {
    setPrimaryValid(lDns);
    setPrimaryValid(lAttributes);
    setPrimaryValid(lFilter);

    String s = dns.getText();

    boolean validDn = true;

    if (s.trim().length() > 0)
    {
      String[] dnArray = s.split("\n");
      for (int i=0; i<dnArray.length; i++)
      {
        if (!Utils.isDn(dnArray[i]))
        {
          errors.add(ERR_CTRL_PANEL_DN_NOT_VALID_WITH_VALUE.get(dnArray[i]));
          validDn = false;
        }
        else
        {
          BackendDescriptor backend = null;

          if (backendName != null)
          {
            ServerDescriptor server = getInfo().getServerDescriptor();
            for (BackendDescriptor b : server.getBackends())
            {
              if (b.getBackendID().equalsIgnoreCase(backendName))
              {
                backend = b;
                break;
              }
            }
          }

          if (backend != null)
          {
            boolean found = false;
            for (BaseDNDescriptor baseDN : backend.getBaseDns())
            {
              try
              {
                DN dn = DN.decode(dnArray[i]);
                if (dn.isDescendantOf(baseDN.getDn()))
                {
                  found = true;
                  break;
                }
              }
              catch (Throwable t)
              {
                // Bug
                t.printStackTrace();
              }
            }
            if (!found)
            {
              errors.add(ERR_CTRL_PANEL_NOT_A_DESCENDANT_OF_BASE_DN.get(
                  dnArray[i], backendName));
            }
          }
        }
      }
    }

    if (!validDn)
    {
      setPrimaryInvalid(lDns);
    }

    s = attributes.getText();

    boolean validAttributes = true;

    if (s.trim().length() > 0)
    {
      String[] attributeArray = s.split(",");
      for (int i=0; i<attributeArray.length; i++)
      {
        if (!Utilities.isValidAttributeName(attributeArray[i]))
        {
          errors.add(ERR_CTRL_PANEL_NOT_VALID_ATTRIBUTE_NAME.get(
              attributeArray[i]));
          validAttributes = false;
        }
      }
    }

    if (!validAttributes)
    {
      setPrimaryInvalid(lAttributes);
    }

    s = filter.getText();
    if ((s != null) && (s.trim().length() > 0))
    {
      try
      {
        LDAPFilter.decode(s);
      }
      catch (LDAPException le)
      {
        errors.add(ERR_CTRL_PANEL_INVALID_FILTER_DETAILS_WITH_VALUE.get(s,
            le.getMessageObject().toString()));
        setPrimaryInvalid(lFilter);
      }
    }
  }

  /**
   * Abstract class that provides some methods that can be used to generate the
   * equivalent command-line arguments for some of the things that are contained
   * in the inclusion/exclusion panels.
   *
   */
  protected abstract class InclusionExclusionTask extends Task
  {
    /**
     * The constructor of the task.
     * @param info the control panel info.
     * @param dlg the progress dialog that shows the progress of the task.
     */
    protected InclusionExclusionTask(ControlPanelInfo info, ProgressDialog dlg)
    {
      super(info, dlg);
    }

    /**
     * Returns the command line arguments corresponding to the elements
     * displayed in the inclusion/exclusion panels.
     * @return the command line arguments corresponding to the elements
     * displayed in the inclusion/exclusion panels.
     */
    protected ArrayList<String> getCommandLineArguments()
    {
      ArrayList<String> args = new ArrayList<String>();
      String s = dnsToInclude.getText();
      if (s.trim().length() > 0)
      {
        String[] dnArray = s.split("\n");
        for (int i=0; i<dnArray.length; i++)
        {
          args.add("--includeBranch");
          args.add(dnArray[i]);
        }
      }
      s = attributesToInclude.getText();
      if (s.trim().length() > 0)
      {
        String[] attrArray = s.split(",");
        for (int i=0; i<attrArray.length; i++)
        {
          args.add("--includeAttribute");
          args.add(attrArray[i]);
        }
      }
      s = inclusionFilter.getText();
      if (s.trim().length() > 0)
      {
        args.add("--includeFilter");
        args.add(s);
      }

      s = dnsToExclude.getText();
      if (s.trim().length() > 0)
      {
        String[] dnArray = s.split("\n");
        for (int i=0; i<dnArray.length; i++)
        {
          args.add("--excludeBranch");
          args.add(dnArray[i]);
        }
      }
      s = attributesToExclude.getText();
      if (s.trim().length() > 0)
      {
        String[] attrArray = s.split(",");
        for (int i=0; i<attrArray.length; i++)
        {
          args.add("--excludeAttribute");
          args.add(attrArray[i]);
        }
      }
      s = exclusionFilter.getText();
      if (s.trim().length() > 0)
      {
        args.add("--excludeFilter");
        args.add(s);
      }
      args.addAll(getConnectionCommandLineArguments());
      return args;
    }
  }
}
