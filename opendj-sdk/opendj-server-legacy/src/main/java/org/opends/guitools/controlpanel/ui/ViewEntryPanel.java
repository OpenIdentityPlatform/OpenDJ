/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */

package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.server.util.CollectionUtils.*;

import java.awt.Container;
import java.awt.GridBagConstraints;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.JLabel;
import javax.swing.tree.TreePath;

import org.opends.guitools.controlpanel.datamodel.BinaryValue;
import org.opends.guitools.controlpanel.datamodel.CustomSearchResult;
import org.opends.guitools.controlpanel.datamodel.ObjectClassValue;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.event.LDAPEntryChangedEvent;
import org.opends.guitools.controlpanel.event.LDAPEntryChangedListener;
import org.opends.guitools.controlpanel.ui.nodes.BasicNode;
import org.opends.guitools.controlpanel.util.Utilities;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.opends.server.schema.SchemaConstants;
import org.opends.server.types.AttributeType;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.types.Attributes;
import org.opends.server.types.Entry;
import org.opends.server.types.ObjectClass;
import org.forgerock.opendj.ldap.schema.ObjectClassType;
import org.opends.server.types.OpenDsException;
import org.opends.server.types.RDN;
import org.opends.server.types.Schema;
import org.opends.server.util.Base64;
import org.opends.server.util.ServerConstants;

/**
 * Abstract class containing code shared by the different LDAP entry view
 * panels (Simplified View, Attribute View and LDIF View).
 *
 */
public abstract class ViewEntryPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = -1908757626234678L;
  /**
   * The read-only attributes as they appear on the schema.
   */
  protected SortedSet<String> schemaReadOnlyAttributes = new TreeSet<>();
  /**
   * The read-only attributes in lower case.
   */
  protected SortedSet<String> schemaReadOnlyAttributesLowerCase = new TreeSet<>();
  /**
   * The editable operational attributes.
   */
  protected SortedSet<String> editableOperationalAttrNames = new TreeSet<>();
  private JLabel title= Utilities.createDefaultLabel();

  private Set<LDAPEntryChangedListener> listeners = new LinkedHashSet<>();

  /**
   * Whether the entry change events should be ignored or not.
   */
  protected boolean ignoreEntryChangeEvents;

  /**
   * Static boolean used to know whether only attributes with values should be
   * displayed or not.
   */
  protected static boolean displayOnlyWithAttrs = true;

  /** {@inheritDoc} */
  public void okClicked()
  {
    // No ok button
  }

  /**
   * Returns an Entry object representing what the panel is displaying.
   * @return an Entry object representing what the panel is displaying.
   * @throws OpenDsException if the entry cannot be generated (in particular if
   * the user provided invalid data).
   */
  public abstract Entry getEntry() throws OpenDsException;

  /**
   * Updates the contents of the panel.
   * @param sr the search result to be used to update the panel.
   * @param isReadOnly whether the entry is read-only or not.
   * @param path the tree path associated with the entry in the tree.
   */
  public abstract void update(CustomSearchResult sr, boolean isReadOnly,
      TreePath path);

  /**
   * Adds a title panel to the container.
   * @param c the container where the title panel must be added.
   * @param gbc the grid bag constraints to be used.
   */
  protected void addTitlePanel(Container c, GridBagConstraints gbc)
  {
    c.add(title, gbc);
  }

  /**
   * Whether the schema must be checked or not.
   * @return <CODE>true</CODE> if the server is configured to check schema and
   * <CODE>false</CODE> otherwise.
   */
  protected boolean checkSchema()
  {
    return getInfo().getServerDescriptor().isSchemaEnabled();
  }

  /**
   * Adds an LDAP entry change listener.
   * @param listener the listener.
   */
  public void addLDAPEntryChangedListener(LDAPEntryChangedListener listener)
  {
    listeners.add(listener);
  }

  /**
   * Removes an LDAP entry change listener.
   * @param listener the listener.
   */
  public void removeLDAPEntryChangedListener(LDAPEntryChangedListener listener)
  {
    listeners.remove(listener);
  }

  /** {@inheritDoc} */
  public boolean requiresBorder()
  {
    return true;
  }

  /**
   * Returns the DN of the entry that the user is editing (it might differ
   * from the DN of the entry in the tree if the user modified the DN).
   * @return the DN of the entry that the user is editing.
   */
  protected abstract String getDisplayedDN();

  /** Notifies the entry changed listeners that the entry changed. */
  protected void notifyListeners()
  {
    if (ignoreEntryChangeEvents)
    {
      return;
    }
    // TODO: With big entries this is pretty slow.  Until there is a fix, try
    // simply to update the dn
    Entry entry = null;
    String dn = getDisplayedDN();
    if (dn != null && !dn.equals(title.getText()))
    {
      title.setText(dn);
    }
    LDAPEntryChangedEvent ev = new LDAPEntryChangedEvent(this, entry);
    for (LDAPEntryChangedListener listener : listeners)
    {
      listener.entryChanged(ev);
    }
  }

  /**
   * Updates the title panel with the provided entry.
   * @param sr the search result.
   * @param path the path to the node of the entry selected in the tree.  Used
   * to display the same icon as in the tree.
   */
  protected void updateTitle(CustomSearchResult sr, TreePath path)
  {
    String dn = sr.getDN();
    if (dn != null && dn.length() > 0)
    {
      title.setText(sr.getDN());
    }
    else if (path != null)
    {
      BasicNode node = (BasicNode)path.getLastPathComponent();
      title.setText(node.getDisplayName());
    }

    if (path != null)
    {
      BasicNode node = (BasicNode)path.getLastPathComponent();
      title.setIcon(node.getIcon());
    }
    else
    {
      title.setIcon(null);
    }

    List<Object> ocs =
      sr.getAttributeValues(ServerConstants.OBJECTCLASS_ATTRIBUTE_TYPE_NAME);
    Schema schema = getInfo().getServerDescriptor().getSchema();
    if (!ocs.isEmpty() && schema != null)
    {
      ObjectClassValue ocDesc = getObjectClassDescriptor(ocs, schema);
      StringBuilder sb = new StringBuilder();
      sb.append("<html>");
      if (ocDesc.getStructural() != null)
      {
        sb.append(INFO_CTRL_OBJECTCLASS_DESCRIPTOR.get(ocDesc.getStructural()));
      }
      if (!ocDesc.getAuxiliary().isEmpty())
      {
        if (sb.length() > 0)
        {
          sb.append("<br>");
        }
        sb.append(INFO_CTRL_AUXILIARY_OBJECTCLASS_DESCRIPTOR.get(
            Utilities.getStringFromCollection(ocDesc.getAuxiliary(), ", ")));
      }
      title.setToolTipText(sb.toString());
    }
    else
    {
      title.setToolTipText(null);
    }
  }

  /**
   * Returns an object class value representing all the object class values of
   * the entry.
   * @param ocValues the list of object class values.
   * @param schema the schema.
   * @return an object class value representing all the object class values of
   * the entry.
   */
  protected ObjectClassValue getObjectClassDescriptor(List<Object> ocValues,
      Schema schema)
  {
    ObjectClass structuralObjectClass = null;
    SortedSet<String> auxiliaryClasses = new TreeSet<>();
    for (Object o : ocValues)
    {
      ObjectClass objectClass =
        schema.getObjectClass(((String)o).toLowerCase());
      if (objectClass != null)
      {
        if (objectClass.getObjectClassType() == ObjectClassType.STRUCTURAL)
        {
          if (structuralObjectClass == null)
          {
            structuralObjectClass = objectClass;
          }
          else
          {
            if (objectClass.isDescendantOf(structuralObjectClass))
            {
              structuralObjectClass = objectClass;
            }
          }
        }
        else
        {
          String name = objectClass.getNameOrOID();
          if (!name.equals(SchemaConstants.TOP_OBJECTCLASS_NAME))
          {
            auxiliaryClasses.add(objectClass.getNameOrOID());
          }
        }
      }
    }
    String structural = structuralObjectClass != null ?
        structuralObjectClass.getNameOrOID() : null;
    return new ObjectClassValue(structural, auxiliaryClasses);
  }

  /**
   * Adds the values in the RDN to the entry definition.
   * @param entry the entry to be updated.
   */
  protected void addValuesInRDN(Entry entry)
  {
//  Add the values in the RDN if  they are not there
    RDN rdn = entry.getName().rdn();
    for (int i=0; i<rdn.getNumValues(); i++)
    {
      String attrName = rdn.getAttributeName(i);
      ByteString value = rdn.getAttributeValue(i);
      List<org.opends.server.types.Attribute> attrs =
        entry.getAttribute(attrName.toLowerCase());
      boolean done = false;
      if (attrs != null)
      {
        for (org.opends.server.types.Attribute attr : attrs)
        {
          if (attr.getNameWithOptions().equals(attrName))
          {
            ArrayList<ByteString> newValues = new ArrayList<>();
            Iterator<ByteString> it = attr.iterator();
            while (it.hasNext())
            {
              newValues.add(it.next());
            }
            newValues.add(value);
            entry.addAttribute(attr, newValues);
            done = true;
            break;
          }
        }
      }
      if (!done)
      {
        org.opends.server.types.Attribute attr =
          Attributes.create(rdn.getAttributeType(i), value);
        entry.addAttribute(attr, newArrayList(value));
      }
    }
  }

  /** {@inheritDoc} */
  public LocalizableMessage getTitle()
  {
    return INFO_CTRL_PANEL_EDIT_LDAP_ENTRY_TITLE.get();
  }

  /** {@inheritDoc} */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    Schema schema = ev.getNewDescriptor().getSchema();
    if (schema != null && schemaReadOnlyAttributes.isEmpty())
    {
      schemaReadOnlyAttributes.clear();
      schemaReadOnlyAttributesLowerCase.clear();
      for (AttributeType attr : schema.getAttributeTypes().values())
      {
        if (attr.isNoUserModification())
        {
          String attrName = attr.getNameOrOID();
          schemaReadOnlyAttributes.add(attrName);
          schemaReadOnlyAttributesLowerCase.add(attrName.toLowerCase());
        }
        else if (attr.isOperational())
        {
          editableOperationalAttrNames.add(attr.getNameOrOID());
        }
      }
    }
  }

  /**
   * Appends the LDIF lines corresponding to the different values of an
   * attribute to the provided StringBuilder.
   * @param sb the StringBuilder that must be updated.
   * @param attrName the attribute name.
   * @param values the attribute values.
   */
  protected void appendLDIFLines(StringBuilder sb, String attrName,
      List<Object> values)
  {
    for (Object value : values)
    {
      appendLDIFLine(sb, attrName, value);
    }
  }

  /**
   * Appends the LDIF line corresponding to the value of an
   * attribute to the provided StringBuilder.
   * @param sb the StringBuilder that must be updated.
   * @param attrName the attribute name.
   * @param value the attribute value.
   */
  protected void appendLDIFLine(StringBuilder sb, String attrName, Object value)
  {
    if (value instanceof ObjectClassValue)
    {
      ObjectClassValue ocValue = (ObjectClassValue)value;
      if (ocValue.getStructural() != null)
      {
        sb.append("\n");
        sb.append(attrName).append(": ").append(ocValue.getStructural());
        Schema schema = getInfo().getServerDescriptor().getSchema();
        if (schema != null)
        {
          ObjectClass oc =
            schema.getObjectClass(ocValue.getStructural().toLowerCase());
          if (oc != null)
          {
            Set<String> names = getObjectClassSuperiorValues(oc);
            for (String name : names)
            {
              sb.append("\n");
              sb.append(attrName).append(": ").append(name);
            }
          }
        }
      }
      for (String v : ocValue.getAuxiliary())
      {
        sb.append("\n");
        sb.append(attrName).append(": ").append(v);
      }
    }
    else if (value instanceof byte[])
    {
      if (((byte[])value).length > 0)
      {
        sb.append("\n");
        sb.append(attrName).append(":: ").append(Base64.encode((byte[])value));
      }
    }
    else if (value instanceof BinaryValue)
    {
      sb.append("\n");
      sb.append(attrName).append(":: ").append(((BinaryValue)value).getBase64());
    }
    else if (String.valueOf(value).trim().length() > 0)
    {
      sb.append("\n");
      sb.append(attrName).append(": ").append(value);
    }
  }

  /**
   * Returns <CODE>true</CODE> if the provided attribute name has binary syntax
   * and <CODE>false</CODE> otherwise.
   * @param attrName the attribute name.
   * @return <CODE>true</CODE> if the provided attribute name has binary syntax
   * and <CODE>false</CODE> otherwise.
   */
  protected boolean isBinary(String attrName)
  {
    Schema schema = getInfo().getServerDescriptor().getSchema();
    return Utilities.hasBinarySyntax(attrName, schema);
  }

  /**
   * Returns <CODE>true</CODE> if the provided attribute name has password
   * syntax and <CODE>false</CODE> otherwise.
   * @param attrName the attribute name.
   * @return <CODE>true</CODE> if the provided attribute name has password
   * syntax and <CODE>false</CODE> otherwise.
   */
  protected boolean isPassword(String attrName)
  {
    Schema schema = getInfo().getServerDescriptor().getSchema();
    return Utilities.hasPasswordSyntax(attrName, schema);
  }

  /**
   * Returns <CODE>true</CODE> if the provided attribute name has certificate
   * syntax and <CODE>false</CODE> otherwise.
   * @param attrName the attribute name.
   * @param schema the schema.
   * @return <CODE>true</CODE> if the provided attribute name has certificate
   * syntax and <CODE>false</CODE> otherwise.
   */
  protected boolean hasCertificateSyntax(String attrName, Schema schema)
  {
    boolean isCertificate = false;
    // Check all the attributes that we consider binaries.
    if (schema != null)
    {
      AttributeType attr = schema.getAttributeType(
          Utilities.getAttributeNameWithoutOptions(attrName).toLowerCase());
      if (attr != null)
      {
        Syntax syntax = attr.getSyntax();
        if (syntax != null)
        {
          isCertificate = syntax.getOID().equals(
              SchemaConstants.SYNTAX_CERTIFICATE_OID);
        }
      }
    }
    return isCertificate;
  }

  /**
   * Gets the values associated with a given attribute.  The values are the
   * ones displayed in the panel.
   * @param attrName the attribute name.
   * @return the values associated with a given attribute.
   */
  protected abstract List<Object> getValues(String attrName);

  /**
   * Sets the values displayed in the panel for a given attribute in the
   * provided search result.
   * @param sr the search result to be updated.
   * @param attrName the attribute name.
   */
  protected void setValues(CustomSearchResult sr, String attrName)
  {
    List<Object> values = getValues(attrName);
    List<Object> valuesToSet = new ArrayList<>();
    for (Object value : values)
    {
      if (value instanceof ObjectClassValue)
      {
        ObjectClassValue ocValue = (ObjectClassValue)value;
        if (ocValue.getStructural() != null)
        {
          valuesToSet.add(ocValue.getStructural());
        }
        valuesToSet.addAll(ocValue.getAuxiliary());
      }
      else if (value instanceof byte[])
      {
        valuesToSet.add(value);
      }
      else if (value instanceof BinaryValue)
      {
        try
        {
          valuesToSet.add(((BinaryValue)value).getBytes());
        }
        catch (ParseException pe)
        {
         throw new RuntimeException("Unexpected error: "+pe, pe);
        }
      }
      else
      {
        if (String.valueOf(value).trim().length() > 0)
        {
          valuesToSet.add(String.valueOf(value));
        }
      }
    }
    if (!valuesToSet.isEmpty())
    {
      sr.set(attrName, valuesToSet);
    }
  }

  /**
   * Returns <CODE>true</CODE> if the provided attribute name is an editable
   * attribute and <CODE>false</CODE> otherwise.
   * @param attrName the attribute name.
   * @param schema the schema.
   * @return <CODE>true</CODE> if the provided attribute name is an editable
   * attribute and <CODE>false</CODE> otherwise.
   */
  public static boolean isEditable(String attrName, Schema schema)
  {
    boolean isEditable = false;
    attrName = Utilities.getAttributeNameWithoutOptions(attrName);
    if (schema != null)
    {
      AttributeType attrType = schema.getAttributeType(attrName.toLowerCase());
      if (attrType != null)
      {
        isEditable = !attrType.isNoUserModification();
      }
    }
    return isEditable;
  }

  /**
   * Returns the list of superior object classes (to top) for a given object
   * class.
   * @param oc the object class.
   * @return the set of superior object classes for a given object classes.
   */
  protected Set<String> getObjectClassSuperiorValues(
      ObjectClass oc)
  {
    Set<String> names = new LinkedHashSet<>();
    Set<ObjectClass> parents = oc.getSuperiorClasses();
    if (parents != null && !parents.isEmpty())
    {
      for (ObjectClass parent : parents)
      {
        names.add(parent.getNameOrOID());
        names.addAll(getObjectClassSuperiorValues(parent));
      }
    }
    return names;
  }
}
