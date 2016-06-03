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
import static org.opends.messages.AdminToolMessages.*;

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

import javax.naming.NamingException;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.swing.SwingUtilities;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.SomeSchemaElement;
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
import org.opends.server.util.StaticUtils;

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

  private List<String> getElementsNameOrOID(final Collection<SomeSchemaElement> schemaElements)
  {
    final List<String> nameOrOIDs = new ArrayList<>();
    for (SomeSchemaElement schemaElement : schemaElements)
    {
      nameOrOIDs.add(schemaElement.getNameOrOID());
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
    final Map<String, List<SomeSchemaElement>> mapAttrs = copy(attributeTypesToSchemaElements(attrsToAdd));
    final Map<String, List<SomeSchemaElement>> mapClasses = copy(objectClassesToSchemaElements(ocsToAdd));
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

  private List<SomeSchemaElement> get(Map<String, List<SomeSchemaElement>> hmElems, String fileName)
  {
    List<SomeSchemaElement> elems = hmElems.get(fileName);
    return elems != null ? elems : Collections.<SomeSchemaElement> emptyList();
  }

  private Map<String, List<SomeSchemaElement>> copy(Set<SomeSchemaElement> elemsToAdd)
  {
    Map<String, List<SomeSchemaElement>> hmElems = new LinkedHashMap<>();
    for (SomeSchemaElement elem : elemsToAdd)
    {
      String fileName = elem.getSchemaFile();
      if (fileName == null)
      {
        fileName = "";
      }
      List<SomeSchemaElement> elems = hmElems.get(fileName);
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
    addSchemaElementOnline(new SomeSchemaElement(attribute),
        INFO_CTRL_PANEL_CREATING_ATTRIBUTE_PROGRESS.get(attribute.getNameOrOID()));
  }

  private void addObjectClassOnline(final ObjectClass objectClass) throws OpenDsException
  {
    addSchemaElementOnline(new SomeSchemaElement(objectClass),
        INFO_CTRL_PANEL_CREATING_OBJECTCLASS_PROGRESS.get(objectClass.getNameOrOID()));
  }

  private void addSchemaElementOnline(final SomeSchemaElement schemaElement, final LocalizableMessage progressMsg)
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
      final BasicAttribute attr = new BasicAttribute(schemaElement.getAttributeName());
      attr.add(getElementDefinition(schemaElement));
      final ModificationItem mod = new ModificationItem(DirContext.ADD_ATTRIBUTE, attr);
      getInfo().getConnection().getLdapContext().modifyAttributes(
          ConfigConstants.DN_DEFAULT_SCHEMA_ROOT, new ModificationItem[] { mod });
    }
    catch (NamingException ne)
    {
      throw new OnlineUpdateException(ERR_CTRL_PANEL_ERROR_UPDATING_SCHEMA.get(ne), ne);
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

  private String getValueOffline(SomeSchemaElement element)
  {
    final Map<String, List<String>> props = element.getExtraProperties();
    List<String> previousValues = props.get(ServerConstants.SCHEMA_PROPERTY_FILENAME);
    element.setExtraPropertySingleValue(null, ServerConstants.SCHEMA_PROPERTY_FILENAME, null);
    String attributeWithoutFileDefinition = getElementDefinition(element);

    if (previousValues != null && !previousValues.isEmpty())
    {
      element.setExtraPropertyMultipleValues(null,
          ServerConstants.SCHEMA_PROPERTY_FILENAME, new ArrayList<String>(previousValues));
    }
    return attributeWithoutFileDefinition;
  }

  private String getElementDefinition(SomeSchemaElement element)
  {
    final List<String> names = new ArrayList<>();
    for (final String name : element.getNames())
    {
      names.add(StaticUtils.toLowerCase(name));
    }
    return element.isAttributeType()
        ? getAttributeTypeDefinition(element.getAttributeType(), names)
        : getObjectClassDefinition(element.getObjectClass(), names);
  }

  private String getAttributeTypeDefinition(final AttributeType attributeType, final List<String> names)
  {
    final StringBuilder buffer = new StringBuilder();
    buffer.append("( ").append(attributeType.getOID());
    appendCollection(buffer, "NAME", names);
    appendDescription(buffer, attributeType.getDescription());
    appendIfTrue(buffer, " OBSOLETE", attributeType.isObsolete());

    final AttributeType superiorType = attributeType.getSuperiorType();
    final String superiorTypeOID = superiorType != null ? superiorType.getOID() : null;
    appendIfNotNull(buffer, " SUP ", superiorTypeOID);
    addMatchingRuleIfNotNull(buffer, " EQUALITY ", attributeType.getEqualityMatchingRule());
    addMatchingRuleIfNotNull(buffer, " ORDERING ", attributeType.getOrderingMatchingRule());
    addMatchingRuleIfNotNull(buffer, " SUBSTR ", attributeType.getSubstringMatchingRule());
    appendIfNotNull(buffer, " SYNTAX ", attributeType.getSyntax().getOID());
    appendIfTrue(buffer, " SINGLE-VALUE", attributeType.isSingleValue());
    appendIfTrue(buffer, " COLLECTIVE", attributeType.isCollective());
    appendIfTrue(buffer, " NO-USER-MODIFICATION", attributeType.isNoUserModification());
    appendIfNotNull(buffer, " USAGE ", attributeType.getUsage());

    final MatchingRule approximateMatchingRule = attributeType.getApproximateMatchingRule();
    if (approximateMatchingRule != null)
    {
      buffer.append(" ").append(ServerConstants.SCHEMA_PROPERTY_APPROX_RULE).append(" '")
            .append(approximateMatchingRule.getOID()).append("'");
    }
    appendExtraProperties(buffer, attributeType.getExtraProperties());
    buffer.append(")");

    return buffer.toString();
  }

  private void addMatchingRuleIfNotNull(final StringBuilder buffer, final String label, final MatchingRule matchingRule)
  {
    if (matchingRule != null)
    {
      append(buffer, label, matchingRule.getOID());
    }
  }

  private String getObjectClassDefinition(final ObjectClass objectClass, final List<String> names)
  {
    final StringBuilder buffer = new StringBuilder();
    buffer.append("( ");
    buffer.append(objectClass.getOID());
    appendCollection(buffer, "NAME", names);
    appendDescription(buffer, objectClass.getDescription());
    appendIfTrue(buffer, " OBSOLETE", objectClass.isObsolete());
    appendOIDs(buffer, "SUP", objectClassesToSchemaElements(objectClass.getSuperiorClasses()));
    appendIfNotNull(buffer, " ", objectClass.getObjectClassType());
    appendOIDs(buffer, "MUST", attributeTypesToSchemaElements(objectClass.getDeclaredRequiredAttributes()));
    appendOIDs(buffer, "MAY", attributeTypesToSchemaElements(objectClass.getDeclaredOptionalAttributes()));
    appendExtraProperties(buffer, objectClass.getExtraProperties());
    buffer.append(")");

    return buffer.toString();
  }

  private void appendOIDs(final StringBuilder buffer, final String label,
      final Collection<SomeSchemaElement> schemaElements)
  {
    if (!schemaElements.isEmpty())
    {
      buffer.append(" ").append(label).append(" ( ");

      final Iterator<SomeSchemaElement> it = schemaElements.iterator();
      buffer.append(it.next().getOID());
      while (it.hasNext())
      {
        buffer.append(" $ ").append(it.next().getOID());
      }
      buffer.append(" )");
    }
  }

  private Set<SomeSchemaElement> objectClassesToSchemaElements(final Collection<ObjectClass> classes)
  {
    Set<SomeSchemaElement> elements = new HashSet<>();
    for (ObjectClass objectClass : classes)
    {
      elements.add(new SomeSchemaElement(objectClass));
    }
    return elements;
  }

  private Set<SomeSchemaElement> attributeTypesToSchemaElements(final Collection<AttributeType> types)
  {
    Set<SomeSchemaElement> elements = new HashSet<>();
    for (AttributeType type : types)
    {
      elements.add(new SomeSchemaElement(type));
    }
    return elements;
  }

  private List<AttributeType> schemaElementsToAttributeTypes(final Collection<SomeSchemaElement> elements)
  {
    List<AttributeType> types = new ArrayList<>();
    for (SomeSchemaElement element : elements)
    {
      types.add(element.getAttributeType());
    }
    return types;
  }

  private List<ObjectClass> schemaElementsToObjectClasses(final Collection<SomeSchemaElement> elements)
  {
    List<ObjectClass> classes = new ArrayList<>();
    for (SomeSchemaElement element : elements)
    {
      classes.add(element.getObjectClass());
    }
    return classes;
  }

  private void appendIfTrue(final StringBuilder buffer, final String label, final boolean labelIsActive)
  {
    if (labelIsActive)
    {
      buffer.append(label);
    }
  }

  private void appendIfNotNull(final StringBuilder buffer, final String label, final Object value)
  {
    if (value != null)
    {
      append(buffer, label, value.toString());
    }
  }

  private void append(final StringBuilder buffer, final String label, final String value)
  {
    buffer.append(label).append(value);
  }

  private void appendDescription(final StringBuilder buffer, final String description)
  {
    if (description != null && !description.isEmpty())
    {
      buffer.append(" DESC '");
      buffer.append(description);
      buffer.append("'");
    }
  }

  private void appendExtraProperties(
      final StringBuilder buffer, final Map<String, List<String>> extraProperties)
  {
    for (final Map.Entry<String, List<String>> e : extraProperties.entrySet())
    {
      appendCollection(buffer, e.getKey(), e.getValue());
    }
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

  private void printEquivalentCommandLineToAddOnline(SomeSchemaElement element)
  {
    List<String> args = new ArrayList<>();
    args.add("-a");
    args.addAll(getObfuscatedCommandLineArguments(getConnectionCommandLineArguments(true, true)));
    args.add(getNoPropertiesFileArgument());

    final String equivalentCmdLine = getEquivalentCommandLine(getCommandLinePath("ldapmodify"), args);
    final StringBuilder sb = new StringBuilder();
    final String attName = element.getAttributeName();
    final String elementId = element.getNameOrOID();
    final LocalizableMessage message = element.isAttributeType()
        ? INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_ADD_ATTRIBUTE_ONLINE.get(elementId)
        : INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_ADD_OBJECTCLASS_ONLINE.get(elementId);
    sb.append(message).append("<br><b>")
      .append(equivalentCmdLine).append("<br>")
      .append("dn: cn=schema<br>")
      .append("changetype: modify<br>")
      .append("add: ").append(attName).append("<br>")
      .append(attName).append(": ").append(getElementDefinition(element)).append("</b><br><br>");
    getProgressDialog().appendProgressHtml(Utilities.applyFont(sb.toString(), ColorAndFontConstants.progressFont));
  }

  private void updateSchemaOffline(
      String file, final List<AttributeType> attributes, final List<ObjectClass> objectClasses) throws OpenDsException
  {
    final List<SomeSchemaElement> schemaElements =
        new ArrayList<SomeSchemaElement>(attributeTypesToSchemaElements(attributes));
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

    for (SomeSchemaElement schemaElement : schemaElements)
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
      String schemaFile, boolean isSchemaFileDefined, List<SomeSchemaElement> schemaElements)
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

    for (SomeSchemaElement schemaElement : schemaElements)
    {
      sb.append(schemaElement.getAttributeName()).append(": ").append(getValueOffline(schemaElement)).append("<br>");
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
  private void updateSchemaFile(String schemaFile, List<SomeSchemaElement> schemaElements)
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

  private void addElementsToEntry(List<SomeSchemaElement> schemaElements, Entry schemaEntry)
      throws DirectoryException
  {
    for (SomeSchemaElement schemaElement : schemaElements)
    {
      Attribute attr = Attributes.create(schemaElement.getAttributeName(), getValueOffline(schemaElement));
      schemaEntry.applyModification(new Modification(ADD, attr));
    }
  }

  private void updateSchemaUndefinedFile(String schemaFile, List<SomeSchemaElement> schemaElements)
      throws OfflineUpdateException
  {
    try (LDIFExportConfig exportConfig = new LDIFExportConfig(schemaFile, ExistingFileBehavior.FAIL))
    {
      List<String> lines = getSchemaEntryLines();
      for (final SomeSchemaElement schemaElement : schemaElements)
      {
        lines.add(schemaElement.getAttributeName() + ": " + getValueOffline(schemaElement));
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
