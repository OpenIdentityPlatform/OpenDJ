/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2012 ForgeRock AS.
 */

package com.forgerock.opendj.ldap;



import org.forgerock.opendj.ldap.ConnectionSecurityLayer;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.glassfish.grizzly.AbstractTransformer;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.TransformationResult;
import org.glassfish.grizzly.attributes.AttributeStorage;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;



/**
 * <tt>Transformer</tt>, which encodes SASL encrypted data, contained in the
 * input Buffer, to the output Buffer.
 */
final class SASLEncoderTransformer extends AbstractTransformer<Buffer, Buffer>
{
  private static final int BUFFER_SIZE = 4096;
  private final byte[] buffer = new byte[BUFFER_SIZE];
  private final ConnectionSecurityLayer bindContext;

  private final MemoryManager<?> memoryManager;



  public SASLEncoderTransformer(final ConnectionSecurityLayer bindContext,
      final MemoryManager<?> memoryManager)
  {
    this.bindContext = bindContext;
    this.memoryManager = memoryManager;
  }



  public String getName()
  {
    return this.getClass().getName();
  }



  public boolean hasInputRemaining(final AttributeStorage storage,
      final Buffer input)
  {
    return input != null && input.hasRemaining();
  }



  @Override
  public TransformationResult<Buffer, Buffer> transformImpl(
      final AttributeStorage storage, final Buffer input)
  {

    final int len = Math.min(buffer.length, input.remaining());
    input.get(buffer, 0, len);

    try
    {
      final Buffer output = Buffers.wrap(memoryManager, bindContext.wrap(
          buffer, 0, len));
      return TransformationResult.createCompletedResult(output, input);
    }
    catch (final ErrorResultException e)
    {
      return TransformationResult.createErrorResult(e.getResult()
          .getResultCode().intValue(), e.getMessage());
    }
  }
}
