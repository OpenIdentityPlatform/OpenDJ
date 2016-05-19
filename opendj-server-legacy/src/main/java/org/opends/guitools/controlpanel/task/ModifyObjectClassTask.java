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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.SwingUtilities;

import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.guitools.controlpanel.util.Utilities;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.opends.server.types.OpenDsException;
import org.opends.server.types.Schema;

/**
 * The task that is in charge of modifying an object class definition (and all
 * the references to this object class).
 */
public class ModifyObjectClassTask extends Task
{
  private ObjectClass oldObjectClass;
  private ObjectClass newObjectClass;

  /**
   * The constructor of the task.
   * @param info the control panel info.
   * @param dlg the progress dialog that shows the progress of the task.
   * @param oldObjectClass the old object class definition.
   * @param newObjectClass the new object class definition.
   */
  public ModifyObjectClassTask(ControlPanelInfo info, ProgressDialog dlg,
      ObjectClass oldObjectClass, ObjectClass newObjectClass)
  {
    super(info, dlg);
    this.oldObjectClass = oldObjectClass;
    this.newObjectClass = newObjectClass;
    if (oldObjectClass == null)
    {
      throw new IllegalArgumentException("oldObjectClass cannot be null.");
    }
    if (newObjectClass == null)
    {
      throw new IllegalArgumentException("newObjectClass cannot be null.");
    }
  }

  @Override
  public Type getType()
  {
    return Type.MODIFY_SCHEMA_ELEMENT;
  }

  @Override
  public LocalizableMessage getTaskDescription()
  {
    return INFO_CTRL_PANEL_MODIFY_OBJECTCLASS_TASK_DESCRIPTION.get(
        oldObjectClass.getNameOrOID());
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

  private ObjectClass getObjectClassToAdd(ObjectClass ocToDelete)
  {
    Set<ObjectClass> currentSups = ocToDelete.getSuperiorClasses();
    if (ocToDelete.equals(oldObjectClass))
    {
      return newObjectClass;
    }
    else if (currentSups.contains(oldObjectClass))
    {
      Map<String, List<String>> extraProperties =
        DeleteSchemaElementsTask.cloneExtraProperties(ocToDelete);
      Set<ObjectClass> newSups = new LinkedHashSet<>();
      for(ObjectClass oc: currentSups)
      {
        if(oc.equals(oldObjectClass))
        {
          newSups.add(newObjectClass);
        }
        else
        {
          newSups.add(oc);
        }
      }
      final String oid = ocToDelete.getOID();
      final Schema schema = getInfo().getServerDescriptor().getSchema();
      return new SchemaBuilder(schema.getSchemaNG()).buildObjectClass(oid)
          .names(ocToDelete.getNames())
          .description(ocToDelete.getDescription())
          .superiorObjectClasses(getNameOrOIDsForOCs(newSups))
          .requiredAttributes(getNameOrOIDsForATs(ocToDelete.getDeclaredRequiredAttributes()))
          .optionalAttributes(getNameOrOIDsForATs(ocToDelete.getDeclaredOptionalAttributes()))
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
    ArrayList<ObjectClass> ocs = newArrayList(oldObjectClass);
    LinkedHashSet<ObjectClass> ocsToDelete =
      DeleteSchemaElementsTask.getOrderedObjectClassesToDelete(ocs, schema);

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
              INFO_CTRL_PANEL_EXPLANATION_TO_MODIFY_OBJECTCLASS.get(
                  oldObjectClass.getNameOrOID())+"<br><br>",
                  ColorAndFontConstants.progressFont));
      }
    });

    DeleteSchemaElementsTask deleteTask =
      new DeleteSchemaElementsTask(getInfo(), getProgressDialog(), ocsToDelete,
          new LinkedHashSet<AttributeType>(0));
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
          new LinkedHashSet<AttributeType>(0));

    createTask.runTask();

    notifyConfigurationElementCreated(newObjectClass);
  }
}

