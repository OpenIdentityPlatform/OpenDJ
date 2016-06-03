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
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.task;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.server.types.ExistingFileBehavior.*;
import static org.opends.server.util.SchemaUtils.*;

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

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageDescriptor.Arg1;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.SchemaElement;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.SomeSchemaElement;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.server.config.ConfigConstants;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attributes;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.Modification;
import org.opends.server.types.OpenDsException;
import org.opends.server.types.Schema;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.LDIFWriter;

/** The task that is launched when a schema element must be deleted. */
public class DeleteSchemaElementsTask extends Task
{
  /** The list of object classes that the user asked to delete. */
  private Set<ObjectClass> providedOcsToDelete = new LinkedHashSet<>();
  /** The list of attributes that the user asked to delete. */
  private Set<AttributeType> providedAttrsToDelete = new LinkedHashSet<>();
  /** The list of object classes that will be actually deleted (some might be recreated). */
  private Set<ObjectClass> ocsToDelete = new LinkedHashSet<>();
  /** The list of attributes that will be actually deleted (some might be recreated). */
  private Set<AttributeType> attrsToDelete = new LinkedHashSet<>();
  /** The list of object classes that will be recreated. */
  private Set<ObjectClass> ocsToAdd = new LinkedHashSet<>();
  /** The list of attributes that will be recreated. */
  private Set<AttributeType> attrsToAdd = new LinkedHashSet<>();

  /**
   * Constructor of the task.
   * @param info the control panel information.
   * @param dlg the progress dialog where the task progress will be displayed.
   * @param ocsToDelete the object classes that must be deleted (ordered).
   * @param attrsToDelete the attributes that must be deleted (ordered).
   */
  public DeleteSchemaElementsTask(ControlPanelInfo info, ProgressDialog dlg,
      Set<ObjectClass> ocsToDelete, Set<AttributeType> attrsToDelete)
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
      LinkedHashSet<ObjectClass> orderedOCs =
          DeleteSchemaElementsTask.getOrderedObjectClassesToDelete(ocsToDelete, schema);
      if (allOcsToDelete == null)
      {
        allOcsToDelete = orderedOCs;
      }
      else
      {
        allOcsToDelete.addAll(orderedOCs);
      }
    }
    ArrayList<AttributeType> lAttrsToDelete = new ArrayList<>(allAttrsToDelete);
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
    ArrayList<ObjectClass> lOcsToDelete = new ArrayList<>(allOcsToDelete);
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

  @Override
  public Set<String> getBackends()
  {
    return Collections.emptySet();
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
  public Type getType()
  {
    return Type.NEW_SCHEMA_ELEMENT;
  }

  @Override
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

  @Override
  protected String getCommandLinePath()
  {
    return null;
  }

  @Override
  protected List<String> getCommandLineArguments()
  {
    return Collections.emptyList();
  }

  @Override
  public LocalizableMessage getTaskDescription()
  {
    return INFO_CTRL_PANEL_DELETE_SCHEMA_ELEMENT_TASK_DESCRIPTION.get();
  }

  /**
   * Updates the schema.
   * @throws OpenDsException if an error occurs.
   */
  private void updateSchema() throws OpenDsException
  {
    final int totalNumber = ocsToDelete.size() + attrsToDelete.size();
    int numberDeleted = 0;
    for (ObjectClass objectClass : ocsToDelete)
    {
      final SomeSchemaElement element = new SomeSchemaElement(objectClass);
      deleteSchemaElement(element, numberDeleted, totalNumber, INFO_CTRL_PANEL_DELETING_OBJECTCLASS);
      numberDeleted++;
    }

    for (AttributeType attribute : attrsToDelete)
    {
      final SomeSchemaElement element = new SomeSchemaElement(attribute);
      deleteSchemaElement(element, numberDeleted, totalNumber, INFO_CTRL_PANEL_DELETING_ATTRIBUTE);
      numberDeleted++;
    }

    if (!ocsToAdd.isEmpty() || !attrsToAdd.isEmpty())
    {
      SwingUtilities.invokeLater(new Runnable()
      {
        @Override
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

  private void deleteSchemaElement(final SomeSchemaElement element, final int numberDeleted, final int totalNumber,
      final Arg1<Object> deletingElementMsg) throws OnlineUpdateException, OpenDsException
  {
    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        final boolean isFirst = numberDeleted == 0;
        if (!isFirst)
        {
          getProgressDialog().appendProgressHtml("<br><br>");
        }
        printEquivalentCommandToDelete(element);
        getProgressDialog().appendProgressHtml(
            Utilities.getProgressWithPoints(
                deletingElementMsg.get(element.getNameOrOID()), ColorAndFontConstants.progressFont));
      }
    });

    if (isServerRunning())
    {
      try
      {
        BasicAttribute attr = new BasicAttribute(element.getAttributeName());
        attr.add(getSchemaFileAttributeValue(element));
        ModificationItem mod = new ModificationItem(DirContext.REMOVE_ATTRIBUTE, attr);
        getInfo().getConnection().getLdapContext().modifyAttributes(
            ConfigConstants.DN_DEFAULT_SCHEMA_ROOT,
            new ModificationItem[]  { mod });
      }
      catch (NamingException ne)
      {
        throw new OnlineUpdateException(ERR_CTRL_PANEL_ERROR_UPDATING_SCHEMA.get(ne), ne);
      }
    }
    else
    {
      updateSchemaFile(element);
    }

    final int fNumberDeleted = numberDeleted + 1;
    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        getProgressDialog().getProgressBar().setIndeterminate(false);
        getProgressDialog().getProgressBar().setValue((fNumberDeleted * 100) / totalNumber);
        getProgressDialog().appendProgressHtml(
            Utilities.getProgressDone(ColorAndFontConstants.progressFont));
      }
    });
  }

  /**
   * Updates the schema file by deleting the provided schema element.
   * @param schemaElement the schema element to be deleted.
   * @throws OpenDsException if an error occurs.
   */
  private void updateSchemaFile(SomeSchemaElement schemaElement) throws OpenDsException
  {
    String schemaFile = getSchemaFile(schemaElement);

    try (LDIFExportConfig exportConfig = new LDIFExportConfig(schemaFile, OVERWRITE);
        LDIFReader reader = new LDIFReader(new LDIFImportConfig(schemaFile)))
    {
      Entry schemaEntry = reader.readEntry();

      Modification mod = new Modification(ModificationType.DELETE,
          Attributes.create(
              schemaElement.getAttributeName(),
              getSchemaFileAttributeValue(schemaElement)));
      schemaEntry.applyModification(mod);
      try (LDIFWriter writer = new LDIFWriter(exportConfig))
      {
        writer.writeEntry(schemaEntry);
        exportConfig.getWriter().newLine();
      }
    }
    catch (IOException e)
    {
      throw new OfflineUpdateException(
          ERR_CTRL_PANEL_ERROR_UPDATING_SCHEMA.get(e), e);
    }
  }

  /**
   * Returns the schema file for a given schema element.
   * @param element the schema element.
   * @return the schema file for a given schema element.
   */
  private String getSchemaFile(SomeSchemaElement element)
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
    return f.getAbsolutePath();
  }

  /**
   * Returns the value in the schema file for the provided element.
   * @param element the schema element.
   * @return the value in the schema file for the provided element.
   */
  private String getSchemaFileAttributeValue(SomeSchemaElement element)
  {
    return element.toString();
  }

  /**
   * Prints the equivalent command-line to delete the schema element in the
   * progress dialog.
   * @param element the schema element to be deleted.
   */
  private void printEquivalentCommandToDelete(SomeSchemaElement element)
  {
    String schemaFile = getSchemaFile(element);
    String attrName = element.getAttributeName();
    String attrValue = getSchemaFileAttributeValue(element);

    String msg;
    if (!isServerRunning())
    {
      msg = getEquivalentCommandOfflineMsg(element, schemaFile)
          + "<br><b>" + attrName + ": " + attrValue + "</b><br><br>";
    }
    else
    {
      ArrayList<String> args = new ArrayList<>();
      args.add("-a");
      args.addAll(getObfuscatedCommandLineArguments(
          getConnectionCommandLineArguments(true, true)));
      args.add(getNoPropertiesFileArgument());
      String equiv = getEquivalentCommandLine(getCommandLinePath("ldapmodify"), args);

      msg = getEquivalentCommandOnlineMsg(element)
          + "<br><b>" + equiv + "<br>"
          + "dn: cn=schema<br>"
          + "changetype: modify<br>"
          + "delete: " + attrName + "<br>"
          + attrName + ": " + attrValue
          + "</b>"
          + "<br><br>";
    }
    getProgressDialog().appendProgressHtml(Utilities.applyFont(msg, ColorAndFontConstants.progressFont));
  }

  private LocalizableMessage getEquivalentCommandOfflineMsg(SomeSchemaElement element, String schemaFile)
  {
    if (element.isAttributeType())
    {
      return INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_DELETE_ATTRIBUTE_OFFLINE.get(element.getNameOrOID(), schemaFile);
    }
    return INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_DELETE_OBJECTCLASS_OFFLINE.get(element.getNameOrOID(), schemaFile);
  }

  private LocalizableMessage getEquivalentCommandOnlineMsg(SomeSchemaElement element)
  {
    if (element.isAttributeType())
    {
      return INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_DELETE_ATTRIBUTE_ONLINE.get(element.getNameOrOID());
    }
    return INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_DELETE_OBJECTCLASS_ONLINE.get(element.getNameOrOID());
  }

  private AttributeType getAttributeToAdd(AttributeType attrToDelete)
  {
    boolean isSuperior = false;
    for (AttributeType attr : providedAttrsToDelete)
    {
      if (attr.equals(attrToDelete.getSuperiorType()))
      {
        isSuperior = true;
        break;
      }
    }
    if (isSuperior)
    {
       // get a new attribute without the superior type
       return SomeSchemaElement.changeSuperiorType(attrToDelete, null);
    }
    else
    {
      // Nothing to be changed in the definition of the attribute itself.
      return attrToDelete;
    }
  }

  private ObjectClass getObjectClassToAdd(ObjectClass ocToDelete)
  {
    boolean containsAttribute = containsAttributeToDelete(ocToDelete);
    boolean hasSuperior = false;
    Set<ObjectClass> newSuperiors = new LinkedHashSet<>();
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
      Map<String, List<String>> extraProperties = cloneExtraProperties(ocToDelete);
      Set<AttributeType> required;
      Set<AttributeType> optional;
      if (containsAttribute)
      {
        required = new HashSet<>(ocToDelete.getDeclaredRequiredAttributes());
        optional = new HashSet<>(ocToDelete.getDeclaredOptionalAttributes());
        required.removeAll(providedAttrsToDelete);
        optional.removeAll(providedAttrsToDelete);
      }
      else
      {
        required = ocToDelete.getDeclaredRequiredAttributes();
        optional = ocToDelete.getDeclaredOptionalAttributes();
      }
      final String oid = ocToDelete.getOID();
      final Schema schema = getInfo().getServerDescriptor().getSchema();
      return new SchemaBuilder(schema.getSchemaNG()).buildObjectClass(oid)
          .names(ocToDelete.getNames())
          .description(ocToDelete.getDescription())
          .superiorObjectClasses(getNameOrOIDsForOCs(newSuperiors))
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

  private boolean containsAttributeToDelete(ObjectClass ocToDelete)
  {
    for (AttributeType attr : providedAttrsToDelete)
    {
      if (ocToDelete.getRequiredAttributes().contains(attr)
          || ocToDelete.getOptionalAttributes().contains(attr))
      {
        return true;
      }
    }
    return false;
  }

  private Set<ObjectClass> getNewSuperiors(ObjectClass currentSup)
  {
    Set<ObjectClass> newSuperiors = new LinkedHashSet<>();
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
    LinkedHashSet<AttributeType> orderedAttributes = new LinkedHashSet<>();
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
    LinkedHashSet<ObjectClass> orderedOcs = new LinkedHashSet<>();
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
    LinkedHashSet<ObjectClass> orderedOcs = new LinkedHashSet<>();
    ArrayList<ObjectClass> dependentClasses = new ArrayList<>();
    for (AttributeType attr : attrsToDelete)
    {
      for (ObjectClass oc : schema.getObjectClasses())
      {
        if (oc.getRequiredAttributes().contains(attr)
            || oc.getOptionalAttributes().contains(attr))
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
  public static Map<String, List<String>> cloneExtraProperties(SchemaElement element)
  {
    Map<String, List<String>> extraProperties = new HashMap<>();
    Map<String, List<String>> props = element.getExtraProperties();
    for (String name : props.keySet())
    {
      extraProperties.put(name, new ArrayList<>(props.get(name)));
    }
    return extraProperties;
  }

  private static LinkedHashSet<AttributeType> getOrderedChildrenToDelete(
      AttributeType attribute, Schema schema)
  {
    LinkedHashSet<AttributeType> children = new LinkedHashSet<>();
    for (AttributeType attr : schema.getAttributeTypes())
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
    LinkedHashSet<ObjectClass> children = new LinkedHashSet<>();
    for (ObjectClass oc : schema.getObjectClasses())
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
