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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2007 Brighton Consulting, Inc.
 */
package org.opends.server.util;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.opends.server.TestCaseUtils;

import java.io.File;
import java.util.List;

/**
 * This class defines a set of tests for the
 * {@link org.opends.server.util.Crypt} class.
 */
@Test(groups = { "slow" }, sequential = true)  // Make them slow, since they are unlikely to break and since there are 4K+ they can take a while
public final class TestCrypt extends UtilTestCase {
  private Crypt crypt = new Crypt();

  /**
   * Crypt valid test data provider.  All crypts are variants of the password
   * "password".
   * 
   * @return Returns an array of all salted version of "password".
   */
  @DataProvider
  public Object[][] passwordWithAllSalts() throws Exception {
    File cryptFile = TestCaseUtils.getTestResource("password-with-all-crypt-salts.txt");
    List <String> cryptedPasswords = TestCaseUtils.readFileToLines(cryptFile);

    // Convert the passwords list into the 2D array needed expected by
    // DataProviders
    String[][] passwordArgs = new String[cryptedPasswords.size()][];
    for (int i = 0; i < cryptedPasswords.size(); i++) {
      passwordArgs[i] = new String[]{cryptedPasswords.get(i)};
    }

    return passwordArgs;
  }

  /**
   * Tests the encryption of "password" against all possible salts.
   * Because the salt is part of the crypt'ed password parameter, the
   * single parameter serves as an input parameter (the salt at the
   * start of the parameter), as well as to validate that the parameter
   * was properly encrypted.
   * 
   * @param saltedPassword
   *          A hashed value of "password".
   */
  @Test(dataProvider = "passwordWithAllSalts")
  public void testAllSalts(String saltedPassword) throws Exception
  {
    validateCryptedPassword("password", saltedPassword);
  }

  // Passowrds and their corresponding crypt values for various salts.
  // These were generated using the crypt() implementation in Perl.
  private static final Object[][] PASSWORDS_AND_CRYPTS = {
       {"", "AEypjPqkpWrm."},
       {"", "DEkEMk50GlpvQ"},
       {"", "SeF2s6N.rrxzE"},
       {"", "seGYEzzfNS6p6"},
       {"", "76gkheUg6SU6g"},
       {"", "02oawyZdjhhpg"},
       {"", "05/lfLEyErrp2"},
       {"1", "AEycSgLoZE2NU"},
       {"1", "DEcGEo4stCwKU"},
       {"1", "Selkb/5Kk.a5U"},
       {"1", "sexFKbViIp1BM"},
       {"1", "76PZeYZkUIiJY"},
       {"1", "02g/Is9QzZ59."},
       {"1", "05Nc5lRLZkOL2"},
       {"12", "AEo9g/hrWcroY"},
       {"12", "DEhWm60GBm51E"},
       {"12", "SeC1ub4/n1onQ"},
       {"12", "seLOcGzRFXQ8A"},
       {"12", "76RWqjxIlxH.s"},
       {"12", "02Yg9V0ugcDK2"},
       {"12", "05YvAHbiMsS/s"},
       {"123", "AEeiDhPbE8FZA"},
       {"123", "DEDUHYzEvFtCg"},
       {"123", "SeKFH3Nooq92c"},
       {"123", "seu.8HpuOxyTU"},
       {"123", "76t8XtqAiQew2"},
       {"123", "025QwM9.cl3TU"},
       {"123", "05pundxrmpCa."},
       {"1234", "AENOqZDrZtpEA"},
       {"1234", "DE.28cJyrukGE"},
       {"1234", "SedVHY1MtpuW."},
       {"1234", "seUc7O/85EPvQ"},
       {"1234", "76bHlu4JyJ4UU"},
       {"1234", "02YlX9ogYhIOw"},
       {"1234", "05pGklGDVsooY"},
       {"12345", "AEsOUkNAVAV/k"},
       {"12345", "DEa555/OS0gCw"},
       {"12345", "Sevs2NnMDrR52"},
       {"12345", "seWfr4VdeLxO."},
       {"12345", "76fEe.ryflQn."},
       {"12345", "02oa0q9I0b8YI"},
       {"12345", "05pIZ2uxl1Oxc"},
       {"123456", "AElA7SvR5BWYs"},
       {"123456", "DERqDk0hJcemI"},
       {"123456", "SeEsHpRQ3Ws4Y"},
       {"123456", "seldpJxxtvI7E"},
       {"123456", "76odHJ.uJuNlM"},
       {"123456", "0274Bl5Z.iP8A"},
       {"123456", "050o6La.i0hjQ"},
       {"1234567", "AEvQ6nKFdz1/Q"},
       {"1234567", "DEqKJFIOJAeP2"},
       {"1234567", "SeevFxkmw4OPM"},
       {"1234567", "seKgNYFPEl0YU"},
       {"1234567", "76Q7Ud2dSYUyY"},
       {"1234567", "02FYf5CcW.z9Y"},
       {"1234567", "053NyeyMsGY3M"},
       {"12345678", "AESEqSznA5e5o"},
       {"12345678", "DE0gVuzpcrIb2"},
       {"12345678", "Sev2LR/CJDCXo"},
       {"12345678", "seXUEz1n7cRRY"},
       {"12345678", "76fpN4iZgIRE2"},
       {"12345678", "0291YyAhKiURA"},
       {"12345678", "05tb4hpIF2mIE"},
       {"123456789", "AESEqSznA5e5o"},
       {"123456789", "DE0gVuzpcrIb2"},
       {"123456789", "Sev2LR/CJDCXo"},
       {"123456789", "seXUEz1n7cRRY"},
       {"123456789", "76fpN4iZgIRE2"},
       {"123456789", "0291YyAhKiURA"},
       {"123456789", "05tb4hpIF2mIE"},
       {"1234567890", "AESEqSznA5e5o"},
       {"1234567890", "DE0gVuzpcrIb2"},
       {"1234567890", "Sev2LR/CJDCXo"},
       {"1234567890", "seXUEz1n7cRRY"},
       {"1234567890", "76fpN4iZgIRE2"},
       {"1234567890", "0291YyAhKiURA"},
       {"1234567890", "05tb4hpIF2mIE"},
       {"!@#0^&*", "AEs3QeaYDQTJE"},
       {"!@#0^&*", "DE9RJdfpk3gEM"},
       {"!@#0^&*", "SeF/SBOVIQgbM"},
       {"!@#0^&*", "seyp8Lbs6DyRc"},
       {"!@#0^&*", "76vSuJ/ho1RxE"},
       {"!@#0^&*", "02i4O5873w8G6"},
       {"!@#0^&*", "05.ep5H/6Gb/Q"},
       {"()_+-=[]", "AE2JFPze7oG2s"},
       {"()_+-=[]", "DE77ctMC0YTs6"},
       {"()_+-=[]", "Se5tUe6lyM2nE"},
       {"()_+-=[]", "sehWSNixuAffY"},
       {"()_+-=[]", "76v/azh6t29QA"},
       {"()_+-=[]", "02.j5xgLpYyto"},
       {"()_+-=[]", "053cNRDrF.GOs"},
       {"{}\\:;\"'<", "AEnWr4wYnt/Sg"},
       {"{}\\:;\"'<", "DEveQ9wxQrdfQ"},
       {"{}\\:;\"'<", "Se6LMgmv4jYSw"},
       {"{}\\:;\"'<", "se4Lmhm8eCYvk"},
       {"{}\\:;\"'<", "76vVkpMqk8MPc"},
       {"{}\\:;\"'<", "024d3tyjpySTc"},
       {"{}\\:;\"'<", "057OTKpDRH1yk"},
       {",>.?/`~", "AEu8PanMV4Yos"},
       {",>.?/`~", "DEfL4O2tY1pbQ"},
       {",>.?/`~", "Se1o/Ln0pz53s"},
       {",>.?/`~", "sevdxlNkzBC9s"},
       {",>.?/`~", "76Oe5rgApyjqQ"},
       {",>.?/`~", "02gwWt.cN0ZW2"},
       {",>.?/`~", "05TkTNlUHKj0o"},
       {"abcdefgh", "AE5sk6ZnF2ne6"},
       {"abcdefgh", "DEgNPea/fV7o6"},
       {"abcdefgh", "SeveLGJNvFGqo"},
       {"abcdefgh", "seoDbs8R1TfGs"},
       {"abcdefgh", "76OgHaEBgUoY6"},
       {"abcdefgh", "02YewT6mKnbtc"},
       {"abcdefgh", "05SENcp2oS3N2"},
       {"ijklmnop", "AElKD7vOnptw2"},
       {"ijklmnop", "DEpcYg28J1nGY"},
       {"ijklmnop", "SemEov1ZT2QeA"},
       {"ijklmnop", "sed20nQs9.5QU"},
       {"ijklmnop", "76fgwIPFi/Yxw"},
       {"ijklmnop", "02xnTfvOZm1Fw"},
       {"ijklmnop", "053/TROBqFM5A"},
       {"qrstuvwx", "AEcIntiaWvH4U"},
       {"qrstuvwx", "DEjMRKWIS8RcE"},
       {"qrstuvwx", "Sedkn.517oB1Q"},
       {"qrstuvwx", "se7WusSyMU74A"},
       {"qrstuvwx", "76KwGhfk1D9.o"},
       {"qrstuvwx", "02NBEYIkvCEDw"},
       {"qrstuvwx", "05WDiAK7932VI"},
       {"yzABCDEF", "AEuBybr3w07MU"},
       {"yzABCDEF", "DEnh3Aa09jH5c"},
       {"yzABCDEF", "SeES5RpbP6IBg"},
       {"yzABCDEF", "sepW867O5DecA"},
       {"yzABCDEF", "76fkw0n09AlVY"},
       {"yzABCDEF", "02b4yKXa2Ezmg"},
       {"yzABCDEF", "05CwQ5.PMAOiw"},
       {"GHIJKLMN", "AEdma.Wd8OVuc"},
       {"GHIJKLMN", "DEvyOA86U40Yw"},
       {"GHIJKLMN", "Se3i1.wK7SvFw"},
       {"GHIJKLMN", "sey8LKM8jnnnY"},
       {"GHIJKLMN", "76eFLLP7Y4z2g"},
       {"GHIJKLMN", "02tr7rNC/2zeU"},
       {"GHIJKLMN", "0527iVLJRmBz6"},
       {"OPQRTSTU", "AEzOvkB5zDp2o"},
       {"OPQRTSTU", "DE6epaIuO/6Xw"},
       {"OPQRTSTU", "SemZ8Rw5UkXW2"},
       {"OPQRTSTU", "seD6BA6YFbqhg"},
       {"OPQRTSTU", "76/WPs6GC5pOU"},
       {"OPQRTSTU", "020T4ievozzgU"},
       {"OPQRTSTU", "05d/UmYKRWeME"},
       {"VWXYZ", "AE.Wv.eqyQmso"},
       {"VWXYZ", "DEM/AU49DMri2"},
       {"VWXYZ", "Se26LxmPj52qM"},
       {"VWXYZ", "sefTLuF2.KvE."},
       {"VWXYZ", "76qVtYProZCDA"},
       {"VWXYZ", "02HY6XzgJWEIU"},
       {"VWXYZ", "051moQVL2PkfU"},

       // Some non-ascii tests
       {"\u00C4", "CyaE7.kWcy.fs"},  // \xC3\x84 in UTF-8
       {"\u00C5", "AEUV1DMPEIDnA"},  // \xC3\x85 in UTF-8
       {"\u00C7", "AEpWNQaF3IUno"},  // \xC3\x87 in UTF-8
       {"\u2020", "AEboIqjs64Y0U"},  // \xE2\x80\xA0 in UTF-8
       {"\u00BF", "AE35zYeBlLaTs"},  // \xC2\xBF in UTF-8
       {"\u02C7", "AE0WyV2GLggXI"},  // \xCB\x87 in UTF-8
       {"\uF8FF", "AEa0xrpV1JyiA"},  // \xEF\xA3\xBF in UTF-8
  };

  @DataProvider
  public Object[][] passwordsAndCrypts() {
    return PASSWORDS_AND_CRYPTS;
  }

  /**
   * Tests various clear-text passwords and a corresponding crypt value.
   * We use the salt from the cryptedPassword so that we can regenerate
   * the same crypted value.
   */
  @Test(dataProvider = "passwordsAndCrypts")
  public void testVarious(String clearPassword, String cryptedPassword) throws Exception
  {
    validateCryptedPassword(clearPassword, cryptedPassword);
  }

  private void validateCryptedPassword(String clearPassword, String cryptedPassword) throws Exception
  {
    byte[] pw = clearPassword.getBytes("UTF-8");
    byte[] s = cryptedPassword.getBytes("UTF-8");

    // The first two bytes of the saltedPassword are used as the salt,
    // so the bytes that we get back from this should be
    byte[] r = crypt.crypt(pw, s);

    String st = new String(r, "UTF-8");
    Assert.assertEquals(st, cryptedPassword);
  }
}


