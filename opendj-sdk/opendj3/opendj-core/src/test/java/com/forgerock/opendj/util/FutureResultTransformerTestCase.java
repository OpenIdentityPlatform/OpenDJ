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
 *      Copyright 2013 ForgeRock AS.
 */

package com.forgerock.opendj.util;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;
import static org.forgerock.opendj.ldap.ErrorResultException.newErrorResult;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.concurrent.TimeUnit;

import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ResultHandler;
import org.testng.annotations.Test;

/**
 * Tests {@link FutureResultTransformer}.
 */
@SuppressWarnings("javadoc")
public class FutureResultTransformerTestCase extends UtilTestCase {
    private static final class TestFuture extends FutureResultTransformer<Integer, String> {
        public TestFuture(final ResultHandler<? super String> handler) {
            super(handler);
        }

        @Override
        protected ErrorResultException transformErrorResult(final ErrorResultException error) {
            assertThat(error).isSameAs(UNTRANSFORMED_ERROR);
            return TRANSFORMED_ERROR;
        }

        @Override
        protected String transformResult(final Integer result) throws ErrorResultException {
            assertThat(result).isSameAs(UNTRANSFORMED_RESULT);
            return TRANSFORMED_RESULT;
        }
    }

    private static final ErrorResultException TRANSFORMED_ERROR = newErrorResult(ResultCode.OTHER,
            "transformed");
    private static final String TRANSFORMED_RESULT = "transformed";
    private static final ErrorResultException UNTRANSFORMED_ERROR = newErrorResult(
            ResultCode.OTHER, "untransformed");
    private static final Integer UNTRANSFORMED_RESULT = Integer.valueOf(0);

    @Test
    public void testGetTransformsError() throws Exception {
        final TestFuture future = new TestFuture(null);
        future.setFutureResult(new CompletedFutureResult<Integer>(UNTRANSFORMED_ERROR));
        future.handleErrorResult(UNTRANSFORMED_ERROR);
        try {
            future.get();
            fail();
        } catch (final ErrorResultException e) {
            assertThat(e).isSameAs(TRANSFORMED_ERROR);
        }
    }

    @Test
    public void testGetTransformsResult() throws Exception {
        final TestFuture future = new TestFuture(null);
        future.setFutureResult(new CompletedFutureResult<Integer>(UNTRANSFORMED_RESULT));
        future.handleResult(UNTRANSFORMED_RESULT);
        assertThat(future.get()).isSameAs(TRANSFORMED_RESULT);
    }

    @Test
    public void testGetWithTimeoutTransformsError() throws Exception {
        final TestFuture future = new TestFuture(null);
        future.setFutureResult(new CompletedFutureResult<Integer>(UNTRANSFORMED_ERROR));
        future.handleErrorResult(UNTRANSFORMED_ERROR);
        try {
            future.get(100, TimeUnit.SECONDS);
            fail();
        } catch (final ErrorResultException e) {
            assertThat(e).isSameAs(TRANSFORMED_ERROR);
        }
    }

    @Test
    public void testGetWithTimeoutTransformsResult() throws Exception {
        final TestFuture future = new TestFuture(null);
        future.setFutureResult(new CompletedFutureResult<Integer>(UNTRANSFORMED_RESULT));
        future.handleResult(UNTRANSFORMED_RESULT);
        assertThat(future.get(100, TimeUnit.SECONDS)).isSameAs(TRANSFORMED_RESULT);
    }

    @Test
    public void testResultHandlerTransformsError() throws Exception {
        @SuppressWarnings("unchecked")
        final ResultHandler<String> handler = mock(ResultHandler.class);
        final TestFuture future = new TestFuture(handler);
        future.setFutureResult(new CompletedFutureResult<Integer>(UNTRANSFORMED_ERROR));
        future.handleErrorResult(UNTRANSFORMED_ERROR);
        verify(handler).handleErrorResult(TRANSFORMED_ERROR);
    }

    @Test
    public void testResultHandlerTransformsResult() throws Exception {
        @SuppressWarnings("unchecked")
        final ResultHandler<String> handler = mock(ResultHandler.class);
        final TestFuture future = new TestFuture(handler);
        future.setFutureResult(new CompletedFutureResult<Integer>(UNTRANSFORMED_RESULT));
        future.handleResult(UNTRANSFORMED_RESULT);
        verify(handler).handleResult(TRANSFORMED_RESULT);
    }
}
