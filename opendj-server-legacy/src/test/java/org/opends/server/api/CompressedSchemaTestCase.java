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
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.api;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.api.CompressedSchema.MAX_LOAD_ID;
import static org.opends.server.util.StaticUtils.bytesToHexNoSpace;
import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.ByteSequenceReader;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests that a compressed schema never hands out a token whose definition was not persisted, and
 * that a token it holds no definition for is reported rather than let out of the decode path as an
 * unchecked exception.
 */
@SuppressWarnings("javadoc")
public class CompressedSchemaTestCase extends APITestCase
{
  /** A compressed schema whose store can be made to fail, recording what it did persist. */
  private static final class TestCompressedSchema extends CompressedSchema
  {
    private final Map<Integer, String> storedAttributes = new LinkedHashMap<>();
    private final Map<Integer, Collection<String>> storedObjectClasses = new LinkedHashMap<>();
    private int attributeStoreCount;
    private int objectClassStoreCount;
    private boolean failStore;
    /** Counted down when a store is entered, when the store is gated. */
    private CountDownLatch enteredStore;
    /** Awaited by a gated store, which holds the exclusive lock while it waits. */
    private CountDownLatch leaveStore;

    private TestCompressedSchema()
    {
      super(DirectoryServer.getInstance().getServerContext());
    }

    @Override
    protected void storeAttribute(final byte[] encodedAttribute, final String attributeName,
        final Iterable<String> attributeOptions) throws DirectoryException
    {
      attributeStoreCount++;
      awaitIfGated();
      failIfRequested();
      storedAttributes.put(token(encodedAttribute), attributeName);
    }

    @Override
    protected void storeObjectClasses(final byte[] encodedObjectClasses, final Collection<String> objectClassNames)
        throws DirectoryException
    {
      objectClassStoreCount++;
      awaitIfGated();
      failIfRequested();
      storedObjectClasses.put(token(encodedObjectClasses), new ArrayList<>(objectClassNames));
    }

    private void failIfRequested() throws DirectoryException
    {
      if (failStore)
      {
        throw new DirectoryException(ResultCode.OTHER, LocalizableMessage.raw("the store failed"));
      }
    }

    private void awaitIfGated() throws DirectoryException
    {
      if (enteredStore == null)
      {
        return;
      }
      enteredStore.countDown();
      try
      {
        if (!leaveStore.await(30, TimeUnit.SECONDS))
        {
          throw new DirectoryException(ResultCode.OTHER, LocalizableMessage.raw("the gated store timed out"));
        }
      }
      catch (final InterruptedException e)
      {
        Thread.currentThread().interrupt();
        throw new DirectoryException(ResultCode.OTHER, LocalizableMessage.raw("the gated store was interrupted"), e);
      }
    }

    /** Loads a definition under the provided token, as an implementation does at startup. */
    private void loadAttributeAt(final int id, final String attributeName)
    {
      loadAttribute(encodedToken(id), attributeName, Collections.<String> emptySet());
    }

    private void loadObjectClassesAt(final int id, final Collection<String> objectClassNames)
    {
      loadObjectClasses(encodedToken(id), objectClassNames);
    }

    /**
     * Loads a definition under the provided key, as an implementation does at startup for every
     * record it read - the key being whatever the storage holds rather than one this test composed.
     */
    private AttributeDescription loadAttributeUnder(final byte[] token, final String attributeName)
    {
      return loadAttribute(token, attributeName, Collections.<String> emptySet());
    }

    private Map<ObjectClass, String> loadObjectClassesUnder(final byte[] token,
        final Collection<String> objectClassNames)
    {
      return loadObjectClasses(token, objectClassNames);
    }

    /** The tokens the whole content would be saved under, as DefaultCompressedSchema saves it. */
    private List<Integer> savedAttributeTokens()
    {
      final List<Integer> tokens = new ArrayList<>();
      for (final Entry<byte[], Entry<String, Iterable<String>>> attribute : getAllAttributes())
      {
        tokens.add(token(attribute.getKey()));
      }
      return tokens;
    }

    private List<Integer> savedObjectClassTokens()
    {
      final List<Integer> tokens = new ArrayList<>();
      for (final Entry<byte[], Collection<String>> objectClasses : getAllObjectClasses())
      {
        tokens.add(token(objectClasses.getKey()));
      }
      return tokens;
    }
  }

  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();
  }

  /**
   * A registration whose store failed must be withdrawn: the next encode of the same attribute has
   * to allocate and store the token again, rather than take the lock-free fast path and write an
   * entry carrying a token whose definition is nowhere.
   */
  @Test
  public void attributeTokenIsWithdrawnWhenItCannotBeStored() throws Exception
  {
    final TestCompressedSchema compressedSchema = new TestCompressedSchema();
    final Attribute attribute = Attributes.create("description", "a value");

    compressedSchema.failStore = true;
    try
    {
      compressedSchema.encodeAttribute(new ByteStringBuilder(), attribute);
      fail("the encode should have failed with the store");
    }
    catch (final DirectoryException expected)
    {
      // The operation fails, which is what the caller is told.
    }
    assertEquals(compressedSchema.attributeStoreCount, 1);
    assertTrue(compressedSchema.storedAttributes.isEmpty(), "nothing was persisted");

    compressedSchema.failStore = false;
    final ByteStringBuilder builder = new ByteStringBuilder();
    compressedSchema.encodeAttribute(builder, attribute);
    assertEquals(compressedSchema.attributeStoreCount, 2, "the failed registration was left behind");

    final int encodedToken = tokenOf(builder.toByteString());
    assertEquals(encodedToken, 0, "the withdrawn id was not allocated again");
    assertTrue(compressedSchema.storedAttributes.containsKey(encodedToken),
        "the entry carries token " + encodedToken + ", which was never stored");
    // What the withdrawal exists for: an element left behind by the failed registration would be
    // saved here under a token whose store never returned. Asserting the store count and the token
    // of the retry is not enough on its own - a registration that leaks its decode map element
    // simply allocates the next id, and every other assertion of this test still holds.
    assertEquals(compressedSchema.savedAttributeTokens(), Collections.singletonList(0),
        "the whole content still holds the element of the failed registration");
    final Attribute decoded = compressedSchema.decodeAttribute(builder.toByteString().asReader());
    assertEquals(decoded.getAttributeDescription(), attribute.getAttributeDescription());
    assertEquals(decoded.iterator().next().toString(), "a value");
  }

  /** The same for an object class set. */
  @Test
  public void objectClassTokenIsWithdrawnWhenItCannotBeStored() throws Exception
  {
    final TestCompressedSchema compressedSchema = new TestCompressedSchema();
    final Map<ObjectClass, String> objectClasses = objectClasses("top", "person");

    compressedSchema.failStore = true;
    try
    {
      compressedSchema.encodeObjectClasses(new ByteStringBuilder(), objectClasses);
      fail("the encode should have failed with the store");
    }
    catch (final DirectoryException expected)
    {
      // The operation fails, which is what the caller is told.
    }
    assertEquals(compressedSchema.objectClassStoreCount, 1);
    assertTrue(compressedSchema.storedObjectClasses.isEmpty(), "nothing was persisted");

    compressedSchema.failStore = false;
    final ByteStringBuilder builder = new ByteStringBuilder();
    compressedSchema.encodeObjectClasses(builder, objectClasses);
    assertEquals(compressedSchema.objectClassStoreCount, 2, "the failed registration was left behind");

    final int encodedToken = tokenOf(builder.toByteString());
    assertEquals(encodedToken, 0, "the withdrawn id was not allocated again");
    assertTrue(compressedSchema.storedObjectClasses.containsKey(encodedToken),
        "the entry carries token " + encodedToken + ", which was never stored");
    // As in attributeTokenIsWithdrawnWhenItCannotBeStored().
    assertEquals(compressedSchema.savedObjectClassTokens(), Collections.singletonList(0),
        "the whole content still holds the element of the failed registration");
    assertEquals(compressedSchema.decodeObjectClasses(builder.toByteString().asReader()), objectClasses);
  }

  /**
   * The withdrawal has to take the element the failed registration appended, and only that one.
   * On a decode map holding a single element every removal looks alike - index 0 is also the last
   * index, and the sole element is also the one that was appended - so a withdrawal taking the
   * wrong element is only visible once something was registered before the one that fails.
   * <p>
   * What it costs is this defect from the other end: removing an element the registration did not
   * append shifts the ids of everything after it, and the entries already written carry them.
   */
  @Test
  public void theAttributeWithdrawalTakesTheElementItAppended() throws Exception
  {
    final TestCompressedSchema compressedSchema = new TestCompressedSchema();
    final Attribute first = Attributes.create("description", "a value");
    final Attribute second = Attributes.create("cn", "a value");
    compressedSchema.encodeAttribute(new ByteStringBuilder(), first);

    compressedSchema.failStore = true;
    try
    {
      compressedSchema.encodeAttribute(new ByteStringBuilder(), second);
      fail("the encode should have failed with the store");
    }
    catch (final DirectoryException expected)
    {
      // The operation fails, which is what the caller is told.
    }

    compressedSchema.failStore = false;
    final ByteStringBuilder builder = new ByteStringBuilder();
    compressedSchema.encodeAttribute(builder, second);
    assertEquals(compressedSchema.attributeStoreCount, 3, "the failed registration was left behind");
    assertEquals(tokenOf(builder.toByteString()), 1, "the withdrawn id was not allocated again");
    assertEquals(compressedSchema.savedAttributeTokens(), Arrays.asList(0, 1),
        "the whole content does not span the ids that were registered");
    // The tokens alone do not separate a withdrawal of the last element from one of the first:
    // both leave two elements behind, under the tokens 0 and 1. What they decode to does.
    assertEquals(attributeNameAt(compressedSchema, 0), "description",
        "the id registered before the failure decodes as another attribute");
    assertEquals(attributeNameAt(compressedSchema, 1), "cn");
  }

  /** The same for an object class set. */
  @Test
  public void theObjectClassWithdrawalTakesTheElementItAppended() throws Exception
  {
    final TestCompressedSchema compressedSchema = new TestCompressedSchema();
    final Map<ObjectClass, String> first = objectClasses("top", "person");
    final Map<ObjectClass, String> second = objectClasses("top", "organizationalUnit");
    compressedSchema.encodeObjectClasses(new ByteStringBuilder(), first);

    compressedSchema.failStore = true;
    try
    {
      compressedSchema.encodeObjectClasses(new ByteStringBuilder(), second);
      fail("the encode should have failed with the store");
    }
    catch (final DirectoryException expected)
    {
      // The operation fails, which is what the caller is told.
    }

    compressedSchema.failStore = false;
    final ByteStringBuilder builder = new ByteStringBuilder();
    compressedSchema.encodeObjectClasses(builder, second);
    assertEquals(compressedSchema.objectClassStoreCount, 3, "the failed registration was left behind");
    assertEquals(tokenOf(builder.toByteString()), 1, "the withdrawn id was not allocated again");
    assertEquals(compressedSchema.savedObjectClassTokens(), Arrays.asList(0, 1),
        "the whole content does not span the ids that were registered");
    // As in theAttributeWithdrawalTakesTheElementItAppended().
    assertEquals(objectClassesAt(compressedSchema, 0), first,
        "the id registered before the failure decodes as another object class set");
    assertEquals(objectClassesAt(compressedSchema, 1), second);
  }

  /**
   * The id of a registration reaches the encode map - the lock-free path an encode takes to it -
   * only once the definition is persisted, so that no other thread can write an entry carrying an
   * id that a failing store is about to withdraw.
   */
  @Test
  public void aTokenIsPublishedOnlyOnceItIsStored() throws Exception
  {
    final TestCompressedSchema compressedSchema = new TestCompressedSchema();
    final Attribute attribute = Attributes.create("description", "a value");
    final ExecutorService executor = Executors.newFixedThreadPool(2);
    try
    {
      // One encode is held inside the store of the registration it made, holding the exclusive lock.
      compressedSchema.enteredStore = new CountDownLatch(1);
      compressedSchema.leaveStore = new CountDownLatch(1);
      final Future<Integer> registering = executor.submit(encoding(compressedSchema, attribute, null));
      assertTrue(compressedSchema.enteredStore.await(30, TimeUnit.SECONDS), "the store was never reached");

      // Another encode of the same attribute must not be handed the id being stored: it has to
      // park on the exclusive lock until the store returns.
      final AtomicReference<Thread> concurrentThread = new AtomicReference<>();
      final Future<Integer> concurrent = executor.submit(encoding(compressedSchema, attribute, concurrentThread));
      awaitParkedOnTheLock(concurrent, concurrentThread);
      assertFalse(concurrent.isDone(), "the token was handed out before it was stored");

      compressedSchema.leaveStore.countDown();
      assertEquals(registering.get(30, TimeUnit.SECONDS), Integer.valueOf(0));
      assertEquals(concurrent.get(30, TimeUnit.SECONDS), Integer.valueOf(0));
      assertEquals(compressedSchema.attributeStoreCount, 1, "the same token was stored twice");
    }
    finally
    {
      executor.shutdownNow();
    }
  }

  /** The same for an object class set, whose registration orders the two maps the same way. */
  @Test
  public void anObjectClassTokenIsPublishedOnlyOnceItIsStored() throws Exception
  {
    final TestCompressedSchema compressedSchema = new TestCompressedSchema();
    final Map<ObjectClass, String> objectClasses = objectClasses("top", "person");
    final ExecutorService executor = Executors.newFixedThreadPool(2);
    try
    {
      compressedSchema.enteredStore = new CountDownLatch(1);
      compressedSchema.leaveStore = new CountDownLatch(1);
      final Future<Integer> registering = executor.submit(encoding(compressedSchema, objectClasses, null));
      assertTrue(compressedSchema.enteredStore.await(30, TimeUnit.SECONDS), "the store was never reached");

      final AtomicReference<Thread> concurrentThread = new AtomicReference<>();
      final Future<Integer> concurrent = executor.submit(encoding(compressedSchema, objectClasses, concurrentThread));
      awaitParkedOnTheLock(concurrent, concurrentThread);
      assertFalse(concurrent.isDone(), "the token was handed out before it was stored");

      compressedSchema.leaveStore.countDown();
      assertEquals(registering.get(30, TimeUnit.SECONDS), Integer.valueOf(0));
      assertEquals(concurrent.get(30, TimeUnit.SECONDS), Integer.valueOf(0));
      assertEquals(compressedSchema.objectClassStoreCount, 1, "the same token was stored twice");
    }
    finally
    {
      executor.shutdownNow();
    }
  }

  /**
   * Waits for the provided encode to park on the exclusive lock, which is what it must do while
   * another thread holds that lock inside a store. Waiting for the thread to park is what makes
   * this prove the encode reached the lock-free read of the encode map: a latch counted down
   * inside the task only proves the task body started, so a build that published an id before
   * storing it would be recorded as a pass whenever the thread was slow between the two.
   * <p>
   * Where the thread is parked is checked as well as that it is parked. A state on its own says
   * nothing about what the thread waits for, and a build handing out an id before storing it
   * parks nowhere: it takes the lock-free path, completes, and leaves the whole discrimination to
   * a non-atomic isDone() sample - so any unrelated park, sampled in the instant before the task
   * publishes its completion, would record that build as a pass.
   */
  private static void awaitParkedOnTheLock(final Future<Integer> encode, final AtomicReference<Thread> runningOn)
      throws Exception
  {
    final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    while (System.nanoTime() < deadline)
    {
      if (encode.isDone())
      {
        fail("the token was handed out before it was stored: " + encode.get());
      }
      final Thread thread = runningOn.get();
      // WAITING alone: the exclusive lock is a ReentrantLock, which parks through LockSupport, so
      // this is the state it puts a thread in. BLOCKED is monitor entry and cannot come from that
      // lock at all - accepting it would admit only parks this test is not about.
      if (thread != null && thread.getState() == Thread.State.WAITING && parkedOnTheLockOfAnId(thread))
      {
        return;
      }
      Thread.sleep(1);
    }
    fail("the concurrent encode never parked on the exclusive lock");
  }

  /**
   * Returns whether the provided thread is parked on a lock taken on the way to an id of a
   * compressed schema, rather than anywhere else - the executor parking its idle worker on the
   * task queue is a park too, and so is a logger or a class initializer.
   */
  private static boolean parkedOnTheLockOfAnId(final Thread thread)
  {
    boolean parkedOnALock = false;
    // The frames run from the park outwards, so the lock is seen before whoever is taking it.
    for (final StackTraceElement frame : thread.getStackTrace())
    {
      if (frame.getClassName().startsWith("java.util.concurrent.locks."))
      {
        parkedOnALock = true;
      }
      else if (parkedOnALock && CompressedSchema.class.getName().equals(frame.getClassName()))
      {
        return "getAttributeId".equals(frame.getMethodName()) || "getObjectClassId".equals(frame.getMethodName());
      }
    }
    return false;
  }

  private static Callable<Integer> encoding(final CompressedSchema compressedSchema, final Attribute attribute,
      final AtomicReference<Thread> runningOn)
  {
    return new Callable<Integer>()
    {
      @Override
      public Integer call() throws Exception
      {
        if (runningOn != null)
        {
          runningOn.set(Thread.currentThread());
        }
        final ByteStringBuilder builder = new ByteStringBuilder();
        compressedSchema.encodeAttribute(builder, attribute);
        return tokenOf(builder.toByteString());
      }
    };
  }

  private static Callable<Integer> encoding(final CompressedSchema compressedSchema,
      final Map<ObjectClass, String> objectClasses, final AtomicReference<Thread> runningOn)
  {
    return new Callable<Integer>()
    {
      @Override
      public Integer call() throws Exception
      {
        if (runningOn != null)
        {
          runningOn.set(Thread.currentThread());
        }
        final ByteStringBuilder builder = new ByteStringBuilder();
        compressedSchema.encodeObjectClasses(builder, objectClasses);
        return tokenOf(builder.toByteString());
      }
    };
  }

  /**
   * A token with no definition is reported, whether it is below the range of the decode map, the
   * first id past its end, or well beyond it - and against a populated map as well as an empty
   * one, since it is the size of that map the lookup is measured against.
   */
  @Test
  public void unknownAttributeTokenIsReported() throws Exception
  {
    final TestCompressedSchema empty = new TestCompressedSchema();
    for (final int unknownToken : new int[] { -1, 0, 7 })
    {
      assertAttributeTokenIsReported(empty, unknownToken);
    }

    final TestCompressedSchema populated = new TestCompressedSchema();
    populated.loadAttributeAt(0, "description");
    populated.loadAttributeAt(1, "cn");
    for (final int unknownToken : new int[] { -1, 2, 7 })
    {
      assertAttributeTokenIsReported(populated, unknownToken);
    }
  }

  @Test
  public void unknownObjectClassTokenIsReported() throws Exception
  {
    final TestCompressedSchema empty = new TestCompressedSchema();
    for (final int unknownToken : new int[] { -1, 0, 7 })
    {
      assertObjectClassTokenIsReported(empty, unknownToken);
    }

    final TestCompressedSchema populated = new TestCompressedSchema();
    populated.loadObjectClassesAt(0, Arrays.asList("top", "person"));
    populated.loadObjectClassesAt(1, Arrays.asList("top", "organizationalUnit"));
    for (final int unknownToken : new int[] { -1, 2, 7 })
    {
      assertObjectClassTokenIsReported(populated, unknownToken);
    }
  }

  /**
   * A record the token cannot even be read from - it ends inside the token, or the length of the
   * token names more bytes than the record holds - is reported like a token no definition was
   * stored for. The read is what precedes the lookup, so leaving it unguarded would let the
   * decode path of a {@code PublicAPI} class raise NegativeArraySizeException or
   * IndexOutOfBoundsException at a caller written for DirectoryException.
   */
  @Test
  public void aTokenThatCannotBeReadIsReported() throws Exception
  {
    final TestCompressedSchema compressedSchema = new TestCompressedSchema();
    for (final ByteString unreadable : unreadableTokens())
    {
      try
      {
        compressedSchema.decodeAttribute(unreadable.asReader());
        fail("the token of " + unreadable + " cannot be read and should have been reported");
      }
      catch (final DirectoryException expected)
      {
        assertMessageIs(expected, ERR_COMPRESSEDSCHEMA_UNREADABLE_AD_TOKEN.get(unreadable),
            "the unreadable token " + unreadable);
      }

      try
      {
        compressedSchema.decodeObjectClasses(unreadable.asReader());
        fail("the token of " + unreadable + " cannot be read and should have been reported");
      }
      catch (final DirectoryException expected)
      {
        assertMessageIs(expected, ERR_COMPRESSEDSCHEMA_UNREADABLE_OC_TOKEN.get(unreadable),
            "the unreadable token " + unreadable);
      }
    }
  }

  /**
   * A token padded past the four bytes an id is ever encoded in decodes to the same id as the
   * canonical token of that id, because the decode folds whatever it is handed. A record carrying
   * one would therefore read as a live definition rather than be reported, which is the one shape
   * of a corrupt token that answers with data instead of an error.
   */
  @Test
  public void anOverlongTokenIsNotDecodedAsTheIdItPadsTo() throws Exception
  {
    final TestCompressedSchema compressedSchema = new TestCompressedSchema();
    compressedSchema.loadAttributeAt(2, "description");
    compressedSchema.loadObjectClassesAt(2, Arrays.asList("top", "person"));

    // The canonical token of the id 2 is 0x03, and this is that value padded to five bytes.
    final byte[] padded = new byte[] { 0x00, 0x00, 0x00, 0x00, 0x03 };
    try
    {
      compressedSchema.decodeAttribute(recordWithToken(padded, true).asReader());
      fail("the token is longer than an id is ever encoded in and should have been reported");
    }
    catch (final DirectoryException expected)
    {
      assertMessageIs(expected, ERR_COMPRESSEDSCHEMA_UNREADABLE_AD_TOKEN.get("padded"), "the overlong token");
    }

    try
    {
      compressedSchema.decodeObjectClasses(recordWithToken(padded, false).asReader());
      fail("the token is longer than an id is ever encoded in and should have been reported");
    }
    catch (final DirectoryException expected)
    {
      assertMessageIs(expected, ERR_COMPRESSEDSCHEMA_UNREADABLE_OC_TOKEN.get("padded"), "the overlong token");
    }
  }

  /**
   * A definition stored under a key no compressed schema hands out is skipped rather than loaded.
   * The load path folds whatever key the storage holds into an id and hands it straight to the
   * decode map, so a corrupt or truncated key reached that map as a negative index, or - where it
   * folds to an id that is live - as an overwrite of the definition that id belongs to. One
   * unreadable record must cost the definition it carries and nothing else: the open goes on, and
   * the token it was stored under is left with no definition, which the decode path reports.
   */
  @Test
  public void aTokenNoCompressedSchemaHandsOutIsSkippedWhenLoaded() throws Exception
  {
    for (final byte[] unusable : unusableTokens())
    {
      final String token = "0x" + bytesToHexNoSpace(unusable);
      final TestCompressedSchema compressedSchema = new TestCompressedSchema();
      compressedSchema.loadAttributeAt(0, "description");
      compressedSchema.loadObjectClassesAt(0, Arrays.asList("top", "person"));

      assertNull(compressedSchema.loadAttributeUnder(unusable, "cn"),
          "the attribute description stored under " + token + " should have been skipped");
      assertNull(compressedSchema.loadObjectClassesUnder(unusable, Arrays.asList("top", "organizationalUnit")),
          "the object classes stored under " + token + " should have been skipped");

      assertEquals(compressedSchema.savedAttributeTokens(), Collections.singletonList(0),
          "the definition stored under " + token + " reached the attribute description maps");
      assertEquals(compressedSchema.savedObjectClassTokens(), Collections.singletonList(0),
          "the definition stored under " + token + " reached the object class maps");
      assertEquals(attributeNameAt(compressedSchema, 0), "description",
          "the definition stored under " + token + " displaced the one the id 0 belongs to");
      assertEquals(objectClassesAt(compressedSchema, 0), objectClasses("top", "person"),
          "the definition stored under " + token + " displaced the one the id 0 belongs to");
    }
  }

  /**
   * A key folding to an id past the highest one a definition may be loaded under is skipped. The
   * decode map is indexed by the id, so loading one under an id of that size pads the map up to it,
   * and it happens under the exclusive lock - inside the write transaction a pluggable backend
   * opens in, where nothing reports what the open is waiting for.
   */
  @Test
  public void aTokenBeyondTheHighestLoadableIdIsSkipped() throws Exception
  {
    final List<byte[]> beyond = Arrays.asList(
        encodedToken(MAX_LOAD_ID + 1),
        // The largest id a four byte key folds to, which is what the issue this guards was raised
        // for: two billion slots, appended one at a time.
        new byte[] { (byte) 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF });
    for (final byte[] unusable : beyond)
    {
      final String token = "0x" + bytesToHexNoSpace(unusable);
      final TestCompressedSchema compressedSchema = new TestCompressedSchema();

      assertNull(compressedSchema.loadAttributeUnder(unusable, "description"),
          "the attribute description stored under " + token + " should have been skipped");
      assertNull(compressedSchema.loadObjectClassesUnder(unusable, Arrays.asList("top", "person")),
          "the object classes stored under " + token + " should have been skipped");

      assertEquals(compressedSchema.savedAttributeTokens(), Collections.<Integer> emptyList(),
          "the definition stored under " + token + " reached the attribute description maps");
      assertEquals(compressedSchema.savedObjectClassTokens(), Collections.<Integer> emptyList(),
          "the definition stored under " + token + " reached the object class maps");
    }
  }

  /**
   * The decode map is padded to the id of a definition in one pass, and rebuilt in one pass when
   * the schema changes. It is a {@link java.util.concurrent.CopyOnWriteArrayList}, so appending the
   * slots of the ids with no definition one at a time copies the whole backing array per slot: the
   * highest id a definition may be loaded under is what a corrupt key can still cost an open, and
   * a quadratic cost there is one an open never finishes paying.
   */
  @Test
  public void theDecodeMapIsPaddedToALoadedAttributeIdInOnePass() throws Exception
  {
    final TestCompressedSchema compressedSchema = new TestCompressedSchema();

    final long loadStart = System.nanoTime();
    assertNotNull(compressedSchema.loadAttributeUnder(encodedToken(MAX_LOAD_ID), "description"),
        "the highest loadable id is one an encode hands out and its definition was skipped");
    final long loadMs = millisSince(loadStart);

    // The first decode rebuilds the maps for the current schema, walking the whole decode map.
    final long decodeStart = System.nanoTime();
    final Attribute decoded =
        compressedSchema.decodeAttribute(recordWithToken(encodedToken(MAX_LOAD_ID), true).asReader());
    final long decodeMs = millisSince(decodeStart);

    assertEquals(decoded.getAttributeDescription().getAttributeType().getNameOrOID(), "description");
    assertTrue(loadMs < PADDING_BUDGET_MS,
        "padding the decode map to the id " + MAX_LOAD_ID + " took " + loadMs + " ms");
    assertTrue(decodeMs < PADDING_BUDGET_MS,
        "rebuilding the decode map of " + MAX_LOAD_ID + " ids took " + decodeMs + " ms");
  }

  /** The same for the object class maps, which grow through a path of their own. */
  @Test
  public void theDecodeMapIsPaddedToALoadedObjectClassIdInOnePass() throws Exception
  {
    final TestCompressedSchema compressedSchema = new TestCompressedSchema();

    final long loadStart = System.nanoTime();
    assertNotNull(
        compressedSchema.loadObjectClassesUnder(encodedToken(MAX_LOAD_ID), Arrays.asList("top", "person")),
        "the highest loadable id is one an encode hands out and its definition was skipped");
    final long loadMs = millisSince(loadStart);

    final long decodeStart = System.nanoTime();
    final Map<ObjectClass, String> decoded =
        compressedSchema.decodeObjectClasses(recordWithToken(encodedToken(MAX_LOAD_ID), false).asReader());
    final long decodeMs = millisSince(decodeStart);

    assertEquals(decoded, objectClasses("top", "person"));
    assertTrue(loadMs < PADDING_BUDGET_MS,
        "padding the decode map to the id " + MAX_LOAD_ID + " took " + loadMs + " ms");
    assertTrue(decodeMs < PADDING_BUDGET_MS,
        "rebuilding the decode map of " + MAX_LOAD_ID + " ids took " + decodeMs + " ms");
  }

  /**
   * A skipped definition leaves nothing behind in the encode map. The load puts the element in the
   * encode map as well, and a key folding to an id that is live would otherwise leave the skipped
   * element mapped to that id: an encode would hand out a token that decodes to another definition,
   * and entries would be written under it - silently, which is worse than the open that fails.
   */
  @Test
  public void aSkippedDefinitionIsNotLeftInTheEncodeMap() throws Exception
  {
    final TestCompressedSchema compressedSchema = new TestCompressedSchema();
    // The canonical token of the id 0 is 0x01, and this is that value padded to two bytes. Loaded
    // before the definition the id 0 belongs to: the order a cursor over a corrupt store reads its
    // records in is not this test's to choose, and neither order may publish the skipped element.
    compressedSchema.loadAttributeUnder(new byte[] { 0x00, 0x01 }, "cn");
    compressedSchema.loadAttributeAt(0, "description");

    final ByteStringBuilder builder = new ByteStringBuilder();
    compressedSchema.encodeAttribute(builder, Attributes.create("cn", "a value"));
    final Attribute decoded = compressedSchema.decodeAttribute(builder.toByteString().asReader());
    assertEquals(decoded.getAttributeDescription().getAttributeType().getNameOrOID(), "cn",
        "an entry was written under a token that decodes to another attribute description");
  }

  /**
   * A skipped definition still costs the id its key folds to. A key mangled from the canonical one
   * folds to the id that key addressed, and entries carrying that id are still out there, so
   * leaving it out of the decode map would let the next registration hand it out again and those
   * entries would decode as whatever that registration stored. Held as the gap it is - which is
   * what the reload carries over for the same reason, and what the decode path reports.
   */
  @Test
  public void aSkippedDefinitionStillCostsTheIdItsKeyNames() throws Exception
  {
    final TestCompressedSchema compressedSchema = new TestCompressedSchema();
    compressedSchema.loadAttributeAt(0, "sn");
    compressedSchema.loadObjectClassesAt(0, Arrays.asList("top", "person"));
    // The canonical token of the id 2 is 0x03, and this is that value padded to two bytes: what is
    // lost is the definition of the id 2, an id entries already written carry.
    compressedSchema.loadAttributeUnder(new byte[] { 0x00, 0x03 }, "cn");
    compressedSchema.loadObjectClassesUnder(new byte[] { 0x00, 0x03 }, Arrays.asList("top", "device"));

    final ByteStringBuilder attributeBuilder = new ByteStringBuilder();
    compressedSchema.encodeAttribute(attributeBuilder, Attributes.create("description", "a value"));
    final ByteStringBuilder objectClassBuilder = new ByteStringBuilder();
    compressedSchema.encodeObjectClasses(objectClassBuilder, objectClasses("top", "organizationalUnit"));

    assertEquals(tokenOf(attributeBuilder.toByteString()), 3,
        "the id of the skipped definition was handed out again");
    assertEquals(tokenOf(objectClassBuilder.toByteString()), 3,
        "the id of the skipped definition was handed out again");
    assertAttributeTokenIsReported(compressedSchema, 2);
    assertObjectClassTokenIsReported(compressedSchema, 2);
  }

  /**
   * A token past the highest id a definition is loaded back under is never handed out. The decode
   * map is padded to the ids read out of a storage, so a key accepted at the ceiling leaves the
   * next registration at an id the next open would refuse: the entry written with it would decode
   * as nothing once the server is restarted, which is the one thing a registration may not do.
   */
  @Test
  public void aTokenBeyondTheHighestLoadableIdIsNeverHandedOut() throws Exception
  {
    final TestCompressedSchema compressedSchema = new TestCompressedSchema();
    compressedSchema.loadAttributeUnder(encodedToken(MAX_LOAD_ID), "description");
    compressedSchema.loadObjectClassesUnder(encodedToken(MAX_LOAD_ID), Arrays.asList("top", "person"));

    try
    {
      compressedSchema.encodeAttribute(new ByteStringBuilder(), Attributes.create("cn", "a value"));
      fail("a token no open would take back should not have been handed out");
    }
    catch (final DirectoryException expected)
    {
      assertMessageIs(expected, ERR_COMPRESSEDSCHEMA_NO_TOKEN_LEFT.get(0, 0), "the exhausted token space");
    }
    try
    {
      compressedSchema.encodeObjectClasses(new ByteStringBuilder(), objectClasses("top", "device"));
      fail("a token no open would take back should not have been handed out");
    }
    catch (final DirectoryException expected)
    {
      assertMessageIs(expected, ERR_COMPRESSEDSCHEMA_NO_TOKEN_LEFT.get(0, 0), "the exhausted token space");
    }
    assertEquals(compressedSchema.attributeStoreCount, 0, "a token no open would take back was stored");
    assertEquals(compressedSchema.objectClassStoreCount, 0, "a token no open would take back was stored");
  }

  /**
   * Every key an encode writes is one a load takes back. The keys are checked against what this
   * schema would have written for the id they fold to, so the check has to accept each of the
   * lengths an id is written in rather than the one byte the small ids of a test fit in.
   */
  @Test
  public void everyTokenAnEncodeWritesIsLoadedBack() throws Exception
  {
    // The ids either side of the byte an encode adds at 0xFF and at 0xFFFF.
    for (final int id : new int[] { 0, 1, 254, 255, 256, 65534, 65535, 65536 })
    {
      final TestCompressedSchema compressedSchema = new TestCompressedSchema();
      assertNotNull(compressedSchema.loadAttributeUnder(encodedToken(id), "description"),
          "the id " + id + " is one an encode hands out and its definition was skipped");
      assertNotNull(compressedSchema.loadObjectClassesUnder(encodedToken(id), Arrays.asList("top", "person")),
          "the id " + id + " is one an encode hands out and its definition was skipped");

      final Attribute decoded =
          compressedSchema.decodeAttribute(recordWithToken(encodedToken(id), true).asReader());
      assertEquals(decoded.getAttributeDescription().getAttributeType().getNameOrOID(), "description",
          "the definition loaded under the id " + id + " is not the one that token decodes to");
      assertEquals(compressedSchema.decodeObjectClasses(recordWithToken(encodedToken(id), false).asReader()),
          objectClasses("top", "person"),
          "the definition loaded under the id " + id + " is not the one that token decodes to");
    }
  }

  /**
   * What padding a decode map to the highest loadable id may cost. One pass over a map of that size
   * is milliseconds; a pass per slot is minutes, which is what this separates rather than any
   * measure of how fast the one pass is.
   */
  private static final long PADDING_BUDGET_MS = 30000;

  private static long millisSince(final long start)
  {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
  }

  /** Keys a compressed schema never hands out, as a corrupt or truncated store holds them. */
  private static List<byte[]> unusableTokens()
  {
    return Arrays.asList(
        // Empty, all zero in one byte, and all zero in the four bytes an id is at most written
        // in: every one of them folds to the id -1, which is not an index of anything.
        new byte[0],
        new byte[] { 0x00 },
        new byte[] { 0x00, 0x00, 0x00, 0x00 },
        // 0xFFFFFFFF, which folds to the id -2.
        new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF },
        // The canonical token of the id 0 padded to two bytes, and past the four bytes an id is
        // ever written in. Both fold to the id 0, whose definition is live.
        new byte[] { 0x00, 0x01 },
        new byte[] { 0x00, 0x00, 0x00, 0x00, 0x01 });
  }

  /** A record carrying the provided token, with a single value where an attribute is asked for. */
  private static ByteString recordWithToken(final byte[] idBytes, final boolean withAValue)
  {
    final ByteStringBuilder builder = new ByteStringBuilder();
    builder.appendBERLength(idBytes.length);
    builder.appendBytes(idBytes);
    if (withAValue)
    {
      builder.appendBERLength(1);
      builder.appendBERLength(1);
      builder.appendBytes(new byte[] { 'x' });
    }
    return builder.toByteString();
  }

  /** Records a decode path cannot read a token from, as a corrupt or truncated store holds them. */
  private static List<ByteString> unreadableTokens()
  {
    return Arrays.asList(
        // The record ends before the length of the token.
        ByteString.empty(),
        // The length names one byte the record does not hold.
        ByteString.wrap(new byte[] { 0x01 }),
        // A four byte length composing to 0xFFFFFFFF, which is -1 as an int.
        ByteString.wrap(new byte[] { (byte) 0x84, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF }),
        // A four byte length of 0x7FFFFFFF: two gigabytes, which must not be allocated to find
        // out that the record does not hold them.
        ByteString.wrap(new byte[] { (byte) 0x84, 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF }));
  }

  private static void assertAttributeTokenIsReported(final TestCompressedSchema compressedSchema,
      final int unknownToken) throws Exception
  {
    try
    {
      compressedSchema.decodeAttribute(encodedAttribute(unknownToken).asReader());
      fail("the token " + unknownToken + " has no definition and should have been reported");
    }
    catch (final DirectoryException expected)
    {
      // Reported as the unknown token it is, and named as such: nothing else in this decode path
      // is allowed to answer for a token, and the message the operator gets is what says which.
      assertMessageIs(expected, ERR_COMPRESSEDSCHEMA_UNRECOGNIZED_AD_TOKEN.get(unknownToken),
          "the token " + unknownToken);
      assertTokenIsNamed(expected, unknownToken);
    }
  }

  private static void assertObjectClassTokenIsReported(final TestCompressedSchema compressedSchema,
      final int unknownToken) throws Exception
  {
    try
    {
      compressedSchema.decodeObjectClasses(encodedToken(unknownToken, new ByteStringBuilder()).asReader());
      fail("the token " + unknownToken + " has no definition and should have been reported");
    }
    catch (final DirectoryException expected)
    {
      assertMessageIs(expected, ERR_COMPRESSEDSCHEMA_UNKNOWN_OC_TOKEN.get(unknownToken),
          "the token " + unknownToken);
      assertTokenIsNamed(expected, unknownToken);
    }
  }

  /** Asserts that the exception carries the expected message, by resource and id rather than text. */
  private static void assertMessageIs(final DirectoryException reported, final LocalizableMessage expected,
      final String context)
  {
    final LocalizableMessage message = reported.getMessageObject();
    assertEquals(message.resourceName() + "-" + message.ordinal(),
        expected.resourceName() + "-" + expected.ordinal(),
        context + " was reported as something else: " + message);
  }

  /**
   * Asserts that the message names the token the way an operator has to read it: the key the
   * storage holds, with the id it decodes to. Asserted on the text, because comparing two
   * messages by resource and ordinal says nothing about the arguments they carry - the accessors
   * take an Object, so the id this test passes and the rendering production passes do not differ
   * at compile time either, and a rendering returning an empty string would keep the suite green.
   */
  private static void assertTokenIsNamed(final DirectoryException reported, final int unknownToken)
  {
    final String named = String.format("0x%02X (id %d)", (unknownToken + 1) & 0xFF, unknownToken);
    final LocalizableMessage message = reported.getMessageObject();
    assertTrue(message.toString().contains(named),
        "the token is not named as the storage holds it, expected " + named + " in: " + message);
  }

  /**
   * A compressed schema loaded from a storage that holds no definition for some of the tokens
   * carries a gap. Decoding across the gap, reloading the maps for a changed schema and saving the
   * whole content must all walk over it, and the ids around it must not shift - the entries already
   * written carry them.
   */
  @Test
  public void aGapInTheDecodeMapsIsCarriedRatherThanDereferenced() throws Exception
  {
    final TestCompressedSchema compressedSchema = new TestCompressedSchema();
    compressedSchema.loadAttributeAt(2, "description");
    compressedSchema.loadObjectClassesAt(2, Arrays.asList("top", "person"));

    // The first decode also rebuilds the maps for the current schema, which is what used to walk
    // into the gap with no null check.
    try
    {
      compressedSchema.decodeAttribute(encodedAttribute(0).asReader());
      fail("the token 0 falls in the gap and should have been reported");
    }
    catch (final DirectoryException expected)
    {
      // Reported as the unknown token it is.
    }
    try
    {
      compressedSchema.decodeObjectClasses(encodedToken(1, new ByteStringBuilder()).asReader());
      fail("the token 1 falls in the gap and should have been reported");
    }
    catch (final DirectoryException expected)
    {
      // Reported as the unknown token it is.
    }

    // What is around the gap is still reachable under the ids it was loaded with.
    assertEquals(compressedSchema.decodeAttribute(encodedAttribute(2).asReader())
        .getAttributeDescription().getAttributeType().getNameOrOID(), "description");
    assertEquals(compressedSchema.decodeObjectClasses(encodedToken(2, new ByteStringBuilder()).asReader()),
        objectClasses("top", "person"));

    // And the next registration allocates the id after the gap, not one inside it.
    final ByteStringBuilder attributeBuilder = new ByteStringBuilder();
    compressedSchema.encodeAttribute(attributeBuilder, Attributes.create("cn", "a value"));
    assertEquals(tokenOf(attributeBuilder.toByteString()), 3);

    final ByteStringBuilder objectClassesBuilder = new ByteStringBuilder();
    compressedSchema.encodeObjectClasses(objectClassesBuilder, objectClasses("top", "organizationalUnit"));
    assertEquals(tokenOf(objectClassesBuilder.toByteString()), 3);

    // The whole content is still saveable, which is how DefaultCompressedSchema persists a store.
    assertEquals(compressedSchema.savedAttributeTokens(), Arrays.asList(2, 3));
    assertEquals(compressedSchema.savedObjectClassTokens(), Arrays.asList(2, 3));
  }

  /** The attribute the provided token decodes to, named as the schema names it. */
  private static String attributeNameAt(final TestCompressedSchema compressedSchema, final int id) throws Exception
  {
    return compressedSchema.decodeAttribute(encodedAttribute(id).asReader())
        .getAttributeDescription().getAttributeType().getNameOrOID();
  }

  /** The object class set the provided token decodes to. */
  private static Map<ObjectClass, String> objectClassesAt(final TestCompressedSchema compressedSchema, final int id)
      throws Exception
  {
    return compressedSchema.decodeObjectClasses(encodedToken(id, new ByteStringBuilder()).asReader());
  }

  private static Map<ObjectClass, String> objectClasses(final String... names)
  {
    final Map<ObjectClass, String> objectClasses = new LinkedHashMap<>(names.length);
    for (final String name : names)
    {
      objectClasses.put(DirectoryServer.getInstance().getServerContext().getSchema().getObjectClass(name), name);
    }
    return objectClasses;
  }

  /** Encodes an attribute holding a single value under the provided token. */
  private static ByteString encodedAttribute(final int id)
  {
    final ByteStringBuilder builder = new ByteStringBuilder();
    encodedToken(id, builder);
    builder.appendBERLength(1);
    builder.appendBERLength(1);
    builder.appendBytes(new byte[] { 'x' });
    return builder.toByteString();
  }

  private static ByteString encodedToken(final int id, final ByteStringBuilder builder)
  {
    final byte[] idBytes = encodedToken(id);
    builder.appendBERLength(idBytes.length);
    builder.appendBytes(idBytes);
    return builder.toByteString();
  }

  /**
   * The key a compressed schema writes the definition of the provided id under, through the
   * encoder production writes it with: a load now takes a definition back only under that exact
   * key, so a second encoder here would let this suite pass against keys the server rejects.
   */
  private static byte[] encodedToken(final int id)
  {
    return CompressedSchema.encodeId(id);
  }

  /** Decodes a token the way CompressedSchema does. */
  private static int token(final byte[] idBytes)
  {
    int id = 0;
    for (final byte b : idBytes)
    {
      id <<= 8;
      id |= b & 0xFF;
    }
    return id - 1;
  }

  /** Reads the token an encoded attribute or object class set starts with. */
  private static int tokenOf(final ByteString encoded)
  {
    final ByteSequenceReader reader = encoded.asReader();
    final byte[] idBytes = new byte[reader.readBERLength()];
    reader.readBytes(idBytes);
    return token(idBytes);
  }
}
