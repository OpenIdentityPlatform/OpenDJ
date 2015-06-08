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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.forgerock.opendj.config.dsconfig;

import static com.forgerock.opendj.cli.CliMessages.*;
import static com.forgerock.opendj.cli.Utils.*;
import static com.forgerock.opendj.dsconfig.DsconfigMessages.*;

import java.io.PrintStream;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.config.AbsoluteInheritedDefaultBehaviorProvider;
import org.forgerock.opendj.config.AbstractManagedObjectDefinition;
import org.forgerock.opendj.config.AdministratorAction;
import org.forgerock.opendj.config.AggregationPropertyDefinition;
import org.forgerock.opendj.config.AliasDefaultBehaviorProvider;
import org.forgerock.opendj.config.DefaultBehaviorProviderVisitor;
import org.forgerock.opendj.config.DefinedDefaultBehaviorProvider;
import org.forgerock.opendj.config.EnumPropertyDefinition;
import org.forgerock.opendj.config.PropertyDefinition;
import org.forgerock.opendj.config.PropertyDefinitionUsageBuilder;
import org.forgerock.opendj.config.PropertyDefinitionVisitor;
import org.forgerock.opendj.config.PropertyOption;
import org.forgerock.opendj.config.RelativeInheritedDefaultBehaviorProvider;
import org.forgerock.opendj.config.StringPropertyDefinition;
import org.forgerock.opendj.config.Tag;
import org.forgerock.opendj.config.UndefinedDefaultBehaviorProvider;
import org.forgerock.opendj.config.client.ManagedObject;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.MenuResult;
import com.forgerock.opendj.cli.StringArgument;
import com.forgerock.opendj.cli.SubCommand;
import com.forgerock.opendj.cli.SubCommandArgumentParser;
import com.forgerock.opendj.cli.TableBuilder;
import com.forgerock.opendj.cli.TablePrinter;
import com.forgerock.opendj.cli.TextTablePrinter;

/**
 * A sub-command handler which is used to display help about managed objects and their properties.
 * <p>
 * This sub-command implements the help-properties sub-command.
 */
final class HelpSubCommandHandler extends SubCommandHandler {

    /** This class is used to print the default behavior of a property. */
    private static class DefaultBehaviorPrinter {

        /**
         * The default behavior printer visitor implementation.
         *
         * @param <T>
         *            The property type.
         */
        private static class DefaultVisitor<T> implements
                DefaultBehaviorProviderVisitor<T, LocalizableMessage, PropertyDefinition<T>> {

            /** {@inheritDoc} */
            public LocalizableMessage visitAbsoluteInherited(AbsoluteInheritedDefaultBehaviorProvider<T> d,
                    PropertyDefinition<T> p) {
                return INFO_DSCFG_HELP_FIELD_INHERITED_ABS.get(d.getPropertyName(), d.getManagedObjectPath()
                        .getRelationDefinition().getUserFriendlyName());
            }

            /** {@inheritDoc} */
            public LocalizableMessage visitAlias(AliasDefaultBehaviorProvider<T> d, PropertyDefinition<T> p) {
                return d.getSynopsis();
            }

            /** {@inheritDoc} */
            public LocalizableMessage visitDefined(DefinedDefaultBehaviorProvider<T> d, PropertyDefinition<T> p) {
                LocalizableMessageBuilder builder = new LocalizableMessageBuilder();
                PropertyValuePrinter printer = new PropertyValuePrinter(null, null, false);
                boolean isFirst = true;
                for (String s : d.getDefaultValues()) {
                    if (!isFirst) {
                        builder.append(", ");
                    }

                    T value = p.decodeValue(s);
                    builder.append(printer.print(p, value));
                }

                return builder.toMessage();
            }

            /** {@inheritDoc} */
            public LocalizableMessage visitRelativeInherited(RelativeInheritedDefaultBehaviorProvider<T> d,
                    PropertyDefinition<T> p) {
                if (d.getRelativeOffset() == 0) {
                    return INFO_DSCFG_HELP_FIELD_INHERITED_THIS.get(d.getPropertyName(), d.getManagedObjectDefinition()
                            .getUserFriendlyName());
                } else {
                    return INFO_DSCFG_HELP_FIELD_INHERITED_PARENT.get(d.getPropertyName(), d
                            .getManagedObjectDefinition().getUserFriendlyName());
                }
            }

            /** {@inheritDoc} */
            public LocalizableMessage visitUndefined(UndefinedDefaultBehaviorProvider<T> d, PropertyDefinition<T> p) {
                return INFO_DSCFG_HELP_FIELD_UNDEFINED.get();
            }
        }

        /**
         * Create a new default behavior printer.
         */
        public DefaultBehaviorPrinter() {
            // No implementation required.
        }

        /**
         * Get a user-friendly description of a property's default behavior.
         *
         * @param <T>
         *            The type of the property definition.
         * @param pd
         *            The property definition.
         * @return Returns the user-friendly description of a property's default behavior.
         */
        public <T> LocalizableMessage print(PropertyDefinition<T> pd) {
            DefaultVisitor<T> v = new DefaultVisitor<>();
            return pd.getDefaultBehaviorProvider().accept(v, pd);
        }
    }

    /** This class is used to print detailed syntax information about a property. */
    private static class SyntaxPrinter {

        /** The syntax printer visitor implementation. */
        private static final class Visitor extends PropertyDefinitionVisitor<Void, PrintStream> {

            /** Private constructor. */
            private Visitor() {
                // No implementation required.
            }

            /** {@inheritDoc} */
            @Override
            public <E extends Enum<E>> Void visitEnum(EnumPropertyDefinition<E> d, PrintStream p) {
                displayUsage(p, INFO_DSCFG_HELP_FIELD_ENUM.get());
                p.println();

                TableBuilder builder = new TableBuilder();
                boolean isFirst = true;
                for (E value : EnumSet.<E> allOf(d.getEnumClass())) {
                    if (!isFirst) {
                        builder.startRow();
                    }

                    builder.startRow();
                    builder.appendCell();
                    builder.appendCell();
                    builder.appendCell(value.toString());
                    builder.appendCell(HEADING_SEPARATOR);
                    builder.appendCell(d.getValueSynopsis(value));

                    isFirst = false;
                }

                TextTablePrinter factory = new TextTablePrinter(p);
                factory.setDisplayHeadings(false);
                factory.setColumnWidth(0, HEADING_WIDTH);
                factory.setColumnWidth(1, HEADING_SEPARATOR.length());
                factory.setColumnWidth(4, 0);
                factory.setPadding(0);

                builder.print(factory);

                return null;
            }

            /** {@inheritDoc} */
            @Override
            public Void visitString(StringPropertyDefinition d, PrintStream p) {
                PropertyDefinitionUsageBuilder usageBuilder = new PropertyDefinitionUsageBuilder(false);

                TableBuilder builder = new TableBuilder();
                builder.startRow();
                builder.appendCell(INFO_DSCFG_HELP_HEADING_SYNTAX.get());
                builder.appendCell(HEADING_SEPARATOR);
                builder.appendCell(usageBuilder.getUsage(d));

                if (d.getPattern() != null) {
                    builder.startRow();
                    builder.startRow();
                    builder.appendCell();
                    builder.appendCell();
                    builder.appendCell(d.getPatternSynopsis());
                }

                TextTablePrinter factory = new TextTablePrinter(p);
                factory.setDisplayHeadings(false);
                factory.setColumnWidth(0, HEADING_WIDTH);
                factory.setColumnWidth(2, 0);
                factory.setPadding(0);

                builder.print(factory);

                return null;
            }

            /** {@inheritDoc} */
            @Override
            public <T> Void visitUnknown(PropertyDefinition<T> d, PrintStream p) {
                PropertyDefinitionUsageBuilder usageBuilder = new PropertyDefinitionUsageBuilder(true);
                displayUsage(p, usageBuilder.getUsage(d));

                return null;
            }

            /** Common usage. */
            private void displayUsage(PrintStream p, LocalizableMessage usage) {
                TableBuilder builder = new TableBuilder();
                builder.startRow();
                builder.appendCell(INFO_DSCFG_HELP_HEADING_SYNTAX.get());
                builder.appendCell(HEADING_SEPARATOR);
                builder.appendCell(usage);

                TextTablePrinter factory = new TextTablePrinter(p);
                factory.setDisplayHeadings(false);
                factory.setColumnWidth(0, HEADING_WIDTH);
                factory.setColumnWidth(2, 0);
                factory.setPadding(0);

                builder.print(factory);
            }
        }

        /** The private implementation. */
        private final Visitor pimpl;

        /**
         * Creates a new syntax printer which can be used to print detailed syntax information about a property.
         */
        public SyntaxPrinter() {
            this.pimpl = new Visitor();
        }

        /**
         * Print detailed syntax information about a property definition.
         *
         * @param out
         *            The output stream.
         * @param pd
         *            The property definition.
         */
        public void print(PrintStream out, PropertyDefinition<?> pd) {
            pd.accept(pimpl, out);
        }
    }

    /** Strings used in property help. */
    private static final String HEADING_SEPARATOR = " : ";

    /** Width of biggest heading (need to be careful of I18N). */
    private static final int HEADING_WIDTH;

    /**
     * The value for the long option category.
     */
    private static final String OPTION_DSCFG_LONG_CATEGORY = "category";

    /**
     * The value for the long option inherited.
     */
    private static final String OPTION_DSCFG_LONG_INHERITED = "inherited";

    /**
     * The value for the long option type.
     */
    private static final String OPTION_DSCFG_LONG_TYPE = "type";

    /**
     * The value for the short option category.
     */
    private static final Character OPTION_DSCFG_SHORT_CATEGORY = 'c';

    /**
     * The value for the short option inherited.
     */
    private static final Character OPTION_DSCFG_SHORT_INHERITED = null;

    /**
     * The value for the short option type.
     */
    private static final Character OPTION_DSCFG_SHORT_TYPE = 't';

    static {
        int tmp = INFO_DSCFG_HELP_HEADING_SYNTAX.get().length();
        tmp = Math.max(tmp, INFO_DSCFG_HELP_HEADING_DEFAULT.get().length());
        tmp = Math.max(tmp, INFO_DSCFG_HELP_HEADING_MULTI_VALUED.get().length());
        tmp = Math.max(tmp, INFO_DSCFG_HELP_HEADING_MANDATORY.get().length());
        tmp = Math.max(tmp, INFO_DSCFG_HELP_HEADING_READ_ONLY.get().length());
        HEADING_WIDTH = tmp;
    }

    /**
     * Creates a new help-properties sub-command.
     *
     * @param parser
     *            The sub-command argument parser.
     * @return Returns the new help-properties sub-command.
     * @throws ArgumentException
     *             If the sub-command could not be created successfully.
     */
    public static HelpSubCommandHandler create(SubCommandArgumentParser parser) throws ArgumentException {
        return new HelpSubCommandHandler(parser);
    }

    /**
     * Displays detailed help about a single component to the specified output stream.
     *
     * @param app
     *            The application console.
     * @param mo
     *            The managed object.
     * @param c
     *            The collection of properties to be displayed.
     */
    public static void displaySingleComponent(ConsoleApplication app, ManagedObject<?> mo,
            Collection<PropertyDefinition<?>> c) {
        String ufn = mo.getManagedObjectPath().getName();
        if (ufn == null) {
            ufn = mo.getManagedObjectDefinition().getUserFriendlyName().toString();
        }
        // Display the title.
        app.println(INFO_DSCFG_HELP_HEADING_COMPONENT.get(ufn));

        final AbstractManagedObjectDefinition<?, ?> d = mo.getManagedObjectDefinition();

        app.println();
        app.println(d.getSynopsis());
        if (d.getDescription() != null) {
            app.println();
            app.println(d.getDescription());
        }

        app.println();
        app.println();
        displayPropertyOptionKey(app);

        app.println();
        app.println();

        // Headings.
        final TableBuilder builder = new TableBuilder();

        builder.appendHeading(INFO_DSCFG_HEADING_PROPERTY_NAME.get());
        builder.appendHeading(INFO_DSCFG_HEADING_PROPERTY_OPTIONS.get());
        builder.appendHeading(INFO_DSCFG_HEADING_PROPERTY_SYNTAX.get());

        // Sort keys.
        builder.addSortKey(0);

        // Output summary of each property.
        for (final PropertyDefinition<?> pd : c) {
            // Display the property.
            builder.startRow();

            // Display the property name.
            builder.appendCell(pd.getName());

            // Display the options.
            builder.appendCell(getPropertyOptionSummary(pd));

            // Display the syntax.
            final PropertyDefinitionUsageBuilder v = new PropertyDefinitionUsageBuilder(false);
            builder.appendCell(v.getUsage(pd));
        }

        builder.print(new TextTablePrinter(app.getErrorStream()));
    }

    /**
     * Displays detailed help about a single property to the specified output stream.
     *
     * @param app
     *            The application console.
     * @param d
     *            The managed object definition.
     * @param name
     *            The name of the property definition.
     */
    public static void displayVerboseSingleProperty(ConsoleApplication app, AbstractManagedObjectDefinition<?, ?> d,
            String name) {
        PropertyDefinition<?> pd = d.getPropertyDefinition(name);

        // Display the title.
        app.println(INFO_DSCFG_HELP_HEADING_PROPERTY.get(name));

        // Display the property synopsis and description.
        app.println();
        app.println(pd.getSynopsis(), 4);

        if (pd.getDescription() != null) {
            app.println();
            app.println(pd.getDescription(), 4);
        }

        if (pd instanceof AggregationPropertyDefinition) {
            AggregationPropertyDefinition<?, ?> apd = (AggregationPropertyDefinition<?, ?>) pd;
            if (apd.getSourceConstraintSynopsis() != null) {
                app.println();
                app.println(apd.getSourceConstraintSynopsis(), 4);
            }
        }

        // Display the syntax.
        app.println();
        SyntaxPrinter syntaxPrinter = new SyntaxPrinter();
        syntaxPrinter.print(app.getErrorStream(), pd);

        // Display remaining information in a table.
        app.println();
        TableBuilder builder = new TableBuilder();

        // Display the default behavior.
        DefaultBehaviorPrinter defaultPrinter = new DefaultBehaviorPrinter();

        builder.startRow();
        builder.appendCell(INFO_DSCFG_HELP_HEADING_DEFAULT.get());
        builder.appendCell(HEADING_SEPARATOR);
        builder.appendCell(defaultPrinter.print(pd));

        // Display options.
        builder.startRow();
        builder.appendCell(INFO_DSCFG_HELP_HEADING_ADVANCED.get());
        builder.appendCell(HEADING_SEPARATOR);
        builder.appendCell(hasOptionYN(pd, PropertyOption.ADVANCED));

        builder.startRow();
        builder.appendCell(INFO_DSCFG_HELP_HEADING_MULTI_VALUED.get());
        builder.appendCell(HEADING_SEPARATOR);
        builder.appendCell(hasOptionYN(pd, PropertyOption.MULTI_VALUED));

        builder.startRow();
        builder.appendCell(INFO_DSCFG_HELP_HEADING_MANDATORY.get());
        builder.appendCell(HEADING_SEPARATOR);
        builder.appendCell(hasOptionYN(pd, PropertyOption.MANDATORY));

        builder.startRow();
        builder.appendCell(INFO_DSCFG_HELP_HEADING_READ_ONLY.get());
        builder.appendCell(HEADING_SEPARATOR);
        if (pd.hasOption(PropertyOption.MONITORING)) {
            builder.appendCell(INFO_DSCFG_HELP_FIELD_MONITORING.get());
        } else if (pd.hasOption(PropertyOption.READ_ONLY)) {
            builder.appendCell(INFO_DSCFG_HELP_FIELD_READ_ONLY.get(d.getUserFriendlyName()));
        } else {
            builder.appendCell(INFO_GENERAL_NO.get());
        }

        TextTablePrinter factory = new TextTablePrinter(app.getErrorStream());
        factory.setDisplayHeadings(false);
        factory.setColumnWidth(0, HEADING_WIDTH);
        factory.setColumnWidth(2, 0);
        factory.setPadding(0);
        builder.print(factory);

        // Administrator action.
        LocalizableMessage synopsis = getSynopsis(d, pd);
        if (synopsis != null) {
            app.println();
            app.println(synopsis);
        }
    }

    private static LocalizableMessage hasOptionYN(PropertyDefinition<?> pd, PropertyOption option) {
        return pd.hasOption(option) ? INFO_GENERAL_YES.get() : INFO_GENERAL_NO.get();
    }

    private static LocalizableMessage getSynopsis(AbstractManagedObjectDefinition<?, ?> d, PropertyDefinition<?> pd) {
        AdministratorAction action = pd.getAdministratorAction();
        LocalizableMessage synopsis = action.getSynopsis();
        if (synopsis != null) {
            return synopsis;
        }

        switch (action.getType()) {
        case COMPONENT_RESTART:
            return INFO_DSCFG_HELP_FIELD_COMPONENT_RESTART.get(d.getUserFriendlyName());
        case SERVER_RESTART:
            return INFO_DSCFG_HELP_FIELD_SERVER_RESTART.get();
        default:
            return null;
        }
    }

    /** Displays the property option summary key. */
    private static void displayPropertyOptionKey(ConsoleApplication app) {
        LocalizableMessageBuilder builder;

        app.println(INFO_DSCFG_HELP_DESCRIPTION_OPTION.get());
        app.println();

        builder = new LocalizableMessageBuilder();
        builder.append(" r -- ");
        builder.append(INFO_DSCFG_HELP_DESCRIPTION_READ.get());
        app.println(builder.toMessage());

        builder = new LocalizableMessageBuilder();
        builder.append(" w -- ");
        builder.append(INFO_DSCFG_HELP_DESCRIPTION_WRITE.get());
        app.println(builder.toMessage());

        builder = new LocalizableMessageBuilder();
        builder.append(" m -- ");
        builder.append(INFO_DSCFG_HELP_DESCRIPTION_MANDATORY.get());
        app.println(builder.toMessage());

        builder = new LocalizableMessageBuilder();
        builder.append(" s -- ");
        builder.append(INFO_DSCFG_HELP_DESCRIPTION_SINGLE_VALUED.get());
        app.println(builder.toMessage());

        builder = new LocalizableMessageBuilder();
        builder.append(" a -- ");
        builder.append(INFO_DSCFG_HELP_DESCRIPTION_ADMIN_ACTION.get());
        app.println(builder.toMessage());
    }

    /** Compute the options field. */
    private static String getPropertyOptionSummary(PropertyDefinition<?> pd) {
        StringBuilder b = new StringBuilder();

        if (pd.hasOption(PropertyOption.MONITORING) || pd.hasOption(PropertyOption.READ_ONLY)) {
            b.append("r-");
        } else {
            b.append("rw");
        }

        if (pd.hasOption(PropertyOption.MANDATORY)) {
            b.append('m');
        } else {
            b.append('-');
        }

        if (pd.hasOption(PropertyOption.MULTI_VALUED)) {
            b.append('-');
        } else {
            b.append('s');
        }

        AdministratorAction action = pd.getAdministratorAction();
        if (action.getType() != AdministratorAction.Type.NONE) {
            b.append('a');
        } else {
            b.append('-');
        }
        return b.toString();
    }

    /**
     * The argument which should be used to specify the category of managed object to be retrieved.
     */
    private final StringArgument categoryArgument;

    /**
     * A table listing all the available types of managed object indexed on their parent type.
     */
    private final Map<String, Map<String, AbstractManagedObjectDefinition<?, ?>>> categoryMap = new TreeMap<>();

    /** The argument which should be used to display inherited properties. */
    private BooleanArgument inheritedModeArgument;

    /** The sub-command associated with this handler. */
    private final SubCommand subCommand;

    /**
     * A table listing all the available types of managed object indexed on their tag(s).
     */
    private final Map<Tag, Map<String, AbstractManagedObjectDefinition<?, ?>>> tagMap = new HashMap<>();

    /**
     * The argument which should be used to specify the sub-type of managed object to be retrieved.
     */
    private final StringArgument typeArgument;

    /** Private constructor. */
    private HelpSubCommandHandler(SubCommandArgumentParser parser) throws ArgumentException {
        // Create the sub-command.
        String name = "list-properties";
        LocalizableMessage desc = INFO_DSCFG_DESCRIPTION_SUBCMD_HELPPROP.get();
        this.subCommand = new SubCommand(parser, name, false, 0, 0, null, desc);

        this.categoryArgument = new StringArgument(OPTION_DSCFG_LONG_CATEGORY, OPTION_DSCFG_SHORT_CATEGORY,
                OPTION_DSCFG_LONG_CATEGORY, false, false, true, INFO_CATEGORY_PLACEHOLDER.get(), null, null,
                INFO_DSCFG_DESCRIPTION_HELP_CATEGORY.get());
        this.subCommand.addArgument(this.categoryArgument);

        this.typeArgument = new StringArgument(OPTION_DSCFG_LONG_TYPE, OPTION_DSCFG_SHORT_TYPE, OPTION_DSCFG_LONG_TYPE,
                false, false, true, INFO_TYPE_PLACEHOLDER.get(), null, null, INFO_DSCFG_DESCRIPTION_HELP_TYPE.get());
        this.subCommand.addArgument(this.typeArgument);

        this.inheritedModeArgument = new BooleanArgument(OPTION_DSCFG_LONG_INHERITED, OPTION_DSCFG_SHORT_INHERITED,
                OPTION_DSCFG_LONG_INHERITED, INFO_DSCFG_DESCRIPTION_HELP_INHERITED.get());
        subCommand.addArgument(inheritedModeArgument);

        // Register common arguments.
        registerPropertyNameArgument(this.subCommand);

        setCommandBuilderUseful(false);
    }

    /** {@inheritDoc} */
    @Override
    public SubCommand getSubCommand() {
        return subCommand;
    }

    /**
     * Registers a managed object definition with this help properties sub-command.
     *
     * @param d
     *            The managed object definition.
     */
    public void registerManagedObjectDefinition(AbstractManagedObjectDefinition<?, ?> d) {
        // Determine the definition's base name.
        AbstractManagedObjectDefinition<?, ?> parent = d;
        while (!parent.getParent().isTop()) {
            parent = parent.getParent();
        }

        String baseName = parent.getName();
        String typeName = null;
        if (parent == d) {
            // This was a top-level definition.
            typeName = DSConfig.GENERIC_TYPE;
        } else {
            // For the type name we shorten it, if possible, by stripping
            // off the trailing part of the name which matches the
            // base-type.
            String suffix = "-" + baseName;
            typeName = d.getName();
            if (typeName.endsWith(suffix)) {
                typeName = typeName.substring(0, typeName.length() - suffix.length());
            }
        }

        // Get the sub-type mapping, creating it if necessary.
        Map<String, AbstractManagedObjectDefinition<?, ?>> subTypes = categoryMap.get(baseName);
        if (subTypes == null) {
            subTypes = new TreeMap<>();
            categoryMap.put(baseName, subTypes);
        }

        subTypes.put(typeName, d);

        // Get the tag mapping, creating it if necessary.
        for (Tag tag : d.getAllTags()) {
            subTypes = tagMap.get(tag);
            if (subTypes == null) {
                subTypes = new TreeMap<>();
                tagMap.put(tag, subTypes);
            }
            subTypes.put(typeName, d);
        }
    }

    /** {@inheritDoc} */
    @Override
    public MenuResult<Integer> run(ConsoleApplication app, LDAPManagementContextFactory factory)
            throws ArgumentException, ClientException {

        String categoryName = categoryArgument.getValue();
        String typeName = typeArgument.getValue();
        Tag tag = null;
        Set<String> propertyNames = getPropertyNames();

        // Reset the command builder
        getCommandBuilder().clearArguments();

        // Update the command builder.
        updateCommandBuilderWithSubCommand();

        List<AbstractManagedObjectDefinition<?, ?>> dlist = new LinkedList<>();
        AbstractManagedObjectDefinition<?, ?> tmp = null;

        if (categoryName != null) {
            // User requested a category of components.
            Map<String, AbstractManagedObjectDefinition<?, ?>> subTypes = categoryMap.get(categoryName);

            if (subTypes == null) {
                // Try a tag-base look-up.
                try {
                    tag = Tag.valueOf(categoryName);
                } catch (IllegalArgumentException e) {
                    throw ArgumentExceptionFactory.unknownCategory(categoryName);
                }

                subTypes = tagMap.get(tag);
                if (subTypes == null) {
                    throw ArgumentExceptionFactory.unknownCategory(categoryName);
                }
            } else {
                // Cache the generic definition for improved errors later on.
                tmp = subTypes.get(DSConfig.GENERIC_TYPE);
            }

            if (typeName != null) {
                AbstractManagedObjectDefinition<?, ?> d = subTypes.get(typeName);
                if (d == null) {
                    throw ArgumentExceptionFactory.unknownTypeForCategory(typeName, categoryName);
                }
                dlist.add(d);

                // Cache the generic definition for improved errors later on.
                tmp = d;
            } else {
                dlist.addAll(subTypes.values());
            }
        } else if (typeName != null) {
            // User requested just the sub-type which could appear in
            // multiple categories.
            boolean isFound = false;

            for (Map<String, AbstractManagedObjectDefinition<?, ?>> subTypes : categoryMap.values()) {
                AbstractManagedObjectDefinition<?, ?> d = subTypes.get(typeName);
                if (d != null) {
                    dlist.add(d);
                    isFound = true;
                }
            }

            if (!isFound) {
                throw ArgumentExceptionFactory.unknownTypeForCategory(typeName, categoryName);
            }
        } else {
            // User did not specify a category nor a sub-type.
            for (Map<String, AbstractManagedObjectDefinition<?, ?>> subTypes : categoryMap.values()) {
                dlist.addAll(subTypes.values());
            }
        }

        // Validate property names.
        if (dlist.size() == 1) {
            // Cache the generic definition for improved errors later on.
            tmp = dlist.get(0);
        }

        for (String propertyName : propertyNames) {
            boolean isFound = false;

            for (AbstractManagedObjectDefinition<?, ?> d : dlist) {
                try {
                    d.getPropertyDefinition(propertyName);
                    isFound = true;
                } catch (IllegalArgumentException e) {
                    // Ignore for now.
                }
            }

            if (!isFound) {
                if (tmp != null) {
                    throw ArgumentExceptionFactory.unknownProperty(tmp, propertyName);
                } else {
                    throw ArgumentExceptionFactory.unknownProperty(propertyName);
                }
            }
        }

        // Output everything to the output stream.
        if (!app.isVerbose()) {
            displayNonVerbose(app, categoryName, typeName, tag, propertyNames);
        } else {
            displayVerbose(app, categoryName, typeName, tag, propertyNames);
        }

        return MenuResult.success(0);
    }

    /** Output property summary table. */
    private void displayNonVerbose(ConsoleApplication app, String categoryName, String typeName, Tag tag,
            Set<String> propertyNames) {
        if (!app.isScriptFriendly()) {
            displayPropertyOptionKey(app);
            app.println();
            app.println();
        }

        // Headings.
        TableBuilder builder = new TableBuilder();

        builder.appendHeading(INFO_DSCFG_HEADING_COMPONENT_NAME.get());
        builder.appendHeading(INFO_DSCFG_HEADING_COMPONENT_TYPE.get());
        builder.appendHeading(INFO_DSCFG_HEADING_PROPERTY_NAME.get());
        builder.appendHeading(INFO_DSCFG_HEADING_PROPERTY_OPTIONS.get());
        builder.appendHeading(INFO_DSCFG_HEADING_PROPERTY_SYNTAX.get());

        // Sort keys.
        builder.addSortKey(0);
        builder.addSortKey(1);
        builder.addSortKey(2);

        // Generate the table content.
        for (String category : categoryMap.keySet()) {
            // Skip if this is the wrong category.
            if (categoryName != null && !categoryName.equals(category)) {
                continue;
            }

            // Process the sub-types.
            Map<String, AbstractManagedObjectDefinition<?, ?>> subTypes = categoryMap.get(category);
            for (String type : subTypes.keySet()) {
                // Skip if this is the wrong sub-type.
                if (typeName != null && !typeName.equals(type)) {
                    continue;
                }

                // Display help for each property.
                AbstractManagedObjectDefinition<?, ?> mod = subTypes.get(type);
                if (cannotDisplay(app, mod, tag)) {
                    continue;
                }

                Set<PropertyDefinition<?>> pds = getPropertyDefinitions(mod);
                for (PropertyDefinition<?> pd : pds) {
                    if (cannotDisplay(app, pd, propertyNames)) {
                        continue;
                    }

                    // Display the property:
                    // - component category and type,
                    // - property name, options
                    // - syntax
                    builder.startRow();
                    builder.appendCell(category);
                    builder.appendCell(type);
                    builder.appendCell(pd.getName());
                    builder.appendCell(getPropertyOptionSummary(pd));
                    PropertyDefinitionUsageBuilder v = new PropertyDefinitionUsageBuilder(false);
                    builder.appendCell(v.getUsage(pd));
                }
            }
        }

        TablePrinter printer;
        if (app.isScriptFriendly()) {
            printer = createScriptFriendlyTablePrinter(app.getOutputStream());
        } else {
            printer = new TextTablePrinter(app.getOutputStream());
        }
        builder.print(printer);
    }

    /** Display detailed help on managed objects and their properties. */
    private void displayVerbose(ConsoleApplication app, String categoryName, String typeName, Tag tag,
            Set<String> propertyNames) {
        // Construct line used to separate consecutive sections.
        LocalizableMessage c1 = buildLine('=', MAX_LINE_WIDTH);
        LocalizableMessage c2 = buildLine('-', MAX_LINE_WIDTH);

        // Display help for each managed object.
        boolean isFirstManagedObject = true;
        for (String category : categoryMap.keySet()) {
            // Skip if this is the wrong category.
            if (categoryName != null && !categoryName.equals(category)) {
                continue;
            }

            // Process the sub-types.
            Map<String, AbstractManagedObjectDefinition<?, ?>> subTypes = categoryMap.get(category);
            for (String type : subTypes.keySet()) {
                // Skip if this is the wrong sub-type.
                if (typeName != null && !typeName.equals(type)) {
                    continue;
                }

                // Display help for each property.
                AbstractManagedObjectDefinition<?, ?> mod = subTypes.get(type);
                if (cannotDisplay(app, mod, tag)) {
                    continue;
                }

                boolean isFirstProperty = true;
                Set<PropertyDefinition<?>> pds = getPropertyDefinitions(mod);
                for (PropertyDefinition<?> pd : pds) {
                    if (cannotDisplay(app, pd, propertyNames)) {
                        continue;
                    }

                    if (isFirstProperty) {
                        // User has requested properties relating to this managed
                        // object definition, so display the summary of the managed object.
                        if (!isFirstManagedObject) {
                            app.println();
                            app.println(c1);
                            app.println();
                        } else {
                            isFirstManagedObject = false;
                        }

                        // Display the title.
                        app.println(INFO_DSCFG_HELP_HEADING_COMPONENT.get(mod.getUserFriendlyName()));

                        app.println();
                        app.println(mod.getSynopsis());
                        if (mod.getDescription() != null) {
                            app.println();
                            app.println(mod.getDescription());
                        }
                    }

                    app.println();
                    app.println(c2);
                    app.println();

                    displayVerboseSingleProperty(app, mod, pd.getName());
                    isFirstProperty = false;
                }
            }
        }
    }

    private LocalizableMessage buildLine(char c, int length) {
        LocalizableMessageBuilder mb = new LocalizableMessageBuilder();
        for (int i = 0; i < length; i++) {
            mb.append(c);
        }
        return mb.toMessage();
    }

    private Set<PropertyDefinition<?>> getPropertyDefinitions(AbstractManagedObjectDefinition<?, ?> mod) {
        Set<PropertyDefinition<?>> pds = new TreeSet<>();
        if (inheritedModeArgument.isPresent()) {
            pds.addAll(mod.getAllPropertyDefinitions());
        } else {
            pds.addAll(mod.getPropertyDefinitions());

            // The list will still contain overridden properties.
            if (mod.getParent() != null) {
                pds.removeAll(mod.getParent().getAllPropertyDefinitions());
            }
        }
        return pds;
    }
}
