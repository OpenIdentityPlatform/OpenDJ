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
import java.awt.Insets;
import java.awt.Point;
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
import java.io.StringReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import javax.swing.tree.TreePath;

import org.opends.guitools.controlpanel.datamodel.BinaryValue;
import org.opends.guitools.controlpanel.datamodel.CheckEntrySyntaxException;
import org.opends.guitools.controlpanel.datamodel.CustomSearchResult;
import org.opends.guitools.controlpanel.datamodel.ObjectClassValue;
import org.opends.guitools.controlpanel.event.ScrollPaneBorderListener;
import org.opends.guitools.controlpanel.task.OfflineUpdateException;
import org.opends.guitools.controlpanel.ui.components.BinaryCellPanel;
import org.opends.guitools.controlpanel.ui.components.ObjectClassCellPanel;
import org.opends.guitools.controlpanel.ui.nodes.BrowserNodeInfo;
import org.opends.guitools.controlpanel.ui.nodes.DndBrowserNodes;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.schema.SchemaConstants;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.OpenDsException;
import org.opends.server.types.RDN;
import org.opends.server.types.Schema;
import org.opends.server.util.Base64;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.ServerConstants;

/**
 * The panel displaying a simplified view of an entry.
 *
 */
public class SimplifiedViewEntryPanel extends ViewEntryPanel
{
  private static final long serialVersionUID = 2775960608128921072L;
  private JPanel attributesPanel;
  private ScrollPaneBorderListener scrollListener;
  private GenericDialog binaryDlg;
  private BinaryValuePanel binaryPanel;
  private GenericDialog editBinaryDlg;
  private BinaryAttributeEditorPanel editBinaryPanel;
  private GenericDialog editOcDlg;
  private ObjectClassEditorPanel editOcPanel;
  private JLabel requiredLabel;
  private JCheckBox showOnlyAttrsWithValues;

  private DropTargetListener dropTargetListener;

  private GenericDialog browseEntriesDlg;
  private LDAPEntrySelectionPanel browseEntriesPanel;

  private Map<String, Set<String>> lastUserPasswords =
    new HashMap<String,Set<String>>();

  private CustomSearchResult searchResult;
  private boolean isReadOnly;
  private TreePath treePath;
  private JScrollPane scrollAttributes;

  private LinkedHashMap<String, Set<EditorComponent>> hmEditors =
    new LinkedHashMap<String, Set<EditorComponent>>();

  private Set<String> requiredAttrs = new HashSet<String>();
  private Map<String, JComponent> hmLabels = new HashMap<String, JComponent>();
  private Map<String, String> hmDisplayedNames = new HashMap<String, String>();
  private Map<String, JComponent> hmComponents =
    new HashMap<String, JComponent>();

  private final String CONFIRM_PASSWORD = "confirm password";

  // Map containing as key the attribute name and as value a localizable
  // message.
  static Map<String, Message> hmFriendlyAttrNames =
    new HashMap<String, Message>();
  // Map containing as key an object class and as value the preferred naming
  // attribute for the objectclass.
  static Map<String, String> hmNameAttrNames = new HashMap<String, String>();
  static Map<String, String[]> hmOrdereredAttrNames =
    new HashMap<String, String[]>();
  static
  {
    hmFriendlyAttrNames.put(ServerConstants.OBJECTCLASS_ATTRIBUTE_TYPE_NAME,
        INFO_CTRL_PANEL_OBJECTCLASS_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put(ServerConstants.ATTR_COMMON_NAME,
        INFO_CTRL_PANEL_CN_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put("givenname",
        INFO_CTRL_PANEL_GIVENNAME_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put("sn",
        INFO_CTRL_PANEL_SN_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put("uid",
        INFO_CTRL_PANEL_UID_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put("employeenumber",
        INFO_CTRL_PANEL_EMPLOYEENUMBER_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put("userpassword",
        INFO_CTRL_PANEL_USERPASSWORD_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put("authpassword",
        INFO_CTRL_PANEL_AUTHPASSWORD_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put("mail",
        INFO_CTRL_PANEL_MAIL_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put("street",
        INFO_CTRL_PANEL_STREET_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put("l",
        INFO_CTRL_PANEL_L_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put("st",
        INFO_CTRL_PANEL_ST_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put("postalcode",
        INFO_CTRL_PANEL_POSTALCODE_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put("mobile",
        INFO_CTRL_PANEL_MOBILE_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put("homephone",
        INFO_CTRL_PANEL_HOMEPHONE_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put("telephonenumber",
        INFO_CTRL_PANEL_TELEPHONENUMBER_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put("pager",
        INFO_CTRL_PANEL_PAGER_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put("facsimiletelephonenumber",
        INFO_CTRL_PANEL_FACSIMILETELEPHONENUMBER_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put("description",
        INFO_CTRL_PANEL_DESCRIPTION_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put("postaladdress",
        INFO_CTRL_PANEL_POSTALADDRESS_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put(ServerConstants.ATTR_UNIQUE_MEMBER_LC,
        INFO_CTRL_PANEL_UNIQUEMEMBER_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put(ServerConstants.ATTR_MEMBER_URL_LC,
        INFO_CTRL_PANEL_MEMBERURL_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put(ServerConstants.ATTR_C,
        INFO_CTRL_PANEL_C_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put("ds-target-group-dn",
        INFO_CTRL_PANEL_DS_TARGET_GROUP_DN_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put("usercertificate",
        INFO_CTRL_PANEL_USERCERTIFICATE_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put("jpegphoto",
        INFO_CTRL_PANEL_JPEGPHOTO_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put(ServerConstants.ATTR_SUPPORTED_AUTH_PW_SCHEMES_LC,
        INFO_CTRL_PANEL_SUPPORTEDPWDSCHEMES_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put(ServerConstants.ATTR_SUPPORTED_CONTROL_LC,
        INFO_CTRL_PANEL_SUPPORTEDCONTROLS_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put(ServerConstants.ATTR_SUPPORTED_LDAP_VERSION_LC,
        INFO_CTRL_PANEL_SUPPORTEDLDAPVERSIONS_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put(ServerConstants.ATTR_SUPPORTED_CONTROL_LC,
        INFO_CTRL_PANEL_SUPPORTEDCONTROLS_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put(ServerConstants.ATTR_SUPPORTED_EXTENSION_LC,
        INFO_CTRL_PANEL_SUPPORTEDEXTENSIONS_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put(ServerConstants.ATTR_SUPPORTED_FEATURE_LC,
        INFO_CTRL_PANEL_SUPPORTEDFEATURES_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put(ServerConstants.ATTR_VENDOR_NAME_LC,
        INFO_CTRL_PANEL_VENDORNAME_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put(ServerConstants.ATTR_VENDOR_VERSION_LC,
        INFO_CTRL_PANEL_VENDORVERSION_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put(ServerConstants.ATTR_NAMING_CONTEXTS_LC,
        INFO_CTRL_PANEL_NAMINGCONTEXTS_FRIENDLY_NAME.get());
    hmFriendlyAttrNames.put(ServerConstants.ATTR_PRIVATE_NAMING_CONTEXTS,
        INFO_CTRL_PANEL_PRIVATENAMINGCONTEXTS_FRIENDLY_NAME.get());

    hmNameAttrNames.put("organizationalunit", ServerConstants.ATTR_OU);
    hmNameAttrNames.put("domain", ServerConstants.ATTR_DC);
    hmNameAttrNames.put("organization", ServerConstants.ATTR_O);
    hmNameAttrNames.put(ServerConstants.OC_GROUP_OF_URLS_LC,
        ServerConstants.ATTR_COMMON_NAME);
    hmNameAttrNames.put(ServerConstants.OC_GROUP_OF_NAMES_LC,
        ServerConstants.ATTR_COMMON_NAME);

    hmOrdereredAttrNames.put("person",
        new String[]{"givenname", "sn", ServerConstants.ATTR_COMMON_NAME, "uid",
        "userpassword", "mail", "telephonenumber", "facsimiletelephonenumber",
        "employeenumber", "street", "l", "st", "postalcode", "mobile",
        "homephone", "pager", "description", "postaladdress"});
    hmOrdereredAttrNames.put(ServerConstants.OC_GROUP_OF_NAMES_LC,
        new String[]{"cn", "description",
        ServerConstants.ATTR_UNIQUE_MEMBER_LC, "ds-target-group-dn"});
    hmOrdereredAttrNames.put(ServerConstants.OC_GROUP_OF_URLS_LC,
        new String[]{"cn", "description", ServerConstants.ATTR_MEMBER_URL_LC});
    hmOrdereredAttrNames.put("organizationalunit",
        new String[]{"ou", "description", "postalAddress", "telephonenumber",
    "facsimiletelephonenumber"});
    hmOrdereredAttrNames.put("organization", new String[]{"o", "description"});
    hmOrdereredAttrNames.put("domain", new String[]{"dc", "description"});
  };

  private Message NAME = INFO_CTRL_PANEL_NAME_LABEL.get();

  /**
   * Default constructor.
   *
   */
  public SimplifiedViewEntryPanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public boolean requiresBorder()
  {
    return false;
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
  private void createLayout()
  {
    dropTargetListener = new DropTargetListener()
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
          if (comp instanceof JTextArea)
          {
            JTextArea ta = (JTextArea)comp;
            StringBuilder sb = new StringBuilder();
            sb.append(ta.getText());
            for (BrowserNodeInfo node : nodes.getNodes())
            {
              if (sb.length() > 0)
              {
                sb.append("\n");
              }
              sb.append(node.getNode().getDN());
            }
            ta.setText(sb.toString());
            ta.setCaretPosition(sb.length());
          }
          else if (comp instanceof JTextField)
          {
            JTextField tf = (JTextField)comp;
            if (nodes.getNodes().length > 0)
            {
              String dn = nodes.getNodes()[0].getNode().getDN();
              tf.setText(dn);
              tf.setCaretPosition(dn.length());
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

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 2;
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.weightx = 1.0;
    gbc.insets = new Insets(10, 10, 0, 10);

    addTitlePanel(this, gbc);

    gbc.gridy ++;
    gbc.insets = new Insets(5, 10, 5, 10);

    gbc.gridwidth = 1;
    showOnlyAttrsWithValues =
      Utilities.createCheckBox(
          INFO_CTRL_PANEL_SHOW_ATTRS_WITH_VALUES_LABEL.get());
    showOnlyAttrsWithValues.setSelected(displayOnlyWithAttrs);
    showOnlyAttrsWithValues.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
       public void actionPerformed(ActionEvent ev)
       {
         updateAttributeVisibility(!showOnlyAttrsWithValues.isSelected());
         displayOnlyWithAttrs = showOnlyAttrsWithValues.isSelected();
       }
    });
    gbc.weightx = 0.0;
    gbc.anchor = GridBagConstraints.WEST;
    add(showOnlyAttrsWithValues, gbc);
    gbc.gridx ++;
    gbc.anchor = GridBagConstraints.EAST;
    gbc.fill = GridBagConstraints.NONE;
    requiredLabel = createRequiredLabel();
    add(requiredLabel, gbc);
    gbc.insets = new Insets(0, 0, 0, 0);
    add(Box.createVerticalStrut(10), gbc);

    showOnlyAttrsWithValues.setFont(requiredLabel.getFont());

    attributesPanel = new JPanel(new GridBagLayout());
    attributesPanel.setOpaque(false);
    attributesPanel.setBorder(new EmptyBorder(5, 10, 5, 10));
    gbc.gridx = 0;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.gridwidth = 2;
    gbc.gridy ++;
    gbc.fill = GridBagConstraints.BOTH;
    scrollAttributes = Utilities.createBorderLessScrollBar(attributesPanel);
    scrollListener = new ScrollPaneBorderListener(scrollAttributes, true);
    gbc.insets = new Insets(0, 0, 0, 0);
    add(scrollAttributes, gbc);
  }

  /**
   * {@inheritDoc}
   */
  public void update(CustomSearchResult sr, boolean isReadOnly, TreePath path)
  {
    boolean sameEntry = false;
    if ((searchResult != null) && (sr != null))
    {
      sameEntry = searchResult.getDN().equals(sr.getDN());
    }
    final Point p = sameEntry ?
        scrollAttributes.getViewport().getViewPosition() : new Point(0, 0);
    searchResult = sr;
    this.isReadOnly = isReadOnly;
    this.treePath = path;

    updateTitle(sr, path);

    requiredLabel.setVisible(!isReadOnly);
    showOnlyAttrsWithValues.setVisible(!isReadOnly);

    attributesPanel.removeAll();

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.HORIZONTAL;

    lastUserPasswords.clear();
    hmEditors.clear();

    hmLabels.clear();
    hmDisplayedNames.clear();
    hmComponents.clear();
    requiredAttrs.clear();


    // Build the attributes panel.
    Collection<String> sortedAttributes = getSortedAttributes(sr, isReadOnly);
    if (isReadOnly)
    {
      for (String attr : sortedAttributes)
      {
        JLabel label = getLabelForAttribute(attr, sr);
        Set<Object> values = sr.getAttributeValues(attr);
        JComponent comp = getReadOnlyComponent(attr, values);
        gbc.weightx = 0.0;
        if (values.size() > 1)
        {
          gbc.anchor = GridBagConstraints.NORTHWEST;
        }
        else
        {
          gbc.anchor = GridBagConstraints.WEST;
        }
        gbc.insets.left = 0;
        gbc.gridwidth = GridBagConstraints.RELATIVE;
        attributesPanel.add(label, gbc);
        gbc.insets.left = 10;
        gbc.weightx = 1.0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        attributesPanel.add(comp, gbc);
        gbc.insets.top = 10;
      }
    }
    else
    {
      for (String attr : sortedAttributes)
      {
        JLabel label = getLabelForAttribute(attr, sr);
        if (isRequired(attr, sr))
        {
          Utilities.setRequiredIcon(label);
          requiredAttrs.add(attr.toLowerCase());
        }
        Set<Object> values = sr.getAttributeValues(attr);
        if (values.isEmpty())
        {
          values = new HashSet<Object>(1);
          if (isBinary(attr))
          {
            values.add(new byte[]{});
          }
          else
          {
            values.add("");
          }
        }

        if (isPassword(attr))
        {
          Set<String> pwds = new HashSet<String>();
          for (Object o : values)
          {
            pwds.add(getPasswordStringValue(o));
          }
          lastUserPasswords.put(attr.toLowerCase(), pwds);
        }

        JComponent comp = getReadWriteComponent(attr, values);

        gbc.weightx = 0.0;
        if (attr.equalsIgnoreCase(
            ServerConstants.OBJECTCLASS_ATTRIBUTE_TYPE_NAME))
        {
          int nOcs = 0;
          for (Object o : values)
          {
            if (!"top".equals(o))
            {
              nOcs ++;
            }
          }
          if (nOcs > 1)
          {
            gbc.anchor = GridBagConstraints.NORTHWEST;
          }
          else
          {
            gbc.anchor = GridBagConstraints.WEST;
          }
        }
        else if (isSingleValue(attr))
        {
          gbc.anchor = GridBagConstraints.WEST;
        }
        else if ((values.size() <= 1) &&
                (hasBinaryValue(values) || isBinary(attr)))
        {
          gbc.anchor = GridBagConstraints.WEST;
        }
        else
        {
          gbc.anchor = GridBagConstraints.NORTHWEST;
        }
        gbc.insets.left = 0;
        gbc.gridwidth = GridBagConstraints.RELATIVE;
        attributesPanel.add(label, gbc);
        gbc.insets.left = 10;
        gbc.weightx = 1.0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        attributesPanel.add(comp, gbc);
        gbc.insets.top = 10;
        hmLabels.put(attr.toLowerCase(), label);
        hmComponents.put(attr.toLowerCase(), comp);

        if (isPassword(attr))
        {
          label = Utilities.createPrimaryLabel(
              INFO_CTRL_PANEL_PASSWORD_CONFIRM_LABEL.get());
          String key = getConfirmPasswordKey(attr);
          comp = getReadWriteComponent(key, values);

          hmLabels.put(key, label);
          hmComponents.put(key, comp);

          gbc.weightx = 0.0;
          if (isSingleValue(attr))
          {
            gbc.anchor = GridBagConstraints.WEST;
          }
          else
          {
            gbc.anchor = GridBagConstraints.NORTHWEST;
          }
          gbc.insets.left = 0;
          gbc.gridwidth = GridBagConstraints.RELATIVE;
          attributesPanel.add(label, gbc);
          gbc.insets.left = 10;
          gbc.weightx = 1.0;
          gbc.gridwidth = GridBagConstraints.REMAINDER;
          attributesPanel.add(comp, gbc);
          gbc.insets.top = 10;
        }
      }
    }
    gbc.weighty = 1.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.fill = GridBagConstraints.VERTICAL;
    gbc.insets = new Insets(0, 0, 0, 0);
    attributesPanel.add(Box.createVerticalGlue(), gbc);
    scrollListener.updateBorder();

    if (showOnlyAttrsWithValues.isSelected())
    {
      updateAttributeVisibility(false);
    }
    else if (isVisible())
    {
      repaint();
    }

    SwingUtilities.invokeLater(new Runnable()
    {
      /**
       * {@inheritDoc}
       */
      public void run()
      {
        if ((p != null) && (scrollAttributes.getViewport().contains(p)))
        {
          scrollAttributes.getViewport().setViewPosition(p);
        }
        ignoreEntryChangeEvents = false;
      }
    });
  }

  private JLabel getLabelForAttribute(String attrName, CustomSearchResult sr)
  {
    MessageBuilder l = new MessageBuilder();
    int index = attrName.indexOf(";");
    String basicAttrName;
    String subType;
    if (index == -1)
    {
      basicAttrName = attrName;
      subType = null;
    }
    else
    {
      basicAttrName = attrName.substring(0, index);
      subType = attrName.substring(index + 1);
    }
    if ((subType != null) && (subType.equalsIgnoreCase("binary")))
    {
      // TODO: use message
      subType = "binary";
    }
    boolean isNameAttribute = isAttrName(basicAttrName, sr);
    if (isNameAttribute)
    {
      if (subType != null)
      {
        l.append(NAME + " ("+subType+")");
      }
      else
      {
        l.append(NAME);
      }
    }
    else
    {
      Message friendly = hmFriendlyAttrNames.get(basicAttrName.toLowerCase());
      if (friendly == null)
      {
        l.append(attrName);
      }
      else
      {
        l.append(friendly);
        if (subType != null)
        {
          l.append(" ("+subType+")");
        }
      }
    }
    hmDisplayedNames.put(attrName.toLowerCase(), l.toString());
    l.append(":");
    return Utilities.createPrimaryLabel(l.toMessage());
  }

  private Collection<String> getSortedAttributes(CustomSearchResult sr,
      boolean isReadOnly)
  {
    LinkedHashSet<String> attrNames = new LinkedHashSet<String>();

//  Get all attributes that the entry can have
    Set<String> attributes = new LinkedHashSet<String>();
    ArrayList<String> entryAttrs = new ArrayList<String>();
    entryAttrs.addAll(sr.getAttributeNames());

    ArrayList<String> attrsWithNoOptions = new ArrayList<String>();
    for (String attr : entryAttrs)
    {
      attrsWithNoOptions.add(
            Utilities.getAttributeNameWithoutOptions(attr).toLowerCase());
    }

    Set<Object> values =
      sr.getAttributeValues(ServerConstants.OBJECTCLASS_ATTRIBUTE_TYPE_NAME);

    // Put first the attributes associated with the objectclass in
    // hmOrderedAttrNames
    for (Object o : values)
    {
      String ocName = (String)o;
      String[] attrs = hmOrdereredAttrNames.get(ocName.toLowerCase());
      if (attrs != null)
      {
        for (String attr : attrs)
        {
          int index = attrsWithNoOptions.indexOf(attr.toLowerCase());
          if (index != -1)
          {
            attrNames.add(entryAttrs.get(index));
          }
          else
          {
            attrNames.add(attr);
          }
        }
      }
    }
    // Handle the root entry separately: most of its attributes are operational
    // so we filter a list of harcoded attributes.
    boolean isRootEntry = sr.getDN().equals("");
    Schema schema = getInfo().getServerDescriptor().getSchema();
    if (isRootEntry)
    {
      String[] attrsNotToAdd = {"entryuuid", "hasnumsubordinates",
          "numsubordinates", "subschemasubentry", "entrydn",
      "hassubordinates"};
      for (String attr : sr.getAttributeNames())
      {
        boolean found = false;
        for (String addedAttr : attrNames)
        {
          found = addedAttr.equalsIgnoreCase(attr);
          if (found)
          {
            break;
          }
        }
        if (!found)
        {
          for (String notToAddAttr : attrsNotToAdd)
          {
            found = notToAddAttr.equalsIgnoreCase(attr);
            if (found)
            {
              break;
            }
          }
        }
        if (!found)
        {
          attrNames.add(attr);
        }
      }
    }
    else
    {
      // Try to get the attributes from the schema: first display the required
      // attributes with a friendly name (in alphabetical order), then (in
      // alphabetical order) the attributes with no friendly name.  Finally
      // do the same with the other attributes.

      SortedSet<String> requiredAttributes = new TreeSet<String>();
      SortedSet<String> allowedAttributes = new TreeSet<String>();

      if (schema != null)
      {
        Set<Object> ocs = sr.getAttributeValues(
            ServerConstants.OBJECTCLASS_ATTRIBUTE_TYPE_NAME);
        for (Object o : ocs)
        {
          String oc = (String)o;
          ObjectClass objectClass = schema.getObjectClass(oc.toLowerCase());
          if (objectClass != null)
          {
            for (AttributeType attr : objectClass.getRequiredAttributeChain())
            {
              requiredAttributes.add(attr.getNameOrOID());
            }
            for (AttributeType attr : objectClass.getOptionalAttributeChain())
            {
              allowedAttributes.add(attr.getNameOrOID());
            }
          }
        }
      }
      // Now try to put first the attributes for which we have a friendly
      // name (the most common ones).
      updateAttributes(attributes, requiredAttributes, entryAttrs,
          attrsWithNoOptions, true);
      updateAttributes(attributes, requiredAttributes, entryAttrs,
          attrsWithNoOptions, false);
      updateAttributes(attributes, allowedAttributes, entryAttrs,
          attrsWithNoOptions, true);
      updateAttributes(attributes, allowedAttributes, entryAttrs,
          attrsWithNoOptions, false);


      for (String attr : entryAttrs)
      {
        attributes.add(attr);
      }

      attributes.add("aci");

      // In read-only mode display only the attributes with values
      if (isReadOnly)
      {
        attributes.retainAll(entryAttrs);
      }

      for (String attr : attributes)
      {
        boolean add = isEditable(attr, schema);

        if (add)
        {
          boolean found = false;
          for (String addedAttr : attrNames)
          {
            found = addedAttr.equalsIgnoreCase(attr);
            if (found)
            {
              break;
            }
          }
          if (!found)
          {
            attrNames.add(attr);
          }
        }
      }
    }
    return attrNames;
  }

  private void updateAttributes(
      Collection<String> attributes,
      Set<String> newAttributes,
      ArrayList<String> entryAttrs,
      ArrayList<String> attrsWithNoOptions,
      boolean addIfFriendlyName)
  {
    for (String attr : newAttributes)
    {
      String attrLc = attr.toLowerCase();
      boolean hasFriendlyName = hmFriendlyAttrNames.get(attrLc) != null;
      if (hasFriendlyName == addIfFriendlyName)
      {
        int index = attrsWithNoOptions.indexOf(attrLc);
        if (index != -1)
        {
          attributes.add(entryAttrs.get(index));
        }
        else
        {
          if (!hasCertificateSyntax(attr,
              getInfo().getServerDescriptor().getSchema()))
          {
            attributes.add(attr);
          }
          else
          {
            attributes.add(attr+";binary");
          }
        }
      }
    }
  }

  private JComponent getReadOnlyComponent(final String attrName,
      Set<Object> values)
  {
//  GridLayout is used to avoid the 512 limit of GridBagLayout
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridy = 0;

    boolean isBinary = hasBinaryValue(values);
    for (Object o : values)
    {
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.weightx = 1.0;
      gbc.gridx = 0;

      if (attrName.equalsIgnoreCase(
          ServerConstants.OBJECTCLASS_ATTRIBUTE_TYPE_NAME))
      {
        ObjectClassCellPanel ocPanel = new ObjectClassCellPanel();
        Schema schema = getInfo().getServerDescriptor().getSchema();
        if (schema != null)
        {
          ObjectClassValue ocDescriptor = getObjectClassDescriptor(values,
              schema);
          ocPanel.setValue(ocDescriptor);
        }
        ocPanel.setEditButtonVisible(false);
        panel.add(ocPanel, gbc);
        break;
      }
      else if (Utilities.mustObfuscate(attrName,
          getInfo().getServerDescriptor().getSchema()))
      {
        panel.add(
            Utilities.createDefaultLabel(
                Message.raw(Utilities.OBFUSCATED_VALUE)), gbc);
      }
      else if (!isBinary)
      {
        Set<String> sValues = new TreeSet<String>();
        for (Object value : values)
        {
          sValues.add(String.valueOf(value));
        }
        final JTextArea ta;
        JComponent toAdd;
        if (values.size() > 15)
        {
          ta = Utilities.createNonEditableTextArea(
              Message.raw(Utilities.getStringFromCollection(sValues, "\n")),
              15, 20);
          toAdd = Utilities.createScrollPane(ta);
        }
        else
        {
          ta = Utilities.createNonEditableTextArea(
              Message.raw(Utilities.getStringFromCollection(sValues, "\n")),
              values.size(), 20);
          toAdd = ta;
        }
        panel.add(toAdd, gbc);
        break;
      }
      else
      {
        final BinaryCellPanel pane = new BinaryCellPanel();
        pane.setEditButtonText(INFO_CTRL_PANEL_VIEW_BUTTON_LABEL.get());
        final byte[] binaryValue = (byte[])o;
        Schema schema = getInfo().getServerDescriptor().getSchema();
        final boolean isImage = Utilities.hasImageSyntax(attrName, schema);
        pane.setValue(binaryValue, isImage);
        pane.addEditActionListener(new ActionListener()
        {
          /**
           * {@inheritDoc}
           */
          public void actionPerformed(ActionEvent ev)
          {
            if (binaryDlg == null)
            {
              binaryPanel = new BinaryValuePanel();
              binaryPanel.setInfo(getInfo());
              binaryDlg = new GenericDialog(
                  Utilities.getFrame(SimplifiedViewEntryPanel.this),
                  binaryPanel);
              binaryDlg.setModal(true);
              Utilities.centerGoldenMean(binaryDlg,
                  Utilities.getParentDialog(SimplifiedViewEntryPanel.this));
            }
            binaryPanel.setValue(attrName, binaryValue);
            binaryDlg.setVisible(true);
          }
        });
        panel.add(pane, gbc);
      }
    }
    return panel;
  }

  private JComponent getReadWriteComponent(final String attrName,
      Set<Object> values)
  {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridy = 0;

    Set<EditorComponent> components = new LinkedHashSet<EditorComponent>();
    hmEditors.put(attrName.toLowerCase(), components);

    boolean isBinary = hasBinaryValue(values);
    for (Object o : values)
    {
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.weightx = 1.0;
      gbc.gridx = 0;
      if (attrName.equalsIgnoreCase(
          ServerConstants.OBJECTCLASS_ATTRIBUTE_TYPE_NAME))
      {
        final ObjectClassCellPanel ocCellPanel = new ObjectClassCellPanel();
        Schema schema = getInfo().getServerDescriptor().getSchema();
        final ObjectClassValue ocDescriptor;
        if (schema != null)
        {
          ocDescriptor = getObjectClassDescriptor(values, schema);
          ocCellPanel.setValue(ocDescriptor);
        }
        else
        {
          ocDescriptor = null;
        }
        ocCellPanel.addEditActionListener(new ActionListener()
        {
          private ObjectClassValue newValue;
          /**
           * {@inheritDoc}
           */
          public void actionPerformed(ActionEvent ev)
          {
            if (editOcDlg == null)
            {
              editOcPanel = new ObjectClassEditorPanel();
              editOcPanel.setInfo(getInfo());
              editOcDlg = new GenericDialog(
                  null,
                  editOcPanel);
              editOcDlg.setModal(true);
              Utilities.centerGoldenMean(editOcDlg,
                  Utilities.getParentDialog(SimplifiedViewEntryPanel.this));
            }
            if ((newValue == null) && (ocDescriptor != null))
            {
              editOcPanel.setValue(ocDescriptor);
            }
            else
            {
              editOcPanel.setValue(newValue);
            }
            editOcDlg.setVisible(true);
            if (editOcPanel.valueChanged())
            {
              newValue = editOcPanel.getObjectClassValue();
              ocCellPanel.setValue(newValue);
              updatePanel(newValue);
            }
          }
        });
        panel = ocCellPanel;
        components.add(new EditorComponent(ocCellPanel));
        break;
      }
      else if (isPassword(attrName) || isConfirmPassword(attrName))
      {
        JPasswordField pf;
        if (o.equals(""))
        {
          pf = Utilities.createPasswordField();
        }
        else
        {
          pf = Utilities.createPasswordField();
          pf.setText(getPasswordStringValue(o));
        }
        panel.add(pf, gbc);
        components.add(new EditorComponent(pf));
      }
      else if (!isBinary)
      {
        if (isSingleValue(attrName))
        {
          final JTextField tf = Utilities.createMediumTextField();
          tf.setText(String.valueOf(o));
          if (mustAddBrowseButton(attrName))
          {
            gbc.gridx = 0;
            panel.add(tf, gbc);
            gbc.insets.left = 5;
            gbc.weightx = 0.0;
            gbc.gridx ++;
            gbc.anchor = GridBagConstraints.NORTH;
            JButton browse = Utilities.createButton(
                INFO_CTRL_PANEL_BROWSE_BUTTON_LABEL.get());
            browse.addActionListener(new ActionListener()
            {
              /**
               * {@inheritDoc}
               */
              public void actionPerformed(ActionEvent ev)
              {
                addBrowseClicked(attrName, tf);
              }
            });
            panel.add(browse, gbc);
            new DropTarget(tf, dropTargetListener);
          }
          else
          {
            gbc.gridx = 0;
            panel.add(tf, gbc);
          }
          components.add(new EditorComponent(tf));
        }
        else
        {
          Set<String> sValues = new TreeSet<String>();
          for (Object value : values)
          {
            sValues.add(String.valueOf(value));
          }
          final JTextArea ta;
          JComponent toAdd;
          if (values.size() > 15)
          {
            ta = Utilities.createTextArea(
                Message.raw(Utilities.getStringFromCollection(sValues, "\n")),
                15, 20);
            toAdd = Utilities.createScrollPane(ta);
          }
          else
          {
            ta = Utilities.createTextAreaWithBorder(
                Message.raw(Utilities.getStringFromCollection(sValues, "\n")),
                values.size(), 20);
            toAdd = ta;
          }
          if (mustAddBrowseButton(attrName))
          {
            panel.add(toAdd, gbc);
            gbc.insets.left = 5;
            gbc.weightx = 0.0;
            gbc.gridx ++;
            gbc.anchor = GridBagConstraints.NORTH;
            final JButton browse = Utilities.createButton(
                INFO_CTRL_PANEL_BROWSE_BUTTON_LABEL.get());
            browse.addActionListener(new ActionListener()
            {
              /**
               * {@inheritDoc}
               */
              public void actionPerformed(ActionEvent ev)
              {
                addBrowseClicked(attrName, ta);
              }
            });
            if (attrName.equalsIgnoreCase(
                ServerConstants.ATTR_UNIQUE_MEMBER_LC))
            {
              browse.setText(
                  INFO_CTRL_PANEL_ADD_MEMBERS_BUTTON.get().toString());
            }
            panel.add(browse, gbc);
            new DropTarget(ta, dropTargetListener);
          }
          else
          {
            panel.add(toAdd, gbc);
          }
          components.add(new EditorComponent(ta));
        }
        break;
      }
      else
      {
        final BinaryCellPanel pane = new BinaryCellPanel();
        Schema schema = getInfo().getServerDescriptor().getSchema();
        final boolean isImage = Utilities.hasImageSyntax(attrName, schema);
        pane.setDisplayDelete(true);
        final byte[] binaryValue = (byte[])o;
        if (binaryValue.length > 0)
        {
          pane.setValue(binaryValue, isImage);
        }
        pane.addEditActionListener(new ActionListener()
        {
          private BinaryValue newValue;
          /**
           * {@inheritDoc}
           */
          public void actionPerformed(ActionEvent ev)
          {
            if (editBinaryDlg == null)
            {
              editBinaryPanel = new BinaryAttributeEditorPanel();
              editBinaryPanel.setInfo(getInfo());
              editBinaryDlg = new GenericDialog(
                  Utilities.getFrame(SimplifiedViewEntryPanel.this),
                  editBinaryPanel);
              editBinaryDlg.setModal(true);
              Utilities.centerGoldenMean(editBinaryDlg,
                  Utilities.getParentDialog(SimplifiedViewEntryPanel.this));
            }
            if (newValue == null)
            {
              // We use an empty binary array to not breaking the logic:
              // it means that there is no value for the attribute.
              if ((binaryValue != null) && (binaryValue.length > 0))
              {
                newValue = BinaryValue.createBase64(binaryValue);
                editBinaryPanel.setValue(attrName, newValue);
              }
              else
              {
                editBinaryPanel.setValue(attrName, null);
              }
            }
            else
            {
              editBinaryPanel.setValue(attrName, newValue);
            }
            editBinaryDlg.setVisible(true);
            if (editBinaryPanel.valueChanged())
            {
              newValue = editBinaryPanel.getBinaryValue();
              pane.setValue(newValue, isImage);
              notifyListeners();
            }
          }
        });
        pane.addDeleteActionListener(new ActionListener()
        {
          /**
           * {@inheritDoc}
           */
          public void actionPerformed(ActionEvent ev)
          {
            pane.setValue((byte[])null, false);
            if (editBinaryPanel != null)
            {
              editBinaryPanel.setValue(attrName, null);
            }
            notifyListeners();
          }
        });
        panel.add(pane, gbc);
        components.add(new EditorComponent(pane));
      }
      gbc.gridy ++;
      gbc.insets.top = 10;
    }
    return panel;
  }

  private boolean isSingleValue(String attrName)
  {
    boolean isSingleValue = false;

    Schema schema = getInfo().getServerDescriptor().getSchema();
    if (schema != null)
    {
      AttributeType attr = schema.getAttributeType(attrName.toLowerCase());
      if (attr != null)
      {
        isSingleValue = attr.isSingleValue();
      }
    }

    return isSingleValue;
  }

  private boolean isRequired(String attrName, CustomSearchResult sr)
  {
    boolean isRequired = false;

    attrName = Utilities.getAttributeNameWithoutOptions(attrName);

    Schema schema = getInfo().getServerDescriptor().getSchema();
    if (schema != null)
    {
      AttributeType attr = schema.getAttributeType(attrName.toLowerCase());
      if (attr != null)
      {
        Set<Object> ocs = sr.getAttributeValues(
            ServerConstants.OBJECTCLASS_ATTRIBUTE_TYPE_NAME);
        for (Object o : ocs)
        {
          String oc = (String)o;
          ObjectClass objectClass = schema.getObjectClass(oc.toLowerCase());
          if (objectClass != null)
          {
            if (objectClass.isRequired(attr))
            {
              isRequired = true;
              break;
            }
          }
        }
      }
    }
    return isRequired;
  }

  /**
   * {@inheritDoc}
   */
  public GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.NO_BUTTON;
  }

  /**
   * {@inheritDoc}
   */
  public Entry getEntry() throws OpenDsException
  {
    Entry entry = null;

    ArrayList<Message> errors = new ArrayList<Message>();

    try
    {
      DN.decode(getDisplayedDN());
    }
    catch (Throwable t)
    {
      errors.add(ERR_CTRL_PANEL_DN_NOT_VALID.get());
    }

    for (String attrName : hmLabels.keySet())
    {
      setPrimaryValid(hmLabels.get(attrName));
    }


    // Check passwords
    for (String attrName : lastUserPasswords.keySet())
    {
      Set<String> pwds = getNewPasswords(attrName);
      Set<String> confirmPwds = getConfirmPasswords(attrName);
      if (!pwds.equals(confirmPwds))
      {
        setPrimaryInvalid(hmLabels.get(attrName));
        setPrimaryInvalid(hmLabels.get(getConfirmPasswordKey(attrName)));
        Message msg = ERR_CTRL_PANEL_PASSWORD_DO_NOT_MATCH.get();
        if (!errors.contains(msg))
        {
          errors.add(msg);
        }
      }
    }
    for (String attrName : requiredAttrs)
    {
      if (!hasValue(attrName))
      {
        setPrimaryInvalid(hmLabels.get(getConfirmPasswordKey(attrName)));
        errors.add(ERR_CTRL_PANEL_ATTRIBUTE_REQUIRED.get(
            hmDisplayedNames.get(attrName)));
      }
    }

    if (errors.size() > 0)
    {
      throw new CheckEntrySyntaxException(errors);
    }

    LDIFImportConfig ldifImportConfig = null;
    try
    {
      String ldif = getLDIF();

      ldifImportConfig = new LDIFImportConfig(new StringReader(ldif));
      LDIFReader reader = new LDIFReader(ldifImportConfig);
      entry = reader.readEntry(checkSchema());
      addValuesInRDN(entry);
    }
    catch (IOException ioe)
    {
      throw new OfflineUpdateException(
          ERR_CTRL_PANEL_ERROR_CHECKING_ENTRY.get(ioe.toString()),
          ioe);
    }
    finally
    {
      if (ldifImportConfig != null)
      {
        ldifImportConfig.close();
      }
    }
    return entry;
  }

  private Set<String> getDisplayedStringValues(String attrName)
  {
    Set<String> values = new LinkedHashSet<String>();
    Set<EditorComponent> comps =
      hmEditors.get(attrName.toLowerCase());
    if (comps != null)
    {
      for (EditorComponent comp : comps)
      {
        Object value = comp.getValue();
        if (value instanceof ObjectClassValue)
        {
          ObjectClassValue ocValue = (ObjectClassValue)value;
          if (ocValue.getStructural() != null)
          {
            values.add(ocValue.getStructural());
          }
          values.addAll(ocValue.getAuxiliary());
        }
        else if (value instanceof Collection)
        {
          for (Object o : (Collection)value)
          {
            values.add((String)o);
          }
        }
        else
        {
          values.add(String.valueOf(comp.getValue()));
        }
      }
    }
    return values;
  }

  private Set<String> getNewPasswords(String attrName)
  {
    String attr =
      Utilities.getAttributeNameWithoutOptions(attrName).toLowerCase();
    return getDisplayedStringValues(attr);
  }

  private Set<String> getConfirmPasswords(String attrName)
  {
    return getDisplayedStringValues(getConfirmPasswordKey(attrName));
  }

  private String getConfirmPasswordKey(String attrName)
  {
    return CONFIRM_PASSWORD+
    Utilities.getAttributeNameWithoutOptions(attrName).toLowerCase();
  }

  private boolean isConfirmPassword(String key)
  {
    return key.startsWith(CONFIRM_PASSWORD);
  }

  /**
   * Returns the LDIF representation of the displayed entry.
   * @return the LDIF representation of the displayed entry.
   */
  private String getLDIF()
  {
    StringBuilder sb = new StringBuilder();

    sb.append("dn: "+getDisplayedDN());

    for (String attrName : hmEditors.keySet())
    {
      if (isConfirmPassword(attrName))
      {
        continue;
      }
      else if (isPassword(attrName))
      {
        Set<String> newPwds = getNewPasswords(attrName);
        if (newPwds.equals(lastUserPasswords.get(attrName.toLowerCase())))
        {
          Set<Object> oldValues = searchResult.getAttributeValues(attrName);
          if (!oldValues.isEmpty())
          {
            appendLDIFLines(sb, attrName, oldValues);
          }
        }
        else
        {
          appendLDIFLines(sb, attrName);
        }
      }
      else
        if (!schemaReadOnlyAttributesLowerCase.contains(attrName.toLowerCase()))
        {
          appendLDIFLines(sb, attrName);
        }
    }

    // Add the attributes that are not displayed
    for (String attrName : schemaReadOnlyAttributesLowerCase)
    {
      Set<Object> values = searchResult.getAttributeValues(attrName);
      if (!values.isEmpty())
      {
        appendLDIFLines(sb, attrName, values);
      }
    }
    return sb.toString();
  }

  private boolean isAttrName(String attrName, CustomSearchResult sr)
  {
    boolean isAttrName = false;
    Set<Object> values =
      sr.getAttributeValues(ServerConstants.OBJECTCLASS_ATTRIBUTE_TYPE_NAME);
    for (Object o : values)
    {
      String ocName = (String)o;
      String attr = hmNameAttrNames.get(ocName.toLowerCase());
      if ((attr != null) && (attr.equalsIgnoreCase(attrName)))
      {
        isAttrName = true;
        break;
      }
    }

    return isAttrName;
  }

  private boolean hasBinaryValue(Set<Object> values)
  {
    boolean isBinary = false;
    if (values.size() > 0)
    {
      isBinary = values.iterator().next() instanceof byte[];
    }
    return isBinary;
  }

  private boolean mustAddBrowseButton(String attrName)
  {
    boolean mustAddBrowseButton =
      attrName.equalsIgnoreCase(ServerConstants.ATTR_UNIQUE_MEMBER_LC) ||
      attrName.equalsIgnoreCase("ds-target-group-dn");
    if (!mustAddBrowseButton)
    {
      Schema schema = getInfo().getServerDescriptor().getSchema();
      if (schema != null)
      {
        AttributeType attr = schema.getAttributeType(attrName.toLowerCase());
        if (attr != null)
        {
          mustAddBrowseButton =
            attr.getSyntax().getSyntaxName().equalsIgnoreCase(
                SchemaConstants.SYNTAX_DN_NAME);
        }
      }
    }
    return mustAddBrowseButton;
  }

  /**
   * {@inheritDoc}
   */
  protected Set<Object> getValues(String attrName)
  {
    Set<Object> values = new LinkedHashSet<Object>();
    Set<EditorComponent> comps = hmEditors.get(attrName);
    if (comps.size() > 0)
    {
      for (EditorComponent comp : comps)
      {
        if (hasValue(comp))
        {
          Object value = comp.getValue();
          if (value instanceof Collection)
          {
            for (Object o : (Collection)value)
            {
              values.add(o);
            }
          }
          else
          {
            values.add(value);
          }
        }
      }
    }
    return values;
  }

  private void appendLDIFLines(StringBuilder sb, String attrName)
  {
    Set<Object> values = getValues(attrName);

    if (values.size() > 0)
    {
      appendLDIFLines(sb, attrName, values);
    }
  }

  /**
   * {@inheritDoc}
   */
  protected String getDisplayedDN()
  {
    StringBuilder sb = new StringBuilder();
    try
    {
      DN oldDN = DN.decode(searchResult.getDN());
      if (oldDN.getNumComponents() > 0)
      {
        RDN rdn = oldDN.getRDN();
        List<AttributeType> attributeTypes = new ArrayList<AttributeType>();
        List<String> attributeNames = new ArrayList<String>();
        List<AttributeValue> attributeValues = new ArrayList<AttributeValue>();
        for (int i=0; i<rdn.getNumValues(); i++)
        {
          String attrName = rdn.getAttributeName(i);
          AttributeValue value = rdn.getAttributeValue(i);

          String sValue = value.getStringValue();

          Set<String> values = getDisplayedStringValues(attrName);
          if (!values.contains(sValue))
          {
            if (values.size() > 0)
            {
              String firstNonEmpty = null;
              for (String v : values)
              {
                v = v.trim();
                if (v.length() > 0)
                {
                  firstNonEmpty = v;
                  break;
                }
              }
              if (firstNonEmpty != null)
              {
                AttributeType attr = rdn.getAttributeType(i);
                attributeTypes.add(attr);
                attributeNames.add(rdn.getAttributeName(i));
                attributeValues.add(new AttributeValue(attr, firstNonEmpty));
              }
            }
          }
          else
          {
            attributeTypes.add(rdn.getAttributeType(i));
            attributeNames.add(rdn.getAttributeName(i));
            attributeValues.add(value);
          }
        }
        if (attributeTypes.size() == 0)
        {
          // Check the attributes in the order that we display them and use
          // the first one.
          Schema schema = getInfo().getServerDescriptor().getSchema();
          if (schema != null)
          {
            for (String attrName : hmEditors.keySet())
            {
              if (isPassword(attrName) ||
                  isConfirmPassword(attrName))
              {
                continue;
              }
              Set<EditorComponent> comps = hmEditors.get(attrName);
              if (comps.size() > 0)
              {
                Object o = comps.iterator().next().getValue();
                if (o instanceof String)
                {
                  String aName =
                    Utilities.getAttributeNameWithoutOptions(attrName);
                  AttributeType attr =
                    schema.getAttributeType(aName.toLowerCase());
                  if (attr != null)
                  {
                    attributeTypes.add(attr);
                    attributeNames.add(attrName);
                    attributeValues.add(new AttributeValue(attr, (String)o));
                  }
                  break;
                }
              }
            }
          }
        }
        DN parent = oldDN.getParent();
        if (attributeTypes.size() > 0)
        {
          DN newDN;
          RDN newRDN = new RDN(attributeTypes, attributeNames, attributeValues);

          if (parent == null)
          {
            newDN = new DN(new RDN[]{newRDN});
          }
          else
          {
            newDN = parent.concat(newRDN);
          }
          sb.append(newDN.toString());
        }
        else
        {
          if (parent != null)
          {
            sb.append(","+parent.toString());
          }
        }
      }
    }
    catch (Throwable t)
    {
      throw new IllegalStateException("Unexpected error: "+t, t);
    }
    return sb.toString();
  }

  private void addBrowseClicked(String attrName, JTextComponent textComponent)
  {
    Message previousTitle = null;
    LDAPEntrySelectionPanel.Filter previousFilter = null;
    Message title;
    LDAPEntrySelectionPanel.Filter filter;
    if (browseEntriesDlg == null)
    {
      browseEntriesPanel = new LDAPEntrySelectionPanel();
      browseEntriesPanel.setMultipleSelection(false);
      browseEntriesPanel.setInfo(getInfo());
      browseEntriesDlg = new GenericDialog(Utilities.getFrame(this),
          browseEntriesPanel);
      Utilities.centerGoldenMean(browseEntriesDlg,
          Utilities.getParentDialog(this));
      browseEntriesDlg.setModal(true);
    }
    else
    {
      previousTitle = browseEntriesPanel.getTitle();
      previousFilter = browseEntriesPanel.getFilter();
    }
    if (attrName.equalsIgnoreCase(ServerConstants.ATTR_UNIQUE_MEMBER_LC))
    {
      title = INFO_CTRL_PANEL_ADD_MEMBERS_LABEL.get();
      filter = LDAPEntrySelectionPanel.Filter.USERS;
    }
    else if (attrName.equalsIgnoreCase("ds-target-group-dn"))
    {
      title = INFO_CTRL_PANEL_CHOOSE_REFERENCE_GROUP.get();
      filter = LDAPEntrySelectionPanel.Filter.DYNAMIC_GROUPS;
    }
    else
    {
      title = INFO_CTRL_PANEL_CHOOSE_ENTRIES.get();
      filter = LDAPEntrySelectionPanel.Filter.DEFAULT;
    }
    if (!title.equals(previousTitle))
    {
      browseEntriesPanel.setTitle(title);
    }
    if (!filter.equals(previousFilter))
    {
      browseEntriesPanel.setFilter(filter);
    }
    browseEntriesPanel.setMultipleSelection(!isSingleValue(attrName));

    browseEntriesDlg.setVisible(true);
    if (textComponent instanceof JTextArea)
    {
      String[] dns = browseEntriesPanel.getDNs();
      if (dns.length > 0)
      {
        StringBuilder sb = new StringBuilder();
        sb.append(textComponent.getText());
        for (String dn : dns)
        {
          if (sb.length() > 0)
          {
            sb.append("\n");
          }
          sb.append(dn);
        }
        textComponent.setText(sb.toString());
        textComponent.setCaretPosition(sb.length());
      }
    }
    else
    {
      String[] dns = browseEntriesPanel.getDNs();
      if (dns.length > 0)
      {
        textComponent.setText(dns[0]);
      }
    }
  }

  private String getPasswordStringValue(Object o)
  {
    if (o instanceof byte[])
    {
      return Base64.encode((byte[])o);
    }
    else
    {
      return String.valueOf(o);
    }
  }

  private void updatePanel(ObjectClassValue newValue)
  {
    CustomSearchResult oldResult = searchResult;
    CustomSearchResult newResult = new CustomSearchResult(searchResult.getDN());

    for (String attrName : schemaReadOnlyAttributesLowerCase)
    {
      Set<Object> values = searchResult.getAttributeValues(attrName);
      if (!values.isEmpty())
      {
        newResult.set(attrName, values);
      }
    }
    ignoreEntryChangeEvents = true;

    Schema schema = getInfo().getServerDescriptor().getSchema();
    if (schema != null)
    {
      ArrayList<String> attributes = new ArrayList<String>();
      ArrayList<String> ocs = new ArrayList<String>();
      if (newValue.getStructural() != null)
      {
        ocs.add(newValue.getStructural().toLowerCase());
      }
      for (String oc : newValue.getAuxiliary())
      {
        ocs.add(oc.toLowerCase());
      }
      for (String oc : ocs)
      {
        ObjectClass objectClass = schema.getObjectClass(oc);
        if (objectClass != null)
        {
          for (AttributeType attr : objectClass.getRequiredAttributeChain())
          {
            attributes.add(attr.getNameOrOID().toLowerCase());
          }
          for (AttributeType attr : objectClass.getOptionalAttributeChain())
          {
            attributes.add(attr.getNameOrOID().toLowerCase());
          }
        }
      }
      for (String attrName : editableOperationalAttrNames)
      {
        attributes.add(attrName.toLowerCase());
      }
      for (String attrName : hmEditors.keySet())
      {
        String attrNoOptions =
          Utilities.getAttributeNameWithoutOptions(attrName);
        if (!attributes.contains(attrNoOptions))
        {
          continue;
        }
        if (isPassword(attrName))
        {
          Set<String> newPwds = getNewPasswords(attrName);
          if (newPwds.equals(lastUserPasswords.get(attrName)))
          {
            Set<Object> oldValues = searchResult.getAttributeValues(attrName);
            newResult.set(attrName, oldValues);
          }
          else
          {
            setValues(newResult, attrName);
          }
        }
        else if (!schemaReadOnlyAttributesLowerCase.contains(
          attrName.toLowerCase()))
        {
          setValues(newResult, attrName);
        }
      }
    }
    update(newResult, isReadOnly, treePath);
    ignoreEntryChangeEvents = false;
    searchResult = oldResult;
    notifyListeners();
  }

  private void updateAttributeVisibility(boolean showAll)
  {
    for (String attrName : hmLabels.keySet())
    {
      Component label = hmLabels.get(attrName);
      Component comp = hmComponents.get(attrName);

      if (showAll || requiredAttrs.contains(attrName))
      {
        label.setVisible(true);
        comp.setVisible(true);
      }
      else
      {
        Set<EditorComponent> editors = hmEditors.get(attrName);
        boolean hasValue = false;

        for (EditorComponent editor : editors)
        {
          hasValue = hasValue(editor);
          if (hasValue)
          {
            break;
          }
        }
        label.setVisible(hasValue);
        comp.setVisible(hasValue);
      }
    }
    repaint();
  }

  private boolean hasValue(String attrName)
  {
    return getValues(attrName).size() > 0;
  }

  private boolean hasValue(EditorComponent editor)
  {
    boolean hasValue = false;
    Object value = editor.getValue();
    if (value != null)
    {
      if (value instanceof byte[])
      {
        hasValue = ((byte[])value).length > 0;
      }
      else if (value instanceof String)
      {
        hasValue = ((String)value).trim().length() > 0;
      }
      else if (value instanceof Collection)
      {
        hasValue = ((Collection)value).size() > 0;
      }
      else
      {
        hasValue = true;
      }
    }
    return hasValue;
  }

  /**
   * A class that makes an association between a component (JTextField, a
   * BinaryCellValue...) and the associated value that will be used to create
   * the modified entry corresponding to the contents of the panel.
   *
   */
  class EditorComponent
  {
    private Component comp;

    /**
     * Creats an EditorComponent using a text component.
     * @param tf the text component.
     */
    public EditorComponent(JTextComponent tf)
    {
      comp = tf;
      tf.getDocument().addDocumentListener(new DocumentListener()
      {
        /**
         * {@inheritDoc}
         */
        public void insertUpdate(DocumentEvent ev)
        {
          notifyListeners();
        }

        /**
         * {@inheritDoc}
         */
        public void changedUpdate(DocumentEvent ev)
        {
          notifyListeners();
        }

        /**
         * {@inheritDoc}
         */
        public void removeUpdate(DocumentEvent ev)
        {
          notifyListeners();
        }
      });
    }

    /**
     * Creats an EditorComponent using a BinaryCellPanel.
     * @param binaryPanel the BinaryCellPanel.
     */
    public EditorComponent(BinaryCellPanel binaryPanel)
    {
      comp = binaryPanel;
    }

    /**
     * Creats an EditorComponent using a ObjectClassCellPanel.
     * @param ocPanel the ObjectClassCellPanel.
     */
    public EditorComponent(ObjectClassCellPanel ocPanel)
    {
      comp = ocPanel;
    }

    /**
     * Returns the value that the component is displaying.  The returned value
     * is a Set of Strings (for multivalued attributes), a byte[] for binary
     * values or a String for single-valued attributes.   Single-valued
     * attributes refer to the definition in the schema (and not to the fact
     * that there is a single value for the attribute in this entry).
     * @return the value that the component is displaying.
     */
    public Object getValue()
    {
      Object returnValue;
      if (comp instanceof ObjectClassCellPanel)
      {
        ObjectClassValue ocDesc = ((ObjectClassCellPanel)comp).getValue();
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        String structural = ocDesc.getStructural();
        if (structural != null)
        {
          values.add(structural);
        }
        values.addAll(ocDesc.getAuxiliary());
        Schema schema = getInfo().getServerDescriptor().getSchema();
        if (schema != null)
        {
          ObjectClass oc = schema.getObjectClass(structural.toLowerCase());
          if (oc != null)
          {
            ObjectClass parent = oc.getSuperiorClass();
            while (parent != null)
            {
              values.add(parent.getNameOrOID());
              parent = parent.getSuperiorClass();
            }
          }
        }
        returnValue = values;
      } else if (comp instanceof JTextArea)
      {
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        String value = ((JTextArea)comp).getText();
        String[] lines = value.split("\n");
        for (String line : lines)
        {
          line = line.trim();
          if (line.length() > 0)
          {
            values.add(line);
          }
        }
        returnValue = values;
      }
      else if (comp instanceof JTextComponent)
      {
        returnValue = ((JTextComponent)comp).getText();
      }
      else
      {
        Object o = ((BinaryCellPanel)comp).getValue();
        if (o instanceof BinaryValue)
        {
          try
          {
            returnValue = ((BinaryValue)o).getBytes();
          }
          catch (ParseException pe)
          {
            throw new IllegalStateException("Unexpected error: "+pe);
          }
        }
        else
        {
          returnValue = o;
        }
      }
      return returnValue;
    }
  }
}

