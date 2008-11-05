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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionSecurityProvider;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.ldap.LDAPClientConnection;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.InitializationException;
import static org.opends.server.loggers.debug.DebugLogger.*;

/**
 * This class provides an implementation of a connection security provider that
 * provides SASL confidentiality/integrity between the server and client.
 *
 */
public class SASLSecurityProvider extends ConnectionSecurityProvider {

    // The tracer object for the debug logger.
    private static final DebugTracer TRACER = getTracer();

    //The client connection associated with this provider.
    private ClientConnection connection;

    //The socket channel associated with this provider.
    private SocketChannel sockChannel;

    //The SASL context associated with the provider
    private SASLContext saslContext;

    //The number of bytes in the length buffer.
    private final int lengthSize = 4;

    //A byte buffer used to hold the length of the clear buffer.
    private ByteBuffer lengthBuf =  ByteBuffer.allocate(lengthSize);

    //The SASL mechanism name.
    private String name;

    /**
     * Create a SASL security provider with the specified parameters that is
     * capable of processing a confidentiality/integrity SASL connection.
     *
     * @param connection The client connection to read/write the bytes.
     * @param name The SASL mechanism name.
     * @param saslContext The SASL context to process the data through.
     */
    public SASLSecurityProvider(ClientConnection connection, String name,
                               SASLContext saslContext) {
      super();
      this.connection = connection;
      this.name = name;
      this.saslContext = saslContext;
      this.sockChannel = ((LDAPClientConnection) connection).getSocketChannel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disconnect(boolean connectionValid) {
        this.saslContext.dispose();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void finalizeConnectionSecurityProvider() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getClearBufferSize() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getEncodedBufferSize() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSecurityMechanismName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initializeConnectionSecurityProvider(ConfigEntry configEntry)
            throws ConfigException, InitializationException {
        this.connection = null;
        this.sockChannel = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSecure() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConnectionSecurityProvider newInstance(ClientConnection clientConn,
                                                  SocketChannel socketChannel) {
            return new SASLSecurityProvider(clientConn, null,null);
    }

    /**
     * Return the clear buffer length as determined by processing the first
     * 4 bytes of the specified buffer.
     *
     * @param byteBuf The buffer to examine the first 4 bytes of.
     * @return The size of the clear buffer.
     */
    private int getBufLength(ByteBuffer byteBuf) {
        int answer = 0;
        byte[] buf = byteBuf.array();

        for (int i = 0; i < lengthSize; i++) {
            answer <<= 8;
            answer |= ((int)buf[i] & 0xff);
        }
        return answer;
    }

    /**
     * Read from the socket channel into the specified byte buffer the
     * number of bytes specified in the total parameter.
     *
     * @param byteBuf The byte buffer to put the bytes in.
     * @param total The total number of bytes to read from the socket channel.
     * @return The number of bytes read, 0 or -1.
     * @throws IOException If an error occurred reading the socket channel.
     */
    private int readAll(ByteBuffer byteBuf, int total) throws IOException {
        int count = 0;
        while(sockChannel.isOpen() && total > 0) {
            count = sockChannel.read(byteBuf);
            if(count == -1)
                return -1;
           if(count == 0)
                return 0;
            total -= count;
        }
        if(total > 0)
            return -1;
        else
            return byteBuf.position();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean readData() throws DirectoryException {
        int recvBufSize = saslContext.getBufSize(Sasl.MAX_BUFFER);
        try {
            while(true) {
                lengthBuf.clear();
                int readResult = readAll(lengthBuf, lengthSize);
                if (readResult == -1) {
                    //Client connection has been closed. Disconnect and return.
                    connection.disconnect(DisconnectReason.CLIENT_DISCONNECT,
                            false, null);
                    return false;
                } else if(readResult == 0)
                    return true;
                int bufLength = getBufLength(lengthBuf);
                if(bufLength > recvBufSize) {
                    connection.disconnect(DisconnectReason.CLIENT_DISCONNECT,
                            false, null);
                    return false;
                }
                ByteBuffer readBuf = ByteBuffer.allocate(bufLength);
                readResult = readAll(readBuf, bufLength);
                if (readResult == -1) {
                    //Client connection has been closed. Disconnect and return.
                    connection.disconnect(DisconnectReason.CLIENT_DISCONNECT,
                            false, null);
                    return false;
                } else if (readResult == 0)
                    return true;
                byte[] inBytes = readBuf.array();
                byte[] clearBytes =
                                saslContext.unwrap(inBytes, 0, inBytes.length);
                ByteBuffer clearBuffer = ByteBuffer.wrap(clearBytes);
                if (!connection.processDataRead(clearBuffer))
                    return false;
            }
        } catch (SaslException saslEx) {
            if (debugEnabled()) {
                TRACER.debugCaught(DebugLogLevel.ERROR, saslEx);
              }
            //Error trying to unwrap the data.
            connection.disconnect(DisconnectReason.IO_ERROR, false, null);
            return false;
        } catch (IOException ioe) {
            // An error occurred while trying to communicate with the client.
            // Disconnect and return.
            if (debugEnabled()) {
                TRACER.debugCaught(DebugLogLevel.ERROR, ioe);
            }
            connection.disconnect(DisconnectReason.IO_ERROR, false, null);
            return false;
        }
    }

    /**
     * Creates a buffer suitable to send to the client using the specified
     * clear byte array, offset and length of the bytes to wrap.
     *
     * @param clearBytes The clear byte array to send to the client.
     * @param offSet An offset into the byte array to start the wrap at.
     * @param len The length of the bytes to wrap in the byte array.
     * @throws SaslException If the wrap of the bytes fails.
     */
    private ByteBuffer
    createSendBuffer(byte[] clearBytes, int offSet, int len)
    throws SaslException {
        byte[] wrapBytes = saslContext.wrap(clearBytes, offSet, len);
        byte[] outBuf = new  byte[wrapBytes.length + lengthSize];
        writeBufLen(outBuf, wrapBytes.length);
        System.arraycopy(wrapBytes, 0, outBuf, lengthSize, wrapBytes.length);
        return ByteBuffer.wrap(outBuf);
    }

    /**
     *  Writes the specified len parameter into the buffer in a form that can
     *  be sent over a network to the client.
     *
     * @param buf The buffer to hold the length bytes.
     * @param len The length to encode.
     */
    private void writeBufLen(byte[] buf, int len) {
        for (int i = 3; i >= 0; i--) {
            buf[i] = (byte)(len & 0xff);
            len >>>= 8;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override()
    public boolean writeData(ByteBuffer clearData) {
        int maxSendBufSize = saslContext.getBufSize(Sasl.RAW_SEND_SIZE);
        int clearLength = clearData.limit();
        try {
            if(clearLength < maxSendBufSize) {
                ByteBuffer sendBuffer =
                    createSendBuffer(clearData.array(),0,clearLength);
                return writeChannel(sendBuffer);
            } else {
                byte[] clearBytes = clearData.array();
                for(int i=0; i < clearLength; i += maxSendBufSize) {
                    int totLength = (clearLength - i) < maxSendBufSize ?
                                    (clearLength - i) : maxSendBufSize;
                    ByteBuffer sendBuffer =
                                     createSendBuffer(clearBytes, i, totLength);
                    if(!writeChannel(sendBuffer))
                        return false;
                }
            }
        } catch (SaslException e) {
            if (debugEnabled()) {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
            connection.disconnect(DisconnectReason.IO_ERROR, false, null);
            return false;
        }
        return true;
    }

   /**
    * Write the specified byte buffer to the socket channel.
    *
    * @param buffer The byte buffer to write to the socket channel.
    * @return {@code true} if the byte buffer was successfully written to the
    *         socket channel, or, {@code false} if not.
    */
    private boolean writeChannel(ByteBuffer buffer) {
        try {
            while (buffer.hasRemaining())  {
                int bytesWritten = sockChannel.write(buffer);
                if (bytesWritten < 0)  {
                    connection.disconnect(DisconnectReason.CLIENT_DISCONNECT,
                                          false, null);
                    return false;
                } else if (bytesWritten == 0)  {
                    return writeWithTimeout(buffer);
                }
            }
        }  catch (IOException ioe)  {
            if (debugEnabled()) {
                TRACER.debugCaught(DebugLogLevel.ERROR, ioe);
            }
            connection.disconnect(DisconnectReason.IO_ERROR, false, null);
            return false;
        }
        return true;
    }

    /**
     * Writes the specified byte buffer parameter to the socket channel waiting
     * for a period of time if the buffer cannot be written immediately.
     *
     * @param buffer The byte buffer to write to the channel.
     * @return {@code true} if the bytes were sent, or, {@code false} otherwise.
     * @throws IOException If an IO error occurs while writing the bytes.
     */
    private boolean writeWithTimeout(ByteBuffer buffer) throws IOException {
        long startTime = System.currentTimeMillis();
        long waitTime  = connection.getMaxBlockedWriteTimeLimit();
        if (waitTime <= 0) {
            // We won't support an infinite time limit, so fall back to using
            // five minutes, which is a very long timeout given that we're
            // blocking a worker thread.
            waitTime = 300000L;
        }
        long stopTime = startTime + waitTime;
        Selector selector = connection.getWriteSelector();
        if (selector == null) {
            // The client connection does not provide a selector, so we'll
            // fall back to a more inefficient way that will work without a
            // selector.
            while (buffer.hasRemaining() &&
                   (System.currentTimeMillis() < stopTime)) {
                if (sockChannel.write(buffer) < 0) {
                    // The client connection has been closed
                    connection.disconnect(DisconnectReason.CLIENT_DISCONNECT,
                                          false,  null);
                    return false;
                }
            }
            if (buffer.hasRemaining()) {
                // If we've gotten here, then the write timed out.
                // Terminate the client connection.
                connection.disconnect(DisconnectReason.IO_TIMEOUT, false, null);
                return false;
            }
            return true;
        }
        // Register with the selector for handling write operations.
        SelectionKey key =
                         sockChannel.register(selector, SelectionKey.OP_WRITE);
        try
        {
            selector.select(waitTime);
            while (buffer.hasRemaining())
            {
                long currentTime = System.currentTimeMillis();
                if (currentTime >= stopTime) {
                    // We've been blocked for too long
                    connection.disconnect(DisconnectReason.IO_TIMEOUT,
                                          false, null);
                    return false;
                }
                else {
                    waitTime = stopTime - currentTime;
                }
                Iterator<SelectionKey> iterator =
                                             selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey k = iterator.next();
                    if (k.isWritable()) {
                        int bytesWritten = sockChannel.write(buffer);
                        if (bytesWritten < 0) {
                            // The client connection has been closed.
                            connection.disconnect(
                                      DisconnectReason.CLIENT_DISCONNECT,
                                      false, null);
                            return false;
                        }
                        iterator.remove();
                    }
                }
                if (buffer.hasRemaining()) {
                    selector.select(waitTime);
                }
            }
            return true;
        } finally {
            if (key.isValid()) {
                key.cancel();
                selector.selectNow();
            }
        }
    }

    /**
     * Return if the underlying SASL context is active or not. The SASL context
     * may still be negotiating a multi-stage SASL bind and is not ready to
     * process confidentiality or integrity data yet.
     *
     * @return {@code true} if the underlying SASL context is active or ready
     *         to process confidentiality/integrity messages, or, {@code false}
     *         if not.
    */

    public boolean isActive() {
        return saslContext.isBindComplete();
    }
}
