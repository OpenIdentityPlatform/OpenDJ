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

import static com.forgerock.opendj.dsconfig.DsconfigMessages.*;
import static com.forgerock.opendj.cli.ArgumentConstants.LIST_TABLE_SEPARATOR;

import java.io.PrintStream;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.AbsoluteInheritedDefaultBehaviorProvider;
import org.forgerock.opendj.config.AliasDefaultBehaviorProvider;
import org.forgerock.opendj.config.DefaultBehaviorProviderVisitor;
import org.forgerock.opendj.config.DefinedDefaultBehaviorProvider;
import org.forgerock.opendj.config.DefinitionDecodingException;
import org.forgerock.opendj.config.InstantiableRelationDefinition;
import org.forgerock.opendj.config.ManagedObjectDefinition;
import org.forgerock.opendj.config.ManagedObjectNotFoundException;
import org.forgerock.opendj.config.ManagedObjectPath;
import org.forgerock.opendj.config.OptionalRelationDefinition;
import org.forgerock.opendj.config.PropertyDefinition;
import org.forgerock.opendj.config.RelationDefinition;
import org.forgerock.opendj.config.RelativeInheritedDefaultBehaviorProvider;
import org.forgerock.opendj.config.SetRelationDefinition;
import org.forgerock.opendj.config.SingletonRelationDefinition;
import org.forgerock.opendj.config.UndefinedDefaultBehaviorProvider;
import org.forgerock.opendj.config.client.ConcurrentModificationException;
import org.forgerock.opendj.config.client.ManagedObject;
import org.forgerock.opendj.config.client.ManagedObjectDecodingException;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.ldap.AuthorizationException;
import org.forgerock.opendj.ldap.LdapException;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ReturnCode;
import com.forgerock.opendj.cli.StringArgument;
import com.forgerock.opendj.cli.SubCommand;
import com.forgerock.opendj.cli.SubCommandArgumentParser;
import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.MenuResult;
import com.forgerock.opendj.cli.TableBuilder;
import com.forgerock.opendj.cli.TablePrinter;
import com.forgerock.opendj.cli.TextTablePrinter;

/**
 * A sub-command handler which is used to retrieve the properties of a managed object.
 * <p>
 * This sub-command implements the various get-xxx-prop sub-commands.
 */
final class GetPropSubCommandHandler extends SubCommandHandler {

    /**
     * Creates a new get-xxx-prop sub-command for an instantiable relation.
     *
     * @param parser
     *            The sub-command argument parser.
     * @param path
     *            The parent managed object path.
     * @param r
     *            The instantiable relation.
     * @return Returns the new get-xxx-prop sub-command.
     * @throws ArgumentException
     *             If the sub-command could not be created successfully.
     */
    public static GetPropSubCommandHandler create(SubCommandArgumentParser parser, ManagedObjectPath<?, ?> path,
            InstantiableRelationDefinition<?, ?> r) throws ArgumentException {
        return new GetPropSubCommandHandler(parser, path.child(r, "DUMMY"), r);
    }

    /**
     * Creates a new get-xxx-prop sub-command for an optional relation.
     *
     * @param parser
     *            The sub-command argument parser.
     * @param path
     *            The parent managed object path.
     * @param r
     *            The optional relation.
     * @return Returns the new get-xxx-prop sub-command.
     * @throws ArgumentException
     *             If the sub-command could not be created successfully.
     */
    public static GetPropSubCommandHandler create(SubCommandArgumentParser parser, ManagedObjectPath<?, ?> path,
            OptionalRelationDefinition<?, ?> r) throws ArgumentException {
        return new GetPropSubCommandHandler(parser, path.child(r), r);
    }

    /**
     * Creates a new get-xxx-prop sub-command for a set relation.
     *
     * @param parser
     *            The sub-command argument parser.
     * @param path
     *            The parent managed object path.
     * @param r
     *            The set relation.
     * @return Returns the new get-xxx-prop sub-command.
     * @throws ArgumentException
     *             If the sub-command could not be created successfully.
     */
    public static GetPropSubCommandHandler create(SubCommandArgumentParser parser, ManagedObjectPath<?, ?> path,
            SetRelationDefinition<?, ?> r) throws ArgumentException {
        return new GetPropSubCommandHandler(parser, path.child(r), r);
    }

    /**
     * Creates a new get-xxx-prop sub-command for a singleton relation.
     *
     * @param parser
     *            The sub-command argument parser.
     * @param path
     *            The parent managed object path.
     * @param r
     *            The singleton relation.
     * @return Returns the new get-xxx-prop sub-command.
     * @throws ArgumentException
     *             If the sub-command could not be created successfully.
     */
    public static GetPropSubCommandHandler create(SubCommandArgumentParser parser, ManagedObjectPath<?, ?> path,
            SingletonRelationDefinition<?, ?> r) throws ArgumentException {
        return new GetPropSubCommandHandler(parser, path.child(r), r);
    }

    /** The sub-commands naming arguments. */
    private final List<StringArgument> namingArgs;

    /** The path of the managed object. */
    private final ManagedObjectPath<?, ?> path;

    /** The sub-command associated with this handler. */
    private final SubCommand subCommand;

    /** Private constructor. */
    private GetPropSubCommandHandler(SubCommandArgumentParser parser, ManagedObjectPath<?, ?> path,
            RelationDefinition<?, ?> r) throws ArgumentException {
        this.path = path;

        // Create the sub-command.
        String name = "get-" + r.getName() + "-prop";
        LocalizableMessage message = INFO_DSCFG_DESCRIPTION_SUBCMD_GETPROP.get(r.getChildDefinition()
                .getUserFriendlyName());
        this.subCommand = new SubCommand(parser, name, false, 0, 0, null, message);

        // Create the naming arguments.
        this.namingArgs = createNamingArgs(subCommand, path, false);

        // Register common arguments.
        registerPropertyNameArgument(this.subCommand);
        registerRecordModeArgument(this.subCommand);
        registerUnitSizeArgument(this.subCommand);
        registerUnitTimeArgument(this.subCommand);

        // Register the tags associated with the child managed objects.
        addTags(path.getManagedObjectDefinition().getAllTags());
    }

    /**
     * Gets the relation definition associated with the type of component that this sub-command handles.
     *
     * @return Returns the relation definition associated with the type of component that this sub-command handles.
     */
    public RelationDefinition<?, ?> getRelationDefinition() {
        return path.getRelationDefinition();
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
        PropertyValuePrinter valuePrinter = new PropertyValuePrinter(getSizeUnit(), getTimeUnit(),
                app.isScriptFriendly());

        // Get the naming argument values.
        List<String> names = getNamingArgValues(app, namingArgs);

        // Reset the command builder
        getCommandBuilder().clearArguments();
        setCommandBuilderUseful(false);

        // Update the command builder.
        updateCommandBuilderWithSubCommand();

        // Get the targeted managed object.
        LocalizableMessage ufn = path.getRelationDefinition().getUserFriendlyName();
        ManagementContext context = factory.getManagementContext();
        MenuResult<ManagedObject<?>> result;
        try {
            result = getManagedObject(app, context, path, names);
        } catch (AuthorizationException e) {
            LocalizableMessage msg = ERR_DSCFG_ERROR_GET_CHILD_AUTHZ.get(ufn);
            throw new ClientException(ReturnCode.INSUFFICIENT_ACCESS_RIGHTS, msg);
        } catch (DefinitionDecodingException e) {
            LocalizableMessage msg = ERR_DSCFG_ERROR_GET_CHILD_DDE.get(ufn, ufn, ufn);
            throw new ClientException(ReturnCode.OTHER, msg);
        } catch (ManagedObjectDecodingException e) {
            LocalizableMessage msg = ERR_DSCFG_ERROR_GET_CHILD_MODE.get(ufn);
            throw new ClientException(ReturnCode.OTHER, msg, e);
        } catch (ConcurrentModificationException e) {
            LocalizableMessage msg = ERR_DSCFG_ERROR_GET_CHILD_CME.get(ufn);
            throw new ClientException(ReturnCode.CONSTRAINT_VIOLATION, msg);
        } catch (ManagedObjectNotFoundException e) {
            LocalizableMessage msg = ERR_DSCFG_ERROR_GET_CHILD_MONFE.get(ufn);
            throw new ClientException(ReturnCode.NO_SUCH_OBJECT, msg);
        } catch (LdapException e) {
            throw new ClientException(ReturnCode.OTHER, LocalizableMessage.raw(e.getLocalizedMessage()));
        }

        if (result.isQuit()) {
            return MenuResult.quit();
        } else if (result.isCancel()) {
            return MenuResult.cancel();
        }

        // Validate the property names.
        ManagedObject<?> child = result.getValue();
        ManagedObjectDefinition<?, ?> d = child.getManagedObjectDefinition();
        Collection<PropertyDefinition<?>> pdList;
        if (propertyNames.isEmpty()) {
            pdList = d.getAllPropertyDefinitions();
        } else {
            pdList = new LinkedList<>();
            for (String name : propertyNames) {
                try {
                    pdList.add(d.getPropertyDefinition(name));
                } catch (IllegalArgumentException e) {
                    throw ArgumentExceptionFactory.unknownProperty(d, name);
                }
            }
        }

        // Now output its properties.
        TableBuilder builder = new TableBuilder();
        builder.appendHeading(INFO_DSCFG_HEADING_PROPERTY_NAME.get());
        builder.appendHeading(INFO_DSCFG_HEADING_PROPERTY_VALUE.get());
        builder.addSortKey(0);
        for (PropertyDefinition<?> pd : pdList) {
            if (cannotDisplay(app, pd, propertyNames)) {
                continue;
            }

            displayProperty(app, builder, child, pd, valuePrinter);
            setCommandBuilderUseful(true);
        }

        PrintStream out = app.getOutputStream();
        if (app.isScriptFriendly()) {
            TablePrinter printer = createScriptFriendlyTablePrinter(out);
            builder.print(printer);
        } else {
            TextTablePrinter printer = new TextTablePrinter(out);
            printer.setColumnSeparator(LIST_TABLE_SEPARATOR);
            printer.setColumnWidth(1, 0);
            builder.print(printer);
        }

        return MenuResult.success(0);
    }

    /** Display the set of values associated with a property. */
    private <T> void displayProperty(final ConsoleApplication app, TableBuilder builder, ManagedObject<?> mo,
            PropertyDefinition<T> pd, PropertyValuePrinter valuePrinter) {
        SortedSet<T> values = mo.getPropertyValues(pd);
        if (values.isEmpty()) {
            // There are no values or default values. Display the default
            // behavior for alias values.
            DefaultBehaviorProviderVisitor<T, LocalizableMessage, Void> visitor
                = new DefaultBehaviorProviderVisitor<T, LocalizableMessage, Void>() {

                    @Override
                    public LocalizableMessage visitAbsoluteInherited(AbsoluteInheritedDefaultBehaviorProvider<T> d,
                            Void p) {
                        // Should not happen - inherited default values are
                        // displayed as normal values.
                        throw new IllegalStateException();
                    }

                    @Override
                    public LocalizableMessage visitAlias(AliasDefaultBehaviorProvider<T> d, Void p) {
                        if (app.isVerbose()) {
                            return d.getSynopsis();
                        }
                        return null;
                    }

                    @Override
                    public LocalizableMessage visitDefined(DefinedDefaultBehaviorProvider<T> d, Void p) {
                        // Should not happen - real default values are displayed as
                        // normal values.
                        throw new IllegalStateException();
                    }

                    @Override
                    public LocalizableMessage visitRelativeInherited(RelativeInheritedDefaultBehaviorProvider<T> d,
                            Void p) {
                        // Should not happen - inherited default values are
                        // displayed as normal values.
                        throw new IllegalStateException();
                    }

                    @Override
                    public LocalizableMessage visitUndefined(UndefinedDefaultBehaviorProvider<T> d, Void p) {
                        return null;
                    }
                };

            builder.startRow();
            builder.appendCell(pd.getName());

            LocalizableMessage content = pd.getDefaultBehaviorProvider().accept(visitor, null);
            if (content == null) {
                if (app.isScriptFriendly()) {
                    builder.appendCell();
                } else {
                    builder.appendCell("-");
                }
            } else {
                builder.appendCell(content);
            }
        } else if (isRecordMode()) {
            for (T value : values) {
                builder.startRow();
                builder.appendCell(pd.getName());
                builder.appendCell(valuePrinter.print(pd, value));
            }
        } else {
            builder.startRow();
            builder.appendCell(pd.getName());

            if (app.isScriptFriendly()) {
                for (T value : values) {
                    builder.appendCell(valuePrinter.print(pd, value));
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
}
