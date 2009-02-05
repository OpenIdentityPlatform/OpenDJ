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



import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ArrayBlockingQueue;

import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.types.ByteSequenceReader;
import org.opends.server.types.ByteStringBuilder;


/**
 * This class provides an implementation of a
 * {@code java.io.InputStream} that can be used to facilitate internal
 * communication with the Directory Server.  On the backend, this
 * input stream will be populated by ASN.1 elements encoded from LDAP
 * messages created from internal operation responses.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class InternalLDAPInputStream
       extends InputStream
{
  // The queue of LDAP messages providing the data to be made
  // available to the client.
  private final ArrayBlockingQueue<LDAPMessage> messageQueue;

  // Indicates whether this stream has been closed.
  private boolean closed;

  // The byte buffer with partial data to be written to the client.
  private final ByteStringBuilder messageBuffer;

  // The byte buffer reader.
  private final ByteSequenceReader messageReader;

  // The byte buffer writer.
  private final ASN1Writer writer;

  // The internal LDAP socket serviced by this input stream.
  private final InternalLDAPSocket socket;



  /**
   * Creates a new internal LDAP input stream that will service the
   * provided internal LDAP socket.
   *
   * @param  socket  The internal LDAP socket serviced by this
   *                 internal LDAP input stream.
   */
  public InternalLDAPInputStream(InternalLDAPSocket socket)
  {
    this.socket = socket;
    this.messageQueue = new ArrayBlockingQueue<LDAPMessage>(10);
    this.messageBuffer = new ByteStringBuilder();
    this.messageReader = messageBuffer.asReader();
    this.writer = ASN1.getWriter(messageBuffer);
    this.closed = false;
  }



  /**
   * Adds the provided LDAP message to the set of messages to be
   * returned to the client.  Note that this may block if there is
   * already a significant backlog of messages to be returned.
   *
   * @param  message  The message to add to the set of messages to be
   *                  returned to the client.
   */
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  void addLDAPMessage(LDAPMessage message)
  {
    // If the stream is closed, then simply drop the message.
    if (closed)
    {
      return;
    }

    try
    {
      messageQueue.put(message);
      return;
    }
    catch (Exception e)
    {
      // This shouldn't happen, but if it does then try three more
      // times before giving up and dropping the message.
      for (int i=0; i < 3; i++)
      {
        try
        {
          messageQueue.put(message);
          break;
        } catch (Exception e2) {}
      }

      return;
    }
  }



  /**
   * Retrieves the number of bytes that can be read (or skipped over)
   * from this input stream without blocking.
   *
   * @return  The number of bytes that can be read (or skipped over)
   *          from this input stream wihtout blocking.
   * @throws IOException if an I/O error occurs.
   */
  @Override()
  public synchronized int available() throws IOException
  {
    if (messageReader.remaining() < 1)
    {
      LDAPMessage message = messageQueue.poll();
      if ((message == null) || (message instanceof NullLDAPMessage))
      {
        if (message != null)
        {
          messageQueue.clear();
          closed = true;
        }

        return 0;
      }
      else
      {
        messageBuffer.clear();
        messageReader.rewind();
        message.write(writer);
      }
    }

    return messageReader.remaining();
  }



  /**
   * Closes this input stream.  This will add a special marker
   * element to the message queue indicating that the end of the
   * stream has been reached.  If the queue is full, thenit will be
   * cleared before adding the marker element.
   */
  @Override()
  public void close()
  {
    socket.close();
  }



  /**
   * Closes this input stream through an internal mechanism that will
   * not cause an infinite recursion loop by trying to also close the
   * input stream.
   */
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  void closeInternal()
  {
    if (closed)
    {
      return;
    }

    closed = true;
    NullLDAPMessage nullMessage = new NullLDAPMessage();

    while (! messageQueue.offer(nullMessage))
    {
      messageQueue.clear();
    }
  }



  /**
   * Marks the current position in the input stream.  This will not
   * have any effect, as this input stream inplementation does not
   * support marking.
   *
   * @param  readLimit  The maximum limit of bytes that can be read
   *                    before the mark position becomes invalid.
   */
  @Override()
  public void mark(int readLimit)
  {
    // No implementation is required.
  }



  /**
   * Indicates whether this input stream inplementation supports the
   * use of the {@code mark} and {@code reset} methods.  This
   * implementation does not support that functionality.
   *
   * @return  {@code false} because this implementation does not
   *          support the use of the {@code mark} and {@code reset}
   *          methods.
   */
  @Override()
  public boolean markSupported()
  {
    return false;
  }



  /**
   * Reads the next byte of data from the input stream, blocking if
   * necessary until there is data available.
   *
   * @return  The next byte of data read from the input stream, or -1
   *          if the end of the input stream has been reached.
   *
   * @throws  IOException  If a problem occurs while trying to read
   *                       data from the stream.
   */
  @Override()
  public synchronized int read()
         throws IOException
  {
    if (messageReader.remaining() < 1)
    {
      LDAPMessage message;
      try
      {
        message = messageQueue.take();
      }
      catch(InterruptedException ie)
      {
        // Probably because a shutdown was started. EOF
        message = new NullLDAPMessage();
      }

      if ((message == null) || (message instanceof NullLDAPMessage))
      {
        if (message instanceof NullLDAPMessage)
        {
          messageQueue.clear();
          closed = true;
          return -1;
        }

        return 0;
      }
      else
      {
        messageBuffer.clear();
        messageReader.rewind();
        message.write(writer);
      }
    }

    return (0xFF & messageReader.get());
  }



  /**
   * Reads some number of bytes from the input stream, blocking if
   * necessary until there is data available, and adds them to the
   * provided array starting at position 0.
   *
   * @param  b  The array to which the data is to be written.
   *
   * @return  The number of bytes actually written into the
   *          provided array, or -1 if the end of the stream has been
   *          reached.
   *
   * @throws  IOException  If a problem occurs while trying to read
   *                       data from the stream.
   */
  @Override()
  public int read(byte[] b)
         throws IOException
  {
    return read(b, 0, b.length);
  }



  /**
   * Reads some number of bytes from the input stream, blocking if
   * necessary until there is data available, and adds them to the
   * provided array starting at the specified position.
   *
   * @param  b    The array to which the data is to be written.
   * @param  off  The offset in the array at which to start writing
   *              data.
   * @param  len  The maximum number of bytes that may be added to the
   *              array.
   *
   * @return  The number of bytes actually written into the
   *          provided array, or -1 if the end of the stream has been
   *          reached.
   *
   * @throws  IOException  If a problem occurs while trying to read
   *                       data from the stream.
   */
  @Override()
  public synchronized int read(byte[] b, int off, int len)
         throws IOException
  {
    if (messageReader.remaining() < 1)
    {
      LDAPMessage message;
      try
      {
        message = messageQueue.take();
      }
      catch(InterruptedException ie)
      {
        // Probably because a shutdown was started. EOF
        message = new NullLDAPMessage();
      }

      if ((message == null) || (message instanceof NullLDAPMessage))
      {
        if (message instanceof NullLDAPMessage)
        {
          messageQueue.clear();
          closed = true;
          return -1;
        }

        return 0;
      }
      else
      {
        messageBuffer.clear();
        messageReader.rewind();
        message.write(writer);
      }
    }

    int actualLen = Math.min(len, messageReader.remaining());
    messageReader.get(b, off, actualLen);
    return actualLen;
  }



  /**
   * Repositions this stream to the position at the time that the
   * {@code mark} method was called on this stream.  This will not
   * have any effect, as this input stream inplementation does not
   * support marking.
   */
  @Override()
  public void reset()
  {
    // No implementation is required.
  }



  /**
   * Skips over and discards up to the specified number of bytes of
   * data from this input stream.  This implementation will always
   * skip the requested number of bytes unless the end of the stream
   * is reached.
   *
   * @param  n  The maximum number of bytes to skip.
   *
   * @return  The number of bytes actually skipped.
   *
   * @throws  IOException  If a problem occurs while trying to read
   *                       data from the input stream.
   */
  @Override()
  public synchronized long skip(long n)
         throws IOException
  {
    byte[] b;
    if (n > 8192)
    {
      b = new byte[8192];
    }
    else
    {
      b = new byte[(int) n];
    }

    long totalBytesRead = 0L;
    while (totalBytesRead < n)
    {
      int maxLen = (int) Math.min((n - totalBytesRead), b.length);

      int bytesRead = read(b, 0, maxLen);
      if (bytesRead < 0)
      {
        if (totalBytesRead > 0)
        {
          return totalBytesRead;
        }
        else
        {
          return bytesRead;
        }
      }
      else
      {
        totalBytesRead += bytesRead;
      }
    }

    return totalBytesRead;
  }



  /**
   * Retrieves a string representation of this internal LDAP socket.
   *
   * @return  A string representation of this internal LDAP socket.
   */
  @Override()
  public String toString()
  {
    return "InternalLDAPInputStream";
  }
}

