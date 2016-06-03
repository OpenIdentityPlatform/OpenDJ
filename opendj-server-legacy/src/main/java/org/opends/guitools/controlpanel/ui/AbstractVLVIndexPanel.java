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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui;

import static org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType.*;
import static org.opends.guitools.controlpanel.util.Utilities.*;
import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeSet;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.config.PropertyException;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.config.client.ldap.LDAPManagementContext;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.server.config.client.BackendVLVIndexCfgClient;
import org.forgerock.opendj.server.config.client.PluggableBackendCfgClient;
import org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType;
import org.forgerock.opendj.server.config.meta.BackendVLVIndexCfgDefn;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.datamodel.CategorizedComboBoxElement;
import org.opends.guitools.controlpanel.datamodel.IndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.datamodel.SomeSchemaElement;
import org.opends.guitools.controlpanel.datamodel.VLVIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVSortOrder;
import org.opends.guitools.controlpanel.ui.components.TitlePanel;
import org.opends.guitools.controlpanel.ui.renderer.CustomListCellRenderer;
import org.opends.guitools.controlpanel.ui.renderer.IndexComboBoxCellRenderer;
import org.opends.guitools.controlpanel.ui.renderer.VLVSortOrderRenderer;
import org.opends.guitools.controlpanel.util.LowerCaseComparator;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.quicksetup.Installation;
import org.opends.server.config.ConfigException;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.types.FilterType;
import org.opends.server.types.LDAPException;
import org.opends.server.types.OpenDsException;
import org.opends.server.types.RawFilter;
import org.opends.server.types.Schema;

/**
 * Abstract class used to re-factor some code between the classes that are used
 * to edit/create a VLV index.
 */
abstract class AbstractVLVIndexPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = -82857384664911898L;

  /** Title panel. */
  protected final TitlePanel titlePanel = new TitlePanel(LocalizableMessage.EMPTY, LocalizableMessage.EMPTY);
  /** Name label. */
  private final JLabel lName = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_VLV_INDEX_NAME_LABEL.get());
  /** Base DN label. */
  private final JLabel lBaseDN = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_VLV_INDEX_BASE_DN_LABEL.get());
  /** Search scope label. */
  private final JLabel lSearchScope = createPrimaryLabel(INFO_CTRL_PANEL_VLV_INDEX_SEARCH_SCOPE_LABEL.get());
  /** Search filter label. */
  private final JLabel lFilter = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_VLV_INDEX_FILTER_LABEL.get());
  /** Sort order label. */
  private final JLabel lSortOrder = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_VLV_INDEX_SORT_ORDER_LABEL.get());
  /** Backends label. */
  private final JLabel lBackend = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_BACKEND_LABEL.get());
  /** Max block size label. */
  private final JLabel lMaxBlockSize = createPrimaryLabel(INFO_CTRL_PANEL_VLV_INDEX_MAX_BLOCK_SIZE_LABEL.get());
  /** Read-only name label. */
  protected final JLabel readOnlyName = Utilities.createDefaultLabel();
  /** Read-only backend name label. */
  protected final JLabel backendName = Utilities.createDefaultLabel();

  /** Name text field. */
  protected final JTextField name = Utilities.createMediumTextField();
  /** Base DNs combo box. */
  protected final JComboBox<CharSequence> baseDNs = Utilities.createComboBox();
  /** Subtree text field. */
  protected final JTextField baseDN = Utilities.createLongTextField();

  /** Base Object scope radio button. */
  protected final JRadioButton baseObject = createRadioButton(INFO_CTRL_PANEL_VLV_INDEX_BASE_OBJECT_LABEL.get());
  /** Single Level scope radio button. */
  protected final JRadioButton singleLevel = createRadioButton(INFO_CTRL_PANEL_VLV_INDEX_SINGLE_LEVEL_LABEL.get());
  /** Subordinate subtree scope radio button. */
  protected final JRadioButton subordinateSubtree = Utilities
      .createRadioButton(INFO_CTRL_PANEL_VLV_INDEX_SUBORDINATE_SUBTREE_LABEL.get());
  /** Whole subtree scope radio button. */
  protected final JRadioButton wholeSubtree = Utilities
      .createRadioButton(INFO_CTRL_PANEL_VLV_INDEX_WHOLE_SUBTREE_LABEL.get());

  /** Filter text field. */
  protected final JTextField filter = Utilities.createLongTextField();
  /** Attributes combo box. */
  protected final JComboBox attributes = Utilities.createComboBox();

  /** The list containing the sort order elements. */
  protected final JList sortOrder = new JList();

  /** The add button. */
  private final JButton add = Utilities.createButton(INFO_CTRL_PANEL_VLV_INDEX_ADD_BUTTON_LABEL.get());
  /** The move up button. */
  private final JButton moveUp = Utilities.createButton(INFO_CTRL_PANEL_VLV_INDEX_MOVE_UP_BUTTON_LABEL.get());
  /** The move down button. */
  private final JButton moveDown = Utilities.createButton(INFO_CTRL_PANEL_VLV_INDEX_MOVE_DOWN_BUTTON_LABEL.get());
  /** The remove button. */
  protected final JButton remove = Utilities.createButton(INFO_CTRL_PANEL_VLV_INDEX_REMOVE_BUTTON_LABEL.get());

  /** Ascending order combo box. */
  private final JComboBox<LocalizableMessage> ascendingOrder = Utilities.createComboBox();

  /** Combo box containing the sort order. */
  protected DefaultListModel<VLVSortOrder> sortOrderModel;

  /** The list of labels. */
  private final JLabel[] labels = { lName, lBaseDN, lSearchScope, lFilter, lSortOrder, lBackend, lMaxBlockSize };

  /**
   * The relative component that must be used to center the parent dialog of
   * this panel.
   */
  private final Component relativeComponent;

  /** Other base DN message. */
  protected final LocalizableMessage OTHER_BASE_DN = INFO_CTRL_PANEL_VLV_OTHER_BASE_DN_LABEL.get();
  /** Ascending message. */
  private final LocalizableMessage ASCENDING = INFO_CTRL_PANEL_VLV_ASCENDING_LABEL.get();
  /** Descending message. */
  private final LocalizableMessage DESCENDING = INFO_CTRL_PANEL_VLV_DESCENDING_LABEL.get();

  /** Custom attributes message. */
  private final LocalizableMessage CUSTOM_ATTRIBUTES = INFO_CTRL_PANEL_CUSTOM_ATTRIBUTES_LABEL.get();
  /** Standard attributes message. */
  private final LocalizableMessage STANDARD_ATTRIBUTES = INFO_CTRL_PANEL_STANDARD_ATTRIBUTES_LABEL.get();

  /** The list of standard attribute names. */
  private final TreeSet<String> standardAttrNames = new TreeSet<>(new LowerCaseComparator());
  /** The list of configuration attribute names. */
  private final TreeSet<String> configurationAttrNames = new TreeSet<>(new LowerCaseComparator());
  /** The list of custom attribute names. */
  private final TreeSet<String> customAttrNames = new TreeSet<>(new LowerCaseComparator());

  /**
   * Constructor.
   *
   * @param backendID
   *          the backend ID where the index is defined (or will be defined).
   * @param relativeComponent
   *          the relative component where the dialog containing this panel must
   *          be centered.
   */
  protected AbstractVLVIndexPanel(String backendID, Component relativeComponent)
  {
    if (backendID != null)
    {
      backendName.setText(backendID);
    }
    this.relativeComponent = relativeComponent;
  }

  /**
   * Sets the name of the backend where the index is defined or will be defined.
   *
   * @param backendID
   *          the ID of the backend.
   */
  public void setBackendName(String backendID)
  {
    backendName.setText(backendID);
  }

  /**
   * Returns the scope of the VLV index as it appears on the panel.
   *
   * @return the scope of the VLV index as it appears on the panel.
   */
  protected SearchScope getScope()
  {
    if (baseObject.isSelected())
    {
      return SearchScope.BASE_OBJECT;
    }
    else if (singleLevel.isSelected())
    {
      return SearchScope.SINGLE_LEVEL;
    }
    else if (subordinateSubtree.isSelected())
    {
      return SearchScope.SUBORDINATES;
    }
    else if (wholeSubtree.isSelected())
    {
      return SearchScope.WHOLE_SUBTREE;
    }

    throw new IllegalStateException("At least one scope should be selected");
  }

  /**
   * Returns the list of VLV sort order elements as they are displayed in the
   * panel.
   *
   * @return the list of VLV sort order elements as they are displayed in the
   *         panel.
   */
  protected List<VLVSortOrder> getSortOrder()
  {
    List<VLVSortOrder> sortOrder = new ArrayList<>();
    for (int i = 0; i < sortOrderModel.getSize(); i++)
    {
      sortOrder.add(sortOrderModel.get(i));
    }
    return sortOrder;
  }

  /**
   * Returns the string representation for the provided list of VLV sort order.
   *
   * @param sortOrder
   *          the list of VLV sort order elements.
   * @return the string representation for the provided list of VLV sort order.
   */
  protected String getSortOrderStringValue(List<VLVSortOrder> sortOrder)
  {
    StringBuilder sb = new StringBuilder();
    for (VLVSortOrder s : sortOrder)
    {
      if (sb.length() > 0)
      {
        sb.append(" ");
      }
      if (s.isAscending())
      {
        sb.append("+");
      }
      else
      {
        sb.append("-");
      }
      sb.append(s.getAttributeName());
    }

    return sb.toString();
  }

  /**
   * Updates the layout with the provided server descriptor.
   *
   * @param desc
   *          the server descriptor.
   * @return <CODE>true</CODE> if an error has been displayed and
   *         <CODE>false</CODE> otherwise.
   */
  protected boolean updateLayout(final ServerDescriptor desc)
  {
    Schema schema = desc.getSchema();
    BackendDescriptor backend = getBackend();
    final boolean[] repack = { false };
    final boolean[] error = { false };
    if (backend != null)
    {
      updateBaseDNCombo(backend);
    }

    if (schema != null)
    {
      repack[0] = attributes.getItemCount() == 0;
      LinkedHashSet<CategorizedComboBoxElement> newElements = new LinkedHashSet<>();

      synchronized (standardAttrNames)
      {
        standardAttrNames.clear();
        configurationAttrNames.clear();
        customAttrNames.clear();

        for (AttributeType attr : schema.getAttributeTypes())
        {
          SomeSchemaElement element = new SomeSchemaElement(attr);
          String name = attr.getNameOrOID();
          if (!isDefined(name))
          {
            if (Utilities.isStandard(element))
            {
              standardAttrNames.add(name);
            }
            else if (Utilities.isConfiguration(element))
            {
              configurationAttrNames.add(name);
            }
            else
            {
              customAttrNames.add(name);
            }
          }
        }
      }
      if (!customAttrNames.isEmpty())
      {
        newElements.add(new CategorizedComboBoxElement(CUSTOM_ATTRIBUTES, CategorizedComboBoxElement.Type.CATEGORY));
        for (String attrName : customAttrNames)
        {
          newElements.add(new CategorizedComboBoxElement(attrName, CategorizedComboBoxElement.Type.REGULAR));
        }
      }
      if (!standardAttrNames.isEmpty())
      {
        newElements.add(new CategorizedComboBoxElement(STANDARD_ATTRIBUTES, CategorizedComboBoxElement.Type.CATEGORY));
        for (String attrName : standardAttrNames)
        {
          newElements.add(new CategorizedComboBoxElement(attrName, CategorizedComboBoxElement.Type.REGULAR));
        }
      }
      DefaultComboBoxModel model = (DefaultComboBoxModel) attributes.getModel();
      updateComboBoxModel(newElements, model);
    }
    else
    {
      updateErrorPane(errorPane, ERR_CTRL_PANEL_SCHEMA_NOT_FOUND_SUMMARY.get(), ColorAndFontConstants.errorTitleFont,
          ERR_CTRL_PANEL_SCHEMA_NOT_FOUND_DETAILS.get(), ColorAndFontConstants.defaultFont);
      repack[0] = true;
      error[0] = true;
    }

    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        if (getButtonType() == GenericDialog.ButtonType.OK)
        {
          setEnabledOK(!error[0]);
        }
        errorPane.setVisible(error[0]);
        add.setEnabled(attributes.getModel().getSize() > 0);
        remove.setEnabled(sortOrder.getSelectedIndex() != -1);
        if (repack[0])
        {
          packParentDialog();
          if (relativeComponent != null)
          {
            Utilities.centerGoldenMean(Utilities.getParentDialog(AbstractVLVIndexPanel.this), relativeComponent);
          }
        }
      }
    });

    return !error[0];
  }

  private boolean isDefined(String name)
  {
    ListModel model = sortOrder.getModel();
    for (int i = 0; i < model.getSize(); i++)
    {
      VLVSortOrder s = (VLVSortOrder) model.getElementAt(i);
      if (name.equalsIgnoreCase(s.getAttributeName()))
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns <CODE>true</CODE> if the user accepts to continue creating the VLV
   * index even if no indexes are created for the provided filter for the VLV
   * index. Returns <CODE>false</CODE> if the user does not accept to create the
   * index. Note that the confirmation dialog will only be displayed when the
   * indexes are not defined, if the dialog is not displayed the method returns
   * <CODE>true</CODE>.
   *
   * @return <CODE>true</CODE> if the user accepts to continue creating the VLV
   *         index even if no indexes are created for the provided filter for
   *         the VLV index. Returns <CODE>false</CODE> if the user does not
   *         accept to create the index.
   */
  protected boolean checkIndexRequired()
  {
    String f = filter.getText().trim();
    try
    {
      LDAPFilter ldapFilter = LDAPFilter.decode(f);
      ArrayList<LocalizableMessage> msgs = new ArrayList<>();
      updateIndexRequiredMessages(ldapFilter, msgs);
      if (!msgs.isEmpty())
      {
        StringBuilder sb = new StringBuilder();
        for (LocalizableMessage msg : msgs)
        {
          sb.append("<br>-").append(msg);
        }
        return displayConfirmationDialog(INFO_CTRL_PANEL_VLV_INDEXES_NOT_DEFINED_CONFIRMATION_TITLE.get(),
                INFO_CTRL_PANEL_VLV_INDEXES_NOT_DEFINED_CONFIRMATION_MSG.get(getBackend().getBackendID(), sb));
      }
      return true;
    }
    catch (Throwable t)
    {
      // Bug
      throw new RuntimeException("Unexpected error: " + t, t);
    }
  }

  /**
   * Updates the provided list of error messages by analyzing the provided
   * filter. The idea is basically to analyze the filter and check if what
   * appears on the filter is indexed or not. If it is not indexed it updates
   * the error message list with a message explaining that.
   *
   * @param filter
   *          the filter to analyze.
   * @param msgs
   *          the list of messages to be updated.
   */
  private void updateIndexRequiredMessages(RawFilter filter, Collection<LocalizableMessage> msgs)
  {
    switch (filter.getFilterType())
    {
    case AND:
    case OR:
      if (filter.getFilterComponents() != null)
      {
        for (RawFilter f : filter.getFilterComponents())
        {
          updateIndexRequiredMessages(f, msgs);
        }
      }
      break;
    case NOT:
      updateIndexRequiredMessages(filter.getNOTComponent(), msgs);
      break;
    default:
      FilterType[] filterTypes =
          { FilterType.EQUALITY, FilterType.SUBSTRING, FilterType.GREATER_OR_EQUAL, FilterType.LESS_OR_EQUAL,
            FilterType.PRESENT, FilterType.APPROXIMATE_MATCH, FilterType.EXTENSIBLE_MATCH };

      IndexType[] indexTypes = { EQUALITY, SUBSTRING, ORDERING, ORDERING, PRESENCE, APPROXIMATE, null };

      LocalizableMessage[] indexTypeNames =
          { INFO_CTRL_PANEL_VLV_INDEX_EQUALITY_TYPE.get(), INFO_CTRL_PANEL_VLV_INDEX_SUBSTRING_TYPE.get(),
            INFO_CTRL_PANEL_VLV_INDEX_ORDERING_TYPE.get(), INFO_CTRL_PANEL_VLV_INDEX_ORDERING_TYPE.get(),
            INFO_CTRL_PANEL_VLV_INDEX_PRESENCE_TYPE.get(), INFO_CTRL_PANEL_VLV_INDEX_APPROXIMATE_TYPE.get(), null };
      for (int i = 0; i < filterTypes.length; i++)
      {
        if (filterTypes[i] == filter.getFilterType())
        {
          IndexDescriptor index = getIndex(filter.getAttributeType());
          if (index != null)
          {
            IndexType type = indexTypes[i];
            if (type != null && !index.getTypes().contains(type))
            {
              msgs.add(INFO_CTRL_PANEL_MUST_UPDATE_INDEX_DEFINITION_TYPE.get(filter.getAttributeType(),
                  indexTypeNames[i]));
            }
          }
          else
          {
            LocalizableMessage type = indexTypeNames[i];
            if (type != null)
            {
              msgs.add(INFO_CTRL_PANEL_MUST_DEFINE_INDEX_TYPE.get(filter.getAttributeType(), type));
            }
            else
            {
              msgs.add(INFO_CTRL_PANEL_MUST_DEFINE_INDEX.get(filter.getAttributeType()));
            }
          }
        }
      }
    }
  }

  /**
   * Returns the index descriptor for a given index name (<CODE>null</CODE> if
   * no index descriptor is found for that name).
   *
   * @param indexName
   *          the name of the index.
   * @return the index descriptor for a given index name.
   */
  private IndexDescriptor getIndex(String indexName)
  {
    BackendDescriptor backend = getBackend();
    if (backend != null)
    {
      for (IndexDescriptor i : backend.getIndexes())
      {
        if (i.getName().equalsIgnoreCase(indexName))
        {
          return i;
        }
      }
    }
    return null;
  }

  /**
   * Updates the base DN combo box with the provided backend.
   *
   * @param backend
   *          the backend to be used with the provided backend.
   */
  protected void updateBaseDNCombo(BackendDescriptor backend)
  {
    List<Object> newElements = new ArrayList<>();
    for (BaseDNDescriptor baseDN : backend.getBaseDns())
    {
      String dn = null;
      try
      {
        dn = Utilities.unescapeUtf8(baseDN.getDn().toString());
      }
      catch (Throwable t)
      {
        throw new RuntimeException("Unexpected error: " + t, t);
      }
      newElements.add(dn);
    }
    newElements.add(COMBO_SEPARATOR);
    newElements.add(OTHER_BASE_DN);
    updateComboBoxModel(newElements, (DefaultComboBoxModel) baseDNs.getModel());
  }

  /**
   * Updates a list of errors with the errors found in the panel.
   *
   * @param checkName
   *          whether the name of the VLV index must be checked or not.
   * @return a list containing the error messages found.
   */
  protected List<LocalizableMessage> checkErrors(boolean checkName)
  {
    for (JLabel l : labels)
    {
      setPrimaryValid(l);
    }

    BackendDescriptor backend = getBackend();

    List<LocalizableMessage> errors = new ArrayList<>();
    if (checkName)
    {
      String n = name.getText();
      if (n.trim().length() == 0)
      {
        errors.add(ERR_CTRL_PANEL_NO_VLV_INDEX_NAME_PROVIDED.get());
        setPrimaryInvalid(lName);
      }
      else if (backend != null)
      {
        // Check that there is no other VLV index with same name
        for (VLVIndexDescriptor index : backend.getVLVIndexes())
        {
          if (index.getName().equalsIgnoreCase(n))
          {
            errors.add(ERR_CTRL_PANEL_VLV_INDEX_ALREADY_DEFINED.get(n, backendName.getText()));
            setPrimaryInvalid(lName);
            break;
          }
        }
      }
    }

    String baseDN = getBaseDN();
    if (baseDN == null || baseDN.length() == 0)
    {
      errors.add(ERR_CTRL_PANEL_NO_BASE_DN_FOR_VLV_PROVIDED.get());
      setPrimaryInvalid(lBaseDN);
    }
    else
    {
      try
      {
        DN.valueOf(baseDN);
      }
      catch (LocalizedIllegalArgumentException e)
      {
        errors.add(ERR_CTRL_PANEL_INVALID_BASE_DN_FOR_VLV_PROVIDED.get(e.getMessageObject()));
        setPrimaryInvalid(lBaseDN);
      }
    }

    String f = filter.getText().trim();
    if ("".equals(f))
    {
      errors.add(ERR_CTRL_PANEL_NO_FILTER_FOR_VLV_PROVIDED.get());
      setPrimaryInvalid(lFilter);
    }
    else
    {
      try
      {
        LDAPFilter.decode(f);
      }
      catch (LDAPException le)
      {
        errors.add(ERR_CTRL_PANEL_INVALID_FILTER_FOR_VLV_PROVIDED.get(le.getMessageObject()));
        setPrimaryInvalid(lFilter);
      }
    }

    if (sortOrder.getModel().getSize() == 0)
    {
      errors.add(ERR_CTRL_PANEL_NO_ATTRIBUTE_FOR_VLV_PROVIDED.get());
      setPrimaryInvalid(lSortOrder);
    }

    return errors;
  }

  /**
   * Returns the backend for the index.
   *
   * @return the backend for the index.
   */
  private BackendDescriptor getBackend()
  {
    for (BackendDescriptor b : getInfo().getServerDescriptor().getBackends())
    {
      if (b.getBackendID().equalsIgnoreCase(backendName.getText()))
      {
        return b;
      }
    }
    return null;
  }

  /**
   * Returns the base DN for the VLV index.
   *
   * @return the base DN for the VLV index.
   */
  protected String getBaseDN()
  {
    Object selectedItem = baseDNs.getSelectedItem();
    if (OTHER_BASE_DN.equals(selectedItem))
    {
      selectedItem = baseDN.getText().trim();
    }
    return selectedItem != null ? selectedItem.toString() : null;
  }

  /**
   * Returns the selected attribute.
   *
   * @return the selected attribute.
   */
  private String getSelectedAttribute()
  {
    CategorizedComboBoxElement o = (CategorizedComboBoxElement) attributes.getSelectedItem();
    return o != null ? o.getValue().toString() : null;
  }

  /**
   * Creates the basic layout of the panel.
   *
   * @param c
   *          the container of the layout.
   * @param gbc
   *          the grid bag constraints to be used.
   * @param nameReadOnly
   *          whether the panel is read-only or not.
   */
  protected void createBasicLayout(Container c, GridBagConstraints gbc, boolean nameReadOnly)
  {
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 3;
    addErrorPane(c, gbc);

    if (nameReadOnly)
    {
      gbc.gridy++;
      titlePanel.setTitle(INFO_CTRL_PANEL_VLV_INDEX_DETAILS_LABEL.get());
      gbc.fill = GridBagConstraints.NONE;
      gbc.anchor = GridBagConstraints.WEST;
      gbc.insets.top = 10;
      c.add(titlePanel, gbc);
    }

    gbc.gridy++;
    gbc.gridwidth = 1;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets.left = 0;
    gbc.gridx = 0;
    c.add(lName, gbc);
    gbc.insets.left = 10;
    gbc.gridx = 1;
    gbc.gridwidth = 2;

    if (nameReadOnly)
    {
      c.add(readOnlyName, gbc);
    }
    else
    {
      JPanel p = new JPanel(new GridBagLayout());
      p.setOpaque(false);
      c.add(p, gbc);
      GridBagConstraints gbc2 = new GridBagConstraints();
      gbc2.weightx = 0.3;
      gbc2.fill = GridBagConstraints.HORIZONTAL;
      gbc2.gridwidth = GridBagConstraints.RELATIVE;
      p.add(name, gbc2);
      gbc2.gridwidth = GridBagConstraints.REMAINDER;
      gbc2.weightx = 0.7;
      p.add(Box.createHorizontalGlue(), gbc2);
    }
    gbc.gridy++;

    gbc.insets.left = 0;
    gbc.insets.top = 10;
    gbc.gridx = 0;
    c.add(lBackend, gbc);
    gbc.insets.left = 10;
    gbc.gridx = 1;
    gbc.gridwidth = 2;
    c.add(backendName, gbc);
    gbc.gridy++;

    gbc.insets.left = 0;
    gbc.gridx = 0;
    gbc.gridwidth = 1;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    c.add(lBaseDN, gbc);
    gbc.anchor = GridBagConstraints.WEST;
    gbc.insets.left = 10;
    gbc.gridx = 1;
    gbc.gridwidth = 2;
    JPanel p = new JPanel(new GridBagLayout());
    p.setOpaque(false);
    c.add(p, gbc);
    gbc.gridy++;

    baseDNs.setModel(new DefaultComboBoxModel<CharSequence>(
        new CharSequence[] { COMBO_SEPARATOR, OTHER_BASE_DN }));
    baseDNs.setRenderer(new CustomListCellRenderer(baseDNs));
    ItemListener listener = new IgnoreItemListener(baseDNs);
    baseDNs.addItemListener(listener);
    baseDNs.addItemListener(new ItemListener()
    {
      @Override
      public void itemStateChanged(ItemEvent ev)
      {
        baseDN.setEnabled(OTHER_BASE_DN.equals(baseDNs.getSelectedItem()));
      }
    });
    listener.itemStateChanged(null);
    GridBagConstraints gbc2 = new GridBagConstraints();
    gbc2.fill = GridBagConstraints.HORIZONTAL;
    p.add(baseDNs, gbc2);
    gbc2.gridwidth = GridBagConstraints.REMAINDER;
    gbc2.weightx = 1.0;
    gbc2.insets.left = 5;
    p.add(baseDN, gbc2);
    gbc2.insets.top = 3;
    JLabel inlineHelp = Utilities.createInlineHelpLabel(INFO_CTRL_PANEL_SUBTREE_INLINE_HELP_LABEL.get());
    p.add(inlineHelp, gbc2);

    gbc.insets.left = 0;
    gbc.gridx = 0;
    gbc.gridwidth = 1;
    c.add(lSearchScope, gbc);
    gbc.insets.left = 10;
    gbc.gridx = 1;
    gbc.gridwidth = 2;
    JRadioButton[] radios = { baseObject, singleLevel, subordinateSubtree, wholeSubtree };
    singleLevel.setSelected(true);
    ButtonGroup group = new ButtonGroup();
    for (JRadioButton radio : radios)
    {
      c.add(radio, gbc);
      group.add(radio);
      gbc.insets.top = 5;
      gbc.gridy++;
    }

    gbc.insets.top = 10;
    gbc.insets.left = 0;
    gbc.gridx = 0;
    gbc.gridwidth = 1;
    c.add(lFilter, gbc);
    gbc.insets.left = 10;
    gbc.gridx = 1;
    gbc.gridwidth = 2;
    c.add(filter, gbc);
    gbc.gridy++;
    gbc.insets.top = 3;
    inlineHelp = Utilities.createInlineHelpLabel(INFO_CTRL_PANEL_FILTER_INLINE_HELP_LABEL.get());
    c.add(inlineHelp, gbc);
    gbc.gridy++;

    gbc.insets.top = 10;
    gbc.insets.left = 0;
    gbc.gridx = 0;
    gbc.gridwidth = 1;
    c.add(lSortOrder, gbc);
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets.left = 10;
    gbc.gridx = 1;
    attributes.addItemListener(new IgnoreItemListener(attributes));
    attributes.setRenderer(new IndexComboBoxCellRenderer(attributes));
    c.add(attributes, gbc);
    gbc.gridx++;

    ascendingOrder.setModel(new DefaultComboBoxModel<LocalizableMessage>(
        new LocalizableMessage[] { ASCENDING, DESCENDING }));
    c.add(ascendingOrder, gbc);
    gbc.gridy++;

    final ListSelectionListener listListener = new ListSelectionListener()
    {
      @Override
      public void valueChanged(ListSelectionEvent ev)
      {
        int[] indexes = sortOrder.getSelectedIndices();
        if (indexes != null && indexes.length > 0)
        {
          moveUp.setEnabled(indexes[0] != 0);
          moveDown.setEnabled(indexes[indexes.length - 1] != sortOrder.getModel().getSize() - 1);
          remove.setEnabled(true);
        }
        else
        {
          moveUp.setEnabled(false);
          moveUp.setEnabled(false);
          remove.setEnabled(false);
        }
      }
    };

    JButton[] buttons = { add, remove, moveUp, moveDown };
    for (JButton button : buttons)
    {
      button.setOpaque(false);
      button.setEnabled(false);
    }

    add.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        String attr = getSelectedAttribute();
        if (attr != null)
        {
          boolean isAscending = ASCENDING == ascendingOrder.getSelectedItem();
          sortOrderModel.addElement(new VLVSortOrder(attr, isAscending));
          DefaultComboBoxModel model = (DefaultComboBoxModel) attributes.getModel();
          int i = attributes.getSelectedIndex();
          if (i > 0)
          {
            // To avoid issues, try to figure out first the new selection
            int newIndex = -1;
            for (int j = i - 1; j > 0 && newIndex == -1; j--)
            {
              CategorizedComboBoxElement o = (CategorizedComboBoxElement) model.getElementAt(j);
              if (o.getType() == CategorizedComboBoxElement.Type.REGULAR)
              {
                newIndex = j;
              }
            }
            if (newIndex == -1)
            {
              for (int j = i + 1; j < model.getSize() && newIndex == -1; j++)
              {
                CategorizedComboBoxElement o = (CategorizedComboBoxElement) model.getElementAt(j);
                if (o.getType() == CategorizedComboBoxElement.Type.REGULAR)
                {
                  newIndex = j;
                }
              }
            }
            if (newIndex != -1)
            {
              attributes.setSelectedIndex(newIndex);
            }
            model.removeElementAt(i);
          }
        }
        listListener.valueChanged(null);
      }
    });
    moveUp.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        int[] indexes = sortOrder.getSelectedIndices();
        for (int i = 0; i < indexes.length; i++)
        {
          VLVSortOrder o1 = sortOrderModel.elementAt(indexes[i] - 1);
          VLVSortOrder o2 = sortOrderModel.elementAt(indexes[i]);
          sortOrderModel.set(indexes[i] - 1, o2);
          sortOrderModel.set(indexes[i], o1);

          indexes[i] = indexes[i] - 1;
        }
        sortOrder.setSelectedIndices(indexes);
        listListener.valueChanged(null);
      }
    });
    moveDown.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        int[] indexes = sortOrder.getSelectedIndices();
        for (int i = 0; i < indexes.length; i++)
        {
          VLVSortOrder o1 = sortOrderModel.elementAt(indexes[i] + 1);
          VLVSortOrder o2 = sortOrderModel.elementAt(indexes[i]);
          sortOrderModel.set(indexes[i] + 1, o2);
          sortOrderModel.set(indexes[i], o1);

          indexes[i] = indexes[i] + 1;
        }
        sortOrder.setSelectedIndices(indexes);
        listListener.valueChanged(null);
      }
    });
    remove.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        int[] indexes = sortOrder.getSelectedIndices();

        synchronized (standardAttrNames)
        {
          DefaultComboBoxModel model = (DefaultComboBoxModel) attributes.getModel();
          for (int index : indexes)
          {
            VLVSortOrder sortOrder = sortOrderModel.getElementAt(index);
            String attrName = sortOrder.getAttributeName();
            boolean isCustom = customAttrNames.contains(attrName);
            boolean dealingWithCustom = true;
            for (int j = 0; j < model.getSize(); j++)
            {
              CategorizedComboBoxElement o = (CategorizedComboBoxElement) model.getElementAt(j);
              if (o.getType() == CategorizedComboBoxElement.Type.REGULAR)
              {
                if (dealingWithCustom == isCustom && attrName.compareTo(o.getValue().toString()) < 0)
                {
                  model.insertElementAt(new CategorizedComboBoxElement(attrName,
                      CategorizedComboBoxElement.Type.REGULAR), j);
                  break;
                }
              }
              else if (!o.getValue().equals(CUSTOM_ATTRIBUTES))
              {
                dealingWithCustom = false;
                if (isCustom)
                {
                  model.insertElementAt(new CategorizedComboBoxElement(attrName,
                      CategorizedComboBoxElement.Type.REGULAR), j);
                  break;
                }
              }
            }
          }
        }

        for (int i = indexes.length - 1; i >= 0; i--)
        {
          sortOrderModel.remove(indexes[i]);
        }
        listListener.valueChanged(null);
      }
    });

    gbc.insets.top = 5;
    gbc.gridx = 1;
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.EAST;
    c.add(add, gbc);
    gbc.gridy++;

    gbc.insets.top = 10;
    gbc.gridwidth = 1;
    gbc.gridheight = 3;
    gbc.gridx = 1;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.anchor = GridBagConstraints.NORTH;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    sortOrderModel = new DefaultListModel();
    sortOrder.setModel(sortOrderModel);
    sortOrder.setCellRenderer(new VLVSortOrderRenderer(sortOrder));
    sortOrder.setVisibleRowCount(6);
    sortOrder.setPrototypeCellValue("AjA");
    c.add(Utilities.createScrollPane(sortOrder), gbc);
    sortOrder.addListSelectionListener(listListener);

    gbc.gridx = 2;
    gbc.weighty = 0.0;
    gbc.weightx = 0.0;
    gbc.gridheight = 1;
    gbc.insets.left = 5;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.WEST;
    c.add(moveUp, gbc);
    gbc.gridy++;
    gbc.insets.top = 5;
    c.add(moveDown, gbc);
    gbc.insets.top = 0;
    gbc.gridy++;
    gbc.weighty = 1.0;

    Dimension d =
        new Dimension(Math.max(moveUp.getPreferredSize().width, moveDown.getPreferredSize().width),
                      Math.max(moveUp.getPreferredSize().height, moveDown.getPreferredSize().height));
    moveUp.setPreferredSize(d);
    moveDown.setPreferredSize(d);

    c.add(Box.createVerticalGlue(), gbc);

    gbc.gridx = 1;
    gbc.gridy++;
    gbc.weighty = 0.0;
    gbc.anchor = GridBagConstraints.NORTHEAST;
    gbc.fill = GridBagConstraints.NONE;
    c.add(remove, gbc);
  }

  void createVLVIndexOffline(final String backendName, final String vlvIndexName, final DN baseDN, final String filter,
      final SearchScope searchScope, final List<VLVSortOrder> sortOrder) throws OpenDsException
  {
    updateVLVIndexOffline(backendName, vlvIndexName, null, baseDN, filter, searchScope, sortOrder);
  }

  void modifyVLVIndexOffline(final String backendName, final String vlvIndexName,
      final VLVIndexDescriptor indexToModify, final DN baseDN, final String filter, final SearchScope searchScope,
      final List<VLVSortOrder> sortOrder) throws OpenDsException
  {
    updateVLVIndexOffline(backendName, vlvIndexName, indexToModify, baseDN, filter, searchScope, sortOrder);
  }

  private void updateVLVIndexOffline(final String backendName, final String vlvIndexName,
      final VLVIndexDescriptor indexToModify, final DN baseDN, final String filter, final SearchScope searchScope,
      final List<VLVSortOrder> sortOrder) throws OpenDsException
  {
    getInfo().initializeConfigurationFramework();
    final File configFile = Installation.getLocal().getCurrentConfigurationFile();
    try (ManagementContext context = LDAPManagementContext.newLDIFManagementContext(configFile))
    {
      final PluggableBackendCfgClient backend =
          (PluggableBackendCfgClient) context.getRootConfiguration().getBackend(backendName);
      updateVLVBackendIndexOnline(backend, vlvIndexName, indexToModify, baseDN, filter, searchScope, sortOrder);
    }
    catch (final Exception e)
    {
      throw new ConfigException(LocalizableMessage.raw(e.getMessage(), e));
    }
  }

  private void updateVLVBackendIndexOnline(final PluggableBackendCfgClient backend, final String vlvIndexName,
      final VLVIndexDescriptor indexToModify, final DN baseDN, final String filter, final SearchScope searchScope,
      final List<VLVSortOrder> sortOrder) throws Exception
  {
    final boolean isCreation = indexToModify == null;
    final List<PropertyException> exceptions = new ArrayList<>();
    final BackendVLVIndexCfgClient index =
        isCreation ? backend.createBackendVLVIndex(BackendVLVIndexCfgDefn.getInstance(), vlvIndexName, exceptions)
                   : backend.getBackendVLVIndex(vlvIndexName);

    if (isCreation || !indexToModify.getBaseDN().equals(baseDN))
    {
      index.setBaseDN(baseDN);
    }

    if (isCreation || !indexToModify.getFilter().equals(filter))
    {
      index.setFilter(filter);
    }

    if (isCreation || indexToModify.getScope() != searchScope)
    {
      index.setScope(VLVIndexDescriptor.getBackendVLVIndexScope(searchScope));
    }

    if (isCreation || !indexToModify.getSortOrder().equals(sortOrder))
    {
      index.setSortOrder(getSortOrderStringValue(sortOrder));
    }
    index.commit();
    Utilities.throwFirstFrom(exceptions);
  }
}
