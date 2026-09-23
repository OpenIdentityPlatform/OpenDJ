/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.protocol;

import static org.opends.server.util.StaticUtils.*;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.DataFormatException;

import javax.net.ssl.SSLSocket;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.api.DirectoryThread;
import org.opends.server.types.HostPort;
import org.opends.server.util.StaticUtils;

/**
 * This class defines a replication session using TLS.
 */
public final class Session extends DirectoryThread implements Closeable
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * How long a close spends sending what the publisher thread left queued in {@code sendQueue},
   * in milliseconds.
   * <p>
   * The same 5 s as {@code DSRSShutdownSync.REPLICA_OFFLINE_GRACE_PERIOD}, which is how long a
   * shutdown is already willing to wait for one of these messages - the announcement that a
   * replica went offline - to be forwarded. A close has no reason to wait longer for it than the
   * shutdown which is waiting on the close. It is a budget per close, though, not per shutdown:
   * a shutdown closes its sessions one after another, so it can pay this once for each peer which
   * is alive and not reading.
   */
  private static final long DRAIN_BUDGET_MS = 5000;

  private final Socket plainSocket;
  private final SSLSocket secureSocket;
  private final InputStream plainInput;
  private final OutputStream plainOutput;
  private final byte[] rcvLengthBuf = new byte[8];
  private final String readableRemoteAddress;
  private final HostPort remoteAddress;
  private final HostPort localUrl;

  /** The time the last message published to this session. */
  private volatile long lastPublishTime;
  /** The time the last message was received on this session. */
  private volatile long lastReceiveTime;

  /**
   * Close and error guarded by stateLock: use a different lock to publish since
   * publishing can block, and we don't want to block while closing failed
   * connections.
   */
  private final Object stateLock = new Object();
  private volatile boolean closeInitiated;
  private Throwable sessionError;

  /**
   * Publish guarded by publishLock: use a full lock here so that we can
   * optionally publish StopMsg during close.
   */
  private final Lock publishLock = new ReentrantLock();

  /**
   * These do not need synchronization because they are only modified during the
   * initial single threaded handshake.
   */
  private short protocolVersion = ProtocolVersion.getCurrentVersion();
  /** Initially encrypted. */
  private boolean isEncrypted = true;

  /**
   * Use a buffered input stream to avoid too many system calls.
   */
  private BufferedInputStream input;

  /**
   * Use a buffered output stream in order to combine message length and content
   * into a single TCP packet if possible.
   */
  private BufferedOutputStream output;

  private final LinkedBlockingQueue<byte[]> sendQueue = new LinkedBlockingQueue<>(4000);
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
    if (logger.isTraceEnabled())
    {
      logger.trace(
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
    this.readableRemoteAddress = plainSocket.getRemoteSocketAddress().toString();
    this.remoteAddress = new HostPort(plainSocket.getInetAddress().getHostAddress(), plainSocket.getPort());
    this.localUrl = new HostPort(plainSocket.getLocalAddress().getHostName(), plainSocket.getLocalPort());
  }



  /**
   * This method is called when the session with the remote must be closed.
   * This object won't be used anymore after this method is called.
   * <p>
   * A message which was published on this session but which its publisher thread had not sent yet
   * is sent here rather than dropped, within the budget of {@link #DRAIN_BUDGET_MS}. See {@link
   * #sendWhatThePublisherLeftQueued()}.
   */
  @Override
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
      interrupt();
      join();
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    /*
     * Re-read the error rather than answer with the snapshot taken before the join: a publisher
     * whose send() failed while this thread was joining it recorded the error there, and what
     * follows - the drain and the StopMsg - is what must not be written to a socket which has
     * already failed. Reading it before the join left both writing to one, the drain naming its
     * own failure rather than the one the publisher had recorded.
     */
    synchronized (stateLock)
    {
      localSessionError = sessionError;
    }

    // Perform close outside of critical section.
    if (logger.isTraceEnabled())
    {
      if (localSessionError == null)
      {
        logger.trace(
            "Closing Session from %s to %s in %s",
            plainSocket.getLocalSocketAddress(),
            plainSocket.getRemoteSocketAddress(),
            stackTraceToSingleLineString(new Exception()));
      }
      else
      {
        logger.traceException(localSessionError,
            "Aborting Session from %s to %s in %s due to the following error",
            plainSocket.getLocalSocketAddress(),
            plainSocket.getRemoteSocketAddress(),
            stackTraceToSingleLineString(new Exception()));
      }
    }

    if (localSessionError != null)
    {
      /*
       * Nothing more is written to a session which already failed, neither what the publisher left
       * queued nor the StopMsg: writing more to it cannot work. The queue is still reported - the
       * publisher may have failed while this thread was joining it, with a backlog behind the
       * write which failed - and it is left without taking publishLock, so that a thread blocked
       * in a write of this socket is released by the close of the sockets below rather than
       * waited for.
       */
      isRunning.set(false);
      if (!sendQueue.isEmpty())
      {
        reportQueueNotSent(sendQueue.size(), "the session had already failed: " + localSessionError);
      }
      StaticUtils.close(plainSocket, secureSocket);
      return;
    }

    /*
     * The publisher thread has stopped and what it had not sent is still in the queue. Send it,
     * rather than let the close drop it: nothing publishes these again, and the StopMsg which
     * follows leaves the peer reading an orderly close with no sign that anything was missing.
     *
     * This thread is not the only one which can write the socket here - a ServerWriter or a
     * HeartbeatThread outlives this close. It is this thread, not run(), which takes the session
     * off the queueing branch of publish(), and it does so under publishLock and keeps the lock
     * across the queue and the StopMsg: until then a publish() concurrent with the close takes
     * the queueing branch, sees closeInitiated and returns, and from then on it takes the
     * synchronous branch and waits for the lock. Either way nothing newer is written between two
     * of the drained messages - see sendWhatThePublisherLeftQueued() below.
     */
    publishLock.lock();
    try
    {
      isRunning.set(false);
      sendWhatThePublisherLeftQueued();

      /*
       * Re-read again: a write of the drain which failed recorded its error, and the StopMsg must
       * not be written to a socket which has already failed either.
       */
      synchronized (stateLock)
      {
        localSessionError = sessionError;
      }

      // V4 protocol introduces a StopMsg to properly end communications.
      if (localSessionError == null
          && protocolVersion >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
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
    finally
    {
      publishLock.unlock();
    }

    StaticUtils.close(plainSocket, secureSocket);
  }



  /**
   * Sends the buffers the publisher thread had not sent when it stopped, so that a close of the
   * session does not drop them.
   * <p>
   * Called from {@link #close()} once the publisher has been joined, with {@code publishLock}
   * held. A queued message is already encoded for this peer's protocol version - {@link
   * #publish(ReplicationMsg)} did that before queueing it - so there is nothing to decide here
   * beyond how long to keep trying.
   * <p>
   * The whole queue goes out under {@code publishLock}, because the publisher is not the only
   * thread which writes this socket: a {@code ServerWriter} is joined only after the close which
   * gets here (ServerHandler.shutdown() closes the session before joining it, and ServerReader's
   * finally closes it before stopping the handler), and a {@code HeartbeatThread} is shut down
   * after it too. Their {@code publish()} takes the synchronous branch once the session is off
   * the queueing one, so without the lock a newer message could be written between two of these
   * older ones - which a peer replication server answers by dropping the older ones at debug
   * level, its log file refusing a record which would break its key ordering. The close takes
   * the session off the queueing branch under the same lock it holds here, so such a
   * {@code publish()} either returns at the door, having seen the close, or waits for the drain
   * and lands after it. The wait for the lock itself is not part of the budget below, no more
   * than it is for the {@code StopMsg} which follows.
   * <p>
   * The budget bounds how many messages a close spends on a peer which is reading slowly. A peer
   * which is reading pays none of it; a peer which answers with a reset pays one failed write. It
   * is checked between messages, so a single write which blocks past the budget still runs to
   * completion - for a peer which has stopped reading, or which vanished without a reset, that is
   * for as long as TCP keeps the connection alive: bounding it needs a non-blocking socket, which
   * this session is not. The budget is per close, and a shutdown closes its sessions one after
   * another - ReplicationServerDomain.stopAllServers() stops each handler in turn on one thread -
   * so a domain with several peers which are alive and not reading pays it once per such peer.
   * On the road which does not shut the whole server down the wait is paid under the lock of the
   * replication domain - ReplicationServerDomain.stopServer() holds it across the handler
   * shutdown which closes this session - where a handshake meanwhile waiting on that lock times
   * out and the broker retries. What is given up on is reported rather than dropped in silence,
   * that being the part of this which cost the most to diagnose.
   */
  private void sendWhatThePublisherLeftQueued()
  {
    final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(DRAIN_BUDGET_MS);
    byte[] buffer;
    while ((buffer = sendQueue.poll()) != null)
    {
      if (System.nanoTime() - deadline >= 0)
      {
        reportQueueNotSent(sendQueue.size() + 1, "the peer did not read them within "
            + DRAIN_BUDGET_MS + " ms");
        return;
      }
      try
      {
        send(buffer);
      }
      catch (final IOException e)
      {
        /*
         * send() has recorded the error; the rest of the queue cannot go out either. The
         * exception is named rather than traced: this is reached whenever a peer which has
         * announced it is leaving closes before the drain reaches it, where what the write
         * failed with is the whole of what a reader of the log needs - a directory server
         * re-reads these from the changelog when it reconnects, a replication server does not.
         */
        reportQueueNotSent(sendQueue.size() + 1,
            "the write failed with " + e.getClass().getName() + ": " + e.getMessage());
        return;
      }
    }
  }

  /** Says which messages a close could not hand to the peer, and why. */
  private void reportQueueNotSent(final int count, final String reason)
  {
    logger.warn(LocalizableMessage.raw(
        "The replication session %s was closed with %d message(s) which had been published on it "
        + "but not yet sent, and the peer was not told about them: %s",
        getName(), count, reason));
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
  public HostPort getLocalUrl()
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
   * Retrieve the IP address and port of the remote server.
   *
   * @return The IP address and port of the remote server.
   */
  public HostPort getRemoteAddress()
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
    if (buffer == null)
    {
      // skip anything that cannot be encoded for this peer.
      return;
    }
    if (isRunning.get())
    {
      while (!closeInitiated)
      {
        try
        {
          // Avoid blocking forever so that we can check for session closure.
          if (sendQueue.offer(buffer, 100, TimeUnit.MILLISECONDS))
          {
            return;
          }
        }
        catch (final InterruptedException e)
        {
          setSessionError(e);
          throw new IOException(e.getMessage());
        }
      }
    }
    else
    {
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
      /*
       * Let's start the stop-watch before waiting on read for the heartbeat
       * check to be operational.
       */
      lastReceiveTime = System.currentTimeMillis();

      // Read the first 8 bytes containing the packet length.
      read(rcvLengthBuf);
      final int totalLength = Integer.parseInt(new String(rcvLengthBuf), 16);

      try
      {
        final byte[] buffer = new byte[totalLength];
        read(buffer);

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
    catch (final IOException | DataFormatException | NotSupportedOldVersionPDUException | RuntimeException e)
    {
      setSessionError(e);
      throw e;
    }
  }

  private void read(byte[] buffer) throws IOException
  {
    final int totalLength = buffer.length;
    int length = 0;
    while (length < totalLength)
    {
      final int read = input.read(buffer, length, totalLength - length);
      if (read == -1)
      {
        lastReceiveTime = 0;
        throw new IOException("no more data");
      }
      length += read;
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
  @Override
  public void run()
  {
    isRunning.set(true);
    latch.countDown();
    if (logger.isTraceEnabled())
    {
      logger.trace(getName() + " starting.");
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
    /*
     * A close clears the flag itself, under publishLock, once it has joined this thread - see
     * close(). Clearing it here would open a window between the end of this thread and the drain
     * of the close, in which a publish() takes the synchronous branch and writes a newer message
     * ahead of the queue. Only a loop which ended without a close - an interrupt from elsewhere -
     * clears it here, so that publish() does not go on queueing onto a queue nobody sends.
     */
    if (!closeInitiated)
    {
      isRunning.set(false);
    }
    if (needClosing)
    {
      close();
    }
    if (logger.isTraceEnabled())
    {
      logger.trace(getName() + " stopped.");
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
