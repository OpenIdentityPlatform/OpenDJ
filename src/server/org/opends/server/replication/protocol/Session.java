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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.protocol;



import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.DataFormatException;

import javax.net.ssl.SSLSocket;

import org.opends.server.api.DirectoryThread;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.util.StaticUtils;



/**
 * This class defines a replication session using TLS.
 */
public final class Session extends DirectoryThread
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  private final Socket plainSocket;
  private final SSLSocket secureSocket;
  private final InputStream plainInput;
  private final OutputStream plainOutput;
  private final byte[] rcvLengthBuf = new byte[8];
  private final String readableRemoteAddress;
  private final String remoteAddress;
  private final String localUrl;

  /**
   * The time the last message published to this session.
   */
  private volatile long lastPublishTime = 0;

  /**
   * The time the last message was received on this session.
   */
  private volatile long lastReceiveTime = 0;

  /*
   * Close and error guarded by stateLock: use a different lock to publish since
   * publishing can block, and we don't want to block while closing failed
   * connections.
   */
  private final Object stateLock = new Object();
  private volatile boolean closeInitiated = false;
  private Throwable sessionError = null;

  /*
   * Publish guarded by publishLock: use a full lock here so that we can
   * optionally publish StopMsg during close.
   */
  private final Lock publishLock = new ReentrantLock();

  /*
   * These do not need synchronization because they are only modified during the
   * initial single threaded handshake.
   */
  private short protocolVersion = ProtocolVersion.getCurrentVersion();
  private boolean isEncrypted = true; // Initially encrypted.

  /*
   * Use a buffered input stream to avoid too many system calls.
   */
  private BufferedInputStream input;

  /*
   * Use a buffered output stream in order to combine message length and content
   * into a single TCP packet if possible.
   */
  private BufferedOutputStream output;

  private final LinkedBlockingQueue<byte[]> sendQueue =
      new LinkedBlockingQueue<byte[]>(4000);
  private AtomicBoolean isRunning = new AtomicBoolean(false);
  private final CountDownLatch latch = new CountDownLatch(1);

  /**
   * Creates a new Session.
   *
   * @param socket
   *          The regular Socket on which the SocketSession will be based.
   * @param secureSocket
   *          The secure Socket on which the SocketSession will be based.
   * @throws IOException
   *           When an IException happens on the socket.
   */
  public Session(final Socket socket,
                 final SSLSocket secureSocket) throws IOException
  {
    super("Replication Session from "+ socket.getLocalSocketAddress() +
        " to " + socket.getRemoteSocketAddress());
    if (debugEnabled())
    {
      TRACER.debugInfo(
          "Creating Session from %s to %s in %s",
          socket.getLocalSocketAddress(),
          socket.getRemoteSocketAddress(),
          stackTraceToSingleLineString(new Exception()));
    }

    this.plainSocket = socket;
    this.secureSocket = secureSocket;
    this.plainInput = plainSocket.getInputStream();
    this.plainOutput = plainSocket.getOutputStream();
    this.input = new BufferedInputStream(secureSocket.getInputStream());
    this.output = new BufferedOutputStream(secureSocket.getOutputStream());
    this.readableRemoteAddress = plainSocket.getRemoteSocketAddress()
        .toString();
    this.remoteAddress = plainSocket.getInetAddress().getHostAddress();
    this.localUrl = plainSocket.getLocalAddress().getHostName() + ":"
        + plainSocket.getLocalPort();
  }



  /**
   * This method is called when the session with the remote must be closed.
   * This object won't be used anymore after this method is called.
   */
  public void close()
  {
    Throwable localSessionError;

    synchronized (stateLock)
    {
      if (closeInitiated)
      {
        return;
      }

      localSessionError = sessionError;
      closeInitiated = true;
    }

    try {
      this.interrupt();
      this.join();
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // Perform close outside of critical section.
    if (debugEnabled())
    {
      if (localSessionError == null)
      {
        TRACER.debugInfo(
            "Closing Session from %s to %s in %s",
            plainSocket.getLocalSocketAddress(),
            plainSocket.getRemoteSocketAddress(),
            stackTraceToSingleLineString(new Exception()));
      }
      else
      {
        TRACER.debugInfo(
            "Aborting Session from %s to %s in %s due to the "
                + "following error: %s",
            plainSocket.getLocalSocketAddress(),
            plainSocket.getRemoteSocketAddress(),
            stackTraceToSingleLineString(new Exception()),
            stackTraceToSingleLineString(localSessionError));
      }
    }

    // V4 protocol introduces a StopMsg to properly end communications.
    if (localSessionError == null)
    {
      if (protocolVersion >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
      {
        try
        {
          publish(new StopMsg());
        }
        catch (final IOException ignored)
        {
          // Ignore errors on close.
        }
      }
    }

    StaticUtils.close(plainSocket, secureSocket);
  }



  /**
   * This methods allows to determine if the session close was initiated
   * on this Session.
   *
   * @return A boolean allowing to determine if the session close was initiated
   * on this Session.
   */
  public boolean closeInitiated()
  {
    synchronized (stateLock)
    {
      return closeInitiated;
    }
  }



  /**
   * Gets the time the last replication message was published on this
   * session.
   * @return The timestamp in milliseconds of the last message published.
   */
  public long getLastPublishTime()
  {
    return lastPublishTime;
  }



  /**
   * Gets the time the last replication message was received on this
   * session.
   * @return The timestamp in milliseconds of the last message received.
   */
  public long getLastReceiveTime()
  {
    if (lastReceiveTime == 0)
    {
      return System.currentTimeMillis();
    }
    return lastReceiveTime;
  }



  /**
   * Retrieve the local URL in the form host:port.
   *
   * @return The local URL.
   */
  public String getLocalUrl()
  {
    return localUrl;
  }



  /**
   * Retrieve the human readable address of the remote server.
   *
   * @return The human readable address of the remote server.
   */
  public String getReadableRemoteAddress()
  {
    return readableRemoteAddress;
  }



  /**
   * Retrieve the IP address of the remote server.
   *
   * @return The IP address of the remote server.
   */
  public String getRemoteAddress()
  {
    return remoteAddress;
  }



  /**
   * Determine whether the session is using a security layer.
   * @return true if the connection is encrypted, false otherwise.
   */
  public boolean isEncrypted()
  {
    return isEncrypted;
  }



  /**
   * Sends a replication message to the remote peer.
   *
   * @param msg
   *          The message to be sent.
   * @throws IOException
   *           If an IO error occurred.
   */
  public void publish(final ReplicationMsg msg) throws IOException
  {
    final byte[] buffer = msg.getBytes(protocolVersion);
    if (isRunning.get())
    {
      try {
        sendQueue.put(buffer);
      }
      catch (final InterruptedException e) {
        setSessionError(e);
        throw new IOException(e.getMessage());
      }
    } else {
      send(buffer);
    }
  }

  /** Sends a replication message already encoded to the socket.
   *
   * @param buffer
   *          the encoded buffer
   * @throws IOException if the message could not be sent
   */
  private void send(final byte[] buffer) throws IOException
  {
    final String str = String.format("%08x", buffer.length);
    final byte[] sendLengthBuf = str.getBytes();

    publishLock.lock();
    try
    {
      /*
       * The buffered output stream ensures that the message is usually sent as
       * a single TCP packet.
       */
      output.write(sendLengthBuf);
      output.write(buffer);
      output.flush();
    } catch (final IOException e) {
      setSessionError(e);
      throw e;
    }
    finally
    {
      publishLock.unlock();
    }

    lastPublishTime = System.currentTimeMillis();
  }



  /**
   * Attempt to receive a ReplicationMsg.
   * This method should block the calling thread until a
   * ReplicationMsg is available or until an error condition.
   *
   * This method can only be called by a single thread and therefore does not
   * need to implement any replication.
   *
   * @return The ReplicationMsg that was received.
   * @throws IOException When error happened during IO process.
   * @throws DataFormatException When the data received is not formatted as a
   *         ReplicationMsg.
   * @throws NotSupportedOldVersionPDUException If the received PDU is part of
   * an old protocol version and we do not support it.
   */
  public ReplicationMsg receive() throws IOException,
      DataFormatException, NotSupportedOldVersionPDUException
  {
    try
    {
      // Read the first 8 bytes containing the packet length.
      int length = 0;

      /*
       * Let's start the stop-watch before waiting on read for the heartbeat
       * check to be operational.
       */
      lastReceiveTime = System.currentTimeMillis();

      while (length < 8)
      {
        final int read = input.read(rcvLengthBuf, length, 8 - length);
        if (read == -1)
        {
          lastReceiveTime = 0;
          throw new IOException("no more data");
        }
        else
        {
          length += read;
        }
      }

      final int totalLength = Integer.parseInt(new String(
          rcvLengthBuf), 16);

      try
      {
        length = 0;
        final byte[] buffer = new byte[totalLength];
        while (length < totalLength)
        {
          final int read = input.read(buffer, length, totalLength
              - length);
          if (read == -1)
          {
            lastReceiveTime = 0;
            throw new IOException("no more data");
          }
          else
          {
            length += read;
          }
        }

        /*
         * We do not want the heartbeat to close the session when we are
         * processing a message even a time consuming one.
         */
        lastReceiveTime = 0;
        return ReplicationMsg.generateMsg(buffer, protocolVersion);
      }
      catch (final OutOfMemoryError e)
      {
        throw new IOException("Packet too large, can't allocate "
            + totalLength + " bytes.");
      }
    }
    catch (final IOException e)
    {
      setSessionError(e);
      throw e;
    }
    catch (final DataFormatException e)
    {
      setSessionError(e);
      throw e;
    }
    catch (final NotSupportedOldVersionPDUException e)
    {
      setSessionError(e);
      throw e;
    }
    catch (final RuntimeException e)
    {
      setSessionError(e);
      throw e;
    }
  }


  /**
   * This method is called at the establishment of the session and can
   * be used to record the version of the protocol that is currently used.
   *
   * @param version The version of the protocol that is currently used.
   */
  public void setProtocolVersion(final short version)
  {
    protocolVersion = version;
  }


  /**
   * Returns the version of the protocol that is currently used.
   *
   * @return The version of the protocol that is currently used.
   */
  public short getProtocolVersion()
  {
    return protocolVersion;
  }



  /**
   * Set a timeout value.
   * With this option set to a non-zero value, calls to the receive() method
   * block for only this amount of time after which a
   * java.net.SocketTimeoutException is raised.
   * The Broker is valid and usable even after such an Exception is raised.
   *
   * @param timeout the specified timeout, in milliseconds.
   * @throws SocketException if there is an error in the underlying protocol,
   *         such as a TCP error.
   */
  public void setSoTimeout(final int timeout) throws SocketException
  {
    plainSocket.setSoTimeout(timeout);
  }



  /**
   * Stop using the security layer, if there is any.
   */
  public void stopEncryption()
  {
    /*
     * The secure socket has been configured not to auto close the underlying
     * plain socket. We should close it here and properly tear down the SSL
     * session, but this is not compatible with the existing protocol.
     */
    if (false)
    {
      StaticUtils.close(secureSocket);
    }

    input = new BufferedInputStream(plainInput);
    output = new BufferedOutputStream(plainOutput);
    isEncrypted = false;
  }



  private void setSessionError(final Exception e)
  {
    synchronized (stateLock)
    {
      if (sessionError == null)
      {
        sessionError = e;
      }
    }
  }

  /**
   * Run method for the Session.
   * Loops waiting for buffers from the queue and sends them when available.
   */
  public void run()
  {
    isRunning.set(true);
    latch.countDown();
    if (debugEnabled())
    {
      TRACER.debugInfo(this.getName() + " starting.");
    }
    boolean needClosing = false;
    while (!closeInitiated)
    {
      byte[] buffer;
      try
      {
        buffer = sendQueue.take();
      }
      catch (InterruptedException ie)
      {
        break;
      }
      try
      {
        send(buffer);
      }
      catch (IOException e)
      {
        setSessionError(e);
        needClosing = true;
      }
    }
    isRunning.set(false);
    if (needClosing)
    {
      close();
    }
    if (debugEnabled())
    {
      TRACER.debugInfo(this.getName() + " stopped.");
    }
  }

  /**
   * This method can be called to wait until the session thread is
   * properly started.
   * @throws InterruptedException when interrupted
   */
  public void waitForStartup() throws InterruptedException
  {
    latch.await();
  }
}
