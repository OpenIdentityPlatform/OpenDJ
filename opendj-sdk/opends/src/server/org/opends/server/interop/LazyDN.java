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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.interop;



import org.opends.server.types.DN;
import org.opends.server.types.RDN;

import static org.opends.server.loggers.Debug.*;
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
public class LazyDN
       extends DN
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME = "org.opends.server.interop.LazyDN";



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
    assert debugConstructor(CLASS_NAME, String.valueOf(dnString));

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
    assert debugEnter(CLASS_NAME, "isNullDN");

    return getDecodedDN().isNullDN();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public int getNumComponents()
         throws RuntimeException
  {
    assert debugEnter(CLASS_NAME, "getNumComponents");

    return getDecodedDN().getNumComponents();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public RDN getRDN()
         throws RuntimeException
  {
    assert debugEnter(CLASS_NAME, "getRDN");

    return getDecodedDN().getRDN();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public RDN getRDN(int pos)
         throws RuntimeException
  {
    assert debugEnter(CLASS_NAME, "getRDN", String.valueOf(pos));

    return getDecodedDN().getRDN(pos);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public DN getParent()
         throws RuntimeException
  {
    assert debugEnter(CLASS_NAME, "getParent");

    return getDecodedDN().getParent();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public DN getParentDNInSuffix()
         throws RuntimeException
  {
    assert debugEnter(CLASS_NAME, "getParentDNInSuffix");

    return getDecodedDN().getParentDNInSuffix();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public DN concat(RDN rdn)
         throws RuntimeException
  {
    assert debugEnter(CLASS_NAME, "concat", String.valueOf(rdn));

    return getDecodedDN().concat(rdn);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public DN concat(RDN[] rdnComponents)
         throws RuntimeException
  {
    assert debugEnter(CLASS_NAME, "concat", String.valueOf(rdnComponents));

    return getDecodedDN().concat(rdnComponents);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public DN concat(DN relativeBaseDN)
         throws RuntimeException
  {
    assert debugEnter(CLASS_NAME, "concat", String.valueOf(relativeBaseDN));

    return getDecodedDN().concat(relativeBaseDN);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isDescendantOf(DN dn)
         throws RuntimeException
  {
    assert debugEnter(CLASS_NAME, "isDescendantOf", String.valueOf(dn));

    return getDecodedDN().isDescendantOf(dn);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isAncestorOf(DN dn)
         throws RuntimeException
  {
    assert debugEnter(CLASS_NAME, "isAncestorOf", String.valueOf(dn));

    return getDecodedDN().isAncestorOf(dn);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean equals(Object o)
         throws RuntimeException
  {
    assert debugEnter(CLASS_NAME, "equals", String.valueOf(o));

    return getDecodedDN().equals(o);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public int hashCode()
         throws RuntimeException
  {
    assert debugEnter(CLASS_NAME, "hashCode");

    return getDecodedDN().hashCode();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public String toString()
  {
    assert debugEnter(CLASS_NAME, "toString");

    return dnString;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void toString(StringBuilder buffer)
  {
    assert debugEnter(CLASS_NAME, "toString", "java.lang.StringBuilder");

    buffer.append(dnString);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public String toNormalizedString()
         throws RuntimeException
  {
    assert debugEnter(CLASS_NAME, "toNormalizedString");

    return getDecodedDN().toNormalizedString();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void toNormalizedString(StringBuilder buffer)
         throws RuntimeException
  {
    assert debugEnter(CLASS_NAME, "toNormalizedString",
                      "java.lang.StringBuilder");

    getDecodedDN().toNormalizedString(buffer);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public int compareTo(DN dn)
         throws RuntimeException
  {
    assert debugEnter(CLASS_NAME, "compareTo", String.valueOf(dn));

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
    assert debugEnter(CLASS_NAME, "getDecodedDN");

    if (decodedDN == null)
    {
      try
      {
        decodedDN = DN.decode(dnString);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "getDecodedDN", e);

        throw new RuntimeException(stackTraceToSingleLineString(e));
      }
    }

    return decodedDN;
  }
}

