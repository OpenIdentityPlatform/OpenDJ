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
      if (thread != null)
      {
        final Thread.State state = thread.getState();
        if (state == Thread.State.WAITING || state == Thread.State.BLOCKED)
        {
          return;
        }
      }
      Thread.sleep(1);
    }
    fail("the concurrent encode never parked on the exclusive lock");
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
      assertMessageIs(expected, ERR_COMPRESSEDSCHEMA_UNRECOGNIZED_AD_TOKEN.get(unknownToken), unknownToken);
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
      assertMessageIs(expected, ERR_COMPRESSEDSCHEMA_UNKNOWN_OC_TOKEN.get(unknownToken), unknownToken);
    }
  }

  /** Asserts that the exception carries the expected message, by resource and id rather than text. */
  private static void assertMessageIs(final DirectoryException reported, final LocalizableMessage expected,
      final int unknownToken)
  {
    final LocalizableMessage message = reported.getMessageObject();
    assertEquals(message.resourceName() + "-" + message.ordinal(),
        expected.resourceName() + "-" + expected.ordinal(),
        "the token " + unknownToken + " was reported as something else: " + message);
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

  /** Encodes a token the way CompressedSchema does, one byte being enough for the tests. */
  private static byte[] encodedToken(final int id)
  {
    return new byte[] { (byte) ((id + 1) & 0xFF) };
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
