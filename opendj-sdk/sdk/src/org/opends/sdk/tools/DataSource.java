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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk.tools;



import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import com.sun.opends.sdk.util.Validator;



/**
 * A source of data for performance tools.
 */
final class DataSource
{
  private static interface IDataSource
  {
    public Object getData();



    public IDataSource duplicate();
  }



  private static class RandomNumberDataSource implements IDataSource
  {
    private final Random random;
    private final int offset;
    private final int range;



    public RandomNumberDataSource(long seed, int low, int high)
    {
      random = new Random(seed);
      offset = low;
      range = high - low;
    }



    public Object getData()
    {
      return random.nextInt(range) + offset;
    }



    public IDataSource duplicate()
    {
      // There is no state info so threads can just share one instance.
      return this;
    }
  }



  private static class IncrementNumberDataSource implements IDataSource
  {
    private final int low;
    private int next;
    private final int high;



    public IncrementNumberDataSource(int low, int high)
    {
      this.low = this.next = low;
      this.high = high;
    }



    public Object getData()
    {
      if (next == high)
      {
        next = low;
        return high;
      }

      return next++;
    }



    public IDataSource duplicate()
    {
      return new IncrementNumberDataSource(low, high);
    }
  }



  private static class RandomLineFileDataSource implements IDataSource
  {
    private final List<String> lines;
    private final Random random;



    public RandomLineFileDataSource(long seed, String file)
        throws IOException
    {
      lines = new ArrayList<String>();
      random = new Random(seed);
      BufferedReader in = new BufferedReader(new FileReader(file));
      String line;
      while ((line = in.readLine()) != null)
      {
        lines.add(line);
      }
    }



    public Object getData()
    {
      return lines.get(random.nextInt(lines.size()));
    }



    public IDataSource duplicate()
    {
      return this;
    }
  }



  private static class IncrementLineFileDataSource implements
      IDataSource
  {
    private final List<String> lines;
    private int next;



    public IncrementLineFileDataSource(String file) throws IOException
    {
      lines = new ArrayList<String>();
      BufferedReader in = new BufferedReader(new FileReader(file));
      String line;
      while ((line = in.readLine()) != null)
      {
        lines.add(line);
      }
    }



    private IncrementLineFileDataSource(List<String> lines)
    {
      this.lines = lines;
    }



    public Object getData()
    {
      if (next == lines.size())
      {
        next = 0;
      }

      return lines.get(next++);
    }



    public IDataSource duplicate()
    {
      return new IncrementLineFileDataSource(lines);
    }
  }



  private static class RandomStringDataSource implements IDataSource
  {
    private final Random random;
    private final int length;
    private final Character[] charSet;



    private RandomStringDataSource(int seed, int length, String charSet)
    {
      this.length = length;
      Set<Character> chars = new HashSet<Character>();
      for (int i = 0; i < charSet.length(); i++)
      {
        char c = charSet.charAt(i);
        if (c == '[')
        {
          i += 1;
          char start = charSet.charAt(i);
          i += 2;
          char end = charSet.charAt(i);
          i += 1;
          for (int j = start; j <= end; j++)
          {
            chars.add((char) j);
          }
        }
        else
        {
          chars.add(c);
        }
      }
      this.charSet = chars.toArray(new Character[chars.size()]);
      this.random = new Random(seed);
    }



    public Object getData()
    {
      char[] str = new char[length];
      for (int i = 0; i < length; i++)
      {
        str[i] = charSet[random.nextInt(charSet.length)];
      }
      return new String(str);
    }



    public IDataSource duplicate()
    {
      return this;
    }
  }



  private static class StaticDataSource implements IDataSource
  {
    private final Object data;



    private StaticDataSource(Object data)
    {
      this.data = data;
    }



    public Object getData()
    {
      return data;
    }



    public IDataSource duplicate()
    {
      // There is no state info so threads can just share one instance.
      return this;
    }
  }

  private IDataSource impl;



  private DataSource(IDataSource impl)
  {
    this.impl = impl;
  }



  public Object getData()
  {
    return impl.getData();
  }



  public DataSource duplicate()
  {
    IDataSource dup = impl.duplicate();
    if (dup == impl)
    {
      return this;
    }
    else
    {
      return new DataSource(dup);
    }
  }



  /**
   * Parses a list of source definitions into an array of data source
   * objects. A data source is defined as follows: - rand({min},{max})
   * generates a random integer between the min and max. -
   * rand({filename}) retrieves a random line from a file. -
   * inc({min},{max}) returns incremental integer between the min and
   * max. - inc({filename}) retrieves lines in order from a file. -
   * {number} always return the integer as given. - {string} always
   * return the string as given.
   * 
   * @param sources
   *          The list of source definitions to parse.
   * @return The array of parsed data sources.
   * @throws IOException
   *           If an exception occurs while reading a file.
   */
  public static DataSource[] parse(List<String> sources)
      throws IOException
  {
    Validator.ensureNotNull(sources);
    DataSource[] dataSources = new DataSource[sources.size()];
    for (int i = 0; i < sources.size(); i++)
    {
      String dataSourceDef = sources.get(i);
      if (dataSourceDef.startsWith("rand(")
          && dataSourceDef.endsWith(")"))
      {
        int lparenPos = dataSourceDef.indexOf("(");
        int commaPos = dataSourceDef.indexOf(",");
        int rparenPos = dataSourceDef.indexOf(")");
        if (commaPos < 0)
        {
          // This is a file name
          dataSources[i] =
              new DataSource(new RandomLineFileDataSource(0,
                  dataSourceDef.substring(lparenPos + 1, rparenPos)));
        }
        else
        {
          // This range of integers
          int low =
              Integer.parseInt(dataSourceDef.substring(lparenPos + 1,
                  commaPos));
          int high =
              Integer.parseInt(dataSourceDef.substring(commaPos + 1,
                  rparenPos));
          dataSources[i] =
              new DataSource(new RandomNumberDataSource(0, low, high));
        }
      }
      else if (dataSourceDef.startsWith("randstr(")
          && dataSourceDef.endsWith(")"))
      {
        int lparenPos = dataSourceDef.indexOf("(");
        int commaPos = dataSourceDef.indexOf(",");
        int rparenPos = dataSourceDef.indexOf(")");
        int length;
        String charSet;
        if (commaPos < 0)
        {
          length =
              Integer.parseInt(dataSourceDef.substring(lparenPos + 1,
                  rparenPos));
          charSet = "[A-Z][a-z][0-9]";
        }
        else
        {
          // length and charSet
          length =
              Integer.parseInt(dataSourceDef.substring(lparenPos + 1,
                  commaPos));
          charSet = dataSourceDef.substring(commaPos + 1, rparenPos);
        }
        dataSources[i] =
            new DataSource(new RandomStringDataSource(0, length,
                charSet));

      }
      else if (dataSourceDef.startsWith("inc(")
          && dataSourceDef.endsWith(")"))
      {
        int lparenPos = dataSourceDef.indexOf("(");
        int commaPos = dataSourceDef.indexOf(",");
        int rparenPos = dataSourceDef.indexOf(")");
        if (commaPos < 0)
        {
          // This is a file name
          dataSources[i] =
              new DataSource(new IncrementLineFileDataSource(
                  dataSourceDef.substring(lparenPos + 1, rparenPos)));
        }
        else
        {
          int low =
              Integer.parseInt(dataSourceDef.substring(lparenPos + 1,
                  commaPos));
          int high =
              Integer.parseInt(dataSourceDef.substring(commaPos + 1,
                  rparenPos));
          dataSources[i] =
              new DataSource(new IncrementNumberDataSource(low, high));
        }
      }
      else
      {
        try
        {
          dataSources[i] =
              new DataSource(new StaticDataSource(Integer
                  .parseInt(dataSourceDef)));
        }
        catch (NumberFormatException nfe)
        {
          dataSources[i] =
              new DataSource(new StaticDataSource(dataSourceDef));
        }
      }
    }

    return dataSources;
  }



  /**
   * Returns Generated data from the specified data sources. Generated
   * data will be placed in the specified data array. If the data array
   * is null or smaller than the number of data sources, one will be
   * allocated.
   * 
   * @param dataSources
   *          Data sources that will generate arguments referenced by
   *          the format specifiers in the format string.
   * @param data
   *          The array where genereated data will be placed to format
   *          the string.
   * @return A formatted string
   */
  public static Object[] generateData(DataSource[] dataSources,
      Object[] data)
  {
    if (data == null || data.length < dataSources.length)
    {
      data = new Object[dataSources.length];
    }
    for (int i = 0; i < dataSources.length; i++)
    {
      data[i] = dataSources[i].getData();
    }
    return data;
  }
}
