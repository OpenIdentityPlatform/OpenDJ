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
 *      Copyright 2007-2009 Sun Microsystems, Inc.
 */
package org.opends.server.tools.dsconfig;



import org.opends.server.admin.client.ManagementContext;
import org.opends.server.tools.ClientException;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.SubCommandArgumentParser;
import org.opends.server.util.cli.CommandBuilder;
import org.opends.server.util.cli.ConsoleApplication;



/**
 * A management context factory which uses a pre-defined management
 * context.
 */
public final class InternalManagementContextFactory implements
    ManagementContextFactory {

  // The pre-defined management context.
  private final ManagementContext context;



  /**
   * Creates a new internal management context factory using the
   * provided management context.
   *
   * @param context
   *          The management context.
   */
  public InternalManagementContextFactory(ManagementContext context) {
    this.context = context;
  }

  /**
   * {@inheritDoc}
   */
  public void close()
  {
    // No implementation required.
    // We let the user of this InternalManagementContextFactory close
    // his/her context.
  }

  /**
   * {@inheritDoc}
   */
  public ManagementContext getManagementContext(ConsoleApplication app)
      throws ArgumentException, ClientException {
    return context;
  }



  /**
   * {@inheritDoc}
   */
  public void registerGlobalArguments(SubCommandArgumentParser parser)
      throws ArgumentException {
    // No implementation required.
  }



  /**
   * {@inheritDoc}
   */
  public void validateGlobalArguments() throws ArgumentException {
    // No implementation required.
  }

  /**
   * {@inheritDoc}
   */
  public CommandBuilder getContextCommandBuilder() {
    // No implementation required.
    return new CommandBuilder(null, null);
  }

  /**
   * {@inheritDoc}
   */
  public void setRawArguments(String[] args) {
    // No implementation required.
  }

}
