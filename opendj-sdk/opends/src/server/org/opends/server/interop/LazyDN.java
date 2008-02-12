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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.interop;



import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.RDN;
import org.opends.server.types.SearchScope;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides an implementation of a {@code DN} which is lazily
 * initialized.  It may be created using only a string representation and no
 * decoding will be performed as long as only the string representation is
 * accessed.  If any methods are called which require the decoded DN, this class
 * will attempt to decode the DN string as a DN and then invoke the
 * corresponding method on the decoded version.  If any error occurs while
 * trying to decode the provided string as a DN, then a {@code RuntimeException}
 * will be thrown.
 * <BR><BR>
 * Note that this implementation is only intended for use in cases in which the
 * DN is only needed as a string representation (in particular, only the
 * {@code toString} methods will be used).  For cases in which any other methods
 * will need to be invoked on the object, the {@code org.opends.server.types.DN}
 * class should be used instead.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true,
     notes="This is only intended for use if a DN will ever only be treated " +
           "as a string and will not be transferred or processed in any way.")
public class LazyDN
       extends DN
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The serial version identifier required to satisfy the compiler because this
   * class implements the {@code java.io.Serializable} interface.  This value
   * was generated using the {@code serialver} command-line utility included
   * with the Java SDK.
   */
  private static final long serialVersionUID = -7461952029886247893L;



  // The decoded form of this DN.
  private DN decodedDN;

  // The string representation of this DN.
  private String dnString;



  /**
   * Creates a new lazily-initialized DN with the provided string
   * representation.
   *
   * @param  dnString  The string representation to use for this
   *                   lazily-initialized DN.
   */
  public LazyDN(String dnString)
  {
    this.dnString  = dnString;
    this.decodedDN = null;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isNullDN()
         throws RuntimeException
  {
    return getDecodedDN().isNullDN();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public int getNumComponents()
         throws RuntimeException
  {
    return getDecodedDN().getNumComponents();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public RDN getRDN()
         throws RuntimeException
  {
    return getDecodedDN().getRDN();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public RDN getRDN(int pos)
         throws RuntimeException
  {
    return getDecodedDN().getRDN(pos);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public DN getParent()
         throws RuntimeException
  {
    return getDecodedDN().getParent();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public DN getParentDNInSuffix()
         throws RuntimeException
  {
    return getDecodedDN().getParentDNInSuffix();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public DN concat(RDN rdn)
         throws RuntimeException
  {
    return getDecodedDN().concat(rdn);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public DN concat(RDN[] rdnComponents)
         throws RuntimeException
  {
    return getDecodedDN().concat(rdnComponents);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public DN concat(DN relativeBaseDN)
         throws RuntimeException
  {
    return getDecodedDN().concat(relativeBaseDN);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isDescendantOf(DN dn)
         throws RuntimeException
  {
    return getDecodedDN().isDescendantOf(dn);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isAncestorOf(DN dn)
         throws RuntimeException
  {
    return getDecodedDN().isAncestorOf(dn);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean matchesBaseAndScope(DN baseDN, SearchScope scope)
         throws RuntimeException
  {
    return getDecodedDN().matchesBaseAndScope(baseDN, scope);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean equals(Object o)
         throws RuntimeException
  {
    return getDecodedDN().equals(o);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public int hashCode()
         throws RuntimeException
  {
    return getDecodedDN().hashCode();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public String toString()
  {
    return dnString;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void toString(StringBuilder buffer)
  {
    buffer.append(dnString);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public String toNormalizedString()
         throws RuntimeException
  {
    return getDecodedDN().toNormalizedString();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void toNormalizedString(StringBuilder buffer)
         throws RuntimeException
  {
    getDecodedDN().toNormalizedString(buffer);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public int compareTo(DN dn)
         throws RuntimeException
  {
    return getDecodedDN().compareTo(dn);
  }



  /**
   * Retrieves a {@code DN} object that is decoded from the string
   * representation.
   *
   * @throws  RuntimeException  If an error occurs while attempting to decode
   *                            the DN string as a DN.
   */
  private DN getDecodedDN()
          throws RuntimeException
  {
    if (decodedDN == null)
    {
      try
      {
        decodedDN = DN.decode(dnString);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        throw new RuntimeException(stackTraceToSingleLineString(e));
      }
    }

    return decodedDN;
  }
}

