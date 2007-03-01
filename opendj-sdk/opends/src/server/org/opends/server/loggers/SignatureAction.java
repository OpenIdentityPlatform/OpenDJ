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
package org.opends.server.loggers;

//import java.io.BufferedInputStream;
import java.io.File;
//import java.io.FileInputStream;
//import java.io.RandomAccessFile;
//import java.security.MessageDigest;
//import java.security.PrivateKey;
//import java.security.PublicKey;
//import java.security.Signature;
//import java.security.cert.X509Certificate;
//import javax.net.ssl.KeyManager;
//import javax.net.ssl.X509KeyManager;

//import org.opends.server.core.DirectoryServer;

/**
 * This class implements a post rotation action that signs
 * the file.
 */
public class SignatureAction implements PostRotationAction
{

  private static final String delimiter = "---------";
  private File originalFile;
  private String signatureAlgorithm = "SHA1withRSA";
  private String digestAlgorithm = "SHA";
  private String alias = null;

  /**
   * Create the signature action based on the log file name,
   * and the certificate alias to use for signing.
   *
   * @param origFile    The source file name to sign.
   * @param alias       The certificate alias to use for signing.
   */
  public SignatureAction(String origFile, String alias)
  {
    this.originalFile = new File(origFile);
    this.alias = alias;
  }

  /**
   * Create the signature action based on the log file name,
   * the signature algorithm, the digest algorithm, and the certificate alias
   * to use for signing.
   *
   * @param origFile    The source file name to sign.
   * @param sigAlg      The signature algorithm to use.
   * @param digestAlg   The MD5 digest algorithm to use.
   * @param alias       The certificate alias to use for signing.
   */
  public SignatureAction(String origFile, String sigAlg, String digestAlg,
                         String alias)
  {
    this.originalFile = new File(origFile);
    this.signatureAlgorithm = sigAlg;
    this.digestAlgorithm = digestAlg;
    this.alias = alias;
  }

  /**
   * The signature action that is executed. Returns true if the
   * action succeeded and false otherwise.
   *
   * @return  <CODE>true</CODE> if the signature was generated successfully, or
   *          <CODE>false</CODE> if not.
   */
  public boolean execute()
  {
    // FIXME -- It is currently not possible to sign on rotate because of the
    // way that they key manager providers are defined.  However, this function
    // wasn't implemented in an ideal fashion anyway, so the signing capability
    // should remain disabled until the rotation action mechanism is rewritten.
    // The original code has been preserved here for reference purposes.
    return false;
//
//    FileInputStream fis = null;
//    boolean inputStreamOpen = false;
//    try
//    {
//      KeyManager[] keyMgrs =
//           DirectoryServer.getKeyManagerProvider().getKeyManagers();
//      if(keyMgrs.length == 0)
//      {
//        // No keys available.
//        // FIXME - Log in error log.
//        System.err.println("No private key available to sign with.");
//        return false;
//      }
//      X509KeyManager mgr = (X509KeyManager) keyMgrs[0];
//      PrivateKey priv = mgr.getPrivateKey(alias);
//
//      Signature sig = Signature.getInstance(signatureAlgorithm);
//      sig.initSign(priv);
//
//      MessageDigest md = MessageDigest.getInstance(digestAlgorithm);
//      md.reset();
//
//      fis = new FileInputStream(originalFile);
//      inputStreamOpen = true;
//      BufferedInputStream bufin = new BufferedInputStream(fis);
//      byte[] buffer = new byte[1024];
//      int len;
//      while (bufin.available() != 0)
//      {
//        len = bufin.read(buffer);
//        md.update(buffer, 0, len);
//      }
//      bufin.close();
//
//      // Create a hash of the log file contents.
//      byte[] hash = md.digest();
//      // printBytes(hash);
//      sig.update(hash);
//
//      // Sign the hash.
//      byte[] realSig = sig.sign();
//      // printBytes(realSig);
//
//      // Append the signature to the end of the file.
//      RandomAccessFile raf = new RandomAccessFile(originalFile, "rw");
//      raf.seek(raf.length());
//      raf.write(delimiter.getBytes());
//      raf.write("\n".getBytes());
//      raf.write(realSig);
//
//      return true;
//    } catch(Exception ioe)
//    {
//      assert debugException(CLASS_NAME, "execute", ioe);
//      if(inputStreamOpen)
//      {
//        try
//        {
//          fis.close();
//        } catch(Exception fe)
//        {
//                assert debugException(CLASS_NAME, "execute", fe);
//          // Cannot do much. Ignore.
//        }
//      }
//      return false;
//    }
  }


  /**
   * Verify the signature int the log file. Returns true if the
   * the signature is valid and false otherwise.
   *
   * @return  <CODE>true</CODE> if the signature is valid, or <CODE>false</CODE>
   *          if not.
   */
  public boolean verify()
  {
    // FIXME -- It is currently not possible to sign on rotate because of the
    // way that they key manager providers are defined.  However, this function
    // wasn't implemented in an ideal fashion anyway, so the signing capability
    // should remain disabled until the rotation action mechanism is rewritten.
    // The original code has been preserved here for reference purposes.
    return false;
//    RandomAccessFile inFile = null;
//    boolean inputStreamOpen = false;
//    try
//    {
//      KeyManager[] keyMgrs =
//           DirectoryServer.getKeyManagerProvider().getKeyManagers();
//
//      if(keyMgrs.length == 0)
//      {
//        // No keys available.
//        // FIXME - Log in error log.
//        System.err.println("No public key available to verify signature.");
//        return false;
//      }
//
//      X509KeyManager mgr = (X509KeyManager) keyMgrs[0];
//      X509Certificate[] certChain = mgr.getCertificateChain(alias);
//
//      if(certChain == null || certChain.length == 0)
//      {
//        System.err.println("Cannot find the public key for the signature.");
//        return false;
//      }
//
//      PublicKey pubKey = certChain[0].getPublicKey();
//
//      Signature sig = Signature.getInstance(signatureAlgorithm);
//      sig.initVerify(pubKey);
//
//      MessageDigest md = MessageDigest.getInstance(digestAlgorithm);
//      md.reset();
//
//      inFile = new RandomAccessFile(originalFile, "r");
//      inputStreamOpen = true;
//      String line = null;
//      while ((line = inFile.readLine()) != null)
//      {
//        if(line.equals(delimiter))
//        {
//          break;
//        }
//        // int len = line.length();
//        // md.update(line.getBytes(), 0, len);
//        byte[] b = (line + "\n").getBytes();
//        md.update(b);
//      }
//
//      // Read signature
//      byte[] sigToVerify = new byte[128];
//      int val = inFile.read(sigToVerify, 0, 128);
//      // printBytes(sigToVerify);
//
//      // Create a hash of the log file contents.
//      byte[] hash = md.digest();
//      // printBytes(hash);
//      sig.update(hash);
//
//
//      // Verify the hash.
//      boolean verifies = sig.verify(sigToVerify);
//
//      return verifies;
//    } catch(Exception ioe)
//    {
//      assert debugException(CLASS_NAME, "execute", ioe);
//      if(inputStreamOpen)
//      {
//        try
//        {
//          inFile.close();
//        } catch(Exception fe)
//        {
//                assert debugException(CLASS_NAME, "execute", fe);
//          // Cannot do much. Ignore.
//        }
//      }
//      return false;
//    }
  }



  /**
   * Prints a representation of the contents of the provided byte array to
   * standard output.
   *
   * @param  bArray  The array containing the data to be printed.
   */
  private void printBytes(byte[] bArray)
  {
    for(int i = 0; i < bArray.length; i++)
    {
      System.out.print(Integer.toHexString((int)bArray[i]));
    }
    System.out.println("");
  }


}

