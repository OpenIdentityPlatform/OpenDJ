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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.asn1;



import java.io.InputStream;
import java.io.IOException;
import java.net.Socket;

import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;



/**
 * This class defines a utility that can be used to read ASN.1 elements from a
 * provided socket or input stream.
 */
public class ASN1Reader
{



  // The input stream from which to read the ASN.1 elements.
  private InputStream inputStream;

  // The largest element size (in bytes) that will be allowed.
  private int maxElementSize;

  // The socket with which the input stream is associated.
  private Socket socket;



  /**
   * Creates a new ASN.1 reader that will read elements from the provided
   * socket.
   *
   * @param  socket  The socket from which to read the ASN.1 elements.
   *
   * @throws  IOException  If a problem occurs while attempting to obtain the
   *                       input stream for the socket.
   */
  public ASN1Reader(Socket socket)
         throws IOException
  {
    this.socket = socket;
    inputStream = socket.getInputStream();

    maxElementSize = -1;
  }



  /**
   * Creates a new ASN.1 reader that will read elements from the provided input
   * stream.
   *
   * @param  inputStream  The input stream from which to read the ASN.1
   *                      elements.
   */
  public ASN1Reader(InputStream inputStream)
  {
    this.inputStream = inputStream;
    socket           = null;
    maxElementSize   = -1;
  }



  /**
   * Retrieves the maximum size in bytes that will be allowed for elements read
   * using this reader.  A negative value indicates that no limit should be
   * enforced.
   *
   * @return  The maximum size in bytes that will be allowed for elements.
   */
  public int getMaxElementSize()
  {
    return maxElementSize;
  }



  /**
   * Specifies the maximum size in bytes that will be allowed for elements.  A
   * negative value indicates that no limit should be enforced.
   *
   * @param  maxElementSize  The maximum size in bytes that will be allowed for
   *                         elements read using this reader.
   */
  public void setMaxElementSize(int maxElementSize)
  {
    this.maxElementSize = maxElementSize;
  }



  /**
   * Retrieves the maximum length of time in milliseconds that this reader will
   * be allowed to block while waiting to read data.  This is only applicable
   * for readers created with sockets rather than input streams.
   *
   * @return  The maximum length of time in milliseconds that this reader will
   *          be allowed to block while waiting to read data, or 0 if there is
   *          no limit, or -1 if this ASN.1 reader is not associated with a
   *          socket and no timeout can be enforced.
   *
   * @throws  IOException  If a problem occurs while polling the socket to
   *                       determine the timeout.
   */
  public int getIOTimeout()
         throws IOException
  {
    if (socket == null)
    {
      return -1;
    }
    else
    {
      return socket.getSoTimeout();
    }
  }



  /**
   * Specifies the maximum length of time in milliseconds that this reader
   * should be allowed to block while waiting to read data.  This will only be
   * applicable for readers created with sockets and will have no effect on
   * readers created with input streams.
   *
   * @param  ioTimeout  The maximum length of time in milliseconds that this
   *                    reader should be allowed to block while waiting to read
   *                    data, or 0 if there should be no limit.
   *
   * @throws  IOException  If a problem occurs while setting the underlying
   *                       socket option.
   */
  public void setIOTimeout(int ioTimeout)
         throws IOException
  {
    if (socket == null)
    {
      return;
    }

    socket.setSoTimeout(Math.max(0, ioTimeout));
  }



  /**
   * Reads an ASN.1 element from the associated input stream.
   *
   * @return  The ASN.1 element read from the associated input stream, or
   *          <CODE>null</CODE> if the end of the stream has been reached.
   *
   * @throws  IOException  If a problem occurs while attempting to read from the
   *                       input stream.
   *
   * @throws  ASN1Exception  If a problem occurs while attempting to decode the
   *                         data read as an ASN.1 element.
   */
  public ASN1Element readElement()
         throws IOException, ASN1Exception
  {
    // First, read the BER type, which should be the first byte.
    int typeValue = inputStream.read();
    if (typeValue < 0)
    {
      return null;
    }

    byte type = (byte) (typeValue & 0xFF);



    // Next, read the first byte of the length, and see if we need to read more.
    int length         = inputStream.read();
    int numLengthBytes = (length & 0x7F);
    if (length != numLengthBytes)
    {
      // Make sure that there are an acceptable number of bytes in the length.
      if (numLengthBytes > 4)
      {
        int    msgID   = MSGID_ASN1_ELEMENT_SET_INVALID_NUM_LENGTH_BYTES;
        String message = getMessage(msgID, numLengthBytes);
        throw new ASN1Exception(msgID, message);
      }


      length = 0;
      for (int i=0; i < numLengthBytes; i++)
      {
        int lengthByte = inputStream.read();
        if (lengthByte < 0)
        {
          // We've reached the end of the stream in the middle of the value.
          // This is not good, so throw an exception.
          int    msgID   = MSGID_ASN1_ELEMENT_SET_TRUNCATED_LENGTH;
          String message = getMessage(msgID, numLengthBytes);
          throw new IOException(message);
        }

        length = (length << 8) | lengthByte;
      }
    }


    // See how many bytes there are in the value.  If there are none, then just
    // create an empty element with only a type.  If the length is larger than
    // the maximum allowed, then fail.
    if (length == 0)
    {
      return new ASN1Element(type);
    }
    else if ((maxElementSize > 0) && (length > maxElementSize))
    {
      int    msgID   = MSGID_ASN1_READER_MAX_SIZE_EXCEEDED;
      String message = getMessage(msgID, length, maxElementSize);
      throw new ASN1Exception(msgID, message);
    }


    // There is a value for the element, so create an array to hold it and read
    // it from the stream.
    byte[] value       = new byte[length];
    int    readPos     = 0;
    int    bytesNeeded = length;
    while (bytesNeeded > 0)
    {
      int bytesRead = inputStream.read(value, readPos, bytesNeeded);
      if (bytesRead < 0)
      {
        int    msgID   = MSGID_ASN1_ELEMENT_SET_TRUNCATED_VALUE;
        String message = getMessage(msgID, length, bytesNeeded);
        throw new IOException(message);
      }

      bytesNeeded -= bytesRead;
      readPos     += bytesRead;
    }


    // Return the constructed element.
    return new ASN1Element(type, value);
  }



  /**
   * Closes this ASN.1 reader and the underlying input stream and/or socket.
   */
  public void close()
  {
    try
    {
      inputStream.close();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }
    }

    if (socket != null)
    {
      try
      {
        socket.close();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }
      }
    }
  }
}

