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
 * Copyright 2016 ForgeRock AS.
 */
package org.opends.server.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Helper class to represent arguments for command-line programs. */
public class Args
{
  private final List<String> args = new ArrayList<>();

  public Args add(String arg)
  {
    args.add(arg);
    return this;
  }

  public Args add(String arg, Object value)
  {
    args.add(arg);
    args.add(value.toString());
    return this;
  }

  public Args addAll(String... additionalArgs)
  {
    if (additionalArgs != null)
    {
      Collections.addAll(args, additionalArgs);
    }
    return this;
  }

  public String[] toArray()
  {
    return args.toArray(new String[0]);
  }

  @Override
  public String toString()
  {
    return args.toString();
  }
}