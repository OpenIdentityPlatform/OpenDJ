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
import java.util.Map;
import java.util.List;
import java.util.Set;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.ldap.InitialLdapContext;

import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.event.PrintStreamListener;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.guitools.controlpanel.util.ApplicationPrintStream;
import org.opends.guitools.controlpanel.util.ConfigReader;
import org.opends.guitools.controlpanel.util.ProcessReader;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.types.ByteString;
import org.opends.server.types.DN;
import org.opends.server.types.Schema;
import org.opends.server.util.Base64;
import org.opends.server.util.SetupUtils;
import org.opends.server.util.cli.CommandBuilder;

/**
 * The class used to define a number of common methods and mechanisms for the
 * tasks that are run in the Control Panel.
 *
 */
public abstract class Task
{
  private static String localHostName = null;
  static
  {
    // Do this since by default the hostname used by the connection is
    // 0.0.0.0, so try to figure the name of the host.  This is used to
    // display the equivalent command-line.
    try
    {
      localHostName = java.net.InetAddress.getLocalHost().getHostName();
    }
    catch (Throwable t)
    {
    }
  }
  /**
   * The different task types.
   *
   */
  public enum Type
  {
    /**
     * New Base DN creation.
     */
    NEW_BASEDN,
    /**
     * New index creation.
     */
    NEW_INDEX,
    /**
     * Modification of indexes.
     */
    MODIFY_INDEX,
    /**
     * Deletion of indexes.
     */
    DELETE_INDEX,
    /**
     * Creation of VLV indexes.
     */
    NEW_VLV_INDEX,
    /**
     * Modification of VLV indexes.
     */
    MODIFY_VLV_INDEX,
    /**
     * Deletion of VLV indexes.
     */
    DELETE_VLV_INDEX,
    /**
     * Import of an LDIF file.
     */
    IMPORT_LDIF,
    /**
     * Export of an LDIF file.
     */
    EXPORT_LDIF,
    /**
     * Backup.
     */
    BACKUP,
    /**
     * Restore.
     */
    RESTORE,
    /**
     * Verification of indexes.
     */
    VERIFY_INDEXES,
    /**
     * Rebuild of indexes.
     */
    REBUILD_INDEXES,
    /**
     * Enabling of Windows Service.
     */
    ENABLE_WINDOWS_SERVICE,
    /**
     * Disabling of Windows Service.
     */
    DISABLE_WINDOWS_SERVICE,
    /**
     * Starting the server.
     */
    START_SERVER,
    /**
     * Stopping the server.
     */
    STOP_SERVER,
    /**
     * Updating the java settings for the different command-lines.
     */
    JAVA_SETTINGS_UPDATE,
    /**
     * Creating a new attribute in the schema.
     */
    NEW_ATTRIBUTE,
    /**
     * Creating a new objectclass in the schema.
     */
    NEW_OBJECTCLASS,
    /**
     * Deleting an attribute in the schema.
     */
    DELETE_ATTRIBUTE,
    /**
     * Deleting an objectclass in the schema.
     */
    DELETE_OBJECTCLASS,
    /**
     * Modifying an entry.
     */
    MODIFY_ENTRY,
    /**
     * Creating an entry.
     */
    NEW_ENTRY,
    /**
     * Deleting an entry.
     */
    DELETE_ENTRY,
    /**
     * Deleting a base DN.
     */
    DELETE_BASEDN,
    /**
     * Deleting a backend.
     */
    DELETE_BACKEND,
    /**
     * Other task.
     */
    OTHER
  };

  /**
   * The state on which the task can be.
   */
  public enum State
  {
    /**
     * The task is not started.
     */
    NOT_STARTED,
    /**
     * The task is running.
     */
    RUNNING,
    /**
     * The task finished successfully.
     */
    FINISHED_SUCCESSFULLY,
    /**
     * The task finished with error.
     */
    FINISHED_WITH_ERROR
  };

  /**
   * Returns the names of the backends that are affected by the task.
   * @return the names of the backends that are affected by the task.
   */
  public abstract Set<String> getBackends();

  /**
   * The current state of the task.
   */
  protected State state = State.NOT_STARTED;
  /**
   * The return code of the task.
   */
  protected Integer returnCode;
  /**
   * The last exception encountered during the task execution.
   */
  protected Throwable lastException;
  /**
   * The progress logs of the task.  Note that the user of StringBuffer is not
   * a bug, because of the way the contents of logs is updated, using
   * StringBuffer instead of StringBuilder is required.
   */
  protected StringBuffer logs = new StringBuffer();
  /**
   * The error logs of the task.
   */
  protected StringBuilder errorLogs = new StringBuilder();
  /**
   * The standard output logs of the task.
   */
  protected StringBuilder outputLogs = new StringBuilder();
  /**
   * The print stream for the error logs.
   */
  protected ApplicationPrintStream errorPrintStream =
    new ApplicationPrintStream();
  /**
   * The print stream for the standard output logs.
   */
  protected ApplicationPrintStream outPrintStream =
    new ApplicationPrintStream();

  /**
   * The process (if any) that the task launched.  For instance if this is a
   * start server task, the process generated executing the start-ds
   * command-line.
   */
  protected Process process;
  private ControlPanelInfo info;

  private ProgressDialog progressDialog;

  private static int MAX_BINARY_LENGTH_TO_DISPLAY = 1024;

  /**
   * Constructor of the task.
   * @param info the control panel information.
   * @param progressDialog the progress dialog where the task progress will be
   * displayed.
   */
  protected Task(ControlPanelInfo info, ProgressDialog progressDialog)
  {
    this.info = info;
    this.progressDialog = progressDialog;
    outPrintStream.addListener(new PrintStreamListener()
    {
      /**
       * Add a new line to the logs.
       * @param msg the new line.
       */
      public void newLine(String msg)
      {
        outputLogs.append(msg+"\n");
        logs.append(msg+"\n");
      }
    });
    errorPrintStream.addListener(new PrintStreamListener()
    {
      /**
       * Add a new line to the error logs.
       * @param msg the new line.
       */
      public void newLine(String msg)
      {
        errorLogs.append(msg+"\n");
        logs.append(msg+"\n");
      }
    });
  }

  /**
   * Returns the ControlPanelInfo object.
   * @return the ControlPanelInfo object.
   */
  public ControlPanelInfo getInfo()
  {
    return info;
  }

  /**
   * Returns the logs of the task.
   * @return the logs of the task.
   */
  public String getLogs()
  {
    return logs.toString();
  }

  /**
   * Returns the error logs of the task.
   * @return the error logs of the task.
   */
  public String getErrorLogs()
  {
    return errorLogs.toString();
  }

  /**
   * Returns the output logs of the task.
   * @return the output logs of the task.
   */
  public String getOutputLogs()
  {
    return outputLogs.toString();
  }

  /**
   * Returns the state of the task.
   * @return the state of the task.
   */
  public State getState()
  {
    return state;
  }

  /**
   * Returns last exception encountered during the task execution.
   * Returns <CODE>null</CODE> if no exception was found.
   * @return last exception encountered during the task execution.
   */
  public Throwable getLastException()
  {
    return lastException;
  }

  /**
   * Returns the return code (this makes sense when the task launches a
   * command-line, it will return the error code returned by the command-line).
   * @return the return code.
   */
  public Integer getReturnCode()
  {
    return returnCode;
  }

  /**
   * Returns the process that the task launched.
   * Returns <CODE>null</CODE> if not process was launched.
   * @return the process that the task launched.
   */
  public Process getProcess()
  {
    return process;
  }

  /**
   * Returns the progress dialog.
   * @return the progress dialog.
   */
  protected ProgressDialog getProgressDialog()
  {
    return progressDialog;
  }

  /**
   * Tells whether a new server descriptor should be regenerated when the task
   * is over.  If the task has an influence in the configuration or state of
   * the server (for instance the creation of a base DN) this method should
   * return <CODE>true</CODE> so that the configuration will be re-read and
   * all the ConfigChangeListeners will receive a notification with the new
   * configuration.
   * @return <CODE>true</CODE> if a new server descriptor must be regenerated
   * when the task is over and <CODE>false</CODE> otherwise.
   */
  public boolean regenerateDescriptor()
  {
    return true;
  }

  /**
   * Method that is called when everything is finished after updating the
   * progress dialog.  It is called from the event thread.
   */
  public void postOperation()
  {
  }

  /**
   * The description of the task.  It is used in both the incompatibility
   * messages and in the warning message displayed when the user wants to
   * quit and there are tasks running.
   * @return the description of the task.
   */
  public abstract Message getTaskDescription();

  /**
   * Returns a String representation of a value.  In general this is called
   * to display the command-line equivalent when we do a modification in an
   * entry.  But since some attributes must be obfuscated (like the user
   * password) we pass through this method.
   * @param attrName the attribute name.
   * @param o the attribute value.
   * @return the obfuscated String representing the attribute value to be
   * displayed in the logs of the user.
   */
  protected String obfuscateAttributeStringValue(String attrName, Object o)
  {
    if (Utilities.mustObfuscate(attrName,
        getInfo().getServerDescriptor().getSchema()))
    {
      return Utilities.OBFUSCATED_VALUE;
    }
    else
    {
      if (o instanceof byte[])
      {
        byte[] bytes = (byte[])o;
        if (displayBase64(attrName))
        {
          if (bytes.length > MAX_BINARY_LENGTH_TO_DISPLAY)
          {
            return INFO_CTRL_PANEL_VALUE_IN_BASE64.get().toString();
          }
          else
          {
            return Base64.encode(bytes);
          }
        }
        else
        {
          if (bytes.length > MAX_BINARY_LENGTH_TO_DISPLAY)
          {
            return INFO_CTRL_PANEL_BINARY_VALUE.get().toString();
          }
          else
          {
            // Get the String value
            ByteString v = ByteString.wrap(bytes);
            return v.toString();
          }
        }
      }
      else
      {
        return String.valueOf(o);
      }
    }
  }

  /**
   * Obfuscates (if required) the attribute value in an LDIF line.
   * @param line the line of the LDIF file that must be treated.
   * @return the line obfuscated.
   */
  protected String obfuscateLDIFLine(String line)
  {
    String returnValue;
    int index = line.indexOf(":");
    if (index != -1)
    {
      String attrName = line.substring(0, index).trim();

      if (Utilities.mustObfuscate(attrName,
          getInfo().getServerDescriptor().getSchema()))
      {
        returnValue = attrName + ": " +Utilities.OBFUSCATED_VALUE;
      }
      else
      {
        returnValue = line;
      }
    }
    else
    {
      returnValue = line;
    }
    return returnValue;
  }

  /**
   * Executes a command-line synchrounously.
   * @param commandLineName the command line full path.
   * @param args the arguments for the command-line.
   * @return the error code returned by the command-line.
   */
  protected int executeCommandLine(String commandLineName, String[] args)
  {
    returnCode = -1;
    String[] cmd = new String[args.length + 1];
    cmd[0] = commandLineName;
    for (int i=0; i<args.length; i++)
    {
      cmd[i+1] = args[i];
    }

    ProcessBuilder pb = new ProcessBuilder(cmd);
    // Use the java args in the script.
    Map<String, String> env = pb.environment();
    //env.put(SetupUtils.OPENDS_JAVA_ARGS, "");
    env.remove(SetupUtils.OPENDS_JAVA_ARGS);
    env.remove("CLASSPATH");
    ProcessReader outReader = null;
    ProcessReader errReader = null;
    try {
      process = pb.start();

      outReader = new ProcessReader(process, outPrintStream, false);
      errReader = new ProcessReader(process, errorPrintStream, true);

      outReader.startReading();
      errReader.startReading();

      returnCode = process.waitFor();
    } catch (Throwable t)
    {
      lastException = t;
    }
    finally
    {
      if (outReader != null)
      {
        outReader.interrupt();
      }
      if (errReader != null)
      {
        errReader.interrupt();
      }
    }
    return returnCode;
  }

  /**
   * Informs of whether the task to be launched can be launched or not. Every
   * task must implement this method so that we avoid launching in paralel two
   * tasks that are not compatible.  Note that in general if the current task
   * is not running this method will return <CODE>true</CODE>.
   *
   * @param taskToBeLaunched the Task that we are trying to launch.
   * @param incompatibilityReasons the list of incompatibility reasons that
   * must be updated.
   * @return <CODE>true</CODE> if the task that we are trying to launch can be
   * launched in paralel with this task and <CODE>false</CODE> otherwise.
   */
  public abstract boolean canLaunch(Task taskToBeLaunched,
      Collection<Message> incompatibilityReasons);

  /**
   * Execute the task.  This method is synchronous.
   *
   */
  public abstract void runTask();

  /**
   * Returns the type of the task.
   * @return the type of the task.
   */
  public abstract Type getType();


  /**
   * Returns the binary/script directory.
   * @return the binary/script directory.
   */
  protected String getBinaryDir()
  {
    if (Utilities.isWindows())
    {
      return getInfo().getServerDescriptor().getInstallPath() +
      File.separator + "bat" + File.separator;
    }
    else
    {
      return getInfo().getServerDescriptor().getInstallPath() +
      File.separator + "bin" + File.separator;
    }
  }

  /**
   * Returns the full path of the command-line associated with this task or
   * <CODE>null</CODE> if there is not a command-line (or a single command-line)
   * associated with the task.
   * @return the full path of the command-line associated with this task.
   */
  protected abstract String getCommandLinePath();

  /**
   * Returns the full path of the command-line for a given script name.
   * @param scriptBasicName the script basic name (with no extension).
   * @return the full path of the command-line for a given script name.
   */
  protected String getCommandLinePath(String scriptBasicName)
  {
    String cmdLineName;
    if (Utilities.isWindows())
    {
      cmdLineName = getBinaryDir()+scriptBasicName+".bat";
    }
    else
    {
      cmdLineName = getBinaryDir()+scriptBasicName;
    }
    return cmdLineName;
  }

  /**
   * Returns the list of command-line arguments.
   * @return the list of command-line arguments.
   */
  protected abstract List<String> getCommandLineArguments();



  /**
   * Returns the list of obfuscated command-line arguments.  This is called
   * basically to display the equivalent command-line to the user.
   * @param clearArgs the arguments in clear.
   * @return the list of obfuscated command-line arguments.
   */
  protected List<String> getObfuscatedCommandLineArguments(
      List<String> clearArgs)
  {
    String[] toObfuscate = {"--bindPassword", "--currentPassword",
        "--newPassword"};
    ArrayList<String> args = new ArrayList<String>(clearArgs);
    for (int i=1; i<args.size(); i++)
    {
      for (String argName : toObfuscate)
      {
        if (args.get(i-1).equalsIgnoreCase(argName))
        {
          args.set(i, Utilities.OBFUSCATED_VALUE);
          break;
        }
      }
    }
    return args;
  }

  /**
   * Returns the command-line arguments that correspond to the configuration.
   * This method is called to remove them when we display the equivalent
   * command-line.  In some cases we run the methods of the command-line
   * directly (on this JVM) instead of launching the script in another process.
   * When we call this methods we must add these arguments, but they are not
   * to be included as arguments of the command-line (when is launched as a
   * script).
   * @return the command-line arguments that correspond to the configuration.
   */
  protected ArrayList<String> getConfigCommandLineArguments()
  {
    ArrayList<String> args = new ArrayList<String>();
    args.add("--configClass");
    args.add(org.opends.server.extensions.ConfigFileHandler.class.getName());
    args.add("--configFile");
    args.add(ConfigReader.configFile);
    return args;
  }

  /**
   * Returns the list of arguments related to the connection (host, port, bind
   * DN, etc.).
   * @return the list of arguments related to the connection.
   */
  protected List<String> getConnectionCommandLineArguments()
  {
    return getConnectionCommandLineArguments(true, false);
  }

  /**
   * Returns the list of arguments related to the connection (host, port, bind
   * DN, etc.).
   * @param useAdminConnector use the administration connector to generate
   * the command line.
   * @param addConnectionTypeParameters add the connection type parameters
   * (--useSSL or --useStartTLS parameters: for ldapadd, ldapdelete, etc.).
   * @return the list of arguments related to the connection.
   */
  protected List<String> getConnectionCommandLineArguments(
      boolean useAdminConnector, boolean addConnectionTypeParameters)
  {
    ArrayList<String> args = new ArrayList<String>();
    InitialLdapContext ctx;

    if (useAdminConnector)
    {
      ctx = getInfo().getDirContext();
    }
    else
    {
      ctx = getInfo().getUserDataDirContext();
    }
    if (isServerRunning() && (ctx != null))
    {
      String hostName = localHostName;
      if (hostName == null)
      {
        hostName = ConnectionUtils.getHostName(ctx);
      }
      int port = ConnectionUtils.getPort(ctx);
      boolean isSSL = ConnectionUtils.isSSL(ctx);
      boolean isStartTLS = ConnectionUtils.isStartTLS(ctx);
      String bindDN = ConnectionUtils.getBindDN(ctx);
      String bindPwd = ConnectionUtils.getBindPassword(ctx);
      args.add("--hostname");
      args.add(hostName);
      args.add("--port");
      args.add(String.valueOf(port));
      args.add("--bindDN");
      args.add(bindDN);
      args.add("--bindPassword");
      args.add(bindPwd);
      if (isSSL || isStartTLS)
      {
        args.add("--trustAll");
      }
      if (isSSL && addConnectionTypeParameters)
      {
        args.add("--useSSL");
      }
      else if (isStartTLS && addConnectionTypeParameters)
      {
        args.add("--useStartTLS");
      }
    }
    return args;
  }

  /**
   * Returns the noPropertiesFile argument.
   * @return the noPropertiesFile argument.
   */
  protected String getNoPropertiesFileArgument()
  {
    return "--noPropertiesFile";
  }

  /**
   * Returns the command-line to be displayed (when we display the equivalent
   * command-line).
   * @return the command-line to be displayed.
   */
  public String getCommandLineToDisplay()
  {
    String cmdLineName = getCommandLinePath();
    if (cmdLineName != null)
    {
      StringBuilder sb = new StringBuilder();
      sb.append(cmdLineName);
      Collection<String> args =
        getObfuscatedCommandLineArguments(getCommandLineArguments());
      args.removeAll(getConfigCommandLineArguments());
      for (String arg : args)
      {
        sb.append(" "+CommandBuilder.escapeValue(arg));
      }
      return sb.toString();
    }
    else
    {
      return null;
    }
  }

  /**
   * Commodity method to know if the server is running or not.
   * @return <CODE>true</CODE> if the server is running and <CODE>false</CODE>
   * otherwise.
   */
  protected boolean isServerRunning()
  {
    return getInfo().getServerDescriptor().getStatus() ==
      ServerDescriptor.ServerStatus.STARTED;
  }

  /**
   *
   * Returns the print stream for the error logs.
   * @return the print stream for the error logs.
   */
  public ApplicationPrintStream getErrorPrintStream()
  {
    return errorPrintStream;
  }

  /**
  *
  * Returns the print stream for the output logs.
  * @return the print stream for the output logs.
  */
  public ApplicationPrintStream getOutPrintStream()
  {
    return outPrintStream;
  }

  /**
   * Prints the equivalent modify command line in the progress dialog.
   * @param dn the dn of the modified entry.
   * @param mods the modifications.
   * @param useAdminCtx use the administration connector.
   */
  protected void printEquivalentCommandToModify(DN dn,
      Collection<ModificationItem> mods, boolean useAdminCtx)
  {
    printEquivalentCommandToModify(dn.toString(), mods, useAdminCtx);
  }

  /**
   * Prints the equivalent modify command line in the progress dialog.
   * @param dn the dn of the modified entry.
   * @param mods the modifications.
   * @param useAdminCtx use the administration connector.
   */
  protected void printEquivalentCommandToModify(String dn,
      Collection<ModificationItem> mods, boolean useAdminCtx)
  {
    ArrayList<String> args = new ArrayList<String>();
    args.add(getCommandLinePath("ldapmodify"));
    args.addAll(getObfuscatedCommandLineArguments(
        getConnectionCommandLineArguments(useAdminCtx, true)));
    args.add(getNoPropertiesFileArgument());
    StringBuilder sb = new StringBuilder();
    for (String arg : args)
    {
      sb.append(" "+CommandBuilder.escapeValue(arg));
    }
    sb.append("<br>");
    sb.append("dn: "+dn);
    boolean firstChangeType = true;
    for (ModificationItem mod : mods)
    {
      if (firstChangeType)
      {
        sb.append("<br>");
      }
      else
      {
        sb.append("-<br>");
      }
      firstChangeType = false;
      sb.append("changetype: modify<br>");
      Attribute attr = mod.getAttribute();
      String attrName = attr.getID();
      if (mod.getModificationOp() == DirContext.ADD_ATTRIBUTE)
      {
        sb.append("add: "+attrName+"<br>");
      }
      else if (mod.getModificationOp() == DirContext.REPLACE_ATTRIBUTE)
      {
        sb.append("replace: "+attrName+"<br>");
      }
      else
      {
        sb.append("delete: "+attrName+"<br>");
      }
      for (int i=0; i<attr.size(); i++)
      {
        try
        {
          Object o = attr.get(i);
          // We are systematically adding the values in binary mode.
          // Use the attribute names to figure out the value to be displayed.
          if (displayBase64(attr.getID()))
          {
            sb.append(attrName+":: ");
          }
          else
          {
            sb.append(attrName+": ");
          }
          sb.append(obfuscateAttributeStringValue(attrName, o));
          sb.append("<br>");
        }
        catch (NamingException ne)
        {
          // Bug
          throw new IllegalStateException(
              "Unexpected error parsing modifications: "+ne, ne);
        }
      }
    }
    getProgressDialog().appendProgressHtml(Utilities.applyFont(
        INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_MODIFY.get().toString()+"<br><b>"+
        sb.toString()+"</b><br><br>",
        ColorAndFontConstants.progressFont));
  }

  /**
   * Tells whether the provided attribute's values must be displayed using
   * base 64 when displaying the equivalent command-line or not.
   * @param attrName the attribute name.
   * @return <CODE>true</CODE> if the attribute must be displayed using base 64
   * and <CODE>false</CODE> otherwise.
   */
  protected boolean displayBase64(String attrName)
  {
    Schema schema = null;
    if (getInfo() != null)
    {
      schema = getInfo().getServerDescriptor().getSchema();
    }
    return Utilities.hasBinarySyntax(attrName, schema);
  }

  /**
   * Prints the equivalent rename command line in the progress dialog.
   * @param oldDN the old DN of the entry.
   * @param newDN the new DN of the entry.
   * @param useAdminCtx use the administration connector.
   */
  protected void printEquivalentRenameCommand(DN oldDN, DN newDN,
      boolean useAdminCtx)
  {
    ArrayList<String> args = new ArrayList<String>();
    args.add(getCommandLinePath("ldapmodify"));
    args.addAll(getObfuscatedCommandLineArguments(
        getConnectionCommandLineArguments(useAdminCtx, true)));
    args.add(getNoPropertiesFileArgument());
    StringBuilder sb = new StringBuilder();
    for (String arg : args)
    {
      sb.append(" "+CommandBuilder.escapeValue(arg));
    }
    sb.append("<br>");
    sb.append("dn: "+oldDN);
    sb.append("<br>");
    sb.append("changetype: moddn<br>");
    sb.append("newrdn: "+newDN.getRDN()+"<br>");
    sb.append("deleteoldrdn: 1");

    getProgressDialog().appendProgressHtml(Utilities.applyFont(
        INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_RENAME.get().toString()+"<br><b>"+
        sb.toString()+"</b><br><br>",
        ColorAndFontConstants.progressFont));
  }

  /**
   * Returns the incompatible message between two tasks.
   * @param taskRunning the task that is running.
   * @param taskToBeLaunched the task that we are trying to launch.
   * @return the incompatible message between two tasks.
   */
  protected Message getIncompatibilityMessage(Task taskRunning,
      Task taskToBeLaunched)
  {
    return INFO_CTRL_PANEL_INCOMPATIBLE_TASKS.get(
        taskRunning.getTaskDescription().toString(),
        taskToBeLaunched.getTaskDescription().toString());
  }
}
