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

package org.opends.guitools.controlpanel.task;

import static org.opends.messages.AdminToolMessages.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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
import org.opends.server.types.SchemaFileElement;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.cli.CommandBuilder;

/**
 * The task that is launched when a schema element must be deleted.
 */
public class DeleteSchemaElementsTask extends Task
{
  ArrayList<ObjectClass> ocsToDelete = new ArrayList<ObjectClass>();
  ArrayList<AttributeType> attrsToDelete = new ArrayList<AttributeType>();
  private Set<String> backendSet;

  /**
   * Constructor of the task.
   * @param info the control panel information.
   * @param dlg the progress dialog where the task progress will be displayed.
   * @param ocsToDelete the object classes that must be deleted.
   * @param attrsToDelete the attributes that must be deleted.
   */
  public DeleteSchemaElementsTask(ControlPanelInfo info, ProgressDialog dlg,
      List<ObjectClass> ocsToDelete, List<AttributeType> attrsToDelete)
  {
    super(info, dlg);
    this.ocsToDelete.addAll(ocsToDelete);
    this.attrsToDelete.addAll(attrsToDelete);
    backendSet = new HashSet<String>();
  }

  /**
   * {@inheritDoc}
   */
  public Set<String> getBackends()
  {
    return backendSet;
  }

  /**
   * {@inheritDoc}
   */
  public boolean canLaunch(Task taskToBeLaunched,
      Collection<Message> incompatibilityReasons)
  {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public Type getType()
  {
    if (attrsToDelete.isEmpty())
    {
      return Type.DELETE_OBJECTCLASS;
    }
    else if (ocsToDelete.isEmpty())
    {
      return Type.DELETE_ATTRIBUTE;
    }
    else
    {
      return Type.DELETE_OBJECTCLASS;
    }
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
  protected ArrayList<String> getCommandLineArguments()
  {
    return new ArrayList<String>();
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
    for (final ObjectClass objectClass : ocsToDelete)
    {
      SwingUtilities.invokeLater(new Runnable()
      {
        public void run()
        {
          if (!isFirst[0])
          {
            getProgressDialog().appendProgressHtml("<br><br>");
          }
          isFirst[0] = false;
          printEquivalentCommandToDelete(objectClass);
          getProgressDialog().appendProgressHtml(
              Utilities.getProgressWithPoints(
                  INFO_CTRL_PANEL_DELETING_OBJECTCLASS.get(
                  objectClass.getNameOrOID()),
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

    for (final AttributeType attribute : attrsToDelete)
    {
      SwingUtilities.invokeLater(new Runnable()
      {
        public void run()
        {
          if (!isFirst[0])
          {
            getProgressDialog().appendProgressHtml("<br><br>");
          }
          isFirst[0] = false;
          printEquivalentCommandToDelete(attribute);
          getProgressDialog().appendProgressHtml(
              Utilities.getProgressWithPoints(
                  INFO_CTRL_PANEL_DELETING_ATTRIBUTE.get(
                  attribute.getNameOrOID()),
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
    Entry schemaEntry = null;
    try
    {
      reader = new LDIFReader(new LDIFImportConfig(schemaFile));
      schemaEntry = reader.readEntry();

      Modification mod = new Modification(ModificationType.DELETE,
          Attributes.create(
              getSchemaFileAttributeName(schemaElement).toLowerCase(),
              getSchemaFileAttributeValue(schemaElement)));
      schemaEntry.applyModification(mod);
      LDIFWriter writer = new LDIFWriter(exportConfig);
      writer.writeEntry(schemaEntry);
      exportConfig.getWriter().newLine();
    }
    catch (Throwable t)
    {
    }
    finally
    {
      if (reader != null)
      {
        try
        {
          reader.close();
        }
        catch (Throwable t)
        {
        }
      }
      if (exportConfig != null)
      {
        try
        {
          exportConfig.close();
        }
        catch (Throwable t)
        {
        }
      }
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
        DirectoryServer.getEnvironmentConfig().getSchemaDirectory(false),
        schemaFile);
      if (f == null || ! f.exists() || f.isDirectory())
      {
        f = new File(
            DirectoryServer.getEnvironmentConfig().getSchemaDirectory(true),
            schemaFile);
      }
    }
    schemaFile = f.getAbsolutePath();
    return schemaFile;
  }

  /**
   * Returns the attribute name in the schema entry that corresponds to the
   * profived schema element.
   * @param element the schema element.
   * @return the attribute name in the schema entry that corresponds to the
   * profived schema element.
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
      getProgressDialog().appendProgressHtml(Utilities.applyFont(
          INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_DELETE_SCHEMA_ELEMENT_OFFLINE.get(
              schemaFile)+"<br><b>"+
          attrName+": "+attrValue+"</b><br><br>",
          ColorAndFontConstants.progressFont));
    }
    else
    {
      ArrayList<String> args = new ArrayList<String>();
      args.add(getCommandLinePath("ldapmodify"));
      args.add("-a");
      args.addAll(getObfuscatedCommandLineArguments(
          getConnectionCommandLineArguments(true, true)));
      args.add(getNoPropertiesFileArgument());
      StringBuilder sb = new StringBuilder();
      for (String arg : args)
      {
        sb.append(" "+CommandBuilder.escapeValue(arg));
      }
      sb.append("<br>");
      sb.append("dn: cn=schema<br>");
      sb.append("changetype: modify<br>");
      sb.append("delete: "+attrName+"<br>");
      sb.append(attrName+": "+attrValue);
      getProgressDialog().appendProgressHtml(Utilities.applyFont(
          INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_DELETE_SCHEMA_ELEMENT_ONLINE.get()+
          "<br><b>"+sb.toString()+"</b><br><br>",
          ColorAndFontConstants.progressFont));
    }
  }
}
