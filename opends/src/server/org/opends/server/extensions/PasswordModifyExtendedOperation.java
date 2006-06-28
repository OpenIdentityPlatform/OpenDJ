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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.Lock;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.ExtendedOperationHandler;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.core.InitializationException;
import org.opends.server.core.LockManager;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.ByteString;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchScope;

import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements the password modify extended operation defined in RFC
 * 3062.  It includes support for requiring the user's current password as well
 * as for generating a new password if none was provided.
 */
public class PasswordModifyExtendedOperation
       extends ExtendedOperationHandler
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.extensions.PasswordModifyExtendedOperation";



  /**
   * Create an instance of this password modify extended operation.  All
   * initialization should be performed in the
   * <CODE>initializeExtendedOperationHandler</CODE> method.
   */
  public PasswordModifyExtendedOperation()
  {
    super();

    assert debugConstructor(CLASS_NAME);
  }




  /**
   * Initializes this extended operation handler based on the information in the
   * provided configuration entry.  It should also register itself with the
   * Directory Server for the particular kinds of extended operations that it
   * will process.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this extended operation handler.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   *
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the server
   *                                   configuration.
   */
  public void initializeExtendedOperationHandler(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {
    assert debugEnter(CLASS_NAME, "initializeExtendedOperationHandler",
                      String.valueOf(configEntry));

    // NYI -- parse the config entry for any settings that might be defined
    // This can include:
    // - Whether to require the old password
    // - Whether to allow automatic generation of a new password
    // - The class name for an algorithm to generate new passwords

    DirectoryServer.registerSupportedExtension(OID_PASSWORD_MODIFY_REQUEST,
                                               this);
  }



  /**
   * Performs any finalization that may be necessary for this extended
   * operation handler.  By default, no finalization is performed.
   */
  public void finalizeExtendedOperationHandler()
  {
    assert debugEnter(CLASS_NAME, "finalizeExtendedOperationHandler");

    DirectoryServer.deregisterSupportedExtension(OID_PASSWORD_MODIFY_REQUEST);
  }



  /**
   * Processes the provided extended operation.
   *
   * @param  operation  The extended operation to be processed.
   */
  public void processExtendedOperation(ExtendedOperation operation)
  {
    assert debugEnter(CLASS_NAME, "processExtendedOperation",
                      String.valueOf(operation));

    // Initialize the variables associated with components that may be included
    // in the request.
    ASN1OctetString userIdentity = null;
    ASN1OctetString oldPassword  = null;
    ASN1OctetString newPassword  = null;


    // Parse the encoded request, if there is one.
    ByteString requestValue = operation.getRequestValue();
    if (requestValue != null)
    {
      try
      {
        ASN1Sequence requestSequence =
             ASN1Sequence.decodeAsSequence(requestValue.value());

        for (ASN1Element e : requestSequence.elements())
        {
          switch (e.getType())
          {
            case TYPE_PASSWORD_MODIFY_USER_ID:
              userIdentity = e.decodeAsOctetString();
              break;
            case TYPE_PASSWORD_MODIFY_OLD_PASSWORD:
              oldPassword = e.decodeAsOctetString();
              break;
            case TYPE_PASSWORD_MODIFY_NEW_PASSWORD:
              newPassword = e.decodeAsOctetString();
              break;
            default:
              operation.setResultCode(ResultCode.PROTOCOL_ERROR);

              int msgID = MSGID_EXTOP_PASSMOD_ILLEGAL_REQUEST_ELEMENT_TYPE;
              operation.appendErrorMessage(getMessage(msgID,
                                                      byteToHex(e.getType())));
              return;
          }
        }
      }
      catch (ASN1Exception ae)
      {
        assert debugException(CLASS_NAME, "processExtendedOperation", ae);

        operation.setResultCode(ResultCode.PROTOCOL_ERROR);

        int    msgID   = MSGID_EXTOP_PASSMOD_CANNOT_DECODE_REQUEST;
        String message = getMessage(msgID, stackTraceToSingleLineString(ae));
        operation.appendErrorMessage(message);

        return;
      }
    }


    // Get the DN of the user that issued the request.
    DN requestorDN = operation.getAuthorizationDN();


    // See if a user identity was provided.  If so, then try to resolve it to
    // an actual user.
    DN    userDN    = null;
    Entry userEntry = null;
    Lock  userLock  = null;

    try
    {
      if (userIdentity == null)
      {
        // This request must be targeted at changing the password for the
        // currently-authenticated user.  Make sure that the user actually is
        // authenticated.
        ClientConnection   clientConnection = operation.getClientConnection();
        AuthenticationInfo authInfo = clientConnection.getAuthenticationInfo();
        if ((! authInfo.isAuthenticated()) || (requestorDN == null) ||
            (requestorDN.isNullDN()))
        {
          operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

          int msgID = MSGID_EXTOP_PASSMOD_NO_AUTH_OR_USERID;
          operation.appendErrorMessage(getMessage(msgID));

          return;
        }


        // If the user is connected over an insecure channel, then determine
        // whether we should attempt to proceed.
        if (! clientConnection.isSecure())
        {
          // NYI
        }


        // Retrieve a write lock on that user's entry.
        userDN = requestorDN;

        for (int i=0; i < 3; i++)
        {
          userLock = LockManager.lockWrite(userDN);

          if (userLock != null)
          {
            break;
          }
        }

        if (userLock == null)
        {
          operation.setResultCode(DirectoryServer.getServerErrorResultCode());

          int    msgID   = MSGID_EXTOP_PASSMOD_CANNOT_LOCK_USER_ENTRY;
          String message = getMessage(msgID, String.valueOf(userDN));
          operation.appendErrorMessage(message);

          return;
        }


        userEntry = getEntryByDN(operation, userDN);
        if (userEntry == null)
        {
          return;
        }
      }
      else
      {
        // There was a userIdentity section in the request.  It should have
        // started with either "dn:" to indicate that it contained a DN, or
        // "u:" to indicate that it contained a user ID.
        String authzIDStr      = userIdentity.stringValue();
        String lowerAuthzIDStr = toLowerCase(authzIDStr);
        if (lowerAuthzIDStr.startsWith("dn:"))
        {
          try
          {
            userDN = DN.decode(authzIDStr.substring(3));
          }
          catch (DirectoryException de)
          {
            assert debugException(CLASS_NAME, "processExtendedOperation", de);

            operation.setResultCode(ResultCode.INVALID_DN_SYNTAX);

            int msgID = MSGID_EXTOP_PASSMOD_CANNOT_DECODE_AUTHZ_DN;
            operation.appendErrorMessage(getMessage(msgID, authzIDStr));

            return;
          }

          userEntry = getEntryByDN(operation, userDN);
          if (userEntry == null)
          {
            return;
          }
        }
        else if (lowerAuthzIDStr.startsWith("u:"))
        {
          userDN = getDNByUserID(operation, authzIDStr.substring(2));
          if (userDN == null)
          {
            return;
          }

          userEntry = getEntryByDN(operation, userDN);
          if (userEntry == null)
          {
            return;
          }
        }
        else
        {
          // The authorization ID was in an illegal format.
          operation.setResultCode(ResultCode.PROTOCOL_ERROR);

          int msgID = MSGID_EXTOP_PASSMOD_INVALID_AUTHZID_STRING;
          operation.appendErrorMessage(getMessage(msgID, authzIDStr));

          return;
        }
      }


      // At this point, we should have the user entry.  If a current password
      // was provided, then validate it.  If not, then see if that's OK.
      AttributeType pwType = DirectoryServer.getAttributeType("userpassword");
      if (pwType == null)
      {
        pwType = DirectoryServer.getDefaultAttributeType("userPassword");
      }

      if (oldPassword == null)
      {
        // NYI -- Confirm that this is allowed.
      }
      else
      {
        // FIXME -- Use a more generic check to determine the correct attribute
        List<Attribute> pwAttrList = userEntry.getAttribute(pwType);
        if ((pwAttrList == null) || pwAttrList.isEmpty())
        {
          // There were no existing passwords, so the validation will fail.
          operation.setResultCode(ResultCode.INVALID_CREDENTIALS);

          int msgID = MSGID_EXTOP_PASSMOD_INVALID_OLD_PASSWORD;
          operation.appendErrorMessage(getMessage(msgID));
          return;
        }

        AttributeValue matchValue = new AttributeValue(pwType, oldPassword);

        boolean matchFound = false;
        for (Attribute a : pwAttrList)
        {
          if (a.hasValue(matchValue))
          {
            matchFound = true;
            break;
          }
        }

        if (! matchFound)
        {
          // None of the password values matched what the user provided.
          operation.setResultCode(ResultCode.INVALID_CREDENTIALS);

          int msgID = MSGID_EXTOP_PASSMOD_INVALID_OLD_PASSWORD;
          operation.appendErrorMessage(getMessage(msgID));
          return;
        }
      }


      // See if a new password was provided.  If not, then generate one.
      boolean generatedPassword = false;
      if (newPassword == null)
      {
        // FIXME -- use an extensible algorithm for generating the new password.
        newPassword = new ASN1OctetString("newpassword");
        generatedPassword = true;
      }

      ArrayList<ASN1OctetString> newPWValues =
           new ArrayList<ASN1OctetString>(1);
      newPWValues.add(newPassword);



      // Create the modification to update the user's password.
      LDAPAttribute pwAttr = new LDAPAttribute("userPassword", newPWValues);
      ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>(1);
      mods.add(new LDAPModification(ModificationType.REPLACE, pwAttr));


      // Get an internal connection and use it to perform the modification.
      // FIXME -- Make a better determination here.
      AuthenticationInfo authInfo = new AuthenticationInfo(requestorDN, false);
      InternalClientConnection internalConnection = new
           InternalClientConnection(authInfo);

      ModifyOperation modifyOperation =
           internalConnection.processModify(
                new ASN1OctetString(userDN.toString()), mods);
      ResultCode resultCode = modifyOperation.getResultCode();
      if (resultCode != resultCode.SUCCESS)
      {
        operation.setResultCode(resultCode);
        operation.setErrorMessage(modifyOperation.getErrorMessage());
        operation.setReferralURLs(modifyOperation.getReferralURLs());
        return;
      }


      // If we've gotten here, then everything is OK, so indicate that the
      // operation was successful.  If a password was generated, then include
      // it in the response.
      operation.setResultCode(ResultCode.SUCCESS);

      if (generatedPassword)
      {
        ArrayList<ASN1Element> valueElements = new ArrayList<ASN1Element>(1);
        newPassword.setType(TYPE_PASSWORD_MODIFY_GENERATED_PASSWORD);
        valueElements.add(newPassword);

        ASN1Sequence valueSequence = new ASN1Sequence(valueElements);
        operation.setResponseValue(new ASN1OctetString(valueSequence.encode()));
      }
    }
    finally
    {
      if (userLock != null)
      {
        LockManager.unlock(userDN, userLock);
      }
    }
  }



  /**
   * Retrieves the entry for the specified user based on the provided DN.  If
   * any problem is encountered or the requested entry does not exist, then the
   * provided operation will be updated with appropriate result information and
   * this method will return <CODE>null</CODE>.  The caller must hold a write
   * lock on the specified entry.
   *
   * @param  operation  The extended operation being processed.
   * @param  entryDN    The DN of the user entry to retrieve.
   *
   * @return  The requested entry, or <CODE>null</CODE> if there was no such
   *          entry or it could not be retrieved.
   */
  private Entry getEntryByDN(ExtendedOperation operation, DN entryDN)
  {
    assert debugEnter(CLASS_NAME, "getEntryByDN", String.valueOf(operation),
                      String.valueOf(entryDN));

    // Retrieve the user's entry from the directory.  If it does not exist, then
    // fail.
    try
    {
      Entry userEntry = DirectoryServer.getEntry(entryDN);

      if (userEntry == null)
      {
        operation.setResultCode(ResultCode.NO_SUCH_OBJECT);

        int msgID = MSGID_EXTOP_PASSMOD_NO_USER_ENTRY_BY_AUTHZID;
        operation.appendErrorMessage(getMessage(msgID,
                                                String.valueOf(entryDN)));

        // See if one of the entry's ancestors exists.
        DN parentDN = entryDN.getParent();
        while (parentDN != null)
        {
          try
          {
            if (DirectoryServer.entryExists(parentDN))
            {
              operation.setMatchedDN(parentDN);
              break;
            }
          }
          catch (Exception e)
          {
            assert debugException(CLASS_NAME, "getEntryByDN", e);
            break;
          }

          parentDN = parentDN.getParent();
        }

        return null;
      }

      return userEntry;
    }
    catch (DirectoryException de)
    {
      assert debugException(CLASS_NAME, "getEntryByDN", de);

      operation.setResultCode(de.getResultCode());
      operation.appendErrorMessage(de.getErrorMessage());
      operation.setMatchedDN(de.getMatchedDN());
      operation.setReferralURLs(de.getReferralURLs());

      return null;
    }
  }



  /**
   * Retrieves the DN of the user with the provided user ID.  The DN will be
   * obtained by performing a subtree search with a base of the null DN (i.e.,
   * the root DSE) and therefore potentially searching across multiple backends.
   * If any problem is encountered or the requested entry does not exist, then
   * the provided operation will be updated with appropriate result information
   * and this method will return <CODE>null</CODE>.  The caller is not required
   * to hold any locks.
   *
   * @param  operation  The extended operation being processed.
   * @param  userID     The user ID for which to retrieve the DN.
   *
   * @return  The requested DN, or <CODE>null</CODE> if there was no such entry
   *          or if a problem was encountered.
   */
  private DN getDNByUserID(ExtendedOperation operation, String userID)
  {
    assert debugEnter(CLASS_NAME, "getDNByUserID", String.valueOf(operation),
                      String.valueOf(userID));

    InternalClientConnection internalConnection =
         InternalClientConnection.getRootConnection();

    LDAPFilter rawFilter =
         LDAPFilter.createEqualityFilter("uid", new ASN1OctetString(userID));

    InternalSearchOperation internalSearch =
         internalConnection.processSearch(new ASN1OctetString(),
                                          SearchScope.WHOLE_SUBTREE, rawFilter);

    ResultCode resultCode = internalSearch.getResultCode();
    if (resultCode != ResultCode.SUCCESS)
    {
      operation.setResultCode(resultCode);
      operation.setErrorMessage(internalSearch.getErrorMessage());
      operation.setMatchedDN(internalSearch.getMatchedDN());
      return null;
    }

    LinkedList<SearchResultEntry> entryList = internalSearch.getSearchEntries();
    if ((entryList == null) || entryList.isEmpty())
    {
      operation.setResultCode(ResultCode.NO_SUCH_OBJECT);

      int msgID = MSGID_EXTOP_PASSMOD_NO_DN_BY_AUTHZID;
      operation.appendErrorMessage(getMessage(msgID, String.valueOf(userID)));
      return null;
    }

    if (entryList.size() > 1)
    {
      operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);

      int msgID = MSGID_EXTOP_PASSMOD_MULTIPLE_ENTRIES_BY_AUTHZID;
      operation.appendErrorMessage(getMessage(msgID, String.valueOf(userID)));
      return null;
    }


    return entryList.get(0).getDN();
  }
}

