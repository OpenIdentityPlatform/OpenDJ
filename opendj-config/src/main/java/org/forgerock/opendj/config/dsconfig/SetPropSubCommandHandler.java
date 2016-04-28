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
 * Copyright 2007-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 * Portions Copyright 2012 profiq, s.r.o.
 */
package org.forgerock.opendj.config.dsconfig;

import static com.forgerock.opendj.dsconfig.DsconfigMessages.*;

import static org.forgerock.opendj.config.dsconfig.ArgumentExceptionFactory.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.AggregationPropertyDefinition;
import org.forgerock.opendj.config.DefinitionDecodingException;
import org.forgerock.opendj.config.InstantiableRelationDefinition;
import org.forgerock.opendj.config.ManagedObjectAlreadyExistsException;
import org.forgerock.opendj.config.ManagedObjectDefinition;
import org.forgerock.opendj.config.ManagedObjectNotFoundException;
import org.forgerock.opendj.config.ManagedObjectPath;
import org.forgerock.opendj.config.OptionalRelationDefinition;
import org.forgerock.opendj.config.PropertyDefinition;
import org.forgerock.opendj.config.PropertyException;
import org.forgerock.opendj.config.PropertyOption;
import org.forgerock.opendj.config.RelationDefinition;
import org.forgerock.opendj.config.SetRelationDefinition;
import org.forgerock.opendj.config.SingletonRelationDefinition;
import org.forgerock.opendj.config.UndefinedDefaultBehaviorProvider;
import org.forgerock.opendj.config.client.ConcurrentModificationException;
import org.forgerock.opendj.config.client.ManagedObject;
import org.forgerock.opendj.config.client.ManagedObjectDecodingException;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.config.client.MissingMandatoryPropertiesException;
import org.forgerock.opendj.config.client.OperationRejectedException;
import org.forgerock.opendj.config.conditions.Condition;
import org.forgerock.opendj.config.conditions.ContainsCondition;
import org.forgerock.opendj.ldap.AuthorizationException;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.util.Pair;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.CommandBuilder;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.MenuResult;
import com.forgerock.opendj.cli.ReturnCode;
import com.forgerock.opendj.cli.StringArgument;
import com.forgerock.opendj.cli.SubCommand;
import com.forgerock.opendj.cli.SubCommandArgumentParser;

/**
 * A sub-command handler which is used to modify the properties of a managed object.
 * <p/>
 * This sub-command implements the various set-xxx-prop sub-commands.
 */
final class SetPropSubCommandHandler extends SubCommandHandler {

    /** Type of modification being performed. */
    private static enum ModificationType {
        /** Append a single value to the property. */
        ADD,
        /** Remove a single value from the property. */
        REMOVE,
        /** Append a single value to the property (first invocation removes existing values). */
        SET;
    }

    /** The value for the long option add. */
    private static final String OPTION_DSCFG_LONG_ADD = "add";
    /** The value for the long option remove. */
    private static final String OPTION_DSCFG_LONG_REMOVE = "remove";
    /** The value for the long option reset. */
    private static final String OPTION_DSCFG_LONG_RESET = "reset";
    /** The value for the long option set. */
    private static final String OPTION_DSCFG_LONG_SET = "set";
    /** The value for the short option add. */
    private static final Character OPTION_DSCFG_SHORT_ADD = null;
    /** The value for the short option remove. */
    private static final Character OPTION_DSCFG_SHORT_REMOVE = null;
    /** The value for the short option reset. */
    private static final Character OPTION_DSCFG_SHORT_RESET = null;
    /** The value for the short option set. */
    private static final Character OPTION_DSCFG_SHORT_SET = null;

    /**
     * Creates a new set-xxx-prop sub-command for an instantiable relation.
     *
     * @param parser
     *         The sub-command argument parser.
     * @param path
     *         The parent managed object path.
     * @param r
     *         The instantiable relation.
     * @return Returns the new set-xxx-prop sub-command.
     * @throws ArgumentException
     *         If the sub-command could not be created successfully.
     */
    public static SetPropSubCommandHandler create(SubCommandArgumentParser parser, ManagedObjectPath<?, ?> path,
            InstantiableRelationDefinition<?, ?> r) throws ArgumentException {
        return new SetPropSubCommandHandler(parser, path.child(r, "DUMMY"), r);
    }

    /**
     * Creates a new set-xxx-prop sub-command for an optional relation.
     *
     * @param parser
     *         The sub-command argument parser.
     * @param path
     *         The parent managed object path.
     * @param r
     *         The optional relation.
     * @return Returns the new set-xxx-prop sub-command.
     * @throws ArgumentException
     *         If the sub-command could not be created successfully.
     */
    public static SetPropSubCommandHandler create(SubCommandArgumentParser parser, ManagedObjectPath<?, ?> path,
            OptionalRelationDefinition<?, ?> r) throws ArgumentException {
        return new SetPropSubCommandHandler(parser, path.child(r), r);
    }

    /**
     * Creates a new set-xxx-prop sub-command for a set relation.
     *
     * @param parser
     *         The sub-command argument parser.
     * @param path
     *         The parent managed object path.
     * @param r
     *         The set relation.
     * @return Returns the new set-xxx-prop sub-command.
     * @throws ArgumentException
     *         If the sub-command could not be created successfully.
     */
    public static SetPropSubCommandHandler create(SubCommandArgumentParser parser, ManagedObjectPath<?, ?> path,
            SetRelationDefinition<?, ?> r) throws ArgumentException {
        return new SetPropSubCommandHandler(parser, path.child(r), r);
    }

    /**
     * Creates a new set-xxx-prop sub-command for a singleton relation.
     *
     * @param parser
     *         The sub-command argument parser.
     * @param path
     *         The parent managed object path.
     * @param r
     *         The singleton relation.
     * @return Returns the new set-xxx-prop sub-command.
     * @throws ArgumentException
     *         If the sub-command could not be created successfully.
     */
    public static SetPropSubCommandHandler create(SubCommandArgumentParser parser, ManagedObjectPath<?, ?> path,
            SingletonRelationDefinition<?, ?> r) throws ArgumentException {
        return new SetPropSubCommandHandler(parser, path.child(r), r);
    }

    /**
     * Configure the provided managed object and updates the command builder in the pased SetPropSubCommandHandler
     * object.
     *
     * @param app
     *         The console application.
     * @param context
     *         The management context.
     * @param mo
     *         The managed object to be configured.
     * @param handler
     *         The SubCommandHandler whose command builder properties must be updated.
     * @return Returns a MenuResult.success() if the managed object was configured successfully, or MenuResult.quit(),
     * or MenuResult.cancel(), if the managed object was edited interactively and the user chose to quit or
     * cancel.
     * @throws ClientException
     *         If an unrecoverable client exception occurred whilst interacting with the server.
     * @throws ClientException
     *         If an error occurred whilst interacting with the console.
     */
    public static MenuResult<Void> modifyManagedObject(ConsoleApplication app, ManagementContext context,
            ManagedObject<?> mo, SubCommandHandler handler) throws ClientException {
        ManagedObjectDefinition<?, ?> d = mo.getManagedObjectDefinition();
        LocalizableMessage ufn = d.getUserFriendlyName();

        PropertyValueEditor editor = new PropertyValueEditor(app, context);
        while (true) {
            // Interactively set properties if applicable.
            if (app.isInteractive()) {
                SortedSet<PropertyDefinition<?>> properties = new TreeSet<>();
                for (PropertyDefinition<?> pd : d.getAllPropertyDefinitions()) {
                    if (cannotDisplay(app, pd)) {
                        continue;
                    }
                    properties.add(pd);
                }

                MenuResult<Void> result = editor.edit(mo, properties, false);

                // Interactively enable/edit referenced components.
                if (result.isSuccess()) {
                    result = checkReferences(app, context, mo, handler);
                    if (result.isAgain()) {
                        // Edit again.
                        continue;
                    }
                }

                if (result.isQuit()) {
                    if (!app.isMenuDrivenMode()) {
                        // User chose to cancel any changes.
                        app.println();
                        app.println(INFO_DSCFG_CONFIRM_MODIFY_FAIL.get(ufn));
                    }
                    return MenuResult.quit();
                } else if (result.isCancel()) {
                    return MenuResult.cancel();
                }
            }

            try {
                // Commit the changes if necessary
                if (mo.isModified()) {
                    mo.commit();

                    // Output success message.
                    if (app.isVerbose() || app.isInteractive()) {
                        app.println();
                        app.println(INFO_DSCFG_CONFIRM_MODIFY_SUCCESS.get(ufn));
                    }

                    for (PropertyEditorModification<?> mod : editor.getModifications()) {
                        try {
                            handler.getCommandBuilder().addArgument(createArgument(mod));
                        } catch (ArgumentException ae) {
                            // This is a bug
                            throw new RuntimeException("Unexpected error generating the command builder: " + ae, ae);
                        }
                    }

                    handler.setCommandBuilderUseful(true);
                }

                return MenuResult.success();
            } catch (MissingMandatoryPropertiesException e) {
                if (app.isInteractive()) {
                    // If interactive, give the user the chance to fix the
                    // problems.
                    app.errPrintln();
                    displayMissingMandatoryPropertyException(app, e);
                    app.errPrintln();
                    if (!app.confirmAction(INFO_DSCFG_PROMPT_EDIT_AGAIN.get(ufn), true)) {
                        return MenuResult.cancel();
                    }
                } else {
                    throw new ClientException(ReturnCode.CONSTRAINT_VIOLATION, e.getMessageObject(), e);
                }
            } catch (AuthorizationException e) {
                LocalizableMessage msg = ERR_DSCFG_ERROR_MODIFY_AUTHZ.get(ufn);
                throw new ClientException(ReturnCode.INSUFFICIENT_ACCESS_RIGHTS, msg);
            } catch (ConcurrentModificationException e) {
                LocalizableMessage msg = ERR_DSCFG_ERROR_MODIFY_CME.get(ufn);
                throw new ClientException(ReturnCode.CONSTRAINT_VIOLATION, msg);
            } catch (OperationRejectedException e) {
                if (app.isInteractive()) {
                    // If interactive, give the user the chance to fix the
                    // problems.
                    app.errPrintln();
                    displayOperationRejectedException(app, e);
                    app.errPrintln();
                    if (!app.confirmAction(INFO_DSCFG_PROMPT_EDIT_AGAIN.get(ufn), true)) {
                        return MenuResult.cancel();
                    }
                } else {
                    throw new ClientException(ReturnCode.CONSTRAINT_VIOLATION, e.getMessageObject(), e);
                }
            } catch (LdapException e) {
                LocalizableMessage msg = ERR_DSCFG_ERROR_MODIFY_CE.get(ufn, e.getMessage());
                throw new ClientException(ReturnCode.OTHER, msg);
            } catch (ManagedObjectAlreadyExistsException e) {
                // Should never happen.
                throw new IllegalStateException(e);
            }
        }
    }

    private static boolean cannotDisplay(ConsoleApplication app, PropertyDefinition<?> pd) {
        return pd.hasOption(PropertyOption.HIDDEN)
                || (!app.isAdvancedMode() && pd.hasOption(PropertyOption.ADVANCED));
    }

    /** Check that any referenced components are enabled if required. */
    private static MenuResult<Void> checkReferences(ConsoleApplication app, ManagementContext context,
            ManagedObject<?> mo, SubCommandHandler handler) throws ClientException {
        ManagedObjectDefinition<?, ?> d = mo.getManagedObjectDefinition();
        LocalizableMessage ufn = d.getUserFriendlyName();

        try {
            for (PropertyDefinition<?> pd : d.getAllPropertyDefinitions()) {
                if (pd instanceof AggregationPropertyDefinition<?, ?>) {
                    AggregationPropertyDefinition<?, ?> apd = (AggregationPropertyDefinition<?, ?>) pd;

                    // Skip this aggregation if the referenced managed objects
                    // do not need to be enabled.
                    if (!apd.getTargetNeedsEnablingCondition().evaluate(context, mo)) {
                        continue;
                    }

                    // The referenced component(s) must be enabled.
                    for (String name : mo.getPropertyValues(apd)) {
                        ManagedObjectPath<?, ?> path = apd.getChildPath(name);
                        LocalizableMessage rufn = path.getManagedObjectDefinition().getUserFriendlyName();
                        ManagedObject<?> ref;
                        try {
                            ref = context.getManagedObject(path);
                        } catch (DefinitionDecodingException e) {
                            LocalizableMessage msg = ERR_DSCFG_ERROR_GET_CHILD_DDE.get(rufn, rufn, rufn);
                            throw new ClientException(ReturnCode.OTHER, msg);
                        } catch (ManagedObjectDecodingException e) {
                            // FIXME: should not abort here. Instead, display the
                            // errors (if verbose) and apply the changes to the
                            // partial managed object.
                            LocalizableMessage msg = ERR_DSCFG_ERROR_GET_CHILD_MODE.get(rufn);
                            throw new ClientException(ReturnCode.OTHER, msg, e);
                        } catch (ManagedObjectNotFoundException e) {
                            LocalizableMessage msg = ERR_DSCFG_ERROR_GET_CHILD_MONFE.get(rufn);
                            throw new ClientException(ReturnCode.NO_SUCH_OBJECT, msg);
                        }

                        Condition condition = apd.getTargetIsEnabledCondition();
                        while (!condition.evaluate(context, ref)) {
                            boolean isBadReference = true;

                            if (condition instanceof ContainsCondition) {
                                // Attempt to automatically enable the managed object.
                                ContainsCondition cvc = (ContainsCondition) condition;
                                app.println();
                                if (app.confirmAction(
                                        INFO_EDITOR_PROMPT_ENABLED_REFERENCED_COMPONENT.get(rufn, name, ufn), true)) {
                                    cvc.setPropertyValue(ref);
                                    try {
                                        ref.commit();

                                        // Try to create the command builder
                                        if (app instanceof DSConfig && app.isInteractive()) {
                                            DSConfig dsConfig = (DSConfig) app;
                                            String subCommandName = "set-" + path.getRelationDefinition().getName()
                                                    + "-prop";
                                            CommandBuilder builder = dsConfig.getCommandBuilder(subCommandName);

                                            if (path.getRelationDefinition()
                                                    instanceof InstantiableRelationDefinition<?, ?>) {
                                                String argName = CLIProfile.getInstance().getNamingArgument(
                                                        path.getRelationDefinition());
                                                try {
                                                    StringArgument arg =
                                                            StringArgument.builder(argName)
                                                                    .description(INFO_DSCFG_DESCRIPTION_NAME.get(
                                                                            d.getUserFriendlyName()))
                                                                    .valuePlaceholder(INFO_NAME_PLACEHOLDER.get())
                                                                    .buildArgument();
                                                    arg.addValue(name);
                                                    builder.addArgument(arg);
                                                } catch (Throwable t) {
                                                    // Bug
                                                    throw new RuntimeException("Unexpected error: " + t, t);
                                                }
                                            }

                                            try {
                                                StringArgument arg =
                                                        StringArgument.builder(OPTION_DSCFG_LONG_SET)
                                                                .shortIdentifier(OPTION_DSCFG_SHORT_SET)
                                                                .description(INFO_DSCFG_DESCRIPTION_PROP_VAL.get())
                                                                .multiValued()
                                                                .valuePlaceholder(INFO_VALUE_SET_PLACEHOLDER.get())
                                                                .buildArgument();
                                                PropertyDefinition<?> propertyDefinition = cvc.getPropertyDefinition();
                                                arg.addValue(propertyDefinition.getName() + ':'
                                                        + castAndGetArgumentValue(propertyDefinition, cvc.getValue()));
                                                builder.addArgument(arg);
                                            } catch (Throwable t) {
                                                // Bug
                                                throw new RuntimeException("Unexpected error: " + t, t);
                                            }
                                            dsConfig.printCommandBuilder(builder);
                                        }

                                        isBadReference = false;
                                    } catch (MissingMandatoryPropertiesException e) {
                                        // Give the user the chance to fix the problems.
                                        app.errPrintln();
                                        displayMissingMandatoryPropertyException(app, e);
                                        app.errPrintln();
                                        if (app.confirmAction(INFO_DSCFG_PROMPT_EDIT.get(rufn), true)) {
                                            MenuResult<Void> result = modifyManagedObject(app, context, ref, handler);
                                            if (result.isQuit()) {
                                                return result;
                                            } else if (result.isSuccess()) {
                                                // The referenced component was modified
                                                // successfully, but may still be disabled.
                                                isBadReference = false;
                                            }
                                        }
                                    } catch (ConcurrentModificationException e) {
                                        LocalizableMessage msg = ERR_DSCFG_ERROR_MODIFY_CME.get(ufn);
                                        throw new ClientException(ReturnCode.CONSTRAINT_VIOLATION, msg);
                                    } catch (OperationRejectedException e) {
                                        // Give the user the chance to fix the problems.
                                        app.errPrintln();
                                        displayOperationRejectedException(app, e);
                                        app.errPrintln();
                                        if (app.confirmAction(INFO_DSCFG_PROMPT_EDIT.get(rufn), true)) {
                                            MenuResult<Void> result = modifyManagedObject(app, context, ref, handler);
                                            if (result.isQuit()) {
                                                return result;
                                            } else if (result.isSuccess()) {
                                                // The referenced component was modified
                                                // successfully, but may still be disabled.
                                                isBadReference = false;
                                            }
                                        }
                                    } catch (ManagedObjectAlreadyExistsException e) {
                                        // Should never happen.
                                        throw new IllegalStateException(e);
                                    }
                                }
                            } else {
                                app.println();
                                if (app.confirmAction(INFO_DSCFG_PROMPT_EDIT_TO_ENABLE.get(rufn, name, ufn), true)) {
                                    MenuResult<Void> result = SetPropSubCommandHandler.modifyManagedObject(app,
                                            context, ref, handler);
                                    if (result.isQuit()) {
                                        return result;
                                    } else if (result.isSuccess()) {
                                        // The referenced component was modified
                                        // successfully, but may still be disabled.
                                        isBadReference = false;
                                    }
                                }
                            }

                            // If the referenced component is still disabled because
                            // the user refused to modify it, then give the used the
                            // option of editing the referencing component.
                            if (isBadReference) {
                                app.errPrintln();
                                app.errPrintln(ERR_SET_REFERENCED_COMPONENT_DISABLED.get(ufn, rufn));
                                app.errPrintln();
                                if (app.confirmAction(INFO_DSCFG_PROMPT_EDIT_AGAIN.get(ufn), true)) {
                                    return MenuResult.again();
                                } else {
                                    return MenuResult.cancel();
                                }
                            }
                        }
                    }
                }
            }
        } catch (AuthorizationException e) {
            LocalizableMessage msg = ERR_DSCFG_ERROR_MODIFY_AUTHZ.get(ufn);
            throw new ClientException(ReturnCode.INSUFFICIENT_ACCESS_RIGHTS, msg);
        } catch (LdapException e) {
            LocalizableMessage msg = ERR_DSCFG_ERROR_MODIFY_CE.get(ufn, e.getMessage());
            throw new ClientException(ReturnCode.OTHER, msg);
        }

        return MenuResult.success();
    }

    /** The sub-commands naming arguments. */
    private final List<StringArgument> namingArgs;

    /** The path of the managed object. */
    private final ManagedObjectPath<?, ?> path;

    /** The argument which should be used to specify zero or more property value adds. */
    private final StringArgument propertyAddArgument;
    /** The argument which should be used to specify zero or more property value removes. */
    private final StringArgument propertyRemoveArgument;
    /** The argument which should be used to specify zero or more property value resets. */
    private final StringArgument propertyResetArgument;
    /** The argument which should be used to specify zero or more property value assignments. */
    private final StringArgument propertySetArgument;
    /** The sub-command associated with this handler. */
    private final SubCommand subCommand;

    /** Private constructor. */
    private SetPropSubCommandHandler(SubCommandArgumentParser parser, ManagedObjectPath<?, ?> path,
            RelationDefinition<?, ?> r) throws ArgumentException {
        this.path = path;

        // Create the sub-command.
        String name = "set-" + r.getName() + "-prop";
        LocalizableMessage description = INFO_DSCFG_DESCRIPTION_SUBCMD_SETPROP.get(r.getChildDefinition()
                .getUserFriendlyName());
        this.subCommand = new SubCommand(parser, name, false, 0, 0, null, description);

        // Create the naming arguments.
        this.namingArgs = createNamingArgs(subCommand, path, false);

        // Create the --set argument.
        propertySetArgument =
                StringArgument.builder(OPTION_DSCFG_LONG_SET)
                        .shortIdentifier(OPTION_DSCFG_SHORT_SET)
                        .description(INFO_DSCFG_DESCRIPTION_PROP_VAL.get())
                        .multiValued()
                        .valuePlaceholder(INFO_VALUE_SET_PLACEHOLDER.get())
                        .buildAndAddToSubCommand(subCommand);
        // Create the --reset argument.
        propertyResetArgument =
                StringArgument.builder(OPTION_DSCFG_LONG_RESET)
                        .shortIdentifier(OPTION_DSCFG_SHORT_RESET)
                        .description(INFO_DSCFG_DESCRIPTION_RESET_PROP.get())
                        .multiValued()
                        .valuePlaceholder(INFO_PROPERTY_PLACEHOLDER.get())
                        .buildAndAddToSubCommand(subCommand);
        // Create the --add argument.
        this.propertyAddArgument =
                StringArgument.builder(OPTION_DSCFG_LONG_ADD)
                        .shortIdentifier(OPTION_DSCFG_SHORT_ADD)
                        .description(INFO_DSCFG_DESCRIPTION_ADD_PROP_VAL.get())
                        .multiValued()
                        .valuePlaceholder(INFO_VALUE_SET_PLACEHOLDER.get())
                        .buildAndAddToSubCommand(subCommand);
        // Create the --remove argument.
        this.propertyRemoveArgument =
                StringArgument.builder(OPTION_DSCFG_LONG_REMOVE)
                        .shortIdentifier(OPTION_DSCFG_SHORT_REMOVE)
                        .description(INFO_DSCFG_DESCRIPTION_REMOVE_PROP_VAL.get())
                        .multiValued()
                        .valuePlaceholder(INFO_VALUE_SET_PLACEHOLDER.get())
                        .buildAndAddToSubCommand(this.subCommand);

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

    /** {@inheritDoc} */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public MenuResult<Integer> run(ConsoleApplication app, LDAPManagementContextFactory factory)
            throws ArgumentException, ClientException {
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
            LocalizableMessage msg = ERR_DSCFG_ERROR_MODIFY_AUTHZ.get(ufn);
            throw new ClientException(ReturnCode.INSUFFICIENT_ACCESS_RIGHTS, msg);
        } catch (DefinitionDecodingException e) {
            LocalizableMessage msg = ERR_DSCFG_ERROR_GET_CHILD_DDE.get(ufn, ufn, ufn);
            throw new ClientException(ReturnCode.OTHER, msg);
        } catch (ManagedObjectDecodingException e) {
            // FIXME: should not abort here. Instead, display the errors (if
            // verbose) and apply the changes to the partial managed object.
            LocalizableMessage msg = ERR_DSCFG_ERROR_GET_CHILD_MODE.get(ufn);
            throw new ClientException(ReturnCode.OTHER, msg, e);
        } catch (ConcurrentModificationException e) {
            LocalizableMessage msg = ERR_DSCFG_ERROR_MODIFY_CME.get(ufn);
            throw new ClientException(ReturnCode.CONSTRAINT_VIOLATION, msg);
        } catch (ManagedObjectNotFoundException e) {
            String objName = names.get(names.size() - 1);
            ArgumentException except = null;
            LocalizableMessage msg;
            // if object name is 'null', get a user-friendly string to represent this
            if (objName == null) {
                msg = ERR_DSCFG_ERROR_FINDER_NO_CHILDREN_NULL.get();
                except = new ArgumentException(msg);
            } else {
                except = ArgumentExceptionFactory.unknownValueForChildComponent("\"" + objName + "\"");
            }
            if (app.isInteractive()) {
                app.errPrintln();
                app.errPrintln(except.getMessageObject());
                return MenuResult.cancel();
            } else {
                throw except;
            }
        } catch (LdapException e) {
            throw new ClientException(ReturnCode.OTHER, LocalizableMessage.raw(e.getLocalizedMessage()));
        }

        if (result.isQuit()) {
            if (!app.isMenuDrivenMode()) {
                // User chose to quit.
                app.println();
                app.println(INFO_DSCFG_CONFIRM_MODIFY_FAIL.get(ufn));
            }
            return MenuResult.quit();
        } else if (result.isCancel()) {
            return MenuResult.cancel();
        }

        ManagedObject<?> child = result.getValue();
        ManagedObjectDefinition<?, ?> d = child.getManagedObjectDefinition();
        Map<String, ModificationType> lastModTypes = new HashMap<>();
        Map<PropertyDefinition, Set> changes = new HashMap<>();

        // Reset properties.
        for (String m : propertyResetArgument.getValues()) {

            // Check one does not try to reset with a value
            if (m.contains(":")) {
                throw ArgumentExceptionFactory.unableToResetPropertyWithValue(m, OPTION_DSCFG_LONG_RESET);
            }

            PropertyDefinition<?> pd = getPropertyDefinition(d, m);

            // Mandatory properties which have no defined defaults cannot be reset.
            if (pd.hasOption(PropertyOption.MANDATORY)
                    && pd.getDefaultBehaviorProvider() instanceof UndefinedDefaultBehaviorProvider) {
                throw ArgumentExceptionFactory.unableToResetMandatoryProperty(d, m, OPTION_DSCFG_LONG_SET);
            }

            // Save the modification type.
            lastModTypes.put(m, ModificationType.SET);

            // Apply the modification.
            modifyPropertyValues(child, pd, changes, ModificationType.SET, null);
        }

        // Set properties.
        for (String m : propertySetArgument.getValues()) {
            Pair<String, String> pair = parseValue(m);
            String propertyName = pair.getFirst();
            String value = pair.getSecond();

            PropertyDefinition<?> pd = getPropertyDefinition(d, propertyName);

            // Apply the modification.
            if (lastModTypes.containsKey(propertyName)) {
                modifyPropertyValues(child, pd, changes, ModificationType.ADD, value);
            } else {
                lastModTypes.put(propertyName, ModificationType.SET);
                modifyPropertyValues(child, pd, changes, ModificationType.SET, value);
            }
        }

        // Remove properties.
        for (String m : propertyRemoveArgument.getValues()) {
            Pair<String, String> pair = parseValue(m);
            String propertyName = pair.getFirst();
            String value = pair.getSecond();

            PropertyDefinition<?> pd = getPropertyDefinition(d, propertyName);

            // Apply the modification.
            if (lastModTypes.containsKey(propertyName) && lastModTypes.get(propertyName) == ModificationType.SET) {
                throw ArgumentExceptionFactory.incompatiblePropertyModification(m);
            }

            lastModTypes.put(propertyName, ModificationType.REMOVE);
            modifyPropertyValues(child, pd, changes, ModificationType.REMOVE, value);
        }

        // Add properties.
        for (String m : propertyAddArgument.getValues()) {
            Pair<String, String> pair = parseValue(m);
            String propertyName = pair.getFirst();
            String value = pair.getSecond();

            PropertyDefinition<?> pd = getPropertyDefinition(d, propertyName);

            // Apply the modification.
            if (lastModTypes.containsKey(propertyName) && lastModTypes.get(propertyName) == ModificationType.SET) {
                throw ArgumentExceptionFactory.incompatiblePropertyModification(m);
            }

            lastModTypes.put(propertyName, ModificationType.ADD);
            modifyPropertyValues(child, pd, changes, ModificationType.ADD, value);
        }

        // Apply the command line changes.
        for (PropertyDefinition<?> pd : changes.keySet()) {
            try {
                child.setPropertyValues(pd, changes.get(pd));
            } catch (PropertyException e) {
                throw ArgumentExceptionFactory.adaptPropertyException(e, d);
            }
            setCommandBuilderUseful(true);
        }

        // Now the command line changes have been made, apply the changes
        // interacting with the user to fix any problems if required.
        MenuResult<Void> result2 = modifyManagedObject(app, context, child, this);
        if (result2.isCancel()) {
            return MenuResult.cancel();
        } else if (result2.isQuit()) {
            return MenuResult.quit();
        } else {
            if (propertyResetArgument.hasValue()) {
                getCommandBuilder().addArgument(propertyResetArgument);
            }
            if (propertySetArgument.hasValue()) {
                getCommandBuilder().addArgument(propertySetArgument);
            }
            if (propertyAddArgument.hasValue()) {
                getCommandBuilder().addArgument(propertyAddArgument);
            }
            if (propertyRemoveArgument.hasValue()) {
                getCommandBuilder().addArgument(propertyRemoveArgument);
            }
            return MenuResult.success(0);
        }
    }

    /** Parse and check the property "property:value". */
    private Pair<String, String> parseValue(String m) throws ArgumentException {
        int sep = m.indexOf(':');
        if (sep < 0) {
            throw ArgumentExceptionFactory.missingSeparatorInPropertyArgument(m);
        }
        if (sep == 0) {
            throw ArgumentExceptionFactory.missingNameInPropertyArgument(m);
        }

        String propertyName = m.substring(0, sep);
        String value = m.substring(sep + 1, m.length());
        if (value.length() == 0) {
            throw ArgumentExceptionFactory.missingValueInPropertyArgument(m);
        }
        return Pair.of(propertyName, value);
    }

    /** Get and check the property definition. */
    private PropertyDefinition<?> getPropertyDefinition(ManagedObjectDefinition<?, ?> def, String propertyName)
            throws ArgumentException {
        try {
            return def.getPropertyDefinition(propertyName);
        } catch (IllegalArgumentException e) {
            throw ArgumentExceptionFactory.unknownProperty(def, propertyName);
        }
    }

    /** Apply a single modification to the current change-set. */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private <T> void modifyPropertyValues(ManagedObject<?> mo, PropertyDefinition<T> pd,
            Map<PropertyDefinition, Set> changes, ModificationType modType, String s) throws ArgumentException {
        Set<T> values = changes.get(pd);
        if (values == null) {
            values = mo.getPropertyValues(pd);
        }

        if (s == null || s.length() == 0) {
            // Reset back to defaults.
            values.clear();
        } else {
            T value;
            try {
                value = pd.decodeValue(s);
            } catch (PropertyException e) {
                throw ArgumentExceptionFactory.adaptPropertyException(e, mo.getManagedObjectDefinition());
            }

            switch (modType) {
            case ADD:
                values.add(value);
                break;
            case REMOVE:
                if (!values.remove(value)) {
                    // value was not part of values
                    throw ArgumentExceptionFactory.unknownValueForMultiValuedProperty(s, pd.getName());
                }
                break;
            case SET:
                values = new TreeSet<>(pd);
                values.add(value);
                break;
            }
        }

        changes.put(pd, values);
    }

    /**
     * Creates an argument (the one that the user should provide in the command-line) that is equivalent to the
     * modification proposed by the user in the provided PropertyEditorModification object.
     *
     * @param mod
     *         the object describing the modification made.
     * @param <T>
     *         the type of the property to be retrieved.
     * @return the argument representing the modification.
     * @throws ArgumentException
     *         if there is a problem creating the argument.
     */
    private static <T> Argument createArgument(PropertyEditorModification<T> mod) throws ArgumentException {
        StringArgument arg;

        PropertyDefinition<T> propertyDefinition = mod.getPropertyDefinition();
        String propName = propertyDefinition.getName();

        switch (mod.getType()) {
        case RESET:
            arg =
                    StringArgument.builder(OPTION_DSCFG_LONG_RESET)
                            .shortIdentifier(OPTION_DSCFG_SHORT_RESET)
                            .description(INFO_DSCFG_DESCRIPTION_RESET_PROP.get())
                            .multiValued()
                            .valuePlaceholder(INFO_PROPERTY_PLACEHOLDER.get())
                            .buildArgument();
            arg.addValue(propName);
            break;
        case REMOVE:
            arg =
                    StringArgument.builder(OPTION_DSCFG_LONG_REMOVE)
                            .shortIdentifier(OPTION_DSCFG_SHORT_REMOVE)
                            .description(INFO_DSCFG_DESCRIPTION_REMOVE_PROP_VAL.get())
                            .multiValued()
                            .valuePlaceholder(INFO_VALUE_SET_PLACEHOLDER.get())
                            .buildArgument();
            for (T value : mod.getModificationValues()) {
                arg.addValue(propName + ':' + getArgumentValue(propertyDefinition, value));
            }
            break;
        case ADD:
            arg =
                    StringArgument.builder(OPTION_DSCFG_LONG_ADD)
                            .shortIdentifier(OPTION_DSCFG_SHORT_ADD)
                            .description(INFO_DSCFG_DESCRIPTION_ADD_PROP_VAL.get())
                            .multiValued()
                            .valuePlaceholder(INFO_VALUE_SET_PLACEHOLDER.get())
                            .buildArgument();
            for (T value : mod.getModificationValues()) {
                arg.addValue(propName + ':' + getArgumentValue(propertyDefinition, value));
            }
            break;
        case SET:
            arg =
                    StringArgument.builder(OPTION_DSCFG_LONG_SET)
                            .shortIdentifier(OPTION_DSCFG_SHORT_SET)
                            .description(INFO_DSCFG_DESCRIPTION_PROP_VAL.get())
                            .multiValued()
                            .valuePlaceholder(INFO_VALUE_SET_PLACEHOLDER.get())
                            .buildArgument();
            for (T value : mod.getModificationValues()) {
                arg.addValue(propName + ':' + getArgumentValue(propertyDefinition, value));
            }
            break;
        default:
            // Bug
            throw new IllegalStateException("Unknown modification type: " + mod.getType());
        }
        return arg;
    }
}
