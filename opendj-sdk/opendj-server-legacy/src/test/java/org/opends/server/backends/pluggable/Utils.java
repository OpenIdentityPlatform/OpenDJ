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
 *      Portions Copyright 2013-2015 ForgeRock AS
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
    assertThat(actual).containsAll(asList(expected));
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
