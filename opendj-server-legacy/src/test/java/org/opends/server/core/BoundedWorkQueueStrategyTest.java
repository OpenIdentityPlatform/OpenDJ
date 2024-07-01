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
 * Copyright 2014 ForgeRock AS.
 */
package org.opends.server.core;

import java.util.Arrays;
import java.util.List;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.api.ClientConnection;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Operation;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.mockito.Mockito.*;

@SuppressWarnings("javadoc")
public class BoundedWorkQueueStrategyTest extends DirectoryServerTestCase
{

  @SuppressWarnings("unchecked")
  private static final List<Class<? extends Operation>> ALL_OPERATION_CLASSES =
      Arrays.asList(AbandonOperation.class, AddOperation.class,
          BindOperation.class, CompareOperation.class, DeleteOperation.class,
          ExtendedOperation.class, ModifyDNOperation.class,
          ModifyOperation.class, SearchOperation.class, UnbindOperation.class);

  /** Overrides the use of undesired static methods. */
  private final class BoundedWorkQueueStrategyForTest extends
      BoundedWorkQueueStrategy
  {

    private boolean enqueueRequestSucceeds;

    private BoundedWorkQueueStrategyForTest(Integer maxNbConcurrentOperations,
        boolean enqueueRequestSucceeds)
    {
      super(maxNbConcurrentOperations);
      this.enqueueRequestSucceeds = enqueueRequestSucceeds;
    }

    @Override
    protected boolean tryEnqueueRequest(Operation op) throws DirectoryException
    {
      return enqueueRequestSucceeds;
    }

    @Override
    protected int getNumWorkerThreads()
    {
      return 1;
    }
  }

  private Operation getMockedOperation(
      Class<? extends Operation> operationClass, boolean isConnectionValid)
  {
    final Operation operation = mock(operationClass);
    final ClientConnection connection = mock(ClientConnection.class);
    when(operation.getClientConnection()).thenReturn(connection);
    when(connection.isConnectionValid()).thenReturn(isConnectionValid);
    return operation;
  }

  @Test
  public void doNotEnqueueOperationWithInvalidConnection() throws Exception
  {
    final BoundedWorkQueueStrategy strategy =
        new BoundedWorkQueueStrategyForTest(1, true);
    final Operation operation =
        getMockedOperation(SearchOperation.class, false);
    strategy.enqueueRequest(operation);

    verify(operation, times(1)).getClientConnection();
    verifyNoMoreInteractions(operation);
  }

  @DataProvider
  public Object[][] allOperationClasses() throws Exception
  {
    final Object[][] results = new Object[ALL_OPERATION_CLASSES.size() * 2][];
    for (int i = 0; i < ALL_OPERATION_CLASSES.size(); i++)
    {
      Class<? extends Operation> operationClass = ALL_OPERATION_CLASSES.get(i);
      results[i * 2] = new Object[] { operationClass, true };
      results[i * 2 + 1] = new Object[] { operationClass, false };
    }
    return results;
  }

  @Test(expectedExceptions = RuntimeException.class)
  // FIXME use expectedExceptionsMessageRegExp = "Not implemented for.*"
  public void enqueueUnhandledOperationClass() throws Exception
  {
    enqueueRequestWithConcurrency(Operation.class, false);
  }

  @Test(dataProvider = "allOperationClasses")
  public void enqueueRequestWithConcurrency(
      Class<? extends Operation> operationClass, boolean enqueueRequestSucceeds)
      throws Exception
  {
    final BoundedWorkQueueStrategy strategy =
        new BoundedWorkQueueStrategyForTest(1, enqueueRequestSucceeds);
    final Operation operation = getMockedOperation(operationClass, true);
    strategy.enqueueRequest(operation);

    verify(operation, times(1)).getClientConnection();
    if (!enqueueRequestSucceeds)
    { // run in current thread
      verify(operation, times(1)).run();
    }
    verifyNoMoreInteractions(operation);
  }

  @Test
  public void enqueueRequestWithLimitedConcurrency()
      throws Exception
  {
    final Class<? extends Operation> operationClass = SearchOperation.class;
    final BoundedWorkQueueStrategy strategy =
        new BoundedWorkQueueStrategyForTest(1, true);
    final Operation operation1 = getMockedOperation(operationClass, true);
    final Operation operation2 = getMockedOperation(operationClass, true);
    final Operation operation3 = getMockedOperation(operationClass, true);
    strategy.enqueueRequest(operation1);
    strategy.enqueueRequest(operation2);
    strategy.enqueueRequest(operation3);

    verify(operation1, times(1)).getClientConnection();
    verifyNoMoreInteractions(operation1);
    verify(operation2, times(1)).getClientConnection();
    verifyNoMoreInteractions(operation2);

    verify(operation3, times(1)).getClientConnection();
    // to many concurrent operations => run in current thread
    verify(operation3, times(1)).run();
    verifyNoMoreInteractions(operation3);
  }

  @Test(dataProvider = "allOperationClasses")
  public void enqueueRequestNoConcurrency(
      Class<? extends Operation> operationClass, boolean enqueueRequestSucceeds)
      throws Exception
  {
    final BoundedWorkQueueStrategy strategy =
        new BoundedWorkQueueStrategyForTest(0, enqueueRequestSucceeds);
    final Operation operation = getMockedOperation(operationClass, true);
    strategy.enqueueRequest(operation);

    verify(operation, times(1)).getClientConnection();
    if (!enqueueRequestSucceeds)
    { // run in current thread
      verify(operation, times(1)).run();
    }
    verifyNoMoreInteractions(operation);
  }

}
