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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.plugins;

import static java.util.concurrent.TimeUnit.*;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.PluginCfg;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.types.OperationType;
import org.opends.server.types.operation.PluginOperation;
import org.opends.server.types.operation.PreParseAddOperation;
import org.opends.server.types.operation.PreParseDeleteOperation;
import org.opends.server.types.operation.PreParseModifyDNOperation;
import org.opends.server.types.operation.PreParseModifyOperation;

/**
 * A plugin which parks an operation at the pre-parse plugin point until the test releases
 * it, so that a test can hold a thread inside {@code Operation.run()} for as long as it
 * takes to do something else.
 * <p>
 * The pre-parse point is the one the replayed operations of a replication domain go
 * through - the pre-operation plugins are not invoked for synchronization operations - so
 * this is how a test holds a replay thread between the moment it starts applying a change
 * and the moment the change reaches the backend.
 * <p>
 * A pause is registered for one operation type on one entry: this plugin is enabled for
 * the whole unit test suite and for the internal operations too, so a pause which parked
 * every operation of a type would park whatever else the server happens to be doing - and
 * report that as the operation the test is waiting for. The park is bounded all the same:
 * a test which never releases costs {@link #MAX_PAUSE_IN_MS} rather than a server thread.
 */
public class PausePreParsePlugin extends DirectoryServerPlugin<PluginCfg>
{
  /**
   * How long an operation is parked when nothing releases it. A test which forgets to
   * release, or which fails before it could, must not leave a server thread parked for
   * the rest of the run.
   */
  public static final long MAX_PAUSE_IN_MS = 60000;

  /** One registered pause: the entry it applies to, and what reports and releases it. */
  private static final class Pause
  {
    /** The entry whose operations are parked. */
    private final DN target;
    /** Counted down by the first operation which reaches the pause. */
    private final CountDownLatch reached = new CountDownLatch(1);
    /** Counted down when the test releases the parked operations. */
    private final CountDownLatch released = new CountDownLatch(1);
    /** How many operations are parked here right now. */
    private final AtomicInteger parked = new AtomicInteger();

    private Pause(DN target)
    {
      this.target = target;
    }
  }

  /** The pauses registered per operation type. */
  private static final Map<OperationType, Pause> pauses = new ConcurrentHashMap<>();

  /**
   * Creates a new instance of this Directory Server plugin. Every plugin must implement a
   * default constructor (it is the only one that will be used to create plugins defined in
   * the configuration), and every plugin constructor must call <CODE>super()</CODE> as its
   * first element.
   */
  public PausePreParsePlugin()
  {
    super();
  }

  @Override
  public void initializePlugin(Set<PluginType> pluginTypes, PluginCfg configuration) throws ConfigException
  {
    // This plugin may only be used as a pre-parse plugin.
    for (PluginType t : pluginTypes)
    {
      switch (t)
      {
        case PRE_PARSE_ADD:
        case PRE_PARSE_DELETE:
        case PRE_PARSE_MODIFY:
        case PRE_PARSE_MODIFY_DN:
          // This is fine.
          break;
        default:
          throw new ConfigException(
              LocalizableMessage.raw("Invalid plugin type " + t + " for the pause pre-parse plugin."));
      }
    }
  }

  @Override
  public void finalizePlugin()
  {
    /*
     * A pause which outlived the test which registered it - one whose test timed out
     * before its finally could run - must not park the operations of the tests which
     * follow. This runs when the plugin is taken away, which is when the test server is
     * stopped or restarted.
     */
    for (OperationType operation : new ArrayList<>(pauses.keySet()))
    {
      release(operation);
    }
  }

  @Override
  public PluginResult.PreParse doPreParse(PreParseAddOperation addOperation)
  {
    pauseInternal(addOperation, addOperation.getRawEntryDN());
    return PluginResult.PreParse.continueOperationProcessing();
  }

  @Override
  public PluginResult.PreParse doPreParse(PreParseDeleteOperation deleteOperation)
  {
    pauseInternal(deleteOperation, deleteOperation.getRawEntryDN());
    return PluginResult.PreParse.continueOperationProcessing();
  }

  @Override
  public PluginResult.PreParse doPreParse(PreParseModifyOperation modifyOperation)
  {
    pauseInternal(modifyOperation, modifyOperation.getRawEntryDN());
    return PluginResult.PreParse.continueOperationProcessing();
  }

  @Override
  public PluginResult.PreParse doPreParse(PreParseModifyDNOperation modifyDNOperation)
  {
    pauseInternal(modifyDNOperation, modifyDNOperation.getRawEntryDN());
    return PluginResult.PreParse.continueOperationProcessing();
  }

  /**
   * Parks the operation if a pause is registered for its type and its entry, and reports
   * that it reached the pause so that the test knows the thread is now inside
   * {@code Operation.run()}.
   *
   * @param operation the operation which is being processed
   * @param rawEntryDN the entry it is on, as the request carries it
   */
  private void pauseInternal(PluginOperation operation, ByteString rawEntryDN)
  {
    final Pause pause = pauses.get(operation.getOperationType());
    if (pause == null || !pause.target.equals(parseOrNull(rawEntryDN)))
    {
      return;
    }
    /*
      * Counted before the pause is reported as reached, not after: a test which is told
      * that an operation got here goes on to read parkedCount(), and a count incremented
      * afterwards would leave that read racing this thread being scheduled.
      */
    pause.parked.incrementAndGet();
    pause.reached.countDown();
    try
    {
      pause.released.await(MAX_PAUSE_IN_MS, MILLISECONDS);
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
    }
    finally
    {
      pause.parked.decrementAndGet();
    }
  }

  /** Returns the DN the request carries, or {@code null} when it does not parse. */
  private static DN parseOrNull(ByteString rawEntryDN)
  {
    if (rawEntryDN == null)
    {
      return null;
    }
    try
    {
      return DN.valueOf(rawEntryDN.toString());
    }
    catch (LocalizedIllegalArgumentException e)
    {
      // Not the entry any pause is registered for, whatever it was meant to be.
      return null;
    }
  }

  /**
   * Registers a pause: the operations of the given type on the given entry are parked at
   * the pre-parse plugin point until {@link #release(OperationType)} is called.
   *
   * @param operation the type of operation to park
   * @param target the entry whose operations are parked
   */
  public static void pause(OperationType operation, DN target)
  {
    final Pause replaced = pauses.put(operation, new Pause(target));
    if (replaced != null)
    {
      // Whatever was parked on the pause this one replaces would stay parked for the rest
      // of MAX_PAUSE_IN_MS: the thread which registered it is not coming back for it.
      replaced.released.countDown();
    }
  }

  /**
   * Waits until an operation reached the pause registered for the given operation type.
   *
   * @param operation the type of operation which was registered
   * @param timeout how long to wait
   * @param unit the unit of the timeout
   * @return {@code true} when an operation reached the pause, {@code false} when the wait
   *         timed out
   * @throws IllegalStateException when no pause is registered for that operation type,
   *         which is a caller waiting for something nothing can report rather than an
   *         operation which is slow to come
   * @throws InterruptedException when the wait was interrupted
   */
  public static boolean awaitPaused(OperationType operation, long timeout, TimeUnit unit)
      throws InterruptedException
  {
    final Pause pause = pauses.get(operation);
    if (pause == null)
    {
      /*
       * Told apart from the timeout, and loudly: a pause registered for another operation
       * type - the whole of the mistake - would otherwise be reported as the operation
       * never coming, after the caller waited its whole budget out for it.
       */
      throw new IllegalStateException(
          "no pause is registered for " + operation + ": nothing can park on it, and nothing"
              + " will report that it did");
    }
    return pause.reached.await(timeout, unit);
  }

  /**
   * Returns how many operations are parked right now by the pause registered for the given
   * operation type.
   * <p>
   * A test which took something down while an operation was parked reads this to say that
   * it really did come down without waiting for it: the pause is only released by the test
   * itself, so an operation which is still parked here never finished.
   * <p>
   * To be read before the pause is released, and not after: the count is decremented by the
   * parked thread itself, on its way out, so a release does not bring it back to zero by the
   * time it returns.
   *
   * @param operation the type of operation which was registered
   * @return the number of operations parked right now, 0 when no pause is registered
   */
  public static int parkedCount(OperationType operation)
  {
    final Pause pause = pauses.get(operation);
    return pause != null ? pause.parked.get() : 0;
  }

  /**
   * Releases the operations parked by the pause registered for the given operation type,
   * and deregisters it so that the operations which follow are not parked. Does nothing
   * when no pause is registered, so that a test can release in a {@code finally} whatever
   * happened.
   *
   * @param operation the type of operation which was registered
   */
  public static void release(OperationType operation)
  {
    final Pause pause = pauses.remove(operation);
    if (pause != null)
    {
      pause.released.countDown();
    }
  }
}
