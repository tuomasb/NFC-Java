package Ticket;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;

public class TicketMac {

	// Set here the secret key that will be used for the MAC. The same key
	// must be known both by the ticket issuer and checker. In a real
	// application, the key would be distributed securely in a file or in a smart
	// card and not embedded in the code.
	// Chosen by a fair dice roll
	// or alternatively:
	// kekkonen ~ % md5sum ruotsinessee.odt
	// f6ca6c9a03ffd065944aad9dd6b0b629  ruotsinessee.odt

	byte[] secretKey = { (byte) 0xF6, (byte) 0xCA, (byte) 0x6C, (byte) 0x9A,
			(byte) 0x03, (byte) 0xFF, (byte) 0xD0, (byte) 0x65, (byte) 0x94,
			(byte) 0x4A, (byte) 0xAD, (byte) 0x9D, (byte) 0xD6, (byte) 0xB0,
			(byte) 0xB6, (byte) 0x29 };

	private static SecretKeySpec hmacKey;
	private Mac mac;

	public TicketMac() throws GeneralSecurityException {
		hmacKey = new SecretKeySpec(secretKey, "HmacSHA1");
		mac = Mac.getInstance("HmacSHA1");
		mac.init(hmacKey);
	}

	public byte[] generateMac(byte[] data) throws GeneralSecurityException {
		mac.reset();
		return mac.doFinal(data);
	}

	public int getMacLength() {
		return mac.getMacLength();
	}

}
