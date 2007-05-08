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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.statuspanel;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.table.TableModel;

import org.opends.quicksetup.CurrentInstallStatus;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.util.Utils;

import org.opends.server.core.DirectoryServer;

import org.opends.statuspanel.i18n.ResourceProvider;
import org.opends.statuspanel.ui.DatabasesTableModel;
import org.opends.statuspanel.ui.ListenersTableModel;

import static org.opends.server.tools.ToolConstants.*;

/**
 * The class used to provide some CLI interface to display status.
 *
 * This class basically is in charge of parsing the data provided by the user
 * in the command line.
 *
 */
class StatusCli
{
  private static String LINE_SEPARATOR = System.getProperty("line.separator");

  private String[] args;

  private boolean displayMustAuthenticateLegend;
  private boolean displayMustStartLegend;

  /**
   * Return code: Uninstall successful.
   */
  static int SUCCESSFUL = 0;
  /**
   * Return code: User provided invalid data.
   */
  static int USER_DATA_ERROR = 1;
  /**
   * Return code: Bug.
   */
  static int BUG = 2;

  /**
   * The main method which is called by the status command lines.
   * @param args the arguments passed by the status command lines.
   */
  public static void main(String[] args)
  {
    StatusCli cli = new StatusCli(args);
    System.exit(cli.run());
  }

  /**
   * The constructor for this object.
   * @param args the arguments of the status command line.
   */
  StatusCli(String[] args)
  {
    /* Ignore the first 4 arguments */
    if ((args != null) && (args.length >= 4))
    {
      this.args = new String[args.length - 4];
      for (int i=0; i<this.args.length; i++)
      {
        this.args[i] = args[i+4];
      }
    }
    else
    {
      this.args = args;
    }
    DirectoryServer.bootstrapClient();
  }

  /**
   * Parses the user data and displays usage if something is missing and the
   * status otherwise.
   *
   * @return the return code (SUCCESSFUL, USER_DATA_ERROR or BUG.
   */
  int run()
  {
    int returnValue = SUCCESSFUL;

    ArrayList<String> errors = new ArrayList<String>();

    boolean printUsage = false;

    String directoryManagerPwd = null;
    String directoryManagerPwdFile = null;
    String directoryManagerDn = null;

    for (int i=0; i<args.length; i++)
    {
      if (args[i].equalsIgnoreCase("-H") ||
          args[i].equalsIgnoreCase("--help") ||
          args[i].equalsIgnoreCase("-?"))
      {
        printUsage = true;
      }
      else if (args[i].equalsIgnoreCase("-D") ||
          args[i].equalsIgnoreCase("--bindDN"))
      {
        if (i+1 >= args.length)
        {
          errors.add(getMsg("cli-status-root-user-dn-not-provided", true));
        }
        else
        {
          if (args[i+1].indexOf("-") == 0)
          {
            errors.add(getMsg("cli-status-root-user-dn-not-provided", true));
          }
          else
          {
            directoryManagerDn = args[i+1];
            i++;
          }
        }
      }
      else if (args[i].equals("-" + OPTION_SHORT_BINDPWD) ||
          args[i].equalsIgnoreCase("--" + OPTION_LONG_BINDPWD))
      {
        if (i+1 >= args.length)
        {
          errors.add(getMsg("cli-status-root-user-pwd-not-provided", true));
        }
        else
        {
          if (args[i+1].indexOf("-") == 0)
          {
            errors.add(getMsg("cli-status-root-user-pwd-not-provided", true));
          }
          else
          {
            directoryManagerPwd = args[i+1];
            i++;
          }
        }
      }
      else if (args[i].equals("-j") ||
          args[i].equalsIgnoreCase("--bindPasswordFile"))
      {
        if (i+1 >= args.length)
        {
          errors.add(getMsg("cli-status-root-user-pwd-file-not-provided",
              true));
        }
        else
        {
          if (args[i+1].indexOf("-") == 0)
          {
            errors.add(getMsg("cli-status-root-user-pwd-file-not-provided",
                true));
          }
          else
          {
            directoryManagerPwdFile = args[i+1];
            i++;
          }
        }
      }
      else
      {
        String[] arg = {args[i]};
        errors.add(getMsg("cli-status-unknown-argument", arg, true));
      }
    }

    if ((directoryManagerPwdFile != null) && (directoryManagerPwd != null))
    {
      errors.add(getMsg("cli-status-pwd-and-pwd-file-provided", true));
    }
    else
    {
      if (directoryManagerPwdFile != null)
      {
        directoryManagerPwd = readPwdFromFile(directoryManagerPwdFile);
        if (directoryManagerPwd == null)
        {
          String[] arg = {directoryManagerPwdFile};
          errors.add(getMsg("cli-status-error-reading-pwd-file", arg, true));
        }
      }
    }

    if (printUsage)
    {
      printUsage(System.out);
    }
    else if (errors.size() > 0)
    {
      System.err.println(Utils.getStringFromCollection(errors,
          LINE_SEPARATOR+LINE_SEPARATOR));
      System.err.println();
      printUsage(System.err);
      returnValue = USER_DATA_ERROR;
    }
    else
    {
      boolean isServerRunning = CurrentInstallStatus.isServerRunning();
      /* This is required to retrieve the ldap url to be used by the
       * ConfigFromLDAP class.
       */
      ConfigFromFile offLineConf = new ConfigFromFile();
      offLineConf.readConfiguration();

      ServerStatusDescriptor desc = createServerStatusDescriptor(
      directoryManagerDn, directoryManagerPwd);
      if (isServerRunning)
      {
        String ldapUrl = offLineConf.getLDAPURL();
        if (directoryManagerDn == null)
        {
          directoryManagerDn = "";
        }
        if (directoryManagerPwd == null)
        {
          directoryManagerPwd = "";
        }
        ConfigFromLDAP onLineConf = new ConfigFromLDAP();
        onLineConf.setConnectionInfo(ldapUrl, directoryManagerDn,
            directoryManagerPwd);
        onLineConf.readConfiguration();
        updateDescriptorWithOnLineInfo(desc, onLineConf);
      }
      else
      {
        updateDescriptorWithOffLineInfo(desc, offLineConf);
      }

      writeStatus(desc);
    }

    return returnValue;
  }

  /**
   * Returns the password stored in a file.  Returns <CODE>null</CODE> if no
   * password is found.
   * @param path the path of the file containing the password.
   * @return the password stored in a file.  Returns <CODE>null</CODE> if no
   * password is found.
   */
  private String readPwdFromFile(String path)
  {
    String pwd = null;
    BufferedReader reader = null;
    try
    {
      reader = new BufferedReader(new FileReader(path));
      pwd = reader.readLine();
    }
    catch (Exception e)
    {
    }
    finally
    {
      try
      {
        if (reader != null)
        {
          reader.close();
        }
      } catch (Exception e) {}
    }
    return pwd;
  }

  /**
   * The following three methods are just commodity methods to get localized
   * messages.
   */
  private String getMsg(String key, boolean wrap)
  {
    String t = getI18n().getMsg(key);
    if (wrap)
    {
      t= wrap(t);
    }
    return t;
  }

  private String getMsg(String key, String[] args, boolean wrap)
  {
    String t = getI18n().getMsg(key, args);
    if (wrap)
    {
      t= wrap(t);
    }
    return t;
  }

  private static ResourceProvider getI18n()
  {
    return ResourceProvider.getInstance();
  }

  private void printUsage(PrintStream stream)
  {
    String arg;
    if (Utils.isWindows())
    {
      arg = Installation.WINDOWS_STATUSCLI_FILE_NAME;
    } else
    {
      arg = Installation.UNIX_STATUSCLI_FILE_NAME;
    }
    /*
     * This is required because the usage message contains '{' characters that
     * mess up the MessageFormat.format method.
     */
    String msg = getMsg("status-cli-usage", true);
    msg = msg.replace("{0}", arg);
    stream.println(msg);
  }

  private ServerStatusDescriptor createServerStatusDescriptor(String dn,
      String pwd)
  {
    ServerStatusDescriptor desc = new ServerStatusDescriptor();
    desc.setAuthenticated((dn != null) && (pwd != null));

    if (CurrentInstallStatus.isServerRunning())
    {
      desc.setStatus(ServerStatusDescriptor.ServerStatus.STARTED);
    }
    else
    {
      desc.setStatus(ServerStatusDescriptor.ServerStatus.STOPPED);
    }

    desc.setInstallPath(new File(Utils.getInstallPathFromClasspath()));

    desc.setOpenDSVersion(
        org.opends.server.util.DynamicConstants.FULL_VERSION_STRING);

    return desc;
  }

  /**
   * Updates the ServerStatusDescriptor object using the information in the
   * config.ldif file (we use a ConfigFromFile object to do this).
   * @param desc the ServerStatusDescriptor object to be updated.
   * @param offLineConf the ConfigFromFile object to be used.
   */
  private void updateDescriptorWithOffLineInfo(ServerStatusDescriptor desc,
      ConfigFromFile offLineConf)
  {
    desc.setAdministrativeUsers(offLineConf.getAdministrativeUsers());
    desc.setDatabases(offLineConf.getDatabases());
    desc.setListeners(offLineConf.getListeners());
    desc.setErrorMessage(offLineConf.getErrorMessage());
    desc.setOpenConnections(-1);
    desc.setJavaVersion(null);
  }

  /**
   * Updates the ServerStatusDescriptor object using the LDAP protocol (we use a
   * ConfigFromLDAP object to do this).
   * @param desc the ServerStatusDescriptor object to be updated.
   * @param onLineConf the ConfigFromLDAP object to be used.
   */
  private void updateDescriptorWithOnLineInfo(ServerStatusDescriptor desc,
      ConfigFromLDAP onLineConf)
  {
    desc.setAdministrativeUsers(onLineConf.getAdministrativeUsers());
    desc.setDatabases(onLineConf.getDatabases());
    desc.setListeners(onLineConf.getListeners());
    desc.setErrorMessage(onLineConf.getErrorMessage());
    desc.setJavaVersion(onLineConf.getJavaVersion());
    desc.setOpenConnections(onLineConf.getOpenConnections());
  }

  private void writeStatus(ServerStatusDescriptor desc)
  {
    String[] labels =
      {
        getMsg("server-status-label", false),
        getMsg("connections-label", false),
        getMsg("administrative-users-label", false),
        getMsg("installation-path-label", false),
        getMsg("opends-version-label", false),
        getMsg("java-version-label", false)
      };
    int labelWidth = 0;
    for (int i=0; i<labels.length; i++)
    {
      labelWidth = Math.max(labelWidth, labels[i].length());
    }
    System.out.println();
    String title = getMsg("server-status-title", false);
    System.out.println(centerTitle(title));
    writeStatusContents(desc, labelWidth);
    writeCurrentConnectionContents(desc, labelWidth);
    System.out.println();

    title = getMsg("server-details-title", false);
    System.out.println(centerTitle(title));
    writeAdministrativeUserContents(desc, labelWidth);
    writeInstallPathContents(desc, labelWidth);
    writeVersionContents(desc, labelWidth);
    writeJavaVersionContents(desc, labelWidth);
    System.out.println();

    writeListenerContents(desc);
    System.out.println();

    writeDatabaseContents(desc);

    writeErrorContents(desc);

    if (displayMustStartLegend)
    {
      System.out.println();
      System.out.println(getMsg("not-available-server-down-cli-legend", true));
    }
    else if (displayMustAuthenticateLegend)
    {
      System.out.println();
      System.out.println(
          getMsg("not-available-authentication-required-cli-legend", true));
    }
    System.out.println();
  }

  /**
   * Writes the status contents displaying with what is specified in the
   * provided ServerStatusDescriptor object.
   * @param desc the ServerStatusDescriptor object.
   */
  private void writeStatusContents(ServerStatusDescriptor desc,
      int maxLabelWidth)
  {
    String status;
    switch (desc.getStatus())
    {
    case STARTED:
      status = getMsg("server-started-label", false);
      break;

    case STOPPED:
      status = getMsg("server-stopped-label", false);
      break;

    case STARTING:
      status = getMsg("server-starting-label", false);
      break;

    case STOPPING:
      status = getMsg("server-stopping-label", false);
      break;

    case UNKNOWN:
      status = getMsg("server-unknown-status-label", false);
      break;

    default:
      throw new IllegalStateException("Unknown status: "+desc.getStatus());
    }
    writeLabelValue(getMsg("server-status-label", false), status,
        maxLabelWidth);
  }

  /**
   * Writes the current connection contents displaying with what is specified
   * in the provided ServerStatusDescriptor object.
   * @param desc the ServerStatusDescriptor object.
   */
  private void writeCurrentConnectionContents(ServerStatusDescriptor desc,
      int maxLabelWidth)
  {
    String text;
    if (desc.getStatus() == ServerStatusDescriptor.ServerStatus.STARTED)
    {
      int nConn = desc.getOpenConnections();
      if (nConn >= 0)
      {
        text = String.valueOf(nConn);
      }
      else
      {
        if (!desc.isAuthenticated())
        {
          text = getNotAvailableBecauseAuthenticationIsRequiredText();
        }
        else
        {
          text = getNotAvailableText();
        }
      }
    }
    else
    {
      text = getNotAvailableBecauseServerIsDownText();
    }

    writeLabelValue(getMsg("connections-label", false), text, maxLabelWidth);
  }

  /**
   * Writes the administrative user contents displaying with what is specified
   * in the provided ServerStatusDescriptor object.
   * @param desc the ServerStatusDescriptor object.
   */
  private void writeAdministrativeUserContents(ServerStatusDescriptor desc,
      int maxLabelWidth)
  {
    Set<String> administrators = desc.getAdministrativeUsers();
    String text;
    if (administrators.size() > 0)
    {
      TreeSet<String> ordered = new TreeSet<String>();
      ordered.addAll(administrators);

      String first = ordered.iterator().next();
      writeLabelValue(getMsg("administrative-users-label", false), first,
          maxLabelWidth);

      Iterator<String> it = ordered.iterator();
      // First one already printed
      it.next();
      while (it.hasNext())
      {
        writeLabelValue(getMsg("administrative-users-label", false), it.next(),
            maxLabelWidth);
      }
    }
    else
    {
      if (desc.getStatus() == ServerStatusDescriptor.ServerStatus.STARTED)
      {
        if (!desc.isAuthenticated())
        {
          text = getNotAvailableBecauseAuthenticationIsRequiredText();
        }
        else
        {
          text = getNotAvailableText();
        }
      }
      else
      {
        text = getNotAvailableText();
      }
      writeLabelValue(getMsg("administrative-users-label", false), text,
          maxLabelWidth);
    }
  }

  /**
   * Writes the install path contents displaying with what is specified in the
   * provided ServerStatusDescriptor object.
   * @param desc the ServerStatusDescriptor object.
   */
  private void writeInstallPathContents(ServerStatusDescriptor desc,
      int maxLabelWidth)
  {
    File path = desc.getInstallPath();
    writeLabelValue(getMsg("installation-path-label", false), path.toString(),
        maxLabelWidth);
  }

  /**
   * Updates the server version contents displaying with what is specified in
   * the provided ServerStatusDescriptor object.
   * This method must be called from the event thread.
   * @param desc the ServerStatusDescriptor object.
   */
  private void writeVersionContents(ServerStatusDescriptor desc,
      int maxLabelWidth)
  {
    String openDSVersion = desc.getOpenDSVersion();
    writeLabelValue(getMsg("opends-version-label", false), openDSVersion,
        maxLabelWidth);
  }

  /**
   * Updates the java version contents displaying with what is specified in
   * the provided ServerStatusDescriptor object.
   * This method must be called from the event thread.
   * @param desc the ServerStatusDescriptor object.
   */
  private void writeJavaVersionContents(ServerStatusDescriptor desc,
      int maxLabelWidth)
  {
    String text;
    if (desc.getStatus() == ServerStatusDescriptor.ServerStatus.STARTED)
    {
      text = desc.getJavaVersion();
      if (text == null)
      {
        if (!desc.isAuthenticated())
        {
          text = getNotAvailableBecauseAuthenticationIsRequiredText();
        }
        else
        {
          text = getNotAvailableText();
        }
      }
    }
    else
    {
      text = getNotAvailableBecauseServerIsDownText();
    }
    writeLabelValue(getMsg("java-version-label", false), text, maxLabelWidth);
  }

  /**
   * Writes the listeners contents displaying with what is specified in
   * the provided ServerStatusDescriptor object.
   * @param desc the ServerStatusDescriptor object.
   */
  private void writeListenerContents(ServerStatusDescriptor desc)
  {
    String title = getMsg("listeners-title", false);
    System.out.println(centerTitle(title));

    Set<ListenerDescriptor> listeners = desc.getListeners();

    if (listeners.size() == 0)
    {
      if (desc.getStatus() == ServerStatusDescriptor.ServerStatus.STARTED)
      {
        if (!desc.isAuthenticated())
        {
          System.out.println(
              getMsg("not-available-authentication-required-cli-label", true));
        }
        else
        {
          System.out.println(getMsg("no-listeners-found", true));
        }
      }
      else
      {
        System.out.println(getMsg("no-listeners-found", true));
      }
    }
    else
    {
      ListenersTableModel listenersTableModel = new ListenersTableModel();
      listenersTableModel.setData(desc.getListeners());
      writeTableModel(listenersTableModel, desc);
    }
  }

  /**
   * Writes the databases contents displaying with what is specified in
   * the provided ServerStatusDescriptor object.
   * @param desc the ServerStatusDescriptor object.
   */
  private void writeDatabaseContents(ServerStatusDescriptor desc)
  {
    String title = getMsg("databases-title", false);
    System.out.println(centerTitle(title));

    Set<DatabaseDescriptor> databases = desc.getDatabases();

    if (databases.size() == 0)
    {
      if (desc.getStatus() == ServerStatusDescriptor.ServerStatus.STARTED)
      {
        if (!desc.isAuthenticated())
        {
          System.out.println(
              getMsg("not-available-authentication-required-cli-label", true));
        }
        else
        {
          System.out.println(getMsg("no-dbs-found", true));
        }
      }
      else
      {
        System.out.println(getMsg("no-dbs-found", true));
      }
    }
    else
    {
      DatabasesTableModel databasesTableModel = new DatabasesTableModel(true);
      Set<BaseDNDescriptor> replicas = new HashSet<BaseDNDescriptor>();
      Set<DatabaseDescriptor> dbs = desc.getDatabases();
      for (DatabaseDescriptor db: dbs)
      {
        replicas.addAll(db.getBaseDns());
      }
      databasesTableModel.setData(replicas);

      writeDatabasesTableModel(databasesTableModel, desc);
    }
  }

  /**
   * Writes the error label contents displaying with what is specified in
   * the provided ServerStatusDescriptor object.
   * @param desc the ServerStatusDescriptor object.
   */
  private void writeErrorContents(ServerStatusDescriptor desc)
  {
    String errorMsg = desc.getErrorMessage();
    if (errorMsg != null)
    {
      System.out.println();
      System.out.println(wrap(errorMsg));
    }
  }

  /**
   * Returns the not available text explaining that the data is not available
   * because the server is down.
   * @return the text.
   */
  private String getNotAvailableBecauseServerIsDownText()
  {
    displayMustStartLegend = true;
    return getMsg("not-available-server-down-cli-label", false);
  }

  /**
   * Returns the not available text explaining that the data is not available
   * because authentication is required.
   * @return the text.
   */
  private String getNotAvailableBecauseAuthenticationIsRequiredText()
  {
    displayMustAuthenticateLegend = true;
    return getMsg("not-available-authentication-required-cli-label", false);
  }

  /**
   * Returns the not available text explaining that the data is not available.
   * @return the text.
   */
  private String getNotAvailableText()
  {
    return getMsg("not-available-label", false);
  }

  /**
   * Writes the contents of the provided table model simulating a table layout
   * using text.
   * @param tableModel the TableModel.
   * @param desc the Server Status descriptor.
   */
  private void writeTableModel(TableModel tableModel,
      ServerStatusDescriptor desc)
  {
    int[] maxWidths = new int[tableModel.getColumnCount()];
    for (int i=0; i<maxWidths.length; i++)
    {
      maxWidths[i] = tableModel.getColumnName(i).length();
    }

    for (int i=0; i<tableModel.getRowCount(); i++)
    {
      for (int j=0; j<maxWidths.length; j++)
      {
        Object v = tableModel.getValueAt(i, j);
        if (v != null)
        {
          if (v instanceof String)
          {
            maxWidths[j] = Math.max(maxWidths[j], ((String)v).length());
          }
          else if (v instanceof Integer)
          {
            String text;
            int nEntries = ((Integer)v).intValue();
            if (nEntries >= 0)
            {
              text = String.valueOf(nEntries);
            }
            else
            {
              if (!desc.isAuthenticated())
              {
                text = getNotAvailableBecauseAuthenticationIsRequiredText();
              }
              else
              {
                text = getNotAvailableText();
              }
            }
            maxWidths[j] = Math.max(maxWidths[j], text.length());
          }
          else
          {
            throw new IllegalStateException("Unknown object type: "+v);
          }
        }
      }
    }

    int totalWidth = 0;
    for (int i=0; i<maxWidths.length; i++)
    {
      if (i < maxWidths.length - 1)
      {
        maxWidths[i] += 5;
      }
      totalWidth += maxWidths[i];
    }

    StringBuilder headerLine = new StringBuilder();
    for (int i=0; i<maxWidths.length; i++)
    {
      String header = tableModel.getColumnName(i);
      headerLine.append(header);
      int extra = maxWidths[i] - header.length();
      for (int j=0; j<extra; j++)
      {
        headerLine.append(" ");
      }
    }
    System.out.println(wrap(headerLine.toString()));
    StringBuilder t = new StringBuilder();
    for (int i=0; i<headerLine.length(); i++)
    {
      t.append("=");
    }
    System.out.println(wrap(t.toString()));

    for (int i=0; i<tableModel.getRowCount(); i++)
    {
      StringBuilder line = new StringBuilder();
      for (int j=0; j<tableModel.getColumnCount(); j++)
      {
        int extra = maxWidths[j];
        Object v = tableModel.getValueAt(i, j);
        if (v != null)
        {
          if (v instanceof String)
          {
            line.append(v);
            extra -= ((String)v).length();
          }
          else if (v instanceof Integer)
          {
            int nEntries = ((Integer)v).intValue();
            if (nEntries >= 0)
            {
              line.append(nEntries);
            }
            else
            {
              if (!desc.isAuthenticated())
              {
                line.append(
                    getNotAvailableBecauseAuthenticationIsRequiredText());
              }
              else
              {
                line.append(getNotAvailableText());
              }
            }
          }
          else
          {
            throw new IllegalStateException("Unknown object type: "+v);
          }

        }
        for (int k=0; k<extra; k++)
        {
          line.append(" ");
        }
      }
      System.out.println(wrap(line.toString()));
    }
  }

  /**
   * Writes the contents of the provided database table model.  Every base DN
   * is written in a block containing pairs of labels and values.
   * @param tableModel the TableModel.
   * @param desc the Server Status descriptor.
   */
  private void writeDatabasesTableModel(DatabasesTableModel tableModel,
  ServerStatusDescriptor desc)
  {
    boolean isRunning =
      desc.getStatus() == ServerStatusDescriptor.ServerStatus.STARTED;

    int labelWidth = 0;
    String[] labels = new String[tableModel.getColumnCount()];
    for (int i=0; i<tableModel.getColumnCount(); i++)
    {
      String header;
      if (i == 5)
      {
        header = getMsg("age-of-oldest-missing-change-column-cli", false);
      }
      else
      {
        header = tableModel.getColumnName(i);
      }
      labels[i] = header+":";
      labelWidth = Math.max(labelWidth, labels[i].length());
    }

    String replicatedLabel = getMsg("suffix-replicated-label", false);
    for (int i=0; i<tableModel.getRowCount(); i++)
    {
      if (i > 0)
      {
        System.out.println();
      }
      for (int j=0; j<tableModel.getColumnCount(); j++)
      {
        String value;
        Object v = tableModel.getValueAt(i, j);
        if (v != null)
        {
          if (v instanceof String)
          {
            value = (String)v;
          }
          else if (v instanceof Integer)
          {
            int nEntries = ((Integer)v).intValue();
            if (nEntries >= 0)
            {
              value = String.valueOf(nEntries);
            }
            else
            {
              if (!isRunning)
              {
                value = getNotAvailableBecauseServerIsDownText();
              }
              if (!desc.isAuthenticated())
              {
                value = getNotAvailableBecauseAuthenticationIsRequiredText();
              }
              else
              {
                value = getNotAvailableText();
              }
            }
          }
          else
          {
            throw new IllegalStateException("Unknown object type: "+v);
          }
        }
        else
        {
          value = "";
        }

        if (value.equals(getNotAvailableText()))
        {
          if (!isRunning)
          {
            value = getNotAvailableBecauseServerIsDownText();
          }
          if (!desc.isAuthenticated())
          {
            value = getNotAvailableBecauseAuthenticationIsRequiredText();
          }
        }

        boolean doWrite = true;
        if ((j == 4) || (j == 5))
        {
          // If the suffix is not replicated we do not have to display these
          // lines.
          if (!replicatedLabel.equals(tableModel.getValueAt(i, 3)))
          {
            doWrite = false;
          }
        }
        if (doWrite)
        {
          writeLabelValue(labels[j], value, labelWidth);
        }
      }
    }
  }

  private void writeLabelValue(String label, String value, int maxLabelWidth)
  {
    StringBuilder buf = new StringBuilder();
    buf.append(label);

    int extra = maxLabelWidth - label.length();
    for (int i = 0; i<extra; i++)
    {
      buf.append(" ");
    }
    buf.append(" "+value);
    System.out.println(wrap(buf.toString()));

  }

  private String wrap(String text)
  {
    return org.opends.server.util.StaticUtils.wrapText(text,
        Utils.getCommandLineMaxLineWidth());
  }

  private String centerTitle(String text)
  {
    String centered;
    if (text.length() <= Utils.getCommandLineMaxLineWidth() - 8)
    {
      StringBuilder buf = new StringBuilder();
      int extra = Math.min(10,
          (Utils.getCommandLineMaxLineWidth() - 8 - text.length()) / 2);
      for (int i=0; i<extra; i++)
      {
        buf.append(" ");
      }
      buf.append("--- "+text+" ---");
      centered = buf.toString();
    }
    else
    {
      centered = text;
    }
    return centered;
  }
}

