/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.core;

import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.api.plugin.InternalDirectoryServerPlugin;
import org.opends.server.api.plugin.PluginResult.PostResponse;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.operation.PostResponseAddOperation;
import org.opends.server.types.operation.PostResponseDeleteOperation;
import org.opends.server.types.operation.PostResponseModifyDNOperation;
import org.opends.server.types.operation.PostResponseModifyOperation;

import static org.opends.server.api.plugin.PluginType.*;

/**
 * This class provides a simple change notification listener that simply counts
 * the number of times that it is invoked during processing.
 */
class TestChangeNotificationListener extends InternalDirectoryServerPlugin
{
  /** The number of times that the listener has been invoked for add operations. */
  private final AtomicInteger addCount = new AtomicInteger(0);

  /**
   * The number of times that the listener has been invoked for delete
   * operations.
   */
  private final AtomicInteger deleteCount = new AtomicInteger(0);

  /**
   * The number of times that the listener has been invoked for modify
   * operations.
   */
  private final AtomicInteger modifyCount = new AtomicInteger(0);

  /**
   * The number of times that the listener has been invoked for modify DN
   * operations.
   */
  private final AtomicInteger modifyDNCount = new AtomicInteger(0);

  /**
   * Creates a new instance of this change notification listener.
   *
   * @throws DirectoryException
   *           If a problem occurs while creating an instance of this class
   */
  public TestChangeNotificationListener() throws DirectoryException
  {
    super(DN.valueOf("cn=TestChangeNotificationListener"),
        EnumSet.of(POST_RESPONSE_ADD, POST_RESPONSE_MODIFY, POST_RESPONSE_MODIFY_DN, POST_RESPONSE_DELETE),
        true);
  }

  /** {@inheritDoc} */
  @Override
  public PostResponse doPostResponse(PostResponseAddOperation op)
  {
    if (op.getResultCode() == ResultCode.SUCCESS)
    {
      addCount.incrementAndGet();
    }
    return PostResponse.continueOperationProcessing();
  }

  /** {@inheritDoc} */
  @Override
  public PostResponse doPostResponse(PostResponseDeleteOperation op)
  {
    if (op.getResultCode() == ResultCode.SUCCESS)
    {
      deleteCount.incrementAndGet();
    }
    return PostResponse.continueOperationProcessing();
  }

  /** {@inheritDoc} */
  @Override
  public PostResponse doPostResponse(PostResponseModifyOperation op)
  {
    if (op.getResultCode() == ResultCode.SUCCESS)
    {
      modifyCount.incrementAndGet();
    }
    return PostResponse.continueOperationProcessing();
  }

  /** {@inheritDoc} */
  @Override
  public PostResponse doPostResponse(PostResponseModifyDNOperation op)
  {
    if (op.getResultCode() == ResultCode.SUCCESS)
    {
      modifyDNCount.incrementAndGet();
    }
    return PostResponse.continueOperationProcessing();
  }

  /**
   * Resets all of the counts to zero.
   */
  public void reset()
  {
    addCount.set(0);
    deleteCount.set(0);
    modifyCount.set(0);
    modifyDNCount.set(0);
  }

  /**
   * Retrieves the current invocation count for add operations.
   *
   * @return  The current invocation count for add operations.
   */
  public int getAddCount()
  {
    return addCount.get();
  }

  /**
   * Retrieves the current invocation count for delete operations.
   *
   * @return  The current invocation count for delete operations.
   */
  public int getDeleteCount()
  {
    return deleteCount.get();
  }

  /**
   * Retrieves the current invocation count for modify operations.
   *
   * @return  The current invocation count for modify operations.
   */
  public int getModifyCount()
  {
    return modifyCount.get();
  }

  /**
   * Retrieves the current invocation count for modify DN operations.
   *
   * @return  The current invocation count for modify DN operations.
   */
  public int getModifyDNCount()
  {
    return modifyDNCount.get();
  }
}

