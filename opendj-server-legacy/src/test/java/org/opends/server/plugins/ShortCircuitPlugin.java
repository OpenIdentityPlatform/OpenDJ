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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.plugins;



import static org.opends.server.util.CollectionUtils.*;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.server.PluginCfg;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.controls.ControlDecoder;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.OperationType;
import org.opends.server.types.operation.*;

/**
 * This class defines a very simple plugin that causes request processing to end
 * immediately and send a specific result code to the client.  It will be
 * triggered by a control contained in the client request, and may be invoked
 * during either pre-parse or pre-operation processing.  Short circuits can
 * also be registered for operations regardless of controls.
 */
public class ShortCircuitPlugin
       extends DirectoryServerPlugin<PluginCfg>
{
  /**
   * The OID for the short circuit request control, which is used to flag
   * operations that should cause the operation processing to end immediately.
   */
  public static final String OID_SHORT_CIRCUIT_REQUEST =
       "1.3.6.1.4.1.26027.1.999.3";

  /**
   * The control used by this plugin.
   */
  public static class ShortCircuitRequestControl extends Control
  {
    /**
     * ControlDecoder implementation to decode this control from a ByteString.
     */
    private static final class Decoder
        implements ControlDecoder<ShortCircuitRequestControl>
    {
      /** {@inheritDoc} */
      @Override
      public ShortCircuitRequestControl decode(boolean isCritical, ByteString value)
          throws DirectoryException
      {
        ASN1Reader reader = ASN1.getReader(value);

        try
        {
          reader.readStartSequence();
          int resultCode = (int)reader.readInteger();
          String section = reader.readOctetStringAsString();
          reader.readEndSequence();

          return new ShortCircuitRequestControl(isCritical,
              resultCode, section);
        }
        catch (Exception e)
        {
          // TODO: Need a better message
          throw new DirectoryException(ResultCode.PROTOCOL_ERROR, null, e);
        }
      }

      @Override
      public String getOID()
      {
        return OID_SHORT_CIRCUIT_REQUEST;
      }

    }

    /**
     * The Control Decoder that can be used to decode this control.
     */
    public static final ControlDecoder<ShortCircuitRequestControl> DECODER =
      new Decoder();


    private int resultCode;
    private String section;

    /**
     * Constructs a new control of this class.
     *
     * @param isCritical
     *          Indicates whether support for this control should be considered
     *          a critical part of the server processing.
     * @param resultCode
     *          The result code to return to the client.
     * @param section
     *          The section to use to determine when to short circuit.
     */
    public ShortCircuitRequestControl(boolean isCritical, int resultCode,
                                      String section)
    {
      super(OID_SHORT_CIRCUIT_REQUEST, isCritical);
      this.resultCode = resultCode;
      this.section = section;
    }

    /**
     * Writes this control's value to an ASN.1 writer. The value (if any)
     * must be written as an ASN1OctetString.
     *
     * @param writer The ASN.1 writer to use.
     * @throws IOException If a problem occurs while writing to the stream.
     */
    @Override
    protected void writeValue(ASN1Writer writer) throws IOException {
      writer.writeStartSequence(ASN1.UNIVERSAL_OCTET_STRING_TYPE);
      writer.writeStartSequence();
      writer.writeInteger(resultCode);
      writer.writeOctetString(section);
      writer.writeEndSequence();
      writer.writeEndSequence();
    }

    /**
     * Retrieves the resultCode.
     *
     * @return The resultCode.
     */
    public int getResultCode()
    {
      return resultCode;
    }

    /**
     * Retrieves the section.
     *
     * @return The section.
     */
    public String getSection()
    {
      return section;
    }
  }



  /**
   * Creates a new instance of this Directory Server plugin.  Every
   * plugin must implement a default constructor (it is the only one
   * that will be used to create plugins defined in the
   * configuration), and every plugin constructor must call
   * <CODE>super()</CODE> as its first element.
   */
  public ShortCircuitPlugin()
  {
    super();
  }



  /** {@inheritDoc} */
  @Override
  public void initializePlugin(Set<PluginType> pluginTypes,
                               PluginCfg configuration)
         throws ConfigException
  {
    // This plugin may only be used as a pre-parse or pre-operation plugin.
    for (PluginType t : pluginTypes)
    {
      switch (t)
      {
        case PRE_PARSE_ABANDON:
        case PRE_PARSE_ADD:
        case PRE_PARSE_BIND:
        case PRE_PARSE_COMPARE:
        case PRE_PARSE_DELETE:
        case PRE_PARSE_EXTENDED:
        case PRE_PARSE_MODIFY:
        case PRE_PARSE_MODIFY_DN:
        case PRE_PARSE_SEARCH:
        case PRE_PARSE_UNBIND:
        case PRE_OPERATION_ADD:
        case PRE_OPERATION_BIND:
        case PRE_OPERATION_COMPARE:
        case PRE_OPERATION_DELETE:
        case PRE_OPERATION_EXTENDED:
        case PRE_OPERATION_MODIFY:
        case PRE_OPERATION_MODIFY_DN:
        case PRE_OPERATION_SEARCH:
          // This is fine.
          break;
        default:
          throw new ConfigException(LocalizableMessage.raw("Invalid plugin type " + t +
                                    " for the short circuit plugin."));
      }
    }
  }



  /** {@inheritDoc} */
  @Override
  public void finalizePlugin()
  {
    /*
     * A park which outlives the test which took it holds a replay thread of this server,
     * and every replayed operation queued behind it, for as long as this plugin is
     * loaded: the map is static and nothing but the test itself removes an entry from it.
     */
    for (ParkedReplay park : parks.values())
    {
      park.deregister();
    }
    parks.clear();
  }



  /** {@inheritDoc} */
  @Override
  public PluginResult.PreParse
         doPreParse(PreParseAbandonOperation abandonOperation)
  {
    int resultCode = shortCircuitInternal(abandonOperation, "PreParse");
    if (resultCode >= 0)
    {
      return PluginResult.PreParse.stopProcessing(
          ResultCode.valueOf(resultCode),
          LocalizableMessage.raw("Short-circuit in pre-parse"));
    }
    else
    {
      return PluginResult.PreParse.continueOperationProcessing();
    }
  }



  /** {@inheritDoc} */
  @Override
  public PluginResult.PreParse doPreParse(PreParseAddOperation addOperation)
  {
    int resultCode = shortCircuitInternal(addOperation, "PreParse");
    if (resultCode >= 0)
    {
      return PluginResult.PreParse.stopProcessing(
          ResultCode.valueOf(resultCode),
          LocalizableMessage.raw("Short-circuit in pre-parse"));
    }
    else
    {
      return PluginResult.PreParse.continueOperationProcessing();
    }
  }



  /** {@inheritDoc} */
  @Override
  public PluginResult.PreParse doPreParse(PreParseBindOperation bindOperation)
  {
    int resultCode = shortCircuitInternal(bindOperation, "PreParse");
    if (resultCode >= 0)
    {
      return PluginResult.PreParse.stopProcessing(
          ResultCode.valueOf(resultCode),
          LocalizableMessage.raw("Short-circuit in pre-parse"));
    }
    else
    {
      return PluginResult.PreParse.continueOperationProcessing();
    }
  }



  /** {@inheritDoc} */
  @Override
  public PluginResult.PreParse
       doPreParse(PreParseCompareOperation compareOperation)
  {
    int resultCode = shortCircuitInternal(compareOperation, "PreParse");
    if (resultCode >= 0)
    {
      return PluginResult.PreParse.stopProcessing(
          ResultCode.valueOf(resultCode),
          LocalizableMessage.raw("Short-circuit in pre-parse"));
    }
    else
    {
      return PluginResult.PreParse.continueOperationProcessing();
    }
  }



  /** {@inheritDoc} */
  @Override
  public PluginResult.PreParse
       doPreParse(PreParseDeleteOperation deleteOperation)
  {
    int resultCode = shortCircuitInternal(deleteOperation, "PreParse");
    if (resultCode >= 0)
    {
      return PluginResult.PreParse.stopProcessing(
          ResultCode.valueOf(resultCode),
          LocalizableMessage.raw("Short-circuit in pre-parse"));
    }
    else
    {
      return PluginResult.PreParse.continueOperationProcessing();
    }
  }



  /** {@inheritDoc} */
  @Override
  public PluginResult.PreParse
       doPreParse(PreParseExtendedOperation extendedOperation)
  {
    int resultCode = shortCircuitInternal(extendedOperation, "PreParse");
    if (resultCode >= 0)
    {
      return PluginResult.PreParse.stopProcessing(
          ResultCode.valueOf(resultCode),
          LocalizableMessage.raw("Short-circuit in pre-parse"));
    }
    else
    {
      return PluginResult.PreParse.continueOperationProcessing();
    }
  }



  /** {@inheritDoc} */
  @Override
  public PluginResult.PreParse
       doPreParse(PreParseModifyOperation modifyOperation)
  {
    int resultCode = shortCircuitInternal(modifyOperation, "PreParse");
    if (resultCode >= 0)
    {
      return PluginResult.PreParse.stopProcessing(
          ResultCode.valueOf(resultCode),
          LocalizableMessage.raw("Short-circuit in pre-parse"));
    }
    else
    {
      return PluginResult.PreParse.continueOperationProcessing();
    }
  }



  /** {@inheritDoc} */
  @Override
  public PluginResult.PreParse
       doPreParse(PreParseModifyDNOperation modifyDNOperation)
  {
    int resultCode = shortCircuitInternal(modifyDNOperation, "PreParse");
    if (resultCode >= 0)
    {
      return PluginResult.PreParse.stopProcessing(
          ResultCode.valueOf(resultCode),
          LocalizableMessage.raw("Short-circuit in pre-parse"));
    }
    else
    {
      return PluginResult.PreParse.continueOperationProcessing();
    }
  }



  /** {@inheritDoc} */
  @Override
  public PluginResult.PreParse
       doPreParse(PreParseSearchOperation searchOperation)
  {
    int resultCode = shortCircuitInternal(searchOperation, "PreParse");
    if (resultCode >= 0)
    {
      return PluginResult.PreParse.stopProcessing(
          ResultCode.valueOf(resultCode),
          LocalizableMessage.raw("Short-circuit in pre-parse"));
    }
    else
    {
      return PluginResult.PreParse.continueOperationProcessing();
    }
  }



  /** {@inheritDoc} */
  @Override
  public PluginResult.PreParse
       doPreParse(PreParseUnbindOperation unbindOperation)
  {
    int resultCode = shortCircuitInternal(unbindOperation, "PreParse");
    if (resultCode >= 0)
    {
      return PluginResult.PreParse.stopProcessing(
          ResultCode.valueOf(resultCode),
          LocalizableMessage.raw("Short-circuit in pre-parse"));
    }
    else
    {
      return PluginResult.PreParse.continueOperationProcessing();
    }
  }



  /** {@inheritDoc} */
  @Override
  public PluginResult.PreOperation
       doPreOperation(PreOperationAddOperation addOperation)
  {
    int resultCode = shortCircuitInternal(addOperation, "PreOperation");
    if (resultCode >= 0)
    {
      return PluginResult.PreOperation.stopProcessing(
          ResultCode.valueOf(resultCode),
          LocalizableMessage.raw("Short-circuit in pre-operation"));
    }
    else
    {
      return PluginResult.PreOperation.continueOperationProcessing();
    }
  }



  /** {@inheritDoc} */
  @Override
  public PluginResult.PreOperation
       doPreOperation(PreOperationBindOperation bindOperation)
  {
    int resultCode = shortCircuitInternal(bindOperation, "PreOperation");
    if (resultCode >= 0)
    {
      return PluginResult.PreOperation.stopProcessing(
          ResultCode.valueOf(resultCode),
          LocalizableMessage.raw("Short-circuit in pre-operation"));
    }
    else
    {
      return PluginResult.PreOperation.continueOperationProcessing();
    }
  }



  /** {@inheritDoc} */
  @Override
  public PluginResult.PreOperation
       doPreOperation(PreOperationCompareOperation compareOperation)
  {
    int resultCode = shortCircuitInternal(compareOperation, "PreOperation");
    if (resultCode >= 0)
    {
      return PluginResult.PreOperation.stopProcessing(
          ResultCode.valueOf(resultCode),
          LocalizableMessage.raw("Short-circuit in pre-operation"));
    }
    else
    {
      return PluginResult.PreOperation.continueOperationProcessing();
    }
  }



  /** {@inheritDoc} */
  @Override
  public PluginResult.PreOperation
       doPreOperation(PreOperationDeleteOperation deleteOperation)
  {
    int resultCode = shortCircuitInternal(deleteOperation, "PreOperation");
    if (resultCode >= 0)
    {
      return PluginResult.PreOperation.stopProcessing(
          ResultCode.valueOf(resultCode),
          LocalizableMessage.raw("Short-circuit in pre-operation"));
    }
    else
    {
      return PluginResult.PreOperation.continueOperationProcessing();
    }
  }



  /** {@inheritDoc} */
  @Override
  public PluginResult.PreOperation
       doPreOperation(PreOperationExtendedOperation extendedOperation)
  {
    int resultCode = shortCircuitInternal(extendedOperation, "PreOperation");
    if (resultCode >= 0)
    {
      return PluginResult.PreOperation.stopProcessing(
          ResultCode.valueOf(resultCode),
          LocalizableMessage.raw("Short-circuit in pre-operation"));
    }
    else
    {
      return PluginResult.PreOperation.continueOperationProcessing();
    }
  }



  /** {@inheritDoc} */
  @Override
  public PluginResult.PreOperation
       doPreOperation(PreOperationModifyOperation modifyOperation)
  {
    int resultCode = shortCircuitInternal(modifyOperation, "PreOperation");
    if (resultCode >= 0)
    {
      return PluginResult.PreOperation.stopProcessing(
          ResultCode.valueOf(resultCode),
          LocalizableMessage.raw("Short-circuit in pre-operation"));
    }
    else
    {
      return PluginResult.PreOperation.continueOperationProcessing();
    }
  }



  /** {@inheritDoc} */
  @Override
  public PluginResult.PreOperation
       doPreOperation(PreOperationModifyDNOperation modifyDNOperation)
  {
    int resultCode = shortCircuitInternal(modifyDNOperation, "PreOperation");
    if (resultCode >= 0)
    {
      return PluginResult.PreOperation.stopProcessing(
          ResultCode.valueOf(resultCode),
          LocalizableMessage.raw("Short-circuit in pre-operation"));
    }
    else
    {
      return PluginResult.PreOperation.continueOperationProcessing();
    }
  }



  /** {@inheritDoc} */
  @Override
  public PluginResult.PreOperation
       doPreOperation(PreOperationSearchOperation searchOperation)
  {
    int resultCode = shortCircuitInternal(searchOperation, "PreOperation");
    if (resultCode >= 0)
    {
      return PluginResult.PreOperation.stopProcessing(
          ResultCode.valueOf(resultCode),
          LocalizableMessage.raw("Short-circuit in pre-operation"));
    }
    else
    {
      return PluginResult.PreOperation.continueOperationProcessing();
    }
  }



  /**
   * Looks for a short-circuit request control in the operation and if one is
   * found with the correct section then generate the appropriate result.
   *
   * @param  operation  The operation to be processed.
   * @param  section    The section to match in the control value.
   *
   * @return  The result code that should be immediately sent to the client, or
   *          -1 if operation processing should continue as normal.
   */
  private int shortCircuitInternal(PluginOperation operation, String section)
  {
    try
    {
      ShortCircuitRequestControl control =
          operation.getRequestControl(ShortCircuitRequestControl.DECODER);
      if (control != null && section.equalsIgnoreCase(control.getSection()))
      {
        return control.resultCode;
      }
    }
    catch (Exception e)
    {
      System.err.println("***** ERROR:  Could not decode short circuit " +
          "control value:  " + e);
      e.printStackTrace();
      return -1;
    }

    // Check for registered short circuits.
    final String key = keyFor(operation.getOperationType(), section);
    Integer resultCode = shortCircuits.get(key);
    if (resultCode != null)
    {
      final int reached = shortCircuitCounts.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
      final Integer maxTimes = shortCircuitLimits.get(key);
      if (maxTimes == null || reached <= maxTimes)
      {
        return resultCode;
      }
      // The short circuit was applied as many times as it was asked for: from now on the
      // operations are let through, which is how a transient failure is simulated.
    }

    /*
     * A parked replay is held here, which is inside the run() of the operation and before
     * anything of the backend was taken: the thread which is replaying a change sits on
     * this monitor while it still owns that change, which is what lets a test act on the
     * thread rather than race it. It is consulted last, so that a park never takes an
     * operation away from a control or from a registered short circuit.
     */
    if (operation.isSynchronizationOperation())
    {
      final ParkedReplay park = parks.get(key);
      if (park != null && park.parks(operation))
      {
        final int parkResultCode = park.hold();
        if (parkResultCode >= 0)
        {
          return parkResultCode;
        }
      }
    }

    // If we've gotten here, then we shouldn't short-circuit the operation
    // processing.
    return -1;
  }



  /**
   * Creates a short circuit request control with the specified result code and
   * section.
   *
   * @param  resultCode  The result code to return to the client.
   * @param  section     The section to use to determine when to short circuit.
   *
   * @return  The appropriate short circuit request control.
   */
  public static Control createShortCircuitControl(int resultCode,
                                                  String section)
  {
    return new ShortCircuitRequestControl(false, resultCode, section);
  }



  /**
   * Retrieves a list containing a short circuit control with the specified
   * result code and section.
   *
   * @param  resultCode  The result code to return to the client.
   * @param  section     The section to use to determine when to short circuit.
   *
   * @return  A list containing the appropriate short circuit request control.
   */
  public static List<Control> createShortCircuitControlList(int resultCode, String section)
  {
    return newArrayList(createShortCircuitControl(resultCode, section));
  }

  /** Registered short circuits for operations regardless of controls. */
  private static Map<String, Integer> shortCircuits = new ConcurrentHashMap<>();

  /** How many times a registered short circuit was reached. */
  private static final Map<String, AtomicInteger> shortCircuitCounts = new ConcurrentHashMap<>();

  /** How many times a registered short circuit must be applied, when it is limited. */
  private static final Map<String, Integer> shortCircuitLimits = new ConcurrentHashMap<>();

  /**
   * Returns how many times the short circuit registered for the given operation type and
   * plugin point was reached. A short circuit registered for a limited number of times is
   * counted as reached by the operations it let through once that number was used up.
   *
   * @param operation The type of operation the short circuit applies to.
   * @param section The plugin point the short circuit applies to.
   * @return the number of operations which reached the short circuit
   */
  public static int getShortCircuitCount(OperationType operation, String section)
  {
    final AtomicInteger count = shortCircuitCounts.get(keyFor(operation, section));
    return count != null ? count.get() : 0;
  }


  /**
   * Register a short circuit for the given operation type and plugin point.
   * @param operation The type of operation the short circuit applies to.
   * @param section The plugin point the short circuit applies to.
   * @param resultCode The result code to be returned for the short circuit.
   */
  public static void registerShortCircuit(OperationType operation, String section, int resultCode)
  {
    final String key = keyFor(operation, section);
    // This registration applies to every operation, and it counts from zero: a limit or
    // a count left behind by a previous registration is not part of it.
    shortCircuitCounts.remove(key);
    shortCircuitLimits.remove(key);
    shortCircuits.put(key, resultCode);
  }

  /**
   * Register a short circuit which only applies to the given number of operations, the
   * ones which follow being let through: this is how a transient failure is simulated.
   *
   * @param operation The type of operation the short circuit applies to.
   * @param section The plugin point the short circuit applies to.
   * @param resultCode The result code to be returned for the short circuit.
   * @param maxTimes How many operations must be short circuited.
   */
  public static void registerShortCircuit(OperationType operation, String section, int resultCode, int maxTimes)
  {
    final String key = keyFor(operation, section);
    shortCircuitCounts.remove(key);
    shortCircuitLimits.put(key, maxTimes);
    shortCircuits.put(key, resultCode);
  }

  /**
   * Deregister a short circuit for the given operation type and plugin point.
   * @param operation The type of operation the short circuit applies to.
   * @param section The plugin point the short circuit applies to.
   */
  public static void deregisterShortCircuit(OperationType operation, String section)
  {
    final String key = keyFor(operation, section);
    shortCircuits.remove(key);
    shortCircuitLimits.remove(key);
    // The count belongs to the registration which is being removed: a test which counts
    // the operations it short circuits must not inherit the count of the previous one.
    shortCircuitCounts.remove(key);
  }

  /** Registered parks for the replayed operations, keyed like the short circuits. */
  private static final Map<String, ParkedReplay> parks = new ConcurrentHashMap<>();

  /**
   * Holds the replayed operations of one type where they are, one at a time, until the
   * test lets each of them go.
   * <p>
   * The hold is taken at a plugin point which runs inside {@code op.run()}, so the thread
   * which is replaying a change is stopped while it still owns that change: a test can
   * then do something to that thread - stop it, disable its domain - and know the change
   * is in flight rather than hope it is. Nothing of the backend has been taken at that
   * point, so a parked operation blocks the replay and nothing else.
   */
  public static final class ParkedReplay
  {
    /**
     * The value which lets the operation run rather than short circuit it.
     * <p>
     * {@code ResultCode.UNDEFINED} is registered on {@code -1} as well, so
     * {@code release(ResultCode.UNDEFINED.intValue())} lets the operation run instead of
     * making it report that code - the same hole {@code registerShortCircuit(-1)} has.
     * No caller has a use for it, and a park releases with a real result code or with
     * none at all.
     */
    private static final int LET_THROUGH = -1;

    /**
     * How long an operation is held before this park gives up on the test which took it.
     * <p>
     * It is far longer than any release a test waits for - the fixture itself waits a
     * minute for a park - and it exists for the test which never releases at all: a park
     * leaked by a method killed on a timeout would otherwise hold a replay thread of this
     * server, and every replayed operation queued behind it, for the life of the JVM.
     */
    private static final long MAX_HOLD_IN_MS = TimeUnit.MINUTES.toMillis(5);

    private final String key;
    /** Which of the replayed operations of that type this park is for. */
    private final Predicate<PluginOperation> parked;
    private final Object lock = new Object();
    /** Whether an operation is parked right now. */
    private boolean occupied;
    /**
     * The thread of the operation which parked last. It is never cleared, so that a test
     * which waited for a park is handed the thread of that park even when the operation
     * has left the park since - a park which is let go of by {@link #deregister()}, or by
     * the thread it holds being interrupted, would otherwise hand out no thread at all
     * and have an assertion on which thread replays the change pass without asserting it.
     */
    private Thread lastParkedThread;
    /** How many operations were parked, which is what tells one park from the next. */
    private int parkedOperations;
    /** How many of them the test has waited for already. */
    private int awaitedOperations;
    private boolean released;
    private int releasedResultCode;
    private boolean deregistered;

    private ParkedReplay(String key, Predicate<PluginOperation> parked)
    {
      this.key = key;
      this.parked = parked;
    }

    /** Returns whether the provided operation is one this park is for. */
    private boolean parks(PluginOperation operation)
    {
      return parked.test(operation);
    }

    /**
     * Parks the calling operation until the test releases it. Runs on the thread which is
     * replaying the change.
     *
     * @return the result code the operation must be short circuited with, or a negative
     *         value to let it run
     */
    private int hold()
    {
      final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(MAX_HOLD_IN_MS);
      synchronized (lock)
      {
        // One operation at a time, so that a release belongs to the operation the test
        // waited for rather than to whichever of them the scheduler let in first.
        while (occupied && !deregistered)
        {
          if (!waitOnLock(deadline))
          {
            return LET_THROUGH;
          }
        }
        if (deregistered)
        {
          return LET_THROUGH;
        }
        occupied = true;
        lastParkedThread = Thread.currentThread();
        parkedOperations++;
        released = false;
        lock.notifyAll();
        try
        {
          while (!released && !deregistered)
          {
            if (!waitOnLock(deadline))
            {
              return LET_THROUGH;
            }
          }
          return released ? releasedResultCode : LET_THROUGH;
        }
        finally
        {
          occupied = false;
          lock.notifyAll();
        }
      }
    }

    /**
     * Waits on the monitor until the provided deadline, reporting whether waiting can go
     * on. A deadline which has passed gives up on this park altogether rather than only
     * on the operation which reached it: the operations behind it would each pay the
     * whole wait again otherwise.
     */
    private boolean waitOnLock(long deadlineInNanos)
    {
      final long leftInNanos = deadlineInNanos - System.nanoTime();
      if (leftInNanos <= 0)
      {
        giveUpOnTheTest();
        return false;
      }
      try
      {
        // Rounded up, so that a budget shorter than a millisecond is still waited out
        // rather than truncated to a wait with no timeout at all.
        lock.wait(TimeUnit.NANOSECONDS.toMillis(leftInNanos + 999999L));
        return true;
      }
      catch (InterruptedException e)
      {
        // Whatever wants this thread to stop wins over the park: let the operation run
        // rather than hold a thread which is being taken down.
        Thread.currentThread().interrupt();
        return false;
      }
    }

    /** Stops parking anything and says so, after a test held an operation for too long. */
    private void giveUpOnTheTest()
    {
      System.err.println("***** ERROR:  a replayed operation was parked on " + key
          + " for " + MAX_HOLD_IN_MS + " ms and was never released:  the test which took"
          + " this park left it behind.  Letting the operation run and parking no more.");
      deregister();
    }

    /**
     * Waits for a replayed operation which was not waited for yet to be parked, and
     * reports which thread is replaying it. The operations are parked one at a time, so
     * that thread is the one which was parked when this returns; the thread of the last
     * park is reported when several of them were let go of without being waited for.
     *
     * @param timeout how long to wait for it
     * @param unit the unit of the timeout
     * @return the thread which is replaying the parked operation
     * @throws InterruptedException if this thread is interrupted while waiting
     * @throws TimeoutException if no operation was parked in time
     * @throws IllegalStateException if this park is gone, so that nothing can be parked
     *           on it any more
     */
    public Thread awaitParked(long timeout, TimeUnit unit)
        throws InterruptedException, TimeoutException
    {
      final long deadline = System.nanoTime() + unit.toNanos(timeout);
      synchronized (lock)
      {
        while (parkedOperations <= awaitedOperations)
        {
          if (deregistered)
          {
            // Waiting out the budget here would report a timeout naming the operations
            // which never parked, rather than the park which cannot park them any more.
            throw new IllegalStateException("the park on " + key + " is gone - it was"
                + " deregistered, or displaced by another park of the same operations -"
                + " so no replayed operation will be parked on it again");
          }
          final long leftInNanos = deadline - System.nanoTime();
          if (leftInNanos <= 0)
          {
            throw new TimeoutException("no replayed operation was parked on " + key
                + " within " + timeout + " " + unit);
          }
          // Rounded up, so that a budget shorter than a millisecond is still waited out
          // rather than truncated to a wait with no timeout at all.
          lock.wait(TimeUnit.NANOSECONDS.toMillis(leftInNanos + 999999L));
        }
        awaitedOperations = parkedOperations;
        return lastParkedThread;
      }
    }

    /**
     * Lets the parked operation run. Valid once {@link #awaitParked} has reported that
     * operation: see there for what a release which arrives before it costs.
     */
    public void release()
    {
      release(LET_THROUGH);
    }

    /**
     * Lets the parked operation go, short circuiting it with the provided result code.
     * <p>
     * Valid once {@link #awaitParked} has reported the operation being released. A
     * release which arrives before an operation is parked is wiped by the park it was
     * meant for - a park starts out unreleased - and that operation then waits for a
     * release which has already been spent.
     *
     * @param resultCode the result code the operation must report
     */
    public void release(int resultCode)
    {
      synchronized (lock)
      {
        if (!occupied)
        {
          throw new IllegalStateException("nothing is parked on " + key + " to release:"
              + " a release is spent by the park it arrives before, and the operation"
              + " which parks next then waits for one which has already been given");
        }
        released = true;
        releasedResultCode = resultCode;
        lock.notifyAll();
      }
    }

    /**
     * Stops parking the replayed operations and lets go of the one which is parked, if
     * any. A test must call this however it ends, or it leaves a replay thread of this
     * server parked for good.
     */
    public void deregister()
    {
      parks.remove(key, this);
      synchronized (lock)
      {
        deregistered = true;
        lock.notifyAll();
      }
    }
  }

  /**
   * Parks the replayed operations of the given type at the given plugin point, until the
   * test releases each of them.
   *
   * @param operation the type of operation to park
   * @param section the plugin point to park them at, which can only be {@code PreParse}
   * @param parked which of them to park - the change a test acts on rather than whatever
   *          of that type reaches this point first, which is somebody else's change as
   *          soon as more than one of them is in flight
   * @return the park, which the test must {@link ParkedReplay#deregister()} when it is
   *         done with it
   * @throws IllegalArgumentException if asked for any plugin point but {@code PreParse}
   */
  public static ParkedReplay parkReplayedOperations(
      OperationType operation, String section, Predicate<PluginOperation> parked)
  {
    if (!"PreParse".equalsIgnoreCase(section))
    {
      /*
       * The pre-operation plugins are not invoked for synchronization operations at all,
       * so a park anywhere else is never reached: the test which took it would wait out
       * its whole budget for an operation which cannot park, and be told that none did
       * rather than that none could.
       */
      throw new IllegalArgumentException("replayed operations can only be parked at"
          + " PreParse, which is the only plugin point they reach, not at " + section);
    }
    final String key = keyFor(operation, section);
    final ParkedReplay park = new ParkedReplay(key, parked);
    final ParkedReplay previous = parks.put(key, park);
    if (previous != null)
    {
      // A park a test left behind holds a replay thread of this server for good once the
      // map stops pointing at it: let go of it rather than lose the last reference to it.
      previous.deregister();
    }
    return park;
  }

  /** Returns the key a short circuit or a park of the given operations is kept under. */
  private static String keyFor(OperationType operation, String section)
  {
    return operation + "/" + section.toLowerCase(Locale.ROOT);
  }
}
