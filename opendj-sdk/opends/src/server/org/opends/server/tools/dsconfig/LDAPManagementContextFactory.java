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

import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.admin.ads.util.OpendsCertificateException;

import static org.opends.messages.DSConfigMessages.*;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.admin.client.AuthenticationException;
import org.opends.server.admin.client.AuthenticationNotSupportedException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.cli.DsFrameworkCliReturnCode;
import org.opends.server.admin.client.cli.SecureConnectionCliArgs;
import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;
import org.opends.server.admin.client.ldap.LDAPConnection;
import org.opends.server.admin.client.ldap.LDAPManagementContext;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.tools.ClientException;
import org.opends.server.util.args.Argument;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.SubCommandArgumentParser;
import org.opends.server.util.cli.LDAPConnectionConsoleInteraction;
import org.opends.server.util.cli.ConsoleApplication;

import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import java.util.LinkedHashSet;


/**
 * An LDAP management context factory.
 */
public final class LDAPManagementContextFactory implements
    ManagementContextFactory {

  // The SecureConnectionCliArgsList object.
  private SecureConnectionCliArgs secureArgsList = null;

  // The management context.
  private ManagementContext context = null;

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
      throws ArgumentException, ClientException
  {
    // Lazily create the LDAP management context.
    if (context == null)
    {
      // Interact with the user though the console to get
      // LDAP connection information
      LDAPConnectionConsoleInteraction ci =
              new LDAPConnectionConsoleInteraction(app, secureArgsList);
      ci.run();
      String hostName = ci.getHostName();
      Integer portNumber = ci.getPortNumber();
      String bindDN = ci.getBindDN();
      String bindPassword = ci.getBindPassword();
      TrustManager trustManager = ci.getTrustManager();
      KeyManager keyManager = ci.getKeyManager();

      // Do we have a secure connection ?
      LDAPConnection conn ;
      if (ci.useSSL())
      {
        InitialLdapContext ctx;
        String ldapsUrl = "ldaps://" + hostName + ":" + portNumber;
        while (true)
        {
          try
          {
            ctx = ConnectionUtils.createLdapsContext(ldapsUrl, bindDN,
                bindPassword, ConnectionUtils.getDefaultLDAPTimeout(), null,
                trustManager, keyManager);
            ctx.reconnect(null);
            conn = JNDIDirContextAdaptor.adapt(ctx);
            break;
          }
          catch (NamingException e)
          {
            if ( app.isInteractive() && ci.isTrustStoreInMemory())
            {
              if ((e.getRootCause() != null)
                  && (e.getRootCause().getCause()
                      instanceof OpendsCertificateException))
              {
                OpendsCertificateException oce =
                  (OpendsCertificateException) e.getRootCause().getCause();
                  if (ci.checkServerCertificate(oce.getChain()))
                  {
                    // If the certificate is trusted, update the trust manager.
                    trustManager = ci.getTrustManager();

                    // Try to connect again.
                    continue ;
                  }
              }
              else
              {
                Message message = ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT.get(
                    hostName, String.valueOf(portNumber));
                throw new ClientException(
                    LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR, message);
              }
            }
            Message message = ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT.get(
                hostName, String.valueOf(portNumber));
            throw new ClientException(
                LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR, message);
          }
        }
      }
      else if (ci.useStartTLS())
      {
        InitialLdapContext ctx;
        String ldapUrl = "ldap://" + hostName + ":" + portNumber;
        while (true)
        {
          try
          {
            ctx = ConnectionUtils.createStartTLSContext(ldapUrl, bindDN,
                bindPassword, ConnectionUtils.getDefaultLDAPTimeout(), null,
                trustManager, keyManager, null);
            ctx.reconnect(null);
            conn = JNDIDirContextAdaptor.adapt(ctx);
            break;
          }
          catch (NamingException e)
          {
            if ( app.isInteractive() && ci.isTrustStoreInMemory())
            {
              if ((e.getRootCause() != null)
                  && (e.getRootCause().getCause()
                      instanceof OpendsCertificateException))
              {
                OpendsCertificateException oce =
                  (OpendsCertificateException) e.getRootCause().getCause();
                  if (ci.checkServerCertificate(oce.getChain()))
                  {
                    // If the certificate is trusted, update the trust manager.
                    trustManager = ci.getTrustManager();

                    // Try to connect again.
                    continue ;
                  }
              }
              else
              {
                Message message = ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT.get(
                    hostName, String.valueOf(portNumber));
                throw new ClientException(
                    LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR, message);
              }
            }
            Message message = ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT.get(
                hostName, String.valueOf(portNumber));
            throw new ClientException(
                LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR, message);
          }
        }
      }
      else
      {
        // Create the management context.
        try
        {
          conn = JNDIDirContextAdaptor.simpleBind(hostName, portNumber,
              bindDN, bindPassword);
        }
        catch (AuthenticationNotSupportedException e)
        {
          Message message = ERR_DSCFG_ERROR_LDAP_SIMPLE_BIND_NOT_SUPPORTED
              .get();
          throw new ClientException(LDAPResultCode.AUTH_METHOD_NOT_SUPPORTED,
              message);
        }
        catch (AuthenticationException e)
        {
          Message message = ERR_DSCFG_ERROR_LDAP_SIMPLE_BIND_FAILED
              .get(bindDN);
          throw new ClientException(LDAPResultCode.INVALID_CREDENTIALS,
              message);
        }
        catch (CommunicationException e)
        {
          Message message = ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT.get(
              hostName, String.valueOf(portNumber));
          throw new ClientException(LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR,
              message);
        }
      }
      context = LDAPManagementContext.createFromContext(conn);
    }
    return context;
  }



  /**
   * {@inheritDoc}
   */
  public void registerGlobalArguments(SubCommandArgumentParser parser)
      throws ArgumentException {
    // Create the global arguments.
    secureArgsList = new SecureConnectionCliArgs();
    LinkedHashSet<Argument> args = secureArgsList.createGlobalArguments();


    // Register the global arguments.
    for (Argument arg : args)
    {
      parser.addGlobalArgument(arg);
    }

  }



  /**
   * {@inheritDoc}
   */
  public void validateGlobalArguments() throws ArgumentException {
    // Make sure that the user didn't specify any conflicting
    // arguments.
    MessageBuilder buf = new MessageBuilder();
    int v = secureArgsList.validateGlobalOptions(buf);
    if (v != DsFrameworkCliReturnCode.SUCCESSFUL_NOP.getReturnCode())
    {
      throw new ArgumentException(buf.toMessage());
    }
  }

}
