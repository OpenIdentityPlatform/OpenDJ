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

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.opends.guitools.controlpanel.browser.BrowserController;
import org.opends.guitools.controlpanel.ui.nodes.BasicNode;
import org.opends.guitools.controlpanel.ui.nodes.BrowserNodeInfo;
import org.opends.guitools.controlpanel.ui.nodes.DndBrowserNodes;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.types.DN;
import org.opends.server.types.LDAPURL;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.ServerConstants;

/**
 * The panel to create a group.
 *
 */
public class NewGroupPanel extends AbstractNewEntryPanel
{
  private static final long serialVersionUID = -8173120152617813282L;
  private JLabel lName = Utilities.createPrimaryLabel();
  private JLabel lDescription = Utilities.createPrimaryLabel();
  private JLabel lMembers = Utilities.createPrimaryLabel();
  private JLabel lDn = Utilities.createPrimaryLabel();

  private JLabel lMemberDNs;
  private JLabel lLDAPURL;
  private JLabel lReferenceGroup;


  private JLabel[] labels = {lName, lDescription, lMembers, lDn};

  private JTextField name = Utilities.createLongTextField();
  private JTextField description = Utilities.createLongTextField();
  private JRadioButton dynamicGroup;
  private JRadioButton staticGroup;
  private JRadioButton virtualGroup;

  private JTextArea staticMembers;
  private JButton addMembers;
  private JTextField filter  = Utilities.createLongTextField();
  private JTextField referenceGroup  = Utilities.createLongTextField();
  private JButton browseReferenceGroup;

  private GenericDialog membersDlg;
  private LDAPEntrySelectionPanel membersPanel;

  private GenericDialog referenceGroupDlg;
  private LDAPEntrySelectionPanel referenceGroupPanel;

  private JLabel dn = Utilities.createDefaultLabel();


  /**
   * An array containing the fields of this panel.
   */
  protected final JTextField[] fields = {name, description, filter,
      referenceGroup};

  /**
   * Default constructor.
   *
   */
  public NewGroupPanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public void setParent(BasicNode parentNode, BrowserController controller)
  {
    super.setParent(parentNode, controller);
    dn.setText(","+parentNode.getDN());
    for (JTextField tf : fields)
    {
      tf.setText("");
    }
    staticMembers.setText("");
    filter.setText("ldap:///"+parentNode.getDN()+"??sub?(<your filter>)");
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_NEW_GROUP_PANEL_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return name;
  }

  /**
   * {@inheritDoc}
   */
  protected Message getProgressDialogTitle()
  {
    return INFO_CTRL_PANEL_NEW_GROUP_PANEL_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  protected boolean checkSyntaxBackground()
  {
    return staticGroup.isSelected();
  }

  /**
   * {@inheritDoc}
   */
  protected void checkSyntax(ArrayList<Message> errors)
  {
    Runnable runnable = new Runnable()
    {
      /**
       * {@inheritDoc}
       */
      public void run()
      {
        for (JLabel label : labels)
        {
          setPrimaryValid(label);
        }
        setSecondaryValid(lMemberDNs);
        setSecondaryValid(lLDAPURL);
        setSecondaryValid(lReferenceGroup);
      }
    };
    if (checkSyntaxBackground())
    {
      SwingUtilities.invokeLater(runnable);
    }
    else
    {
      runnable.run();
    }

    JTextField[] requiredFields = {name};
    Message[] msgs = {ERR_CTRL_PANEL_NAME_OF_GROUP_REQUIRED.get()};
    for (int i=0; i<requiredFields.length; i++)
    {
      String v = requiredFields[i].getText().trim();
      if (v.length() == 0)
      {
        errors.add(msgs[i]);
      }
    }

    if (staticGroup.isSelected())
    {
      String[] members = staticMembers.getText().split("\n");
      boolean oneMemberDefined = false;
      boolean errorFound = false;
      for (String member : members)
      {
        member = member.trim();
        if (member.length() > 0)
        {
          try
          {
            DN.decode(member);
            if (!entryExists(member))
            {
              errorFound = true;
              errors.add(ERR_CTRL_PANEL_MEMBER_NOT_FOUND.get(member));
            }
            else
            {
              oneMemberDefined = true;
            }
          }
          catch (OpenDsException ode)
          {
            errorFound = true;
            errors.add(ERR_CTRL_PANEL_MEMBER_VALUE_NOT_VALID.get(member,
                ode.getMessageObject().toString()));
          }
        }
      }
      if (!oneMemberDefined && !errorFound)
      {
        errorFound = true;
        errors.add(ERR_CTRL_PANEL_MEMBER_REQUIRED.get());
      }
      if (errorFound)
      {
        SwingUtilities.invokeLater(new Runnable()
        {
          public void run()
          {
            setSecondaryInvalid(lMemberDNs);
            setPrimaryInvalid(lMembers);
          }
        });
      }
    }
    else if (dynamicGroup.isSelected())
    {
      boolean errorFound = false;
      String f = filter.getText().trim();
      if (f.length() == 0)
      {
        errors.add(ERR_CTRL_PANEL_GROUP_FILTER_REQUIRED.get());
        errorFound = true;
      }
      else
      {
        try
        {
          LDAPURL.decode(f, true);
        }
        catch (OpenDsException ode)
        {
          errors.add(ERR_CTRL_PANEL_GROUP_FILTER_NOT_VALID.get(
              ode.getMessageObject().toString()));
        }
      }
      if (errorFound)
      {
        setSecondaryInvalid(lLDAPURL);
        setPrimaryInvalid(lMembers);
      }
    }
    else
    {
      boolean errorFound = false;
      String ref = referenceGroup.getText().trim();
      try
      {
        DN.decode(ref);
        if (!entryExists(ref))
        {
          errorFound = true;
          errors.add(ERR_CTRL_PANEL_REFERENCE_GROUP_NOT_FOUND.get());
        }
        else if (!hasObjectClass(ref, "groupOfURLs"))
        {
          errorFound = true;
          errors.add(ERR_CTRL_PANEL_REFERENCE_GROUP_NOT_DYNAMIC.get());
        }
      }
      catch (OpenDsException ode)
      {
        errorFound = true;
        errors.add(ERR_CTRL_PANEL_REFERENCE_GROUP_NOT_VALID.get(
            ode.getMessageObject().toString()));
      }
      if (errorFound)
      {
        setSecondaryInvalid(lReferenceGroup);
        setPrimaryInvalid(lMembers);
      }
    }
  }


  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    Message[] ls = {
        INFO_CTRL_PANEL_NEW_GROUP_NAME_LABEL.get(),
        INFO_CTRL_PANEL_NEW_GROUP_DESCRIPTION_LABEL.get(),
        INFO_CTRL_PANEL_NEW_GROUP_MEMBERS_LABEL.get(),
        INFO_CTRL_PANEL_NEW_GROUP_ENTRY_DN_LABEL.get()
        };
    int i = 0;
    for (Message l : ls)
    {
      labels[i].setText(l.toString());
      i++;
    }
    Utilities.setRequiredIcon(lName);
    Utilities.setRequiredIcon(lMembers);

    gbc.gridwidth = 2;
    gbc.gridy = 0;
    addErrorPane(gbc);

    gbc.gridy ++;
    gbc.gridwidth = 1;
    gbc.weighty = 0.0;
    gbc.gridx = 1;
    gbc.anchor = GridBagConstraints.EAST;
    gbc.fill = GridBagConstraints.NONE;
    JLabel requiredLabel = createRequiredLabel();
    gbc.insets.bottom = 10;
    add(requiredLabel, gbc);

    gbc.gridy ++;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.insets.bottom = 0;

    staticGroup = Utilities.createRadioButton(
        INFO_CTRL_PANEL_STATIC_GROUP_LABEL.get());
    dynamicGroup = Utilities.createRadioButton(
        INFO_CTRL_PANEL_DYNAMIC_GROUP_LABEL.get());
    virtualGroup = Utilities.createRadioButton(
        INFO_CTRL_PANEL_VIRTUAL_STATIC_GROUP_LABEL.get());
    ButtonGroup group = new ButtonGroup();
    group.add(staticGroup);
    group.add(dynamicGroup);
    group.add(virtualGroup);
    staticGroup.setSelected(true);
    ActionListener actionListener = new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        checkEnabling();
      }
    };
    staticGroup.addActionListener(actionListener);
    dynamicGroup.addActionListener(actionListener);
    virtualGroup.addActionListener(actionListener);

    JLabel[] labels = {lName, lDescription, lMembers};
    Component[] comps = {name, description, staticGroup};
    Component[] inlineHelp = {null, null, null};

    for (i=0 ; i< labels.length; i++)
    {
      gbc.insets.left = 0;
      gbc.weightx = 0.0;
      gbc.gridx = 0;
      gbc.gridwidth = 1;
      add(labels[i], gbc);
      gbc.insets.left = 10;
      gbc.weightx = 1.0;
      gbc.gridx = 1;
      add(comps[i], gbc);
      if (inlineHelp[i] != null)
      {
        gbc.insets.top = 3;
        gbc.gridy ++;
        add(inlineHelp[i], gbc);
      }
      gbc.insets.top = 10;
      gbc.gridy ++;
    }
    gbc.insets.top = 5;
    lMemberDNs = Utilities.createDefaultLabel(
        INFO_CTRL_PANEL_GROUP_MEMBER_DNS_LABEL.get());
    gbc.insets.left = 30;
    add(lMemberDNs, gbc);
    staticMembers = Utilities.createTextArea(Message.EMPTY, 8, 40);
    JScrollPane scroll = Utilities.createScrollPane(staticMembers);
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridy ++;
    JPanel p = new JPanel(new GridBagLayout());
    p.setOpaque(false);
    lLDAPURL = Utilities.createDefaultLabel(
        INFO_CTRL_PANEL_GROUP_FILTER_LABEL.get());
    add(p, gbc);
    GridBagConstraints gbc2 = new GridBagConstraints();
    gbc2.gridx = 0;
    gbc2.weightx = 1.0;
    gbc2.weighty = 1.0;
    gbc2.fill = GridBagConstraints.BOTH;
    p.add(scroll, gbc2);
    gbc2.insets.left = 5;
    gbc2.weightx = 0.0;
    gbc2.fill = GridBagConstraints.HORIZONTAL;
    gbc2.gridx ++;
    addMembers = Utilities.createButton(
        INFO_CTRL_PANEL_ADD_MEMBERS_BUTTON.get());
    gbc2.anchor = GridBagConstraints.NORTH;
    p.add(addMembers, gbc2);

    addMembers.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        addMembersClicked();
      }
    });

    gbc.insets.left = 10;
    gbc.insets.top = 10;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weighty = 0.0;
    gbc.gridy ++;
    add(dynamicGroup, gbc);
    gbc.insets.top = 5;
    gbc.weightx = 0.0;
    gbc.gridy ++;
    p = new JPanel(new GridBagLayout());
    p.setOpaque(false);
    gbc.insets.left = 30;
    add(p, gbc);

    gbc2 = new GridBagConstraints();
    gbc2.gridx = 0;
    gbc2.fill = GridBagConstraints.HORIZONTAL;
    p.add(lLDAPURL, gbc2);
    gbc2.insets.left = 5;
    gbc2.weightx = 1.0;
    gbc2.gridx ++;
    p.add(filter, gbc2);

    lReferenceGroup = Utilities.createDefaultLabel(
        INFO_CTRL_PANEL_DYNAMIC_GROUP_REFERENCE_LABEL.get());
    gbc.insets.left = 30;
    p = new JPanel(new GridBagLayout());
    p.setOpaque(false);
    gbc.gridy ++;
    add(p, gbc);

    gbc.gridy ++;
    gbc.gridx = 1;
    gbc.insets.left = 10;
    gbc.insets.top = 10;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weighty = 0.0;
    add(virtualGroup, gbc);
    gbc.insets.top = 5;
    gbc.weightx = 0.0;

    gbc.insets.top = 10;
    gbc.gridy ++;
    gbc.insets.left = 0;
    gbc.weightx = 0.0;
    gbc.gridx = 0;
    gbc.gridwidth = 1;
    add(lDn, gbc);
    gbc.insets.left = 10;
    gbc.weightx = 1.0;
    gbc.gridx = 1;
    add(dn, gbc);

    gbc2 = new GridBagConstraints();
    gbc2.gridx = 0;
    gbc2.fill = GridBagConstraints.HORIZONTAL;
    p.add(lReferenceGroup, gbc2);
    gbc2.insets.left = 5;
    gbc2.weightx = 1.0;
    gbc2.gridx ++;
    p.add(referenceGroup, gbc2);
    gbc2.weightx = 0.0;
    gbc2.gridx ++;
    browseReferenceGroup =
      Utilities.createButton(INFO_CTRL_PANEL_BROWSE_BUTTON_LABEL.get());
    browseReferenceGroup.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        browseReferenceGroupClicked();
      }
    });
    p.add(browseReferenceGroup, gbc2);


    DocumentListener listener = new DocumentListener()
    {
      /**
       * {@inheritDoc}
       */
      public void insertUpdate(DocumentEvent ev)
      {
        updateDNValue();
      }

      /**
       * {@inheritDoc}
       */
      public void changedUpdate(DocumentEvent ev)
      {
        insertUpdate(ev);
      }

      /**
       * {@inheritDoc}
       */
      public void removeUpdate(DocumentEvent ev)
      {
        insertUpdate(ev);
      }
    };
    JTextField[] toAddListener = {name};
    for (JTextField tf : toAddListener)
    {
      tf.getDocument().addDocumentListener(listener);
    }

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

          Component comp = e.getDropTargetContext().getComponent();
          if (comp == staticMembers)
          {
            StringBuilder sb = new StringBuilder();
            sb.append(staticMembers.getText());
            for (BrowserNodeInfo node : nodes.getNodes())
            {
              if (sb.length() > 0)
              {
                sb.append("\n");
              }
              sb.append(node.getNode().getDN());
            }
            staticMembers.setText(sb.toString());
            staticMembers.setCaretPosition(sb.length());
          }
          else if (comp == referenceGroup)
          {
            if (nodes.getNodes().length > 0)
            {
              String dn = nodes.getNodes()[0].getNode().getDN();
              referenceGroup.setText(dn);
              referenceGroup.setCaretPosition(dn.length());
            }
          }
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
    new DropTarget(staticMembers, dropTargetlistener);
    new DropTarget(referenceGroup, dropTargetlistener);

    checkEnabling();
  }

  /**
   * {@inheritDoc}
   */
  protected void updateDNValue()
  {
    String value = name.getText().trim();
    if (value.length() > 0)
    {
       String rdn = Utilities.getRDNString("cn", value);
          dn.setText(rdn+","+parentNode.getDN());
    }
    else
    {
      dn.setText(","+parentNode.getDN());
    }
  }

  private void addMembersClicked()
  {
    if (membersDlg == null)
    {
      membersPanel = new LDAPEntrySelectionPanel();
      membersPanel.setTitle(INFO_CTRL_PANEL_ADD_MEMBERS_LABEL.get());
      membersPanel.setFilter(LDAPEntrySelectionPanel.Filter.USERS);
      membersPanel.setMultipleSelection(true);
      membersPanel.setInfo(getInfo());
      membersDlg = new GenericDialog(Utilities.getFrame(this), membersPanel);
      Utilities.centerGoldenMean(membersDlg,
          Utilities.getParentDialog(this));
      membersDlg.setModal(true);
    }
    membersDlg.setVisible(true);
    String[] dns = membersPanel.getDNs();
    if (dns.length > 0)
    {
      StringBuilder sb = new StringBuilder();
      sb.append(staticMembers.getText());
      for (String dn : dns)
      {
        if (sb.length() > 0)
        {
          sb.append("\n");
        }
        sb.append(dn);
      }
      staticMembers.setText(sb.toString());
      staticMembers.setCaretPosition(sb.length());
    }
  }

  private void browseReferenceGroupClicked()
  {
    if (referenceGroupDlg == null)
    {
      referenceGroupPanel = new LDAPEntrySelectionPanel();
      referenceGroupPanel.setTitle(
          INFO_CTRL_PANEL_CHOOSE_REFERENCE_GROUP.get());
      referenceGroupPanel.setFilter(
          LDAPEntrySelectionPanel.Filter.DYNAMIC_GROUPS);
      referenceGroupPanel.setMultipleSelection(false);
      referenceGroupPanel.setInfo(getInfo());
      referenceGroupDlg = new GenericDialog(Utilities.getFrame(this),
          referenceGroupPanel);
      Utilities.centerGoldenMean(referenceGroupDlg,
          Utilities.getParentDialog(this));
      referenceGroupDlg.setModal(true);
    }
    referenceGroupDlg.setVisible(true);
    String[] dns = referenceGroupPanel.getDNs();
    if (dns.length > 0)
    {
      referenceGroup.setText(dns[0]);
    }
  }

  /**
   * {@inheritDoc}
   */
  protected String getLDIF()
  {
    StringBuilder sb = new StringBuilder();
    sb.append("dn: "+dn.getText()+"\n");

    String[] attrNames = {"cn", "description"};
    JTextField[] textFields = {name, description};
    for (int i=0; i<attrNames.length; i++)
    {
      String value = textFields[i].getText().trim();
      if (value.length() > 0)
      {
        sb.append(attrNames[i]+": "+value+"\n");
      }
    }

    sb.append("objectclass: top\n");
    if (staticGroup.isSelected())
    {
      sb.append("objectClass: "+ServerConstants.OC_GROUP_OF_UNIQUE_NAMES);
      String[] members = staticMembers.getText().split("\n");
      LinkedHashSet<DN> dns = new LinkedHashSet<DN>();
      for (String member : members)
      {
        member = member.trim();
        if (member.length() > 0)
        {
          try
          {
            dns.add(DN.decode(member));
          }
          catch (OpenDsException ode)
          {
            throw new IllegalStateException("Unexpected error decoding DN: "+
                member, ode);
          }
        }
      }

      for (DN dn : dns)
      {
        sb.append("\n"+ServerConstants.ATTR_UNIQUE_MEMBER+": "+dn.toString());
      }
    }
    else if (dynamicGroup.isSelected())
    {
      sb.append("objectClass: "+ServerConstants.OC_GROUP_OF_URLS+"\n");
      sb.append(ServerConstants.ATTR_MEMBER_URL+": "+filter.getText().trim());
    }
    else
    {
      sb.append("objectClass: ds-virtual-static-group\n");
      sb.append("objectClass: "+ServerConstants.OC_GROUP_OF_URLS+"\n");
      sb.append("ds-target-group-dn: "+referenceGroup.getText().trim());
    }

    return sb.toString();
  }

  private void checkEnabling()
  {
    staticMembers.setEnabled(staticGroup.isSelected());
    addMembers.setEnabled(staticGroup.isSelected());
    filter.setEnabled(dynamicGroup.isSelected());
    referenceGroup.setEnabled(virtualGroup.isSelected());
    browseReferenceGroup.setEnabled(virtualGroup.isSelected());

    lMemberDNs.setEnabled(staticGroup.isSelected());
    lLDAPURL.setEnabled(dynamicGroup.isSelected());
    lReferenceGroup.setEnabled(virtualGroup.isSelected());
  }
}

