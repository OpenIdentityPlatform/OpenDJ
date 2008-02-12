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
package org.opends.server.tools.dsconfig;



import org.opends.server.admin.client.ManagementContext;
import org.opends.server.tools.ClientException;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.SubCommandArgumentParser;
import org.opends.server.util.cli.ConsoleApplication;



/**
 * A factory for retrieving the management context which should be
 * used by the dsconfig application.
 * <p>
 * Factory implementations are responsible for registering their
 * required global options during initialization.
 */
public interface ManagementContextFactory {

  /**
   * Gets the management context which sub-commands should use in
   * order to manage the directory server. Implementations can use the
   * application instance for retrieving passwords interactively.
   *
   * @param app
   *          The application instance.
   * @return Returns the management context which sub-commands should
   *         use in order to manage the directory server.
   * @throws ArgumentException
   *           If a management context related argument could not be
   *           parsed successfully.
   * @throws ClientException
   *           If the management context could not be created.
   */
  ManagementContext getManagementContext(ConsoleApplication app)
      throws ArgumentException, ClientException;


  /**
   * Closes this management context.
   */
  void close();


  /**
   * Initializes this management context factory using the provided
   * parser. The management context factory can register global
   * options with the parser if required.
   *
   * @param parser
   *          The application sub-command argument parser.
   * @throws ArgumentException
   *           If the factory failed to register its required global
   *           options.
   */
  void registerGlobalArguments(SubCommandArgumentParser parser)
      throws ArgumentException;



  /**
   * Validates any global arguments passed to the application.
   * Implementations of this method should check that the values
   * passed to their global arguments are valid and are not
   * incompatible with each other.
   *
   * @throws ArgumentException
   *           If the global arguments are invalid for some reason.
   */
  void validateGlobalArguments() throws ArgumentException;
}
