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
package org.opends.server.tools.dsconfig;
import org.opends.messages.Message;



import static org.opends.messages.ToolMessages.*;
import static org.opends.server.tools.ToolConstants.*;

import org.opends.server.admin.client.AuthenticationException;
import org.opends.server.admin.client.AuthenticationNotSupportedException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;
import org.opends.server.admin.client.ldap.LDAPConnection;
import org.opends.server.admin.client.ldap.LDAPManagementContext;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.tools.ClientException;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.args.SubCommandArgumentParser;



/**
 * An LDAP management context factory.
 */
public final class LDAPManagementContextFactory implements
    ManagementContextFactory {

  // The default bind DN which will be used to manage the directory
  // server.
  private static final String DEFAULT_BIND_DN = "cn=directory manager";

  // The management context.
  private ManagementContext context = null;

  // The argument which should be used to specify the bind DN.
  private StringArgument bindDNArgument;

  // The argument which should be used to specify the bind password.
  private StringArgument bindPasswordArgument;

  // The argument which should be used to specify the location of the
  // bind password file.
  private FileBasedArgument bindPasswordFileArgument;

  // The argument which should be used to specify the directory server
  // LDAP host address.
  private StringArgument hostArgument;

  // The argument which should be used to specify the directory server
  // LDAP port.
  private IntegerArgument portArgument;



  /**
   * Creates a new LDAP management context factory.
   */
  public LDAPManagementContextFactory() {
    // No implementation required.
  }



  /**
   * {@inheritDoc}
   */
  public ManagementContext getManagementContext(ConsoleApplication app)
      throws ArgumentException, ClientException {
    // Lazily create the LDAP management context.
    if (context == null) {
      // Get the LDAP host.
      String hostName = hostArgument.getValue();

      // Get the LDAP port.
      int portNumber = portArgument.getIntValue();

      // Get the LDAP bind credentials.
      String bindDN = bindDNArgument.getValue();
      String bindPassword = bindPasswordArgument.getValue();

      if (bindPasswordFileArgument.isPresent()) {
        // Read from file if it exists.
        bindPassword = bindPasswordFileArgument.getValue();

        if (bindPassword == null) {
          throw ArgumentExceptionFactory.missingBindPassword(bindDN);
        }
      } else if (bindPassword == null || bindPassword.equals("-")) {
        // Read the password from the stdin.
        if (!app.isInteractive()) {
          throw ArgumentExceptionFactory
              .unableToReadBindPasswordInteractively();
        }

        try {
          Message prompt = INFO_LDAPAUTH_PASSWORD_PROMPT.get(bindDN);
          bindPassword = app.readPassword(prompt);
        } catch (Exception e) {
          throw ArgumentExceptionFactory.unableToReadBindPassword(e);
        }
      }

      // Create the management context.
      try {
        LDAPConnection conn = JNDIDirContextAdaptor.simpleBind(hostName,
            portNumber, bindDN, bindPassword);
        context = LDAPManagementContext.createFromContext(conn);
      } catch (AuthenticationNotSupportedException e) {
        Message message = ERR_DSCFG_ERROR_LDAP_SIMPLE_BIND_NOT_SUPPORTED.get();
        throw new ClientException(LDAPResultCode.AUTH_METHOD_NOT_SUPPORTED,
            message);
      } catch (AuthenticationException e) {
        Message message = ERR_DSCFG_ERROR_LDAP_SIMPLE_BIND_FAILED.get(bindDN);
        throw new ClientException(LDAPResultCode.INVALID_CREDENTIALS, message);
      } catch (CommunicationException e) {
        Message message =
            ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT.get(
                    hostName, String.valueOf(portNumber));
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR,
            message);
      }
    }
    return context;
  }



  /**
   * {@inheritDoc}
   */
  public void registerGlobalArguments(SubCommandArgumentParser parser)
      throws ArgumentException {
    // Create the global arguments.
    hostArgument = new StringArgument("host", OPTION_SHORT_HOST,
        OPTION_LONG_HOST, false, false, true, OPTION_VALUE_HOST, "localhost",
        null, INFO_DESCRIPTION_HOST.get());

    portArgument = new IntegerArgument("port", OPTION_SHORT_PORT,
        OPTION_LONG_PORT, false, false, true, OPTION_VALUE_PORT, 389, null,
        INFO_DESCRIPTION_PORT.get());

    bindDNArgument = new StringArgument("bindDN", OPTION_SHORT_BINDDN,
        OPTION_LONG_BINDDN, false, false, true, OPTION_VALUE_BINDDN,
        DEFAULT_BIND_DN, null, INFO_DESCRIPTION_BINDDN.get());

    bindPasswordArgument = new StringArgument("bindPassword",
        OPTION_SHORT_BINDPWD, OPTION_LONG_BINDPWD, false, false, true,
        OPTION_VALUE_BINDPWD, null, null, INFO_DESCRIPTION_BINDPASSWORD.get());

    bindPasswordFileArgument = new FileBasedArgument("bindPasswordFile",
        OPTION_SHORT_BINDPWD_FILE, OPTION_LONG_BINDPWD_FILE, false, false,
        OPTION_VALUE_BINDPWD_FILE, null, null,
        INFO_DESCRIPTION_BINDPASSWORDFILE.get());

    // Register the global arguments.
    parser.addGlobalArgument(hostArgument);
    parser.addGlobalArgument(portArgument);
    parser.addGlobalArgument(bindDNArgument);
    parser.addGlobalArgument(bindPasswordArgument);
    parser.addGlobalArgument(bindPasswordFileArgument);
  }



  /**
   * {@inheritDoc}
   */
  public void validateGlobalArguments() throws ArgumentException {
    // Make sure that the user didn't specify any conflicting
    // arguments.
    if (bindPasswordArgument.isPresent()
        && bindPasswordFileArgument.isPresent()) {
      Message message = ERR_TOOL_CONFLICTING_ARGS.
          get(bindPasswordArgument.getLongIdentifier(),
              bindPasswordFileArgument.getLongIdentifier());
      throw new ArgumentException(message);
    }
  }

}
