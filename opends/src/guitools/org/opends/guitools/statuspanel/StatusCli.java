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

package org.opends.guitools.statuspanel;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

import javax.swing.table.TableModel;

import org.opends.guitools.statuspanel.ui.DatabasesTableModel;
import org.opends.guitools.statuspanel.ui.ListenersTableModel;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.QuickSetupLog;
import static org.opends.quicksetup.util.Utils.*;

import org.opends.server.admin.client.cli.DsFrameworkCliReturnCode;
import org.opends.server.admin.client.cli.SecureConnectionCliParser;
import org.opends.server.core.DirectoryServer;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import static org.opends.messages.ToolMessages.*;
import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.QuickSetupMessages.*;

import org.opends.server.util.args.ArgumentException;

/**
 * The class used to provide some CLI interface to display status.
 *
 * This class basically is in charge of parsing the data provided by the user
 * in the command line.
 *
 */
class StatusCli extends SecureConnectionCliParser
{
  private String[] args;

  private boolean displayMustAuthenticateLegend;
  private boolean displayMustStartLegend;

  /**
   * The Logger.
   */
  static private final Logger LOG = Logger.getLogger(StatusCli.class.getName());

  /**
   * The main method which is called by the status command lines.
   * @param args the arguments passed by the status command lines.
   */
  public static void main(String[] args)
  {
    QuickSetupLog.disableConsoleLogging();
    StatusCli cli = new StatusCli(args);
    System.exit(cli.run());
  }

  /**
   * The constructor for this object.
   * @param args the arguments of the status command line.
   */
  StatusCli(String[] args)
  {
    super(org.opends.guitools.statuspanel.StatusCli.class.getName(),
        INFO_STATUS_CLI_USAGE_DESCRIPTION.get(), false);
    this.args = args;
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
    try
    {
      initializeGlobalOption(System.err);
      removeGlobalArgument(portArg);
      removeGlobalArgument(hostNameArg);
      removeGlobalArgument(verboseArg);
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
      System.err.println(wrap(message));
      return DsFrameworkCliReturnCode.ERROR_UNEXPECTED.getReturnCode();
    }

    // Validate user provided data
    try
    {
      parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());
      System.err.println(wrap(message));
      System.err.println(getUsage());

      return DsFrameworkCliReturnCode.ERROR_PARSING_ARGS.getReturnCode();
    }

    //  If we should just display usage or version information,
    // then print it and exit.
    if (usageOrVersionDisplayed())
    {
      return DsFrameworkCliReturnCode.SUCCESSFUL_NOP.getReturnCode();
    }
    int v = validateGlobalOption(System.err);

    if (v != DsFrameworkCliReturnCode.SUCCESSFUL_NOP.getReturnCode())
    {
      System.err.println(getUsage());
      return v;
    }
    else
    {
      boolean isServerRunning =
              Installation.getLocal().getStatus().isServerRunning();
      /* This is required to retrieve the ldap url to be used by the
       * ConfigFromLDAP class.
       */
      ConfigFromFile offLineConf = new ConfigFromFile();
      offLineConf.readConfiguration();

      try
      {
        if (isServerRunning)
        {
          String directoryManagerDn = getBindDN();
          String directoryManagerPwd = getBindPassword(directoryManagerDn,
              System.out, System.err);
          if (directoryManagerDn == null)
          {
            directoryManagerDn = "";
          }
          if (directoryManagerPwd == null)
          {
            directoryManagerPwd = "";
          }
          ServerStatusDescriptor desc = createServerStatusDescriptor(
              directoryManagerDn, directoryManagerPwd);
          ConfigFromLDAP onLineConf = new ConfigFromLDAP();
          ConnectionProtocolPolicy policy;
          if (startTLSArg.isPresent())
          {
            policy = ConnectionProtocolPolicy.USE_STARTTLS;
          }
          if (useSSLArg.isPresent())
          {
            policy = ConnectionProtocolPolicy.USE_LDAPS;
          }
          else
          {
            policy = ConnectionProtocolPolicy.USE_MOST_SECURE_AVAILABLE;
          }
          onLineConf.setConnectionInfo(offLineConf, policy, directoryManagerDn,
              directoryManagerPwd, getTrustManager());
          onLineConf.readConfiguration();
          updateDescriptorWithOnLineInfo(desc, onLineConf);
          writeStatus(desc);
        }
        else
        {
          ServerStatusDescriptor desc = createServerStatusDescriptor(null,
              null);
          updateDescriptorWithOffLineInfo(desc, offLineConf);
          writeStatus(desc);
        }
      }
      catch (ConfigException ce)
      {
        System.err.println(wrap(ce.getMessageObject()));
      }
    }

    return DsFrameworkCliReturnCode.SUCCESSFUL_NOP.getReturnCode();
  }

  private ServerStatusDescriptor createServerStatusDescriptor(String dn,
      String pwd)
  {
    ServerStatusDescriptor desc = new ServerStatusDescriptor();
    desc.setAuthenticated((dn != null) && (pwd != null));

    if (Installation.getLocal().getStatus().isServerRunning())
    {
      desc.setStatus(ServerStatusDescriptor.ServerStatus.STARTED);
    }
    else
    {
      desc.setStatus(ServerStatusDescriptor.ServerStatus.STOPPED);
    }

    desc.setInstallPath(new File(getInstallPathFromClasspath()));

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
    Message[] labels =
      {
        INFO_SERVER_STATUS_LABEL.get(),
        INFO_CONNECTIONS_LABEL.get(),
        INFO_ADMINISTRATIVE_USERS_LABEL.get(),
        INFO_INSTALLATION_PATH_LABEL.get(),
        INFO_OPENDS_VERSION_LABEL.get(),
        INFO_JAVA_VERSION_LABEL.get()
      };
    int labelWidth = 0;
    for (int i=0; i<labels.length; i++)
    {
      labelWidth = Math.max(labelWidth, labels[i].length());
    }
    System.out.println();
    Message title = INFO_SERVER_STATUS_TITLE.get();
    System.out.println(centerTitle(title));
    writeStatusContents(desc, labelWidth);
    writeCurrentConnectionContents(desc, labelWidth);
    System.out.println();

    title = INFO_SERVER_DETAILS_TITLE.get();
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
      System.out.println(wrap(INFO_NOT_AVAILABLE_SERVER_DOWN_CLI_LEGEND.get()));
    }
    else if (displayMustAuthenticateLegend)
    {
      System.out.println();
      System.out.println(
          wrap(INFO_NOT_AVAILABLE_AUTHENTICATION_REQUIRED_CLI_LEGEND.get()));
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
    Message status;
    switch (desc.getStatus())
    {
    case STARTED:
      status = INFO_SERVER_STARTED_LABEL.get();
      break;

    case STOPPED:
      status = INFO_SERVER_STOPPED_LABEL.get();
      break;

    case STARTING:
      status = INFO_SERVER_STARTING_LABEL.get();
      break;

    case STOPPING:
      status = INFO_SERVER_STOPPING_LABEL.get();
      break;

    case UNKNOWN:
      status = INFO_SERVER_UNKNOWN_STATUS_LABEL.get();
      break;

    default:
      throw new IllegalStateException("Unknown status: "+desc.getStatus());
    }
    writeLabelValue(INFO_SERVER_STATUS_LABEL.get(), status,
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
    Message text;
    if (desc.getStatus() == ServerStatusDescriptor.ServerStatus.STARTED)
    {
      int nConn = desc.getOpenConnections();
      if (nConn >= 0)
      {
        text = Message.raw(String.valueOf(nConn));
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

    writeLabelValue(INFO_CONNECTIONS_LABEL.get(), text, maxLabelWidth);
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
    Message text;
    if (administrators.size() > 0)
    {
      TreeSet<String> ordered = new TreeSet<String>();
      ordered.addAll(administrators);

      String first = ordered.iterator().next();
      writeLabelValue(
              INFO_ADMINISTRATIVE_USERS_LABEL.get(),
              Message.raw(first),
              maxLabelWidth);

      Iterator<String> it = ordered.iterator();
      // First one already printed
      it.next();
      while (it.hasNext())
      {
        writeLabelValue(
                INFO_ADMINISTRATIVE_USERS_LABEL.get(),
                Message.raw(it.next()),
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
      writeLabelValue(INFO_ADMINISTRATIVE_USERS_LABEL.get(), text,
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
    writeLabelValue(INFO_INSTALLATION_PATH_LABEL.get(),
            Message.raw(path.toString()),
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
    writeLabelValue(INFO_OPENDS_VERSION_LABEL.get(),
            Message.raw(openDSVersion),
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
    Message text;
    if (desc.getStatus() == ServerStatusDescriptor.ServerStatus.STARTED)
    {
      text = Message.raw(desc.getJavaVersion());
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
    writeLabelValue(INFO_JAVA_VERSION_LABEL.get(), text, maxLabelWidth);
  }

  /**
   * Writes the listeners contents displaying with what is specified in
   * the provided ServerStatusDescriptor object.
   * @param desc the ServerStatusDescriptor object.
   */
  private void writeListenerContents(ServerStatusDescriptor desc)
  {
    Message title = INFO_LISTENERS_TITLE.get();
    System.out.println(centerTitle(title));

    Set<ListenerDescriptor> listeners = desc.getListeners();

    if (listeners.size() == 0)
    {
      if (desc.getStatus() == ServerStatusDescriptor.ServerStatus.STARTED)
      {
        if (!desc.isAuthenticated())
        {
          System.out.println(
              wrap(INFO_NOT_AVAILABLE_AUTHENTICATION_REQUIRED_CLI_LABEL.get()));
        }
        else
        {
          System.out.println(wrap(INFO_NO_LISTENERS_FOUND.get()));
        }
      }
      else
      {
        System.out.println(wrap(INFO_NO_LISTENERS_FOUND.get()));
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
    Message title = INFO_DATABASES_TITLE.get();
    System.out.println(centerTitle(title));

    Set<DatabaseDescriptor> databases = desc.getDatabases();

    if (databases.size() == 0)
    {
      if (desc.getStatus() == ServerStatusDescriptor.ServerStatus.STARTED)
      {
        if (!desc.isAuthenticated())
        {
          System.out.println(
              wrap(INFO_NOT_AVAILABLE_AUTHENTICATION_REQUIRED_CLI_LABEL.get()));
        }
        else
        {
          System.out.println(wrap(INFO_NO_DBS_FOUND.get()));
        }
      }
      else
      {
        System.out.println(wrap(INFO_NO_DBS_FOUND.get()));
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
    Message errorMsg = desc.getErrorMessage();
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
  private Message getNotAvailableBecauseServerIsDownText()
  {
    displayMustStartLegend = true;
    return INFO_NOT_AVAILABLE_SERVER_DOWN_CLI_LABEL.get();
  }

  /**
   * Returns the not available text explaining that the data is not available
   * because authentication is required.
   * @return the text.
   */
  private Message getNotAvailableBecauseAuthenticationIsRequiredText()
  {
    displayMustAuthenticateLegend = true;
    return INFO_NOT_AVAILABLE_AUTHENTICATION_REQUIRED_CLI_LABEL.get();
  }

  /**
   * Returns the not available text explaining that the data is not available.
   * @return the text.
   */
  private Message getNotAvailableText()
  {
    return INFO_NOT_AVAILABLE_LABEL.get();
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
            Message text;
            int nEntries = ((Integer)v).intValue();
            if (nEntries >= 0)
            {
              text = Message.raw(String.valueOf(nEntries));
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

    MessageBuilder headerLine = new MessageBuilder();
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
    System.out.println(wrap(headerLine.toMessage()));
    MessageBuilder t = new MessageBuilder();
    for (int i=0; i<headerLine.length(); i++)
    {
      t.append("=");
    }
    System.out.println(wrap(t.toMessage()));

    for (int i=0; i<tableModel.getRowCount(); i++)
    {
      MessageBuilder line = new MessageBuilder();
      for (int j=0; j<tableModel.getColumnCount(); j++)
      {
        int extra = maxWidths[j];
        Object v = tableModel.getValueAt(i, j);
        if (v != null)
        {
          if (v instanceof String)
          {
            line.append((String)v);
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
      System.out.println(wrap(line.toMessage()));
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
    Message[] labels = new Message[tableModel.getColumnCount()];
    for (int i=0; i<tableModel.getColumnCount(); i++)
    {
      Message header;
      if (i == 5)
      {
        header = INFO_AGE_OF_OLDEST_MISSING_CHANGE_COLUMN_CLI.get();
      }
      else
      {
        header = Message.raw(tableModel.getColumnName(i));
      }
      labels[i] = new MessageBuilder(header).append(":").toMessage();
      labelWidth = Math.max(labelWidth, labels[i].length());
    }

    Message replicatedLabel = INFO_SUFFIX_REPLICATED_LABEL.get();
    for (int i=0; i<tableModel.getRowCount(); i++)
    {
      if (i > 0)
      {
        System.out.println();
      }
      for (int j=0; j<tableModel.getColumnCount(); j++)
      {
        Message value;
        Object v = tableModel.getValueAt(i, j);
        if (v != null)
        {
          if (v instanceof String)
          {
            value = Message.raw((String)v);
          }
          else if (v instanceof Message)
          {
            value = (Message)v;
          }
          else if (v instanceof Integer)
          {
            int nEntries = ((Integer)v).intValue();
            if (nEntries >= 0)
            {
              value = Message.raw(String.valueOf(nEntries));
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
          value = Message.EMPTY;
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

  private void writeLabelValue(Message label, Message value, int maxLabelWidth)
  {
    MessageBuilder buf = new MessageBuilder();
    buf.append(label);

    int extra = maxLabelWidth - label.length();
    for (int i = 0; i<extra; i++)
    {
      buf.append(" ");
    }
    buf.append(" ").append(String.valueOf(value));
    System.out.println(wrap(buf.toMessage()));

  }

  private Message centerTitle(Message text)
  {
    Message centered;
    if (text.length() <= getCommandLineMaxLineWidth() - 8)
    {
      MessageBuilder buf = new MessageBuilder();
      int extra = Math.min(10,
          (getCommandLineMaxLineWidth() - 8 - text.length()) / 2);
      for (int i=0; i<extra; i++)
      {
        buf.append(" ");
      }
      buf.append("--- "+text+" ---");
      centered = buf.toMessage();
    }
    else
    {
      centered = text;
    }
    return centered;
  }
}


