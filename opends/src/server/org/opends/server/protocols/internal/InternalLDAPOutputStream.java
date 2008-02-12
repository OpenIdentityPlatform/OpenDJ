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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.internal;



import java.io.OutputStream;
import java.io.IOException;
import java.util.ArrayList;

import org.opends.messages.Message;
import org.opends.server.core.*;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.ldap.*;
import org.opends.server.types.AuthenticationType;
import org.opends.server.types.Control;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class provides an implementation of a
 * {@code java.io.OutputStream} that can be used to facilitate
 * internal communication with the Directory Server.  On the backend,
 * data written to this output stream will be first decoded as an
 * ASN.1 element and then as an LDAP message.  That LDAP message will
 * be converted to an internal operation which will then be processed
 * and the result returned to the client via the input stream on the
 * other side of the associated internal LDAP socket.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class InternalLDAPOutputStream
       extends OutputStream
       implements InternalSearchListener
{
  // Indicates whether this stream has been closed.
  private boolean closed;

  // Indicates whether the type of the ASN.1 element is needed.
  private boolean needType;

  // The BER type for the ASN.1 element being read.
  private byte elementType;

  // The data for the ASN.1 element being read.
  private byte[] elementBytes;

  // The length bytes for the ASN.1 element being read.
  private byte[] lengthBytes;

  // The offset in the appropriate array at which we should begin
  // writing data.  This could either refer to the length or data
  // array, depending on the stage of the encoding process.
  private int arrayOffset;

  // The internal LDAP socket with which this output stream is
  // associated.
  private InternalLDAPSocket socket;



  /**
   * Creates a new instance of an internal LDAP output stream that is
   * associated with the provided internal LDAP socket.
   *
   * @param  socket  The internal LDAP socket that will be serviced by
   *                 this internal LDAP output stream.
   */
  public InternalLDAPOutputStream(InternalLDAPSocket socket)
  {
    this.socket = socket;

    closed = false;

    needType = true;
    elementType = 0x00;
    elementBytes = null;
    lengthBytes = null;
    arrayOffset = 0;
  }



  /**
   * Closes this output stream, its associated socket, and the
   * socket's associated input stream.
   */
  @Override()
  public void close()
  {
    socket.close();
  }



  /**
   * Closes this output stream through an internal mechanism that will
   * not cause an infinite recursion loop by trying to also close the
   * output stream.
   */
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  void closeInternal()
  {
    closed = true;
  }



  /**
   * Flushes this output stream and forces any buffered data to be
   * written out.  This will have no effect, since this output
   * stream implementation does not use buffering.
   */
  @Override()
  public void flush()
  {
    // No implementation is required.
  }



  /**
   * Writes the contents of the provided byte array to this output
   * stream.
   *
   * @param  b  The byte array to be written.
   *
   * @throws  IOException  If the output stream is closed, or if there
   *                       is a problem with the data being written.
   */
  @Override()
  public void write(byte[] b)
         throws IOException
  {
    write(b, 0, b.length);
  }



  /**
   * Writes the specified portion of the data in the provided byte
   * array to this output stream.  Any data written will be
   * accumulated until a complete ASN.1 element is available, at which
   * point it will be decoded as an LDAP message and converted to an
   * internal operation that will be processed.
   *
   * @param  b    The byte array containing the data to be read.
   * @param  off  The position in the array at which to start reading
   *              data.
   * @param  len  The number of bytes to read from the array.
   *
   * @throws  IOException  If the output stream is closed, or if there
   *                       is a problem with the data being written.
   */
  @Override()
  public synchronized void write(byte[] b, int off, int len)
         throws IOException
  {
    if (closed)
    {
      Message m = ERR_INTERNALOS_CLOSED.get();
      throw new IOException(m.toString());
    }

    if (len == 0)
    {
      return;
    }


    // See if we need to read the BER type.
    int position  = off;
    int remaining = len;
    if (needType)
    {
      elementType = b[position++];
      needType = false;

      if (--remaining <= 0)
      {
        return;
      }
    }


    // See if we need to read the first length byte.
    if ((lengthBytes == null) && (elementBytes == null))
    {
      int length = b[position++];
      if (length == (length & 0x7F))
      {
        // It's a single-byte length, so we can create the value
        // array.
        elementBytes = new byte[length];
      }
      else
      {
        // It's a multi-byte length, so we can create the length
        // array.
        lengthBytes = new byte[length & 0x7F];
      }

      arrayOffset = 0;
      if (--remaining <= 0)
      {
        return;
      }
    }


    // See if we need to continue reading part of a multi-byte length.
    if (lengthBytes != null)
    {
      // See if we have enough to read the full length.  If so, then
      // do it.  Otherwise, read what we can and return.
      int needed = lengthBytes.length - arrayOffset;
      if (remaining >= needed)
      {
        System.arraycopy(b, position, lengthBytes, arrayOffset,
                         needed);
        position += needed;
        remaining -= needed;

        int length = 0;
        for (byte lb : lengthBytes)
        {
          length <<= 8;
          length |= (lb & 0xFF);
        }

        elementBytes = new byte[length];
        lengthBytes = null;
        arrayOffset = 0;
        if (remaining <= 0)
        {
          return;
        }
      }
      else
      {
        System.arraycopy(b, position, lengthBytes, arrayOffset,
                         remaining);
        arrayOffset += remaining;
        return;
      }
    }


    // See if we need to read data for the element value.
    if (elementBytes != null)
    {
      // See if we have enough to read the full value.  If so, then
      // do it, create the element, and process it.  Otherwise, read
      // what we can and return.
      int needed = elementBytes.length - arrayOffset;
      if (remaining >= needed)
      {
        System.arraycopy(b, position, elementBytes, arrayOffset,
                         needed);
        position += needed;
        remaining -= needed;
        processElement(new ASN1Element(elementType, elementBytes));

        needType     = true;
        arrayOffset  = 0;
        lengthBytes  = null;
        elementBytes = null;
      }
      else
      {
        System.arraycopy(b, position, lengthBytes, arrayOffset,
                         remaining);
        arrayOffset += remaining;
        return;
      }
    }


    // If there is still more data available, then call this method
    // again to process it.
    if (remaining > 0)
    {
      write(b, position, remaining);
    }
  }



  /**
   * Writes a single byte of data to this output stream.  If the byte
   * written completes an ASN.1 element that was in progress, then it
   * will be decoded as an LDAP message and converted to an internal
   * operation that will be processed.  Otherwise, the data will be
   * accumulated until a complete element can be formed.
   *
   * @param  b The byte to be written.
   *
   * @throws  IOException  If the output stream is closed, or if there
   *                       is a problem with the data being written.
   */
  @Override()
  public synchronized void write(int b)
         throws IOException
  {
    if (closed)
    {
      Message m = ERR_INTERNALOS_CLOSED.get();
      throw new IOException(m.toString());
    }

    if (needType)
    {
      elementType = (byte) (b & 0xFF);
      needType = false;
      return;
    }
    else if (elementBytes != null)
    {
      // The byte should be part of the element value.
      elementBytes[arrayOffset++] = (byte) (b & 0xFF);
      if (arrayOffset == elementBytes.length)
      {
        // The element has been completed, so process it.
        processElement(new ASN1Element(elementType, elementBytes));
      }

      lengthBytes  = null;
      elementBytes = null;
      arrayOffset  = 0;
      needType     = true;

      return;
    }
    else if (lengthBytes != null)
    {
      // The byte should be part of a multi-byte length.
      lengthBytes[arrayOffset++] = (byte) (b & 0xFF);
      if (arrayOffset == lengthBytes.length)
      {
        int length = 0;
        for (int i=0; i < lengthBytes.length; i++)
        {
          length <<= 8;
          length |= (lengthBytes[i] & 0xFF);
        }

        elementBytes = new byte[length];
        lengthBytes  = null;
        arrayOffset   = 0;
      }

      return;
    }
    else
    {
      if ((b & 0x7F) == b)
      {
        // It's the complete length.
        elementBytes = new byte[b];
        lengthBytes  = null;
        arrayOffset = 0;
      }
      else
      {
        lengthBytes  = new byte[b & 0x7F];
        elementBytes = null;
        arrayOffset  = 0;
      }

      return;
    }
  }



  /**
   * Processes the provided ASN.1 element by decoding it as an LDAP
   * message, converting that to an internal operation, and sending
   * the appropriate response message(s) to the client through the
   * corresponding internal LDAP input stream.
   *
   * @param  element  The ASN.1 element to be processed.
   *
   * @throws  IOException  If a problem occurs while attempting to
   *                       decode the provided ASN.1 element as an
   *                       LDAP message.
   */
  private void processElement(ASN1Element element)
          throws IOException
  {
    LDAPMessage message;
    try
    {
      message = LDAPMessage.decode(element.decodeAsSequence());
    }
    catch (Exception e)
    {
      throw new IOException(e.getMessage());
    }

    switch (message.getProtocolOpType())
    {
      case OP_TYPE_ABANDON_REQUEST:
        // No action is required.
        return;

      case OP_TYPE_ADD_REQUEST:
        processAddOperation(message);
        break;

      case OP_TYPE_BIND_REQUEST:
        processBindOperation(message);
        break;

      case OP_TYPE_COMPARE_REQUEST:
        processCompareOperation(message);
        break;


      case OP_TYPE_DELETE_REQUEST:
        processDeleteOperation(message);
        break;


      case OP_TYPE_EXTENDED_REQUEST:
        processExtendedOperation(message);
        break;


      case OP_TYPE_MODIFY_REQUEST:
        processModifyOperation(message);
        break;


      case OP_TYPE_MODIFY_DN_REQUEST:
        processModifyDNOperation(message);
        break;


      case OP_TYPE_SEARCH_REQUEST:
        processSearchOperation(message);
        break;


      case OP_TYPE_UNBIND_REQUEST:
        socket.close();
        break;


      default:
        Message m = ERR_INTERNALOS_INVALID_REQUEST.get(
                         message.getProtocolElementName());
        throw new IOException(m.toString());
    }
  }



  /**
   * Processes the content of the provided LDAP message as an add
   * operation and returns the appropriate result to the client.
   *
   * @param  message  The LDAP message containing the request to
   *                  process.
   *
   * @throws  IOException  If a problem occurs while attempting to
   *                       process the operation.
   */
  private void processAddOperation(LDAPMessage message)
          throws IOException
  {
    int messageID = message.getMessageID();
    AddRequestProtocolOp request = message.getAddRequestProtocolOp();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    if (message.getControls() != null)
    {
      for (LDAPControl c : message.getControls())
      {
        requestControls.add(c.getControl());
      }
    }

    InternalClientConnection conn = socket.getConnection();
    AddOperationBasis op =
         new AddOperationBasis(conn, conn.nextOperationID(),
                               messageID, requestControls,
                               request.getDN(),
                               request.getAttributes());
    op.run();

    AddResponseProtocolOp addResponse =
         new AddResponseProtocolOp(op.getResultCode().getIntValue(),
                                   op.getErrorMessage().toMessage(),
                                   op.getMatchedDN(),
                                   op.getReferralURLs());
    ArrayList<LDAPControl> responseControls =
         new ArrayList<LDAPControl>();
    for (Control c : op.getResponseControls())
    {
      responseControls.add(new LDAPControl(c));
    }

    socket.getInputStream().addLDAPMessage(
         new LDAPMessage(messageID, addResponse, responseControls));
  }



  /**
   * Processes the content of the provided LDAP message as a bind
   * operation and returns the appropriate result to the client.
   *
   * @param  message  The LDAP message containing the request to
   *                  process.
   *
   * @throws  IOException  If a problem occurs while attempting to
   *                       process the operation.
   */
  private void processBindOperation(LDAPMessage message)
          throws IOException
  {
    int messageID = message.getMessageID();
    BindRequestProtocolOp request =
         message.getBindRequestProtocolOp();

    if (request.getAuthenticationType() == AuthenticationType.SASL)
    {
      Message m = ERR_INTERNALOS_SASL_BIND_NOT_SUPPORTED.get();
      BindResponseProtocolOp bindResponse =
           new BindResponseProtocolOp(
                    LDAPResultCode.UNWILLING_TO_PERFORM, m);
      socket.getInputStream().addLDAPMessage(
           new LDAPMessage(messageID, bindResponse));
      return;
    }

    ArrayList<Control> requestControls = new ArrayList<Control>();
    if (message.getControls() != null)
    {
      for (LDAPControl c : message.getControls())
      {
        requestControls.add(c.getControl());
      }
    }

    InternalClientConnection conn = socket.getConnection();
    BindOperationBasis op =
         new BindOperationBasis(conn, conn.nextOperationID(),
                  messageID, requestControls,
                  String.valueOf(request.getProtocolVersion()),
                  request.getDN(), request.getSimplePassword());
    op.run();

    BindResponseProtocolOp bindResponse =
         new BindResponseProtocolOp(op.getResultCode().getIntValue(),
                                    op.getErrorMessage().toMessage(),
                                    op.getMatchedDN(),
                                    op.getReferralURLs());
    ArrayList<LDAPControl> responseControls =
         new ArrayList<LDAPControl>();
    for (Control c : op.getResponseControls())
    {
      responseControls.add(new LDAPControl(c));
    }

    if (bindResponse.getResultCode() == LDAPResultCode.SUCCESS)
    {
      socket.setConnection(new InternalClientConnection(
           op.getAuthenticationInfo()));
    }

    socket.getInputStream().addLDAPMessage(
         new LDAPMessage(messageID, bindResponse, responseControls));
  }



  /**
   * Processes the content of the provided LDAP message as a compare
   * operation and returns the appropriate result to the client.
   *
   * @param  message  The LDAP message containing the request to
   *                  process.
   *
   * @throws  IOException  If a problem occurs while attempting to
   *                       process the operation.
   */
  private void processCompareOperation(LDAPMessage message)
          throws IOException
  {
    int messageID = message.getMessageID();
    CompareRequestProtocolOp request =
         message.getCompareRequestProtocolOp();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    if (message.getControls() != null)
    {
      for (LDAPControl c : message.getControls())
      {
        requestControls.add(c.getControl());
      }
    }

    InternalClientConnection conn = socket.getConnection();
    CompareOperationBasis op =
         new CompareOperationBasis(conn, conn.nextOperationID(),
                  messageID, requestControls, request.getDN(),
                  request.getAttributeType(),
                  request.getAssertionValue());
    op.run();

    CompareResponseProtocolOp compareResponse =
         new CompareResponseProtocolOp(
                  op.getResultCode().getIntValue(),
                  op.getErrorMessage().toMessage(),
                  op.getMatchedDN(),
                  op.getReferralURLs());
    ArrayList<LDAPControl> responseControls =
         new ArrayList<LDAPControl>();
    for (Control c : op.getResponseControls())
    {
      responseControls.add(new LDAPControl(c));
    }

    socket.getInputStream().addLDAPMessage(
         new LDAPMessage(messageID, compareResponse,
                         responseControls));
  }



  /**
   * Processes the content of the provided LDAP message as a delete
   * operation and returns the appropriate result to the client.
   *
   * @param  message  The LDAP message containing the request to
   *                  process.
   *
   * @throws  IOException  If a problem occurs while attempting to
   *                       process the operation.
   */
  private void processDeleteOperation(LDAPMessage message)
          throws IOException
  {
    int messageID = message.getMessageID();
    DeleteRequestProtocolOp request =
         message.getDeleteRequestProtocolOp();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    if (message.getControls() != null)
    {
      for (LDAPControl c : message.getControls())
      {
        requestControls.add(c.getControl());
      }
    }

    InternalClientConnection conn = socket.getConnection();
    DeleteOperationBasis op =
         new DeleteOperationBasis(conn, conn.nextOperationID(),
                  messageID, requestControls, request.getDN());
    op.run();

    DeleteResponseProtocolOp deleteResponse =
         new DeleteResponseProtocolOp(
                  op.getResultCode().getIntValue(),
                  op.getErrorMessage().toMessage(),
                  op.getMatchedDN(),
                  op.getReferralURLs());
    ArrayList<LDAPControl> responseControls =
         new ArrayList<LDAPControl>();
    for (Control c : op.getResponseControls())
    {
      responseControls.add(new LDAPControl(c));
    }

    socket.getInputStream().addLDAPMessage(
         new LDAPMessage(messageID, deleteResponse,
                         responseControls));
  }



  /**
   * Processes the content of the provided LDAP message as an extended
   * operation and returns the appropriate result to the client.
   *
   * @param  message  The LDAP message containing the request to
   *                  process.
   *
   * @throws  IOException  If a problem occurs while attempting to
   *                       process the operation.
   */
  private void processExtendedOperation(LDAPMessage message)
          throws IOException
  {
    int messageID = message.getMessageID();
    ExtendedRequestProtocolOp request =
         message.getExtendedRequestProtocolOp();
    if (request.getOID().equals(OID_START_TLS_REQUEST))
    {
      Message m = ERR_INTERNALOS_STARTTLS_NOT_SUPPORTED.get();
      ExtendedResponseProtocolOp extendedResponse =
           new ExtendedResponseProtocolOp(
                    LDAPResultCode.UNWILLING_TO_PERFORM, m);
      socket.getInputStream().addLDAPMessage(
           new LDAPMessage(messageID, extendedResponse));
      return;
    }

    ArrayList<Control> requestControls = new ArrayList<Control>();
    if (message.getControls() != null)
    {
      for (LDAPControl c : message.getControls())
      {
        requestControls.add(c.getControl());
      }
    }

    InternalClientConnection conn = socket.getConnection();
    ExtendedOperationBasis op =
         new ExtendedOperationBasis(conn, conn.nextOperationID(),
                  messageID, requestControls, request.getOID(),
                  request.getValue());
    op.run();

    ExtendedResponseProtocolOp extendedResponse =
         new ExtendedResponseProtocolOp(
                  op.getResultCode().getIntValue(),
                  op.getErrorMessage().toMessage(),
                  op.getMatchedDN(),
                  op.getReferralURLs(), op.getResponseOID(),
                  op.getResponseValue());
    ArrayList<LDAPControl> responseControls =
         new ArrayList<LDAPControl>();
    for (Control c : op.getResponseControls())
    {
      responseControls.add(new LDAPControl(c));
    }

    socket.getInputStream().addLDAPMessage(
         new LDAPMessage(messageID, extendedResponse,
                         responseControls));
  }



  /**
   * Processes the content of the provided LDAP message as a modify
   * operation and returns the appropriate result to the client.
   *
   * @param  message  The LDAP message containing the request to
   *                  process.
   *
   * @throws  IOException  If a problem occurs while attempting to
   *                       process the operation.
   */
  private void processModifyOperation(LDAPMessage message)
          throws IOException
  {
    int messageID = message.getMessageID();
    ModifyRequestProtocolOp request =
         message.getModifyRequestProtocolOp();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    if (message.getControls() != null)
    {
      for (LDAPControl c : message.getControls())
      {
        requestControls.add(c.getControl());
      }
    }

    InternalClientConnection conn = socket.getConnection();
    ModifyOperationBasis op =
         new ModifyOperationBasis(conn, conn.nextOperationID(),
                  messageID, requestControls, request.getDN(),
                  request.getModifications());
    op.run();

    ModifyResponseProtocolOp modifyResponse =
         new ModifyResponseProtocolOp(
                  op.getResultCode().getIntValue(),
                  op.getErrorMessage().toMessage(),
                  op.getMatchedDN(),
                  op.getReferralURLs());
    ArrayList<LDAPControl> responseControls =
         new ArrayList<LDAPControl>();
    for (Control c : op.getResponseControls())
    {
      responseControls.add(new LDAPControl(c));
    }

    socket.getInputStream().addLDAPMessage(
         new LDAPMessage(messageID, modifyResponse,
                         responseControls));
  }



  /**
   * Processes the content of the provided LDAP message as a modify DN
   * operation and returns the appropriate result to the client.
   *
   * @param  message  The LDAP message containing the request to
   *                  process.
   *
   * @throws  IOException  If a problem occurs while attempting to
   *                       process the operation.
   */
  private void processModifyDNOperation(LDAPMessage message)
          throws IOException
  {
    int messageID = message.getMessageID();
    ModifyDNRequestProtocolOp request =
         message.getModifyDNRequestProtocolOp();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    if (message.getControls() != null)
    {
      for (LDAPControl c : message.getControls())
      {
        requestControls.add(c.getControl());
      }
    }

    InternalClientConnection conn = socket.getConnection();
    ModifyDNOperationBasis op =
         new ModifyDNOperationBasis(conn, conn.nextOperationID(),
                  messageID, requestControls, request.getEntryDN(),
                  request.getNewRDN(), request.deleteOldRDN(),
                  request.getNewSuperior());
    op.run();

    ModifyDNResponseProtocolOp modifyDNResponse =
         new ModifyDNResponseProtocolOp(
                  op.getResultCode().getIntValue(),
                  op.getErrorMessage().toMessage(),
                  op.getMatchedDN(),
                  op.getReferralURLs());
    ArrayList<LDAPControl> responseControls =
         new ArrayList<LDAPControl>();
    for (Control c : op.getResponseControls())
    {
      responseControls.add(new LDAPControl(c));
    }

    socket.getInputStream().addLDAPMessage(
         new LDAPMessage(messageID, modifyDNResponse,
                         responseControls));
  }



  /**
   * Processes the content of the provided LDAP message as a search
   * operation and returns the appropriate result to the client.
   *
   * @param  message  The LDAP message containing the request to
   *                  process.
   *
   * @throws  IOException  If a problem occurs while attempting to
   *                       process the operation.
   */
  private void processSearchOperation(LDAPMessage message)
          throws IOException
  {
    int messageID = message.getMessageID();
    SearchRequestProtocolOp request =
         message.getSearchRequestProtocolOp();

    ArrayList<Control> requestControls = new ArrayList<Control>();
    if (message.getControls() != null)
    {
      for (LDAPControl c : message.getControls())
      {
        requestControls.add(c.getControl());
      }
    }

    InternalClientConnection conn = socket.getConnection();
    InternalSearchOperation op =
         new InternalSearchOperation(conn, conn.nextOperationID(),
                  messageID, requestControls, request.getBaseDN(),
                  request.getScope(), request.getDereferencePolicy(),
                  request.getSizeLimit(), request.getTimeLimit(),
                  request.getTypesOnly(), request.getFilter(),
                  request.getAttributes(), this);
    op.run();

    SearchResultDoneProtocolOp searchDone =
         new SearchResultDoneProtocolOp(
                  op.getResultCode().getIntValue(),
                  op.getErrorMessage().toMessage(),
                  op.getMatchedDN(),
                  op.getReferralURLs());
    ArrayList<LDAPControl> responseControls =
         new ArrayList<LDAPControl>();
    for (Control c : op.getResponseControls())
    {
      responseControls.add(new LDAPControl(c));
    }

    socket.getInputStream().addLDAPMessage(
         new LDAPMessage(messageID, searchDone, responseControls));
  }



  /**
   * Performs any processing necessary for the provided search result
   * entry.
   *
   * @param  searchOperation  The internal search operation being
   *                          processed.
   * @param  searchEntry      The matching search result entry to be
   *                          processed.
   */
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  public void handleInternalSearchEntry(
                   InternalSearchOperation searchOperation,
                   SearchResultEntry searchEntry)
  {
    ArrayList<LDAPControl> entryControls =
         new ArrayList<LDAPControl>();
    for (Control c : searchEntry.getControls())
    {
      entryControls.add(new LDAPControl(c));
    }

    SearchResultEntryProtocolOp entry =
         new SearchResultEntryProtocolOp(searchEntry);

    socket.getInputStream().addLDAPMessage(
         new LDAPMessage(searchOperation.getMessageID(), entry,
                         entryControls));
  }



  /**
   * Performs any processing necessary for the provided search result
   * reference.
   *
   * @param  searchOperation  The internal search operation being
   *                          processed.
   * @param  searchReference  The search result reference to be
   *                          processed.
   */
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  public void handleInternalSearchReference(
                   InternalSearchOperation searchOperation,
                   SearchResultReference searchReference)
  {
    ArrayList<LDAPControl> entryControls =
         new ArrayList<LDAPControl>();
    for (Control c : searchReference.getControls())
    {
      entryControls.add(new LDAPControl(c));
    }

    SearchResultReferenceProtocolOp reference =
         new SearchResultReferenceProtocolOp(searchReference);

    socket.getInputStream().addLDAPMessage(
         new LDAPMessage(searchOperation.getMessageID(), reference,
                         entryControls));
  }



  /**
   * Retrieves a string representation of this internal LDAP socket.
   *
   * @return  A string representation of this internal LDAP socket.
   */
  @Override()
  public String toString()
  {
    return "InternalLDAPOutputStream";
  }
}

