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
import java.util.HashSet;
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
 * The task that is in charge of modifying an attribute definition (and all
 * the references to this attribute).
 *
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
    return INFO_CTRL_PANEL_MODIFY_ATTRIBUTE_TASK_DESCRIPTION.get(
        oldAttribute.getNameOrOID());
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

  private AttributeType getAttributeToAdd(AttributeType attrToDelete)
  {
    AttributeType attrToAdd;
    if (attrToDelete.equals(oldAttribute))
    {
      attrToAdd = newAttribute;
    }
    else
    {
      if (oldAttribute.equals(attrToDelete.getSuperiorType()))
      {
        ArrayList<String> allNames = new ArrayList<String>();
        for (String str : attrToDelete.getNormalizedNames())
        {
          allNames.add(str);
        }
        Map<String, List<String>> extraProperties =
          DeleteSchemaElementsTask.cloneExtraProperties(attrToDelete);
        AttributeType newSuperior = newAttribute;
        attrToAdd = new AttributeType(
            "",
            attrToDelete.getPrimaryName(),
            allNames,
            attrToDelete.getOID(),
            attrToDelete.getDescription(),
            newSuperior,
            attrToDelete.getSyntax(),
            attrToDelete.getApproximateMatchingRule(),
            attrToDelete.getEqualityMatchingRule(),
            attrToDelete.getOrderingMatchingRule(),
            attrToDelete.getSubstringMatchingRule(),
            attrToDelete.getUsage(),
            attrToDelete.isCollective(),
            attrToDelete.isNoUserModification(),
            attrToDelete.isObsolete(),
            attrToDelete.isSingleValue(),
            extraProperties);
      }
      else
      {
        // Nothing to be changed in the definition of the attribute itself.
        attrToAdd = attrToDelete;
      }
    }
    return attrToAdd;
  }

  private ObjectClass getObjectClassToAdd(ObjectClass ocToDelete)
  {
    ObjectClass ocToAdd;
    boolean containsAttribute =
      ocToDelete.getRequiredAttributeChain().contains(oldAttribute) ||
      ocToDelete.getOptionalAttributeChain().contains(oldAttribute);
    if (containsAttribute)
    {
      ArrayList<String> allNames = new ArrayList<String>();
      for (String str : ocToDelete.getNormalizedNames())
      {
        allNames.add(str);
      }
      Map<String, List<String>> extraProperties =
        DeleteSchemaElementsTask.cloneExtraProperties(ocToDelete);
      Set<AttributeType> required = new HashSet<AttributeType>(
          ocToDelete.getRequiredAttributes());
      Set<AttributeType> optional = new HashSet<AttributeType>(
          ocToDelete.getOptionalAttributes());
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
      ocToAdd = new ObjectClass("",
          ocToDelete.getPrimaryName(),
          allNames,
          ocToDelete.getOID(),
          ocToDelete.getDescription(),
          ocToDelete.getSuperiorClasses(),
          required,
          optional,
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
    ArrayList<AttributeType> attrs = new ArrayList<AttributeType>();
    attrs.add(oldAttribute);
    LinkedHashSet<AttributeType> attrsToDelete =
      DeleteSchemaElementsTask.getOrderedAttributesToDelete(attrs, schema);
    LinkedHashSet<ObjectClass> ocsToDelete =
      DeleteSchemaElementsTask.getOrderedObjectClassesToDeleteFromAttrs(
          attrsToDelete, schema);

    LinkedHashSet<AttributeType> attrsToAdd =
      new LinkedHashSet<AttributeType>();
    ArrayList<AttributeType> lAttrsToDelete =
      new ArrayList<AttributeType>(attrsToDelete);
    for (int i = lAttrsToDelete.size() - 1; i >= 0; i--)
    {
      AttributeType attrToAdd = getAttributeToAdd(lAttrsToDelete.get(i));
      if (attrToAdd != null)
      {
        attrsToAdd.add(attrToAdd);
      }
    }

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
