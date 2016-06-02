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
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui;

import static org.opends.server.util.StaticUtils.*;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.swing.JList;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.opends.guitools.controlpanel.event.SchemaElementSelectionEvent;
import org.opends.guitools.controlpanel.event.SchemaElementSelectionListener;
import org.opends.server.types.Schema;

/**
 * Abstract class used to re-factor some code among the panels that display the
 * contents of a schema element.
 */
public abstract class SchemaElementPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = -8556383593966382604L;

  private Set<SchemaElementSelectionListener> listeners = new HashSet<>();

  /**
   * The empty border shared by all the schema element panels.
   */
  protected Border PANEL_BORDER = new EmptyBorder(10, 10, 10, 10);

  /**
   * Adds a schema element selection listener.
   * @param listener the listener.
   */
  public void addSchemaElementSelectionListener(
      SchemaElementSelectionListener listener)
  {
    listeners.add(listener);
  }

  /**
   * Removes a schema element selection listener.
   * @param listener the listener.
   */
  public void removeSchemaElementSelectionListener(
      SchemaElementSelectionListener listener)
  {
    listeners.add(listener);
  }

  /**
   * Notifies to all the listeners that a new schema element was selected.
   * @param schemaElement the new schema element that has been selected.
   */
  protected void notifySchemaSelectionListeners(Object schemaElement)
  {
    for (SchemaElementSelectionListener listener : listeners)
    {
      listener.schemaElementSelected(
          new SchemaElementSelectionEvent(this, schemaElement));
    }
  }

  /**
   * Method used to know if there are unsaved changes or not.  It is used by
   * the schema selection listener when the user changes the selection.
   * @return <CODE>true</CODE> if there are unsaved changes (and so the
   * selection of the schema should be canceled) and <CODE>false</CODE>
   * otherwise.
   */
  public boolean mustCheckUnsavedChanges()
  {
    return false;
  }

  /**
   * Tells whether the user chose to save the changes in the panel, to not save
   * them or simply cancelled the selection in the tree.
   * @return the value telling whether the user chose to save the changes in the
   * panel, to not save them or simply cancelled the selection in the tree.
   */
  public UnsavedChangesDialog.Result checkUnsavedChanges()
  {
    return UnsavedChangesDialog.Result.DO_NOT_SAVE;
  }

  /**
   * Method called when there is an object class selected in a list.
   * @param list the list.
   */
  protected void objectClassSelected(JList<?> list)
  {
    String o = (String)list.getSelectedValue();
    Schema schema = getInfo().getServerDescriptor().getSchema();
    if (o != null && schema != null)
    {
      ObjectClass oc = schema.getObjectClass(o);
      if (!oc.isPlaceHolder())
      {
        notifySchemaSelectionListeners(oc);
      }
    }
  }

  /**
   * Returns the list of aliases for the provided attribute.
   * @param attr the attribute.
   * @return the list of aliases for the provided attribute.
   */
  protected Set<String> getAliases(AttributeType attr)
  {
    return getAliases(attr.getNames(), attr.getNameOrOID());
  }

  /**
   * Returns the list of aliases for the provided object class.
   * @param oc the object class.
   * @return the list of aliases for the provided object class.
   */
  protected Set<String> getAliases(ObjectClass oc)
  {
    return getAliases(oc.getNames(), oc.getNameOrOID());
  }

  private Set<String> getAliases(Iterable<String> names, String nameOrOid)
  {
    nameOrOid = nameOrOid != null ? nameOrOid : "";

    final Set<String> aliases = new LinkedHashSet<>();
    for (String name : names)
    {
      if (!name.equalsIgnoreCase(nameOrOid))
      {
        aliases.add(toLowerCase(name));
      }
    }
    return aliases;
  }
}
