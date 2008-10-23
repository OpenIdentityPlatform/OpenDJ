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

import java.util.HashSet;
import java.util.Set;

import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

import org.opends.guitools.controlpanel.event.SchemaElementSelectionEvent;
import org.opends.guitools.controlpanel.event.SchemaElementSelectionListener;

/**
 * Abstract class used to refactor some code among the panels that display the
 * contents of a schema element.
 *
 */
public abstract class SchemaElementPanel extends StatusGenericPanel
{
  private Set<SchemaElementSelectionListener> listeners =
    new HashSet<SchemaElementSelectionListener>();

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
}
