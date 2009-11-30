package org.opends.sdk.util.ssl;



import java.security.cert.CertificateException;



/**
 * The certificate's subject DN's value and the host name we tried to
 * connect to do not match.
 */
@SuppressWarnings("serial")
public class HostnameMismatchCertificateException extends
    CertificateException
{
  private String expectedHostname;

  private String certificateHostname;



  public HostnameMismatchCertificateException(String expectedHostname,
      String certificateHostname)
  {
    this.expectedHostname = expectedHostname;
    this.certificateHostname = certificateHostname;
  }



  public HostnameMismatchCertificateException(String msg,
      String expectedHostname, String certificateHostname)
  {
    super(msg);
    this.expectedHostname = expectedHostname;
    this.certificateHostname = certificateHostname;
  }



  public String getExpectedHostname()
  {
    return expectedHostname;
  }



  public void setExpectedHostname(String expectedHostname)
  {
    this.expectedHostname = expectedHostname;
  }



  public String getCertificateHostname()
  {
    return certificateHostname;
  }



  public void setCertificateHostname(String certificateHostname)
  {
    this.certificateHostname = certificateHostname;
  }
}
