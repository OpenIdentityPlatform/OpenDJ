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
package org.opends.server.workflowelement.localbackend;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.util.ServerConstants.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.api.PasswordValidator;
import org.opends.server.controls.PasswordPolicyErrorType;
import org.opends.server.controls.PasswordPolicyResponseControl;
import org.opends.server.core.AddOperation;
import org.opends.server.core.AddOperationWrapper;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PasswordPolicy;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.schema.AuthPasswordSyntax;
import org.opends.server.schema.BooleanSyntax;
import org.opends.server.schema.UserPasswordSyntax;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.ResultCode;
import org.opends.server.types.operation.PostOperationAddOperation;
import org.opends.server.types.operation.PostResponseAddOperation;
import org.opends.server.types.operation.PreOperationAddOperation;
import org.opends.server.util.TimeThread;

/**
 * This class defines an operation used to add an entry in a local backend
 * of the Directory Server.
 */
public class LocalBackendAddOperation extends AddOperationWrapper
  implements PreOperationAddOperation,
             PostOperationAddOperation,
             PostResponseAddOperation
{

  // The entry being added to the server.
  private Entry entry;

  /**
   * Creates a new operation that may be used to add a new entry in a
   * local backend of the Directory Server.
   *
   * @param add The operation to enhance.
   */
  public LocalBackendAddOperation(AddOperation add)
  {
    super(add);
    LocalBackendWorkflowElement.attachLocalOperation (add, this);
  }


  /**
   * Retrieves the entry to be added to the server.  Note that this will not be
   * available to pre-parse plugins or during the conflict resolution portion of
   * the synchronization processing.
   *
   * @return  The entry to be added to the server, or <CODE>null</CODE> if it is
   *          not yet available.
   */
  public final Entry getEntryToAdd()
  {
    return entry;
  }

  /**
   * Sets the entry to be added to the server.
   *
   * @param  entry - The entry to be added to the server, or <CODE>null</CODE>
   *                 if it is not yet available.
   */
  public final void setEntryToAdd(Entry entry){
    this.entry = entry;
  }

  /**
   * Performs all password policy processing necessary for the provided add
   * operation.
   *
   * @param  passwordPolicy  The password policy associated with the entry to be
   *                         added.
   * @param  userEntry       The user entry being added.
   *
   * @throws  DirectoryException  If a problem occurs while performing password
   *                              policy processing for the add operation.
   */
  public final void handlePasswordPolicy(PasswordPolicy passwordPolicy,
                                         Entry userEntry)
         throws DirectoryException
  {
    // See if a password was specified.
    AttributeType passwordAttribute = passwordPolicy.getPasswordAttribute();
    List<Attribute> attrList = userEntry.getAttribute(passwordAttribute);
    if ((attrList == null) || attrList.isEmpty())
    {
      // The entry doesn't have a password, so no action is required.
      return;
    }
    else if (attrList.size() > 1)
    {
      // This must mean there are attribute options, which we won't allow for
      // passwords.
      Message message = ERR_PWPOLICY_ATTRIBUTE_OPTIONS_NOT_ALLOWED.get(
          passwordAttribute.getNameOrOID());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    Attribute passwordAttr = attrList.get(0);
    if (passwordAttr.hasOptions())
    {
      Message message = ERR_PWPOLICY_ATTRIBUTE_OPTIONS_NOT_ALLOWED.get(
          passwordAttribute.getNameOrOID());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    LinkedHashSet<AttributeValue> values = passwordAttr.getValues();
    if (values.isEmpty())
    {
      // This will be treated the same as not having a password.
      return;
    }

    if ((! passwordPolicy.allowMultiplePasswordValues()) && (values.size() > 1))
    {
      // FIXME -- What if they're pre-encoded and might all be the same?
      addPWPolicyControl(PasswordPolicyErrorType.PASSWORD_MOD_NOT_ALLOWED);

      Message message = ERR_PWPOLICY_MULTIPLE_PW_VALUES_NOT_ALLOWED.get(
          passwordAttribute.getNameOrOID());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    CopyOnWriteArrayList<PasswordStorageScheme> defaultStorageSchemes =
         passwordPolicy.getDefaultStorageSchemes();
    LinkedHashSet<AttributeValue> newValues =
         new LinkedHashSet<AttributeValue>(defaultStorageSchemes.size());
    for (AttributeValue v : values)
    {
      ByteString value = v.getValue();

      // See if the password is pre-encoded.
      if (passwordPolicy.usesAuthPasswordSyntax())
      {
        if (AuthPasswordSyntax.isEncoded(value))
        {
          if (passwordPolicy.allowPreEncodedPasswords())
          {
            newValues.add(v);
            continue;
          }
          else
          {
            addPWPolicyControl(
                 PasswordPolicyErrorType.INSUFFICIENT_PASSWORD_QUALITY);

            Message message = ERR_PWPOLICY_PREENCODED_NOT_ALLOWED.get(
                passwordAttribute.getNameOrOID());
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                         message);
          }
        }
      }
      else
      {
        if (UserPasswordSyntax.isEncoded(value))
        {
          if (passwordPolicy.allowPreEncodedPasswords())
          {
            newValues.add(v);
            continue;
          }
          else
          {
            addPWPolicyControl(
                 PasswordPolicyErrorType.INSUFFICIENT_PASSWORD_QUALITY);

            Message message = ERR_PWPOLICY_PREENCODED_NOT_ALLOWED.get(
                passwordAttribute.getNameOrOID());
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                         message);
          }
        }
      }


      // See if the password passes validation.  We should only do this if
      // validation should be performed for administrators.
      if (! passwordPolicy.skipValidationForAdministrators())
      {
        // There are never any current passwords for an add operation.
        HashSet<ByteString> currentPasswords = new HashSet<ByteString>(0);
        MessageBuilder invalidReason = new MessageBuilder();
        for (PasswordValidator<?> validator :
             passwordPolicy.getPasswordValidators().values())
        {
          if (! validator.passwordIsAcceptable(value, currentPasswords, this,
                                               userEntry, invalidReason))
          {
            addPWPolicyControl(
                 PasswordPolicyErrorType.INSUFFICIENT_PASSWORD_QUALITY);

            Message message = ERR_PWPOLICY_VALIDATION_FAILED.
                get(passwordAttribute.getNameOrOID(),
                    String.valueOf(invalidReason));
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                         message);
          }
        }
      }


      // Encode the password.
      if (passwordPolicy.usesAuthPasswordSyntax())
      {
        for (PasswordStorageScheme s : defaultStorageSchemes)
        {
          ByteString encodedValue = s.encodeAuthPassword(value);
          newValues.add(new AttributeValue(passwordAttribute, encodedValue));
        }
      }
      else
      {
        for (PasswordStorageScheme s : defaultStorageSchemes)
        {
          ByteString encodedValue = s.encodePasswordWithScheme(value);
          newValues.add(new AttributeValue(passwordAttribute, encodedValue));
        }
      }
    }


    // Put the new encoded values in the entry.
    passwordAttr.setValues(newValues);


    // Set the password changed time attribute.
    ByteString timeString =
         new ASN1OctetString(TimeThread.getGeneralizedTime());
    AttributeType changedTimeType =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_CHANGED_TIME_LC);
    if (changedTimeType == null)
    {
      changedTimeType = DirectoryServer.getDefaultAttributeType(
                                             OP_ATTR_PWPOLICY_CHANGED_TIME);
    }

    LinkedHashSet<AttributeValue> changedTimeValues =
         new LinkedHashSet<AttributeValue>(1);
    changedTimeValues.add(new AttributeValue(changedTimeType, timeString));

    ArrayList<Attribute> changedTimeList = new ArrayList<Attribute>(1);
    changedTimeList.add(new Attribute(changedTimeType,
                                      OP_ATTR_PWPOLICY_CHANGED_TIME,
                                      changedTimeValues));

    userEntry.putAttribute(changedTimeType, changedTimeList);


    // If we should force change on add, then set the appropriate flag.
    if (passwordPolicy.forceChangeOnAdd())
    {
      addPWPolicyControl(PasswordPolicyErrorType.CHANGE_AFTER_RESET);

      AttributeType resetType =
           DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_RESET_REQUIRED_LC);
      if (resetType == null)
      {
        resetType = DirectoryServer.getDefaultAttributeType(
                                         OP_ATTR_PWPOLICY_RESET_REQUIRED);
      }

      LinkedHashSet<AttributeValue> resetValues = new
           LinkedHashSet<AttributeValue>(1);
      resetValues.add(BooleanSyntax.createBooleanValue(true));

      ArrayList<Attribute> resetList = new ArrayList<Attribute>(1);
      resetList.add(new Attribute(resetType, OP_ATTR_PWPOLICY_RESET_REQUIRED,
                                  resetValues));
      userEntry.putAttribute(resetType, resetList);
    }
  }

  /**
   * Adds a password policy response control if the corresponding request
   * control was included.
   *
   * @param  errorType  The error type to use for the response control.
   */
  private void addPWPolicyControl(PasswordPolicyErrorType errorType)
  {
    for (Control c : getRequestControls())
    {
      if (c.getOID().equals(OID_PASSWORD_POLICY_CONTROL))
      {
        addResponseControl(new PasswordPolicyResponseControl(null, 0,
                                                             errorType));
      }
    }
  }

  /**
   * Adds the provided objectClass to the entry, along with its superior classes
   * if appropriate.
   *
   * @param  objectClass  The objectclass to add to the entry.
   */
  public final void addObjectClassChain(ObjectClass objectClass)
  {
    Map<ObjectClass, String> objectClasses = getObjectClasses();
    if (objectClasses != null){
      if (! objectClasses.containsKey(objectClass))
      {
        objectClasses.put(objectClass, objectClass.getNameOrOID());
      }

      ObjectClass superiorClass = objectClass.getSuperiorClass();
      if ((superiorClass != null) &&
          (! objectClasses.containsKey(superiorClass)))
      {
        addObjectClassChain(superiorClass);
      }
    }
  }

}
