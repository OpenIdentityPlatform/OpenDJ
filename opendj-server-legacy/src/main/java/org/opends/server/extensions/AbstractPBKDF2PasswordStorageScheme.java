package org.opends.server.extensions;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.Base64;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.server.PBKDF2PasswordStorageSchemeCfg;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.List;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.util.StaticUtils.getExceptionMessage;

abstract class AbstractPBKDF2PasswordStorageScheme
        extends PasswordStorageScheme<PBKDF2PasswordStorageSchemeCfg>
        implements ConfigurationChangeListener<PBKDF2PasswordStorageSchemeCfg>
{
    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /** The number of bytes of random data to use as the salt when generating the hashes. */
    private static final int NUM_SALT_BYTES = 8;

    /** The chosen digest algorithm. */
    private SecretKeyFactory keyFactory;

    /** The secure random number generator to use to generate the salt values. */
    private SecureRandom random;

    /** The current configuration for this storage scheme. */
    private volatile PBKDF2PasswordStorageSchemeCfg config;

    /**
     * Creates a new instance of this password storage scheme.  Note that no
     * initialization should be performed here, as all initialization should be
     * done in the <CODE>initializePasswordStorageScheme</CODE> method.
     */
    public AbstractPBKDF2PasswordStorageScheme()
    {
        super();
    }

    @Override
    public void initializePasswordStorageScheme(PBKDF2PasswordStorageSchemeCfg configuration)
            throws ConfigException, InitializationException
    {
        try
        {
            random = new SecureRandom();
            // Initialize the digest algorithm
            keyFactory = SecretKeyFactory.getInstance(getMessageDigestAlgorithm());
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new InitializationException(null);
        }

        this.config = configuration;
        config.addPBKDF2ChangeListener(this);
    }

    @Override
    public boolean isConfigurationChangeAcceptable(PBKDF2PasswordStorageSchemeCfg configuration,
                                                   List<LocalizableMessage> unacceptableReasons)
    {
        return true;
    }

    @Override
    public ConfigChangeResult applyConfigurationChange(PBKDF2PasswordStorageSchemeCfg configuration)
    {
        this.config = configuration;
        return new ConfigChangeResult();
    }

    @Override
    public abstract String getStorageSchemeName();

    @Override
    public ByteString encodePassword(ByteSequence plaintext)
            throws DirectoryException
    {
        byte[] saltBytes      = new byte[NUM_SALT_BYTES];
        int    iterations     = config.getPBKDF2Iterations();

        SecretKey digest = encodeWithRandomSalt(plaintext, saltBytes, iterations);
        byte[] hashPlusSalt = concatenateHashPlusSalt(saltBytes, digest.getEncoded());

        return ByteString.valueOfUtf8(iterations + ":" + Base64.encode(hashPlusSalt));
    }

    @Override
    public ByteString encodePasswordWithScheme(ByteSequence plaintext)
            throws DirectoryException
    {
        return ByteString.valueOfUtf8('{' + getStorageSchemeName() + '}' + encodePassword(plaintext));
    }

    @Override
    public boolean passwordMatches(ByteSequence plaintextPassword, ByteSequence storedPassword) {
        // Split the iterations from the stored value (separated by a ':')
        // Base64-decode the remaining value and take the last bytes as the salt.
        try
        {
            final String stored = storedPassword.toString();
            final int pos = stored.indexOf(':');
            if (pos == -1)
            {
                throw new Exception();
            }

            final int iterations = Integer.parseInt(stored.substring(0, pos));
            byte[] decodedBytes = Base64.decode(stored.substring(pos + 1)).toByteArray();

            int digestLength = getDigestSize();
            final int saltLength = decodedBytes.length - digestLength;
            if (saltLength <= 0) {
                logger.error(ERR_PWSCHEME_INVALID_BASE64_DECODED_STORED_PASSWORD, storedPassword);
                return false;
            }

            final byte[] digestBytes = new byte[digestLength];
            final byte[] saltBytes = new byte[saltLength];
            System.arraycopy(decodedBytes, 0, digestBytes, 0, digestLength);
            System.arraycopy(decodedBytes, digestLength, saltBytes, 0, saltLength);
            return encodeAndMatch(plaintextPassword, saltBytes, digestBytes, iterations);
        }
        catch (Exception e)
        {
            logger.traceException(e);
            logger.error(ERR_PWSCHEME_CANNOT_BASE64_DECODE_STORED_PASSWORD, storedPassword, e);
            return false;
        }
    }

    @Override
    public boolean supportsAuthPasswordSyntax()
    {
        return true;
    }

    @Override
    public abstract String getAuthPasswordSchemeName();

    abstract String getMessageDigestAlgorithm();

    abstract int getDigestSize();

    @Override
    public ByteString encodeAuthPassword(ByteSequence plaintext)
            throws DirectoryException
    {
        byte[] saltBytes      = new byte[NUM_SALT_BYTES];
        int    iterations     = config.getPBKDF2Iterations();
        SecretKey digest = encodeWithRandomSalt(plaintext, saltBytes, iterations);
        byte[] digestBytes = digest.getEncoded();

        // Encode and return the value.
        return ByteString.valueOfUtf8(getAuthPasswordSchemeName() + '$'
                + iterations + ':' + Base64.encode(saltBytes) + '$' + Base64.encode(digestBytes));
    }

    @Override
    public boolean authPasswordMatches(ByteSequence plaintextPassword, String authInfo, String authValue)
    {
        try
        {
            int pos = authInfo.indexOf(':');
            if (pos == -1)
            {
                throw new Exception();
            }
            int iterations = Integer.parseInt(authInfo.substring(0, pos));
            byte[] saltBytes   = Base64.decode(authInfo.substring(pos + 1)).toByteArray();
            byte[] digestBytes = Base64.decode(authValue).toByteArray();
            return encodeAndMatch(plaintextPassword, saltBytes, digestBytes, iterations);
        }
        catch (Exception e)
        {
            logger.traceException(e);
            return false;
        }
    }

    @Override
    public boolean isReversible()
    {
        return false;
    }

    @Override
    public ByteString getPlaintextValue(ByteSequence storedPassword)
            throws DirectoryException
    {
        LocalizableMessage message = ERR_PWSCHEME_NOT_REVERSIBLE.get(getStorageSchemeName());
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    @Override
    public ByteString getAuthPasswordPlaintextValue(String authInfo, String authValue)
            throws DirectoryException
    {
        LocalizableMessage message = ERR_PWSCHEME_NOT_REVERSIBLE.get(getAuthPasswordSchemeName());
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    @Override
    public boolean isStorageSchemeSecure()
    {
        return true;
    }

    /**
     * Generates an encoded password string from the given clear-text password.
     * This method is primarily intended for use when it is necessary to generate a password with the server
     * offline (e.g., when setting the initial root user password).
     *
     * @param  passwordBytes  The bytes that make up the clear-text password.
     * @param schemeName The storage scheme name
     * @param digestAlgo The digest algorithm name
     * @param digestSize The digest algorithm size in bytes
     * @return  The encoded password string, including the scheme name in curly braces.
     * @throws  DirectoryException  If a problem occurs during processing.
     */
    public static String encodeOffline(byte[] passwordBytes, String schemeName, String digestAlgo, int digestSize)
            throws DirectoryException
    {
        byte[] saltBytes      = new byte[NUM_SALT_BYTES];
        final int iterations  = 10000;

        SecureRandom random = new SecureRandom();
        random.nextBytes(saltBytes);

        final ByteString password = ByteString.wrap(passwordBytes);
        char[] plaintextChars = password.toString().toCharArray();

        PBEKeySpec spec = null;
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(digestAlgo);
            spec = new PBEKeySpec(plaintextChars, saltBytes, iterations, digestSize * 8);
            SecretKey digest = factory.generateSecret(spec);
            byte[] digestBytes = digest.getEncoded();
            byte[] hashPlusSalt = concatenateHashPlusSalt(saltBytes, digestBytes);

            return '{' + schemeName + '}' + iterations + ':' + Base64.encode(hashPlusSalt);
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e)
        {
            throw cannotEncodePassword(schemeName, e);
        }
        finally {
            Arrays.fill(plaintextChars, '0');

            if (spec != null)
                spec.clearPassword();
        }
    }

    private SecretKey encodeWithRandomSalt(ByteString plaintext, byte[] saltBytes, int iterations)
            throws DirectoryException
    {
        random.nextBytes(saltBytes);
        return encodeWithRandomSalt(plaintext, saltBytes, iterations);
    }

    private SecretKey encodeWithSalt(ByteSequence plaintext, byte[] saltBytes, int iterations)
            throws DirectoryException
    {
        final char[] plaintextChars = plaintext.toString().toCharArray();

        PBEKeySpec spec = null;
        try
        {
            spec = new PBEKeySpec(plaintextChars, saltBytes, iterations, getDigestSize() * 8);
            return keyFactory.generateSecret(spec);
        }
        catch (Exception e)
        {
            throw cannotEncodePassword(getStorageSchemeName(), e);
        }
        finally
        {
            Arrays.fill(plaintextChars, '0');
            if (spec != null)
                spec.clearPassword();
        }
    }

    private boolean encodeAndMatch(ByteSequence plaintext, byte[] saltBytes, byte[] digestBytes, int iterations)
    {
        try
        {
            final SecretKey userDigestBytes = encodeWithSalt(plaintext, saltBytes, iterations);
            return Arrays.equals(digestBytes, userDigestBytes.getEncoded());
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private SecretKey encodeWithRandomSalt(ByteSequence plaintext, byte[] saltBytes, int iterations)
            throws DirectoryException
    {
        random.nextBytes(saltBytes);
        return encodeWithSalt(plaintext, saltBytes, iterations);
    }

    private static DirectoryException cannotEncodePassword(String storageSchemeName, Exception e)
    {
        logger.traceException(e);

        LocalizableMessage message = ERR_PWSCHEME_CANNOT_ENCODE_PASSWORD.get(
                storageSchemeName, getExceptionMessage(e));
        return new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message, e);
    }

    private static byte[] concatenateHashPlusSalt(byte[] saltBytes, byte[] digestBytes) {
        final byte[] hashPlusSalt = new byte[digestBytes.length + saltBytes.length];
        System.arraycopy(digestBytes, 0, hashPlusSalt, 0, digestBytes.length);
        System.arraycopy(saltBytes, 0, hashPlusSalt, digestBytes.length, saltBytes.length);
        return hashPlusSalt;
    }
}
