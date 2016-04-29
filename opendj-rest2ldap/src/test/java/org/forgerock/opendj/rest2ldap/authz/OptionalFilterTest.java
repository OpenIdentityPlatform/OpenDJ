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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap.authz;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.opendj.rest2ldap.authz.OptionalFilter.Condition;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.testng.ForgeRockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class OptionalFilterTest extends ForgeRockTestCase {

    private OptionalFilter optionalFilter;
    private Filter filter;
    private Condition condition;

    @BeforeMethod
    public void setUp() {
        filter = mock(Filter.class);
        condition = mock(Condition.class);
        optionalFilter = new OptionalFilter(filter, condition);
    }

    @Test
    public void testFilterNotAppliedIfConditionIsFalse() {
        when(condition.canApplyFilter(any(Context.class), any(Request.class))).thenReturn(false);

        optionalFilter.filter(new RootContext(), new Request(), mock(Handler.class));

        verify(filter, never()).filter(any(RootContext.class), any(Request.class), any(Handler.class));
    }

    @Test
    public void testFilterAppliedIfConditionIsTrue() {
        when(condition.canApplyFilter(any(Context.class), any(Request.class))).thenReturn(true);

        final Context context = new RootContext();
        final Request request = new Request();
        final Handler handler = mock(Handler.class);
        optionalFilter.filter(context, request, handler);

        verify(filter).filter(same(context), same(request), same(handler));
    }
}
