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
 * Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


final class Utils
{
  public static void assertIdsEquals(Iterator<EntryID> actual, long... expected)
  {
    assertThat(actual).toIterable().containsAll(asList(expected));
  }

  public static void assertIsEmpty(EntryIDSet actual)
  {
    assertIdsEquals(actual);
  }

  public static void assertIdsEquals(EntryIDSet actual, long... expected)
  {
    // needed is undefined EntryIDSet" => "needed since undefined EntryIDSet
    assertThat(actual.isDefined());
    assertIdsEquals(actual.iterator(), expected);
  }

  public static EntryID id(long id) {
    return new EntryID(id);
  }

  private static List<EntryID> asList(long... array) {
    List<EntryID> list = new ArrayList<>(array.length);
    for(long l : array) {
      list.add(new EntryID(l));
    }
    return list;
  }

  private Utils()
  {
    // Hide consutrctor
  }


}
