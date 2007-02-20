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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server;



import java.util.LinkedList;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

import org.opends.server.api.AccessLogger;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionSecurityProvider;
import org.opends.server.core.AbandonOperation;
import org.opends.server.core.AddOperation;
import org.opends.server.core.BindOperation;
import org.opends.server.core.CompareOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.Operation;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.UnbindOperation;
import org.opends.server.config.ConfigEntry;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.Control;
import org.opends.server.types.ByteString;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.DN;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;



/**
 * This class provides an implementation of an access logger which will store
 * all messages logged in memory.  It provides methods to retrieve and clear the
 * sets of accumulated log messages.  It is only intended for use in the context
 * of the unit test framework, where it will provide a means of getting any
 * access log messages associated with failed test cases.  Note that because it
 * is only intended for testing purposes, it may include information that might
 * otherwise be considered sensitive or too verbose to include in the log.
 */
public class TestAccessLogger
       extends AccessLogger
{
  // The list that will hold the messages logged.
  private final LinkedList<String> messageList;



  /**
   * The singleton instance of this test access logger.
   */
  private static final TestAccessLogger SINGLETON = new TestAccessLogger();



  /**
   * Creates a new instance of this test access logger.
   */
  private TestAccessLogger()
  {
    super();

    messageList = new LinkedList<String>();
  }



  /**
   * Retrieves the singleton instance of this test access logger.
   *
   * @return  The singleton instance of this test access logger.
   */
  public static TestAccessLogger getInstance()
  {
    return SINGLETON;
  }



  /**
   * Retrieves a copy of the set of messages logged to this error logger since
   * the last time it was cleared.  A copy of the list is returned to avoid
   * a ConcurrentModificationException.
   *
   * @return  The set of messages logged to this error logger since the last
   *          time it was cleared.
   */
  public static List<String> getMessages()
  {
    synchronized (SINGLETON) {
      return new ArrayList<String>(SINGLETON.messageList);
    }
  }



  /**
   * Clears any messages currently stored by this logger.
   */
  public static void clear()
  {
    synchronized (SINGLETON) {
      SINGLETON.messageList.clear();
    }
  }



  /**
   * {@inheritDoc}
   */
  public void initializeAccessLogger(ConfigEntry configEntry)
  {
    // No implementation is required.
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void closeAccessLogger()
  {
    messageList.clear();
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void logConnect(ClientConnection clientConnection)
  {
    StringBuilder buffer = new StringBuilder();

    buffer.append("CONNECT conn=");
    buffer.append(clientConnection.getConnectionID());
    buffer.append(" address=\"");
    buffer.append(clientConnection.getClientAddress());
    buffer.append("\" connhandler=\"");
    buffer.append(
         clientConnection.getConnectionHandler().getConnectionHandlerName());
    buffer.append("\" security=\"");

    ConnectionSecurityProvider securityProvider =
         clientConnection.getConnectionSecurityProvider();
    if (securityProvider == null)
    {
      buffer.append("none\"");
    }
    else
    {
      buffer.append(securityProvider.getSecurityMechanismName());
      buffer.append("\"");
    }

    messageList.add(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void logDisconnect(ClientConnection clientConnection,
                                         DisconnectReason disconnectReason,
                                         String message)
  {
    StringBuilder buffer = new StringBuilder();

    buffer.append("DISCONNECT conn=");
    buffer.append(clientConnection.getConnectionID());
    buffer.append(" reason=\"");
    buffer.append(disconnectReason);
    buffer.append("\" message=\"");

    if (message != null)
    {
      buffer.append(message);
    }

    buffer.append("\"");

    messageList.add(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void logAbandonRequest(AbandonOperation abandonOperation)
  {
    StringBuilder buffer = new StringBuilder();

    addCommonRequestElements(abandonOperation, buffer);
    buffer.append(" targetMsgID=");
    buffer.append(abandonOperation.getIDToAbandon());
    addRequestControls(abandonOperation, buffer);

    messageList.add(buffer.toString());
  }


  /**
   * {@inheritDoc}
   */
  public synchronized void logAbandonResult(AbandonOperation abandonOperation)
  {
    StringBuilder buffer = new StringBuilder();

    addCommonResultElements(abandonOperation, buffer);
    addResponseControls(abandonOperation, buffer);

    messageList.add(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void logAddRequest(AddOperation addOperation)
  {
    StringBuilder buffer = new StringBuilder();

    addCommonRequestElements(addOperation, buffer);
    buffer.append(" dn=\"");
    buffer.append(addOperation.getRawEntryDN());
    buffer.append("\" attibutes={");

    Iterator<LDAPAttribute> attrIterator =
         addOperation.getRawAttributes().iterator();
    if (attrIterator.hasNext())
    {
      LDAPAttribute attr = attrIterator.next();
      buffer.append(attr.getAttributeType());
      buffer.append("={\"");

      Iterator<ASN1OctetString> valueIterator = attr.getValues().iterator();
      if (valueIterator.hasNext())
      {
        buffer.append(valueIterator.next().stringValue());
        while (valueIterator.hasNext())
        {
          buffer.append("\",\"");
          buffer.append(valueIterator.next().stringValue());
        }
      }
      buffer.append("\"}");

      while (attrIterator.hasNext())
      {
        buffer.append(",");
        attr = attrIterator.next();
        buffer.append(attr.getAttributeType());
        buffer.append("={\"");

        valueIterator = attr.getValues().iterator();
        if (valueIterator.hasNext())
        {
          buffer.append(valueIterator.next().stringValue());
          while (valueIterator.hasNext())
          {
            buffer.append("\",\"");
            buffer.append(valueIterator.next().stringValue());
          }
        }
        buffer.append("\"}");
      }
    }

    buffer.append("}");
    addRequestControls(addOperation, buffer);

    messageList.add(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void logAddResponse(AddOperation addOperation)
  {
    StringBuilder buffer = new StringBuilder();

    addCommonResultElements(addOperation, buffer);
    addResponseControls(addOperation, buffer);

    messageList.add(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void logBindRequest(BindOperation bindOperation)
  {
    StringBuilder buffer = new StringBuilder();

    addCommonRequestElements(bindOperation, buffer);

    switch (bindOperation.getAuthenticationType())
    {
      case SIMPLE:
        buffer.append(" type=\"SIMPLE\" dn=\"");
        buffer.append(bindOperation.getRawBindDN().stringValue());
        buffer.append("\" password=\"");
        buffer.append(bindOperation.getSimplePassword());
        buffer.append("\"");
        break;
      case SASL:
        buffer.append(" type=\"SASL\" mechanism=\"");
        buffer.append(bindOperation.getSASLMechanism());
        buffer.append("\" dn=\"");
        buffer.append(bindOperation.getRawBindDN().stringValue());
        buffer.append("\"");
        break;
      default:
        buffer.append(" type=\"");
        buffer.append(bindOperation.getAuthenticationType());
        buffer.append("\"");
        break;
    }

    addRequestControls(bindOperation, buffer);

    messageList.add(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void logBindResponse(BindOperation bindOperation)
  {
    StringBuilder buffer = new StringBuilder();

    addCommonResultElements(bindOperation, buffer);

    String authFailureReason = bindOperation.getAuthFailureReason();
    if (authFailureReason != null)
    {
      buffer.append(" authFailureReason=\"");
      buffer.append(authFailureReason);
      buffer.append("\"");
    }

    DN authDN = bindOperation.getClientConnection().getAuthenticationInfo().
                     getAuthenticationDN();
    if (authDN == null)
    {
      buffer.append(" authDN=\"\"");
    }
    else
    {
      buffer.append(" authDN=\"");
      buffer.append(authDN);
      buffer.append("\"");
    }

    addResponseControls(bindOperation, buffer);

    messageList.add(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void logCompareRequest(CompareOperation compareOperation)
  {
    StringBuilder buffer = new StringBuilder();

    addCommonRequestElements(compareOperation, buffer);

    buffer.append(" dn=\"");
    buffer.append(compareOperation.getRawEntryDN().stringValue());
    buffer.append("\" attributeType=\"");
    buffer.append(compareOperation.getRawAttributeType());
    buffer.append("\" assertionValue=\"");
    buffer.append(compareOperation.getAssertionValue().stringValue());
    buffer.append("\"");

    addRequestControls(compareOperation, buffer);

    messageList.add(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void logCompareResponse(CompareOperation
                                                   compareOperation)
  {
    StringBuilder buffer = new StringBuilder();

    addCommonResultElements(compareOperation, buffer);
    addResponseControls(compareOperation, buffer);

    messageList.add(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void logDeleteRequest(DeleteOperation deleteOperation)
  {
    StringBuilder buffer = new StringBuilder();

    addCommonRequestElements(deleteOperation, buffer);
    buffer.append(" dn=\"");
    buffer.append(deleteOperation.getRawEntryDN().stringValue());
    buffer.append("\"");
    addRequestControls(deleteOperation, buffer);

    messageList.add(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void logDeleteResponse(DeleteOperation deleteOperation)
  {
    StringBuilder buffer = new StringBuilder();

    addCommonResultElements(deleteOperation, buffer);
    addResponseControls(deleteOperation, buffer);

    messageList.add(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void logExtendedRequest(ExtendedOperation
                                                   extendedOperation)
  {
    StringBuilder buffer = new StringBuilder();

    addCommonRequestElements(extendedOperation, buffer);
    buffer.append(" requestOID=\"");
    buffer.append(extendedOperation.getRequestOID());
    buffer.append("\"");
    addRequestControls(extendedOperation, buffer);

    messageList.add(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void logExtendedResponse(ExtendedOperation
                                                    extendedOperation)
  {
    StringBuilder buffer = new StringBuilder();

    addCommonResultElements(extendedOperation, buffer);

    String responseOID = extendedOperation.getResponseOID();
    if (responseOID == null)
    {
      buffer.append(" responseOID=\"\"");
    }
    else
    {
      buffer.append(" responseOID=\"");
      buffer.append(responseOID);
      buffer.append("\"");
    }

    addResponseControls(extendedOperation, buffer);

    messageList.add(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void logModifyRequest(ModifyOperation modifyOperation)
  {
    StringBuilder buffer = new StringBuilder();

    addCommonRequestElements(modifyOperation, buffer);

    buffer.append(" dn=\"");
    buffer.append(modifyOperation.getRawEntryDN().stringValue());
    buffer.append("\" mods={");

    Iterator<LDAPModification> modIterator =
         modifyOperation.getRawModifications().iterator();
    if (modIterator.hasNext())
    {
      LDAPModification mod = modIterator.next();
      buffer.append(mod.getModificationType().toString());
      buffer.append(" attribute=");
      buffer.append(mod.getAttribute().getAttributeType());
      buffer.append(" values={");

      Iterator<ASN1OctetString> valueIterator =
           mod.getAttribute().getValues().iterator();
      if (valueIterator.hasNext())
      {
        buffer.append("\"");
        buffer.append(valueIterator.next().stringValue());

        while(valueIterator.hasNext())
        {
          buffer.append("\",\"");
          buffer.append(valueIterator.next().stringValue());
        }

        buffer.append("\"");
      }

      while (modIterator.hasNext())
      {
        mod = modIterator.next();
        buffer.append(mod.getModificationType().toString());
        buffer.append(" attribute=");
        buffer.append(mod.getAttribute().getAttributeType());
        buffer.append(" values={");

        valueIterator = mod.getAttribute().getValues().iterator();
        if (valueIterator.hasNext())
        {
          buffer.append("\"");
          buffer.append(valueIterator.next().stringValue());

          while(valueIterator.hasNext())
          {
            buffer.append("\",\"");
            buffer.append(valueIterator.next().stringValue());
          }

          buffer.append("\"");
        }
      }
    }

    buffer.append("}");

    addRequestControls(modifyOperation, buffer);

    messageList.add(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void logModifyResponse(ModifyOperation modifyOperation)
  {
    StringBuilder buffer = new StringBuilder();

    addCommonResultElements(modifyOperation, buffer);
    addResponseControls(modifyOperation, buffer);

    messageList.add(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void logModifyDNRequest(ModifyDNOperation
                                                   modifyDNOperation)
  {
    StringBuilder buffer = new StringBuilder();

    addCommonRequestElements(modifyDNOperation, buffer);

    buffer.append(" dn=\"");
    buffer.append(modifyDNOperation.getRawEntryDN().stringValue());
    buffer.append("\" newRDN=\"");
    buffer.append(modifyDNOperation.getRawNewRDN().stringValue());
    buffer.append("\" deleteOldRDN=");
    buffer.append(modifyDNOperation.deleteOldRDN());

    ByteString newSuperior = modifyDNOperation.getRawNewSuperior();
    if (newSuperior == null)
    {
      buffer.append(" newSuperior=\"\"");
    }
    else
    {
      buffer.append(" newSuperior=\"");
      buffer.append(newSuperior.stringValue());
      buffer.append("\"");
    }

    addRequestControls(modifyDNOperation, buffer);

    messageList.add(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void logModifyDNResponse(ModifyDNOperation
                                                    modifyDNOperation)
  {
    StringBuilder buffer = new StringBuilder();

    addCommonResultElements(modifyDNOperation, buffer);
    addResponseControls(modifyDNOperation, buffer);

    messageList.add(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void logSearchRequest(SearchOperation searchOperation)
  {
    StringBuilder buffer = new StringBuilder();

    addCommonRequestElements(searchOperation, buffer);

    buffer.append(" baseDN=\"");
    buffer.append(searchOperation.getRawBaseDN().stringValue());
    buffer.append("\" scope=");
    buffer.append(searchOperation.getScope());
    buffer.append(" derefAliases=");
    buffer.append(searchOperation.getDerefPolicy());
    buffer.append(" sizeLimit=");
    buffer.append(searchOperation.getSizeLimit());
    buffer.append(" timeLimit=");
    buffer.append(searchOperation.getTimeLimit());
    buffer.append(" typesOnly=");
    buffer.append(searchOperation.getTypesOnly());
    buffer.append(" filter=\"");
    buffer.append(searchOperation.getRawFilter().toString());
    buffer.append("\" attrs={");

    Iterator<String> iterator = searchOperation.getAttributes().iterator();
    if (iterator.hasNext())
    {
      buffer.append(iterator.next());

      while (iterator.hasNext())
      {
        buffer.append(",");
        buffer.append(iterator.next());
      }
    }

    buffer.append("}");

    addRequestControls(searchOperation, buffer);

    messageList.add(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void logSearchResultEntry(SearchOperation searchOperation,
                                                SearchResultEntry searchEntry)
  {
    StringBuilder buffer = new StringBuilder();

    buffer.append("SEARCH ENTRY conn=");
    buffer.append(searchOperation.getConnectionID());
    buffer.append(" op=");
    buffer.append(searchOperation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(searchOperation.getMessageID());
    buffer.append(" dn=\"");
    buffer.append(searchEntry.getDN());
    buffer.append("\" userAttributes={");

    Iterator<List<Attribute>> attrListIterator =
         searchEntry.getUserAttributes().values().iterator();
    if (attrListIterator.hasNext())
    {
      List<Attribute> attrList = attrListIterator.next();
      Iterator<Attribute> attrIterator = attrList.iterator();
      if (attrIterator.hasNext())
      {
        Attribute attr = attrIterator.next();
        buffer.append(attr.getName());
        buffer.append(" values={");

        Iterator<AttributeValue> valueIterator = attr.getValues().iterator();
        if (valueIterator.hasNext())
        {
          buffer.append("\"");
          buffer.append(valueIterator.next().getStringValue());

          while (valueIterator.hasNext())
          {
            buffer.append("\",\"");
            buffer.append(valueIterator.next().getStringValue());
          }

          buffer.append("\"");
        }

        buffer.append("}");
      }

      while (attrListIterator.hasNext())
      {
        attrList = attrListIterator.next();
        attrIterator = attrList.iterator();
        if (attrIterator.hasNext())
        {
          Attribute attr = attrIterator.next();
          buffer.append(attr.getName());
          buffer.append(" values={");

          Iterator<AttributeValue> valueIterator = attr.getValues().iterator();
          if (valueIterator.hasNext())
          {
            buffer.append("\"");
            buffer.append(valueIterator.next().getStringValue());

            while (valueIterator.hasNext())
            {
              buffer.append("\",\"");
              buffer.append(valueIterator.next().getStringValue());
            }

            buffer.append("\"");
          }

          buffer.append("}");
        }
      }
    }

    buffer.append("}");
    buffer.append("\" operationalAttributes={");

    attrListIterator =
         searchEntry.getOperationalAttributes().values().iterator();
    if (attrListIterator.hasNext())
    {
      List<Attribute> attrList = attrListIterator.next();
      Iterator<Attribute> attrIterator = attrList.iterator();
      if (attrIterator.hasNext())
      {
        Attribute attr = attrIterator.next();
        buffer.append(attr.getName());
        buffer.append(" values={");

        Iterator<AttributeValue> valueIterator = attr.getValues().iterator();
        if (valueIterator.hasNext())
        {
          buffer.append("\"");
          buffer.append(valueIterator.next().getStringValue());

          while (valueIterator.hasNext())
          {
            buffer.append("\",\"");
            buffer.append(valueIterator.next().getStringValue());
          }

          buffer.append("\"");
        }

        buffer.append("}");
      }

      while (attrListIterator.hasNext())
      {
        attrList = attrListIterator.next();
        attrIterator = attrList.iterator();
        if (attrIterator.hasNext())
        {
          Attribute attr = attrIterator.next();
          buffer.append(attr.getName());
          buffer.append(" values={");

          Iterator<AttributeValue> valueIterator = attr.getValues().iterator();
          if (valueIterator.hasNext())
          {
            buffer.append("\"");
            buffer.append(valueIterator.next().getStringValue());

            while (valueIterator.hasNext())
            {
              buffer.append("\",\"");
              buffer.append(valueIterator.next().getStringValue());
            }

            buffer.append("\"");
          }

          buffer.append("}");
        }
      }
    }

    buffer.append("}");

    List<Control> controls = searchEntry.getControls();
    if ((controls != null) && (! controls.isEmpty()))
    {
      buffer.append(" controls={");

      Iterator<Control> iterator = controls.iterator();

      Control c = iterator.next();
      buffer.append(c.getOID());
      buffer.append(":");
      buffer.append(c.isCritical());

      while (iterator.hasNext())
      {
        buffer.append(",");
        c = iterator.next();
        buffer.append(c.getOID());
        buffer.append(":");
        buffer.append(c.isCritical());
      }

      buffer.append("}");
    }

    messageList.add(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void logSearchResultReference(
                                SearchOperation searchOperation,
                                SearchResultReference searchReference)
  {
    StringBuilder buffer = new StringBuilder();

    buffer.append("SEARCH REFERENCE conn=");
    buffer.append(searchOperation.getConnectionID());
    buffer.append(" op=");
    buffer.append(searchOperation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(searchOperation.getMessageID());
    buffer.append(" referralURLs={");

    if (searchReference.getReferralURLs() != null)
    {
      Iterator<String> iterator = searchReference.getReferralURLs().iterator();
      if (iterator.hasNext())
      {
        buffer.append("\"");
        buffer.append(iterator.next());

        while (iterator.hasNext())
        {
          buffer.append("\",\"");
          buffer.append(iterator.next());
        }

        buffer.append("\"");
      }
    }

    buffer.append("}");

    List<Control> controls = searchReference.getControls();
    if ((controls != null) && (! controls.isEmpty()))
    {
      buffer.append(" controls={");

      Iterator<Control> iterator = controls.iterator();

      Control c = iterator.next();
      buffer.append(c.getOID());
      buffer.append(":");
      buffer.append(c.isCritical());

      while (iterator.hasNext())
      {
        buffer.append(",");
        c = iterator.next();
        buffer.append(c.getOID());
        buffer.append(":");
        buffer.append(c.isCritical());
      }

      buffer.append("}");
    }

    messageList.add(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void logSearchResultDone(SearchOperation searchOperation)
  {
    StringBuilder buffer = new StringBuilder();

    addCommonResultElements(searchOperation, buffer);
    buffer.append(" numEntries=");
    buffer.append(searchOperation.getEntriesSent());
    buffer.append(" numReferences=");
    buffer.append(searchOperation.getReferencesSent());
    addResponseControls(searchOperation, buffer);

    messageList.add(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void logUnbind(UnbindOperation unbindOperation)
  {
    StringBuilder buffer = new StringBuilder();

    addCommonRequestElements(unbindOperation, buffer);
    addRequestControls(unbindOperation, buffer);

    messageList.add(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  public boolean equals(Object o)
  {
    return (this == o);
  }



  /**
   * {@inheritDoc}
   */
  public int hashCode()
  {
    return 1;
  }



  /**
   * Adds a set of information common to all types of operation requests to the
   * provided buffer.
   *
   * @param  operation  The operation from which to obtain the information.
   * @param  buffer     The buffer to which the information is to be written.
   */
  private void addCommonRequestElements(Operation operation,
                                        StringBuilder buffer)
  {
    buffer.append(operation.getOperationType().getOperationName());
    buffer.append(" REQUEST conn=");
    buffer.append(operation.getConnectionID());
    buffer.append(" op=");
    buffer.append(operation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(operation.getMessageID());
  }



  /**
   * Adds a set of information common to all types of operation results to the
   * provided buffer.
   *
   * @param  operation  The operation from which to obtain the information.
   * @param  buffer     The buffer to which the information is to be written.
   */
  private void addCommonResultElements(Operation operation,
                                       StringBuilder buffer)
  {
    buffer.append(operation.getOperationType().getOperationName());
    buffer.append(" RESULT conn=");
    buffer.append(operation.getConnectionID());
    buffer.append(" op=");
    buffer.append(operation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(operation.getMessageID());
    buffer.append(" resultCode=\"");
    buffer.append(operation.getResultCode());
    buffer.append("\" message=\"");
    buffer.append(operation.getErrorMessage().toString());
    buffer.append("\" matchedDN=\"");

    DN matchedDN = operation.getMatchedDN();
    if (matchedDN != null)
    {
      buffer.append(matchedDN);
    }
    buffer.append("\" referralURLs={");

    if (operation.getReferralURLs() != null)
    {
      Iterator<String> iterator = operation.getReferralURLs().iterator();
      if (iterator.hasNext())
      {
        buffer.append("\"");
        buffer.append(iterator.next());

        while (iterator.hasNext())
        {
          buffer.append("\",\"");
          buffer.append(iterator.next());
        }

        buffer.append("\"");
      }
    }

    StringBuilder additionalLogMessage = operation.getAdditionalLogMessage();
    if (additionalLogMessage.length() > 0)
    {
      buffer.append(" additionalLogMessage\"");
      buffer.append(additionalLogMessage.toString());
      buffer.append("\"");
    }

    buffer.append("}");
  }



  /**
   * Adds information about the request controls (if any) for the operation into
   * the provided buffer.
   *
   * @param  operation  The operation from which to obtain the information.
   * @param  buffer     The buffer to which the information is to be written.
   */
  private void addRequestControls(Operation operation, StringBuilder buffer)
  {
    List<Control> controls = operation.getRequestControls();
    if ((controls != null) && (! controls.isEmpty()))
    {
      buffer.append(" controls={");

      Iterator<Control> iterator = controls.iterator();

      Control c = iterator.next();
      buffer.append(c.getOID());
      buffer.append(":");
      buffer.append(c.isCritical());

      while (iterator.hasNext())
      {
        buffer.append(",");
        c = iterator.next();
        buffer.append(c.getOID());
        buffer.append(":");
        buffer.append(c.isCritical());
      }

      buffer.append("}");
    }
  }



  /**
   * Adds information about the response controls (if any) for the operation
   * into the provided buffer.
   *
   * @param  operation  The operation from which to obtain the information.
   * @param  buffer     The buffer to which the information is to be written.
   */
  private void addResponseControls(Operation operation, StringBuilder buffer)
  {
    List<Control> controls = operation.getResponseControls();
    if ((controls != null) && (! controls.isEmpty()))
    {
      buffer.append(" controls={");

      Iterator<Control> iterator = controls.iterator();

      Control c = iterator.next();
      buffer.append(c.getOID());
      buffer.append(":");
      buffer.append(c.isCritical());

      while (iterator.hasNext())
      {
        buffer.append(",");
        c = iterator.next();
        buffer.append(c.getOID());
        buffer.append(":");
        buffer.append(c.isCritical());
      }

      buffer.append("}");
    }
  }
}

