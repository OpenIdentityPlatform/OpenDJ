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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.task;

import static org.opends.messages.AdminToolMessages.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.NamingException;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.swing.SwingUtilities;

import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.config.ConfigConstants;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attributes;
import org.opends.server.types.AttributeType;
import org.opends.server.types.CommonSchemaElements;
import org.opends.server.types.Entry;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.OpenDsException;
import org.opends.server.types.Schema;
import org.opends.server.types.SchemaFileElement;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.StaticUtils;

/**
 * The task that is launched when a schema element must be deleted.
 */
public class DeleteSchemaElementsTask extends Task
{
  // The list of object classes that the user asked to delete.
  LinkedHashSet<ObjectClass> providedOcsToDelete =
    new LinkedHashSet<ObjectClass>();
  // The list of attributes that the user asked to delete.
  LinkedHashSet<AttributeType> providedAttrsToDelete =
    new LinkedHashSet<AttributeType>();
  // The list of object classes that will be actually deleted (some might be
  // recreated).
  LinkedHashSet<ObjectClass> ocsToDelete = new LinkedHashSet<ObjectClass>();
  // The list of attributes that will be actually deleted (some might be
  // recreated).
  LinkedHashSet<AttributeType> attrsToDelete =
    new LinkedHashSet<AttributeType>();
  // The list of object classes that will be recreated.
  LinkedHashSet<ObjectClass> ocsToAdd = new LinkedHashSet<ObjectClass>();
  // The list of attributes that will be recreated.
  LinkedHashSet<AttributeType> attrsToAdd = new LinkedHashSet<AttributeType>();

  /**
   * Constructor of the task.
   * @param info the control panel information.
   * @param dlg the progress dialog where the task progress will be displayed.
   * @param ocsToDelete the object classes that must be deleted (ordered).
   * @param attrsToDelete the attributes that must be deleted (ordered).
   */
  public DeleteSchemaElementsTask(ControlPanelInfo info, ProgressDialog dlg,
      LinkedHashSet<ObjectClass> ocsToDelete,
      LinkedHashSet<AttributeType> attrsToDelete)
  {
    super(info, dlg);

    this.providedOcsToDelete.addAll(ocsToDelete);
    this.providedAttrsToDelete.addAll(attrsToDelete);

    Schema schema = info.getServerDescriptor().getSchema();
    LinkedHashSet<AttributeType> allAttrsToDelete =
      DeleteSchemaElementsTask.getOrderedAttributesToDelete(attrsToDelete,
          schema);
    LinkedHashSet<ObjectClass> allOcsToDelete = null;
    if (!attrsToDelete.isEmpty())
    {
      allOcsToDelete =
        DeleteSchemaElementsTask.getOrderedObjectClassesToDeleteFromAttrs(
          attrsToDelete, schema);
    }
    if (!ocsToDelete.isEmpty())
    {
      if (allOcsToDelete == null)
      {
      allOcsToDelete =
        DeleteSchemaElementsTask.getOrderedObjectClassesToDelete(
            ocsToDelete, schema);
      }
      else
      {
        allOcsToDelete.addAll(
            DeleteSchemaElementsTask.getOrderedObjectClassesToDelete(
                ocsToDelete, schema));
      }
    }
    ArrayList<AttributeType> lAttrsToDelete =
      new ArrayList<AttributeType>(allAttrsToDelete);
    for (int i = lAttrsToDelete.size() - 1; i >= 0; i--)
    {
      AttributeType attrToDelete = lAttrsToDelete.get(i);
      if (!attrsToDelete.contains(attrToDelete))
      {
        AttributeType attrToAdd = getAttributeToAdd(attrToDelete);
        if (attrToAdd != null)
        {
          attrsToAdd.add(attrToAdd);
        }
      }
    }

    assert allOcsToDelete != null;
    ArrayList<ObjectClass> lOcsToDelete =
      new ArrayList<ObjectClass>(allOcsToDelete);
    for (int i = lOcsToDelete.size() - 1; i >= 0; i--)
    {
      ObjectClass ocToDelete = lOcsToDelete.get(i);
      if (!ocsToDelete.contains(ocToDelete))
      {
        ocsToAdd.add(getObjectClassToAdd(lOcsToDelete.get(i)));
      }
    }

    this.ocsToDelete.addAll(allOcsToDelete);
    this.attrsToDelete.addAll(allAttrsToDelete);
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
  public Type getType()
  {
    return Type.NEW_SCHEMA_ELEMENT;
  }

  /**
   * {@inheritDoc}
   */
  public void runTask()
  {
    state = State.RUNNING;
    lastException = null;

    try
    {
      updateSchema();
      state = State.FINISHED_SUCCESSFULLY;
    }
    catch (Throwable t)
    {
      lastException = t;
      state = State.FINISHED_WITH_ERROR;
    }
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
  protected List<String> getCommandLineArguments()
  {
    return Collections.emptyList();
  }

  /**
   * {@inheritDoc}
   */
  public Message getTaskDescription()
  {
    return INFO_CTRL_PANEL_DELETE_SCHEMA_ELEMENT_TASK_DESCRIPTION.get();
  }

  /**
   * Updates the schema.
   * @throws OpenDsException if an error occurs.
   */
  private void updateSchema() throws OpenDsException
  {
    final boolean[] isFirst = {true};
    final int totalNumber = ocsToDelete.size() + attrsToDelete.size();
    int numberDeleted = 0;
    for (ObjectClass objectClass : ocsToDelete)
    {
      final ObjectClass fObjectclass = objectClass;
      SwingUtilities.invokeLater(new Runnable()
      {
        public void run()
        {
          if (!isFirst[0])
          {
            getProgressDialog().appendProgressHtml("<br><br>");
          }
          isFirst[0] = false;
          printEquivalentCommandToDelete(fObjectclass);
          getProgressDialog().appendProgressHtml(
              Utilities.getProgressWithPoints(
                  INFO_CTRL_PANEL_DELETING_OBJECTCLASS.get(
                      fObjectclass.getNameOrOID()),
                  ColorAndFontConstants.progressFont));
        }
      });

      if (isServerRunning())
      {
        try
        {
          BasicAttribute attr = new BasicAttribute(
              getSchemaFileAttributeName(objectClass));
          attr.add(getSchemaFileAttributeValue(objectClass));
          ModificationItem mod =
            new ModificationItem(DirContext.REMOVE_ATTRIBUTE, attr);
          getInfo().getDirContext().modifyAttributes(
              ConfigConstants.DN_DEFAULT_SCHEMA_ROOT,
              new ModificationItem[]  { mod });
        }
        catch (NamingException ne)
        {
          throw new OnlineUpdateException(
              ERR_CTRL_PANEL_ERROR_UPDATING_SCHEMA.get(ne.toString()), ne);
        }
      }
      else
      {
        updateSchemaFile(objectClass);
      }
      numberDeleted ++;
      final int fNumberDeleted = numberDeleted;
      SwingUtilities.invokeLater(new Runnable()
      {
        public void run()
        {
          getProgressDialog().getProgressBar().setIndeterminate(false);
          getProgressDialog().getProgressBar().setValue(
              (fNumberDeleted * 100) / totalNumber);
          getProgressDialog().appendProgressHtml(
              Utilities.getProgressDone(ColorAndFontConstants.progressFont));
        }
      });
    }

    for (AttributeType attribute : attrsToDelete)
    {
      final AttributeType fAttribute = attribute;
      SwingUtilities.invokeLater(new Runnable()
      {
        public void run()
        {
          if (!isFirst[0])
          {
            getProgressDialog().appendProgressHtml("<br><br>");
          }
          isFirst[0] = false;
          printEquivalentCommandToDelete(fAttribute);
          getProgressDialog().appendProgressHtml(
              Utilities.getProgressWithPoints(
                  INFO_CTRL_PANEL_DELETING_ATTRIBUTE.get(
                      fAttribute.getNameOrOID()),
                  ColorAndFontConstants.progressFont));
        }
      });

      if (isServerRunning())
      {
        try
        {
          BasicAttribute attr = new BasicAttribute(
              getSchemaFileAttributeName(attribute));
          attr.add(getSchemaFileAttributeValue(attribute));
          ModificationItem mod = new ModificationItem(
              DirContext.REMOVE_ATTRIBUTE,
              attr);
          getInfo().getDirContext().modifyAttributes(
              ConfigConstants.DN_DEFAULT_SCHEMA_ROOT,
              new ModificationItem[]  { mod });
        }
        catch (NamingException ne)
        {
          throw new OnlineUpdateException(
              ERR_CTRL_PANEL_ERROR_UPDATING_SCHEMA.get(ne.toString()), ne);
        }
      }
      else
      {
        updateSchemaFile(attribute);
      }

      numberDeleted ++;
      final int fNumberDeleted = numberDeleted;
      SwingUtilities.invokeLater(new Runnable()
      {
        public void run()
        {
          getProgressDialog().getProgressBar().setIndeterminate(false);
          getProgressDialog().getProgressBar().setValue(
              (fNumberDeleted * 100) / totalNumber);
          getProgressDialog().appendProgressHtml(
              Utilities.getProgressDone(ColorAndFontConstants.progressFont));
        }
      });
    }

    if (!ocsToAdd.isEmpty() || !attrsToAdd.isEmpty())
    {
      SwingUtilities.invokeLater(new Runnable()
      {
        public void run()
        {
          getProgressDialog().appendProgressHtml(Utilities.applyFont(
              "<br><br>"+
              INFO_CTRL_PANEL_EXPLANATION_TO_DELETE_REFERENCED_ELEMENTS.get()+
              "<br><br>",
              ColorAndFontConstants.progressFont));
        }
      });

      NewSchemaElementsTask createTask =
        new NewSchemaElementsTask(getInfo(), getProgressDialog(), ocsToAdd,
            attrsToAdd);
      createTask.runTask();
    }
  }

  /**
   * Updates the schema file by deleting the provided schema element.
   * @param schemaElement the schema element to be deleted.
   * @throws OpenDsException if an error occurs.
   */
  private void updateSchemaFile(CommonSchemaElements schemaElement)
  throws OpenDsException
  {
    String schemaFile = getSchemaFile((SchemaFileElement)schemaElement);
    LDIFExportConfig exportConfig =
      new LDIFExportConfig(schemaFile,
          ExistingFileBehavior.OVERWRITE);
    LDIFReader reader = null;
    LDIFWriter writer = null;
    try
    {
      reader = new LDIFReader(new LDIFImportConfig(schemaFile));
      Entry schemaEntry = reader.readEntry();

      Modification mod = new Modification(ModificationType.DELETE,
          Attributes.create(
              getSchemaFileAttributeName(schemaElement).toLowerCase(),
              getSchemaFileAttributeValue(schemaElement)));
      schemaEntry.applyModification(mod);
      writer = new LDIFWriter(exportConfig);
      writer.writeEntry(schemaEntry);
      exportConfig.getWriter().newLine();
    }
    catch (IOException e)
    {
      throw new OfflineUpdateException(
          ERR_CTRL_PANEL_ERROR_UPDATING_SCHEMA.get(e.toString()), e);
    }
    finally
    {
      StaticUtils.close(reader, exportConfig, writer);
    }
  }

  /**
   * Returns the schema file for a given schema element.
   * @param element the schema element.
   * @return the schema file for a given schema element.
   */
  private String getSchemaFile(SchemaFileElement element)
  {
    String schemaFile = element.getSchemaFile();
    if (schemaFile == null)
    {
      schemaFile = ConfigConstants.FILE_USER_SCHEMA_ELEMENTS;
    }
    File f = new File(schemaFile);
    if (!f.isAbsolute())
    {
      f = new File(
          DirectoryServer.getEnvironmentConfig().getSchemaDirectory(),
          schemaFile);
    }
    schemaFile = f.getAbsolutePath();
    return schemaFile;
  }

  /**
   * Returns the attribute name in the schema entry that corresponds to the
   * provided schema element.
   * @param element the schema element.
   * @return the attribute name in the schema entry that corresponds to the
   * provided schema element.
   */
  private String getSchemaFileAttributeName(CommonSchemaElements element)
  {
    if (element instanceof AttributeType)
    {
      return "attributeTypes";
    }
    else
    {
      return "objectClasses";
    }
  }

  /**
   * Returns the value in the schema file for the provided element.
   * @param element the schema element.
   * @return the value in the schema file for the provided element.
   */
  private String getSchemaFileAttributeValue(CommonSchemaElements element)
  {
    if (element instanceof AttributeType)
    {
      return ((AttributeType)element).getDefinition();
    }
    else
    {
      return ((ObjectClass)element).getDefinition();
    }
  }

  /**
   * Prints the equivalent command-line to delete the schema element in the
   * progress dialog.
   * @param element the schema element to be deleted.
   */
  private void printEquivalentCommandToDelete(CommonSchemaElements element)
  {
    String schemaFile = getSchemaFile((SchemaFileElement)element);
    String attrName = getSchemaFileAttributeName(element);
    String attrValue = getSchemaFileAttributeValue(element);
    if (!isServerRunning())
    {
      Message msg;
      if (element instanceof AttributeType)
      {
        msg = INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_DELETE_ATTRIBUTE_OFFLINE.get(
            element.getNameOrOID(), schemaFile);
      }
      else
      {
        msg = INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_DELETE_OBJECTCLASS_OFFLINE.get(
            element.getNameOrOID(), schemaFile);
      }
      getProgressDialog().appendProgressHtml(Utilities.applyFont(
          msg+"<br><b>"+
          attrName+": "+attrValue+"</b><br><br>",
          ColorAndFontConstants.progressFont));
    }
    else
    {
      ArrayList<String> args = new ArrayList<String>();
      args.add("-a");
      args.addAll(getObfuscatedCommandLineArguments(
          getConnectionCommandLineArguments(true, true)));
      args.add(getNoPropertiesFileArgument());
      String equiv = getEquivalentCommandLine(getCommandLinePath("ldapmodify"),
          args);

      Message msg;
      if (element instanceof AttributeType)
      {
        msg = INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_DELETE_ATTRIBUTE_ONLINE.get(
            element.getNameOrOID());
      }
      else
      {
        msg = INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_DELETE_OBJECTCLASS_ONLINE.get(
            element.getNameOrOID());
      }

      StringBuilder sb = new StringBuilder();
      sb.append(msg).append("<br><b>");
      sb.append(equiv);
      sb.append("<br>");
      sb.append("dn: cn=schema<br>");
      sb.append("changetype: modify<br>");
      sb.append("delete: ").append(attrName).append("<br>");
      sb.append(attrName).append(": ").append(attrValue);
      sb.append("</b><br><br>");
      getProgressDialog().appendProgressHtml(Utilities.applyFont(sb.toString(),
          ColorAndFontConstants.progressFont));
    }
  }

  private AttributeType getAttributeToAdd(AttributeType attrToDelete)
  {
    AttributeType attrToAdd;
    boolean isSuperior = false;
    for (AttributeType attr : providedAttrsToDelete)
    {
      if (attr.equals(attrToDelete.getSuperiorType()))
      {
        isSuperior = true;
        AttributeType newSuperior = attr.getSuperiorType();
        while (newSuperior != null &&
            providedAttrsToDelete.contains(newSuperior))
        {
          newSuperior = newSuperior.getSuperiorType();
        }
        break;
      }
    }
    if (isSuperior)
    {
      ArrayList<String> allNames = new ArrayList<String>();
      for (String str : attrToDelete.getNormalizedNames())
      {
        allNames.add(str);
      }
      Map<String, List<String>> extraProperties =
        cloneExtraProperties(attrToDelete);
      attrToAdd = new AttributeType(
          "",
          attrToDelete.getPrimaryName(),
          allNames,
          attrToDelete.getOID(),
          attrToDelete.getDescription(),
          null,
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
    return attrToAdd;
  }

  private ObjectClass getObjectClassToAdd(ObjectClass ocToDelete)
  {
    ObjectClass ocToAdd;
    boolean containsAttribute = false;
    for (AttributeType attr : providedAttrsToDelete)
    {
      if(ocToDelete.getRequiredAttributeChain().contains(attr) ||
      ocToDelete.getOptionalAttributeChain().contains(attr))
      {
        containsAttribute = true;
        break;
      }
    }
    boolean hasSuperior = false;
    Set<ObjectClass> newSuperiors = new LinkedHashSet<ObjectClass>();
    for (ObjectClass sup : ocToDelete.getSuperiorClasses())
    {
      boolean isFound = false;
      for (ObjectClass oc: providedOcsToDelete)
      {
        if(sup.equals(oc))
        {
          hasSuperior = true;
          isFound = true;
          newSuperiors.addAll(getNewSuperiors(oc));
          break;
        }
      }
      if (!isFound)
      {
        //Use the same super if not found in the list.
        newSuperiors.add(sup);
      }
    }

    if (containsAttribute || hasSuperior)
    {
      ArrayList<String> allNames = new ArrayList<String>();
      for (String str : ocToDelete.getNormalizedNames())
      {
        allNames.add(str);
      }
      Map<String, List<String>> extraProperties =
        cloneExtraProperties(ocToDelete);
      Set<AttributeType> required;
      Set<AttributeType> optional;
      if (containsAttribute)
      {
        required = new HashSet<AttributeType>(
            ocToDelete.getRequiredAttributes());
        optional = new HashSet<AttributeType>(
            ocToDelete.getOptionalAttributes());
        required.removeAll(providedAttrsToDelete);
        optional.removeAll(providedAttrsToDelete);
      }
      else
      {
        required = ocToDelete.getRequiredAttributes();
        optional = ocToDelete.getOptionalAttributes();
      }
      ocToAdd = new ObjectClass("",
          ocToDelete.getPrimaryName(),
          allNames,
          ocToDelete.getOID(),
          ocToDelete.getDescription(),
          newSuperiors,
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


  private Set<ObjectClass> getNewSuperiors(ObjectClass currentSup)
  {
    Set<ObjectClass> newSuperiors = new LinkedHashSet<ObjectClass>();
    if (currentSup.getSuperiorClasses() != null &&
        !currentSup.getSuperiorClasses().isEmpty())
    {
      for (ObjectClass o : currentSup.getSuperiorClasses())
      {
        if (providedOcsToDelete.contains(o))
        {
          newSuperiors.addAll(getNewSuperiors(o));
        }
        else
        {
          newSuperiors.add(o);
        }
      }
    }
    return newSuperiors;
  }


  /**
   * Returns an ordered set of the attributes that must be deleted.
   * @param attrsToDelete the attributes to be deleted.
   * @param schema the server schema.
   * @return an ordered list of the attributes that must be deleted.
   */
  public static LinkedHashSet<AttributeType> getOrderedAttributesToDelete(
      Collection<AttributeType> attrsToDelete, Schema schema)
  {
    LinkedHashSet<AttributeType> orderedAttributes =
      new LinkedHashSet<AttributeType>();
    for (AttributeType attribute : attrsToDelete)
    {
      orderedAttributes.addAll(getOrderedChildrenToDelete(attribute, schema));
      orderedAttributes.add(attribute);
    }
    return orderedAttributes;
  }

  /**
   * Returns an ordered list of the object classes that must be deleted.
   * @param ocsToDelete the object classes to be deleted.
   * @param schema the server schema.
   * @return an ordered list of the object classes that must be deleted.
   */
  public static LinkedHashSet<ObjectClass> getOrderedObjectClassesToDelete(
      Collection<ObjectClass> ocsToDelete, Schema schema)
  {
    LinkedHashSet<ObjectClass> orderedOcs = new LinkedHashSet<ObjectClass>();
    for (ObjectClass oc : ocsToDelete)
    {
      orderedOcs.addAll(getOrderedChildrenToDelete(oc, schema));
      orderedOcs.add(oc);
    }
    return orderedOcs;
  }

  /**
   * Returns an ordered list of the object classes that must be deleted when
   * deleting a list of attributes that must be deleted.
   * @param attrsToDelete the attributes to be deleted.
   * @param schema the server schema.
   * @return an ordered list of the object classes that must be deleted when
   * deleting a list of attributes that must be deleted.
   */
  public static LinkedHashSet<ObjectClass>
  getOrderedObjectClassesToDeleteFromAttrs(
      Collection<AttributeType> attrsToDelete, Schema schema)
  {
    LinkedHashSet<ObjectClass> orderedOcs = new LinkedHashSet<ObjectClass>();
    ArrayList<ObjectClass> dependentClasses = new ArrayList<ObjectClass>();
    for (AttributeType attr : attrsToDelete)
    {
      for (ObjectClass oc : schema.getObjectClasses().values())
      {
        if (oc.getRequiredAttributeChain().contains(attr))
        {
          dependentClasses.add(oc);
        }
        else if (oc.getOptionalAttributeChain().contains(attr))
        {
          dependentClasses.add(oc);
        }
      }
    }
    for (ObjectClass oc : dependentClasses)
    {
      orderedOcs.addAll(getOrderedChildrenToDelete(oc, schema));
      orderedOcs.add(oc);
    }
    return orderedOcs;
  }

  /**
   * Clones the extra properties of the provided schema element.  This can
   * be used when copying schema elements.
   * @param element the schema element.
   * @return the extra properties of the provided schema element.
   */
  public static Map<String, List<String>> cloneExtraProperties(
      CommonSchemaElements element)
  {
    Map<String, List<String>> extraProperties =
      new HashMap<String, List<String>>();
    for (String name : element.getExtraPropertyNames())
    {
      List<String> values = new ArrayList<String>();
      Iterable<String> properties = element.getExtraProperty(name);
      for (String v : properties)
      {
        values.add(v);
      }
      extraProperties.put(name, values);
    }
    return extraProperties;
  }


  private static LinkedHashSet<AttributeType> getOrderedChildrenToDelete(
      AttributeType attribute, Schema schema)
  {
    LinkedHashSet<AttributeType> children = new LinkedHashSet<AttributeType>();
    for (AttributeType attr : schema.getAttributeTypes().values())
    {
      if (attribute.equals(attr.getSuperiorType()))
      {
        children.addAll(getOrderedChildrenToDelete(attr, schema));
        children.add(attr);
      }
    }
    return children;
  }

  private static LinkedHashSet<ObjectClass> getOrderedChildrenToDelete(
      ObjectClass objectClass, Schema schema)
  {
    LinkedHashSet<ObjectClass> children = new LinkedHashSet<ObjectClass>();
    for (ObjectClass oc : schema.getObjectClasses().values())
    {
      if (oc.getSuperiorClasses().contains(objectClass))
      {
        children.addAll(getOrderedChildrenToDelete(oc, schema));
        children.add(oc);
      }
    }
    return children;
  }
}
