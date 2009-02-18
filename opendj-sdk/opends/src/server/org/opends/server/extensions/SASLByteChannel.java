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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */

package org.opends.server.extensions;

import java.nio.channels.ByteChannel;
import java.security.cert.Certificate;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import javax.security.sasl.Sasl;
import org.opends.server.api.ClientConnection;
import org.opends.server.util.StaticUtils;

/**
 * This class implements a SASL byte channel that can be used during
 * confidentiality and integrity.
 *
 */
public class
SASLByteChannel implements ByteChannel, ConnectionSecurityProvider {

    // The client connection associated with this provider.
    private ClientConnection connection;

    // The SASL context associated with the provider
    private SASLContext saslContext;

    // The byte channel associated with this provider.
    private RedirectingByteChannel channel;

    // The number of bytes in the length buffer.
    private final int lengthSize = 4;

    //Length of the buffer.
    private int bufLength;

    // The SASL mechanism name.
    private String name;

    //Buffers used in reading and decoding (unwrap)
    private ByteBuffer readBuffer, decodeBuffer;

    //How many bytes of the subsequent buffer is needed to complete a partially
    //read buffer.
    private int neededBytes = 0;

    //Used to not reset the buffer length size because the first 4 bytes of a
    //buffer are not size bytes.
    private boolean reading = false;

    /**
     * Create a SASL byte channel with the specified parameters
     * that is capable of processing a confidentiality/integrity SASL
     * connection.
     *
     * @param connection
     *          The client connection to read/write the bytes.
     * @param name
     *          The SASL mechanism name.
     * @param saslContext
     *          The SASL context to process the data through.
     */
    private SASLByteChannel(ClientConnection connection, String name,
        SASLContext saslContext) {
      this.connection = connection;
      this.name = name;
      this.saslContext = saslContext;
      this.channel = connection.getChannel();
      this.readBuffer = ByteBuffer.allocate(connection.getAppBufferSize());
      this.decodeBuffer = ByteBuffer.allocate(connection.getAppBufferSize());
    }

    /**
     * Return a SASL byte channel instance created using the specified
     * parameters.
     *
     * @param c A client connection associated with the instance.
     * @param name The name of the instance (SASL mechanism name).
     * @param context A SASL context associated with the instance.
     * @return A SASL byte channel.
     */
    public static SASLByteChannel
    getSASLByteChannel(ClientConnection c, String name,
                          SASLContext context) {
          return new SASLByteChannel(c, name, context);
    }

    /**
     * Finish processing a previous, partially read buffer using some, or, all
     * of the bytes of the current buffer.
     *
     */
    private int processPartial(int readResult, ByteBuffer clearDst)
    throws IOException {
      readBuffer.flip();
      //Use all of the bytes of the current buffer and read some more.
      if(neededBytes > readResult) {
        neededBytes -= readResult;
        decodeBuffer.put(readBuffer);
        readBuffer.clear();
        reading = false;
        return 0;
      }
      //Use a portion of the current buffer.
      for(;neededBytes > 0;neededBytes--) {
        decodeBuffer.put(readBuffer.get());
      }
      //Unwrap the now completed buffer.
      byte[] inBytes = decodeBuffer.array();
      byte[]clearBytes = saslContext.unwrap(inBytes, lengthSize, bufLength);
      clearDst.put(clearBytes);
      decodeBuffer.clear();
      readBuffer.compact();
      //If the read buffer has bytes, these are a new buffer. Reset the
      //buffer length to the new value.
      if(readBuffer.position() != 0) {
        bufLength = getBufLength(readBuffer);
        reading = true;
      } else
        reading=false;
      return clearDst.position();
    }

    /**
     * Read from the socket channel into the specified byte buffer at least
     * the number of bytes specified in the total parameter.
     *
     * @param byteBuf
     *          The byte buffer to put the bytes in.
     * @param total
     *          The total number of bytes to read from the socket
     *          channel.
     * @return The number of bytes read, 0 or -1.
     * @throws IOException
     *           If an error occurred reading the socket channel.
     */
    private int readAll(ByteBuffer byteBuf, int total) throws IOException
    {
      int count = 0;
      while (channel.isOpen() && total > 0) {
        count = channel.read(byteBuf);
        if (count == -1) return -1;
        if (count == 0) return 0;
        total -= count;
      }
      if (total > 0)
        return -1;
      else
        return byteBuf.position();
    }

    /**
     * Return the clear buffer length as determined by processing the
     * first 4 bytes of the specified buffer.
     *
     * @param byteBuf
     *          The buffer to examine the first 4 bytes of.
     * @return The size of the clear buffer.
     */
    private int getBufLength(ByteBuffer byteBuf)
    {
      int answer = 0;

      for (int i = 0; i < lengthSize; i++)
      {
        byte b = byteBuf.get(i);
        answer <<= 8;
        answer |= ((int) b & 0xff);
      }
      return answer;
    }

    /**
     * {@inheritDoc}
     */
    public int read(ByteBuffer clearDst) throws IOException {
      int bytesToRead = lengthSize;
      if(reading)
        bytesToRead = neededBytes;
      int readResult = readAll(readBuffer, bytesToRead);
      if (readResult == -1)
        return -1;
      //The previous buffer read was not complete, the current
      //buffer completes it.
      if(neededBytes > 0 && readResult > 0)
          return(processPartial(readResult, clearDst));
      if(readResult == 0 && !reading) return 0;
      if(!reading) {
        bufLength = getBufLength(readBuffer);
      }
      reading=false;
      //The buffer length is greater than what is there, save what is there,
      //figure out how much more is needed and return.
      if(bufLength > readBuffer.position()) {
        neededBytes = bufLength - readBuffer.position() + 4;
        readBuffer.flip();
        decodeBuffer.put(readBuffer);
        readBuffer.clear();
        return 0;
      } else {
        readBuffer.flip();
        decodeBuffer.put(readBuffer);
        byte[] inBytes = decodeBuffer.array();
        byte[]clearBytes = saslContext.unwrap(inBytes, lengthSize, bufLength);
        decodeBuffer.clear();
        clearDst.put(clearBytes);
        readBuffer.clear();
      }
      return clearDst.position();
    }

    /**
     * Writes the specified len parameter into the buffer in a form that
     * can be sent over a network to the client.
     *
     * @param buf
     *          The buffer to hold the length bytes.
     * @param len
     *          The length to encode.
     */
    private void writeBufLen(byte[] buf, int len)
    {
      for (int i = 3; i >= 0; i--)
      {
        buf[i] = (byte) (len & 0xff);
        len >>>= 8;
      }
    }

    /**
     * Creates a buffer suitable to send to the client using the
     * specified clear byte array  and length of the bytes to
     * wrap.
     *
     * @param clearBytes
     *          The clear byte array to send to the client.
     * @param len
     *          The length of the bytes to wrap in the byte array.
     * @throws SaslException
     *           If the wrap of the bytes fails.
     */
    private ByteBuffer wrap(byte[] clearBytes, int len) throws IOException {
      byte[] wrapBytes = saslContext.wrap(clearBytes, 0, len);
      byte[] outBytes = new byte[wrapBytes.length + lengthSize];
      writeBufLen(outBytes, wrapBytes.length);
      System.arraycopy(wrapBytes, 0, outBytes, lengthSize, wrapBytes.length);
      return ByteBuffer.wrap(outBytes);
    }


    /**
     * {@inheritDoc}
     */
    public int write(ByteBuffer clearSrc) throws IOException {
        int sendBufSize = getAppBufSize();
        int srcLen = clearSrc.remaining();
        ByteBuffer sendBuffer = ByteBuffer.allocate(sendBufSize);
        if (srcLen > sendBufSize) {
            int oldPos = clearSrc.position();
            int curPos = oldPos;
            int curLimit = oldPos + sendBufSize;
            while (curPos < srcLen) {
                clearSrc.position(curPos);
                clearSrc.limit(curLimit);
                sendBuffer.put(clearSrc);
                writeChannel(wrap(sendBuffer.array(), clearSrc.remaining()));
                curPos = curLimit;
                curLimit = Math.min(srcLen, curPos + sendBufSize);
            }
            return srcLen;
        } else {
            sendBuffer.put(clearSrc);
            return writeChannel(wrap(sendBuffer.array() ,srcLen));
        }
    }


    /**
     * Write the specified byte buffer to the socket channel.
     *
     * @param buffer
     *          The byte buffer to write to the socket channel.
     * @return {@code true} if the byte buffer was successfully written
     *         to the socket channel, or, {@code false} if not.
     */
    private int writeChannel(ByteBuffer buffer) throws IOException {
        int bytesWritten = channel.write(buffer);
        if (bytesWritten < 0)
            throw new ClosedChannelException();
        else if (bytesWritten == 0) {
            if(!StaticUtils.writeWithTimeout(connection, buffer))
                throw new ClosedChannelException();
        }
        return bytesWritten;
      }

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        saslContext.dispose();
        saslContext=null;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isOpen() {
        return saslContext != null;
    }

    /**
     * {@inheritDoc}
     */
    public int getAppBufSize() {
        return saslContext.getBufSize(Sasl.MAX_BUFFER);
    }

    /**
     * {@inheritDoc}
     */
    public Certificate[] getClientCertificateChain() {
        return new Certificate[0];
    }

    /**
     * {@inheritDoc}
     */
    public int getSSF() {
        return saslContext.getSSF();
    }

    /**
     * {@inheritDoc}
     */
    public ByteChannel wrapChannel(ByteChannel channel) {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isSecure() {
        return true;
    }

}
