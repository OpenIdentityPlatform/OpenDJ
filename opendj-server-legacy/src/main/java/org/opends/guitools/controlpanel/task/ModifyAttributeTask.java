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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.task;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.SchemaUtils.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.SwingUtilities;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.SomeSchemaElement;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.server.types.OpenDsException;
import org.opends.server.types.Schema;

/**
 * The task that is in charge of modifying an attribute definition (and all
 * the references to this attribute).
 */
public class ModifyAttributeTask extends Task
{
  private AttributeType oldAttribute;
  private AttributeType newAttribute;

  /**
   * The constructor of the task.
   * @param info the control panel info.
   * @param dlg the progress dialog that shows the progress of the task.
   * @param oldAttribute the old attribute definition.
   * @param newAttribute the new attribute definition.
   */
  public ModifyAttributeTask(ControlPanelInfo info, ProgressDialog dlg,
      AttributeType oldAttribute, AttributeType newAttribute)
  {
    super(info, dlg);
    if (oldAttribute == null)
    {
      throw new IllegalArgumentException("oldAttribute cannot be null.");
    }
    if (newAttribute == null)
    {
      throw new IllegalArgumentException("newAttribute cannot be null.");
    }
    this.oldAttribute = oldAttribute;
    this.newAttribute = newAttribute;
  }

  @Override
  public Type getType()
  {
    return Type.MODIFY_SCHEMA_ELEMENT;
  }

  @Override
  public LocalizableMessage getTaskDescription()
  {
    return INFO_CTRL_PANEL_MODIFY_ATTRIBUTE_TASK_DESCRIPTION.get(
        oldAttribute.getNameOrOID());
  }

  @Override
  public boolean canLaunch(Task taskToBeLaunched,
      Collection<LocalizableMessage> incompatibilityReasons)
  {
    boolean canLaunch = true;
    if (state == State.RUNNING &&
        (taskToBeLaunched.getType() == Task.Type.DELETE_SCHEMA_ELEMENT ||
         taskToBeLaunched.getType() == Task.Type.MODIFY_SCHEMA_ELEMENT ||
         taskToBeLaunched.getType() == Task.Type.NEW_SCHEMA_ELEMENT))
    {
      incompatibilityReasons.add(getIncompatibilityMessage(this,
            taskToBeLaunched));
      canLaunch = false;
    }
    return canLaunch;
  }

  @Override
  public Set<String> getBackends()
  {
    return Collections.emptySet();
  }

  @Override
  protected List<String> getCommandLineArguments()
  {
    return Collections.emptyList();
  }

  @Override
  protected String getCommandLinePath()
  {
    return null;
  }

  @Override
  public void runTask()
  {
    try
    {
      updateSchema();
      state = State.FINISHED_SUCCESSFULLY;
    }
    catch (Throwable t)
    {
      // TODO
      //revertChanges();
      lastException = t;
      state = State.FINISHED_WITH_ERROR;
    }
  }

  private AttributeType getAttributeToAdd(AttributeType attrToDelete)
  {
    if (attrToDelete.equals(oldAttribute))
    {
      return newAttribute;
    }
    else if (oldAttribute.equals(attrToDelete.getSuperiorType()))
    {
      // get a new attribute with the new superior type
      return SomeSchemaElement.changeSuperiorType(attrToDelete, newAttribute);
    }
    else
    {
      // Nothing to be changed in the definition of the attribute itself.
      return attrToDelete;
    }
  }

  private ObjectClass getObjectClassToAdd(ObjectClass ocToDelete)
  {
    boolean containsAttribute =
      ocToDelete.getRequiredAttributes().contains(oldAttribute) ||
      ocToDelete.getOptionalAttributes().contains(oldAttribute);
    if (containsAttribute)
    {
      Map<String, List<String>> extraProperties =
        DeleteSchemaElementsTask.cloneExtraProperties(ocToDelete);
      Set<AttributeType> required = new HashSet<>(ocToDelete.getDeclaredRequiredAttributes());
      Set<AttributeType> optional = new HashSet<>(ocToDelete.getDeclaredOptionalAttributes());
      if (required.contains(oldAttribute))
      {
        required.remove(oldAttribute);
        required.add(newAttribute);
      }
      else if (optional.contains(oldAttribute))
      {
        optional.remove(oldAttribute);
        optional.add(newAttribute);
      }

      Schema schema = getInfo().getServerDescriptor().getSchema();
      String oid = ocToDelete.getOID();
      return new SchemaBuilder(schema.getSchemaNG()).buildObjectClass(oid)
          .names(ocToDelete.getNames())
          .description(ocToDelete.getDescription())
          .superiorObjectClasses(getNameOrOIDsForOCs(ocToDelete.getSuperiorClasses()))
          .requiredAttributes(getNameOrOIDsForATs(required))
          .optionalAttributes(getNameOrOIDsForATs(optional))
          .type(ocToDelete.getObjectClassType())
          .obsolete(ocToDelete.isObsolete())
          .extraProperties(extraProperties)
          .addToSchema()
          .toSchema()
          .getObjectClass(oid);
    }
    else
    {
      // Nothing to be changed in the definition of the object class itself.
      return ocToDelete;
    }
  }

  /**
   * Updates the schema.
   * @throws OpenDsException if an error occurs.
   */
  private void updateSchema() throws OpenDsException
  {
    Schema schema = getInfo().getServerDescriptor().getSchema();
    ArrayList<AttributeType> attrs = newArrayList(oldAttribute);
    LinkedHashSet<AttributeType> attrsToDelete =
      DeleteSchemaElementsTask.getOrderedAttributesToDelete(attrs, schema);
    LinkedHashSet<ObjectClass> ocsToDelete =
      DeleteSchemaElementsTask.getOrderedObjectClassesToDeleteFromAttrs(
          attrsToDelete, schema);

    LinkedHashSet<AttributeType> attrsToAdd = new LinkedHashSet<>();
    ArrayList<AttributeType> lAttrsToDelete = new ArrayList<>(attrsToDelete);
    for (int i = lAttrsToDelete.size() - 1; i >= 0; i--)
    {
      AttributeType attrToAdd = getAttributeToAdd(lAttrsToDelete.get(i));
      if (attrToAdd != null)
      {
        attrsToAdd.add(attrToAdd);
      }
    }

    ArrayList<ObjectClass> lOcsToDelete = new ArrayList<>(ocsToDelete);
    LinkedHashSet<ObjectClass> ocsToAdd = new LinkedHashSet<>();
    for (int i = lOcsToDelete.size() - 1; i >= 0; i--)
    {
      ocsToAdd.add(getObjectClassToAdd(lOcsToDelete.get(i)));
    }

    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        getProgressDialog().appendProgressHtml(Utilities.applyFont(
            INFO_CTRL_PANEL_EXPLANATION_TO_MODIFY_ATTRIBUTE.get(
                oldAttribute.getNameOrOID())+"<br><br>",
                ColorAndFontConstants.progressFont));
      }
    });

    DeleteSchemaElementsTask deleteTask =
      new DeleteSchemaElementsTask(getInfo(), getProgressDialog(), ocsToDelete,
          attrsToDelete);
    deleteTask.runTask();

    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        getProgressDialog().appendProgressHtml(Utilities.applyFont("<br><br>",
                ColorAndFontConstants.progressFont));
      }
    });

    NewSchemaElementsTask createTask =
      new NewSchemaElementsTask(getInfo(), getProgressDialog(), ocsToAdd,
          attrsToAdd);
    createTask.runTask();

    notifyConfigurationElementCreated(newAttribute);
  }
}
