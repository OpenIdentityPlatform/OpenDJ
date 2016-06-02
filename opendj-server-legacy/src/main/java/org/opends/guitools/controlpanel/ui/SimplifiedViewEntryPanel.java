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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui;

import static com.forgerock.opendj.cli.Utils.*;

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
import java.util.Arrays;
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

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.AVA;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.opends.guitools.controlpanel.datamodel.BinaryValue;
import org.opends.guitools.controlpanel.datamodel.CheckEntrySyntaxException;
import org.opends.guitools.controlpanel.datamodel.CustomSearchResult;
import org.opends.guitools.controlpanel.datamodel.ObjectClassValue;
import org.opends.guitools.controlpanel.event.ScrollPaneBorderListener;
import org.opends.guitools.controlpanel.task.OnlineUpdateException;
import org.opends.guitools.controlpanel.ui.components.BinaryCellPanel;
import org.opends.guitools.controlpanel.ui.components.ObjectClassCellPanel;
import org.opends.guitools.controlpanel.ui.nodes.BrowserNodeInfo;
import org.opends.guitools.controlpanel.ui.nodes.DndBrowserNodes;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.server.schema.SchemaConstants;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.OpenDsException;
import org.opends.server.types.Schema;
import org.opends.server.util.Base64;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.ServerConstants;

/** The panel displaying a simplified view of an entry. */
class SimplifiedViewEntryPanel extends ViewEntryPanel
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

  private final Map<String, List<String>> lastUserPasswords = new HashMap<>();

  private CustomSearchResult searchResult;
  private boolean isReadOnly;
  private TreePath treePath;
  private JScrollPane scrollAttributes;

  private final Map<String, List<EditorComponent>> hmEditors = new LinkedHashMap<>();

  private final Set<String> requiredAttrs = new HashSet<>();
  private final Map<String, JComponent> hmLabels = new HashMap<>();
  private final Map<String, String> hmDisplayedNames = new HashMap<>();
  private final Map<String, JComponent> hmComponents = new HashMap<>();

  private final String CONFIRM_PASSWORD = "opendj-confirm-password";

  /** Map containing as key the attribute name and as value a localizable message. */
  private static final Map<String, LocalizableMessage> hmFriendlyAttrNames = new HashMap<>();
  /**
   * Map containing as key an object class and as value the preferred naming
   * attribute for the objectclass.
   */
  private static final Map<String, String> hmNameAttrNames = new HashMap<>();
  private static final Map<String, String[]> hmOrdereredAttrNames = new HashMap<>();
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
    hmFriendlyAttrNames.put(ServerConstants.ATTR_MEMBER,
        INFO_CTRL_PANEL_MEMBER_FRIENDLY_NAME.get());
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
  }

  private static final LocalizableMessage NAME = INFO_CTRL_PANEL_NAME_LABEL.get();

  /** Default constructor. */
  public SimplifiedViewEntryPanel()
  {
    super();
    createLayout();
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return null;
  }

  @Override
  public boolean requiresBorder()
  {
    return false;
  }

  /** Creates the layout of the panel (but the contents are not populated here). */
  private void createLayout()
  {
    dropTargetListener = new DropTargetListener()
    {
      @Override
      public void dragEnter(DropTargetDragEvent e)
      {
        // no-op
      }

      @Override
      public void dragExit(DropTargetEvent e)
      {
        // no-op
      }

      @Override
      public void dragOver(DropTargetDragEvent e)
      {
        // no-op
      }

      @Override
      public void dropActionChanged(DropTargetDragEvent e)
      {
        // no-op
      }

      @Override
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
        catch (IOException | UnsupportedFlavorException io)
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
       @Override
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
    scrollListener = ScrollPaneBorderListener.createBottomAndTopBorderListener(
        scrollAttributes);
    gbc.insets = new Insets(0, 0, 0, 0);
    add(scrollAttributes, gbc);
  }

  @Override
  public void update(CustomSearchResult sr, boolean isReadOnly, TreePath path)
  {
    boolean sameEntry = false;
    if (searchResult != null && sr != null)
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
        List<Object> values = sr.getAttributeValues(attr);
        JComponent comp = getReadOnlyComponent(attr, values);
        gbc.weightx = 0.0;
        gbc.anchor = anchor1(values);
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
      for (final String attr : sortedAttributes)
      {
        String lcAttr = attr.toLowerCase();
        JLabel label = getLabelForAttribute(attr, sr);
        if (isRequired(attr, sr))
        {
          Utilities.setRequiredIcon(label);
          requiredAttrs.add(lcAttr);
        }
        List<Object> values = sr.getAttributeValues(attr);
        if (values.isEmpty())
        {
          values = new ArrayList<>(1);
          if (isBinary(attr))
          {
            values.add(new byte[]{});
          }
          else
          {
            values.add("");
          }
        }

        final boolean isPasswordAttr = isPassword(attr);
        if (isPasswordAttr)
        {
          List<String> pwds = new ArrayList<>();
          for (Object o : values)
          {
            pwds.add(getPasswordStringValue(o));
          }
          lastUserPasswords.put(lcAttr, pwds);
        }

        JComponent comp = getReadWriteComponent(attr, values);
        gbc.weightx = 0.0;
        gbc.anchor = anchor2(attr, values);
        gbc.insets.left = 0;
        gbc.gridwidth = GridBagConstraints.RELATIVE;
        attributesPanel.add(label, gbc);
        gbc.insets.left = 10;
        gbc.weightx = 1.0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        attributesPanel.add(comp, gbc);
        gbc.insets.top = 10;
        hmLabels.put(lcAttr, label);
        hmComponents.put(lcAttr, comp);

        if (isPasswordAttr)
        {
          label = Utilities.createPrimaryLabel(
              INFO_CTRL_PANEL_PASSWORD_CONFIRM_LABEL.get());
          String key = getConfirmPasswordKey(attr);
          comp = getReadWriteComponent(key, values);

          hmLabels.put(key, label);
          hmComponents.put(key, comp);

          gbc.weightx = 0.0;
          gbc.anchor = isSingleValue(attr) ? GridBagConstraints.WEST : GridBagConstraints.NORTHWEST;
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
      @Override
      public void run()
      {
        if (p != null && scrollAttributes.getViewport().contains(p))
        {
          scrollAttributes.getViewport().setViewPosition(p);
        }
        ignoreEntryChangeEvents = false;
      }
    });
  }

  private int anchor2(final String attr, List<Object> values)
  {
    if (ServerConstants.OBJECTCLASS_ATTRIBUTE_TYPE_NAME.equalsIgnoreCase(attr))
    {
      int nOcs = 0;
      for (Object o : values)
      {
        if (!"top".equals(o))
        {
          nOcs++;
        }
      }
      return nOcs > 1 ? GridBagConstraints.NORTHWEST : GridBagConstraints.WEST;
    }
    else if (isSingleValue(attr))
    {
      return GridBagConstraints.WEST;
    }
    else if (values.size() <= 1 && (hasBinaryValue(values) || isBinary(attr)))
    {
      return GridBagConstraints.WEST;
    }
    else
    {
      return GridBagConstraints.NORTHWEST;
    }
  }

  private int anchor1(List<Object> values)
  {
    int size = values.size();
    if (size > 1)
    {
      return GridBagConstraints.NORTHWEST;
    }
    else if (size == 1)
    {
      Object v = values.get(0);
      if (v instanceof String && ((String) v).contains("\n"))
      {
        return GridBagConstraints.NORTHWEST;
      }
    }
    return GridBagConstraints.WEST;
  }

  private JLabel getLabelForAttribute(String attrName, CustomSearchResult sr)
  {
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
    if ("binary".equalsIgnoreCase(subType))
    {
      // TODO: use message
      subType = "binary";
    }

    LocalizableMessageBuilder l = new LocalizableMessageBuilder();
    boolean isNameAttribute = isAttrName(basicAttrName, sr);
    if (isNameAttribute)
    {
      l.append(NAME);
      if (subType != null)
      {
        l.append(" (").append(subType).append(")");
      }
    }
    else
    {
      LocalizableMessage friendly = hmFriendlyAttrNames.get(basicAttrName.toLowerCase());
      if (friendly != null)
      {
        l.append(friendly);
        if (subType != null)
        {
          l.append(" (").append(subType).append(")");
        }
      }
      else
      {
        l.append(attrName);
      }
    }
    hmDisplayedNames.put(attrName.toLowerCase(), l.toString());
    l.append(":");
    return Utilities.createPrimaryLabel(l.toMessage());
  }

  private Collection<String> getSortedAttributes(CustomSearchResult sr, boolean isReadOnly)
  {
    // Get all attributes that the entry can have
    Set<String> attributes = new LinkedHashSet<>();
    List<String> entryAttrs = new ArrayList<>(sr.getAttributeNames());
    List<String> attrsWithNoOptions = new ArrayList<>();
    for (String attr : entryAttrs)
    {
      AttributeDescription attrDesc = AttributeDescription.valueOf(attr);
      attrsWithNoOptions.add(attrDesc.getNameOrOID().toLowerCase());
    }

    // Put first the attributes associated with the objectclass in hmOrderedAttrNames
    LinkedHashSet<String> attrNames = new LinkedHashSet<>();
    List<Object> values = sr.getAttributeValues(ServerConstants.OBJECTCLASS_ATTRIBUTE_TYPE_NAME);
    for (Object o : values)
    {
      String ocName = (String)o;
      String[] attrs = hmOrdereredAttrNames.get(ocName.toLowerCase());
      if (attrs != null)
      {
        for (String attr : attrs)
        {
          int index = attrsWithNoOptions.indexOf(attr.toLowerCase());
          attrNames.add(index != -1 ? entryAttrs.get(index) : attr);
        }
      }
    }
    // Handle the root entry separately: most of its attributes are operational
    // so we filter a list of hardcoded attributes.
    boolean isRootEntry = "".equals(sr.getDN());
    Schema schema = getInfo().getServerDescriptor().getSchema();
    if (isRootEntry)
    {
      List<String> attrsNotToAdd = Arrays.asList("entryuuid", "hassubordinates",
          "numsubordinates", "subschemasubentry", "entrydn");
      for (String attr : sr.getAttributeNames())
      {
        if (!find(attrNames, attr) && !find(attrsNotToAdd, attr))
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

      SortedSet<String> requiredAttributes = new TreeSet<>();
      SortedSet<String> allowedAttributes = new TreeSet<>();

      if (schema != null)
      {
        List<Object> ocs = sr.getAttributeValues(
            ServerConstants.OBJECTCLASS_ATTRIBUTE_TYPE_NAME);
        for (Object oc : ocs)
        {
          ObjectClass objectClass = schema.getObjectClass((String) oc);
          if (!objectClass.isPlaceHolder())
          {
            for (AttributeType attr : objectClass.getRequiredAttributes())
            {
              requiredAttributes.add(attr.getNameOrOID());
            }
            for (AttributeType attr : objectClass.getOptionalAttributes())
            {
              allowedAttributes.add(attr.getNameOrOID());
            }
          }
        }
      }

      // Now try to put first the attributes for which we have a friendly
      // name (the most common ones).
      updateAttributes(attributes, requiredAttributes, entryAttrs, attrsWithNoOptions);
      updateAttributes(attributes, allowedAttributes, entryAttrs, attrsWithNoOptions);

      attributes.addAll(entryAttrs);
      attributes.add("aci");

      // In read-only mode display only the attributes with values
      if (isReadOnly)
      {
        attributes.retainAll(entryAttrs);
      }

      for (String attr : attributes)
      {
        boolean canAdd = isEditable(attr, schema);
        if (canAdd && !find(attrNames, attr))
        {
          attrNames.add(attr);
        }
      }
    }
    return attrNames;
  }

  private boolean find(Collection<String> attrNames, String attrNameToFind)
  {
    for (String attrName : attrNames)
    {
      if (attrName.equalsIgnoreCase(attrNameToFind))
      {
        return true;
      }
    }
    return false;
  }

  private void updateAttributes(
      Collection<String> attributes,
      Set<String> newAttributes,
      List<String> entryAttrs,
      List<String> attrsWithNoOptions)
  {
    for (String attr : newAttributes)
    {
      int index = attrsWithNoOptions.indexOf(attr.toLowerCase());
      if (index != -1)
      {
        attributes.add(entryAttrs.get(index));
      }
      else if (hasCertificateSyntax(attr))
      {
        attributes.add(attr + ";binary");
      }
      else
      {
        attributes.add(attr);
      }
    }
  }

  private boolean hasCertificateSyntax(String attrName)
  {
    Schema schema = getInfo().getServerDescriptor().getSchema();
    boolean isCertificate = false;
    // Check all the attributes that we consider binaries.
    if (schema != null)
    {
      String attributeName = AttributeDescription.valueOf(attrName).getNameOrOID().toLowerCase();
      if (schema.hasAttributeType(attributeName))
      {
        AttributeType attr = schema.getAttributeType(attributeName);
        Syntax syntax = attr.getSyntax();
        if (syntax != null)
        {
          isCertificate = SchemaConstants.SYNTAX_CERTIFICATE_OID.equals(syntax.getOID());
        }
      }
    }
    return isCertificate;
  }

  private JComponent getReadOnlyComponent(final String attrName, List<Object> values)
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

      if (ServerConstants.OBJECTCLASS_ATTRIBUTE_TYPE_NAME.equalsIgnoreCase(attrName))
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
                LocalizableMessage.raw(OBFUSCATED_VALUE)), gbc);
      }
      else if (!isBinary)
      {
        Set<String> sValues = toStrings(values);
        LocalizableMessage text = LocalizableMessage.raw(Utilities.getStringFromCollection(sValues, "\n"));
        final JTextArea ta;
        JComponent toAdd;
        if (values.size() > 15)
        {
          ta = Utilities.createNonEditableTextArea(text, 15, 20);
          toAdd = Utilities.createScrollPane(ta);
        }
        else
        {
          ta = Utilities.createNonEditableTextArea(text, values.size(), 20);
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
          @Override
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

  private Set<String> toStrings(Collection<Object> objects)
  {
    Set<String> results = new TreeSet<>();
    for (Object o : objects)
    {
      results.add(String.valueOf(o));
    }
    return results;
  }

  private JComponent getReadWriteComponent(final String attrName,
      List<Object> values)
  {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridy = 0;

    List<EditorComponent> components = new ArrayList<>();
    hmEditors.put(attrName.toLowerCase(), components);

    boolean isBinary = hasBinaryValue(values);
    for (Object o : values)
    {
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.weightx = 1.0;
      gbc.gridx = 0;
      if (ServerConstants.OBJECTCLASS_ATTRIBUTE_TYPE_NAME.equalsIgnoreCase(attrName))
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
          @Override
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
            if (ocDescriptor != null)
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
        JPasswordField pf = Utilities.createPasswordField();
        if (!"".equals(o))
        {
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
          gbc.gridx = 0;
          panel.add(tf, gbc);
          if (mustAddBrowseButton(attrName))
          {
            gbc.insets.left = 5;
            gbc.weightx = 0.0;
            gbc.gridx ++;
            gbc.anchor = GridBagConstraints.NORTH;
            JButton browse = Utilities.createButton(INFO_CTRL_PANEL_BROWSE_BUTTON_LABEL.get());
            browse.addActionListener(new AddBrowseClickedActionListener(tf, attrName));
            panel.add(browse, gbc);
            new DropTarget(tf, dropTargetListener);
          }
          components.add(new EditorComponent(tf));
        }
        else
        {
          Set<String> sValues = toStrings(values);
          final LocalizableMessage text = LocalizableMessage.raw(Utilities.getStringFromCollection(sValues, "\n"));
          final JTextArea ta;
          JComponent toAdd;
          if (values.size() > 15)
          {
            ta = Utilities.createTextArea(text, 15, 20);
            toAdd = Utilities.createScrollPane(ta);
          }
          else
          {
            ta = Utilities.createTextAreaWithBorder(text, values.size(), 20);
            toAdd = ta;
          }
          panel.add(toAdd, gbc);
          if (mustAddBrowseButton(attrName))
          {
            gbc.insets.left = 5;
            gbc.weightx = 0.0;
            gbc.gridx ++;
            gbc.anchor = GridBagConstraints.NORTH;
            final JButton browse = Utilities.createButton(
                INFO_CTRL_PANEL_BROWSE_BUTTON_LABEL.get());
            browse.addActionListener(new AddBrowseClickedActionListener(ta, attrName));
            if (ServerConstants.ATTR_UNIQUE_MEMBER_LC.equalsIgnoreCase(attrName))
            {
              browse.setText(
                  INFO_CTRL_PANEL_ADD_MEMBERS_BUTTON.get().toString());
            }
            panel.add(browse, gbc);
            new DropTarget(ta, dropTargetListener);
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
          @Override
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
              if (binaryValue.length > 0)
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
          @Override
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
    Schema schema = getInfo().getServerDescriptor().getSchema();
    if (schema != null && schema.hasAttributeType(attrName))
    {
      AttributeType attr = schema.getAttributeType(attrName);
      return attr.isSingleValue();
    }
    return false;
  }

  private boolean isRequired(String attrName, CustomSearchResult sr)
  {
    Schema schema = getInfo().getServerDescriptor().getSchema();
    if (schema != null)
    {
      AttributeDescription attrDesc = AttributeDescription.valueOf(attrName, schema.getSchemaNG());
      AttributeType attrType = attrDesc.getAttributeType();
      if (!attrType.isPlaceHolder())
      {
        List<Object> ocs = sr.getAttributeValues(ServerConstants.OBJECTCLASS_ATTRIBUTE_TYPE_NAME);
        for (Object oc : ocs)
        {
          ObjectClass objectClass = schema.getObjectClass(((String) oc));
          if (!objectClass.isPlaceHolder() && objectClass.isRequired(attrType))
          {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Override
  public GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.NO_BUTTON;
  }

  @Override
  public Entry getEntry() throws OpenDsException
  {
    final List<LocalizableMessage> errors = new ArrayList<>();

    try
    {
      DN.valueOf(getDisplayedDN());
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
      List<String> pwds = getNewPasswords(attrName);
      List<String> confirmPwds = getConfirmPasswords(attrName);
      if (!pwds.equals(confirmPwds))
      {
        setPrimaryInvalid(hmLabels.get(attrName));
        setPrimaryInvalid(hmLabels.get(getConfirmPasswordKey(attrName)));
        LocalizableMessage msg = ERR_CTRL_PANEL_PASSWORD_DO_NOT_MATCH.get();
        if (!errors.contains(msg))
        {
          errors.add(msg);
        }
      }
    }
    for (String attrName : requiredAttrs)
    {
      if (getValues(attrName).isEmpty())
      {
        setPrimaryInvalid(hmLabels.get(attrName));
        errors.add(ERR_CTRL_PANEL_ATTRIBUTE_REQUIRED.get(hmDisplayedNames.get(attrName)));
      }
    }

    if (!errors.isEmpty())
    {
      throw new CheckEntrySyntaxException(errors);
    }

    final String ldif = getLDIF();
    try (LDIFImportConfig ldifImportConfig = new LDIFImportConfig(new StringReader(ldif));
        LDIFReader reader = new LDIFReader(ldifImportConfig))
    {
      final Entry entry = reader.readEntry(checkSchema());
      addValuesInRDN(entry);
      return entry;
    }
    catch (IOException ioe)
    {
      throw new OnlineUpdateException(
          ERR_CTRL_PANEL_ERROR_CHECKING_ENTRY.get(ioe), ioe);
    }
  }

  private List<String> getDisplayedStringValues(String attrName)
  {
    List<String> values = new ArrayList<>();
    List<EditorComponent> comps =
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
        else if (value instanceof Collection<?>)
        {
          for (Object o : (Collection<?>)value)
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

  private List<String> getNewPasswords(String attrName)
  {
    AttributeDescription attrDesc = AttributeDescription.valueOf(attrName);
    return getDisplayedStringValues(attrDesc.getNameOrOID());
  }

  private List<String> getConfirmPasswords(String attrName)
  {
    return getDisplayedStringValues(getConfirmPasswordKey(attrName));
  }

  private String getConfirmPasswordKey(String attrName)
  {
    AttributeDescription attrDesc = AttributeDescription.valueOf(attrName);
    return CONFIRM_PASSWORD + attrDesc.getNameOrOID().toLowerCase();
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
    sb.append("dn: ").append(getDisplayedDN());

    for (String attrName : hmEditors.keySet())
    {
      if (isConfirmPassword(attrName))
      {
        continue;
      }
      else if (isPassword(attrName))
      {
        List<String> newPwds = getNewPasswords(attrName);
        if (newPwds.equals(lastUserPasswords.get(attrName.toLowerCase())))
        {
          List<Object> oldValues = searchResult.getAttributeValues(attrName);
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
      List<Object> values = searchResult.getAttributeValues(attrName);
      if (!values.isEmpty())
      {
        appendLDIFLines(sb, attrName, values);
      }
    }
    return sb.toString();
  }

  private boolean isAttrName(String attrName, CustomSearchResult sr)
  {
    List<Object> values = sr.getAttributeValues(ServerConstants.OBJECTCLASS_ATTRIBUTE_TYPE_NAME);
    for (Object o : values)
    {
      String ocName = (String)o;
      String attr = hmNameAttrNames.get(ocName.toLowerCase());
      if (attr != null && attr.equalsIgnoreCase(attrName))
      {
        return true;
      }
    }
    return false;
  }

  private boolean hasBinaryValue(List<Object> values)
  {
    return !values.isEmpty() && values.iterator().next() instanceof byte[];
  }

  private boolean mustAddBrowseButton(String attrName)
  {
    if (ServerConstants.ATTR_UNIQUE_MEMBER_LC.equalsIgnoreCase(attrName)
        || "ds-target-group-dn".equalsIgnoreCase(attrName))
    {
      return true;
    }
    Schema schema = getInfo().getServerDescriptor().getSchema();
    if (schema != null && schema.hasAttributeType(attrName))
    {
      AttributeType attr = schema.getAttributeType(attrName);
      // There is no name for a regex syntax.
      return SchemaConstants.SYNTAX_DN_NAME.equalsIgnoreCase(attr.getSyntax().getName());
    }
    return false;
  }

  @Override
  protected List<Object> getValues(String attrName)
  {
    List<Object> values = new ArrayList<>();
    for (EditorComponent comp : hmEditors.get(attrName))
    {
      if (hasValue(comp))
      {
        Object value = comp.getValue();
        if (value instanceof Collection<?>)
        {
          values.addAll((Collection<?>) value);
        }
        else
        {
          values.add(value);
        }
      }
    }
    return values;
  }

  private void appendLDIFLines(StringBuilder sb, String attrName)
  {
    {
      appendLDIFLines(sb, attrName, getValues(attrName));
    }
  }

  private void appendLDIFLines(StringBuilder sb, String attrName, List<Object> values)
  {
    for (Object value : values)
    {
      appendLDIFLine(sb, attrName, value);
    }
  }

  @Override
  protected String getDisplayedDN()
  {
    StringBuilder sb = new StringBuilder();
    try
    {
      DN oldDN = DN.valueOf(searchResult.getDN());
      if (oldDN.size() > 0)
      {
        List<AVA> avas = toAvas(oldDN.rdn());
        if (avas.isEmpty())
        {
          // Check the attributes in the order that we display them and use the first one.
          Schema schema = getInfo().getServerDescriptor().getSchema();
          if (schema != null)
          {
            for (String attrName : hmEditors.keySet())
            {
              if (isPassword(attrName) || isConfirmPassword(attrName))
              {
                continue;
              }
              List<EditorComponent> comps = hmEditors.get(attrName);
              if (!comps.isEmpty())
              {
                Object o = comps.iterator().next().getValue();
                if (o instanceof String)
                {
                  AttributeDescription attrDesc = AttributeDescription.valueOf(attrName, schema.getSchemaNG());
                  AttributeType attrType = attrDesc.getAttributeType();
                  if (!attrType.isPlaceHolder())
                  {
                    avas.add(new AVA(attrType, attrDesc.getNameOrOID(), o));
                  }
                  break;
                }
              }
            }
          }
        }
        DN parent = oldDN.parent();
        if (!avas.isEmpty())
        {
          DN newParent = parent != null ? parent : DN.rootDN();
          DN newDN = newParent.child(new RDN(avas));
          sb.append(newDN);
        }
        else if (parent != null)
        {
          sb.append(",").append(parent);
        }
      }
    }
    catch (Throwable t)
    {
      throw new RuntimeException("Unexpected error: "+t, t);
    }
    return sb.toString();
  }

  private List<AVA> toAvas(RDN rdn)
  {
    List<AVA> avas = new ArrayList<>();
    for (AVA ava : rdn)
    {
      AttributeType attrType = ava.getAttributeType();
      String attrName = ava.getAttributeName();
      ByteString value = ava.getAttributeValue();

      List<String> values = getDisplayedStringValues(attrName);
      if (!values.contains(value.toString()))
      {
        if (!values.isEmpty())
        {
          String firstNonEmpty = getFirstNonEmpty(values);
          if (firstNonEmpty != null)
          {
            avas.add(new AVA(attrType, attrName, ByteString.valueOfUtf8(firstNonEmpty)));
          }
        }
      }
      else
      {
        avas.add(new AVA(attrType, attrName, value));
      }
    }
    return avas;
  }

  private String getFirstNonEmpty(List<String> values)
  {
    for (String v : values)
    {
      v = v.trim();
      if (!v.isEmpty())
      {
        return v;
      }
    }
    return null;
  }

  private void addBrowseClicked(String attrName, JTextComponent textComponent)
  {
    LocalizableMessage previousTitle = null;
    LDAPEntrySelectionPanel.Filter previousFilter = null;
    LocalizableMessage title;
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
    if (ServerConstants.ATTR_UNIQUE_MEMBER_LC.equalsIgnoreCase(attrName))
    {
      title = INFO_CTRL_PANEL_ADD_MEMBERS_LABEL.get();
      filter = LDAPEntrySelectionPanel.Filter.USERS;
    }
    else if ("ds-target-group-dn".equalsIgnoreCase(attrName))
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
    String[] dns = browseEntriesPanel.getDNs();
    if (dns.length > 0)
    {
      if (textComponent instanceof JTextArea)
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
      else
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
      List<Object> values = searchResult.getAttributeValues(attrName);
      if (!values.isEmpty())
      {
        newResult.set(attrName, values);
      }
    }
    ignoreEntryChangeEvents = true;

    Schema schema = getInfo().getServerDescriptor().getSchema();
    if (schema != null)
    {
      ArrayList<String> attributes = new ArrayList<>();
      ArrayList<String> ocs = new ArrayList<>();
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
        if (!objectClass.isPlaceHolder())
        {
          for (AttributeType attr : objectClass.getRequiredAttributes())
          {
            attributes.add(attr.getNameOrOID().toLowerCase());
          }
          for (AttributeType attr : objectClass.getOptionalAttributes())
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
        String attrNoOptions = AttributeDescription.valueOf(attrName).getNameOrOID();
        if (!attributes.contains(attrNoOptions))
        {
          continue;
        }
        if (isPassword(attrName))
        {
          List<String> newPwds = getNewPasswords(attrName);
          if (newPwds.equals(lastUserPasswords.get(attrName)))
          {
            List<Object> oldValues = searchResult.getAttributeValues(attrName);
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
      final boolean visible = showAll || requiredAttrs.contains(attrName) || hasValue(hmEditors.get(attrName));
      hmLabels    .get(attrName).setVisible(visible);
      hmComponents.get(attrName).setVisible(visible);
    }
    repaint();
  }

  private boolean hasValue(List<EditorComponent> editors)
  {
    for (EditorComponent editor : editors)
    {
      if (hasValue(editor))
      {
        return true;
      }
    }
    return false;
  }

  private boolean hasValue(EditorComponent editor)
  {
    Object value = editor.getValue();
    if (value instanceof byte[])
    {
      return ((byte[])value).length > 0;
    }
    else if (value instanceof String)
    {
      return ((String)value).trim().length() > 0;
    }
    else if (value instanceof Collection<?>)
    {
      return !((Collection<?>)value).isEmpty();
    }
    return value != null;
  }

  /** Calls #addBrowseClicked(). */
  private final class AddBrowseClickedActionListener implements ActionListener
  {
    private final JTextComponent tc;
    private final String attrName;

    private AddBrowseClickedActionListener(JTextComponent tc, String attrName)
    {
      this.tc = tc;
      this.attrName = attrName;
    }

    @Override
    public void actionPerformed(ActionEvent ev)
    {
      addBrowseClicked(attrName, tc);
    }
  }

  /**
   * A class that makes an association between a component (JTextField, a
   * BinaryCellValue...) and the associated value that will be used to create
   * the modified entry corresponding to the contents of the panel.
   */
  private class EditorComponent
  {
    private final Component comp;

    /**
     * Creates an EditorComponent using a text component.
     * @param tf the text component.
     */
    private EditorComponent(JTextComponent tf)
    {
      comp = tf;
      tf.getDocument().addDocumentListener(new DocumentListener()
      {
        @Override
        public void insertUpdate(DocumentEvent ev)
        {
          notifyListeners();
        }

        @Override
        public void changedUpdate(DocumentEvent ev)
        {
          notifyListeners();
        }

        @Override
        public void removeUpdate(DocumentEvent ev)
        {
          notifyListeners();
        }
      });
    }

    /**
     * Creates an EditorComponent using a BinaryCellPanel.
     * @param binaryPanel the BinaryCellPanel.
     */
    private EditorComponent(BinaryCellPanel binaryPanel)
    {
      comp = binaryPanel;
    }

    /**
     * Creates an EditorComponent using a ObjectClassCellPanel.
     * @param ocPanel the ObjectClassCellPanel.
     */
    private EditorComponent(ObjectClassCellPanel ocPanel)
    {
      comp = ocPanel;
    }

    /**
     * Returns the value that the component is displaying.  The returned value
     * is a Set of Strings (for multi-valued attributes), a byte[] for binary
     * values or a String for single-valued attributes.   Single-valued
     * attributes refer to the definition in the schema (and not to the fact
     * that there is a single value for the attribute in this entry).
     * @return the value that the component is displaying.
     */
    public Object getValue()
    {
      if (comp instanceof ObjectClassCellPanel)
      {
        ObjectClassValue ocDesc = ((ObjectClassCellPanel)comp).getValue();
        LinkedHashSet<String> values = new LinkedHashSet<>();
        String structural = ocDesc.getStructural();
        if (structural != null)
        {
          values.add(structural);
        }
        values.addAll(ocDesc.getAuxiliary());
        Schema schema = getInfo().getServerDescriptor().getSchema();
        if (schema != null && structural != null)
        {
          ObjectClass oc = schema.getObjectClass(structural);
          if (!oc.isPlaceHolder())
          {
            values.addAll(getObjectClassSuperiorValues(oc));
          }
        }
        return values;
      } else if (comp instanceof JTextArea)
      {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        String value = ((JTextArea)comp).getText();
        String[] lines = value.split("\n");
        for (String line : lines)
        {
          line = line.trim();
          if (!line.isEmpty())
          {
            values.add(line);
          }
        }
        return values;
      }
      else if (comp instanceof JTextComponent)
      {
        return ((JTextComponent) comp).getText();
      }
      else
      {
        Object o = ((BinaryCellPanel)comp).getValue();
        if (o instanceof BinaryValue)
        {
          try
          {
            return ((BinaryValue) o).getBytes();
          }
          catch (ParseException pe)
          {
            throw new RuntimeException("Unexpected error: "+pe);
          }
        }
        return o;
      }
    }
  }
}
