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



import static org.opends.messages.DSConfigMessages.*;

import java.util.List;

import org.opends.messages.Message;
import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.OptionalRelationDefinition;
import org.opends.server.admin.RelationDefinition;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ConcurrentModificationException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagedObjectDecodingException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.OperationRejectedException;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.tools.ClientException;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.args.SubCommand;
import org.opends.server.util.args.SubCommandArgumentParser;
import org.opends.server.util.cli.CLIException;
import org.opends.server.util.cli.ConsoleApplication;
import org.opends.server.util.cli.MenuResult;



/**
 * A sub-command handler which is used to delete existing managed
 * objects.
 * <p>
 * This sub-command implements the various delete-xxx sub-commands.
 */
final class DeleteSubCommandHandler extends SubCommandHandler {

  /**
   * The value for the long option force.
   */
  private static final String OPTION_DSCFG_LONG_FORCE = "force";

  /**
   * The value for the short option force.
   */
  private static final char OPTION_DSCFG_SHORT_FORCE = 'f';



  /**
   * Creates a new delete-xxx sub-command for an instantiable
   * relation.
   *
   * @param parser
   *          The sub-command argument parser.
   * @param p
   *          The parent managed object path.
   * @param r
   *          The instantiable relation.
   * @return Returns the new delete-xxx sub-command.
   * @throws ArgumentException
   *           If the sub-command could not be created successfully.
   */
  public static DeleteSubCommandHandler create(
      SubCommandArgumentParser parser, ManagedObjectPath<?, ?> p,
      InstantiableRelationDefinition<?, ?> r) throws ArgumentException {
    return new DeleteSubCommandHandler(parser, p, r, p.child(r, "DUMMY"));
  }



  /**
   * Creates a new delete-xxx sub-command for an optional relation.
   *
   * @param parser
   *          The sub-command argument parser.
   * @param p
   *          The parent managed object path.
   * @param r
   *          The optional relation.
   * @return Returns the new delete-xxx sub-command.
   * @throws ArgumentException
   *           If the sub-command could not be created successfully.
   */
  public static DeleteSubCommandHandler create(
      SubCommandArgumentParser parser, ManagedObjectPath<?, ?> p,
      OptionalRelationDefinition<?, ?> r) throws ArgumentException {
    return new DeleteSubCommandHandler(parser, p, r, p.child(r));
  }

  // The argument which should be used to force deletion.
  private final BooleanArgument forceArgument;

  // The sub-commands naming arguments.
  private final List<StringArgument> namingArgs;

  // The path of the managed object.
  private final ManagedObjectPath<?, ?> path;

  // The relation which references the managed
  // object to be deleted.
  private final RelationDefinition<?, ?> relation;

  // The sub-command associated with this handler.
  private final SubCommand subCommand;



  // Private constructor.
  private DeleteSubCommandHandler(
      SubCommandArgumentParser parser, ManagedObjectPath<?, ?> p,
      RelationDefinition<?, ?> r, ManagedObjectPath<?, ?> c)
      throws ArgumentException {
    this.path = p;
    this.relation = r;

    // Create the sub-command.
    String name = "delete-" + r.getName();
    Message ufpn = r.getChildDefinition().getUserFriendlyPluralName();
    Message description = INFO_DSCFG_DESCRIPTION_SUBCMD_DELETE.get(ufpn);
    this.subCommand = new SubCommand(parser, name, false, 0, 0, null,
        description);

    // Create the naming arguments.
    this.namingArgs = createNamingArgs(subCommand, c, false);

    // Create the --force argument which is used to force deletion.
    this.forceArgument = new BooleanArgument(OPTION_DSCFG_LONG_FORCE,
        OPTION_DSCFG_SHORT_FORCE, OPTION_DSCFG_LONG_FORCE,
        INFO_DSCFG_DESCRIPTION_FORCE.get(ufpn));
    subCommand.addArgument(forceArgument);

    // Register the tags associated with the child managed objects.
    addTags(relation.getChildDefinition().getAllTags());
  }



  /**
   * Gets the relation definition associated with the type of
   * component that this sub-command handles.
   *
   * @return Returns the relation definition associated with the type
   *         of component that this sub-command handles.
   */
  public RelationDefinition<?, ?> getRelationDefinition() {
    return relation;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public SubCommand getSubCommand() {
    return subCommand;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public MenuResult<Integer> run(ConsoleApplication app,
      ManagementContextFactory factory) throws ArgumentException,
      ClientException, CLIException {
    // Get the naming argument values.
    List<String> names = getNamingArgValues(app, namingArgs);

    // Delete the child managed object.
    ManagementContext context = factory.getManagementContext(app);
    MenuResult<ManagedObject<?>> result;
    try {
      result = getManagedObject(app, context, path, names);
    } catch (AuthorizationException e) {
      Message msg = ERR_DSCFG_ERROR_DELETE_AUTHZ.get(relation
          .getUserFriendlyName());
      throw new ClientException(LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS, msg);
    } catch (DefinitionDecodingException e) {
      Message ufn = path.getManagedObjectDefinition().getUserFriendlyName();
      Message msg = ERR_DSCFG_ERROR_GET_PARENT_DDE.get(ufn, ufn, ufn);
      throw new ClientException(LDAPResultCode.OPERATIONS_ERROR, msg);
    } catch (ManagedObjectDecodingException e) {
      Message ufn = path.getManagedObjectDefinition().getUserFriendlyName();
      Message msg = ERR_DSCFG_ERROR_GET_PARENT_MODE.get(ufn);
      throw new ClientException(LDAPResultCode.OPERATIONS_ERROR, msg, e);
    } catch (CommunicationException e) {
      Message msg = ERR_DSCFG_ERROR_DELETE_CE.get(relation
          .getUserFriendlyName(), e.getMessage());
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msg);
    } catch (ConcurrentModificationException e) {
      Message msg = ERR_DSCFG_ERROR_DELETE_CME.get(relation
          .getUserFriendlyName());
      throw new ClientException(LDAPResultCode.CONSTRAINT_VIOLATION, msg);
    } catch (ManagedObjectNotFoundException e) {
      // Ignore the error if the deletion is being forced.
      if (!forceArgument.isPresent()) {
        Message ufn = path.getManagedObjectDefinition().getUserFriendlyName();
        Message msg = ERR_DSCFG_ERROR_GET_PARENT_MONFE.get(ufn);
        throw new ClientException(LDAPResultCode.NO_SUCH_OBJECT, msg);
      } else {
        return MenuResult.success(0);
      }
    }

    if (result.isQuit()) {
      if (!app.isMenuDrivenMode()) {
        // User chose to cancel deletion.
        Message msg = INFO_DSCFG_CONFIRM_DELETE_FAIL.get(relation
            .getUserFriendlyName());
        app.printVerboseMessage(msg);
      }
      return MenuResult.quit();
    } else if (result.isCancel()) {
      // Must be menu driven, so no need for error message.
      return MenuResult.cancel();
    }

    ManagedObject<?> parent = result.getValue();
    try {
      if (relation instanceof InstantiableRelationDefinition) {
        InstantiableRelationDefinition<?, ?> irelation =
          (InstantiableRelationDefinition<?, ?>) relation;
        String childName = names.get(names.size() - 1);

        if (childName == null) {
          MenuResult<String> sresult =
            readChildName(app, parent, irelation, null);

          if (sresult.isQuit()) {
            if (!app.isMenuDrivenMode()) {
              // User chose to cancel deletion.
              Message msg = INFO_DSCFG_CONFIRM_DELETE_FAIL.get(relation
                  .getUserFriendlyName());
              app.printVerboseMessage(msg);
            }
            return MenuResult.quit();
          } else if (sresult.isCancel()) {
            // Must be menu driven, so no need for error message.
            return MenuResult.cancel();
          } else {
            childName = sresult.getValue();
          }
        }

        if (confirmDeletion(app)) {
          parent.removeChild(irelation, childName);
        } else {
          return MenuResult.cancel();
        }
      } else if (relation instanceof OptionalRelationDefinition) {
        OptionalRelationDefinition<?, ?> orelation =
          (OptionalRelationDefinition<?, ?>) relation;

        if (confirmDeletion(app)) {
          parent.removeChild(orelation);
        } else {
          return MenuResult.cancel();
        }
      }
    } catch (AuthorizationException e) {
      Message msg = ERR_DSCFG_ERROR_DELETE_AUTHZ.get(relation
          .getUserFriendlyName());
      throw new ClientException(LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS, msg);
    } catch (OperationRejectedException e) {
      Message msg;
      if (e.getMessages().size() == 1) {
        msg = ERR_DSCFG_ERROR_DELETE_ORE_SINGLE.get(relation
            .getUserFriendlyName(), e.getMessagesAsSingleMessage());
      } else {
        msg = ERR_DSCFG_ERROR_DELETE_ORE_PLURAL.get(relation
            .getUserFriendlyName(), e.getMessagesAsSingleMessage());
      }
      throw new ClientException(LDAPResultCode.CONSTRAINT_VIOLATION, msg);
    } catch (ManagedObjectNotFoundException e) {
      // Ignore the error if the deletion is being forced.
      if (!forceArgument.isPresent()) {
        Message msg = ERR_DSCFG_ERROR_DELETE_MONFE.get(relation
            .getUserFriendlyName());
        throw new ClientException(LDAPResultCode.NO_SUCH_OBJECT, msg);
      }
    } catch (ConcurrentModificationException e) {
      Message msg = ERR_DSCFG_ERROR_DELETE_CME.get(relation
          .getUserFriendlyName());
      throw new ClientException(LDAPResultCode.CONSTRAINT_VIOLATION, msg);
    } catch (CommunicationException e) {
      Message msg = ERR_DSCFG_ERROR_DELETE_CE.get(relation
          .getUserFriendlyName(), e.getMessage());
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msg);
    }

    // Output success message.
    Message msg = INFO_DSCFG_CONFIRM_DELETE_SUCCESS.get(relation
        .getUserFriendlyName());
    app.printVerboseMessage(msg);

    return MenuResult.success(0);
  }



  // Confirm deletion.
  private boolean confirmDeletion(ConsoleApplication app) throws CLIException {
    if (app.isInteractive()) {
      Message prompt = INFO_DSCFG_CONFIRM_DELETE.get(relation
          .getUserFriendlyName());
      app.println();
      if (!app.confirmAction(prompt, false)) {
        // Output failure message.
        Message msg = INFO_DSCFG_CONFIRM_DELETE_FAIL.get(relation
            .getUserFriendlyName());
        app.printVerboseMessage(msg);
        return false;
      }
    }

    return true;
  }

}
