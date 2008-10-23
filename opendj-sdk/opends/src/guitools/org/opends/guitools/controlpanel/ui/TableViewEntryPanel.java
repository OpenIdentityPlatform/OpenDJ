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

import org.opends.guitools.controlpanel.datamodel.BinaryValue;
import org.opends.guitools.controlpanel.datamodel.CustomSearchResult;
import org.opends.guitools.controlpanel.datamodel.ObjectClassValue;
import org.opends.guitools.controlpanel.datamodel.SortableTableModel;
import org.opends.guitools.controlpanel.task.OfflineUpdateException;
import org.opends.guitools.controlpanel.ui.renderer.AttributeCellEditor;
import org.opends.guitools.controlpanel.ui.renderer.LDAPEntryTableCellRenderer;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.OpenDsException;
import org.opends.server.types.RDN;
import org.opends.server.types.Schema;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.ServerConstants;

/**
 * The panel displaying a table view of an LDAP entry.
 *
 */

public class TableViewEntryPanel extends ViewEntryPanel
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

  /**
   * Default constructor.
   *
   */
  public TableViewEntryPanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return table;
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
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
      /**
       * {@inheritDoc}
       */
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
    tableModel.displayEntry(searchResult);
    Utilities.updateTableSizes(table);
    Utilities.updateScrollMode(scroll, table);
    SwingUtilities.invokeLater(new Runnable()
    {
      public void run()
      {
        if ((p != null) && (scroll.getViewport().contains(p)))
        {
          scroll.getViewport().setViewPosition(p);
        }
        ignoreEntryChangeEvents = false;
      }
    });
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

  /**
   * Returns the LDIF representation of the displayed entry.
   * @return the LDIF representation of the displayed entry.
   */
  private String getLDIF()
  {
    StringBuilder sb = new StringBuilder();
    sb.append("dn: "+getDisplayedDN());
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
            for (int i=0; i<table.getRowCount(); i++)
            {
              String attrName = (String)table.getValueAt(i, 0);
              if (isPassword(attrName) ||
                  attrName.equals(
                      ServerConstants.OBJECTCLASS_ATTRIBUTE_TYPE_NAME) ||
                  !table.isCellEditable(i, 1))
              {
                continue;
              }
              Object o = table.getValueAt(i, 1);
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

  private Set<String> getDisplayedStringValues(String attrName)
  {
    Set<String> values = new LinkedHashSet<String>();
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

  /**
   * {@inheritDoc}
   */
  protected Set<Object> getValues(String attrName)
  {
    return tableModel.getValues(attrName);
  }

  /**
   * The table model used by the tree in the panel.
   *
   */
  protected class LDAPEntryTableModel extends SortableTableModel
  implements Comparator<AttributeValuePair>
  {
    private static final long serialVersionUID = -1240282431326505113L;
    private ArrayList<AttributeValuePair> dataArray =
      new ArrayList<AttributeValuePair>();
    private SortedSet<AttributeValuePair> allSortedValues =
      new TreeSet<AttributeValuePair>(this);
    Set<String> requiredAttrs = new HashSet<String>();
    private final String[] COLUMN_NAMES = new String[] {
        getHeader(Message.raw("Attribute"), 40),
        getHeader(Message.raw("Value", 40))};
    private int sortColumn = 0;
    private boolean sortAscending = true;

    /**
     * Sets the entry to be displayed by this table model.
     * @param searchResult the entry to be displayed.
     */
    public void displayEntry(CustomSearchResult searchResult)
    {
      updateDataArray();
      fireTableDataChanged();
    }

    /**
     * Updates the table model contents and sorts its contents depending on the
     * sort options set by the user.
     */
    public void forceResort()
    {
      updateDataArray();
      fireTableDataChanged();
    }

    /**
     * {@inheritDoc}
     */
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
        if (o2 == null)
        {
          return 0;
        }
        else
        {
          return -1;
        }
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
        else
        {
          return String.valueOf(o1).compareTo(String.valueOf(o2));
        }
      }
      else
      {
        return String.valueOf(o1).compareTo(String.valueOf(o2));
      }
    }

    /**
     * {@inheritDoc}
     */
    public int getColumnCount()
    {
      return COLUMN_NAMES.length;
    }

    /**
     * {@inheritDoc}
     */
    public int getRowCount()
    {
      return dataArray.size();
    }

    /**
     * {@inheritDoc}
     */
    public Object getValueAt(int row, int col)
    {
      if (col == 0)
      {
        return dataArray.get(row).attrName;
      }
      else
      {
        return dataArray.get(row).value;
      }
    }

    /**
     * {@inheritDoc}
     */
    public String getColumnName(int col) {
      return COLUMN_NAMES[col];
    }


    /**
     * Returns whether the sort is ascending or descending.
     * @return <CODE>true</CODE> if the sort is ascending and <CODE>false</CODE>
     * otherwise.
     */
    public boolean isSortAscending()
    {
      return sortAscending;
    }

    /**
     * Sets whether to sort ascending of descending.
     * @param sortAscending whether to sort ascending or descending.
     */
    public void setSortAscending(boolean sortAscending)
    {
      this.sortAscending = sortAscending;
    }

    /**
     * Returns the column index used to sort.
     * @return the column index used to sort.
     */
    public int getSortColumn()
    {
      return sortColumn;
    }

    /**
     * Sets the column index used to sort.
     * @param sortColumn column index used to sort..
     */
    public void setSortColumn(int sortColumn)
    {
      this.sortColumn = sortColumn;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isCellEditable(int row, int col) {
      if (col == 0)
      {
        return false;
      }
      else
      {
        if (!isReadOnly)
        {
          return !schemaReadOnlyAttributesLowerCase.contains(
              dataArray.get(row).attrName.toLowerCase());
        }
        else
        {
          return false;
        }
      }
    }

    /**
     * {@inheritDoc}
     */
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
      Set<String> addedAttrs = new HashSet<String>();
      Schema schema = getInfo().getServerDescriptor().getSchema();
      Set<Object> ocs = null;
      for (String attrName : searchResult.getAttributeNames())
      {
        if (attrName.equalsIgnoreCase(
            ServerConstants.OBJECTCLASS_ATTRIBUTE_TYPE_NAME))
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
        addedAttrs.add(
            Utilities.getAttributeNameWithoutOptions(attrName).toLowerCase());
      }
      if ((ocs != null) && (schema != null))
      {
        for (Object o : ocs)
        {
          String oc = (String)o;
          ObjectClass objectClass = schema.getObjectClass(oc.toLowerCase());
          if (objectClass != null)
          {
            for (AttributeType attr : objectClass.getRequiredAttributeChain())
            {
              String attrName = attr.getNameOrOID();
              if (!addedAttrs.contains(attrName.toLowerCase()))
              {
                if (isBinary(attrName) || isPassword(attrName))
                {
                  allSortedValues.add(new AttributeValuePair(attrName,
                      new byte[]{}));
                }
                else
                {
                  allSortedValues.add(new AttributeValuePair(attrName, ""));
                }
              }
              requiredAttrs.add(attrName.toLowerCase());
            }
            for (AttributeType attr : objectClass.getOptionalAttributeChain())
            {
              String attrName = attr.getNameOrOID();
              if (!addedAttrs.contains(attrName.toLowerCase()))
              {
                if (isBinary(attrName) || isPassword(attrName))
                {
                  allSortedValues.add(new AttributeValuePair(attrName,
                      new byte[]{}));
                }
                else
                {
                  allSortedValues.add(new AttributeValuePair(attrName, ""));
                }
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

    /**
     * Checks if we have to display all the attributes or only those that
     * contain a value and updates the contents of the model accordingly.  Note
     * that even if the required attributes have no value they will be
     * displayed.
     *
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
     * Returns the set of values associated with a given attribute.
     * @param attrName the name of the attribute.
     * @return the set of values associated with a given attribute.
     */
    public Set<Object> getValues(String attrName)
    {
      Set<Object> values = new LinkedHashSet<Object>();
      for (AttributeValuePair valuePair : dataArray)
      {
        if (valuePair.attrName.equalsIgnoreCase(attrName))
        {
          if (hasValue(valuePair))
          {
            if (valuePair.value instanceof Collection)
            {
              for (Object o : (Collection)valuePair.value)
              {
                values.add(o);
              }
            }
            else
            {
              values.add(valuePair.value);
            }
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
        for (AttributeValuePair currValue : allSortedValues)
        {
          String attrNoOptions = Utilities.getAttributeNameWithoutOptions(
              currValue.attrName).toLowerCase();
          if (!attributes.contains(attrNoOptions))
          {
            continue;
          }
          else if (!schemaReadOnlyAttributesLowerCase.contains(
              currValue.attrName.toLowerCase()))
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
      return requiredAttrs.contains(
          Utilities.getAttributeNameWithoutOptions(
              value.attrName.toLowerCase()));
    }

    private boolean hasValue(AttributeValuePair value)
    {
      boolean hasValue = value.value != null;
      if (hasValue)
      {
        if (value.value instanceof String)
        {
          hasValue = ((String)value.value).length() > 0;
        }
        else if (value.value instanceof byte[])
        {
          hasValue = ((byte[])value.value).length > 0;
        }
      }
      return hasValue;
    }
  }

  /**
   * A simple class that contains an attribute name and a single value.  It is
   * used by the table model to be able to retrieve more easily all the values
   * for a given attribute.
   *
   */
  class AttributeValuePair
  {
    /**
     * The attribute name.
     */
    String attrName;
    /**
     * The value.
     */
    Object value;
    /**
     * Constructor.
     * @param attrName the attribute name.
     * @param value the value.
     */
    public AttributeValuePair(String attrName, Object value)
    {
      this.attrName = attrName;
      this.value = value;
    }
  }
}
