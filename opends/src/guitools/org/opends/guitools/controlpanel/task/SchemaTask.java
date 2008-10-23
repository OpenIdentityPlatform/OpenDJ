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
import java.util.Set;

import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.config.ConfigConstants;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.CommonSchemaElements;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.cli.CommandBuilder;

/**
 * An abstract class used to refactor some code between the different tasks
 * that update the schema.
 *
 */
public abstract class SchemaTask extends Task
{
  private Set<String> backendSet;
  /**
   * The file where the schema elements updated by this task is located.
   */
  protected String schemaFile;

  /**
   * Whether the schema file is defined or not.
   */
  protected boolean isSchemaFileDefined;

  /**
   * Constructor of the task.
   * @param info the control panel information.
   * @param dlg the progress dialog where the task progress will be displayed.
   */
  protected SchemaTask(ControlPanelInfo info, ProgressDialog dlg)
  {
    super(info, dlg);
    backendSet = new HashSet<String>();
    CommonSchemaElements element = getSchemaElement();
    schemaFile = element.getSchemaFile();
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
    isSchemaFileDefined = isSchemaFileDefined();
  }

  /**
   * Returns the schema element that this task is handling.
   * @return the schema element that this task is handling.
   */
  protected abstract CommonSchemaElements getSchemaElement();

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
    boolean canLaunch = true;
    return canLaunch;
  }

  /**
   * Returns whether the file defined in the schema element exists or not.
   * @return <CODE>true</CODE> if the schema file is defined and
   * <CODE>false</CODE> otherwise.
   */
  protected boolean isSchemaFileDefined()
  {
    boolean schemaDefined = false;
    LDIFReader reader = null;
    try
    {
      reader = new LDIFReader(new LDIFImportConfig(schemaFile));
      while (reader.readEntry() != null)
      {
        schemaDefined = true;
        break;
      }
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
    }
    return schemaDefined;
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
   * Update the schema.
   * @throws OpenDsException if an error occurs.
   */
  protected abstract void updateSchema() throws OpenDsException;

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
   * Returns the list of LDIF lines that are enough to create the entry
   * containing only the schema element associated with this task.
   * @return the list of LDIF lines that are enough to create the entry
   * containing only the schema element associated with this task.
   */
  protected ArrayList<String> getSchemaEntryLines()
  {
    ArrayList<String> lines = new ArrayList<String>();
    lines.add("dn: cn=schema");
    lines.add("objectClass: top");
    lines.add("objectClass: ldapSubentry");
    lines.add("objectClass: subschema");
    lines.add(getSchemaFileAttributeName()+": "+
        getSchemaFileAttributeValue());
    return lines;
  }

  /**
   * Returns the attribute in the schema file that contains the definition
   * of the schema element.
   * @return the attribute in the schema file that contains the definition
   * of the schema element.
   */
  protected abstract String getSchemaFileAttributeName();

  /**
   * Returns the value in the schema file that corresponds to the definition
   * of the schema element.
   * @return the value in the schema file that corresponds to the definition
   * of the schema element.
   */
  protected abstract String getSchemaFileAttributeValue();


  /**
   * Prints the equivalent command-line to add the schema element.
   *
   */
  protected void printEquivalentCommandToAdd()
  {
    if (!isServerRunning())
    {
      if (isSchemaFileDefined)
      {
        getProgressDialog().appendProgressHtml(Utilities.applyFont(
            INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_ADD_SCHEMA_ELEMENT_OFFLINE.get(
                schemaFile)+"<br><b>"+
            getSchemaFileAttributeName()+": "+getSchemaFileAttributeValue()+
            "</b><br><br>",
            ColorAndFontConstants.progressFont));
      }
      else
      {
        StringBuilder sb = new StringBuilder();
        for (String line : getSchemaEntryLines())
        {
          if (sb.length() > 0)
          {
            sb.append("<br>");
          }
          sb.append(line);
        }
        getProgressDialog().appendProgressHtml(Utilities.applyFont(
            INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_ADD_SCHEMA_ENTRY_OFFLINE.get(
                schemaFile)+"<br><b>"+sb+"</b><br><br>",
            ColorAndFontConstants.progressFont));
      }
    }
    else
    {
      ArrayList<String> args = new ArrayList<String>();
      args.add(getCommandLinePath("ldapmodify"));
      args.add("-a");
      args.addAll(getObfuscatedCommandLineArguments(
          getConnectionCommandLineArguments()));
      StringBuilder sb = new StringBuilder();
      for (String arg : args)
      {
        sb.append(" "+CommandBuilder.escapeValue(arg));
      }
      sb.append("<br>");
      sb.append("dn: cn=schema<br>");
      sb.append("changetype: modify<br>");
      sb.append("add: "+getSchemaFileAttributeName()+"<br>");
      sb.append(getSchemaFileAttributeName()+": "+
          getSchemaFileAttributeValue());
      getProgressDialog().appendProgressHtml(Utilities.applyFont(
          INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_ADD_SCHEMA_ELEMENT_ONLINE.get()+
          "<br><b>"+sb.toString()+"</b><br><br>",
          ColorAndFontConstants.progressFont));
    }
  }
}
