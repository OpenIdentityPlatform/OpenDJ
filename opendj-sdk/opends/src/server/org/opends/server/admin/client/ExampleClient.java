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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.server.admin.client;



import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.util.ServerConstants.PROPERTY_SERVER_ROOT;

import java.io.File;

import org.opends.server.admin.ClassLoaderProvider;
import org.opends.server.admin.AttributeTypePropertyDefinition;
import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.client.ldap.LDAPManagementContext;
import org.opends.server.admin.std.client.ConnectionHandlerCfgClient;
import org.opends.server.admin.std.client.GlobalCfgClient;
import org.opends.server.admin.std.client.LDAPConnectionHandlerCfgClient;
import org.opends.server.admin.std.client.RootCfgClient;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.types.InitializationException;



/**
 * Sample client code.
 * <p>
 * FIXME: should the configuration client managed object interfaces
 * expose a commit() method?
 * <p>
 * FIXME: should the commit() method take a context parameter or
 * should it be stored inside the managed object.
 * <p>
 * FIXME: should managed objects store their name internally?
 */
public final class ExampleClient {

  /**
   * Private constructor.
   */
  private ExampleClient() {
    // No implementation required.
  }



  /**
   * Boot strap config and schema.
   *
   * @throws InitializationException
   *           If the directory server infrastructure could not be
   *           initialized.
   * @throws ConfigException
   *           If a configuration problem was encountered.
   */
  private void initialize() throws InitializationException,
      ConfigException {
    // Make sure a new instance is created.
    //
    // This is effectively a no-op at the moment, but may do lazy
    // initialization at some point.
    DirectoryServer.getInstance();

    // Initialize minimal features such as key syntaxes.
    DirectoryServer.bootstrapClient();

    // Many things are dependent on JMX to register an alert
    // generator.
    DirectoryServer.initializeJMX();

    removeAllErrorLogPublishers();

    // Initialize the configuration.
    File instanceRoot = new File("build/package/OpenDS-0.1");
    File configResourceDirectory = new File(instanceRoot, "config");
    System.setProperty(PROPERTY_SERVER_ROOT, instanceRoot
        .getAbsolutePath());
    File configFile = new File(configResourceDirectory, "config.ldif");

    DirectoryServer directoryServer = DirectoryServer.getInstance();

    // Bootstrap definition classes.
    ClassLoaderProvider.getInstance().enable();

    directoryServer.initializeConfiguration(ConfigFileHandler.class
        .getName(), configFile.getAbsolutePath());

    // Initialize and load the schema files.
    DirectoryServer.getInstance().initializeSchema();

    // Switch off class name validation in client.
    ClassPropertyDefinition.setAllowClassValidation(false);

    // Switch off attribute type name validation in client.
    AttributeTypePropertyDefinition.setCheckSchema(false);
  }



  /**
   * Perform the client operations.
   */
  private void run() throws Exception {
    ManagementContext ctx = LDAPManagementContext.createLDAPContext(
        "localhost", 1389, "cn=directory manager", "password");

    RootCfgClient root = ctx.getRootConfiguration();

    // Modify global server property.
    GlobalCfgClient server = root.getGlobalConfiguration();
    System.out.println("Server check-schema is "
        + server.isCheckSchema());
    System.out.println("Changing to false and re-reading...");
    server.setCheckSchema(false);
    server.commit();

    server = root.getGlobalConfiguration();
    System.out.println("Server check-schema is "
        + server.isCheckSchema());
    System.out.println("Changing to true and re-reading...");
    server.setCheckSchema(true);
    server.commit();

    server = root.getGlobalConfiguration();
    System.out.println("Server check-schema is "
        + server.isCheckSchema());

    // List connection handlers.
    System.out.println("Listing connection handlers...");
    String[] names = root.listConnectionHandlers();
    for (String name : names) {
      ConnectionHandlerCfgClient handler = root
          .getConnectionHandler(name);

      System.out.println("Got type=" + handler.definition().getName()
          + ", name=" + name);
      System.out.println("Connection handler is-enabled is "
          + handler.isEnabled());

      if (handler instanceof LDAPConnectionHandlerCfgClient) {
        LDAPConnectionHandlerCfgClient lhandler =
          (LDAPConnectionHandlerCfgClient) handler;
        System.out.println("LDAP Connection handler listen-port is "
            + lhandler.getListenPort());
      }
    }
  }



  /**
   * Sample client application.
   *
   * @param args
   *          CLI arguments.
   * @throws Exception
   *           If a problem occurred.
   */
  public static void main(String[] args) throws Exception {
    ExampleClient client = new ExampleClient();

    client.initialize();
    client.run();
  }

}
