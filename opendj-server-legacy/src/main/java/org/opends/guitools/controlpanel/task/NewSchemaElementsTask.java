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
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.task;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.forgerock.util.Utils.*;
import static org.opends.guitools.controlpanel.util.Utilities.*;
import static org.opends.messages.AdminToolMessages.*;
import static org.opends.server.util.SchemaUtils.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.SwingUtilities;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.SchemaElement;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.server.config.ConfigConstants;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.Modification;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.ServerConstants;

/**
 * An abstract class used to re-factor some code between the different tasks
 * that create elements in the schema.
 */
public class NewSchemaElementsTask extends Task
{
  private final Set<ObjectClass> ocsToAdd = new LinkedHashSet<>();
  private final Set<AttributeType> attrsToAdd = new LinkedHashSet<>();

  /**
   * Constructor of the task.
   *
   * @param info
   *          the control panel information.
   * @param dlg
   *          the progress dialog where the task progress will be displayed.
   * @param ocsToAdd
   *          the object classes that must be created in order.
   * @param attrsToAdd
   *          the attributes that must be created in order.
   */
  public NewSchemaElementsTask(
      ControlPanelInfo info, ProgressDialog dlg, Set<ObjectClass> ocsToAdd, Set<AttributeType> attrsToAdd)
  {
    super(info, dlg);
    this.ocsToAdd.addAll(ocsToAdd);
    this.attrsToAdd.addAll(attrsToAdd);
  }

  @Override
  public Set<String> getBackends()
  {
    return Collections.emptySet();
  }

  @Override
  public boolean canLaunch(Task taskToBeLaunched, Collection<LocalizableMessage> incompatibilityReasons)
  {
    Type taskTypeToBeLaunched = taskToBeLaunched.getType();
    if (state == State.RUNNING &&
        (taskTypeToBeLaunched == Task.Type.DELETE_SCHEMA_ELEMENT
            || taskTypeToBeLaunched == Task.Type.MODIFY_SCHEMA_ELEMENT
            || taskTypeToBeLaunched == Task.Type.NEW_SCHEMA_ELEMENT))
    {
      incompatibilityReasons.add(getIncompatibilityMessage(this, taskToBeLaunched));
      return false;
    }
    return true;
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
  public Type getType()
  {
    return Type.NEW_SCHEMA_ELEMENT;
  }

  @Override
  public LocalizableMessage getTaskDescription()
  {
    if (attrsToAdd.size() == 1 && ocsToAdd.isEmpty())
    {
      return INFO_CTRL_PANEL_NEW_ATTRIBUTE_TASK_DESCRIPTION.get(attrsToAdd.iterator().next().getNameOrOID());
    }
    else if (ocsToAdd.size() == 1 && attrsToAdd.isEmpty())
    {
      return INFO_CTRL_PANEL_NEW_OBJECTCLASS_TASK_DESCRIPTION.get(ocsToAdd.iterator().next().getNameOrOID());
    }
    else
    {
      final List<String> attrNames = getElementsNameOrOID(attributeTypesToSchemaElements(attrsToAdd));
      final List<String> ocNames = getElementsNameOrOID(objectClassesToSchemaElements(ocsToAdd));
      String attrNamesStr = joinAsString(", ", attrNames);
      String ocNamesStr = joinAsString(", ", ocNames);
      if (ocNames.isEmpty())
      {
        return INFO_CTRL_PANEL_NEW_ATTRIBUTES_TASK_DESCRIPTION.get(attrNamesStr);
      }
      else if (attrNames.isEmpty())
      {
        return INFO_CTRL_PANEL_NEW_OBJECTCLASSES_TASK_DESCRIPTION.get(ocNamesStr);
      }
      else
      {
        return INFO_CTRL_PANEL_NEW_SCHEMA_ELEMENTS_TASK_DESCRIPTION.get(attrNamesStr, ocNamesStr);
      }
    }
  }

  private List<String> getElementsNameOrOID(final Collection<SchemaElement> schemaElements)
  {
    final List<String> nameOrOIDs = new ArrayList<>();
    for (SchemaElement schemaElement : schemaElements)
    {
      nameOrOIDs.add(getElementNameOrOID(schemaElement));
    }
    return nameOrOIDs;
  }

  /**
   * Update the schema.
   *
   * @throws OpenDsException
   *           if an error occurs.
   */
  private void updateSchema() throws OpenDsException
  {
    if (isServerRunning())
    {
      updateSchemaOnline();
    }
    else
    {
      updateSchemaOffline();
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

  /**
   * Add the schema elements one by one: we are not sure that the server will
   * handle the adds sequentially if we only send one modification.
   *
   * @throws OpenDsException
   */
  private void updateSchemaOnline() throws OpenDsException
  {
    for (AttributeType attr : attrsToAdd)
    {
      addAttributeOnline(attr);
      appendNewLinesToProgress();
    }

    for (ObjectClass oc : ocsToAdd)
    {
      addObjectClassOnline(oc);
      appendNewLinesToProgress();
    }
  }

  private void appendNewLinesToProgress()
  {
    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        getProgressDialog().appendProgressHtml(Utilities.applyFont("<br><br>", ColorAndFontConstants.progressFont));
      }
    });
  }

  private void updateSchemaOffline() throws OpenDsException
  {
    // Group the changes in the same schema file.
    final Map<String, List<SchemaElement>> mapAttrs = copy(attributeTypesToSchemaElements(attrsToAdd));
    final Map<String, List<SchemaElement>> mapClasses = copy(objectClassesToSchemaElements(ocsToAdd));
    final Set<String> allFileNames = new LinkedHashSet<>(mapAttrs.keySet());
    allFileNames.addAll(mapClasses.keySet());

    for (String fileName : allFileNames)
    {
      List<AttributeType> attrs = schemaElementsToAttributeTypes(get(mapAttrs, fileName));
      List<ObjectClass> ocs = schemaElementsToObjectClasses(get(mapClasses, fileName));

      if ("".equals(fileName))
      {
        fileName = null;
      }
      updateSchemaOffline(fileName, attrs, ocs);
      appendNewLinesToProgress();
    }
  }

  private List<SchemaElement> get(Map<String, List<SchemaElement>> hmElems, String fileName)
  {
    List<SchemaElement> elems = hmElems.get(fileName);
    return elems != null ? elems : Collections.<SchemaElement> emptyList();
  }

  private Map<String, List<SchemaElement>> copy(Set<SchemaElement> elemsToAdd)
  {
    Map<String, List<SchemaElement>> hmElems = new LinkedHashMap<>();
    for (SchemaElement elem : elemsToAdd)
    {
      String fileName = getElementSchemaFile(elem);
      if (fileName == null)
      {
        fileName = "";
      }
      List<SchemaElement> elems = hmElems.get(fileName);
      if (elems == null)
      {
        elems = new ArrayList<>();
        hmElems.put(fileName, elems);
      }
      elems.add(elem);
    }
    return hmElems;
  }

  private void addAttributeOnline(final AttributeType attribute) throws OpenDsException
  {
    addSchemaElementOnline(attribute,
        INFO_CTRL_PANEL_CREATING_ATTRIBUTE_PROGRESS.get(attribute.getNameOrOID()));
  }

  private void addObjectClassOnline(final ObjectClass objectClass) throws OpenDsException
  {
    addSchemaElementOnline(objectClass,
        INFO_CTRL_PANEL_CREATING_OBJECTCLASS_PROGRESS.get(objectClass.getNameOrOID()));
  }

  private void addSchemaElementOnline(final SchemaElement schemaElement, final LocalizableMessage progressMsg)
      throws OpenDsException
  {
    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        printEquivalentCommandLineToAddOnline(schemaElement);
        getProgressDialog().appendProgressHtml(
            Utilities.getProgressWithPoints(progressMsg, ColorAndFontConstants.progressFont));
      }
    });
    try
    {
      ModifyRequest request = Requests.newModifyRequest(ConfigConstants.DN_DEFAULT_SCHEMA_ROOT)
          .addModification(ADD, getAttributeConfigName(schemaElement), schemaElement.toString());
      getInfo().getConnection().getConnection().modify(request);
    }
    catch (LdapException e)
    {
      throw new OnlineUpdateException(ERR_CTRL_PANEL_ERROR_UPDATING_SCHEMA.get(e), e);
    }
    notifyConfigurationElementCreated(schemaElement);
    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        getProgressDialog().appendProgressHtml(Utilities.getProgressDone(ColorAndFontConstants.progressFont));
      }
    });
  }

  /** Returns the definition for provided element without the file name. */
  private String getValueOffline(SchemaElement element)
  {
    return updateSchemaElementExtraPropertySingleValue(null, element, ServerConstants.SCHEMA_PROPERTY_FILENAME, null)
        .toString();
  }

  private Set<SchemaElement> objectClassesToSchemaElements(final Collection<ObjectClass> classes)
  {
    Set<SchemaElement> elements = new HashSet<>();
    for (ObjectClass objectClass : classes)
    {
      elements.add(objectClass);
    }
    return elements;
  }

  private Set<SchemaElement> attributeTypesToSchemaElements(final Collection<AttributeType> types)
  {
    Set<SchemaElement> elements = new HashSet<>();
    for (AttributeType type : types)
    {
      elements.add(type);
    }
    return elements;
  }

  private List<AttributeType> schemaElementsToAttributeTypes(final Collection<SchemaElement> elements)
  {
    List<AttributeType> types = new ArrayList<>();
    for (SchemaElement element : elements)
    {
      types.add((AttributeType) element);
    }
    return types;
  }

  private List<ObjectClass> schemaElementsToObjectClasses(final Collection<SchemaElement> elements)
  {
    List<ObjectClass> classes = new ArrayList<>();
    for (SchemaElement element : elements)
    {
      classes.add((ObjectClass) element);
    }
    return classes;
  }

  private void append(final StringBuilder buffer, final String label, final String value)
  {
    buffer.append(label).append(value);
  }

  private void appendCollection(final StringBuilder buffer, final String property, final Collection<String> values)
  {
    final boolean isMultiValued = values.size() > 1;
    if (!values.isEmpty())
    {
      buffer.append(" ").append(property);
      buffer.append(isMultiValued ? " ( '" : " '");
      final Iterator<String> it = values.iterator();
      buffer.append(it.next()).append("' ");
      while (it.hasNext())
      {
        buffer.append("'").append(it.next()).append("' ");
      }
      if (isMultiValued)
      {
        buffer.append(")");
      }
    }
  }

  private void printEquivalentCommandLineToAddOnline(SchemaElement element)
  {
    List<String> args = new ArrayList<>();
    args.add("-a");
    args.addAll(getObfuscatedCommandLineArguments(getConnectionCommandLineArguments(true, true)));
    args.add(getNoPropertiesFileArgument());

    final String equivalentCmdLine = getEquivalentCommandLine(getCommandLinePath("ldapmodify"), args);
    final StringBuilder sb = new StringBuilder();
    final String attName = getAttributeConfigName(element);
    final String elementId = getElementNameOrOID(element);
    final LocalizableMessage message = isAttributeType(element)
        ? INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_ADD_ATTRIBUTE_ONLINE.get(elementId)
        : INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_ADD_OBJECTCLASS_ONLINE.get(elementId);
    sb.append(message).append("<br><b>")
      .append(equivalentCmdLine).append("<br>")
      .append("dn: cn=schema<br>")
      .append("changetype: modify<br>")
      .append("add: ").append(attName).append("<br>")
      .append(attName).append(": ").append(element.toString()).append("</b><br><br>");
    getProgressDialog().appendProgressHtml(Utilities.applyFont(sb.toString(), ColorAndFontConstants.progressFont));
  }

  private void updateSchemaOffline(
      String file, final List<AttributeType> attributes, final List<ObjectClass> objectClasses) throws OpenDsException
  {
    final List<SchemaElement> schemaElements =
        new ArrayList<SchemaElement>(attributeTypesToSchemaElements(attributes));
    schemaElements.addAll(objectClassesToSchemaElements(objectClasses));
    if (file == null)
    {
      file = ConfigConstants.FILE_USER_SCHEMA_ELEMENTS;
    }
    File f = new File(file);
    if (!f.isAbsolute())
    {
      f = new File(DirectoryServer.getEnvironmentConfig().getSchemaDirectory(), file);
    }
    final String fileName = f.getAbsolutePath();
    final boolean isSchemaFileDefined = isSchemaFileDefined(fileName);
    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        final ProgressDialog progressDialog = getProgressDialog();
        final String command = equivalentCommandToAddOffline(fileName, isSchemaFileDefined, schemaElements);
        progressDialog.appendProgressHtml(Utilities.applyFont(command, ColorAndFontConstants.progressFont));

        if (attributes.size() == 1 && objectClasses.isEmpty())
        {
          String attributeName = attributes.get(0).getNameOrOID();
          progressDialog.appendProgressHtml(Utilities.getProgressWithPoints(
              INFO_CTRL_PANEL_CREATING_ATTRIBUTE_PROGRESS.get(attributeName), ColorAndFontConstants.progressFont));
        }
        else if (objectClasses.size() == 1 && attributes.isEmpty())
        {
          String ocName = objectClasses.get(0).getNameOrOID();
          progressDialog.appendProgressHtml(Utilities.getProgressWithPoints(
              INFO_CTRL_PANEL_CREATING_OBJECTCLASS_PROGRESS.get(ocName), ColorAndFontConstants.progressFont));
        }
        else
        {
          progressDialog.appendProgressHtml(Utilities.getProgressWithPoints(
              INFO_CTRL_PANEL_UPDATING_SCHEMA_FILE_PROGRESS.get(fileName), ColorAndFontConstants.progressFont));
        }
      }
    });

    if (isSchemaFileDefined)
    {
      updateSchemaFile(fileName, schemaElements);
    }
    else
    {
      updateSchemaUndefinedFile(fileName, schemaElements);
    }

    for (SchemaElement schemaElement : schemaElements)
    {
      notifyConfigurationElementCreated(schemaElement);
    }
    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        getProgressDialog().appendProgressHtml(Utilities.getProgressDone(ColorAndFontConstants.progressFont));
      }
    });
  }

  private String equivalentCommandToAddOffline(
      String schemaFile, boolean isSchemaFileDefined, List<SchemaElement> schemaElements)
  {
    List<String> names = getElementsNameOrOID(schemaElements);

    final String namesString = joinAsString(", ", names);
    final StringBuilder sb = new StringBuilder();
    if (isSchemaFileDefined)
    {
      sb.append(INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_ADD_SCHEMA_ELEMENT_OFFLINE.get(namesString, schemaFile))
        .append("<br><b>");
    }
    else
    {
      sb.append(INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_ADD_SCHEMA_ENTRY_OFFLINE.get(namesString, schemaFile))
        .append("<br><b>");
      for (String line : getSchemaEntryLines())
      {
        sb.append(line);
        sb.append("<br>");
      }
    }

    for (SchemaElement element : schemaElements)
    {
      sb.append(getAttributeConfigName(element)).append(": ").append(getValueOffline(element)).append("<br>");
    }
    sb.append("</b><br><br>");

    return sb.toString();
  }

  /**
   * Returns whether the file defined in the schema element exists or not.
   *
   * @param schemaFile
   *          the path to the schema file.
   * @return <CODE>true</CODE> if the schema file is defined and
   *         <CODE>false</CODE> otherwise.
   */
  private boolean isSchemaFileDefined(String schemaFile)
  {
    try (LDIFReader reader = new LDIFReader(new LDIFImportConfig(schemaFile)))
    {
      return reader.readEntry() != null;
    }
    catch (Throwable t)
    {
      return false;
    }
  }

  /**
   * Returns the list of LDIF lines that are enough to create the entry
   * containing only the schema element associated with this task.
   *
   * @return the list of LDIF lines that are enough to create the entry
   *         containing only the schema element associated with this task.
   */
  private List<String> getSchemaEntryLines()
  {
    List<String> lines = new ArrayList<>();
    lines.add("dn: cn=schema");
    lines.add("objectClass: top");
    lines.add("objectClass: ldapSubentry");
    lines.add("objectClass: subschema");
    return lines;
  }

  /**
   * Updates the contents of the schema file.
   *
   * @param schemaFile
   *          the schema file.
   * @param isSchemaFileDefined
   *          whether the schema is defined or not.
   * @param attributes
   *          the attributes to add.
   * @param objectClasses
   *          the object classes to add.
   * @throws OpenDsException
   *           if an error occurs updating the schema file.
   */
  private void updateSchemaFile(String schemaFile, List<SchemaElement> schemaElements)
      throws OpenDsException
  {
    try (final LDIFExportConfig exportConfig = new LDIFExportConfig(schemaFile, ExistingFileBehavior.OVERWRITE))
    {
      try (final LDIFReader reader = new LDIFReader(new LDIFImportConfig(schemaFile)))
      {
        final Entry schemaEntry = reader.readEntry();
        addElementsToEntry(schemaElements, schemaEntry);
        try (final LDIFWriter writer = new LDIFWriter(exportConfig))
        {
          writer.writeEntry(schemaEntry);
          exportConfig.getWriter().newLine();
        }
      }
      catch (Throwable t)
      {
        throw new OfflineUpdateException(ERR_CTRL_PANEL_ERROR_UPDATING_SCHEMA.get(t), t);
      }
    }
  }

  private void addElementsToEntry(List<SchemaElement> schemaElements, Entry schemaEntry)
      throws DirectoryException
  {
    for (SchemaElement element : schemaElements)
    {
      Attribute attr = Attributes.create(getAttributeConfigName(element), getValueOffline(element));
      schemaEntry.applyModification(new Modification(ADD, attr));
    }
  }

  private void updateSchemaUndefinedFile(String schemaFile, List<SchemaElement> schemaElements)
      throws OfflineUpdateException
  {
    try (LDIFExportConfig exportConfig = new LDIFExportConfig(schemaFile, ExistingFileBehavior.FAIL))
    {
      List<String> lines = getSchemaEntryLines();
      for (final SchemaElement element : schemaElements)
      {
        lines.add(getAttributeConfigName(element) + ": " + getValueOffline(element));
      }
      for (String line : lines)
      {
        final boolean wrapLines = exportConfig.getWrapColumn() > 1;
        LDIFWriter.writeLDIFLine(
            new StringBuilder(line), exportConfig.getWriter(), wrapLines, exportConfig.getWrapColumn());
      }
      exportConfig.getWriter().newLine();
    }
    catch (Throwable t)
    {
      throw new OfflineUpdateException(ERR_CTRL_PANEL_ERROR_UPDATING_SCHEMA.get(t), t);
    }
  }
}
