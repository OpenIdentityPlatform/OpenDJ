package org.opends.sdk.ldif;



import java.io.IOException;

import org.opends.sdk.requests.AddRequest;
import org.opends.sdk.requests.DeleteRequest;
import org.opends.sdk.requests.ModifyDNRequest;
import org.opends.sdk.requests.ModifyRequest;



/**
 * A visitor which can be used to write generic change records.
 */
final class ChangeRecordVisitorWriter implements
    ChangeRecordVisitor<IOException, ChangeRecordWriter>
{
  // Visitor used for writing generic change records.
  private static final ChangeRecordVisitorWriter VISITOR =
      new ChangeRecordVisitorWriter();



  /**
   * Returns the singleton instance.
   * 
   * @return The instance.
   */
  static ChangeRecordVisitorWriter getInstance()
  {
    return VISITOR;
  }



  private ChangeRecordVisitorWriter()
  {
    // Nothing to do.
  }



  public IOException visitChangeRecord(ChangeRecordWriter p,
      AddRequest change)
  {
    try
    {
      p.writeChangeRecord(change);
      return null;
    }
    catch (final IOException e)
    {
      return e;
    }
  }



  public IOException visitChangeRecord(ChangeRecordWriter p,
      DeleteRequest change)
  {
    try
    {
      p.writeChangeRecord(change);
      return null;
    }
    catch (final IOException e)
    {
      return e;
    }
  }



  public IOException visitChangeRecord(ChangeRecordWriter p,
      ModifyDNRequest change)
  {
    try
    {
      p.writeChangeRecord(change);
      return null;
    }
    catch (final IOException e)
    {
      return e;
    }
  }



  public IOException visitChangeRecord(ChangeRecordWriter p,
      ModifyRequest change)
  {
    try
    {
      p.writeChangeRecord(change);
      return null;
    }
    catch (final IOException e)
    {
      return e;
    }
  }
}
