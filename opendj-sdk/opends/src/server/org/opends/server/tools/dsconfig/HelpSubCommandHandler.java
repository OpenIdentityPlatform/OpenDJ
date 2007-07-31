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
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.opends.server.admin.AbsoluteInheritedDefaultBehaviorProvider;
import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.AdministratorAction;
import org.opends.server.admin.AliasDefaultBehaviorProvider;
import org.opends.server.admin.DefaultBehaviorProviderVisitor;
import org.opends.server.admin.DefinedDefaultBehaviorProvider;
import org.opends.server.admin.EnumPropertyDefinition;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyDefinitionUsageBuilder;
import org.opends.server.admin.PropertyDefinitionVisitor;
import org.opends.server.admin.PropertyOption;
import org.opends.server.admin.RelativeInheritedDefaultBehaviorProvider;
import org.opends.server.admin.StringPropertyDefinition;
import org.opends.server.admin.Tag;
import org.opends.server.admin.UndefinedDefaultBehaviorProvider;
import org.opends.server.admin.UnknownPropertyDefinitionException;
import org.opends.server.tools.ClientException;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.args.SubCommand;
import org.opends.server.util.args.SubCommandArgumentParser;
import org.opends.server.util.table.TableBuilder;
import org.opends.server.util.table.TablePrinter;
import org.opends.server.util.table.TextTablePrinter;



/**
 * A sub-command handler which is used to display help about managed
 * objects and their properties.
 * <p>
 * This sub-command implements the help-properties sub-command.
 */
final class HelpSubCommandHandler extends SubCommandHandler {

  /**
   * This class is used to print the default behavior of a property.
   */
  private static class DefaultBehaviorPrinter {

    /**
     * The default behavior printer visitor implementation.
     *
     * @param <T>
     *          The property type.
     */
    private static class DefaultVisitor<T> implements
        DefaultBehaviorProviderVisitor<T, String, PropertyDefinition<T>> {

      /**
       * {@inheritDoc}
       */
      public String visitAbsoluteInherited(
          AbsoluteInheritedDefaultBehaviorProvider<T> d,
          PropertyDefinition<T> p) {
        return getMessage(MSGID_DSCFG_HELP_FIELD_INHERITED_ABS, d
            .getPropertyName(), d.getManagedObjectPath()
            .getRelationDefinition().getUserFriendlyName());
      }



      /**
       * {@inheritDoc}
       */
      public String visitAlias(AliasDefaultBehaviorProvider<T> d,
          PropertyDefinition<T> p) {
        return d.getSynopsis();
      }



      /**
       * {@inheritDoc}
       */
      public String visitDefined(DefinedDefaultBehaviorProvider<T> d,
          PropertyDefinition<T> p) {
        StringBuilder builder = new StringBuilder();
        PropertyValuePrinter printer = new PropertyValuePrinter(null, null,
            false);
        boolean isFirst = true;
        for (String s : d.getDefaultValues()) {
          if (!isFirst) {
            builder.append(", ");
          }

          T value = p.decodeValue(s);
          builder.append(printer.print(p, value));
        }

        return builder.toString();
      }



      /**
       * {@inheritDoc}
       */
      public String visitRelativeInherited(
          RelativeInheritedDefaultBehaviorProvider<T> d,
          PropertyDefinition<T> p) {
        if (d.getRelativeOffset() == 0) {
          return getMessage(MSGID_DSCFG_HELP_FIELD_INHERITED_THIS, d
              .getPropertyName(), d.getManagedObjectDefinition()
              .getUserFriendlyName());
        } else {
          return getMessage(MSGID_DSCFG_HELP_FIELD_INHERITED_PARENT, d
              .getPropertyName(), d.getManagedObjectDefinition()
              .getUserFriendlyName());
        }
      }



      /**
       * {@inheritDoc}
       */
      public String visitUndefined(UndefinedDefaultBehaviorProvider<T> d,
          PropertyDefinition<T> p) {
        return getMessage(MSGID_DSCFG_HELP_FIELD_UNDEFINED);
      }

    }



    /**
     * Create a new default behavior printer.
     */
    public DefaultBehaviorPrinter() {
      // No implementation required.
    }



    /**
     * Get a user-friendly description of a property's default
     * behavior.
     *
     * @param <T>
     *          The type of the property definition.
     * @param pd
     *          The property definition.
     * @return Returns the user-friendly description of a property's
     *         default behavior.
     */
    public <T> String print(PropertyDefinition<T> pd) {
      DefaultVisitor<T> v = new DefaultVisitor<T>();
      return pd.getDefaultBehaviorProvider().accept(v, pd);
    }
  }



  /**
   * This class is used to print detailed syntax information about a
   * property.
   */
  private static class SyntaxPrinter {

    /**
     * The syntax printer visitor implementation.
     */
    private static class Visitor extends
        PropertyDefinitionVisitor<Void, PrintStream> {

      // Private constructor.
      private Visitor() {
        // No implementation required.
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public <E extends Enum<E>> Void visitEnum(EnumPropertyDefinition<E> d,
          PrintStream p) {
        displayUsage(p, getMessage(MSGID_DSCFG_HELP_FIELD_ENUM));
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



      /**
       * {@inheritDoc}
       */
      @Override
      public Void visitString(StringPropertyDefinition d, PrintStream p) {
        PropertyDefinitionUsageBuilder usageBuilder =
          new PropertyDefinitionUsageBuilder(true);

        TableBuilder builder = new TableBuilder();
        builder.startRow();
        builder.appendCell(getMessage(MSGID_DSCFG_HELP_HEADING_SYNTAX));
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



      /**
       * {@inheritDoc}
       */
      @Override
      public Void visitUnknown(PropertyDefinition<?> d, PrintStream p)
          throws UnknownPropertyDefinitionException {
        PropertyDefinitionUsageBuilder usageBuilder =
          new PropertyDefinitionUsageBuilder(true);
        displayUsage(p, usageBuilder.getUsage(d));

        return null;
      }



      // Common usage.
      private void displayUsage(PrintStream p, String usage) {
        TableBuilder builder = new TableBuilder();
        builder.startRow();
        builder.appendCell(getMessage(MSGID_DSCFG_HELP_HEADING_SYNTAX));
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

    // The private implementation.
    private final Visitor pimpl;



    /**
     * Creates a new syntax printer which can be used to print
     * detailed syntax information about a property.
     */
    public SyntaxPrinter() {
      this.pimpl = new Visitor();
    }



    /**
     * Print detailed syntax information about a property definition.
     *
     * @param out
     *          The output stream.
     * @param pd
     *          The property definition.
     */
    public void print(PrintStream out, PropertyDefinition<?> pd) {
      pd.accept(pimpl, out);
    }
  }

  /**
   * The type component name to be used for top-level definitions.
   */
  private static final String GENERIC_TYPE = "generic";

  // Strings used in property help.
  private final static String HEADING_SEPARATOR = " : ";

  // Width of biggest heading (need to be careful of I18N).
  private final static int HEADING_WIDTH;

  /**
   * The value for the long option inherited.
   */
  private static final String OPTION_DSCFG_LONG_INHERITED = "inherited";

  /**
   * The value for the short option inherited.
   */
  private static final Character OPTION_DSCFG_SHORT_INHERITED = null;

  /**
   * The value for the long option type.
   */
  private static final String OPTION_DSCFG_LONG_TYPE = "type";

  /**
   * The value for the short option type.
   */
  private static final Character OPTION_DSCFG_SHORT_TYPE = 't';

  /**
   * The value for the long option category.
   */
  private static final String OPTION_DSCFG_LONG_CATEGORY = "category";

  /**
   * The value for the short option category.
   */
  private static final Character OPTION_DSCFG_SHORT_CATEGORY = 'c';

  static {
    int tmp = getMessage(MSGID_DSCFG_HELP_HEADING_SYNTAX).length();
    tmp = Math.max(tmp, getMessage(MSGID_DSCFG_HELP_HEADING_DEFAULT).length());
    tmp = Math.max(tmp, getMessage(MSGID_DSCFG_HELP_HEADING_MULTI_VALUED)
        .length());
    tmp = Math
        .max(tmp, getMessage(MSGID_DSCFG_HELP_HEADING_MANDATORY).length());
    tmp = Math
        .max(tmp, getMessage(MSGID_DSCFG_HELP_HEADING_READ_ONLY).length());
    HEADING_WIDTH = tmp;
  }



  /**
   * Creates a new help-properties sub-command.
   *
   * @param app
   *          The console application.
   * @param parser
   *          The sub-command argument parser.
   * @return Returns the new help-properties sub-command.
   * @throws ArgumentException
   *           If the sub-command could not be created successfully.
   */
  public static HelpSubCommandHandler create(ConsoleApplication app,
      SubCommandArgumentParser parser) throws ArgumentException {
    return new HelpSubCommandHandler(app, parser);
  }



  /**
   * Displays detailed help about a single property to the specified
   * output stream.
   *
   * @param d
   *          The managed object definition.
   * @param name
   *          The name of the property definition.
   * @param out
   *          The output stream.
   */
  public static void displayVerboseSingleProperty(
      AbstractManagedObjectDefinition<?, ?> d, String name, PrintStream out) {
    PropertyDefinition<?> pd = d.getPropertyDefinition(name);

    // Display the title.
    out.println(getMessage(MSGID_DSCFG_HELP_HEADING_PROPERTY, name));

    // Display the property synopsis and description.
    out.println();
    out.println(wrapText(pd.getSynopsis(), MAX_LINE_WIDTH));
    if (pd.getDescription() != null) {
      out.println();
      out.println(wrapText(pd.getDescription(), MAX_LINE_WIDTH));
    }

    // Display the syntax.
    out.println();
    SyntaxPrinter syntaxPrinter = new SyntaxPrinter();
    syntaxPrinter.print(out, pd);

    // Display remaining information in a table.
    out.println();
    TableBuilder builder = new TableBuilder();

    // Display the default behavior.
    DefaultBehaviorPrinter defaultPrinter = new DefaultBehaviorPrinter();

    builder.startRow();
    builder.appendCell(getMessage(MSGID_DSCFG_HELP_HEADING_DEFAULT));
    builder.appendCell(HEADING_SEPARATOR);
    builder.appendCell(defaultPrinter.print(pd));

    // Display options.
    builder.startRow();
    builder.appendCell(getMessage(MSGID_DSCFG_HELP_HEADING_ADVANCED));
    builder.appendCell(HEADING_SEPARATOR);
    if (pd.hasOption(PropertyOption.ADVANCED)) {
      builder.appendCell(getMessage(MSGID_DSCFG_GENERAL_CONFIRM_YES));
    } else {
      builder.appendCell(getMessage(MSGID_DSCFG_GENERAL_CONFIRM_NO));
    }

    builder.startRow();
    builder.appendCell(getMessage(MSGID_DSCFG_HELP_HEADING_MULTI_VALUED));
    builder.appendCell(HEADING_SEPARATOR);
    if (pd.hasOption(PropertyOption.MULTI_VALUED)) {
      builder.appendCell(getMessage(MSGID_DSCFG_GENERAL_CONFIRM_YES));
    } else {
      builder.appendCell(getMessage(MSGID_DSCFG_GENERAL_CONFIRM_NO));
    }

    builder.startRow();
    builder.appendCell(getMessage(MSGID_DSCFG_HELP_HEADING_MANDATORY));
    builder.appendCell(HEADING_SEPARATOR);
    if (pd.hasOption(PropertyOption.MANDATORY)) {
      builder.appendCell(getMessage(MSGID_DSCFG_GENERAL_CONFIRM_YES));
    } else {
      builder.appendCell(getMessage(MSGID_DSCFG_GENERAL_CONFIRM_NO));
    }

    builder.startRow();
    builder.appendCell(getMessage(MSGID_DSCFG_HELP_HEADING_READ_ONLY));
    builder.appendCell(HEADING_SEPARATOR);
    if (pd.hasOption(PropertyOption.MONITORING)) {
      builder.appendCell(getMessage(MSGID_DSCFG_HELP_FIELD_MONITORING));
    } else if (pd.hasOption(PropertyOption.READ_ONLY)) {
      builder.appendCell(getMessage(MSGID_DSCFG_HELP_FIELD_READ_ONLY, d
          .getUserFriendlyName()));
    } else {
      builder.appendCell(getMessage(MSGID_DSCFG_GENERAL_CONFIRM_NO));
    }

    TextTablePrinter factory = new TextTablePrinter(out);
    factory.setDisplayHeadings(false);
    factory.setColumnWidth(0, HEADING_WIDTH);
    factory.setColumnWidth(2, 0);
    factory.setPadding(0);
    builder.print(factory);

    // Administrator action.
    AdministratorAction action = pd.getAdministratorAction();
    String synopsis = action.getSynopsis();
    if (synopsis == null) {
      switch (action.getType()) {
      case COMPONENT_RESTART:
        synopsis = getMessage(MSGID_DSCFG_HELP_FIELD_COMPONENT_RESTART, d
            .getUserFriendlyName());
        break;
      case SERVER_RESTART:
        synopsis = getMessage(MSGID_DSCFG_HELP_FIELD_SERVER_RESTART);
        break;
      default:
        // Do nothing.
        break;
      }
    }

    if (synopsis != null) {
      out.println();
      out.println(wrapText(synopsis, MAX_LINE_WIDTH));
    }
  }

  // The sub-command associated with this handler.
  private final SubCommand subCommand;

  // The argument which should be used to specify the category of
  // managed object to be retrieved.
  private final StringArgument categoryArgument;

  //The argument which should be used to display inherited properties.
  private BooleanArgument inheritedModeArgument;

  // The argument which should be used to specify the sub-type of
  // managed object to be retrieved.
  private final StringArgument typeArgument;

  // A table listing all the available types of managed object indexed
  // on their parent type.
  private final Map<String,
    Map<String, AbstractManagedObjectDefinition<?, ?>>> categoryMap;

  // A table listing all the available types of managed object indexed
  // on their tag(s).
  private final Map<Tag,
    Map<String, AbstractManagedObjectDefinition<?, ?>>> tagMap;



  // Private constructor.
  private HelpSubCommandHandler(ConsoleApplication app,
      SubCommandArgumentParser parser) throws ArgumentException {
    super(app);

    // Create the sub-command.
    String name = "list-properties";
    int descriptionID = MSGID_DSCFG_DESCRIPTION_SUBCMD_HELPPROP;
    this.subCommand = new SubCommand(parser, name, false, 0, 0, null,
        descriptionID);

    this.categoryArgument = new StringArgument(OPTION_DSCFG_LONG_CATEGORY,
        OPTION_DSCFG_SHORT_CATEGORY, OPTION_DSCFG_LONG_CATEGORY, false, false,
        true, "{CATEGORY}", null, null, MSGID_DSCFG_DESCRIPTION_HELP_CATEGORY);
    this.subCommand.addArgument(this.categoryArgument);

    this.typeArgument = new StringArgument(OPTION_DSCFG_LONG_TYPE,
        OPTION_DSCFG_SHORT_TYPE, OPTION_DSCFG_LONG_TYPE, false, false, true,
        "{TYPE}", null, null, MSGID_DSCFG_DESCRIPTION_HELP_TYPE);
    this.subCommand.addArgument(this.typeArgument);

    this.inheritedModeArgument = new BooleanArgument(
        OPTION_DSCFG_LONG_INHERITED, OPTION_DSCFG_SHORT_INHERITED,
        OPTION_DSCFG_LONG_INHERITED, MSGID_DSCFG_DESCRIPTION_HELP_INHERITED);
    subCommand.addArgument(inheritedModeArgument);

    // Register common arguments.
    registerAdvancedModeArgument(this.subCommand,
        MSGID_DSCFG_DESCRIPTION_ADVANCED_HELP);
    registerPropertyNameArgument(this.subCommand);

    this.categoryMap = new TreeMap<String,
      Map<String, AbstractManagedObjectDefinition<?, ?>>>();
    this.tagMap = new HashMap<Tag,
      Map<String, AbstractManagedObjectDefinition<?, ?>>>();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public SubCommand getSubCommand() {
    return subCommand;
  }



  /**
   * Registers a managed object definition with this help properties
   * sub-command.
   *
   * @param d
   *          The managed object definition.
   */
  public void registerManagedObjectDefinition(
      AbstractManagedObjectDefinition<?, ?> d) {
    // Determine the definition's base name.
    AbstractManagedObjectDefinition<?, ?> parent = d;
    while (parent.getParent() != null) {
      parent = parent.getParent();
    }

    String baseName = parent.getName();
    String typeName = null;
    if (parent == d) {
      // This was a top-level definition.
      typeName = GENERIC_TYPE;
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
    Map<String, AbstractManagedObjectDefinition<?, ?>> subTypes = categoryMap
        .get(baseName);
    if (subTypes == null) {
      subTypes = new TreeMap<String, AbstractManagedObjectDefinition<?, ?>>();
      categoryMap.put(baseName, subTypes);
    }

    subTypes.put(typeName, d);

    // Get the tag mapping, creating it if necessary.
    for (Tag tag : d.getAllTags()) {
      subTypes = tagMap.get(baseName);
      if (subTypes == null) {
        subTypes = new TreeMap<String, AbstractManagedObjectDefinition<?, ?>>();
        tagMap.put(tag, subTypes);
      }
      subTypes.put(typeName, d);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public int run() throws ArgumentException, ClientException {
    String categoryName = categoryArgument.getValue();
    String typeName = typeArgument.getValue();
    Tag tag = null;
    Set<String> propertyNames = getPropertyNames();

    List<AbstractManagedObjectDefinition<?, ?>> dlist =
      new LinkedList<AbstractManagedObjectDefinition<?, ?>>();
    AbstractManagedObjectDefinition<?, ?> tmp = null;

    if (categoryName != null) {
      // User requested a category of components.
      Map<String, AbstractManagedObjectDefinition<?, ?>> subTypes = categoryMap
          .get(categoryName);

      if (subTypes == null) {
        // Try a tag-base look-up.
        try {
          tag = Tag.valueOf(categoryName);
        } catch (IllegalArgumentException e) {
          throw ArgumentExceptionFactory.unknownCategory(categoryName);
        }

        categoryName = null;
        subTypes = tagMap.get(tag);
        if (subTypes == null) {
          throw ArgumentExceptionFactory.unknownCategory(categoryName);
        }
      } else {
        // Cache the generic definition for improved errors later on.
        tmp = subTypes.get(GENERIC_TYPE);
      }

      if (typeName != null) {
        AbstractManagedObjectDefinition<?, ?> d = subTypes.get(typeName);
        if (d == null) {
          throw ArgumentExceptionFactory.unknownTypeInCategory(categoryName,
              typeName);
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

      for (Map<String, AbstractManagedObjectDefinition<?, ?>> subTypes :
        categoryMap.values()) {
        AbstractManagedObjectDefinition<?, ?> d = subTypes.get(typeName);
        if (d != null) {
          dlist.add(d);
          isFound = true;
        }
      }

      if (!isFound) {
        throw ArgumentExceptionFactory.unknownType(typeName);
      }
    } else {
      // User did not specify a category nor a sub-type.
      for (Map<String, AbstractManagedObjectDefinition<?, ?>> subTypes :
        categoryMap.values()) {
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

    if (!getConsoleApplication().isVerbose()) {
      displayNonVerbose(categoryName, typeName, tag, propertyNames);
    } else {
      displayVerbose(categoryName, typeName, tag, propertyNames);
    }

    return 0;
  }



  // Output property summary table.
  private void displayNonVerbose(String categoryName, String typeName,
      Tag tag, Set<String> propertyNames) {
    PrintStream out = getConsoleApplication().getOutputStream();
    if (!getConsoleApplication().isScriptFriendly()) {
      out.println(getMessage(MSGID_DSCFG_HELP_DESCRIPTION_OPTION));
      out.println();
      out.print(" r -- ");
      out.println(getMessage(MSGID_DSCFG_HELP_DESCRIPTION_READ));
      out.print(" w -- ");
      out.println(getMessage(MSGID_DSCFG_HELP_DESCRIPTION_WRITE));
      out.print(" m -- ");
      out.println(getMessage(MSGID_DSCFG_HELP_DESCRIPTION_MANDATORY));
      out.print(" s -- ");
      out.println(getMessage(MSGID_DSCFG_HELP_DESCRIPTION_SINGLE_VALUED));
      out.print(" a -- ");
      out.println(getMessage(MSGID_DSCFG_HELP_DESCRIPTION_ADMIN_ACTION));
      out.println();
      out.println();
    }

    // Headings.
    TableBuilder builder = new TableBuilder();

    builder.appendHeading(getMessage(MSGID_DSCFG_HEADING_COMPONENT_NAME));
    builder.appendHeading(getMessage(MSGID_DSCFG_HEADING_COMPONENT_TYPE));
    builder.appendHeading(getMessage(MSGID_DSCFG_HEADING_PROPERTY_NAME));
    builder.appendHeading(getMessage(MSGID_DSCFG_HEADING_PROPERTY_OPTIONS));
    builder.appendHeading(getMessage(MSGID_DSCFG_HEADING_PROPERTY_SYNTAX));

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
      Map<String, AbstractManagedObjectDefinition<?, ?>> subTypes = categoryMap
          .get(category);
      for (String type : subTypes.keySet()) {
        // Skip if this is the wrong sub-type.
        if (typeName != null && !typeName.equals(type)) {
          continue;
        }

        // Display help for each property.
        AbstractManagedObjectDefinition<?, ?> mod = subTypes.get(type);

        // Skip if this does not have the required tag.
        if (tag != null && !mod.hasTag(tag)) {
          continue;
        }

        Set<PropertyDefinition<?>> pds = new TreeSet<PropertyDefinition<?>>();
        if (inheritedModeArgument.isPresent()) {
          pds.addAll(mod.getAllPropertyDefinitions());
        } else {
          pds.addAll(mod.getPropertyDefinitions());

          // The list will still contain overridden properties.
          if (mod.getParent() != null) {
            pds.removeAll(mod.getParent().getAllPropertyDefinitions());
          }
        }

        for (PropertyDefinition<?> pd : pds) {
          if (pd.hasOption(PropertyOption.HIDDEN)) {
            continue;
          }

          if (!isAdvancedMode() && pd.hasOption(PropertyOption.ADVANCED)) {
            continue;
          }

          if (!propertyNames.isEmpty() &&
              !propertyNames.contains(pd.getName())) {
            continue;
          }

          // Display the property.
          builder.startRow();

          // Display the component category.
          builder.appendCell(category);

          // Display the component type.
          builder.appendCell(type);

          // Display the property name.
          builder.appendCell(pd.getName());

          // Display the options.
          builder.appendCell(getPropertyOptionSummary(pd));

          // Display the syntax.
          PropertyDefinitionUsageBuilder v = new PropertyDefinitionUsageBuilder(
              false);
          builder.appendCell(v.getUsage(pd));
        }
      }
    }

    TablePrinter printer;
    if (getConsoleApplication().isScriptFriendly()) {
      printer = createScriptFriendlyTablePrinter(out);
    } else {
      printer = new TextTablePrinter(out);
    }
    builder.print(printer);
  }



  // Display detailed help on managed objects and their properties.
  private void displayVerbose(String categoryName, String typeName,
      Tag tag, Set<String> propertyNames) {
    PrintStream out = getConsoleApplication().getOutputStream();

    // Construct line used to separate consecutive sections.
    char[] c1 = new char[MAX_LINE_WIDTH];
    Arrays.fill(c1, '=');
    char[] c2 = new char[MAX_LINE_WIDTH];
    Arrays.fill(c2, '-');

    // Display help for each managed object.
    boolean isFirstManagedObject = true;
    for (String category : categoryMap.keySet()) {
      // Skip if this is the wrong category.
      if (categoryName != null && !categoryName.equals(category)) {
        continue;
      }

      // Process the sub-types.
      Map<String, AbstractManagedObjectDefinition<?, ?>> subTypes = categoryMap
          .get(category);
      for (String type : subTypes.keySet()) {
        // Skip if this is the wrong sub-type.
        if (typeName != null && !typeName.equals(type)) {
          continue;
        }

        // Display help for each property.
        AbstractManagedObjectDefinition<?, ?> mod = subTypes.get(type);

        // Skip if this does not have the required tag.
        if (tag != null && !mod.hasTag(tag)) {
          continue;
        }

        Set<PropertyDefinition<?>> pds = new TreeSet<PropertyDefinition<?>>();
        if (inheritedModeArgument.isPresent()) {
          pds.addAll(mod.getAllPropertyDefinitions());
        } else {
          pds.addAll(mod.getPropertyDefinitions());

          // The list will still contain overridden properties.
          if (mod.getParent() != null) {
            pds.removeAll(mod.getParent().getAllPropertyDefinitions());
          }
        }

        boolean isFirstProperty = true;
        for (PropertyDefinition<?> pd : pds) {
          if (pd.hasOption(PropertyOption.HIDDEN)) {
            continue;
          }

          if (!isAdvancedMode() && pd.hasOption(PropertyOption.ADVANCED)) {
            continue;
          }

          if (!propertyNames.isEmpty() &&
              !propertyNames.contains(pd.getName())) {
            continue;
          }

          if (isFirstProperty) {
            // User has requested properties relating to this managed
            // object definition, so display the summary of the
            // managed
            // object.
            if (!isFirstManagedObject) {
              out.println();
              out.println(c1);
              out.println();
            } else {
              isFirstManagedObject = false;
            }

            // Display the title.
            out.println(wrapText(getMessage(MSGID_DSCFG_HELP_HEADING_COMPONENT,
                mod.getUserFriendlyName()), MAX_LINE_WIDTH));

            out.println();
            out.println(wrapText(mod.getSynopsis(), MAX_LINE_WIDTH));
            if (mod.getDescription() != null) {
              out.println();
              out.println(wrapText(mod.getDescription(), MAX_LINE_WIDTH));
            }
          }

          out.println();
          out.println(c2);
          out.println();

          displayVerboseSingleProperty(mod, pd.getName(), out);
          isFirstProperty = false;
        }
      }
    }
  }



  // Compute the options field.
  private String getPropertyOptionSummary(PropertyDefinition<?> pd) {
    StringBuilder b = new StringBuilder();

    if (pd.hasOption(PropertyOption.MONITORING)
        || pd.hasOption(PropertyOption.READ_ONLY)) {
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
}
