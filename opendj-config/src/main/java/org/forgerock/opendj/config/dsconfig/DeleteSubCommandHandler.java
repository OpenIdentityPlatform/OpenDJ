/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config.dsconfig;

import static com.forgerock.opendj.cli.ReturnCode.*;
import static com.forgerock.opendj.dsconfig.DsconfigMessages.*;

import java.util.List;
import java.util.SortedMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.DefinitionDecodingException;
import org.forgerock.opendj.config.InstantiableRelationDefinition;
import org.forgerock.opendj.config.ManagedObjectDefinition;
import org.forgerock.opendj.config.ManagedObjectNotFoundException;
import org.forgerock.opendj.config.ManagedObjectPath;
import org.forgerock.opendj.config.OptionalRelationDefinition;
import org.forgerock.opendj.config.RelationDefinition;
import org.forgerock.opendj.config.SetRelationDefinition;
import org.forgerock.opendj.config.client.ConcurrentModificationException;
import org.forgerock.opendj.config.client.ManagedObject;
import org.forgerock.opendj.config.client.ManagedObjectDecodingException;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.config.client.OperationRejectedException;
import org.forgerock.opendj.ldap.AuthorizationException;
import org.forgerock.opendj.ldap.LdapException;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.MenuResult;
import com.forgerock.opendj.cli.ReturnCode;
import com.forgerock.opendj.cli.StringArgument;
import com.forgerock.opendj.cli.SubCommand;
import com.forgerock.opendj.cli.SubCommandArgumentParser;
import com.forgerock.opendj.cli.TableBuilder;
import com.forgerock.opendj.cli.TextTablePrinter;

import static org.forgerock.opendj.config.dsconfig.DSConfig.*;

/**
 * A sub-command handler which is used to delete existing managed objects.
 * <p>
 * This sub-command implements the various delete-xxx sub-commands.
 */
final class DeleteSubCommandHandler extends SubCommandHandler {

    /** The value for the long option force. */
    private static final String OPTION_DSCFG_LONG_FORCE = "force";

    /** The value for the short option force. */
    private static final char OPTION_DSCFG_SHORT_FORCE = 'f';

    /**
     * Creates a new delete-xxx sub-command for an instantiable relation.
     *
     * @param parser
     *            The sub-command argument parser.
     * @param p
     *            The parent managed object path.
     * @param r
     *            The instantiable relation.
     * @return Returns the new delete-xxx sub-command.
     * @throws ArgumentException
     *             If the sub-command could not be created successfully.
     */
    public static DeleteSubCommandHandler create(SubCommandArgumentParser parser, ManagedObjectPath<?, ?> p,
            InstantiableRelationDefinition<?, ?> r) throws ArgumentException {
        return new DeleteSubCommandHandler(parser, p, r, p.child(r, "DUMMY"));
    }

    /**
     * Creates a new delete-xxx sub-command for an optional relation.
     *
     * @param parser
     *            The sub-command argument parser.
     * @param p
     *            The parent managed object path.
     * @param r
     *            The optional relation.
     * @return Returns the new delete-xxx sub-command.
     * @throws ArgumentException
     *             If the sub-command could not be created successfully.
     */
    public static DeleteSubCommandHandler create(SubCommandArgumentParser parser, ManagedObjectPath<?, ?> p,
            OptionalRelationDefinition<?, ?> r) throws ArgumentException {
        return new DeleteSubCommandHandler(parser, p, r, p.child(r));
    }

    /**
     * Creates a new delete-xxx sub-command for a set relation.
     *
     * @param parser
     *            The sub-command argument parser.
     * @param p
     *            The parent managed object path.
     * @param r
     *            The set relation.
     * @return Returns the new delete-xxx sub-command.
     * @throws ArgumentException
     *             If the sub-command could not be created successfully.
     */
    public static DeleteSubCommandHandler create(SubCommandArgumentParser parser, ManagedObjectPath<?, ?> p,
            SetRelationDefinition<?, ?> r) throws ArgumentException {
        return new DeleteSubCommandHandler(parser, p, r, p.child(r));
    }

    /** The argument which should be used to force deletion. */
    private final BooleanArgument forceArgument;

    /** The sub-commands naming arguments. */
    private final List<StringArgument> namingArgs;

    /** The path of the managed object. */
    private final ManagedObjectPath<?, ?> path;

    /** The relation which references the managed object to be deleted. */
    private final RelationDefinition<?, ?> relation;

    /** The sub-command associated with this handler. */
    private final SubCommand subCommand;

    /** Private constructor. */
    private DeleteSubCommandHandler(SubCommandArgumentParser parser, ManagedObjectPath<?, ?> p,
            RelationDefinition<?, ?> r, ManagedObjectPath<?, ?> c) throws ArgumentException {
        this.path = p;
        this.relation = r;

        // Create the sub-command.
        String name = "delete-" + r.getName();
        LocalizableMessage ufpn = r.getChildDefinition().getUserFriendlyPluralName();
        LocalizableMessage description = INFO_DSCFG_DESCRIPTION_SUBCMD_DELETE.get(ufpn);
        this.subCommand = new SubCommand(parser, name, false, 0, 0, null, description);

        // Create the naming arguments.
        this.namingArgs = createNamingArgs(subCommand, c, false);

        // Create the --force argument which is used to force deletion.
        this.forceArgument =
                BooleanArgument.builder(OPTION_DSCFG_LONG_FORCE)
                        .shortIdentifier(OPTION_DSCFG_SHORT_FORCE)
                        .description(INFO_DSCFG_DESCRIPTION_FORCE.get(ufpn))
                        .buildAndAddToSubCommand(subCommand);

        // Register the tags associated with the child managed objects.
        addTags(relation.getChildDefinition().getAllTags());
    }

    /**
     * Gets the relation definition associated with the type of component that this sub-command handles.
     *
     * @return Returns the relation definition associated with the type of component that this sub-command handles.
     */
    public RelationDefinition<?, ?> getRelationDefinition() {
        return relation;
    }

    @Override
    public SubCommand getSubCommand() {
        return subCommand;
    }

    @Override
    public MenuResult<Integer> run(ConsoleApplication app, LDAPManagementContextFactory factory)
            throws ArgumentException, ClientException {
        // Get the naming argument values.
        List<String> names = getNamingArgValues(app, namingArgs);

        // Reset the command builder
        getCommandBuilder().clearArguments();
        setCommandBuilderUseful(false);

        // Delete the child managed object.
        ManagementContext context = factory.getManagementContext();
        MenuResult<ManagedObject<?>> result;
        LocalizableMessage ufn = relation.getUserFriendlyName();
        try {
            result = getManagedObject(app, context, path, names);
        } catch (AuthorizationException e) {
            LocalizableMessage msg = ERR_DSCFG_ERROR_DELETE_AUTHZ.get(ufn);
            throw new ClientException(ReturnCode.INSUFFICIENT_ACCESS_RIGHTS, msg);
        } catch (DefinitionDecodingException e) {
            LocalizableMessage pufn = path.getManagedObjectDefinition().getUserFriendlyName();
            LocalizableMessage msg = ERR_DSCFG_ERROR_GET_PARENT_DDE.get(pufn, pufn, pufn);
            throw new ClientException(ReturnCode.OTHER, msg);
        } catch (ManagedObjectDecodingException e) {
            LocalizableMessage pufn = path.getManagedObjectDefinition().getUserFriendlyName();
            LocalizableMessage msg = ERR_DSCFG_ERROR_GET_PARENT_MODE.get(pufn);
            throw new ClientException(ReturnCode.OTHER, msg, e);
        } catch (ConcurrentModificationException e) {
            LocalizableMessage msg = ERR_DSCFG_ERROR_DELETE_CME.get(ufn);
            throw new ClientException(ReturnCode.CONSTRAINT_VIOLATION, msg);
        } catch (ManagedObjectNotFoundException e) {
            // Ignore the error if the deletion is being forced.
            if (!forceArgument.isPresent()) {
                LocalizableMessage pufn = path.getManagedObjectDefinition().getUserFriendlyName();
                LocalizableMessage msg = ERR_DSCFG_ERROR_GET_PARENT_MONFE.get(pufn);
                return interactivePrintOrThrowError(app, msg, NO_SUCH_OBJECT);
            } else {
                return MenuResult.success(0);
            }
        } catch (LdapException e) {
            throw new ClientException(ReturnCode.OTHER, LocalizableMessage.raw(e.getLocalizedMessage()));
        }

        if (result.isQuit()) {
            if (!app.isMenuDrivenMode()) {
                // User chose to cancel deletion.
                app.println();
                app.println(INFO_DSCFG_CONFIRM_DELETE_FAIL.get(ufn));
            }
            return MenuResult.quit();
        } else if (result.isCancel()) {
            // Must be menu driven, so no need for error message.
            return MenuResult.cancel();
        }

        ManagedObject<?> parent = result.getValue();
        try {
            if (relation instanceof InstantiableRelationDefinition || relation instanceof SetRelationDefinition) {
                String childName = names.get(names.size() - 1);

                if (childName == null) {
                    MenuResult<String> sresult = readChildName(app, parent, relation, null);

                    if (sresult.isQuit()) {
                        if (!app.isMenuDrivenMode()) {
                            // User chose to cancel deletion.
                            app.println();
                            app.println(INFO_DSCFG_CONFIRM_DELETE_FAIL.get(ufn));
                        }
                        return MenuResult.quit();
                    } else if (sresult.isCancel()) {
                        // Must be menu driven, so no need for error message.
                        return MenuResult.cancel();
                    } else {
                        childName = sresult.getValue();
                    }
                } else if (relation instanceof SetRelationDefinition) {
                    // The provided type short name needs mapping to the full name.
                    String name = childName.trim();
                    SortedMap<?, ?> types = getSubTypes(relation.getChildDefinition());
                    ManagedObjectDefinition<?, ?> cd = (ManagedObjectDefinition<?, ?>) types.get(name);
                    if (cd == null) {
                        // The name must be invalid.
                        String typeUsage = getSubTypesUsage(relation.getChildDefinition());
                        LocalizableMessage msg = ERR_DSCFG_ERROR_SUB_TYPE_UNRECOGNIZED.get(name,
                                relation.getUserFriendlyName(), typeUsage);
                        throw new ArgumentException(msg);
                    } else {
                        childName = cd.getName();
                    }
                }

                if (confirmDeletion(app)) {
                    setCommandBuilderUseful(true);
                    if (relation instanceof InstantiableRelationDefinition) {
                        parent.removeChild((InstantiableRelationDefinition<?, ?>) relation, childName);
                    } else {
                        parent.removeChild((SetRelationDefinition<?, ?>) relation, childName);
                    }
                } else {
                    return MenuResult.cancel();
                }
            } else if (relation instanceof OptionalRelationDefinition) {
                OptionalRelationDefinition<?, ?> orelation = (OptionalRelationDefinition<?, ?>) relation;

                if (confirmDeletion(app)) {
                    setCommandBuilderUseful(true);

                    parent.removeChild(orelation);
                } else {
                    return MenuResult.cancel();
                }
            }
        } catch (AuthorizationException e) {
            LocalizableMessage msg = ERR_DSCFG_ERROR_DELETE_AUTHZ.get(ufn);
            throw new ClientException(ReturnCode.INSUFFICIENT_ACCESS_RIGHTS, msg);
        } catch (OperationRejectedException e) {
            LocalizableMessage msg = e.getMessages().size() == 1 ? ERR_DSCFG_ERROR_DELETE_ORE_SINGLE.get(ufn)
                                                                 : ERR_DSCFG_ERROR_DELETE_ORE_PLURAL.get(ufn);

            if (app.isInteractive()) {
                // If interactive, let the user go back to the main menu.
                app.errPrintln();
                app.errPrintln(msg);
                app.errPrintln();
                TableBuilder builder = new TableBuilder();
                for (LocalizableMessage reason : e.getMessages()) {
                    builder.startRow();
                    builder.appendCell("*");
                    builder.appendCell(reason);
                }
                TextTablePrinter printer = new TextTablePrinter(app.getErrorStream());
                printer.setDisplayHeadings(false);
                printer.setColumnWidth(1, 0);
                printer.setIndentWidth(4);
                builder.print(printer);
                return MenuResult.cancel();
            } else {
                throw new ClientException(ReturnCode.CONSTRAINT_VIOLATION, msg, e);
            }
        } catch (ManagedObjectNotFoundException e) {
            // Ignore the error if the deletion is being forced.
            if (!forceArgument.isPresent()) {
                LocalizableMessage msg = ERR_DSCFG_ERROR_DELETE_MONFE.get(ufn);
                throw new ClientException(ReturnCode.NO_SUCH_OBJECT, msg);
            }
        } catch (ConcurrentModificationException e) {
            LocalizableMessage msg = ERR_DSCFG_ERROR_DELETE_CME.get(ufn);
            throw new ClientException(ReturnCode.CONSTRAINT_VIOLATION, msg);
        } catch (LdapException e) {
            LocalizableMessage msg = ERR_DSCFG_ERROR_DELETE_CE.get(ufn, e.getMessage());
            throw new ClientException(ReturnCode.CLIENT_SIDE_SERVER_DOWN, msg);
        }

        // Add the naming arguments if they were provided.
        for (StringArgument arg : namingArgs) {
            if (arg.isPresent()) {
                getCommandBuilder().addArgument(arg);
            }
        }

        // Output success message.
        app.println();
        app.println(INFO_DSCFG_CONFIRM_DELETE_SUCCESS.get(ufn));

        return MenuResult.success(0);
    }

    /** Confirm deletion. */
    private boolean confirmDeletion(ConsoleApplication app) throws ClientException {
        if (app.isInteractive()) {
            app.println();
            if (!app.confirmAction(INFO_DSCFG_CONFIRM_DELETE.get(relation.getUserFriendlyName()), false)) {
                app.errPrintln(INFO_DSCFG_CONFIRM_DELETE_FAIL.get(relation.getUserFriendlyName()));
                return false;
            }
        }

        return true;
    }

}
