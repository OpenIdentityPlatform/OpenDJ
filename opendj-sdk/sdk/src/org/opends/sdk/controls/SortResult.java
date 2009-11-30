package org.opends.sdk.controls;



import java.util.Arrays;
import java.util.List;

import org.opends.sdk.ResultCode;



/**
 * Created by IntelliJ IDEA. User: boli Date: Jun 30, 2009 Time: 4:33:56
 * PM To change this template use File | Settings | File Templates.
 */
public class SortResult
{
  private static final SortResult[] ELEMENTS = new SortResult[81];

  public static final SortResult SUCCESS = register(ResultCode.SUCCESS);

  public static final SortResult OPERATIONS_ERROR = register(ResultCode.OPERATIONS_ERROR);

  public static final SortResult TIME_LIMIT_EXCEEDED = register(ResultCode.TIME_LIMIT_EXCEEDED);

  public static final SortResult STRONG_AUTH_REQUIRED = register(ResultCode.STRONG_AUTH_REQUIRED);

  public static final SortResult ADMIN_LIMIT_EXCEEDED = register(ResultCode.ADMIN_LIMIT_EXCEEDED);

  public static final SortResult NO_SUCH_ATTRIBUTE = register(ResultCode.NO_SUCH_ATTRIBUTE);

  public static final SortResult INAPPROPRIATE_MATCHING = register(ResultCode.INAPPROPRIATE_MATCHING);

  public static final SortResult INSUFFICIENT_ACCESS_RIGHTS = register(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);

  public static final SortResult BUSY = register(ResultCode.BUSY);

  public static final SortResult UNWILLING_TO_PERFORM = register(ResultCode.UNWILLING_TO_PERFORM);

  public static final SortResult OTHER = register(ResultCode.OTHER);



  public static SortResult register(ResultCode resultCode)
  {
    SortResult t = new SortResult(resultCode);
    ELEMENTS[resultCode.intValue()] = t;
    return t;
  }



  public static SortResult valueOf(int intValue)
  {
    SortResult e = ELEMENTS[intValue];
    if (e == null)
    {
      e = new SortResult(ResultCode.valueOf(intValue));
    }
    return e;
  }



  public static List<SortResult> values()
  {
    return Arrays.asList(ELEMENTS);
  }



  private final ResultCode resultCode;



  private SortResult(ResultCode resultCode)
  {
    this.resultCode = resultCode;
  }



  @Override
  public boolean equals(Object o)
  {
    return (this == o)
        || ((o instanceof SortResult) && resultCode.equals(o));

  }



  @Override
  public int hashCode()
  {
    return resultCode.hashCode();
  }



  public int intValue()
  {
    return resultCode.intValue();
  }



  @Override
  public String toString()
  {
    return resultCode.toString();
  }
}
