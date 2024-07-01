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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config.dsconfig;

import static com.forgerock.opendj.dsconfig.DsconfigMessages.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.AbstractManagedObjectDefinition;
import org.forgerock.opendj.config.ManagedObjectDefinition;
import org.forgerock.opendj.config.PropertyDefinition;
import org.forgerock.opendj.config.PropertyDefinitionUsageBuilder;
import org.forgerock.opendj.config.PropertyException;
import org.forgerock.opendj.config.RelationDefinition;
import org.forgerock.opendj.config.client.IllegalManagedObjectNameException;
import org.forgerock.opendj.config.client.ManagedObjectDecodingException;
import org.forgerock.opendj.config.client.MissingMandatoryPropertiesException;
import org.forgerock.opendj.config.client.OperationRejectedException;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.ReturnCode;
import com.forgerock.opendj.cli.TableBuilder;
import com.forgerock.opendj.cli.TextTablePrinter;

/** A utility class for converting various admin exception types into argument exceptions. */
public final class ArgumentExceptionFactory {

    /**
     * Creates a ClientException exception from an illegal managed object name exception.
     *
     * @param e
     *            The illegal managed object name exception.
     * @param d
     *            The managed object definition.
     * @return Returns a ClientException exception.
     */
    public static ClientException adaptIllegalManagedObjectNameException(IllegalManagedObjectNameException e,
            AbstractManagedObjectDefinition<?, ?> d) {
        String illegalName = e.getIllegalName();
        PropertyDefinition<?> pd = e.getNamingPropertyDefinition();

        if (illegalName.length() == 0) {
            LocalizableMessage message = ERR_DSCFG_ERROR_ILLEGAL_NAME_EMPTY.get(d.getUserFriendlyPluralName());
            return new ClientException(ReturnCode.ERROR_USER_DATA, message);
        } else if (illegalName.trim().length() == 0) {
            LocalizableMessage message = ERR_DSCFG_ERROR_ILLEGAL_NAME_BLANK.get(d.getUserFriendlyPluralName());
            return new ClientException(ReturnCode.ERROR_USER_DATA, message);
        } else if (pd != null) {
            try {
                pd.decodeValue(illegalName);
            } catch (PropertyException e1) {
                PropertyDefinitionUsageBuilder b = new PropertyDefinitionUsageBuilder(true);
                LocalizableMessage syntax = b.getUsage(pd);

                LocalizableMessage message = ERR_DSCFG_ERROR_ILLEGAL_NAME_SYNTAX.get(illegalName,
                        d.getUserFriendlyName(), syntax);
                return new ClientException(ReturnCode.ERROR_USER_DATA, message);
            }
        }

        LocalizableMessage message = ERR_DSCFG_ERROR_ILLEGAL_NAME_UNKNOWN.get(illegalName, d.getUserFriendlyName());
        return new ClientException(ReturnCode.ERROR_USER_DATA, message);
    }

    /**
     * Creates an argument exception from a property exception.
     *
     * @param e
     *            The property exception.
     * @param d
     *            The managed object definition.
     * @return Returns an argument exception.
     */
    public static ArgumentException adaptPropertyException(PropertyException e,
            AbstractManagedObjectDefinition<?, ?> d) {
        return new ArgumentException(e.getMessageObject());
    }

    /**
     * Displays a table listing reasons why a managed object could not be decoded successfully.
     *
     * @param app
     *            The console application.
     * @param e
     *            The managed object decoding exception.
     */
    public static void displayManagedObjectDecodingException(ConsoleApplication app, ManagedObjectDecodingException e) {
        AbstractManagedObjectDefinition<?, ?> d = e.getPartialManagedObject().getManagedObjectDefinition();
        LocalizableMessage ufn = d.getUserFriendlyName();
        LocalizableMessage msg = e.getCauses().size() == 1 ? ERR_GET_HEADING_MODE_SINGLE.get(ufn)
                                                           : ERR_GET_HEADING_MODE_PLURAL.get(ufn);
        app.errPrintln(msg);
        app.errPrintln();
        TableBuilder builder = new TableBuilder();
        for (PropertyException pe : e.getCauses()) {
            ArgumentException ae = adaptPropertyException(pe, d);
            builder.startRow();
            builder.appendCell("*");
            builder.appendCell(ae.getMessage());
        }

        TextTablePrinter printer = new TextTablePrinter(app.getErrorStream());
        printer.setDisplayHeadings(false);
        printer.setColumnWidth(1, 0);
        printer.setIndentWidth(4);
        builder.print(printer);
    }

    /**
     * Displays a table listing missing mandatory properties.
     *
     * @param app
     *            The console application.
     * @param e
     *            The missing mandatory property exception.
     */
    public static void displayMissingMandatoryPropertyException(ConsoleApplication app,
            MissingMandatoryPropertiesException e) {
        LocalizableMessage ufn = e.getUserFriendlyName();
        LocalizableMessage msg;
        final boolean onePropertyMissing = e.getCauses().size() == 1;
        if (e.isCreate()) {
            msg = onePropertyMissing ? ERR_CREATE_HEADING_MMPE_SINGLE.get(ufn)
                                     : ERR_CREATE_HEADING_MMPE_PLURAL.get(ufn);
        } else {
            msg = onePropertyMissing ? ERR_MODIFY_HEADING_MMPE_SINGLE.get(ufn)
                                     : ERR_MODIFY_HEADING_MMPE_PLURAL.get(ufn);
        }

        app.errPrintln(msg);
        app.errPrintln();
        TableBuilder builder = new TableBuilder();
        builder.addSortKey(0);
        builder.appendHeading(INFO_DSCFG_HEADING_PROPERTY_NAME.get());
        builder.appendHeading(INFO_DSCFG_HEADING_PROPERTY_SYNTAX.get());

        PropertyDefinitionUsageBuilder b = new PropertyDefinitionUsageBuilder(true);
        for (PropertyException pe : e.getCauses()) {
            PropertyDefinition<?> pd = pe.getPropertyDefinition();
            builder.startRow();
            builder.appendCell(pd.getName());
            builder.appendCell(b.getUsage(pd));
        }

        TextTablePrinter printer = new TextTablePrinter(app.getErrorStream());
        printer.setDisplayHeadings(true);
        printer.setColumnWidth(1, 0);
        printer.setIndentWidth(4);
        builder.print(printer);
    }

    /**
     * Displays a table listing the reasons why an operation was rejected.
     *
     * @param app
     *            The console application.
     * @param e
     *            The operation rejected exception.
     */
    public static void displayOperationRejectedException(ConsoleApplication app, OperationRejectedException e) {
        LocalizableMessage ufn = e.getUserFriendlyName();
        LocalizableMessage msg;
        final boolean singleMessage = e.getMessages().size() == 1;

        switch (e.getOperationType()) {
        case CREATE:
            msg = singleMessage ? ERR_DSCFG_ERROR_CREATE_ORE_SINGLE.get(ufn)
                                : ERR_DSCFG_ERROR_CREATE_ORE_PLURAL.get(ufn);
            break;
        case DELETE:
            msg = singleMessage ? ERR_DSCFG_ERROR_DELETE_ORE_SINGLE.get(ufn)
                                : ERR_DSCFG_ERROR_DELETE_ORE_PLURAL.get(ufn);
            break;
        default:
            msg = singleMessage ? ERR_DSCFG_ERROR_MODIFY_ORE_SINGLE.get(ufn)
                                : ERR_DSCFG_ERROR_MODIFY_ORE_PLURAL.get(ufn);
            break;
        }

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
    }

    /**
     * Creates an argument exception which should be used when a property modification argument is incompatible with a
     * previous modification argument.
     *
     * @param arg
     *            The incompatible argument.
     * @return Returns an argument exception.
     */
    public static ArgumentException incompatiblePropertyModification(String arg) {
        LocalizableMessage msg = ERR_DSCFG_ERROR_INCOMPATIBLE_PROPERTY_MOD.get(arg);
        return new ArgumentException(msg);
    }

    /**
     * Creates an argument exception which should be used when the client has not specified a bind password.
     *
     * @param bindDN
     *            The name of the user requiring a password.
     * @return Returns an argument exception.
     */
    public static ArgumentException missingBindPassword(String bindDN) {
        LocalizableMessage msg = ERR_DSCFG_ERROR_NO_PASSWORD.get(bindDN);
        return new ArgumentException(msg);
    }

    /**
     * Creates an argument exception which should be used when the client has not specified a bind password.
     *
     * @param bindDN
     *            The name of the user requiring a password.
     * @return Returns an argument exception.
     */
    public static ArgumentException missingBindPassword(char[] bindDN) {
        LocalizableMessage msg = ERR_DSCFG_ERROR_NO_PASSWORD.get(bindDN);
        return new ArgumentException(msg);
    }

    /**
     * Creates an argument exception which should be used when an argument, which is mandatory when the application is
     * non-interactive, has not been specified.
     *
     * @param arg
     *            The missing argument.
     * @return Returns an argument exception.
     */
    public static ArgumentException missingMandatoryNonInteractiveArgument(Argument arg) {
        LocalizableMessage msg = ERR_DSCFG_ERROR_MISSING_NON_INTERACTIVE_ARG.get(arg.getLongIdentifier());
        return new ArgumentException(msg);
    }

    /**
     * Creates an argument exception which should be used when a property value argument is invalid because it does not
     * a property name.
     *
     * @param arg
     *            The argument having the missing property name.
     * @return Returns an argument exception.
     */
    public static ArgumentException missingNameInPropertyArgument(String arg) {
        LocalizableMessage msg = ERR_DSCFG_ERROR_NO_NAME_IN_PROPERTY_VALUE.get(arg);
        return new ArgumentException(msg);
    }

    /**
     * Creates an argument exception which should be used when a property modification argument is invalid because it
     * does not a property name.
     *
     * @param arg
     *            The argument having the missing property name.
     * @return Returns an argument exception.
     */
    public static ArgumentException missingNameInPropertyModification(String arg) {
        LocalizableMessage msg = ERR_DSCFG_ERROR_NO_NAME_IN_PROPERTY_MOD.get(arg);
        return new ArgumentException(msg);
    }

    /**
     * Creates an argument exception which should be used when a property value argument is invalid because it does not
     * contain a separator between the property name and its value.
     *
     * @param arg
     *            The argument having a missing separator.
     * @return Returns an argument exception.
     */
    public static ArgumentException missingSeparatorInPropertyArgument(String arg) {
        LocalizableMessage msg = ERR_DSCFG_ERROR_NO_SEPARATOR_IN_PROPERTY_VALUE.get(arg);
        return new ArgumentException(msg);
    }

    /**
     * Creates an argument exception which should be used when a property modification argument is invalid because it
     * does not contain a separator between the property name and its value.
     *
     * @param arg
     *            The argument having a missing separator.
     * @return Returns an argument exception.
     */
    public static ArgumentException missingSeparatorInPropertyModification(String arg) {
        LocalizableMessage msg = ERR_DSCFG_ERROR_NO_SEPARATOR_IN_PROPERTY_MOD.get(arg);
        return new ArgumentException(msg);
    }

    /**
     * Creates an argument exception which should be used when a property value argument is invalid because it does not
     * a property value.
     *
     * @param arg
     *            The argument having the missing property value.
     * @return Returns an argument exception.
     */
    public static ArgumentException missingValueInPropertyArgument(String arg) {
        LocalizableMessage msg = ERR_DSCFG_ERROR_NO_VALUE_IN_PROPERTY_VALUE.get(arg);
        return new ArgumentException(msg);
    }

    /**
     * Creates an argument exception which should be used when a property modification argument is invalid because it
     * does not a property value.
     *
     * @param arg
     *            The argument having the missing property value.
     * @return Returns an argument exception.
     */
    public static ArgumentException missingValueInPropertyModification(String arg) {
        LocalizableMessage msg = ERR_DSCFG_ERROR_NO_NAME_IN_PROPERTY_MOD.get(arg);
        return new ArgumentException(msg);
    }

    /**
     * Creates an argument exception which should be used when the connection parameters could not be read from the
     * standard input.
     *
     * @param cause
     *            The reason why the connection parameters could not be read.
     * @return Returns an argument exception.
     */
    public static ArgumentException unableToReadConnectionParameters(Exception cause) {
        LocalizableMessage message = ERR_DSCFG_ERROR_CANNOT_READ_CONNECTION_PARAMETERS.get(cause.getMessage());
        return new ArgumentException(message, cause);
    }

    /**
     * Creates an argument exception which should be used when the bind password could not be read from the standard
     * input because the application is non-interactive.
     *
     * @return Returns an argument exception.
     */
    public static ArgumentException unableToReadBindPasswordInteractively() {
        LocalizableMessage message = ERR_DSCFG_ERROR_BIND_PASSWORD_NONINTERACTIVE.get();
        return new ArgumentException(message);
    }

    /**
     * Creates an argument exception which should be used when an attempt is made to reset a mandatory property that
     * does not have any default values.
     *
     * @param d
     *            The managed object definition.
     * @param name
     *            The name of the mandatory property.
     * @param setOption
     *            The name of the option which should be used to set the property's values.
     * @return Returns an argument exception.
     */
    public static ArgumentException unableToResetMandatoryProperty(AbstractManagedObjectDefinition<?, ?> d,
            String name, String setOption) {
        LocalizableMessage message = ERR_DSCFG_ERROR_UNABLE_TO_RESET_MANDATORY_PROPERTY.get(
                d.getUserFriendlyPluralName(), name, setOption);
        return new ArgumentException(message);
    }

    /**
     * Creates an argument exception which should be used when an attempt is made to reset a property with a value.
     *
     * @param name
     *            The name of the mandatory property.
     * @param resetOption
     *            The name of the option which should be used to reset the property's values.
     * @return Returns an argument exception.
     */
    public static ArgumentException unableToResetPropertyWithValue(String name, String resetOption) {
        LocalizableMessage message = ERR_DSCFG_ERROR_UNABLE_TO_RESET_PROPERTY_WITH_VALUE.get(resetOption, name,
                resetOption);
        return new ArgumentException(message);
    }

    /**
     * Creates an argument exception which should be used when an attempt is made to set the naming property for a
     * managed object during creation.
     *
     * @param d
     *            The managed object definition.
     * @param pd
     *            The naming property definition.
     * @return Returns an argument exception.
     */
    public static ArgumentException unableToSetNamingProperty(AbstractManagedObjectDefinition<?, ?> d,
            PropertyDefinition<?> pd) {
        LocalizableMessage message = ERR_DSCFG_ERROR_UNABLE_TO_SET_NAMING_PROPERTY.get(pd.getName(),
                d.getUserFriendlyName());
        return new ArgumentException(message);
    }

    /**
     * Creates an argument exception which should be used when a component category argument is not recognized.
     *
     * @param categoryName
     *            The unrecognized component category.
     * @return Returns an argument exception.
     */
    public static ArgumentException unknownCategory(String categoryName) {
        LocalizableMessage msg = ERR_DSCFG_ERROR_CATEGORY_UNRECOGNIZED.get(categoryName);
        return new ArgumentException(msg);
    }

    /**
     * Creates an argument exception which should be used when a property name is not recognized.
     *
     * @param d
     *            The managed object definition.
     * @param name
     *            The unrecognized property name.
     * @return Returns an argument exception.
     */
    public static ArgumentException unknownProperty(AbstractManagedObjectDefinition<?, ?> d, String name) {
        LocalizableMessage message = ERR_DSCFG_ERROR_PROPERTY_UNRECOGNIZED.get(name, d.getUserFriendlyPluralName());
        return new ArgumentException(message);
    }

    /**
     * Creates an argument exception which should be used when a property name is not recognized.
     *
     * @param name
     *            The unrecognized property name.
     * @return Returns an argument exception.
     */
    public static ArgumentException unknownProperty(String name) {
        LocalizableMessage message = ERR_DSCFG_ERROR_PROPERTY_UNRECOGNIZED_NO_DEFN.get(name);
        return new ArgumentException(message);
    }

    /**
     * Creates an argument exception which should be used when a sub-type argument in a create-xxx sub-command is not
     * recognized.
     *
     * @param r
     *            The relation definition.
     * @param typeName
     *            The unrecognized property sub-type.
     * @param typeUsage
     *            A usage string describing the allowed sub-types.
     * @return Returns an argument exception.
     */
    public static ArgumentException unknownSubType(RelationDefinition<?, ?> r, String typeName, String typeUsage) {
        LocalizableMessage msg = ERR_DSCFG_ERROR_SUB_TYPE_UNRECOGNIZED
                .get(typeName, r.getUserFriendlyName(), typeUsage);
        return new ArgumentException(msg);
    }

    /**
     * Creates an argument exception which should be used when a managed object type argument is not associated with a
     * category.
     *
     * @param categoryName
     *            The component category.
     * @param typeName
     *            The unrecognized component type.
     * @return Returns an argument exception.
     */
    public static ArgumentException unknownTypeForCategory(String typeName, String categoryName) {
        LocalizableMessage msg = ERR_DSCFG_ERROR_CATEGORY_TYPE_UNRECOGNIZED.get(typeName, categoryName);
        return new ArgumentException(msg);
    }

    /**
     * Creates an argument exception which should be used when a multi-valued property does not contain a given value.
     *
     * @param value
     *            The property value.
     * @param propertyName
     *            The property name.
     * @return Returns an argument exception.
     */
    public static ArgumentException unknownValueForMultiValuedProperty(String value, String propertyName) {
        LocalizableMessage msg = ERR_DSCFG_ERROR_VALUE_DOES_NOT_EXIST.get(value, propertyName);
        return new ArgumentException(msg);
    }

    /**
     * Creates an argument exception which should be used when a child component does not exist.
     *
     * @param componentName
     *            The component name.
     * @return Returns an argument exception.
     */
    public static ArgumentException unknownValueForChildComponent(String componentName) {
        LocalizableMessage msg = ERR_DSCFG_ERROR_FINDER_NO_CHILDREN.get(componentName);
        return new ArgumentException(msg);
    }

    /**
     * Creates a CLI exception which should be used when a managed object is retrieved but does not have the correct
     * type appropriate for the associated sub-command.
     *
     * @param r
     *            The relation definition.
     * @param d
     *            The definition of the managed object that was retrieved.
     * @param subcommandName
     *            the sub-command name.
     * @return Returns a Client exception.
     */
    public static ClientException wrongManagedObjectType(RelationDefinition<?, ?> r, ManagedObjectDefinition<?, ?> d,
            String subcommandName) {
        LocalizableMessage msg = ERR_DSCFG_ERROR_TYPE_UNRECOGNIZED_FOR_SUBCOMMAND.get(d.getUserFriendlyName(),
                subcommandName);
        return new ClientException(ReturnCode.ERROR_USER_DATA, msg);
    }

    /** Prevent instantiation. */
    private ArgumentExceptionFactory() {
        // No implementation required.
    }
}
