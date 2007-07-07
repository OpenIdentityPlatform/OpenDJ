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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.DefaultBehaviorException;
import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.IllegalPropertyValueStringException;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.ManagedObjectAlreadyExistsException;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.OptionalRelationDefinition;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyException;
import org.opends.server.admin.PropertyIsSingleValuedException;
import org.opends.server.admin.PropertyOption;
import org.opends.server.admin.PropertyProvider;
import org.opends.server.admin.RelationDefinition;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ConcurrentModificationException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagedObjectDecodingException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.MissingMandatoryPropertiesException;
import org.opends.server.admin.client.OperationRejectedException;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.tools.ClientException;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.args.SubCommand;
import org.opends.server.util.args.SubCommandArgumentParser;



/**
 * A sub-command handler which is used to create new managed objects.
 * <p>
 * This sub-command implements the various create-xxx sub-commands.
 *
 * @param <C>
 *          The type of managed object which can be created.
 */
final class CreateSubCommandHandler<C extends ConfigurationClient> extends
    SubCommandHandler {

  /**
   * A property provider which uses the command-line arguments to
   * provide initial property values.
   */
  private static class MyPropertyProvider implements PropertyProvider {

    // Decoded set of properties.
    private final Map<PropertyDefinition, Collection> properties =
      new HashMap<PropertyDefinition, Collection>();



    /**
     * Create a new property provider using the provided set of
     * property value arguments.
     *
     * @param d
     *          The managed object definition.
     * @param args
     *          The property value arguments.
     * @throws ArgumentException
     *           If the property value arguments could not be parsed.
     */
    public MyPropertyProvider(ManagedObjectDefinition<?, ?> d,
        List<String> args) throws ArgumentException {
      for (String s : args) {
        // Parse the property "property:value".
        int sep = s.indexOf(':');

        if (sep < 0) {
          throw ArgumentExceptionFactory.missingSeparatorInPropertyArgument(s);
        }

        if (sep == 0) {
          throw ArgumentExceptionFactory.missingNameInPropertyArgument(s);
        }

        String propertyName = s.substring(0, sep);
        String value = s.substring(sep + 1, s.length());
        if (value.length() == 0) {
          throw ArgumentExceptionFactory.missingValueInPropertyArgument(s);
        }

        // Check the property definition.
        PropertyDefinition<?> pd;
        try {
          pd = d.getPropertyDefinition(propertyName);
        } catch (IllegalArgumentException e) {
          throw ArgumentExceptionFactory.unknownProperty(d, propertyName);
        }

        // Add the value.
        addPropertyValue(d, pd, value);
      }
    }



    /**
     * Get the set of parsed property definitions that have values
     * specified.
     *
     * @return Returns the set of parsed property definitions that
     *         have values specified.
     */
    public Set<PropertyDefinition> getProperties() {
      return properties.keySet();
    }



    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public <T> Collection<T> getPropertyValues(PropertyDefinition<T> d)
        throws IllegalArgumentException {
      Collection<T> values = properties.get(d);
      if (values == null) {
        return Collections.emptySet();
      } else {
        return values;
      }
    }



    // Add a single property value.
    @SuppressWarnings("unchecked")
    private <T> void addPropertyValue(ManagedObjectDefinition<?, ?> d,
        PropertyDefinition<T> pd, String s) throws ArgumentException {
      T value;
      try {
        value = pd.decodeValue(s);
      } catch (IllegalPropertyValueStringException e) {
        throw ArgumentExceptionFactory.adaptPropertyException(e, d);
      }

      Collection<T> values = properties.get(pd);
      if (values == null) {
        values = new LinkedList<T>();
      }
      values.add(value);

      if (values.size() > 1 && !pd.hasOption(PropertyOption.MULTI_VALUED)) {
        PropertyException e = new PropertyIsSingleValuedException(pd);
        throw ArgumentExceptionFactory.adaptPropertyException(e, d);
      }

      properties.put(pd, values);
    }
  }

  /**
   * The value for the -t argument which will be used for the most
   * generic managed object when it is instantiable.
   */
  private static final String GENERIC_TYPE = "generic";

  /**
   * The value for the long option set.
   */
  private static final String OPTION_DSCFG_LONG_SET = "set";

  /**
   * The value for the long option type.
   */
  private static final String OPTION_DSCFG_LONG_TYPE = "type";

  /**
   * The value for the short option property.
   */
  private static final Character OPTION_DSCFG_SHORT_SET = null;

  /**
   * The value for the short option type.
   */
  private static final Character OPTION_DSCFG_SHORT_TYPE = 't';



  /**
   * Creates a new create-xxx sub-command for an instantiable
   * relation.
   *
   * @param <C>
   *          The type of managed object which can be created.
   * @param parser
   *          The sub-command argument parser.
   * @param p
   *          The parent managed object path.
   * @param r
   *          The instantiable relation.
   * @return Returns the new create-xxx sub-command.
   * @throws ArgumentException
   *           If the sub-command could not be created successfully.
   */
  public static <C extends ConfigurationClient> CreateSubCommandHandler create(
      SubCommandArgumentParser parser, ManagedObjectPath<?, ?> p,
      InstantiableRelationDefinition<C, ?> r) throws ArgumentException {
    return new CreateSubCommandHandler<C>(parser, p, r, p.child(r, "DUMMY"));
  }



  /**
   * Creates a new create-xxx sub-command for an optional relation.
   *
   * @param <C>
   *          The type of managed object which can be created.
   * @param parser
   *          The sub-command argument parser.
   * @param p
   *          The parent managed object path.
   * @param r
   *          The optional relation.
   * @return Returns the new create-xxx sub-command.
   * @throws ArgumentException
   *           If the sub-command could not be created successfully.
   */
  public static <C extends ConfigurationClient> CreateSubCommandHandler create(
      SubCommandArgumentParser parser, ManagedObjectPath<?, ?> p,
      OptionalRelationDefinition<C, ?> r) throws ArgumentException {
    return new CreateSubCommandHandler<C>(parser, p, r, p.child(r));
  }

  // The sub-commands naming arguments.
  private final List<StringArgument> namingArgs;

  // The path of the parent managed object.
  private final ManagedObjectPath<?, ?> path;

  // The argument which should be used to specify zero or more
  // property values.
  private final StringArgument propertySetArgument;

  // The relation which should be used for creating children.
  private final RelationDefinition<C, ?> relation;

  // The sub-command associated with this handler.
  private final SubCommand subCommand;

  // The argument which should be used to specify the type of managed
  // object to be created.
  private final StringArgument typeArgument;

  // The set of instantiable managed object definitions and their
  // associated type option value.
  private final SortedMap<String,
    ManagedObjectDefinition<? extends C, ?>> types;

  // The syntax of the type argument.
  private final String typeUsage;



  // Common constructor.
  private CreateSubCommandHandler(SubCommandArgumentParser parser,
      ManagedObjectPath<?, ?> p, RelationDefinition<C, ?> r,
      ManagedObjectPath<?, ?> c) throws ArgumentException {
    this.path = p;
    this.relation = r;

    // Create the sub-command.
    String name = "create-" + r.getName();
    int descriptionID = MSGID_DSCFG_DESCRIPTION_SUBCMD_CREATE;
    this.subCommand = new SubCommand(parser, name, false, 0, 0, null,
        descriptionID, r.getChildDefinition().getUserFriendlyPluralName());

    // Create the -t argument which is used to specify the type of
    // managed object to be created.
    this.types = getSubTypes(r.getChildDefinition());

    // Create the naming arguments.
    this.namingArgs = createNamingArgs(subCommand, c);

    // Create the --property argument which is used to specify
    // property values.
    this.propertySetArgument = new StringArgument(OPTION_DSCFG_LONG_SET,
        OPTION_DSCFG_SHORT_SET, OPTION_DSCFG_LONG_SET, false, true,
        true, "{PROP:VALUE}", null, null, MSGID_DSCFG_DESCRIPTION_PROP_VAL);
    this.subCommand.addArgument(this.propertySetArgument);

    // Build the -t option usage.
    StringBuilder builder = new StringBuilder();
    boolean isFirst = true;
    for (String s : types.keySet()) {
      if (!isFirst) {
        builder.append(" | ");
      }
      builder.append(s);
      isFirst = false;
    }
    this.typeUsage = builder.toString();

    if (!types.containsKey(GENERIC_TYPE)) {
      // The option is mandatory.
      this.typeArgument = new StringArgument("type", OPTION_DSCFG_SHORT_TYPE,
          OPTION_DSCFG_LONG_TYPE, true, false, true, "{TYPE}", null, null,
          MSGID_DSCFG_DESCRIPTION_TYPE, r.getChildDefinition()
              .getUserFriendlyName(), typeUsage);
    } else {
      // The option has a sensible default "generic".
      this.typeArgument = new StringArgument("type", OPTION_DSCFG_SHORT_TYPE,
          OPTION_DSCFG_LONG_TYPE, false, false, true, "{TYPE}", GENERIC_TYPE,
          null, MSGID_DSCFG_DESCRIPTION_TYPE_DEFAULT, r.getChildDefinition()
              .getUserFriendlyName(), GENERIC_TYPE, typeUsage);
    }
    this.subCommand.addArgument(this.typeArgument);

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
    // Determine the type of managed object to be created.
    String typeName = typeArgument.getValue();
    ManagedObjectDefinition<? extends C, ?> d = types.get(typeName);
    if (d == null) {
      throw ArgumentExceptionFactory.unknownSubType(relation, typeName,
          typeUsage);
    }

    // Get the naming argument values.
    List<String> names = getNamingArgValues(namingArgs);

    // Encode the provided properties.
    List<String> propertyArgs = propertySetArgument.getValues();
    MyPropertyProvider provider = new MyPropertyProvider(d, propertyArgs);

    // Add the child managed object.
    ManagementContext context = app.getManagementContext();
    ManagedObject<?> parent;
    try {
      parent = getManagedObject(context, path, names);
    } catch (AuthorizationException e) {
      int msgID = MSGID_DSCFG_ERROR_CREATE_AUTHZ;
      String msg = getMessage(msgID, d.getUserFriendlyName());
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
      int msgID = MSGID_DSCFG_ERROR_CREATE_CE;
      String msg = getMessage(msgID, d.getUserFriendlyName(), e.getMessage());
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msgID,
          msg);
    } catch (ConcurrentModificationException e) {
      int msgID = MSGID_DSCFG_ERROR_CREATE_CME;
      String msg = getMessage(msgID, d.getUserFriendlyName());
      throw new ClientException(LDAPResultCode.CONSTRAINT_VIOLATION,
          msgID, msg);
    } catch (ManagedObjectNotFoundException e) {
      int msgID = MSGID_DSCFG_ERROR_GET_PARENT_MONFE;
      String ufn = path.getManagedObjectDefinition().getUserFriendlyName();
      String msg = getMessage(msgID, ufn);
      throw new ClientException(LDAPResultCode.NO_SUCH_OBJECT, msgID, msg);
    }

    try {
      ManagedObject<? extends C> child;
      List<DefaultBehaviorException> exceptions =
        new LinkedList<DefaultBehaviorException>();
      if (relation instanceof InstantiableRelationDefinition) {
        InstantiableRelationDefinition<C, ?> irelation =
          (InstantiableRelationDefinition<C, ?>) relation;
        String name = names.get(names.size() - 1);
        child = parent.createChild(irelation, d, name, exceptions);
      } else {
        OptionalRelationDefinition<C, ?> orelation =
          (OptionalRelationDefinition<C, ?>) relation;
        child = parent.createChild(orelation, d, exceptions);
      }

      // FIXME: display any default behavior exceptions in verbose
      // mode.

      // Set any properties specified on the command line.
      for (PropertyDefinition<?> pd : provider.getProperties()) {
        setProperty(child, provider, pd);
      }

      // Confirm commit.
      String prompt = String.format(Messages.getString("create.confirm"), d
          .getUserFriendlyName());
      if (!app.confirmAction(prompt)) {
        // Output failure message.
        String msg = String.format(Messages.getString("create.failed"), d
            .getUserFriendlyName());
        app.displayVerboseMessage(msg);
        return 1;
      }

      // Add the managed object.
      child.commit();

      // Output success message.
      String msg = String.format(Messages.getString("create.done"), d
          .getUserFriendlyName());
      app.displayVerboseMessage(msg);
    } catch (MissingMandatoryPropertiesException e) {
      throw ArgumentExceptionFactory.adaptMissingMandatoryPropertiesException(
          e, d);
    } catch (AuthorizationException e) {
      int msgID = MSGID_DSCFG_ERROR_CREATE_AUTHZ;
      String msg = getMessage(msgID, d.getUserFriendlyName());
      throw new ClientException(LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS,
          msgID, msg);
    } catch (ManagedObjectAlreadyExistsException e) {
      int msgID = MSGID_DSCFG_ERROR_CREATE_MOAEE;
      String msg = getMessage(msgID, d.getUserFriendlyName());
      throw new ClientException(LDAPResultCode.ENTRY_ALREADY_EXISTS,
          msgID, msg);
    } catch (ConcurrentModificationException e) {
      int msgID = MSGID_DSCFG_ERROR_CREATE_CME;
      String msg = getMessage(msgID, d.getUserFriendlyName());
      throw new ClientException(LDAPResultCode.CONSTRAINT_VIOLATION,
          msgID, msg);
    } catch (OperationRejectedException e) {
      int msgID = MSGID_DSCFG_ERROR_CREATE_ORE;
      String msg = getMessage(msgID, d.getUserFriendlyName(), e.getMessage());
      throw new ClientException(LDAPResultCode.CONSTRAINT_VIOLATION,
          msgID, msg);
    } catch (CommunicationException e) {
      int msgID = MSGID_DSCFG_ERROR_CREATE_CE;
      String msg = getMessage(msgID, d.getUserFriendlyName(), e.getMessage());
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msgID,
          msg);
    }

    return 0;
  }



  // Generate the type name - definition mapping table.
  @SuppressWarnings("unchecked")
  private SortedMap<String, ManagedObjectDefinition<? extends C, ?>>
      getSubTypes(AbstractManagedObjectDefinition<C, ?> d) {
    SortedMap<String, ManagedObjectDefinition<? extends C, ?>> map;
    map = new TreeMap<String, ManagedObjectDefinition<? extends C, ?>>();

    // If the top-level definition is instantiable, we use the value
    // "generic".
    if (d instanceof ManagedObjectDefinition) {
      ManagedObjectDefinition<? extends C, ?> mod =
        (ManagedObjectDefinition<? extends C, ?>) d;
      map.put(GENERIC_TYPE, mod);
    }

    // Process its sub-definitions.
    String suffix = "-" + d.getName();
    for (AbstractManagedObjectDefinition<? extends C, ?> c :
        d.getAllChildren()) {
      if (c instanceof ManagedObjectDefinition) {
        ManagedObjectDefinition<? extends C, ?> mod =
          (ManagedObjectDefinition<? extends C, ?>) c;

        // For the type name we shorten it, if possible, by stripping
        // off the trailing part of the name which matches the
        // base-type.
        String name = mod.getName();
        if (name.endsWith(suffix)) {
          name = name.substring(0, name.length() - suffix.length());
        }

        map.put(name, mod);
      }
    }

    return map;
  }



  // Set a property's initial values.
  private <T> void setProperty(ManagedObject<?> mo,
      MyPropertyProvider provider, PropertyDefinition<T> pd) {
    Collection<T> values = provider.getPropertyValues(pd);

    // This cannot fail because the property values have already been
    // validated.
    mo.setPropertyValues(pd, values);
  }
}
