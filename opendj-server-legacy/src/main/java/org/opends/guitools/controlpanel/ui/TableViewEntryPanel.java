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

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.AVA;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.opends.guitools.controlpanel.datamodel.BinaryValue;
import org.opends.guitools.controlpanel.datamodel.CustomSearchResult;
import org.opends.guitools.controlpanel.datamodel.ObjectClassValue;
import org.opends.guitools.controlpanel.datamodel.SortableTableModel;
import org.opends.guitools.controlpanel.task.OnlineUpdateException;
import org.opends.guitools.controlpanel.ui.renderer.AttributeCellEditor;
import org.opends.guitools.controlpanel.ui.renderer.LDAPEntryTableCellRenderer;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.OpenDsException;
import org.opends.server.types.Schema;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.ServerConstants;

/** The panel displaying a table view of an LDAP entry. */
class TableViewEntryPanel extends ViewEntryPanel
{
  private static final long serialVersionUID = 2135331526526472175L;
  private CustomSearchResult searchResult;
  private LDAPEntryTableModel tableModel;
  private LDAPEntryTableCellRenderer renderer;
  private JTable table;
  private boolean isReadOnly;
  private TreePath treePath;
  private JScrollPane scroll;
  private AttributeCellEditor editor;
  private JLabel requiredLabel;
  private JCheckBox showOnlyAttrsWithValues;

  /** Default constructor. */
  public TableViewEntryPanel()
  {
    super();
    createLayout();
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return table;
  }

  /** Creates the layout of the panel (but the contents are not populated here). */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 2;
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.weightx = 1.0;

    addTitlePanel(this, gbc);

    gbc.gridy ++;
    gbc.insets.top = 5;
    gbc.gridwidth = 1;
    showOnlyAttrsWithValues = Utilities.createCheckBox(
        INFO_CTRL_PANEL_SHOW_ATTRS_WITH_VALUES_LABEL.get());
    showOnlyAttrsWithValues.setSelected(displayOnlyWithAttrs);
    showOnlyAttrsWithValues.addActionListener(new ActionListener()
    {
       @Override
       public void actionPerformed(ActionEvent ev)
       {
         updateAttributeVisibility();
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

    gbc.gridy ++;
    gbc.gridx = 0;
    gbc.insets.top = 10;
    gbc.gridwidth = 2;
    tableModel = new LDAPEntryTableModel();
    renderer = new LDAPEntryTableCellRenderer();
    table = Utilities.createSortableTable(tableModel, renderer);
    renderer.setTable(table);
    editor = new AttributeCellEditor();
    table.getColumnModel().getColumn(1).setCellEditor(editor);
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridy ++;
    scroll = Utilities.createScrollPane(table);
    add(scroll, gbc);
  }

  @Override
  public void update(CustomSearchResult sr, boolean isReadOnly, TreePath path)
  {
    boolean sameEntry = false;
    if (searchResult != null && sr != null)
    {
      sameEntry = searchResult.getDN().equals(sr.getDN());
    }

    searchResult = sr;
    final Point p = sameEntry ? scroll.getViewport().getViewPosition() :
      new Point(0, 0);
    renderer.setSchema(getInfo().getServerDescriptor().getSchema());
    editor.setInfo(getInfo());
    requiredLabel.setVisible(!isReadOnly);
    this.isReadOnly = isReadOnly;
    this.treePath = path;
    updateTitle(sr, path);
    ignoreEntryChangeEvents = true;
    tableModel.displayEntry();
    Utilities.updateTableSizes(table);
    Utilities.updateScrollMode(scroll, table);
    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        if (p != null && scroll.getViewport().contains(p))
        {
          scroll.getViewport().setViewPosition(p);
        }
        ignoreEntryChangeEvents = false;
      }
    });
  }

  @Override
  public GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.NO_BUTTON;
  }

  @Override
  public Entry getEntry() throws OpenDsException
  {
    if (SwingUtilities.isEventDispatchThread())
    {
      editor.stopCellEditing();
    }
    else
    {
      try
      {
        SwingUtilities.invokeAndWait(new Runnable()
        {
          @Override
          public void run()
          {
            editor.stopCellEditing();
          }
        });
      }
      catch (Throwable ignore)
      {
        // ignored
      }
    }
    String ldif = getLDIF();
    try (LDIFImportConfig ldifImportConfig = new LDIFImportConfig(new StringReader(ldif));
        LDIFReader reader = new LDIFReader(ldifImportConfig))
    {
      Entry entry = reader.readEntry(checkSchema());
      addValuesInRDN(entry);
      return entry;
    }
    catch (IOException ioe)
    {
      throw new OnlineUpdateException(
          ERR_CTRL_PANEL_ERROR_CHECKING_ENTRY.get(ioe), ioe);
    }
  }

  /**
   * Returns the LDIF representation of the displayed entry.
   * @return the LDIF representation of the displayed entry.
   */
  private String getLDIF()
  {
    StringBuilder sb = new StringBuilder();
    sb.append("dn: ").append(getDisplayedDN());
    for (int i=0; i<tableModel.getRowCount(); i++)
    {
      String attrName = (String)tableModel.getValueAt(i, 0);
      if (!schemaReadOnlyAttributesLowerCase.contains(attrName.toLowerCase()))
      {
        Object value = tableModel.getValueAt(i, 1);
        appendLDIFLine(sb, attrName, value);
      }
    }
    return sb.toString();
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
        RDN rdn = oldDN.rdn();
        List<AVA> avas = new ArrayList<>();
        for (AVA ava : rdn)
        {
          AttributeType attrType = ava.getAttributeType();
          String attrName = ava.getAttributeName();
          ByteString value = ava.getAttributeValue();

          Set<String> values = getDisplayedStringValues(attrName);
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
        if (avas.isEmpty())
        {
          // Check the attributes in the order that we display them and use
          // the first one.
          Schema schema = getInfo().getServerDescriptor().getSchema();
          if (schema != null)
          {
            for (int i=0; i<table.getRowCount(); i++)
            {
              String attrName = (String)table.getValueAt(i, 0);
              if (isPassword(attrName)
                  || ServerConstants.OBJECTCLASS_ATTRIBUTE_TYPE_NAME.equals(attrName)
                  || !table.isCellEditable(i, 1))
              {
                continue;
              }
              Object o = table.getValueAt(i, 1);
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
        DN parent = oldDN.parent();
        if (!avas.isEmpty())
        {
          DN newParent = (parent != null) ? parent : DN.rootDN();
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

  private String getFirstNonEmpty(Set<String> values)
  {
    for (String v : values)
    {
      v = v.trim();
      if (v.length() > 0)
      {
        return v;
      }
    }
    return null;
  }

  private Set<String> getDisplayedStringValues(String attrName)
  {
    Set<String> values = new LinkedHashSet<>();
    for (int i=0; i<table.getRowCount(); i++)
    {
      if (attrName.equalsIgnoreCase((String)table.getValueAt(i, 0)))
      {
        Object o = table.getValueAt(i, 1);
        if (o instanceof String)
        {
          values.add((String)o);
        }
      }
    }
    return values;
  }

  private void updateAttributeVisibility()
  {
    tableModel.updateAttributeVisibility();
  }

  @Override
  protected List<Object> getValues(String attrName)
  {
    return tableModel.getValues(attrName);
  }

  /** The table model used by the tree in the panel. */
  private class LDAPEntryTableModel extends SortableTableModel
  implements Comparator<AttributeValuePair>
  {
    private static final long serialVersionUID = -1240282431326505113L;
    private final List<AttributeValuePair> dataArray = new ArrayList<>();
    private final SortedSet<AttributeValuePair> allSortedValues = new TreeSet<>(this);
    private final Set<String> requiredAttrs = new HashSet<>();
    private final String[] COLUMN_NAMES = new String[] {
        getHeader(LocalizableMessage.raw("Attribute"), 40),
        getHeader(LocalizableMessage.raw("Value", 40))};
    private int sortColumn;
    private boolean sortAscending = true;

    /**
     * Updates the contents of the table model with the
     * {@code TableViewEntryPanel.searchResult} object.
     */
    private void displayEntry()
    {
      updateDataArray();
      fireTableDataChanged();
    }

    /**
     * Updates the table model contents and sorts its contents depending on the
     * sort options set by the user.
     */
    @Override
    public void forceResort()
    {
      updateDataArray();
      fireTableDataChanged();
    }

    @Override
    public int compare(AttributeValuePair desc1, AttributeValuePair desc2)
    {
      int result;
      int[] possibleResults = {
          desc1.attrName.compareTo(desc2.attrName),
          compareValues(desc1.value, desc2.value)};
      result = possibleResults[sortColumn];
      if (result == 0)
      {
        for (int i : possibleResults)
        {
          if (i != 0)
          {
            result = i;
            break;
          }
        }
      }
      if (!sortAscending)
      {
        result = -result;
      }
      return result;
    }

    private int compareValues(Object o1, Object o2)
    {
      if (o1 == null)
      {
        return o2 == null ? 0 : -1;
      }
      else if (o2 == null)
      {
        return 1;
      }

      if (o1 instanceof ObjectClassValue)
      {
        o1 = renderer.getString((ObjectClassValue)o1);
      }
      else if (o1 instanceof BinaryValue)
      {
        o1 = renderer.getString((BinaryValue)o1);
      }
      else if (o1 instanceof byte[])
      {
        o1 = renderer.getString((byte[])o1);
      }
      if (o2 instanceof ObjectClassValue)
      {
        o2 = renderer.getString((ObjectClassValue)o2);
      }
      else if (o2 instanceof BinaryValue)
      {
        o2 = renderer.getString((BinaryValue)o2);
      }
      else if (o2 instanceof byte[])
      {
        o2 = renderer.getString((byte[])o2);
      }

      if (o1.getClass().equals(o2.getClass()))
      {
        if (o1 instanceof String)
        {
          return ((String)o1).compareTo((String)o2);
        }
        else if (o1 instanceof Integer)
        {
          return ((Integer)o1).compareTo((Integer)o2);
        }
        else if (o1 instanceof Long)
        {
          return ((Long)o1).compareTo((Long)o2);
        }
      }
      return String.valueOf(o1).compareTo(String.valueOf(o2));
    }

    @Override
    public int getColumnCount()
    {
      return COLUMN_NAMES.length;
    }

    @Override
    public int getRowCount()
    {
      return dataArray.size();
    }

    @Override
    public Object getValueAt(int row, int col)
    {
      AttributeValuePair attrValuePair = dataArray.get(row);
      return col == 0 ? attrValuePair.attrName : attrValuePair.value;
    }

    @Override
    public String getColumnName(int col) {
      return COLUMN_NAMES[col];
    }

    /**
     * Returns whether the sort is ascending or descending.
     * @return <CODE>true</CODE> if the sort is ascending and <CODE>false</CODE>
     * otherwise.
     */
    @Override
    public boolean isSortAscending()
    {
      return sortAscending;
    }

    /**
     * Sets whether to sort ascending of descending.
     * @param sortAscending whether to sort ascending or descending.
     */
    @Override
    public void setSortAscending(boolean sortAscending)
    {
      this.sortAscending = sortAscending;
    }

    /**
     * Returns the column index used to sort.
     * @return the column index used to sort.
     */
    @Override
    public int getSortColumn()
    {
      return sortColumn;
    }

    /**
     * Sets the column index used to sort.
     * @param sortColumn column index used to sort..
     */
    @Override
    public void setSortColumn(int sortColumn)
    {
      this.sortColumn = sortColumn;
    }

    @Override
    public boolean isCellEditable(int row, int col) {
      return col != 0
          && !isReadOnly
          && !schemaReadOnlyAttributesLowerCase.contains(dataArray.get(row).attrName.toLowerCase());
    }

    @Override
    public void setValueAt(Object value, int row, int col)
    {
      dataArray.get(row).value = value;
      if (value instanceof ObjectClassValue)
      {
        updateObjectClass((ObjectClassValue)value);
      }
      else
      {
        fireTableCellUpdated(row, col);

        notifyListeners();
      }
    }

    private void updateDataArray()
    {
      allSortedValues.clear();
      requiredAttrs.clear();
      List<String> addedAttrs = new ArrayList<>();
      Schema schema = getInfo().getServerDescriptor().getSchema();
      List<Object> ocs = null;
      for (String attrName : searchResult.getAttributeNames())
      {
        if (ServerConstants.OBJECTCLASS_ATTRIBUTE_TYPE_NAME.equalsIgnoreCase(attrName))
        {
          if (schema != null)
          {
            ocs = searchResult.getAttributeValues(attrName);
            ObjectClassValue ocValue = getObjectClassDescriptor(
                ocs, schema);
            allSortedValues.add(new AttributeValuePair(attrName, ocValue));
          }
        }
        else
        {
          for (Object v : searchResult.getAttributeValues(attrName))
          {
            allSortedValues.add(new AttributeValuePair(attrName, v));
          }
        }
        AttributeDescription attrDesc = AttributeDescription.valueOf(attrName);
        addedAttrs.add(attrDesc.getNameOrOID().toLowerCase());
      }
      if (ocs != null && schema != null)
      {
        for (Object oc : ocs)
        {
          ObjectClass objectClass = schema.getObjectClass((String) oc);
          if (!objectClass.isPlaceHolder())
          {
            for (AttributeType attr : objectClass.getRequiredAttributes())
            {
              String attrName = attr.getNameOrOID();
              String lowerCase = attrName.toLowerCase();
              if (!addedAttrs.contains(lowerCase))
              {
                allSortedValues.add(newAttributeValuePair(attrName));
              }
              requiredAttrs.add(lowerCase);
            }
            for (AttributeType attr : objectClass.getOptionalAttributes())
            {
              String attrName = attr.getNameOrOID();
              if (!addedAttrs.contains(attrName.toLowerCase()))
              {
                allSortedValues.add(newAttributeValuePair(attrName));
              }
            }
          }
        }
      }
      dataArray.clear();
      for (AttributeValuePair value : allSortedValues)
      {
        if (!showOnlyAttrsWithValues.isSelected() ||
            isRequired(value) || hasValue(value))
        {
          dataArray.add(value);
        }
      }
      renderer.setRequiredAttrs(requiredAttrs);
    }

    private AttributeValuePair newAttributeValuePair(String attrName)
    {
      if (isBinary(attrName) || isPassword(attrName))
      {
        return new AttributeValuePair(attrName, new byte[] {});
      }
      else
      {
        return new AttributeValuePair(attrName, "");
      }
    }

    /**
     * Checks if we have to display all the attributes or only those that
     * contain a value and updates the contents of the model accordingly.  Note
     * that even if the required attributes have no value they will be
     * displayed.
     */
    void updateAttributeVisibility()
    {
      dataArray.clear();
      for (AttributeValuePair value : allSortedValues)
      {
        if (!showOnlyAttrsWithValues.isSelected() ||
            isRequired(value) || hasValue(value))
        {
          dataArray.add(value);
        }
      }
      fireTableDataChanged();

      Utilities.updateTableSizes(table);
      Utilities.updateScrollMode(scroll, table);
    }

    /**
     * Returns the list of values associated with a given attribute.
     * @param attrName the name of the attribute.
     * @return the list of values associated with a given attribute.
     */
    public List<Object> getValues(String attrName)
    {
      List<Object> values = new ArrayList<>();
      for (AttributeValuePair valuePair : dataArray)
      {
        if (valuePair.attrName.equalsIgnoreCase(attrName)
            && hasValue(valuePair))
        {
          if (valuePair.value instanceof Collection<?>)
          {
            values.addAll((Collection<?>) valuePair.value);
          }
          else
          {
            values.add(valuePair.value);
          }
        }
      }
      return values;
    }

    private void updateObjectClass(ObjectClassValue newValue)
    {
      CustomSearchResult oldResult = searchResult;
      CustomSearchResult newResult =
        new CustomSearchResult(searchResult.getDN());

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
        for (AttributeValuePair currValue : allSortedValues)
        {
          AttributeDescription attrDesc = AttributeDescription.valueOf(currValue.attrName);
          String attrNoOptions = attrDesc.getNameOrOID().toLowerCase();
          if (attributes.contains(attrNoOptions)
              && !schemaReadOnlyAttributesLowerCase.contains(currValue.attrName.toLowerCase()))
          {
            setValues(newResult, currValue.attrName);
          }
        }
      }
      update(newResult, isReadOnly, treePath);
      ignoreEntryChangeEvents = false;
      searchResult = oldResult;
      notifyListeners();
    }

    private boolean isRequired(AttributeValuePair value)
    {
      AttributeDescription attrDesc = AttributeDescription.valueOf(value.attrName.toLowerCase());
      return requiredAttrs.contains(attrDesc.getNameOrOID());
    }

    private boolean hasValue(AttributeValuePair value)
    {
      boolean hasValue = value.value != null;
      if (hasValue)
      {
        if (value.value instanceof String)
        {
          return ((String) value.value).length() > 0;
        }
        else if (value.value instanceof byte[])
        {
          return ((byte[]) value.value).length > 0;
        }
      }
      return hasValue;
    }
  }

  /**
   * A simple class that contains an attribute name and a single value.  It is
   * used by the table model to be able to retrieve more easily all the values
   * for a given attribute.
   */
  private static class AttributeValuePair
  {
    /** The attribute name. */
    private final String attrName;
    /** The value. */
    private Object value;
    /**
     * Constructor.
     * @param attrName the attribute name.
     * @param value the value.
     */
    private AttributeValuePair(String attrName, Object value)
    {
      this.attrName = attrName;
      this.value = value;
    }
  }
}
