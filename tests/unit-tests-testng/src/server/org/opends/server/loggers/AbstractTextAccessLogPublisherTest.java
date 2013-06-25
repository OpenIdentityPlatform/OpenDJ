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
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.loggers;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.loggers.AbstractTextAccessLogPublisher.RootFilter;
import org.opends.server.types.Operation;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class AbstractTextAccessLogPublisherTest extends DirectoryServerTestCase
{

  @DataProvider(name = "isLoggableData")
  public Object[][] getIsLoggableData()
  {
    // When suppress is set to true and the corresponding operation is set to
    // true too, then the operation is not loggable.
    // You can read the array like this: read two by two from line start, if
    // both are true in a pair, then the expected result is false (not
    // loggable).
    // There is just one exception: when the operation is a synchronization
    // operation and we do not suppress synchronization operation, then we
    // return true regardless of whether this is an internal operation
    return new Object[][] {
      { true, true, true, true, false },
      { true, true, true, false, false },
      { true, true, false, true, false },
      { true, true, false, false, false },
      { true, false, true, true, false },
      { true, false, true, false, true },
      { true, false, false, true, true },
      { true, false, false, false, true },
      { false, true, true, true, true },
      { false, true, true, false, true },
      { false, true, false, true, true },
      { false, true, false, false, true },
      { false, false, true, true, false },
      { false, false, true, false, true },
      { false, false, false, true, true },
      { false, false, false, false, true }, };
  }

  @Test(dataProvider = "isLoggableData")
  public void rootFilterIsLoggable(boolean suppressSynchronization,
      boolean isSynchronizationOp, boolean suppressInternal,
      boolean isInternalOp, boolean expectedTestResult)
  {
    final Operation operation = mock(Operation.class);
    when(operation.isSynchronizationOperation())
        .thenReturn(isSynchronizationOp);
    when(operation.isInnerOperation()).thenReturn(isInternalOp);

    final RootFilter filter =
        new RootFilter(suppressInternal, suppressSynchronization, null, null);
    assertEquals(filter.isLoggable(operation), expectedTestResult);
  }

}
