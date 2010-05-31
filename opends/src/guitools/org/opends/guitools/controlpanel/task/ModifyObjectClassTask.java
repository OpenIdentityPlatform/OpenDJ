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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 */
package org.opends.guitools.controlpanel.task;

import static org.opends.messages.AdminToolMessages.*;

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
import org.opends.messages.Message;
import org.opends.server.types.AttributeType;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.OpenDsException;
import org.opends.server.types.Schema;

/**
 * The task that is in charge of modifying an object class definition (and all
 * the references to this object class).
 *
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

  /**
   * {@inheritDoc}
   */
  public Type getType()
  {
    return Type.MODIFY_SCHEMA_ELEMENT;
  }

  /**
   * {@inheritDoc}
   */
  public Message getTaskDescription()
  {
    return INFO_CTRL_PANEL_MODIFY_OBJECTCLASS_TASK_DESCRIPTION.get(
        oldObjectClass.getNameOrOID());
  }

  /**
   * {@inheritDoc}
   */
  public boolean canLaunch(Task taskToBeLaunched,
      Collection<Message> incompatibilityReasons)
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

  /**
   * {@inheritDoc}
   */
  public Set<String> getBackends()
  {
    return Collections.emptySet();
  }

  /**
   * {@inheritDoc}
   */
  protected List<String> getCommandLineArguments()
  {
    return Collections.emptyList();
  }

  /**
   * {@inheritDoc}
   */
  protected String getCommandLinePath()
  {
    return null;
  }

  /**
   * {@inheritDoc}
   */
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
    ObjectClass ocToAdd;
    Set<ObjectClass> currentSups = ocToDelete.getSuperiorClasses();
    if (ocToDelete.equals(oldObjectClass))
    {
      ocToAdd = newObjectClass;
    }
    else if (currentSups.contains(oldObjectClass))
    {
      ArrayList<String> allNames = new ArrayList<String>();
      for (String str : ocToDelete.getNormalizedNames())
      {
        allNames.add(str);
      }
      Map<String, List<String>> extraProperties =
        DeleteSchemaElementsTask.cloneExtraProperties(ocToDelete);
      Set<ObjectClass> newSups = new LinkedHashSet<ObjectClass>();
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
      ocToAdd = new ObjectClass("",
          ocToDelete.getPrimaryName(),
          allNames,
          ocToDelete.getOID(),
          ocToDelete.getDescription(),
          newSups,
          ocToDelete.getRequiredAttributes(),
          ocToDelete.getOptionalAttributes(),
          ocToDelete.getObjectClassType(),
          ocToDelete.isObsolete(),
          extraProperties);
    }
    else
    {
      // Nothing to be changed in the definition of the object class itself.
      ocToAdd = ocToDelete;
    }
    return ocToAdd;
  }

  /**
   * Updates the schema.
   * @throws OpenDsException if an error occurs.
   */
  private void updateSchema() throws OpenDsException
  {
    Schema schema = getInfo().getServerDescriptor().getSchema();
    ArrayList<ObjectClass> ocs = new ArrayList<ObjectClass>();
    ocs.add(oldObjectClass);
    LinkedHashSet<ObjectClass> ocsToDelete =
      DeleteSchemaElementsTask.getOrderedObjectClassesToDelete(ocs, schema);

    ArrayList<ObjectClass> lOcsToDelete =
      new ArrayList<ObjectClass>(ocsToDelete);
    LinkedHashSet<ObjectClass> ocsToAdd = new LinkedHashSet<ObjectClass>();
    for (int i = lOcsToDelete.size() - 1; i >= 0; i--)
    {
      ocsToAdd.add(getObjectClassToAdd(lOcsToDelete.get(i)));
    }

    SwingUtilities.invokeLater(new Runnable()
    {
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

