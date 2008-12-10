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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 */

package org.opends.server.tools.status;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;
import javax.swing.table.TableModel;

import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNTableModel;
import org.opends.guitools.controlpanel.datamodel.ConfigReadException;
import org.opends.guitools.controlpanel.datamodel.ConnectionHandlerDescriptor;
import org.opends.guitools.controlpanel.datamodel.ConnectionHandlerTableModel;
import org.opends.guitools.controlpanel.datamodel.ConnectionProtocolPolicy;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.util.ControlPanelLog;
import org.opends.guitools.controlpanel.util.Utilities;

import static org.opends.quicksetup.util.Utils.*;

import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.cli.DsFrameworkCliReturnCode;
import org.opends.server.admin.client.cli.SecureConnectionCliArgs;
import org.opends.server.core.DirectoryServer;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.admin.AdministrationConnector;
import static org.opends.messages.ToolMessages.*;
import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.QuickSetupMessages.*;

import org.opends.server.tools.ClientException;
import org.opends.server.tools.ToolConstants;
import org.opends.server.tools.dsconfig.LDAPManagementContextFactory;
import org.opends.server.types.DN;
import org.opends.server.types.NullOutputStream;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.cli.ConsoleApplication;
import org.opends.server.util.cli.LDAPConnectionConsoleInteraction;
import org.opends.server.util.table.TableBuilder;
import org.opends.server.util.table.TextTablePrinter;

/**
 * The class used to provide some CLI interface to display status.
 *
 * This class basically is in charge of parsing the data provided by the user
 * in the command line.
 *
 */
class StatusCli extends ConsoleApplication
{

  private boolean displayMustAuthenticateLegend;
  private boolean displayMustStartLegend;

  /** Prefix for log files. */
  static public final String LOG_FILE_PREFIX = "opends-status-";

  /** Suffix for log files. */
  static public final String LOG_FILE_SUFFIX = ".log";

  private ApplicationTrustManager interactiveTrustManager;

  private boolean useInteractiveTrustManager;

  // This CLI is always using the administration connector with SSL
  private final boolean alwaysSSL = true;

  /**
   * The enumeration containing the different return codes that the command-line
   * can have.
   *
   */
  enum ErrorReturnCode
  {
    /**
     * Successful display of the status.
     */
    SUCCESSFUL(0),
    /**
     * We did no have an error but the status was not displayed (displayed
     * version or usage).
     */
    SUCCESSFUL_NOP(0),
    /**
     * Unexpected error (potential bug).
     */
    ERROR_UNEXPECTED(1),
    /**
     * Cannot parse arguments.
     */
    ERROR_PARSING_ARGS(2),
    /**
     * User cancelled (for instance not accepting the certificate proposed) or
     * could not use the provided connection parameters in interactive mode.
     */
    USER_CANCELLED_OR_DATA_ERROR(3),
    /**
     * This occurs for instance when the authentication provided by the user is
     * not valid.
     */
    ERROR_READING_CONFIGURATION_WITH_LDAP(4);

    private int returnCode;
    private ErrorReturnCode(int returnCode)
    {
      this.returnCode = returnCode;
    }

    /**
     * Get the corresponding return code value.
     *
     * @return The corresponding return code value.
     */
    public int getReturnCode()
    {
      return returnCode;
    }
  };

  /**
   * The Logger.
   */
  static private final Logger LOG = Logger.getLogger(StatusCli.class.getName());

  // The argument parser
  private StatusCliArgumentParser argParser;

  /**
   * Constructor for the StatusCli object.
   *
   * @param out the print stream to use for standard output.
   * @param err the print stream to use for standard error.
   * @param in the input stream to use for standard input.
   */
  public StatusCli(PrintStream out, PrintStream err, InputStream in)
  {
    super(in, out, err);
  }

  /**
   * The main method for the status CLI tool.
   *
   * @param args the command-line arguments provided to this program.
   */

  public static void main(String[] args)
  {
    int retCode = mainCLI(args, true, System.out, System.err, System.in);

    if(retCode != 0)
    {
      System.exit(retCode);
    }
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the status tool.
   *
   * @param args the command-line arguments provided to this program.
   *
   * @return The error code.
   */

  public static int mainCLI(String[] args)
  {
    return mainCLI(args, true, System.out, System.err, System.in);
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the status tool.
   *
   * @param  args              The command-line arguments provided to this
   *                           program.
   * @param initializeServer   Indicates whether to initialize the server.
   * @param  outStream         The output stream to use for standard output, or
   *                           <CODE>null</CODE> if standard output is not
   *                           needed.
   * @param  errStream         The output stream to use for standard error, or
   *                           <CODE>null</CODE> if standard error is not
   *                           needed.
   * @param  inStream          The input stream to use for standard input.
   * @return The error code.
   */

  public static int mainCLI(String[] args, boolean initializeServer,
      OutputStream outStream, OutputStream errStream, InputStream inStream)
  {
    PrintStream out;
    if (outStream == null)
    {
      out = NullOutputStream.printStream();
    }
    else
    {
      out = new PrintStream(outStream);
    }

    PrintStream err;
    if (errStream == null)
    {
      err = NullOutputStream.printStream();
    }
    else
    {
      err = new PrintStream(errStream);
    }

    try {
      ControlPanelLog.initLogFileHandler(
              File.createTempFile(LOG_FILE_PREFIX, LOG_FILE_SUFFIX));
      ControlPanelLog.initPackage("org.opends.server.tools.status");
    } catch (Throwable t) {
      System.err.println("Unable to initialize log");
      t.printStackTrace();
    }

    StatusCli statusCli = new StatusCli(out, err, inStream);

    return statusCli.execute(args, initializeServer);
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the status CLI.
   *
   * @param args the command-line arguments provided to this program.
   * @param  initializeServer  Indicates whether to initialize the server.
   *
   * @return the return code (SUCCESSFUL, USER_DATA_ERROR or BUG.
   */
  public int execute(String[] args, boolean initializeServer) {
    if (initializeServer) {
      DirectoryServer.bootstrapClient();
    }

    argParser = new StatusCliArgumentParser(StatusCli.class.getName());
    try {
      argParser.initializeGlobalArguments(getOutputStream());
    } catch (ArgumentException ae) {
      Message message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
      println(message);
      return ErrorReturnCode.ERROR_UNEXPECTED.getReturnCode();
    }

    // Validate user provided data
    try {
      argParser.parseArguments(args);
    } catch (ArgumentException ae) {
      Message message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());
      println(message);
      println();
      println(Message.raw(argParser.getUsage()));

      return ErrorReturnCode.ERROR_PARSING_ARGS.getReturnCode();
    }

    //  If we should just display usage or version information,
    // then print it and exit.
    if (argParser.usageOrVersionDisplayed()) {
      return ErrorReturnCode.SUCCESSFUL_NOP.getReturnCode();
    }
    int v = argParser.validateGlobalOptions(getErrorStream());

    if (v != DsFrameworkCliReturnCode.SUCCESSFUL_NOP.getReturnCode()) {
      println(Message.raw(argParser.getUsage()));
      return v;
    } else {
      ControlPanelInfo controlInfo = ControlPanelInfo.getInstance();
      controlInfo.setTrustManager(getTrustManager());
      controlInfo.regenerateDescriptor();
      boolean authProvided = false;
      if (controlInfo.getServerDescriptor().getStatus() ==
        ServerDescriptor.ServerStatus.STARTED) {
        String bindDn;
        String bindPwd;
        if (argParser.isInteractive()) {
          ManagementContext ctx = null;

          // This is done because we do not need to ask the user about these
          // parameters.  If we force their presence the class
          // LDAPConnectionConsoleInteraction will not prompt the user for
          // them.
          SecureConnectionCliArgs secureArgsList =
            argParser.getSecureArgsList();

          secureArgsList.hostNameArg.setPresent(true);
          secureArgsList.portArg.setPresent(true);
          secureArgsList.hostNameArg.addValue(
            secureArgsList.hostNameArg.getDefaultValue());
          secureArgsList.portArg.addValue(
            secureArgsList.portArg.getDefaultValue());
          // We already know if SSL or StartTLS can be used.  If we cannot
          // use them we will not propose them in the connection parameters
          // and if none of them can be used we will just not ask for the
          // protocol to be used.
          LDAPConnectionConsoleInteraction ci =
            new LDAPConnectionConsoleInteraction(
            this, argParser.getSecureArgsList());
          try {
            ci.run(true, false);
            bindDn = ci.getBindDN();
            bindPwd = ci.getBindPassword();

            int port =
              AdministrationConnector.DEFAULT_ADMINISTRATION_CONNECTOR_PORT;
            controlInfo.setConnectionPolicy(
              ConnectionProtocolPolicy.USE_ADMIN);
            String ldapUrl = controlInfo.getURLToConnect();
            try {
              URI uri = new URI(ldapUrl);
              port = uri.getPort();
              ci.setPortNumber(port);
            } catch (Throwable t) {
              LOG.log(Level.SEVERE, "Error parsing url: " + ldapUrl);
            }
            LDAPManagementContextFactory factory =
              new LDAPManagementContextFactory(alwaysSSL);
            ctx = factory.getManagementContext(this, ci);
            interactiveTrustManager = ci.getTrustManager();
            controlInfo.setTrustManager(interactiveTrustManager);
            useInteractiveTrustManager = true;
          } catch (ArgumentException e) {
            println(e.getMessageObject());
            return ErrorReturnCode.USER_CANCELLED_OR_DATA_ERROR.getReturnCode();
          } catch (ClientException e) {
            println(e.getMessageObject());
            // Display the information in the config file
            ServerDescriptor desc = controlInfo.getServerDescriptor();
            writeStatus(desc);
            return ErrorReturnCode.USER_CANCELLED_OR_DATA_ERROR.getReturnCode();
          } finally {
            if (ctx != null) {
              try {
                ctx.close();
              } catch (Throwable t) {
              }
            }
          }
        } else {
          bindDn = argParser.getBindDN();
          bindPwd = argParser.getBindPassword();
        }

        authProvided = bindPwd != null;

        if (bindDn == null) {
          bindDn = "";
        }
        if (bindPwd == null) {
          bindPwd = "";
        }

        if (authProvided) {
          InitialLdapContext ctx = null;
          try {
            ctx = Utilities.getAdminDirContext(controlInfo, bindDn, bindPwd);
            controlInfo.setDirContext(ctx);
            controlInfo.regenerateDescriptor();
            ServerDescriptor desc = controlInfo.getServerDescriptor();
            writeStatus(desc);

            if (!desc.getExceptions().isEmpty()) {
              return ErrorReturnCode.ERROR_READING_CONFIGURATION_WITH_LDAP.
                getReturnCode();
            }
          } catch (NamingException ne) {
            // This should not happen but this is useful information to
            // diagnose the error.
            println();
            println(INFO_ERROR_READING_SERVER_CONFIGURATION.get(
              ne.toString()));
            return ErrorReturnCode.ERROR_READING_CONFIGURATION_WITH_LDAP.
              getReturnCode();
          } catch (ConfigReadException cre) {
            // This should not happen but this is useful information to
            // diagnose the error.
            println();
            println(cre.getMessageObject());
            return ErrorReturnCode.ERROR_READING_CONFIGURATION_WITH_LDAP.
              getReturnCode();
          } finally {
            if (ctx != null) {
              try {
                ctx.close();
              } catch (Throwable t) {
              }
            }
          }
        } else {
          // The user did not provide authentication: just display the
          // information we can get reading the config file.
          ServerDescriptor desc = controlInfo.getServerDescriptor();
          writeStatus(desc);
        }
      } else {
        ServerDescriptor desc = controlInfo.getServerDescriptor();
        writeStatus(desc);
      }
    }

    return ErrorReturnCode.SUCCESSFUL.getReturnCode();
  }

  private void writeStatus(ServerDescriptor desc)
  {
    Message[] labels =
      {
        INFO_SERVER_STATUS_LABEL.get(),
        INFO_CONNECTIONS_LABEL.get(),
        INFO_HOSTNAME_LABEL.get(),
        INFO_ADMINISTRATIVE_USERS_LABEL.get(),
        INFO_INSTALLATION_PATH_LABEL.get(),
        INFO_OPENDS_VERSION_LABEL.get(),
        INFO_JAVA_VERSION_LABEL.get(),
        INFO_CTRL_PANEL_ADMIN_CONNECTOR_LABEL.get()
      };
    int labelWidth = 0;
    Message title = INFO_SERVER_STATUS_TITLE.get();
    if (!isScriptFriendly())
    {
      for (int i=0; i<labels.length; i++)
      {
        labelWidth = Math.max(labelWidth, labels[i].length());
      }
      getOutputStream().println();
      getOutputStream().println(centerTitle(title));
    }
    writeStatusContents(desc, labelWidth);
    writeCurrentConnectionContents(desc, labelWidth);
    if (!isScriptFriendly())
    {
      getOutputStream().println();
    }

    title = INFO_SERVER_DETAILS_TITLE.get();
    if (!isScriptFriendly())
    {
      getOutputStream().println(centerTitle(title));
    }
    writeHostnameContents(desc, labelWidth);
    writeAdministrativeUserContents(desc, labelWidth);
    writeInstallPathContents(desc, labelWidth);
    writeVersionContents(desc, labelWidth);
    writeJavaVersionContents(desc, labelWidth);
    writeAdminConnectorContents(desc, labelWidth);
    if (!isScriptFriendly())
    {
      getOutputStream().println();
    }

    writeListenerContents(desc);
    if (!isScriptFriendly())
    {
      getOutputStream().println();
    }

    writeBaseDNContents(desc);

    writeErrorContents(desc);

    if (!isScriptFriendly())
    {
      if (displayMustStartLegend)
      {
        getOutputStream().println();
        getOutputStream().println(
            wrapText(INFO_NOT_AVAILABLE_SERVER_DOWN_CLI_LEGEND.get()));
      }
      else if (displayMustAuthenticateLegend)
      {
        getOutputStream().println();
        getOutputStream().println(
            wrapText(
                INFO_NOT_AVAILABLE_AUTHENTICATION_REQUIRED_CLI_LEGEND.get()));
      }
    }
    getOutputStream().println();
  }

  /**
   * Writes the status contents displaying with what is specified in the
   * provided ServerDescriptor object.
   * @param desc the ServerStatusDescriptor object.
   */
  private void writeStatusContents(ServerDescriptor desc,
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
   * in the provided ServerDescriptor object.
   * @param desc the ServerDescriptor object.
   */
  private void writeCurrentConnectionContents(ServerDescriptor desc,
      int maxLabelWidth)
  {
    Message text;
    if (desc.getStatus() == ServerDescriptor.ServerStatus.STARTED)
    {
      int nConn = desc.getOpenConnections();
      if (nConn >= 0)
      {
        text = Message.raw(String.valueOf(nConn));
      }
      else
      {
        if (!desc.isAuthenticated() || !desc.getExceptions().isEmpty())
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
   * Writes the host name contents.
   * @param desc the ServerDescriptor object.
   * @param maxLabelWidth the maximum label width of the left label.
   */
  private void writeHostnameContents(ServerDescriptor desc,
      int maxLabelWidth)
  {
    writeLabelValue(INFO_HOSTNAME_LABEL.get(),
        Message.raw(desc.getHostname()),
        maxLabelWidth);
  }
  /**
   * Writes the administrative user contents displaying with what is specified
   * in the provided ServerStatusDescriptor object.
   * @param desc the ServerStatusDescriptor object.
   * @param maxLabelWidth the maximum label width of the left label.
   */
  private void writeAdministrativeUserContents(ServerDescriptor desc,
      int maxLabelWidth)
  {
    Set<DN> administrators = desc.getAdministrativeUsers();
    Message text;
    if (administrators.size() > 0)
    {
      TreeSet<DN> ordered = new TreeSet<DN>();
      ordered.addAll(administrators);

      DN first = ordered.iterator().next();
      writeLabelValue(
              INFO_ADMINISTRATIVE_USERS_LABEL.get(),
              Message.raw(first.toString()),
              maxLabelWidth);

      Iterator<DN> it = ordered.iterator();
      // First one already printed
      it.next();
      while (it.hasNext())
      {
        writeLabelValue(
                INFO_ADMINISTRATIVE_USERS_LABEL.get(),
                Message.raw(it.next().toString()),
                maxLabelWidth);
      }
    }
    else
    {
      if (desc.getStatus() == ServerDescriptor.ServerStatus.STARTED)
      {
        if (!desc.isAuthenticated() || !desc.getExceptions().isEmpty())
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
   * provided ServerDescriptor object.
   * @param desc the ServerDescriptor object.
   * @param maxLabelWidth the maximum label width of the left label.
   */
  private void writeInstallPathContents(ServerDescriptor desc,
      int maxLabelWidth)
  {
    File path = desc.getInstallPath();
    writeLabelValue(INFO_INSTALLATION_PATH_LABEL.get(),
            Message.raw(path.toString()),
            maxLabelWidth);
  }

  /**
   * Updates the server version contents displaying with what is specified in
   * the provided ServerDescriptor object.
   * This method must be called from the event thread.
   * @param desc the ServerDescriptor object.
   */
  private void writeVersionContents(ServerDescriptor desc,
      int maxLabelWidth)
  {
    String openDSVersion = desc.getOpenDSVersion();
    writeLabelValue(INFO_OPENDS_VERSION_LABEL.get(),
            Message.raw(openDSVersion),
            maxLabelWidth);
  }

  /**
   * Updates the java version contents displaying with what is specified in
   * the provided ServerDescriptor object.
   * This method must be called from the event thread.
   * @param desc the ServerDescriptor object.
   * @param maxLabelWidth the maximum label width of the left label.
   */
  private void writeJavaVersionContents(ServerDescriptor desc,
      int maxLabelWidth)
  {
    Message text;
    if (desc.getStatus() == ServerDescriptor.ServerStatus.STARTED)
    {
      text = Message.raw(desc.getJavaVersion());
      if (text == null)
      {
        if (!desc.isAuthenticated() || !desc.getExceptions().isEmpty())
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
   * Updates the admin connector contents displaying with what is specified in
   * the provided ServerDescriptor object.
   * This method must be called from the event thread.
   * @param desc the ServerDescriptor object.
   * @param maxLabelWidth the maximum label width of the left label.
   */
  private void writeAdminConnectorContents(ServerDescriptor desc,
      int maxLabelWidth)
  {
    ConnectionHandlerDescriptor adminConnector = desc.getAdminConnector();
    if (adminConnector != null)
    {
      Message text = INFO_CTRL_PANEL_ADMIN_CONNECTOR_DESCRIPTION.get(
          adminConnector.getPort());
      writeLabelValue(INFO_CTRL_PANEL_ADMIN_CONNECTOR_LABEL.get(), text,
          maxLabelWidth);
    }
    else
    {
      writeLabelValue(INFO_CTRL_PANEL_ADMIN_CONNECTOR_LABEL.get(),
          INFO_NOT_AVAILABLE_SHORT_LABEL.get(),
          maxLabelWidth);
    }
  }

  /**
   * Writes the listeners contents displaying with what is specified in
   * the provided ServerDescriptor object.
   * @param desc the ServerDescriptor object.
   */
  private void writeListenerContents(ServerDescriptor desc)
  {
    if (!isScriptFriendly())
    {
      Message title = INFO_LISTENERS_TITLE.get();
      getOutputStream().println(centerTitle(title));
    }

    Set<ConnectionHandlerDescriptor> allHandlers = desc.getConnectionHandlers();

    Set<ConnectionHandlerDescriptor> connectionHandlers =
      new LinkedHashSet<ConnectionHandlerDescriptor>();
    for (ConnectionHandlerDescriptor listener: allHandlers)
    {
      connectionHandlers.add(listener);
    }

    if (connectionHandlers.size() == 0)
    {
      if (desc.getStatus() == ServerDescriptor.ServerStatus.STARTED)
      {
        if (!desc.isAuthenticated())
        {
          getOutputStream().println(
              wrapText(
                  INFO_NOT_AVAILABLE_AUTHENTICATION_REQUIRED_CLI_LABEL.get()));
        }
        else
        {
          getOutputStream().println(wrapText(INFO_NO_LISTENERS_FOUND.get()));
        }
      }
      else
      {
        getOutputStream().println(wrapText(INFO_NO_LISTENERS_FOUND.get()));
      }
    }
    else
    {
      ConnectionHandlerTableModel connHandlersTableModel =
        new ConnectionHandlerTableModel(false);
      connHandlersTableModel.setData(connectionHandlers);
      writeTableModel(connHandlersTableModel, desc);
    }
  }

  /**
   * Writes the base DN contents displaying with what is specified in
   * the provided ServerDescriptor object.
   * @param desc the ServerDescriptor object.
   */
  private void writeBaseDNContents(ServerDescriptor desc)
  {
    Message title = INFO_DATABASES_TITLE.get();
    if (!isScriptFriendly())
    {
      getOutputStream().println(centerTitle(title));
    }

    Set<BaseDNDescriptor> replicas = new HashSet<BaseDNDescriptor>();
    Set<BackendDescriptor> bs = desc.getBackends();
    for (BackendDescriptor backend: bs)
    {
      if (!backend.isConfigBackend())
      {
        replicas.addAll(backend.getBaseDns());
      }
    }
    if (replicas.size() == 0)
    {
      if (desc.getStatus() == ServerDescriptor.ServerStatus.STARTED)
      {
        if (!desc.isAuthenticated())
        {
          getOutputStream().println(
              wrapText(
                  INFO_NOT_AVAILABLE_AUTHENTICATION_REQUIRED_CLI_LABEL.get()));
        }
        else
        {
          getOutputStream().println(wrapText(INFO_NO_DBS_FOUND.get()));
        }
      }
      else
      {
        getOutputStream().println(wrapText(INFO_NO_DBS_FOUND.get()));
      }
    }
    else
    {
      BaseDNTableModel baseDNTableModel = new BaseDNTableModel(true, false);
      baseDNTableModel.setData(replicas, desc.getStatus(),
          desc.isAuthenticated());

      writeBaseDNTableModel(baseDNTableModel, desc);
    }
  }

  /**
   * Writes the error label contents displaying with what is specified in
   * the provided ServerDescriptor object.
   * @param desc the ServerDescriptor object.
   */
  private void writeErrorContents(ServerDescriptor desc)
  {
    for (OpenDsException ex : desc.getExceptions())
    {
      Message errorMsg = ex.getMessageObject();
      if (errorMsg != null)
      {
        getOutputStream().println();
        getOutputStream().println(wrapText(errorMsg));
      }
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
      ServerDescriptor desc)
  {
    if (isScriptFriendly())
    {
      for (int i=0; i<tableModel.getRowCount(); i++)
      {
        getOutputStream().println("-");
        for (int j=0; j<tableModel.getColumnCount(); j++)
        {
          MessageBuilder line = new MessageBuilder();
          line.append(tableModel.getColumnName(j)+": ");

          line.append(getCellValue(tableModel.getValueAt(i, j), desc));

          getOutputStream().println(wrapText(line.toMessage()));
        }
      }
    }
    else
    {
      TableBuilder table = new TableBuilder();
      for (int i=0; i< tableModel.getColumnCount(); i++)
      {
        table.appendHeading(Message.raw(tableModel.getColumnName(i)));
      }
      for (int i=0; i<tableModel.getRowCount(); i++)
      {
        table.startRow();
        for (int j=0; j<tableModel.getColumnCount(); j++)
        {
          table.appendCell(getCellValue(tableModel.getValueAt(i, j), desc));
        }
      }
      TextTablePrinter printer = new TextTablePrinter(getOutputStream());
      printer.setColumnSeparator(ToolConstants.LIST_TABLE_SEPARATOR);
      table.print(printer);
    }
  }


  private Message getCellValue(Object v, ServerDescriptor desc)
  {
    Message s = null;
    if (v != null)
    {
      if (v instanceof String)
      {
        s = Message.raw((String)v);
      }
      else if (v instanceof Integer)
      {
        int nEntries = ((Integer)v).intValue();
        if (nEntries >= 0)
        {
          s = Message.raw(String.valueOf(nEntries));
        }
        else
        {
          if (!desc.isAuthenticated() || !desc.getExceptions().isEmpty())
          {
            s = getNotAvailableBecauseAuthenticationIsRequiredText();
          }
          else
          {
            s = getNotAvailableText();
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
      s = getNotAvailableText();
    }
    return s;
  }

  /**
   * Writes the contents of the provided base DN table model.  Every base DN
   * is written in a block containing pairs of labels and values.
   * @param tableModel the TableModel.
   * @param desc the Server Status descriptor.
   */
  private void writeBaseDNTableModel(BaseDNTableModel tableModel,
  ServerDescriptor desc)
  {
    boolean isRunning =
      desc.getStatus() == ServerDescriptor.ServerStatus.STARTED;

    int labelWidth = 0;
    int labelWidthWithoutReplicated = 0;
    Message[] labels = new Message[tableModel.getColumnCount()];
    for (int i=0; i<tableModel.getColumnCount(); i++)
    {
      Message header = Message.raw(tableModel.getColumnName(i));
      labels[i] = new MessageBuilder(header).append(":").toMessage();
      labelWidth = Math.max(labelWidth, labels[i].length());
      if ((i != 4) && (i != 5))
      {
        labelWidthWithoutReplicated =
          Math.max(labelWidthWithoutReplicated, labels[i].length());
      }
    }

    Message replicatedLabel = INFO_BASEDN_REPLICATED_LABEL.get();
    for (int i=0; i<tableModel.getRowCount(); i++)
    {
      if (isScriptFriendly())
      {
        getOutputStream().println("-");
      }
      else if (i > 0)
      {
        getOutputStream().println();
      }
      for (int j=0; j<tableModel.getColumnCount(); j++)
      {
        Message value;
        Object v = tableModel.getValueAt(i, j);
        if (v != null)
        {
          if (v == BaseDNTableModel.NOT_AVAILABLE_SERVER_DOWN)
          {
            value = getNotAvailableBecauseServerIsDownText();
          }
          else if (v == BaseDNTableModel.NOT_AVAILABLE_AUTHENTICATION_REQUIRED)
          {
            value = getNotAvailableBecauseAuthenticationIsRequiredText();
          }
          else if (v == BaseDNTableModel.NOT_AVAILABLE)
          {
            value = getNotAvailableText();
          }
          else if (v instanceof String)
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
              if (!desc.isAuthenticated() || !desc.getExceptions().isEmpty())
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
          if (!desc.isAuthenticated() || !desc.getExceptions().isEmpty())
          {
            value = getNotAvailableBecauseAuthenticationIsRequiredText();
          }
        }

        boolean doWrite = true;
        boolean isReplicated =
          replicatedLabel.toString().equals(
              String.valueOf(tableModel.getValueAt(i, 3)));
        if ((j == 4) || (j == 5))
        {
          // If the suffix is not replicated we do not have to display these
          // lines.
          doWrite = isReplicated;
        }
        if (doWrite)
        {
          writeLabelValue(labels[j], value,
              isReplicated?labelWidth:labelWidthWithoutReplicated);
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
    getOutputStream().println(wrapText(buf.toMessage()));
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

  /**
   * Returns the trust manager to be used by this application.
   * @return the trust manager to be used by this application.
   */
  private ApplicationTrustManager getTrustManager()
  {
    if (useInteractiveTrustManager)
    {
      return interactiveTrustManager;
    }
    else
    {
      return argParser.getTrustManager();
    }
  }

  /**
   * {@inheritDoc}
   */
  public boolean isAdvancedMode() {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isInteractive() {
    return argParser.isInteractive();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isMenuDrivenMode() {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isQuiet() {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isScriptFriendly() {
    return argParser.isScriptFriendly();
  }



  /**
   * {@inheritDoc}
   */
  public boolean isVerbose() {
    return true;
  }



  /**
   * Wraps a message accoring to client tool console width.
   * @param text to wrap
   * @return raw message representing wrapped string
   */
  private Message wrapText(Message text)
  {
    return Message.raw(
        StaticUtils.wrapText(text, getCommandLineMaxLineWidth()));
  }
}
