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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config.dsconfig;

import java.io.PrintStream;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.DefinitionDecodingException;
import org.forgerock.opendj.config.InstantiableRelationDefinition;
import org.forgerock.opendj.config.ManagedObjectDefinition;
import org.forgerock.opendj.config.ManagedObjectNotFoundException;
import org.forgerock.opendj.config.ManagedObjectPath;
import org.forgerock.opendj.config.OptionalRelationDefinition;
import org.forgerock.opendj.config.PropertyDefinition;
import org.forgerock.opendj.config.RelationDefinition;
import org.forgerock.opendj.config.SetRelationDefinition;
import org.forgerock.opendj.config.client.ConcurrentModificationException;
import org.forgerock.opendj.config.client.ManagedObject;
import org.forgerock.opendj.config.client.ManagedObjectDecodingException;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.ldap.AuthorizationException;
import org.forgerock.opendj.ldap.LdapException;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.MenuResult;
import com.forgerock.opendj.cli.ReturnCode;
import com.forgerock.opendj.cli.StringArgument;
import com.forgerock.opendj.cli.SubCommand;
import com.forgerock.opendj.cli.SubCommandArgumentParser;
import com.forgerock.opendj.cli.TableBuilder;
import com.forgerock.opendj.cli.TablePrinter;
import com.forgerock.opendj.cli.TextTablePrinter;

import static org.forgerock.opendj.config.ManagedObjectOption.*;
import static org.forgerock.opendj.config.dsconfig.DSConfig.*;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.ReturnCode.*;
import static com.forgerock.opendj.dsconfig.DsconfigMessages.*;

/**
 * A sub-command handler which is used to list existing managed objects.
 * <p>
 * This sub-command implements the various list-xxx sub-commands.
 */
final class ListSubCommandHandler extends SubCommandHandler {

    /**
     * Creates a new list-xxx sub-command for an instantiable relation.
     *
     * @param parser
     *            The sub-command argument parser.
     * @param p
     *            The parent managed object path.
     * @param r
     *            The instantiable relation.
     * @return Returns the new list-xxx sub-command.
     * @throws ArgumentException
     *             If the sub-command could not be created successfully.
     */
    public static ListSubCommandHandler create(SubCommandArgumentParser parser, ManagedObjectPath<?, ?> p,
            InstantiableRelationDefinition<?, ?> r) throws ArgumentException {
        return new ListSubCommandHandler(parser, p, r, r.getPluralName(), r.getUserFriendlyPluralName());
    }

    /**
     * Creates a new list-xxx sub-command for a set relation.
     *
     * @param parser
     *            The sub-command argument parser.
     * @param p
     *            The parent managed object path.
     * @param r
     *            The set relation.
     * @return Returns the new list-xxx sub-command.
     * @throws ArgumentException
     *             If the sub-command could not be created successfully.
     */
    public static ListSubCommandHandler create(SubCommandArgumentParser parser, ManagedObjectPath<?, ?> p,
            SetRelationDefinition<?, ?> r) throws ArgumentException {
        return new ListSubCommandHandler(parser, p, r, r.getPluralName(), r.getUserFriendlyPluralName());
    }

    /**
     * Creates a new list-xxx sub-command for an optional relation.
     *
     * @param parser
     *            The sub-command argument parser.
     * @param p
     *            The parent managed object path.
     * @param r
     *            The optional relation.
     * @return Returns the new list-xxx sub-command.
     * @throws ArgumentException
     *             If the sub-command could not be created successfully.
     */
    public static ListSubCommandHandler create(SubCommandArgumentParser parser, ManagedObjectPath<?, ?> p,
            OptionalRelationDefinition<?, ?> r) throws ArgumentException {
        return new ListSubCommandHandler(parser, p, r, r.getName(), r.getUserFriendlyName());
    }

    /** The sub-commands naming arguments. */
    private final List<StringArgument> namingArgs;

    /** The path of the parent managed object. */
    private final ManagedObjectPath<?, ?> path;

    /** The relation which should be listed. */
    private final RelationDefinition<?, ?> relation;

    /** The sub-command associated with this handler. */
    private final SubCommand subCommand;

    /** Private constructor. */
    private ListSubCommandHandler(SubCommandArgumentParser parser, ManagedObjectPath<?, ?> p,
            RelationDefinition<?, ?> r, String rname, LocalizableMessage rufn) throws ArgumentException {
        path = p;
        relation = r;

        // Create the sub-command.
        subCommand = new SubCommand(parser, "list-" + rname, false, 0, 0, null,
                INFO_DSCFG_DESCRIPTION_SUBCMD_LIST.get(rufn));

        // Create the naming arguments.
        namingArgs = createNamingArgs(subCommand, path, false);

        // Register arguments.
        registerPropertyNameArgument(subCommand);
        registerUnitSizeArgument(subCommand);
        registerUnitTimeArgument(subCommand);

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
        // Get the property names.
        Set<String> propertyNames = getPropertyNames();

        // Reset the command builder
        getCommandBuilder().clearArguments();

        // Update the command builder.
        updateCommandBuilderWithSubCommand();

        if (propertyNames.isEmpty()) {
            // Use a default set of properties.
            propertyNames = CLIProfile.getInstance().getDefaultListPropertyNames(relation);
        }

        PropertyValuePrinter valuePrinter = new PropertyValuePrinter(getSizeUnit(), getTimeUnit(),
                app.isScriptFriendly());

        // Get the naming argument values.
        List<String> names = getNamingArgValues(app, namingArgs);

        LocalizableMessage ufn;
        if (relation instanceof InstantiableRelationDefinition) {
            InstantiableRelationDefinition<?, ?> irelation = (InstantiableRelationDefinition<?, ?>) relation;
            ufn = irelation.getUserFriendlyPluralName();
        } else if (relation instanceof SetRelationDefinition) {
            SetRelationDefinition<?, ?> srelation = (SetRelationDefinition<?, ?>) relation;
            ufn = srelation.getUserFriendlyPluralName();
        } else {
            ufn = relation.getUserFriendlyName();
        }

        // List the children.
        ManagementContext context = factory.getManagementContext();
        MenuResult<ManagedObject<?>> result;
        try {
            result = getManagedObject(app, context, path, names);
        } catch (AuthorizationException e) {
            LocalizableMessage msg = ERR_DSCFG_ERROR_LIST_AUTHZ.get(ufn);
            throw new ClientException(ReturnCode.INSUFFICIENT_ACCESS_RIGHTS, msg);
        } catch (DefinitionDecodingException e) {
            ufn = path.getManagedObjectDefinition().getUserFriendlyName();
            LocalizableMessage msg = ERR_DSCFG_ERROR_GET_PARENT_DDE.get(ufn, ufn, ufn);
            throw new ClientException(ReturnCode.OTHER, msg);
        } catch (ManagedObjectDecodingException e) {
            ufn = path.getManagedObjectDefinition().getUserFriendlyName();
            LocalizableMessage msg = ERR_DSCFG_ERROR_GET_PARENT_MODE.get(ufn);
            throw new ClientException(ReturnCode.OTHER, msg, e);
        } catch (ConcurrentModificationException e) {
            LocalizableMessage msg = ERR_DSCFG_ERROR_LIST_CME.get(ufn);
            throw new ClientException(ReturnCode.CONSTRAINT_VIOLATION, msg);
        } catch (ManagedObjectNotFoundException e) {
            ufn = path.getManagedObjectDefinition().getUserFriendlyName();
            LocalizableMessage msg = ERR_DSCFG_ERROR_GET_PARENT_MONFE.get(ufn);
            return interactivePrintOrThrowError(app, msg, NO_SUCH_OBJECT);
        } catch (LdapException e) {
            throw new ClientException(ReturnCode.OTHER, LocalizableMessage.raw(e.getLocalizedMessage()));
        }

        if (result.isQuit()) {
            return MenuResult.quit();
        } else if (result.isCancel()) {
            return MenuResult.cancel();
        }

        ManagedObject<?> parent = result.getValue();
        SortedMap<String, ManagedObject<?>> children = new TreeMap<>();
        if (relation instanceof InstantiableRelationDefinition) {
            InstantiableRelationDefinition<?, ?> irelation = (InstantiableRelationDefinition<?, ?>) relation;
            try {
                for (String s : parent.listChildren(irelation)) {
                    try {
                        children.put(s, parent.getChild(irelation, s));
                    } catch (ManagedObjectNotFoundException e) {
                        // Ignore - as it's been removed since we did the list.
                    }
                }
            } catch (DefinitionDecodingException e) {
                // FIXME: just output this as a warnings (incl. the name) but
                // continue.
                LocalizableMessage msg = ERR_DSCFG_ERROR_LIST_DDE.get(ufn, ufn, ufn);
                throw new ClientException(ReturnCode.OTHER, msg);
            } catch (ManagedObjectDecodingException e) {
                // FIXME: just output this as a warnings (incl. the name) but
                // continue.
                LocalizableMessage msg = ERR_DSCFG_ERROR_LIST_MODE.get(ufn);
                throw new ClientException(ReturnCode.OTHER, msg, e);
            } catch (AuthorizationException e) {
                LocalizableMessage msg = ERR_DSCFG_ERROR_LIST_AUTHZ.get(ufn);
                throw new ClientException(ReturnCode.INSUFFICIENT_ACCESS_RIGHTS, msg);
            } catch (ConcurrentModificationException e) {
                LocalizableMessage msg = ERR_DSCFG_ERROR_LIST_CME.get(ufn);
                throw new ClientException(ReturnCode.CONSTRAINT_VIOLATION, msg);
            } catch (LdapException e) {
                LocalizableMessage msg = ERR_DSCFG_ERROR_LIST_CE.get(ufn, e.getMessage());
                throw new ClientException(ReturnCode.CLIENT_SIDE_SERVER_DOWN, msg);
            }
        } else if (relation instanceof SetRelationDefinition) {
            SetRelationDefinition<?, ?> srelation = (SetRelationDefinition<?, ?>) relation;
            try {
                for (String s : parent.listChildren(srelation)) {
                    try {
                        children.put(s, parent.getChild(srelation, s));
                    } catch (ManagedObjectNotFoundException e) {
                        // Ignore - as it's been removed since we did the list.
                    }
                }
            } catch (DefinitionDecodingException e) {
                // FIXME: just output this as a warnings (incl. the name) but
                // continue.
                LocalizableMessage msg = ERR_DSCFG_ERROR_LIST_DDE.get(ufn, ufn, ufn);
                throw new ClientException(ReturnCode.OTHER, msg);
            } catch (ManagedObjectDecodingException e) {
                // FIXME: just output this as a warnings (incl. the name) but
                // continue.
                LocalizableMessage msg = ERR_DSCFG_ERROR_LIST_MODE.get(ufn);
                throw new ClientException(ReturnCode.OTHER, msg, e);
            } catch (AuthorizationException e) {
                LocalizableMessage msg = ERR_DSCFG_ERROR_LIST_AUTHZ.get(ufn);
                throw new ClientException(ReturnCode.INSUFFICIENT_ACCESS_RIGHTS, msg);
            } catch (ConcurrentModificationException e) {
                LocalizableMessage msg = ERR_DSCFG_ERROR_LIST_CME.get(ufn);
                throw new ClientException(ReturnCode.CONSTRAINT_VIOLATION, msg);
            } catch (LdapException e) {
                LocalizableMessage msg = ERR_DSCFG_ERROR_LIST_CE.get(ufn, e.getMessage());
                throw new ClientException(ReturnCode.CLIENT_SIDE_SERVER_DOWN, msg);
            }
        } else if (relation instanceof OptionalRelationDefinition) {
            OptionalRelationDefinition<?, ?> orelation = (OptionalRelationDefinition<?, ?>) relation;
            try {
                if (parent.hasChild(orelation)) {
                    ManagedObject<?> child = parent.getChild(orelation);
                    children.put(child.getManagedObjectDefinition().getName(), child);
                } else {
                    // Indicate that the managed object does not exist.
                    return interactivePrintOrThrowError(
                        app, ERR_DSCFG_ERROR_FINDER_NO_CHILDREN.get(ufn), NO_SUCH_OBJECT);
                }
            } catch (AuthorizationException e) {
                LocalizableMessage msg = ERR_DSCFG_ERROR_LIST_AUTHZ.get(ufn);
                throw new ClientException(ReturnCode.INSUFFICIENT_ACCESS_RIGHTS, msg);
            } catch (DefinitionDecodingException e) {
                LocalizableMessage msg = ERR_DSCFG_ERROR_LIST_DDE.get(ufn, ufn, ufn);
                throw new ClientException(ReturnCode.OTHER, msg);
            } catch (ManagedObjectDecodingException e) {
                LocalizableMessage msg = ERR_DSCFG_ERROR_LIST_MODE.get(ufn);
                throw new ClientException(ReturnCode.OTHER, msg, e);
            } catch (ConcurrentModificationException e) {
                LocalizableMessage msg = ERR_DSCFG_ERROR_LIST_CME.get(ufn);
                throw new ClientException(ReturnCode.CONSTRAINT_VIOLATION, msg);
            } catch (LdapException e) {
                LocalizableMessage msg = ERR_DSCFG_ERROR_LIST_CE.get(ufn, e.getMessage());
                throw new ClientException(ReturnCode.CLIENT_SIDE_SERVER_DOWN, msg);
            } catch (ManagedObjectNotFoundException e) {
                LocalizableMessage msg = ERR_DSCFG_ERROR_LIST_MONFE.get(ufn);
                throw new ClientException(ReturnCode.NO_SUCH_OBJECT, msg);
            }
        }

        // Output the results.
        if (app.isScriptFriendly()) {
            // Output just the names of the children.
            for (String name : children.keySet()) {
                ManagedObjectDefinition<?, ?> d = children.get(name).getManagedObjectDefinition();
                if (!cannotDisplay(app, d)) {
                    app.println(LocalizableMessage.raw(name));
                }
            }
        } else {
            // Create a table of their properties containing the name, type (if
            // appropriate), and requested properties.
            SortedMap<String, ?> subTypes = getSubTypes(relation.getChildDefinition());
            boolean includeTypesColumn = subTypes.size() != 1 || !subTypes.containsKey(DSConfig.GENERIC_TYPE);

            TableBuilder builder = new TableBuilder();
            builder.appendHeading(relation.getUserFriendlyName());
            if (includeTypesColumn) {
                builder.appendHeading(INFO_DSCFG_HEADING_COMPONENT_TYPE.get());
            }
            for (String propertyName : propertyNames) {
                builder.appendHeading(LocalizableMessage.raw(propertyName));
            }
            builder.addSortKey(0);

            String baseType = relation.getChildDefinition().getName();
            String typeSuffix = "-" + baseType;
            for (String name : children.keySet()) {
                ManagedObject<?> child = children.get(name);
                ManagedObjectDefinition<?, ?> d = child.getManagedObjectDefinition();
                if (cannotDisplay(app, d)) {
                    continue;
                }

                // First output the name.
                builder.startRow();
                if (relation instanceof SetRelationDefinition) {
                    builder.appendCell(d.getUserFriendlyName());
                } else {
                    builder.appendCell(name);
                }

                if (includeTypesColumn) {
                    // Output the managed object type in the form used in
                    // create-xxx commands.
                    String childType = d.getName();
                    boolean isCustom = CLIProfile.getInstance().isForCustomization(d);
                    if (baseType.equals(childType)) {
                        if (isCustom) {
                            builder.appendCell(DSConfig.CUSTOM_TYPE);
                        } else {
                            builder.appendCell(DSConfig.GENERIC_TYPE);
                        }
                    } else if (childType.endsWith(typeSuffix)) {
                        String ctname = childType.substring(0, childType.length() - typeSuffix.length());
                        if (isCustom) {
                            ctname = String.format("%s-%s", DSConfig.CUSTOM_TYPE, ctname);
                        }
                        builder.appendCell(ctname);
                    } else {
                        builder.appendCell(childType);
                    }
                }

                // Now any requested properties.
                for (String propertyName : propertyNames) {
                    try {
                        PropertyDefinition<?> pd = d.getPropertyDefinition(propertyName);
                        displayProperty(app, builder, child, pd, valuePrinter);
                    } catch (IllegalArgumentException e) {
                        // Assume this child managed object does not support this
                        // property.
                        if (app.isScriptFriendly()) {
                            builder.appendCell();
                        } else {
                            builder.appendCell("-");
                        }
                    }
                }
            }

            PrintStream out = app.getOutputStream();
            if (app.isScriptFriendly()) {
                TablePrinter printer = createScriptFriendlyTablePrinter(out);
                builder.print(printer);
            } else {
                if (app.isInteractive()) {
                    // Make interactive mode prettier.
                    app.println();
                    app.println();
                }
                TextTablePrinter printer = new TextTablePrinter(out);
                printer.setColumnSeparator(LIST_TABLE_SEPARATOR);
                builder.print(printer);
            }
        }

        return MenuResult.success(0);
    }

    private boolean cannotDisplay(ConsoleApplication app, ManagedObjectDefinition<?, ?> d) {
        return !app.isAdvancedMode() && (d.hasOption(HIDDEN) || d.hasOption(ADVANCED));
    }

    /** Display the set of values associated with a property. */
    private <T> void displayProperty(ConsoleApplication app, TableBuilder builder, ManagedObject<?> mo,
            PropertyDefinition<T> pd, PropertyValuePrinter valuePrinter) {
        SortedSet<T> values = mo.getPropertyValues(pd);
        if (values.isEmpty()) {
            if (app.isScriptFriendly()) {
                builder.appendCell();
            } else {
                builder.appendCell("-");
            }
        } else {
            StringBuilder sb = new StringBuilder();
            boolean isFirst = true;
            for (T value : values) {
                if (!isFirst) {
                    sb.append(", ");
                }
                sb.append(valuePrinter.print(pd, value));
                isFirst = false;
            }

            builder.appendCell(sb.toString());
        }
    }
}
