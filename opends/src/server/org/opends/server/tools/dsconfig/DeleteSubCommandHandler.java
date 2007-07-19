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



import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;

import java.io.PrintStream;
import java.util.List;

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
  public static DeleteSubCommandHandler create(SubCommandArgumentParser parser,
      ManagedObjectPath<?, ?> p, InstantiableRelationDefinition<?, ?> r)
      throws ArgumentException {
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
  public static DeleteSubCommandHandler create(SubCommandArgumentParser parser,
      ManagedObjectPath<?, ?> p, OptionalRelationDefinition<?, ?> r)
      throws ArgumentException {
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
  private DeleteSubCommandHandler(SubCommandArgumentParser parser,
      ManagedObjectPath<?, ?> p, RelationDefinition<?, ?> r,
      ManagedObjectPath<?, ?> c) throws ArgumentException {
    this.path = p;
    this.relation = r;

    // Create the sub-command.
    String name = "delete-" + r.getName();
    String ufpn = r.getChildDefinition().getUserFriendlyPluralName();
    int descriptionID = MSGID_DSCFG_DESCRIPTION_SUBCMD_DELETE;
    this.subCommand = new SubCommand(parser, name, false, 0, 0, null,
        descriptionID, ufpn);

    // Create the naming arguments.
    this.namingArgs = createNamingArgs(subCommand, c, false);

    // Create the --force argument which is used to force deletion.
    this.forceArgument = new BooleanArgument(OPTION_DSCFG_LONG_FORCE,
        OPTION_DSCFG_SHORT_FORCE, OPTION_DSCFG_LONG_FORCE,
        MSGID_DSCFG_DESCRIPTION_FORCE, ufpn);
    subCommand.addArgument(forceArgument);

    // Register the tags associated with the child managed objects.
    addTags(relation.getChildDefinition().getAllTags());
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
  public int run(DSConfig app, PrintStream out, PrintStream err)
      throws ArgumentException, ClientException {
    // Get the naming argument values.
    List<String> names = getNamingArgValues(namingArgs);

    // Delete the child managed object.
    ManagementContext context = app.getManagementContext();
    ManagedObject<?> parent = null;
    try {
      parent = getManagedObject(context, path, names);
    } catch (AuthorizationException e) {
      int msgID = MSGID_DSCFG_ERROR_DELETE_AUTHZ;
      String msg = getMessage(msgID, relation.getUserFriendlyName());
      throw new ClientException(LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS,
          msgID, msg);
    } catch (DefinitionDecodingException e) {
      int msgID = MSGID_DSCFG_ERROR_GET_PARENT_DDE;
      String ufn = path.getManagedObjectDefinition().getUserFriendlyName();
      String msg = getMessage(msgID, ufn, ufn, ufn);
      throw new ClientException(LDAPResultCode.OPERATIONS_ERROR, msgID, msg);
    } catch (ManagedObjectDecodingException e) {
      int msgID = MSGID_DSCFG_ERROR_GET_PARENT_MODE;
      String ufn = path.getManagedObjectDefinition().getUserFriendlyName();
      String msg = getMessage(msgID, ufn);
      throw new ClientException(LDAPResultCode.OPERATIONS_ERROR, msgID, msg);
    } catch (CommunicationException e) {
      int msgID = MSGID_DSCFG_ERROR_DELETE_CE;
      String msg = getMessage(msgID, relation.getUserFriendlyName(), e
          .getMessage());
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msgID,
          msg);
    } catch (ConcurrentModificationException e) {
      int msgID = MSGID_DSCFG_ERROR_DELETE_CME;
      String msg = getMessage(msgID, relation.getUserFriendlyName());
      throw new ClientException(LDAPResultCode.CONSTRAINT_VIOLATION, msgID,
          msg);
    } catch (ManagedObjectNotFoundException e) {
      // Ignore the error if the deletion is being forced.
      if (!forceArgument.isPresent()) {
        int msgID = MSGID_DSCFG_ERROR_GET_PARENT_MONFE;
        String ufn = path.getManagedObjectDefinition().getUserFriendlyName();
        String msg = getMessage(msgID, ufn);
        throw new ClientException(LDAPResultCode.NO_SUCH_OBJECT, msgID, msg);
      }
    }

    if (parent != null) {
      try {
        // Confirm deletion.
        String prompt = String.format(Messages.getString("delete.confirm"),
            relation.getUserFriendlyName());
        if (!app.confirmAction(prompt)) {
          // Output failure message.
          String msg = String.format(Messages.getString("delete.failed"),
              relation.getUserFriendlyName());
          app.displayVerboseMessage(msg);
          return 1;
        }

        if (relation instanceof InstantiableRelationDefinition) {
          InstantiableRelationDefinition<?, ?> irelation =
            (InstantiableRelationDefinition<?, ?>) relation;
          parent.removeChild(irelation, names.get(names.size() - 1));
        } else if (relation instanceof OptionalRelationDefinition) {
          OptionalRelationDefinition<?, ?> orelation =
            (OptionalRelationDefinition<?, ?>) relation;
          parent.removeChild(orelation);
        }
      } catch (AuthorizationException e) {
        int msgID = MSGID_DSCFG_ERROR_DELETE_AUTHZ;
        String msg = getMessage(msgID, relation.getUserFriendlyName());
        throw new ClientException(LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS,
            msgID, msg);
      } catch (OperationRejectedException e) {
        int msgID = MSGID_DSCFG_ERROR_DELETE_ORE;
        String msg = getMessage(msgID, relation.getUserFriendlyName(), e
            .getMessage());
        throw new ClientException(LDAPResultCode.CONSTRAINT_VIOLATION, msgID,
            msg);
      } catch (ManagedObjectNotFoundException e) {
        // Ignore the error if the deletion is being forced.
        if (!forceArgument.isPresent()) {
          int msgID = MSGID_DSCFG_ERROR_DELETE_MONFE;
          String msg = getMessage(msgID, relation.getUserFriendlyName());
          throw new ClientException(LDAPResultCode.NO_SUCH_OBJECT, msgID, msg);
        }
      } catch (ConcurrentModificationException e) {
        int msgID = MSGID_DSCFG_ERROR_DELETE_CME;
        String msg = getMessage(msgID, relation.getUserFriendlyName());
        throw new ClientException(LDAPResultCode.CONSTRAINT_VIOLATION, msgID,
            msg);
      } catch (CommunicationException e) {
        int msgID = MSGID_DSCFG_ERROR_DELETE_CE;
        String msg = getMessage(msgID, relation.getUserFriendlyName(), e
            .getMessage());
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN,
            msgID, msg);
      }
    }

    // Output success message.
    String msg = String.format(Messages.getString("delete.done"), relation
        .getUserFriendlyName());
    app.displayVerboseMessage(msg);

    return 0;
  }

}
