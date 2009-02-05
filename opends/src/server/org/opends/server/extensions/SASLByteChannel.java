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

package org.opends.server.extensions;

import java.nio.channels.ByteChannel;
import java.security.cert.Certificate;
import static org.opends.server.loggers.debug.DebugLogger.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import javax.security.sasl.Sasl;
import org.opends.server.api.ClientConnection;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.ldap.LDAPClientConnection;
import org.opends.server.util.StaticUtils;

/**
 * This class implements a SASL byte channel that can be used during
 * confidentiality and integrity.
 *
 */
public class
SASLByteChannel implements ByteChannel, ConnectionSecurityProvider {

    // The tracer object for the debug logger.
    private static final DebugTracer TRACER = getTracer();

    // The client connection associated with this provider.
    private ClientConnection connection;

    // The socket channel associated with this provider.
    private SocketChannel sockChannel;

    // The SASL context associated with the provider
    private SASLContext saslContext;

    // The number of bytes in the length buffer.
    private final int lengthSize = 4;

    // A byte buffer used to hold the length of the clear buffer.
    private ByteBuffer lengthBuf = ByteBuffer.allocate(lengthSize);

    // The SASL mechanism name.
    private String name;


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
      this.sockChannel = ((LDAPClientConnection) connection).getSocketChannel();
    }

    /**
     * Return a SASL byte channel instance created using the specified
     * parameters.
     *
     * @param c A client connection associated with the instance.
     * @param name The name of the instance (SASL mechanism name).
     * @param context A SASL context associaetd with the instance.
     * @return A SASL byte channel.
     */
    public static SASLByteChannel
    getSASLByteChannel(ClientConnection c, String name,
                          SASLContext context) {
          return new SASLByteChannel(c, name, context);
    }

    /**
     * Read from the socket channel into the specified byte buffer the
     * number of bytes specified in the total parameter.
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
      while (sockChannel.isOpen() && total > 0) {
        count = sockChannel.read(byteBuf);
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
      byte[] buf = byteBuf.array();

      for (int i = 0; i < lengthSize; i++)
      {
        answer <<= 8;
        answer |= ((int) buf[i] & 0xff);
      }
      return answer;
    }


    /**
     * {@inheritDoc}
     */
    public int read(ByteBuffer clearDst) throws IOException {
        int recvBufSize = getAppBufSize();
        if(recvBufSize > clearDst.capacity())
            return -1;
        lengthBuf.clear();
        int readResult = readAll(lengthBuf, lengthSize);
        if (readResult == -1)
            return -1;
        else if (readResult == 0) return 0;
        int bufLength = getBufLength(lengthBuf);
        if (bufLength > recvBufSize) //TODO SASLPhase2 add message
            return -1;
        ByteBuffer readBuf = ByteBuffer.allocate(bufLength);
        readResult = readAll(readBuf, bufLength);
        if (readResult == -1)
            return -1;
        else if (readResult == 0) return 0;
        byte[] inBytes = readBuf.array();
        byte[] clearBytes = saslContext.unwrap(inBytes, 0, inBytes.length);
        for(int i = 0; i < clearBytes.length; i++) {
            clearDst.put(clearBytes[i]);
        }
        return clearDst.remaining();
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
        int bytesWritten = sockChannel.write(buffer);
        if (bytesWritten < 0)
            throw new ClosedChannelException();
        else if (bytesWritten == 0) {
            if(!StaticUtils.writeWithTimeout(
                    connection, sockChannel, buffer))
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
        return saslContext.getBufSize(Sasl.RAW_SEND_SIZE) + lengthSize;
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
