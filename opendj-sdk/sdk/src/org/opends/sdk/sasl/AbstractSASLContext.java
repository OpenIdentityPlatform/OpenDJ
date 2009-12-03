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

package org.opends.sdk.sasl;



import static com.sun.opends.sdk.util.Messages.*;

import java.io.IOException;

import javax.security.auth.callback.*;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.RealmChoiceCallback;
import javax.security.sasl.SaslException;



/**
 * An abstract SASL context.
 */
public abstract class AbstractSASLContext implements SASLContext,
    CallbackHandler
{
  /**
   * The name of the default protocol used.
   */
  protected static final String SASL_DEFAULT_PROTOCOL = "ldap";



  public void handle(Callback[] callbacks) throws IOException,
      UnsupportedCallbackException
  {
    for (Callback callback : callbacks)
    {
      if (callback instanceof NameCallback)
      {
        handle((NameCallback) callback);
      }
      else if (callback instanceof PasswordCallback)
      {
        handle((PasswordCallback) callback);
      }
      else if (callback instanceof AuthorizeCallback)
      {
        handle((AuthorizeCallback) callback);
      }
      else if (callback instanceof RealmCallback)
      {
        handle((RealmCallback) callback);
      }
      else if (callback instanceof RealmChoiceCallback)
      {
        handle((RealmChoiceCallback) callback);
      }
      else if (callback instanceof ChoiceCallback)
      {
        handle((ChoiceCallback) callback);
      }
      else if (callback instanceof ConfirmationCallback)
      {
        handle((ConfirmationCallback) callback);
      }
      else if (callback instanceof LanguageCallback)
      {
        handle((LanguageCallback) callback);
      }
      else if (callback instanceof TextInputCallback)
      {
        handle((TextInputCallback) callback);
      }
      else if (callback instanceof TextOutputCallback)
      {
        handle((TextOutputCallback) callback);
      }
      else
      {
        com.sun.opends.sdk.util.Message message =
            INFO_SASL_UNSUPPORTED_CALLBACK.get(getSASLBindRequest()
                .getSASLMechanism(), String.valueOf(callback));
        throw new UnsupportedCallbackException(callback, message
            .toString());
      }
    }
  }



  /**
   * Default implementation just returns the copy of the bytes.
   */
  public byte[] unwrap(byte[] incoming, int offset, int len)
      throws SaslException
  {
    byte[] copy = new byte[len];
    System.arraycopy(incoming, offset, copy, 0, len);
    return copy;
  }



  /**
   * Default implemenation just returns the copy of the bytes.
   */
  public byte[] wrap(byte[] outgoing, int offset, int len)
      throws SaslException
  {
    byte[] copy = new byte[len];
    System.arraycopy(outgoing, offset, copy, 0, len);
    return copy;
  }



  protected void handle(AuthorizeCallback callback)
      throws UnsupportedCallbackException
  {
    com.sun.opends.sdk.util.Message message =
        INFO_SASL_UNSUPPORTED_CALLBACK.get(getSASLBindRequest()
            .getSASLMechanism(), String.valueOf(callback));
    throw new UnsupportedCallbackException(callback, message.toString());
  }



  protected void handle(ChoiceCallback callback)
      throws UnsupportedCallbackException
  {
    com.sun.opends.sdk.util.Message message =
        INFO_SASL_UNSUPPORTED_CALLBACK.get(getSASLBindRequest()
            .getSASLMechanism(), String.valueOf(callback));
    throw new UnsupportedCallbackException(callback, message.toString());
  }



  protected void handle(ConfirmationCallback callback)
      throws UnsupportedCallbackException
  {
    com.sun.opends.sdk.util.Message message =
        INFO_SASL_UNSUPPORTED_CALLBACK.get(getSASLBindRequest()
            .getSASLMechanism(), String.valueOf(callback));
    throw new UnsupportedCallbackException(callback, message.toString());
  }



  protected void handle(LanguageCallback callback)
      throws UnsupportedCallbackException
  {
    com.sun.opends.sdk.util.Message message =
        INFO_SASL_UNSUPPORTED_CALLBACK.get(getSASLBindRequest()
            .getSASLMechanism(), String.valueOf(callback));
    throw new UnsupportedCallbackException(callback, message.toString());
  }



  protected void handle(NameCallback callback)
      throws UnsupportedCallbackException
  {
    com.sun.opends.sdk.util.Message message =
        INFO_SASL_UNSUPPORTED_CALLBACK.get(getSASLBindRequest()
            .getSASLMechanism(), String.valueOf(callback));
    throw new UnsupportedCallbackException(callback, message.toString());
  }



  protected void handle(PasswordCallback callback)
      throws UnsupportedCallbackException
  {
    com.sun.opends.sdk.util.Message message =
        INFO_SASL_UNSUPPORTED_CALLBACK.get(getSASLBindRequest()
            .getSASLMechanism(), String.valueOf(callback));
    throw new UnsupportedCallbackException(callback, message.toString());
  }



  protected void handle(RealmCallback callback)
      throws UnsupportedCallbackException
  {
    com.sun.opends.sdk.util.Message message =
        INFO_SASL_UNSUPPORTED_CALLBACK.get(getSASLBindRequest()
            .getSASLMechanism(), String.valueOf(callback));
    throw new UnsupportedCallbackException(callback, message.toString());
  }



  protected void handle(RealmChoiceCallback callback)
      throws UnsupportedCallbackException
  {
    com.sun.opends.sdk.util.Message message =
        INFO_SASL_UNSUPPORTED_CALLBACK.get(getSASLBindRequest()
            .getSASLMechanism(), String.valueOf(callback));
    throw new UnsupportedCallbackException(callback, message.toString());
  }



  protected void handle(TextInputCallback callback)
      throws UnsupportedCallbackException
  {
    com.sun.opends.sdk.util.Message message =
        INFO_SASL_UNSUPPORTED_CALLBACK.get(getSASLBindRequest()
            .getSASLMechanism(), String.valueOf(callback));
    throw new UnsupportedCallbackException(callback, message.toString());
  }



  protected void handle(TextOutputCallback callback)
      throws UnsupportedCallbackException
  {
    com.sun.opends.sdk.util.Message message =
        INFO_SASL_UNSUPPORTED_CALLBACK.get(getSASLBindRequest()
            .getSASLMechanism(), String.valueOf(callback));
    throw new UnsupportedCallbackException(callback, message.toString());
  }
}
