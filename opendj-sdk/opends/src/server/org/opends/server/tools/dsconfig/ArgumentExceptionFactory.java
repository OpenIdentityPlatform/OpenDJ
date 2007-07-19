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

import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.DefaultBehaviorException;
import org.opends.server.admin.IllegalPropertyValueException;
import org.opends.server.admin.IllegalPropertyValueStringException;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyDefinitionUsageBuilder;
import org.opends.server.admin.PropertyException;
import org.opends.server.admin.PropertyIsMandatoryException;
import org.opends.server.admin.PropertyIsReadOnlyException;
import org.opends.server.admin.PropertyIsSingleValuedException;
import org.opends.server.admin.RelationDefinition;
import org.opends.server.admin.client.IllegalManagedObjectNameException;
import org.opends.server.admin.client.MissingMandatoryPropertiesException;
import org.opends.server.util.args.ArgumentException;



/**
 * A utility class for converting various admin exception types into
 * argument exceptions.
 */
final class ArgumentExceptionFactory {

  /**
   * Creates an argument exception from an illegal managed object name
   * exception.
   *
   * @param e
   *          The illegal managed object name exception.
   * @param d
   *          The managed object definition.
   * @return Returns an argument exception.
   */
  public static ArgumentException adaptIllegalManagedObjectNameException(
      IllegalManagedObjectNameException e, AbstractManagedObjectDefinition d) {
    String illegalName = e.getIllegalName();
    PropertyDefinition<?> pd = e.getNamingPropertyDefinition();

    if (illegalName.length() == 0) {
      int msgID = MSGID_DSCFG_ERROR_ILLEGAL_NAME_EMPTY;
      String message = getMessage(msgID, d.getUserFriendlyPluralName());
      return new ArgumentException(msgID, message);
    } else if (illegalName.trim().length() == 0) {
      int msgID = MSGID_DSCFG_ERROR_ILLEGAL_NAME_BLANK;
      String message = getMessage(msgID, d.getUserFriendlyPluralName());
      return new ArgumentException(msgID, message);
    } else if (pd != null) {
      try {
        pd.decodeValue(illegalName);
      } catch (IllegalPropertyValueStringException e1) {
        PropertyDefinitionUsageBuilder b = new PropertyDefinitionUsageBuilder(
            true);
        String syntax = b.getUsage(pd);

        int msgID = MSGID_DSCFG_ERROR_ILLEGAL_NAME_SYNTAX;
        String message = getMessage(msgID, illegalName,
            d.getUserFriendlyName(), pd.getName(), syntax);
        return new ArgumentException(msgID, message);
      }
    }

    int msgID = MSGID_DSCFG_ERROR_ILLEGAL_NAME_UNKNOWN;
    String message = getMessage(msgID, illegalName, d.getUserFriendlyName());
    return new ArgumentException(msgID, message);
  }



  /**
   * Creates an argument exception from a missing mandatory properties
   * exception.
   *
   * @param e
   *          The missing mandatory properties exception.
   * @param d
   *          The managed object definition.
   * @return Returns an argument exception.
   */
  public static ArgumentException adaptMissingMandatoryPropertiesException(
      MissingMandatoryPropertiesException e,
      AbstractManagedObjectDefinition d) {
    int msgID = MSGID_DSCFG_ERROR_CREATE_MMPE;
    StringBuilder builder = new StringBuilder();
    boolean isFirst = true;
    for (PropertyIsMandatoryException pe : e.getCauses()) {
      if (!isFirst) {
        builder.append(", ");
      }
      builder.append(pe.getPropertyDefinition().getName());
      isFirst = false;
    }
    String msg = getMessage(msgID, d.getUserFriendlyName(), builder.toString());
    return new ArgumentException(msgID, msg);
  }



  /**
   * Creates an argument exception from a property exception.
   *
   * @param e
   *          The property exception.
   * @param d
   *          The managed object definition.
   * @return Returns an argument exception.
   */
  public static ArgumentException adaptPropertyException(PropertyException e,
      AbstractManagedObjectDefinition d) {
    if (e instanceof IllegalPropertyValueException) {
      IllegalPropertyValueException pe = (IllegalPropertyValueException) e;
      return adapt(d, pe);
    } else if (e instanceof IllegalPropertyValueStringException) {
      IllegalPropertyValueStringException pe =
        (IllegalPropertyValueStringException) e;
      return adapt(d, pe);
    } else if (e instanceof PropertyIsMandatoryException) {
      PropertyIsMandatoryException pe = (PropertyIsMandatoryException) e;
      return adapt(d, pe);
    } else if (e instanceof PropertyIsSingleValuedException) {
      PropertyIsSingleValuedException pe = (PropertyIsSingleValuedException) e;
      return adapt(d, pe);
    } else if (e instanceof PropertyIsReadOnlyException) {
      PropertyIsReadOnlyException pe = (PropertyIsReadOnlyException) e;
      return adapt(d, pe);
    } else if (e instanceof DefaultBehaviorException) {
      DefaultBehaviorException pe = (DefaultBehaviorException) e;
      return adapt(d, pe);
    } else {
      int msgID = MSGID_DSCFG_ERROR_PROPERTY_UNKNOWN_ERROR;
      String message = getMessage(msgID, d.getUserFriendlyName(), e
          .getPropertyDefinition().getName(), e.getMessage());
      return new ArgumentException(msgID, message);
    }
  }



  /**
   * Creates an argument exception which should be used when a
   * property modification argument is incompatible with a previous
   * modification argument.
   *
   * @param arg
   *          The incompatible argument.
   * @return Returns an argument exception.
   */
  public static ArgumentException incompatiblePropertyModification(String arg) {
    int msgID = MSGID_DSCFG_ERROR_INCOMPATIBLE_PROPERTY_MOD;
    String msg = getMessage(msgID, arg);
    return new ArgumentException(msgID, msg);
  }



  /**
   * Creates an argument exception which should be used when the
   * client has not specified a bind password.
   *
   * @param bindDN
   *          The name of the user requiring a password.
   * @return Returns an argument exception.
   */
  public static ArgumentException missingBindPassword(String bindDN) {
    int msgID = MSGID_DSCFG_ERROR_NO_PASSWORD;
    String msg = getMessage(msgID, bindDN);
    return new ArgumentException(msgID, msg);
  }



  /**
   * Creates an argument exception which should be used when a
   * property value argument is invalid because it does not a property
   * name.
   *
   * @param arg
   *          The argument having the missing property name.
   * @return Returns an argument exception.
   */
  public static ArgumentException missingNameInPropertyArgument(String arg) {
    int msgID = MSGID_DSCFG_ERROR_NO_NAME_IN_PROPERTY_VALUE;
    String msg = getMessage(msgID, arg);
    return new ArgumentException(msgID, msg);
  }



  /**
   * Creates an argument exception which should be used when a
   * property modification argument is invalid because it does not a
   * property name.
   *
   * @param arg
   *          The argument having the missing property name.
   * @return Returns an argument exception.
   */
  public static ArgumentException missingNameInPropertyModification(
      String arg) {
    int msgID = MSGID_DSCFG_ERROR_NO_NAME_IN_PROPERTY_MOD;
    String msg = getMessage(msgID, arg);
    return new ArgumentException(msgID, msg);
  }



  /**
   * Creates an argument exception which should be used when a
   * property value argument is invalid because it does not contain a
   * separator between the property name and its value.
   *
   * @param arg
   *          The argument having a missing separator.
   * @return Returns an argument exception.
   */
  public static ArgumentException missingSeparatorInPropertyArgument(
      String arg) {
    int msgID = MSGID_DSCFG_ERROR_NO_SEPARATOR_IN_PROPERTY_VALUE;
    String msg = getMessage(msgID, arg);
    return new ArgumentException(msgID, msg);
  }



  /**
   * Creates an argument exception which should be used when a
   * property modification argument is invalid because it does not
   * contain a separator between the property name and its value.
   *
   * @param arg
   *          The argument having a missing separator.
   * @return Returns an argument exception.
   */
  public static ArgumentException missingSeparatorInPropertyModification(
      String arg) {
    int msgID = MSGID_DSCFG_ERROR_NO_SEPARATOR_IN_PROPERTY_MOD;
    String msg = getMessage(msgID, arg);
    return new ArgumentException(msgID, msg);
  }



  /**
   * Creates an argument exception which should be used when a
   * property value argument is invalid because it does not a property
   * value.
   *
   * @param arg
   *          The argument having the missing property value.
   * @return Returns an argument exception.
   */
  public static ArgumentException missingValueInPropertyArgument(String arg) {
    int msgID = MSGID_DSCFG_ERROR_NO_VALUE_IN_PROPERTY_VALUE;
    String msg = getMessage(msgID, arg);
    return new ArgumentException(msgID, msg);
  }



  /**
   * Creates an argument exception which should be used when a
   * property modification argument is invalid because it does not a
   * property value.
   *
   * @param arg
   *          The argument having the missing property value.
   * @return Returns an argument exception.
   */
  public static ArgumentException missingValueInPropertyModification(
      String arg) {
    int msgID = MSGID_DSCFG_ERROR_NO_NAME_IN_PROPERTY_MOD;
    String msg = getMessage(msgID, arg);
    return new ArgumentException(msgID, msg);
  }



  /**
   * Creates an argument exception which should be used when an
   * attempt is made to set the naming property for a managed object
   * during creation.
   *
   * @param d
   *          The managed object definition.
   * @param pd
   *          The naming property definition.
   * @return Returns an argument exception.
   */
  public static ArgumentException unableToSetNamingProperty(
      AbstractManagedObjectDefinition d, PropertyDefinition<?> pd) {
    int msgID = MSGID_DSCFG_ERROR_UNABLE_TO_SET_NAMING_PROPERTY;
    String message = getMessage(msgID, pd.getName(), d.getUserFriendlyName());
    return new ArgumentException(msgID, message);
  }



  /**
   * Creates an argument exception which should be used when the bind
   * password could not be read from the standard input.
   *
   * @param cause
   *          The reason why the bind password could not be read.
   * @return Returns an argument exception.
   */
  public static ArgumentException unableToReadBindPassword(Exception cause) {
    int msgID = MSGID_DSCFG_ERROR_CANNOT_READ_LDAP_BIND_PASSWORD;
    String message = getMessage(msgID, cause.getMessage());
    return new ArgumentException(msgID, message, cause);
  }



  /**
   * Creates an argument exception which should be used when the bind
   * password could not be read from the standard input because the
   * application is non-interactive.
   *
   * @return Returns an argument exception.
   */
  public static ArgumentException unableToReadBindPasswordInteractively() {
    int msgID = MSGID_DSCFG_ERROR_BIND_PASSWORD_NONINTERACTIVE;
    String message = getMessage(msgID);
    return new ArgumentException(msgID, message);
  }



  /**
   * Creates an argument exception which should be used when an
   * attempt is made to reset a mandatory property that does not have
   * any default values.
   *
   * @param d
   *          The managed object definition.
   * @param name
   *          The name of the mandatory property.
   * @param setOption
   *          The name of the option which should be used to set the
   *          property's values.
   * @return Returns an argument exception.
   */
  public static ArgumentException unableToResetMandatoryProperty(
      AbstractManagedObjectDefinition d, String name, String setOption) {
    int msgID = MSGID_DSCFG_ERROR_UNABLE_TO_RESET_MANDATORY_PROPERTY;
    String message = getMessage(msgID, d.getUserFriendlyPluralName(), name,
        setOption);
    return new ArgumentException(msgID, message);
  }



  /**
   * Creates an argument exception which should be used when a
   * property name is not recognized.
   *
   * @param d
   *          The managed object definition.
   * @param name
   *          The unrecognized property name.
   * @return Returns an argument exception.
   */
  public static ArgumentException unknownProperty(
      AbstractManagedObjectDefinition d, String name) {
    int msgID = MSGID_DSCFG_ERROR_PROPERTY_UNRECOGNIZED;
    String message = getMessage(msgID, name, d.getUserFriendlyPluralName());
    return new ArgumentException(msgID, message);
  }



  /**
   * Creates an argument exception which should be used when a
   * sub-type argument in a create-xxx sub-command is not recognized.
   *
   * @param r
   *          The relation definition.
   * @param typeName
   *          The unrecognized property sub-type.
   * @param typeUsage
   *          A usage string describing the allowed sub-types.
   * @return Returns an argument exception.
   */
  public static ArgumentException unknownSubType(RelationDefinition r,
      String typeName, String typeUsage) {
    int msgID = MSGID_DSCFG_ERROR_SUB_TYPE_UNRECOGNIZED;
    String msg = getMessage(msgID, typeName, r.getUserFriendlyName(),
        typeUsage);
    return new ArgumentException(msgID, msg);
  }



  /**
   * Creates an argument exception which should be used when a managed
   * object type argument is not recognized.
   *
   * @param typeName
   *          The unrecognized property sub-type.
   * @return Returns an argument exception.
   */
  public static ArgumentException unknownType(String typeName) {
    int msgID = MSGID_DSCFG_ERROR_TYPE_UNRECOGNIZED;
    String msg = getMessage(msgID, typeName);
    return new ArgumentException(msgID, msg);
  }



  /**
   * Creates an argument exception which should be used when a managed
   * object is retrieved but does not have the correct type
   * appropriate for the associated sub-command.
   *
   * @param r
   *          The relation definition.
   * @param d
   *          The definition of the managed object that was retrieved.
   * @return Returns an argument exception.
   */
  public static ArgumentException wrongManagedObjectType(RelationDefinition r,
      ManagedObjectDefinition d) {
    int msgID = MSGID_DSCFG_ERROR_TYPE_UNRECOGNIZED;
    String msg = getMessage(msgID, r.getUserFriendlyName(), d
        .getUserFriendlyName());
    return new ArgumentException(msgID, msg);
  }



  /**
   * Creates an argument exception from a default behavior exception.
   *
   * @param d
   *          The managed object definition.
   * @param e
   *          The default behavior exception.
   * @return Returns an argument exception.
   */
  private static ArgumentException adapt(AbstractManagedObjectDefinition d,
      DefaultBehaviorException e) {
    int msgID = MSGID_DSCFG_ERROR_PROPERTY_DEFAULT_BEHAVIOR;
    String message = getMessage(msgID, d.getUserFriendlyName(), e
        .getPropertyDefinition().getName(), e.getMessage());
    return new ArgumentException(msgID, message);
  }



  /**
   * Creates an argument exception from an illegal property value
   * exception.
   *
   * @param d
   *          The managed object definition.
   * @param e
   *          The illegal property value exception.
   * @return Returns an argument exception.
   */
  private static ArgumentException adapt(AbstractManagedObjectDefinition d,
      IllegalPropertyValueException e) {
    PropertyDefinitionUsageBuilder b = new PropertyDefinitionUsageBuilder(true);
    String syntax = b.getUsage(e.getPropertyDefinition());

    if (syntax.length() > 20) {
      // syntax =
      // getMessage(MSGID_DSCFG_DESCRIPTION_PROPERTY_SYNTAX_HELP);
    }

    int msgID = MSGID_DSCFG_ERROR_PROPERTY_INVALID_VALUE;
    String message = getMessage(msgID, String.valueOf(e.getIllegalValue()), d
        .getUserFriendlyName(), e.getPropertyDefinition().getName(), syntax);
    return new ArgumentException(msgID, message);
  }



  /**
   * Creates an argument exception from an illegal property string
   * value exception.
   *
   * @param d
   *          The managed object definition.
   * @param e
   *          The illegal property string value exception.
   * @return Returns an argument exception.
   */
  private static ArgumentException adapt(AbstractManagedObjectDefinition d,
      IllegalPropertyValueStringException e) {
    PropertyDefinitionUsageBuilder b = new PropertyDefinitionUsageBuilder(true);
    String syntax = b.getUsage(e.getPropertyDefinition());

    if (syntax.length() > 20) {
      // syntax =
      // getMessage(MSGID_DSCFG_DESCRIPTION_PROPERTY_SYNTAX_HELP);
    }

    int msgID = MSGID_DSCFG_ERROR_PROPERTY_INVALID_VALUE;
    String message = getMessage(msgID, String
        .valueOf(e.getIllegalValueString()), d.getUserFriendlyName(), e
        .getPropertyDefinition().getName(), syntax);
    return new ArgumentException(msgID, message);
  }



  /**
   * Creates an argument exception from a property is mandatory
   * exception.
   *
   * @param d
   *          The managed object definition.
   * @param e
   *          The property is mandatory exception.
   * @return Returns an argument exception.
   */
  private static ArgumentException adapt(AbstractManagedObjectDefinition d,
      PropertyIsMandatoryException e) {
    int msgID = MSGID_DSCFG_ERROR_PROPERTY_MANDATORY;
    String message = getMessage(msgID, d.getUserFriendlyName(), e
        .getPropertyDefinition().getName());
    return new ArgumentException(msgID, message);
  }



  /**
   * Creates an argument exception from a property is read-only
   * exception.
   *
   * @param d
   *          The managed object definition.
   * @param e
   *          The property is read-only exception.
   * @return Returns an argument exception.
   */
  private static ArgumentException adapt(AbstractManagedObjectDefinition d,
      PropertyIsReadOnlyException e) {
    int msgID = MSGID_DSCFG_ERROR_PROPERTY_READ_ONLY;
    String message = getMessage(msgID, d.getUserFriendlyName(), e
        .getPropertyDefinition().getName());
    return new ArgumentException(msgID, message);
  }



  /**
   * Creates an argument exception from a property is single-valued
   * exception.
   *
   * @param d
   *          The managed object definition.
   * @param e
   *          The property is single-valued exception.
   * @return Returns an argument exception.
   */
  private static ArgumentException adapt(AbstractManagedObjectDefinition d,
      PropertyIsSingleValuedException e) {
    int msgID = MSGID_DSCFG_ERROR_PROPERTY_SINGLE_VALUED;
    String message = getMessage(msgID, d.getUserFriendlyName(), e
        .getPropertyDefinition().getName());
    return new ArgumentException(msgID, message);
  }



  // Prevent instantiation.
  private ArgumentExceptionFactory() {
    // No implementation required.
  }
}
