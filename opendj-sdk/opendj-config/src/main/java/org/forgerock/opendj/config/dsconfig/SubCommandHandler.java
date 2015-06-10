/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2007-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.forgerock.opendj.config.dsconfig;

import static com.forgerock.opendj.dsconfig.DsconfigMessages.*;
import static com.forgerock.opendj.cli.CliMessages.*;
import static org.forgerock.opendj.config.dsconfig.DSConfig.PROPERTY_SCRIPT_NAME;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.AbstractManagedObjectDefinition;
import org.forgerock.opendj.config.Configuration;
import org.forgerock.opendj.config.ConfigurationClient;
import org.forgerock.opendj.config.DefinitionDecodingException;
import org.forgerock.opendj.config.DurationUnit;
import org.forgerock.opendj.config.InstantiableRelationDefinition;
import org.forgerock.opendj.config.ManagedObjectDefinition;
import org.forgerock.opendj.config.ManagedObjectNotFoundException;
import org.forgerock.opendj.config.ManagedObjectOption;
import org.forgerock.opendj.config.ManagedObjectPath;
import org.forgerock.opendj.config.ManagedObjectPathSerializer;
import org.forgerock.opendj.config.OptionalRelationDefinition;
import org.forgerock.opendj.config.PropertyDefinition;
import org.forgerock.opendj.config.PropertyDefinitionUsageBuilder;
import org.forgerock.opendj.config.PropertyOption;
import org.forgerock.opendj.config.RelationDefinition;
import org.forgerock.opendj.config.SetRelationDefinition;
import org.forgerock.opendj.config.SingletonRelationDefinition;
import org.forgerock.opendj.config.SizeUnit;
import org.forgerock.opendj.config.Tag;
import org.forgerock.opendj.config.client.ConcurrentModificationException;
import org.forgerock.opendj.config.client.IllegalManagedObjectNameException;
import org.forgerock.opendj.config.client.ManagedObject;
import org.forgerock.opendj.config.client.ManagedObjectDecodingException;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.ldap.AuthorizationException;
import org.forgerock.opendj.ldap.LdapException;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.CommandBuilder;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.Menu;
import com.forgerock.opendj.cli.MenuBuilder;
import com.forgerock.opendj.cli.MenuResult;
import com.forgerock.opendj.cli.ReturnCode;
import com.forgerock.opendj.cli.StringArgument;
import com.forgerock.opendj.cli.SubCommand;
import com.forgerock.opendj.cli.TabSeparatedTablePrinter;
import com.forgerock.opendj.cli.TablePrinter;

/** An interface for sub-command implementations. */
abstract class SubCommandHandler implements Comparable<SubCommandHandler> {

    /**
     * A path serializer which is used to retrieve a managed object based on a path and a list of path arguments.
     */
    private class ManagedObjectFinder implements ManagedObjectPathSerializer {

        /** The console application. */
        private ConsoleApplication app;

        /** The index of the next path argument to be retrieved. */
        private int argIndex;

        /** The list of managed object path arguments. */
        private List<String> args;

        private AuthorizationException authze;

        private LdapException ere;

        /** Any CLI exception that was caught when attempting to find the managed object. */
        private ClientException clie;

        private ConcurrentModificationException cme;

        /** Any operation exception that was caught when attempting to find the managed object. */
        private DefinitionDecodingException dde;

        private ManagedObjectDecodingException mode;

        private ManagedObjectNotFoundException monfe;

        /** The current result. */
        private MenuResult<ManagedObject<?>> result;

        /** {@inheritDoc} */
        @Override
        public <C extends ConfigurationClient, S extends Configuration> void appendManagedObjectPathElement(
                InstantiableRelationDefinition<? super C, ? super S> r, AbstractManagedObjectDefinition<C, S> d,
                String name) {
            if (result.isSuccess()) {
                // We should ignore the "template" name here and use a path
                // argument.
                String childName = args.get(argIndex++);

                try {
                    // If the name is null then we must be interactive - so let
                    // the user choose.
                    if (childName == null) {
                        try {
                            MenuResult<String> sresult = readChildName(app, result.getValue(), r, d);

                            if (sresult.isCancel()) {
                                result = MenuResult.cancel();
                                return;
                            } else if (sresult.isQuit()) {
                                result = MenuResult.quit();
                                return;
                            } else {
                                childName = sresult.getValue();
                            }
                        } catch (ClientException e) {
                            clie = e;
                            result = MenuResult.quit();
                            return;
                        }
                    } else if (childName.trim().length() == 0) {
                        IllegalManagedObjectNameException e = new IllegalManagedObjectNameException(childName);
                        clie = ArgumentExceptionFactory.adaptIllegalManagedObjectNameException(e, d);
                        result = MenuResult.quit();
                        return;
                    }

                    ManagedObject<?> child = result.getValue().getChild(r, childName);

                    // Check that child is a sub-type of the specified
                    // definition.
                    if (!child.getManagedObjectDefinition().isChildOf(d)) {
                        clie = ArgumentExceptionFactory.wrongManagedObjectType(r, child.getManagedObjectDefinition(),
                                getSubCommand().getName());
                        result = MenuResult.quit();
                    } else {
                        result = MenuResult.<ManagedObject<?>> success(child);
                    }
                } catch (DefinitionDecodingException e) {
                    dde = e;
                    result = MenuResult.quit();
                } catch (ManagedObjectDecodingException e) {
                    mode = e;
                    result = MenuResult.quit();
                } catch (AuthorizationException e) {
                    authze = e;
                    result = MenuResult.quit();
                } catch (ManagedObjectNotFoundException e) {
                    monfe = e;
                    result = MenuResult.quit();
                } catch (ConcurrentModificationException e) {
                    cme = e;
                    result = MenuResult.quit();
                } catch (LdapException e) {
                    ere = e;
                    result = MenuResult.quit();
                }
            }
        }

        /** {@inheritDoc} */
        @Override
        public <C extends ConfigurationClient, S extends Configuration> void appendManagedObjectPathElement(
                OptionalRelationDefinition<? super C, ? super S> r, AbstractManagedObjectDefinition<C, S> d) {
            if (result.isSuccess()) {
                try {
                    ManagedObject<?> child = result.getValue().getChild(r);

                    // Check that child is a sub-type of the specified
                    // definition.
                    if (!child.getManagedObjectDefinition().isChildOf(d)) {
                        clie = ArgumentExceptionFactory.wrongManagedObjectType(r, child.getManagedObjectDefinition(),
                                getSubCommand().getName());
                        result = MenuResult.quit();
                    } else {
                        result = MenuResult.<ManagedObject<?>> success(child);
                    }
                } catch (DefinitionDecodingException e) {
                    dde = e;
                    result = MenuResult.quit();
                } catch (ManagedObjectDecodingException e) {
                    mode = e;
                    result = MenuResult.quit();
                } catch (AuthorizationException e) {
                    authze = e;
                    result = MenuResult.quit();
                } catch (ManagedObjectNotFoundException e) {
                    monfe = e;
                    result = MenuResult.quit();
                } catch (ConcurrentModificationException e) {
                    cme = e;
                    result = MenuResult.quit();
                } catch (LdapException e) {
                    ere = e;
                    result = MenuResult.quit();
                }
            }
        }

        /** {@inheritDoc} */
        @Override
        public <C extends ConfigurationClient, S extends Configuration> void appendManagedObjectPathElement(
                SetRelationDefinition<? super C, ? super S> r, AbstractManagedObjectDefinition<C, S> d) {
            if (result.isSuccess()) {
                // We should ignore the "template" name here and use a path
                // argument.
                String childName = args.get(argIndex++);

                try {
                    // If the name is null then we must be interactive - so let
                    // the user choose.
                    if (childName == null) {
                        try {
                            MenuResult<String> sresult = readChildName(app, result.getValue(), r, d);

                            if (sresult.isCancel()) {
                                result = MenuResult.cancel();
                                return;
                            } else if (sresult.isQuit()) {
                                result = MenuResult.quit();
                                return;
                            } else {
                                childName = sresult.getValue();
                            }
                        } catch (ClientException e) {
                            clie = e;
                            result = MenuResult.quit();
                            return;
                        }
                    } else if (childName.trim().length() == 0) {
                        IllegalManagedObjectNameException e = new IllegalManagedObjectNameException(childName);
                        clie = ArgumentExceptionFactory.adaptIllegalManagedObjectNameException(e, d);
                        result = MenuResult.quit();
                        return;
                    } else {
                        String name = childName.trim();
                        SortedMap<String, ManagedObjectDefinition<? extends C, ? extends S>> types = getSubTypes(d);
                        ManagedObjectDefinition<?, ?> cd = types.get(name);
                        if (cd == null) {
                            // The name must be invalid.
                            String typeUsage = getSubTypesUsage(d);
                            LocalizableMessage msg = ERR_DSCFG_ERROR_SUB_TYPE_UNRECOGNIZED.get(name,
                                    r.getUserFriendlyName(), typeUsage);
                            clie = new ClientException(ReturnCode.APPLICATION_ERROR, msg);
                            result = MenuResult.quit();
                            return;
                        } else {
                            childName = cd.getName();
                        }
                    }

                    ManagedObject<?> child = result.getValue().getChild(r, childName);

                    // Check that child is a sub-type of the specified
                    // definition.
                    if (!child.getManagedObjectDefinition().isChildOf(d)) {
                        clie = ArgumentExceptionFactory.wrongManagedObjectType(r, child.getManagedObjectDefinition(),
                                getSubCommand().getName());
                        result = MenuResult.quit();
                    } else {
                        result = MenuResult.<ManagedObject<?>> success(child);
                    }
                } catch (DefinitionDecodingException e) {
                    dde = e;
                    result = MenuResult.quit();
                } catch (ManagedObjectDecodingException e) {
                    mode = e;
                    result = MenuResult.quit();
                } catch (AuthorizationException e) {
                    authze = e;
                    result = MenuResult.quit();
                } catch (ManagedObjectNotFoundException e) {
                    monfe = e;
                    result = MenuResult.quit();
                } catch (ConcurrentModificationException e) {
                    cme = e;
                    result = MenuResult.quit();
                } catch (LdapException e) {
                    ere = e;
                    result = MenuResult.quit();
                }
            }
        }

        /** {@inheritDoc} */
        @Override
        public <C extends ConfigurationClient, S extends Configuration> void appendManagedObjectPathElement(
                SingletonRelationDefinition<? super C, ? super S> r, AbstractManagedObjectDefinition<C, S> d) {
            if (result.isSuccess()) {
                try {
                    ManagedObject<?> child = result.getValue().getChild(r);

                    // Check that child is a sub-type of the specified
                    // definition.
                    if (!child.getManagedObjectDefinition().isChildOf(d)) {
                        clie = ArgumentExceptionFactory.wrongManagedObjectType(r, child.getManagedObjectDefinition(),
                                getSubCommand().getName());
                        result = MenuResult.quit();
                    } else {
                        result = MenuResult.<ManagedObject<?>> success(child);
                    }
                } catch (DefinitionDecodingException e) {
                    dde = e;
                    result = MenuResult.quit();
                } catch (ManagedObjectDecodingException e) {
                    mode = e;
                    result = MenuResult.quit();
                } catch (AuthorizationException e) {
                    authze = e;
                    result = MenuResult.quit();
                } catch (ManagedObjectNotFoundException e) {
                    monfe = e;
                    result = MenuResult.quit();
                } catch (ConcurrentModificationException e) {
                    cme = e;
                    result = MenuResult.quit();
                } catch (LdapException e) {
                    ere = e;
                    result = MenuResult.quit();
                }
            }
        }

        /**
         * Finds the named managed object.
         *
         * @param app
         *            The console application.
         * @param context
         *            The management context.
         * @param path
         *            The managed object path.
         * @param args
         *            The managed object path arguments.
         * @return Returns a {@link MenuResult#success()} containing the managed object referenced by the provided
         *         managed object path, or {@link MenuResult#quit()}, or {@link MenuResult#cancel()}, if the sub-command
         *         was run interactively and the user chose to quit or cancel.
         * @throws ClientException
         *             If one of the naming arguments referenced a managed object of the wrong type.
         * @throws DefinitionDecodingException
         *             If the managed object was found but its type could not be determined.
         * @throws ManagedObjectDecodingException
         *             If the managed object was found but one or more of its properties could not be decoded.
         * @throws ManagedObjectNotFoundException
         *             If the requested managed object could not be found on the server.
         * @throws ConcurrentModificationException
         *             If this managed object has been removed from the server by another client.
         * @throws AuthorizationException
         *             If the server refuses to retrieve the managed object because the client does not have the correct
         *             privileges.
         * @throws LdapException
         *             If any other error occurs.
         */
        public MenuResult<ManagedObject<?>> find(ConsoleApplication app, ManagementContext context,
                ManagedObjectPath<?, ?> path, List<String> args) throws ClientException, AuthorizationException,
                ConcurrentModificationException, DefinitionDecodingException, ManagedObjectDecodingException,
                ManagedObjectNotFoundException, LdapException {
            this.result = MenuResult.<ManagedObject<?>> success(context.getRootConfigurationManagedObject());
            this.app = app;
            this.args = args;
            this.argIndex = 0;

            this.clie = null;
            this.authze = null;
            this.cme = null;
            this.dde = null;
            this.mode = null;
            this.monfe = null;
            this.ere = null;

            path.serialize(this);

            if (result.isSuccess()) {
                return result;
            } else if (clie != null) {
                throw clie;
            } else if (authze != null) {
                throw authze;
            } else if (cme != null) {
                throw cme;
            } else if (dde != null) {
                throw dde;
            } else if (mode != null) {
                throw mode;
            } else if (monfe != null) {
                throw monfe;
            } else if (ere != null) {
                throw ere;
            } else {
                // User requested termination interactively.
                return result;
            }
        }
    }

    /** A path serializer which is used to register a sub-command's naming arguments. */
    private static final class NamingArgumentBuilder implements ManagedObjectPathSerializer {

        /**
         * Creates the naming arguments for a given path.
         *
         * @param subCommand
         *            The sub-command.
         * @param path
         *            The managed object path.
         * @param isCreate
         *            Indicates whether the sub-command is a create-xxx sub-command, in which case the final path
         *            element will have different usage information.
         * @return Returns the naming arguments.
         * @throws ArgumentException
         *             If one or more naming arguments could not be registered.
         */
        private static List<StringArgument> create(SubCommand subCommand, ManagedObjectPath<?, ?> path,
                boolean isCreate) throws ArgumentException {
            NamingArgumentBuilder builder = new NamingArgumentBuilder(subCommand, path.size(), isCreate);
            path.serialize(builder);

            if (builder.e != null) {
                throw builder.e;
            }

            return builder.arguments;
        }

        /** The list of naming arguments. */
        private final List<StringArgument> arguments = new LinkedList<>();

        /** Any argument exception thrown when creating the naming arguments. */
        private ArgumentException e;

        /**
         * Indicates whether the sub-command is a create-xxx sub-command, in which case the final path element will have
         * different usage information.
         */
        private final boolean isCreate;

        /** The sub-command. */
        private final SubCommand subCommand;

        /** The number of path elements to expect. */
        private int sz;

        /** Private constructor. */
        private NamingArgumentBuilder(SubCommand subCommand, int sz, boolean isCreate) {
            this.subCommand = subCommand;
            this.sz = sz;
            this.isCreate = isCreate;
        }

        /** {@inheritDoc} */
        @Override
        public <C extends ConfigurationClient, S extends Configuration> void appendManagedObjectPathElement(
                InstantiableRelationDefinition<? super C, ? super S> r, AbstractManagedObjectDefinition<C, S> d,
                String name) {
            sz--;

            String argName = CLIProfile.getInstance().getNamingArgument(r);
            StringArgument arg;

            try {
                if (isCreate && sz == 0) {
                    // The final path element in create-xxx sub-commands should
                    // have a different usage.
                    PropertyDefinition<?> pd = r.getNamingPropertyDefinition();

                    if (pd != null) {
                        // Use syntax and description from naming property.
                        PropertyDefinitionUsageBuilder b = new PropertyDefinitionUsageBuilder(false);
                        LocalizableMessage usage = LocalizableMessage.raw("{" + b.getUsage(pd) + "}");
                        arg = new StringArgument(argName, null, argName, false, true, usage,
                                INFO_DSCFG_DESCRIPTION_NAME_CREATE_EXT.get(d.getUserFriendlyName(), pd.getName(),
                                        pd.getSynopsis()));
                    } else {
                        arg = new StringArgument(argName, null, argName, false, true, INFO_NAME_PLACEHOLDER.get(),
                                INFO_DSCFG_DESCRIPTION_NAME_CREATE.get(d.getUserFriendlyName()));
                    }
                } else {
                    // A normal naming argument.
                    arg = new StringArgument(argName, null, argName, false, true, INFO_NAME_PLACEHOLDER.get(),
                            INFO_DSCFG_DESCRIPTION_NAME.get(d.getUserFriendlyName()));
                }
                subCommand.addArgument(arg);
                arguments.add(arg);
            } catch (ArgumentException e) {
                this.e = e;
            }
        }

        /** {@inheritDoc} */
        @Override
        public <C extends ConfigurationClient, S extends Configuration> void appendManagedObjectPathElement(
                OptionalRelationDefinition<? super C, ? super S> r, AbstractManagedObjectDefinition<C, S> d) {
            sz--;
        }

        /** {@inheritDoc} */
        @Override
        public <C extends ConfigurationClient, S extends Configuration> void appendManagedObjectPathElement(
                SetRelationDefinition<? super C, ? super S> r, AbstractManagedObjectDefinition<C, S> d) {
            sz--;

            // The name of the managed object is determined from its type, so
            // we don't need this argument.
            if (isCreate && sz == 0) {
                return;
            }

            String argName = CLIProfile.getInstance().getNamingArgument(r);
            StringArgument arg;

            try {
                arg = new StringArgument(argName, null, argName, false, true, INFO_NAME_PLACEHOLDER.get(),
                        INFO_DSCFG_DESCRIPTION_NAME.get(d.getUserFriendlyName()));
                subCommand.addArgument(arg);
                arguments.add(arg);
            } catch (ArgumentException e) {
                this.e = e;
            }
        }

        /** {@inheritDoc} */
        @Override
        public <C extends ConfigurationClient, S extends Configuration> void appendManagedObjectPathElement(
                SingletonRelationDefinition<? super C, ? super S> r, AbstractManagedObjectDefinition<C, S> d) {
            sz--;
        }
    }

    /** The threshold above which choice menus should be displayed in multiple columns. */
    public static final int MULTI_COLUMN_THRESHOLD = 8;

    /** The value for the long option property. */
    private static final String OPTION_DSCFG_LONG_PROPERTY = "property";
    /** The value for the long option record. */
    private static final String OPTION_DSCFG_LONG_RECORD = "record";
    /** The value for the long option unit-size. */
    private static final String OPTION_DSCFG_LONG_UNIT_SIZE = "unit-size";
    /** The value for the long option unit-time. */
    private static final String OPTION_DSCFG_LONG_UNIT_TIME = "unit-time";
    /** The value for the short option property. */
    private static final Character OPTION_DSCFG_SHORT_PROPERTY = null;
    /** The value for the short option record. */
    private static final char OPTION_DSCFG_SHORT_RECORD = 'E';
    /** The value for the short option unit-size. */
    private static final char OPTION_DSCFG_SHORT_UNIT_SIZE = 'z';
    /** The value for the short option unit-time. */
    private static final char OPTION_DSCFG_SHORT_UNIT_TIME = 'm';

    /** The argument which should be used to specify zero or more property names. */
    private StringArgument propertyArgument;
    /** The argument which should be used to request record mode. */
    private BooleanArgument recordModeArgument;
    /** The argument which should be used to request specific size units. */
    private StringArgument unitSizeArgument;
    /** The argument which should be used to request specific time units. */
    private StringArgument unitTimeArgument;

    /** The tags associated with this sub-command handler. */
    private final Set<Tag> tags = new HashSet<>();
    /** The command builder associated with this handler. */
    private CommandBuilder commandBuilder;

    /**
     * The boolean that says whether is useful to display the command builder's contents after calling the run method or
     * not.
     */
    private boolean isCommandBuilderUseful = true;

    /** Create a new sub-command handler. */
    protected SubCommandHandler() {
        // No implementation required.
    }

    /** {@inheritDoc} */
    @Override
    public final int compareTo(SubCommandHandler o) {
        String s1 = getSubCommand().getName();
        String s2 = o.getSubCommand().getName();

        return s1.compareTo(s2);
    }

    /** {@inheritDoc} */
    @Override
    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof SubCommandHandler) {
            SubCommandHandler other = (SubCommandHandler) obj;

            String s1 = getSubCommand().getName();
            String s2 = other.getSubCommand().getName();
            return s1.equals(s2);
        } else {
            return false;
        }
    }

    /**
     * Gets the sub-command associated with this handler.
     *
     * @return Returns the sub-command associated with this handler.
     */
    public abstract SubCommand getSubCommand();

    /**
     * Gets the command builder associated with this handler. The method should be called after calling
     * <CODE>run()</CODE> method.
     *
     * @return Returns the sub-command associated with this handler.
     */
    public final CommandBuilder getCommandBuilder() {
        if (commandBuilder == null) {
            commandBuilder = new CommandBuilder(System.getProperty(PROPERTY_SCRIPT_NAME), getSubCommand().getName());
        }
        return commandBuilder;
    }

    /**
     * This method tells whether displaying the command builder contents makes sense or not. For instance in the case of
     * the help subcommand handler displaying information makes no much sense.
     *
     * @return <CODE>true</CODE> if displaying the command builder is useful and <CODE>false</CODE> otherwise.
     */
    public final boolean isCommandBuilderUseful() {
        return isCommandBuilderUseful;
    }

    /**
     * Sets wheter the command builder is useful or not.
     *
     * @param isCommandBuilderUseful
     *            whether the command builder is useful or not.
     */
    protected final void setCommandBuilderUseful(boolean isCommandBuilderUseful) {
        this.isCommandBuilderUseful = isCommandBuilderUseful;
    }

    /**
     * Gets the tags associated with this sub-command handler.
     *
     * @return Returns the tags associated with this sub-command handler.
     */
    public final Set<Tag> getTags() {
        return tags;
    }

    /** {@inheritDoc} */
    @Override
    public final int hashCode() {
        return getSubCommand().getName().hashCode();
    }

    /**
     * Run this sub-command handler.
     *
     * @param app
     *            The console application.
     * @param factory
     *            The LDAP management context factory context factory.
     * @return Returns a {@link MenuResult#success()} containing zero if the sub-command completed successfully or
     *         non-zero if it did not, or {@link MenuResult#quit()}, or {@link MenuResult#cancel()}, if the sub-command
     *         was run interactively and the user chose to quit or cancel.
     * @throws ArgumentException
     *             If an argument required by the sub-command could not be parsed successfully.
     * @throws ClientException
     *             If the management context could not be created.
     */
    public abstract MenuResult<Integer> run(ConsoleApplication app, LDAPManagementContextFactory factory)
            throws ArgumentException, ClientException;

    /**
     * Get the string representation of this sub-command handler.
     * <p>
     * The string representation is simply the sub-command's name.
     *
     * @return Returns the string representation of this sub-command handler.
     */
    @Override
    public final String toString() {
        return getSubCommand().getName();
    }

    /**
     * Adds one or more tags to this sub-command handler.
     *
     * @param tags
     *            The tags to be added to this sub-command handler.
     */
    protected final void addTags(Collection<Tag> tags) {
        this.tags.addAll(tags);
    }

    /**
     * Creates the naming arguments for a given path and registers them.
     *
     * @param subCommand
     *            The sub-command.
     * @param p
     *            The managed object path.
     * @param isCreate
     *            Indicates whether the sub-command is a create-xxx sub-command, in which case the final path element
     *            will have different usage information.
     * @return Returns the naming arguments.
     * @throws ArgumentException
     *             If one or more naming arguments could not be registered.
     */
    protected final List<StringArgument> createNamingArgs(SubCommand subCommand, ManagedObjectPath<?, ?> p,
            boolean isCreate) throws ArgumentException {
        return NamingArgumentBuilder.create(subCommand, p, isCreate);
    }

    /**
     * Creates a script-friendly table printer. This factory method should be used by sub-command handler
     * implementations rather than constructing a table printer directly so that we can easily switch table
     * implementations (perhaps dynamically depending on argument).
     *
     * @param stream
     *            The output stream for the table.
     * @return Returns a script-friendly table printer.
     */
    protected final TablePrinter createScriptFriendlyTablePrinter(PrintStream stream) {
        return new TabSeparatedTablePrinter(stream);
    }

    /**
     * Get the managed object referenced by the provided managed object path.
     *
     * @param app
     *            The console application.
     * @param context
     *            The management context.
     * @param path
     *            The managed object path.
     * @param args
     *            The list of managed object names required by the path.
     * @return Returns a {@link MenuResult#success()} containing the managed object referenced by the provided managed
     *         object path, or {@link MenuResult#quit()}, or {@link MenuResult#cancel()}, if the sub-command was run
     *         interactively and the user chose to quit or cancel.
     * @throws DefinitionDecodingException
     *             If the managed object was found but its type could not be determined.
     * @throws ManagedObjectDecodingException
     *             If the managed object was found but one or more of its properties could not be decoded.
     * @throws ManagedObjectNotFoundException
     *             If the requested managed object could not be found on the server.
     * @throws ConcurrentModificationException
     *             If this managed object has been removed from the server by another client.
     * @throws AuthorizationException
     *             If the server refuses to retrieve the managed object because the client does not have the correct
     *             privileges.
     * @throws ClientException
     *             If one of the naming arguments referenced a managed object of the wrong type.
     * @throws LdapException
     *             If any other error occurs.
     */
    protected final MenuResult<ManagedObject<?>> getManagedObject(ConsoleApplication app, ManagementContext context,
            ManagedObjectPath<?, ?> path, List<String> args) throws ClientException, AuthorizationException,
            DefinitionDecodingException, ManagedObjectDecodingException, ConcurrentModificationException,
            ManagedObjectNotFoundException, LdapException {
        ManagedObjectFinder finder = new ManagedObjectFinder();
        return finder.find(app, context, path, args);
    }

    /**
     * Gets the values of the naming arguments.
     *
     * @param app
     *            The console application.
     * @param namingArgs
     *            The naming arguments.
     * @return Returns the values of the naming arguments.
     * @throws ArgumentException
     *             If one of the naming arguments is missing and the application is non-interactive.
     */
    protected final List<String> getNamingArgValues(ConsoleApplication app, List<StringArgument> namingArgs)
            throws ArgumentException {
        ArrayList<String> values = new ArrayList<>(namingArgs.size());
        for (final StringArgument arg : namingArgs) {
            final String value = arg.getValue();
            if (value == null && !app.isInteractive()) {
                throw ArgumentExceptionFactory.missingMandatoryNonInteractiveArgument(arg);
            }
            values.add(value);
        }
        return values;
    }

    /**
     * Gets the optional list of property names that the user requested.
     *
     * @return Returns the optional list of property names that the user requested.
     */
    protected final Set<String> getPropertyNames() {
        if (propertyArgument != null) {
            return new LinkedHashSet<>(propertyArgument.getValues());
        }
        return Collections.emptySet();
    }

    /**
     * Gets the optional size unit that the user requested.
     *
     * @return Returns the size unit that the user requested, or <code>null</code> if no size unit was specified.
     * @throws ArgumentException
     *             If the user specified an invalid size unit.
     */
    protected final SizeUnit getSizeUnit() throws ArgumentException {
        if (unitSizeArgument != null) {
            final String value = unitSizeArgument.getValue();

            if (value != null) {
                try {
                    return SizeUnit.getUnit(value);
                } catch (IllegalArgumentException e) {
                    LocalizableMessage msg = INFO_DSCFG_ERROR_SIZE_UNIT_UNRECOGNIZED.get(value);
                    throw new ArgumentException(msg);
                }
            }
        }

        return null;
    }

    /**
     * Gets the optional time unit that the user requested.
     *
     * @return Returns the time unit that the user requested, or <code>null</code> if no time unit was specified.
     * @throws ArgumentException
     *             If the user specified an invalid time unit.
     */
    protected final DurationUnit getTimeUnit() throws ArgumentException {
        if (unitTimeArgument != null) {
            final String value = unitTimeArgument.getValue();

            if (value != null) {
                try {
                    return DurationUnit.getUnit(value);
                } catch (IllegalArgumentException e) {
                    LocalizableMessage msg = INFO_DSCFG_ERROR_TIME_UNIT_UNRECOGNIZED.get(value);
                    throw new ArgumentException(msg);
                }
            }
        }

        return null;
    }

    /**
     * Determines whether the user requested record-mode.
     *
     * @return Returns <code>true</code> if the user requested record-mode.
     */
    protected final boolean isRecordMode() {
        return recordModeArgument != null && recordModeArgument.isPresent();
    }

    /**
     * Interactively prompts the user to select from a choice of child managed objects.
     * <p>
     * This method will adapt according to the available choice. For example, if there is only one choice, then a
     * question will be asked. If there are no children then an <code>ArgumentException</code> will be thrown.
     *
     * @param <C>
     *            The type of child client configuration.
     * @param <S>
     *            The type of child server configuration.
     * @param app
     *            The console application.
     * @param parent
     *            The parent managed object.
     * @param r
     *            The relation between the parent and the children, must be a set or instantiable relation.
     * @param d
     *            The type of child managed object to choose from.
     * @return Returns a {@link MenuResult#success()} containing the name of the managed object that the user selected,
     *         or {@link MenuResult#quit()}, or {@link MenuResult#cancel()}, if the sub-command was run interactive and
     *         the user chose to quit or cancel.
     * @throws ConcurrentModificationException
     *             If the parent managed object has been deleted.
     * @throws AuthorizationException
     *             If the children cannot be listed due to an authorization failure.
     * @throws ClientException
     *             If the user input can be read from the console or if there are no children.
     */
    protected final <C extends ConfigurationClient, S extends Configuration> MenuResult<String> readChildName(
            ConsoleApplication app, ManagedObject<?> parent, RelationDefinition<C, S> r,
            AbstractManagedObjectDefinition<? extends C, ? extends S> d) throws AuthorizationException,
            ConcurrentModificationException, ClientException {
        if (d == null) {
            d = r.getChildDefinition();
        }

        app.println();
        app.println();

        // Filter out advanced and hidden types if required.
        String[] childNames = null;
        try {
            if (r instanceof InstantiableRelationDefinition) {
                childNames = parent.listChildren((InstantiableRelationDefinition<C, S>) r, d);
            } else {
                childNames = parent.listChildren((SetRelationDefinition<C, S>) r, d);
            }
        } catch (LdapException e) {
            // FIXME check exceptions
            System.out.println(String.format("An error occured %s", e.getMessage()));
        }

        SortedMap<String, String> children = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (String childName : childNames) {
            ManagedObject<?> child;
            try {
                if (r instanceof InstantiableRelationDefinition) {
                    child = parent.getChild((InstantiableRelationDefinition<C, S>) r, childName);
                } else {
                    child = parent.getChild((SetRelationDefinition<C, S>) r, childName);
                }

                ManagedObjectDefinition<?, ?> cd = child.getManagedObjectDefinition();
                if (cannotDisplay(app, cd, null)) {
                    continue;
                }

                if (r instanceof InstantiableRelationDefinition) {
                    children.put(childName, childName);
                } else {
                    // For sets the RDN is the type string, the ufn is more friendly.
                    children.put(cd.getUserFriendlyName().toString(), childName);
                }
            } catch (DefinitionDecodingException | ManagedObjectDecodingException | LdapException e) {
                // Add it anyway: maybe the user is trying to fix the problem.
                children.put(childName, childName);
            } catch (ManagedObjectNotFoundException e) {
                // Skip it - the managed object has been concurrently removed.
            }
        }

        switch (children.size()) {
        case 0: {
            // No options available - abort.
            app.errPrintln(ERR_DSCFG_ERROR_FINDER_NO_CHILDREN.get(d.getUserFriendlyPluralName()));
            return MenuResult.cancel();
        }
        case 1: {
            // Only one option available so confirm that the user wishes to
            // access it.
            LocalizableMessage msg = INFO_DSCFG_FINDER_PROMPT_SINGLE.get(d.getUserFriendlyName(), children.firstKey());
            if (app.confirmAction(msg, true)) {
                try {
                    String argName = CLIProfile.getInstance().getNamingArgument(r);
                    StringArgument arg = new StringArgument(argName, null, argName, false, true,
                            INFO_NAME_PLACEHOLDER.get(),
                            INFO_DSCFG_DESCRIPTION_NAME_CREATE.get(d.getUserFriendlyName()));
                    if (r instanceof InstantiableRelationDefinition) {
                        arg.addValue(children.get(children.firstKey()));
                    } else {
                        // Set relation: need the short type name.
                        String friendlyName = children.firstKey();
                        String shortName = children.get(friendlyName);
                        try {
                            AbstractManagedObjectDefinition<?, ?> cd = null;
                            try {
                                cd = d.getChild(shortName);
                            } catch (IllegalArgumentException e) {
                                // Last resource: try with friendly name
                                cd = d.getChild(friendlyName);
                            }
                            arg.addValue(getShortTypeName(r.getChildDefinition(), cd));
                        } catch (IllegalArgumentException e) {
                            arg.addValue(shortName);
                        }
                    }
                    getCommandBuilder().addArgument(arg);
                } catch (Throwable t) {
                    // Bug
                    throw new RuntimeException("Unexpected exception: " + t, t);
                }
                return MenuResult.success(children.get(children.firstKey()));
            } else {
                return MenuResult.cancel();
            }
        }
        default: {
            // Display a menu.
            MenuBuilder<String> builder = new MenuBuilder<>(app);
            builder.setMultipleColumnThreshold(MULTI_COLUMN_THRESHOLD);
            builder.setPrompt(INFO_DSCFG_FINDER_PROMPT_MANY.get(d.getUserFriendlyName()));

            for (Map.Entry<String, String> child : children.entrySet()) {
                LocalizableMessage option = LocalizableMessage.raw("%s", child.getKey());
                builder.addNumberedOption(option, MenuResult.success(child.getValue()));
            }

            if (app.isMenuDrivenMode()) {
                builder.addCancelOption(true);
            }
            builder.addQuitOption();

            Menu<String> menu = builder.toMenu();
            MenuResult<String> result = menu.run();
            try {
                if (result.getValue() == null) {
                    // nothing has been entered ==> cancel
                    return MenuResult.cancel();
                }
                String argName = CLIProfile.getInstance().getNamingArgument(r);
                StringArgument arg = new StringArgument(argName, null, argName, false, true,
                        INFO_NAME_PLACEHOLDER.get(), INFO_DSCFG_DESCRIPTION_NAME_CREATE.get(d.getUserFriendlyName()));
                if (r instanceof InstantiableRelationDefinition) {
                    arg.addValue(result.getValue());
                } else {
                    // Set relation: need the short type name.
                    String longName = result.getValue();
                    try {
                        AbstractManagedObjectDefinition<?, ?> cd = d.getChild(longName);
                        arg.addValue(getShortTypeName(r.getChildDefinition(), cd));
                    } catch (IllegalArgumentException e) {
                        arg.addValue(children.get(result.getValue()));
                    }
                }
                getCommandBuilder().addArgument(arg);
            } catch (Throwable t) {
                // Bug
                throw new RuntimeException("Unexpected exception: " + t, t);
            }
            return result;
        }
        }
    }

    /**
     * Skips:
     *
     * <ul>
     * <li>hidden types</li>
     * <li>advanced types if required</li>
     * <li>if this does not have the required tag</li>
     * </ul>.
     */
    static boolean cannotDisplay(ConsoleApplication app, AbstractManagedObjectDefinition<?, ?> defn, Tag tag) {
        return defn.hasOption(ManagedObjectOption.HIDDEN)
                || (!app.isAdvancedMode() && defn.hasOption(ManagedObjectOption.ADVANCED))
                || (tag != null && !defn.hasTag(tag));
    }

    static boolean cannotDisplay(ConsoleApplication app, PropertyDefinition<?> pd, Set<String> allowedPropertyNames) {
        return pd.hasOption(PropertyOption.HIDDEN)
                || (!app.isAdvancedMode() && pd.hasOption(PropertyOption.ADVANCED))
                || (!allowedPropertyNames.isEmpty() && !allowedPropertyNames.contains(pd.getName()));
    }

    /**
     * Registers the property name argument with the sub-command.
     *
     * @param subCommand
     *            The sub-command.
     * @throws ArgumentException
     *             If the property name argument could not be registered.
     */
    protected final void registerPropertyNameArgument(SubCommand subCommand) throws ArgumentException {
        this.propertyArgument = new StringArgument(OPTION_DSCFG_LONG_PROPERTY, OPTION_DSCFG_SHORT_PROPERTY,
                OPTION_DSCFG_LONG_PROPERTY, false, true, true, INFO_PROPERTY_PLACEHOLDER.get(), null, null,
                INFO_DSCFG_DESCRIPTION_PROP.get());
        subCommand.addArgument(propertyArgument);
    }

    /**
     * Registers the record mode argument with the sub-command.
     *
     * @param subCommand
     *            The sub-command.
     * @throws ArgumentException
     *             If the record mode argument could not be registered.
     */
    protected final void registerRecordModeArgument(SubCommand subCommand) throws ArgumentException {
        this.recordModeArgument = new BooleanArgument(OPTION_DSCFG_LONG_RECORD, OPTION_DSCFG_SHORT_RECORD,
                OPTION_DSCFG_LONG_RECORD, INFO_DSCFG_DESCRIPTION_RECORD.get());
        this.recordModeArgument.setPropertyName(OPTION_DSCFG_LONG_RECORD);
        subCommand.addArgument(recordModeArgument);
    }

    /**
     * Registers the unit-size argument with the sub-command.
     *
     * @param subCommand
     *            The sub-command.
     * @throws ArgumentException
     *             If the unit-size argument could not be registered.
     */
    protected final void registerUnitSizeArgument(SubCommand subCommand) throws ArgumentException {
        this.unitSizeArgument = new StringArgument(OPTION_DSCFG_LONG_UNIT_SIZE, OPTION_DSCFG_SHORT_UNIT_SIZE,
                OPTION_DSCFG_LONG_UNIT_SIZE, false, true, INFO_UNIT_PLACEHOLDER.get(),
                INFO_DSCFG_DESCRIPTION_UNIT_SIZE.get());
        this.unitSizeArgument.setPropertyName(OPTION_DSCFG_LONG_UNIT_SIZE);

        subCommand.addArgument(unitSizeArgument);
    }

    /**
     * Registers the unit-time argument with the sub-command.
     *
     * @param subCommand
     *            The sub-command.
     * @throws ArgumentException
     *             If the unit-time argument could not be registered.
     */
    protected final void registerUnitTimeArgument(SubCommand subCommand) throws ArgumentException {
        this.unitTimeArgument = new StringArgument(OPTION_DSCFG_LONG_UNIT_TIME, OPTION_DSCFG_SHORT_UNIT_TIME,
                OPTION_DSCFG_LONG_UNIT_TIME, false, true, INFO_UNIT_PLACEHOLDER.get(),
                INFO_DSCFG_DESCRIPTION_UNIT_TIME.get());
        this.unitTimeArgument.setPropertyName(OPTION_DSCFG_LONG_UNIT_TIME);

        subCommand.addArgument(unitTimeArgument);
    }

    /**
     * Updates the command builder with the arguments defined in the sub command. This implies basically putting the
     * arguments provided by the user in the command builder.
     */
    protected final void updateCommandBuilderWithSubCommand() {
        for (Argument arg : getSubCommand().getArguments()) {
            if (arg.isPresent()) {
                getCommandBuilder().addArgument(arg);
            }
        }
    }

    /**
     * Returns the string value for a given object as it will be displayed in the equivalent command-line. The code will
     * cast the provided object using the property definition.
     *
     * @param propertyDefinition
     *            the property definition.
     * @param o
     *            the value.
     * @param <T>
     *            the type of the property to be retrieved.
     * @return the String value to be displayed in the equivalent command-line.
     */
    static <T> String castAndGetArgumentValue(PropertyDefinition<T> propertyDefinition, Object o) {
        return propertyDefinition.encodeValue(propertyDefinition.castValue(o));
    }

    /**
     * Returns the string value for a given object as it will be displayed in the equivalent command-line.
     *
     * @param propertyDefinition
     *            the property definition.
     * @param o
     *            the value.
     * @param <T>
     *            the type of the property to be retrieved.
     * @return the String value to be displayed in the equivalent command-line.
     */
    static <T> String getArgumentValue(PropertyDefinition<T> propertyDefinition, T o) {
        return propertyDefinition.encodeValue(o);
    }

    /**
     * Returns a mapping of subordinate managed object type argument values to their corresponding managed object
     * definitions for the provided managed object definition.
     *
     * @param <C>
     *            The type of client configuration.
     * @param <S>
     *            The type of server configuration.
     * @param d
     *            The managed object definition.
     * @return A mapping of managed object type argument values to their corresponding managed object definitions.
     */
    static <C extends ConfigurationClient, S extends Configuration> SortedMap<String,
        ManagedObjectDefinition<? extends C, ? extends S>> getSubTypes(
            AbstractManagedObjectDefinition<C, S> d) {
        SortedMap<String, ManagedObjectDefinition<? extends C, ? extends S>> map = new TreeMap<>();

        // If the top-level definition is instantiable, we use the value
        // "generic" or "custom".
        if (!d.hasOption(ManagedObjectOption.HIDDEN) && d instanceof ManagedObjectDefinition) {
            ManagedObjectDefinition<? extends C, ? extends S> mod
                = (ManagedObjectDefinition<? extends C, ? extends S>) d;
            map.put(getShortTypeName(d, mod), mod);
        }

        // Process its sub-definitions.
        for (AbstractManagedObjectDefinition<? extends C, ? extends S> c : d.getAllChildren()) {
            if (c.hasOption(ManagedObjectOption.HIDDEN)) {
                continue;
            }

            if (c instanceof ManagedObjectDefinition) {
                ManagedObjectDefinition<? extends C, ? extends S> mod
                    = (ManagedObjectDefinition<? extends C, ? extends S>) c;
                map.put(getShortTypeName(d, mod), mod);
            }
        }

        return map;
    }

    /**
     * Returns the type short name for a child managed object definition.
     *
     * @param <C>
     *            The type of client configuration.
     * @param <S>
     *            The type of server configuration.
     * @param d
     *            The top level parent definition.
     * @param c
     *            The child definition.
     * @return The type short name.
     */
    private static <C extends ConfigurationClient, S extends Configuration> String getShortTypeName(
            AbstractManagedObjectDefinition<C, S> d, AbstractManagedObjectDefinition<?, ?> c) {
        if (c == d) {
            // This is the top-level definition, so use the value "generic" or
            // "custom".
            if (CLIProfile.getInstance().isForCustomization(c)) {
                return DSConfig.CUSTOM_TYPE;
            } else {
                return DSConfig.GENERIC_TYPE;
            }
        } else {
            // It's a child definition.
            String suffix = "-" + d.getName();

            // For the type name we shorten it, if possible, by stripping
            // off the trailing part of the name which matches the
            // base-type.
            String name = c.getName();
            if (name.endsWith(suffix)) {
                name = name.substring(0, name.length() - suffix.length());
            }

            // If this type is intended for customization, prefix it with
            // "custom".
            if (CLIProfile.getInstance().isForCustomization(c)) {
                name = String.format("%s-%s", DSConfig.CUSTOM_TYPE, name);
            }

            return name;
        }
    }

    /**
     * Returns a usage string representing the list of possible types for the provided managed object definition.
     *
     * @param d
     *            The managed object definition.
     * @return A usage string representing the list of possible types for the provided managed object definition.
     */
    static String getSubTypesUsage(AbstractManagedObjectDefinition<?, ?> d) {
        // Build the -t option usage.
        SortedMap<String, ?> types = getSubTypes(d);
        StringBuilder builder = new StringBuilder();
        boolean isFirst = true;
        for (String s : types.keySet()) {
            if (!isFirst) {
                builder.append(" | ");
            }
            builder.append(s);
            isFirst = false;
        }
        return builder.toString();
    }
}
