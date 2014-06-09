package Ticket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import javax.smartcardio.CardException;
import Ultralight.UltralightCommands;
import Ultralight.UltralightUtilities;


/**
 * TODO: Complete the implementation of this class. Most of your code and
 * modifications go to this file. You will want to replace some of the example
 * code below.
 * 
 * @author You!
 * 
 */
public class Ticket {

	// Define a page-4 application tag to use for the ticket application.
	// It will be written to card memory page 4 and used to identify the
	// ticket application.
	public byte[] applicationTag = { (byte) 0x54, (byte) 0x49, (byte) 0x4B,
			(byte) 0x54 };
	private static final int usedMacLength  = 2; // Mac length in 4-byte pages.

	private java.io.PrintStream msgOut; // Use this for any output to the user.
	private UltralightCommands ul;
	private UltralightUtilities utils;
	private TicketMac macAlgorithm;

	public Ticket(UltralightCommands ul, java.io.PrintStream msgOut)
			throws IOException, GeneralSecurityException {
		this.msgOut = msgOut;
		this.ul = ul;
		utils = new UltralightUtilities(ul, msgOut);
		macAlgorithm = new TicketMac();
		if (macAlgorithm.getMacLength() < usedMacLength*4)
			throw new GeneralSecurityException("Bug: The MAC is too short.");
	}

	// Format the card to be used as a ticket.
	public boolean format() throws CardException {
		boolean status;

		// Zero the card memory. Fails is any of the pages is locked.
		status = utils.eraseMemory();
		if (!status) return false;

		// Write the application tag to memory page 4.
		status = ul.writeBinary(4, applicationTag, 0);
		if (!status) return false;
		// In a real application, we probably would lock page 4 here,
		// but remember that locking pages is irreversible.

		// Check the format.
		if (!checkFormat()) return false;
		
		return true;
	}
	
	// Check that the card has been correctly formatted.
	protected boolean checkFormat() throws CardException {
		// Read the card contents and check that all is ok.
		byte[] memory = utils.readMemory();
		if (memory == null) return false;
		// Check the application tag.
		for (int i = 1; i < 4; i++)
			if (memory[4 * 4 + i] != applicationTag[i]) return false;
		// Check zeros. Ignore page 15 because of the safe mode.
		for (int i = 5 * 4; i < 15 * 4; i++)
			if (memory[i] != 0) return false;
		// Check that the memory pages 5..15 are not locked.
		if ((memory[2 * 4 + 2] & (byte) 0xE0) != 0) return false;
		if (memory[2 * 4 + 2] != 0) return false;	
		return true;
	}
	
	// Check that the card has been correctly formatted.
	protected boolean checkReIssuability() throws CardException {
		// Read the card contents and check that all is ok.
		byte[] memory = utils.readMemory();
		if (memory == null) return false;
		// Check the application tag.
		for (int i = 1; i < 4; i++)
			if (memory[4 * 4 + i] != applicationTag[i]) return false;
		// Check that the memory pages 5..15 are not locked.
		if ((memory[2 * 4 + 2] & (byte) 0xE0) != 0) return false;
		if (memory[2 * 4 + 2] != 0) return false;	
		return true;
	}
	
	// Issue new tickets.
	public boolean issue(int expiryTime, int uses) throws CardException,
			GeneralSecurityException {
		// Check the format.
		if (!checkFormat()) return false;
		// We only use 8 bytes (64 bits) of the MAC.
		// Pages 0 and 1 will contain UID (minus second check byte)
		// Page 2 will contain UID check byte, internal byte and two lock bytes
		// Page 3 will contain 4 One Time Programmable bytes
		// Page 4 will contain Application Tag
		// Page 5 will contain expiryTime in Big Endian Byte order (Java default)
		// Page 6 will contain number of uses in 4-bytes (Big Endian)
		// Pages 7 and 8 will contain first 64bits of MAC(originally 160bit/20byte HMAC-SHA1)
		byte[] dataOnCard = new byte[5 * 4];
		utils.readPages(0, 5, dataOnCard, 0);
		dataOnCard[2 * 4 + 2] = 0; // Ignore the lock bits.
		dataOnCard[2 * 4 + 3] = 0;

		// Ignore OTP
		dataOnCard[12] = 0;
		dataOnCard[13] = 0;
		dataOnCard[14] = 0;
		dataOnCard[15] = 0;
				
		// Wrap into ByteBuffer for easier handling
		ByteBuffer data = ByteBuffer.allocate(7*4);
		data.put(dataOnCard);
		
		// Page 5(bytes 20-23) will contain expiryTime in Big Endian Byte order (Java default)
		byte[] expiryBytes = ByteBuffer.allocate(4).putInt(expiryTime).array(); // Expirytime into its own ByteBuffer
		data.put(expiryBytes); // Put into data for MAC calculation
		utils.writePages(expiryBytes, 0, 5, 1); // Also write into card

		// Page 6(bytes 24-27) will contain number of allowed uses in Big Endian Byte order (Java default)
		byte[] useBytes = ByteBuffer.allocate(4).putInt(uses).array(); // Number of uses into its own ByteBuffer
		data.put(useBytes);
		utils.writePages(useBytes, 0, 6, 1); // Also write useBytes into card

		// Calculate MAC and write 8 first bytes from it into pages 7 and 8
		dataOnCard = data.array();
		byte[] mac = macAlgorithm.generateMac(dataOnCard);
		utils.writePages(mac, 0, 7, usedMacLength);
	
		return true;
	}

	// Use the ticket once.
	public void use(int currentTime) throws CardException,
			GeneralSecurityException {
		
		byte[] dataOnCard = new byte[7 * 4];
		byte[] macOnCard = new byte[3 * 4];
		utils.readPages(0, 7, dataOnCard, 0);
		utils.readPages(7, usedMacLength, macOnCard, 0);
		dataOnCard[2 * 4 + 2] = 0; // Ignore the lock bits.
		dataOnCard[2 * 4 + 3] = 0;

		int readExpiryTime = ByteBuffer.wrap(dataOnCard, 20, 4).getInt();
		int allowedUses = ByteBuffer.wrap(dataOnCard, 24, 4).getInt();
		
		// int overflows at 2^31 so need a hack to get all 32 uses in OTP bytes
		ByteBuffer longbuf = ByteBuffer.allocate(8);
		byte[] prependZero = { (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0 };
		longbuf.put(prependZero);
		longbuf.put(ByteBuffer.wrap(dataOnCard, 12, 4));
		longbuf.flip();
		long OTP = longbuf.getLong();
		
		// Ignore OTP when calculating MAC
		dataOnCard[12] = 0;
		dataOnCard[13] = 0;
		dataOnCard[14] = 0;
		dataOnCard[15] = 0;
		
		// Prevent error message about MAC with unissued cards
		if (checkFormat()) { 
			msgOut.println("ERROR: Trying to use formatted card with no tickets issued");
			return;
		}
		
		// System.out.println("OTP: " + OTP);
		int currentUses = 0;
		for(long i=0; i<33; i++) {
			long test = (long)(Math.pow(2, i) - 1);
		    if(OTP == test) currentUses = (int)i;
		}
		
		remainingUses = allowedUses - currentUses;
		expiryTime = readExpiryTime;
		
		byte[] mac = macAlgorithm.generateMac(dataOnCard);
		// We only use 8 bytes (64 bits) of the MAC.
		for (int i = 0; i < usedMacLength*4; i++)
			if (macOnCard[i] != mac[i]) {
				msgOut.println("ERROR: Invalid Message Authentication Code");
				isValid = false;
				return;
			}

		if (currentTime > readExpiryTime) {
			msgOut.println("ERROR: Ticket expired");
			isValid = false;
			return;
		}
		
		if (remainingUses < 1 || currentUses > 31) {
			msgOut.println("ERROR: No more uses available");
			isValid = false;
			return;
		}
		
		// System.out.println("CurrentUses before incrementation: " + currentUses);
		currentUses++;
		OTP = (long)(Math.pow(2, currentUses) - 1);
		// System.out.println("OTP: " + OTP);
		
		byte[] OTPBytes = ByteBuffer.allocate(8).putLong(OTP).array();
		utils.writePages(OTPBytes, 4, 3, 1);
		remainingUses--;
		isValid = true;
	}

	public boolean reissue(int expiryTime, int uses) throws CardException,
	GeneralSecurityException {
		// Check the format.
		if (!checkReIssuability()) return false;
		byte[] dataOnCard = new byte[5 * 4];
		utils.readPages(0, 5, dataOnCard, 0);
		dataOnCard[2 * 4 + 2] = 0; // Ignore the lock bits.
		dataOnCard[2 * 4 + 3] = 0;

		// Ignore OTP
		dataOnCard[12] = 0;
		dataOnCard[13] = 0;
		dataOnCard[14] = 0;
		dataOnCard[15] = 0;

		byte[] OTPBytes = new byte[4];
		utils.readPages(3, 1, OTPBytes, 0);
		
		
		// int overflows at 2^31 so need a hack to get all 32 uses in OTP bytes
		ByteBuffer longbuf = ByteBuffer.allocate(8);
		byte[] prependZero = { (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0 };
		longbuf.put(prependZero);
		longbuf.put(ByteBuffer.wrap(OTPBytes));
		longbuf.flip();
		long OTP = longbuf.getLong();
		
		int currentUses = 0;
		for(long i=0; i<33; i++) {
			long test = (long)(Math.pow(2, i) - 1);
		    if(OTP == test) currentUses = (int)i;
		}
		
		if(uses > 32) { msgOut.println("ERROR: Cannot add more than 32 uses"); return false; }
		if(uses < currentUses) { msgOut.println("ERROR: Cannot issue a ticket for " + String.valueOf(uses) + " uses since ticked already used " + String.valueOf(currentUses) + " times"); return false; }
		
		
		// Wrap into ByteBuffer for easier handling
		ByteBuffer data = ByteBuffer.allocate(7*4);
		data.put(dataOnCard);
		byte[] expiryBytes = ByteBuffer.allocate(4).putInt(expiryTime).array(); // Expirytime into its own ByteBuffer

		// Page 4(bytes 16-19) will contain expiryTime in Big Endian Byte order (Java default)
		data.put(expiryBytes); // Put into data for MAC calculation
		utils.writePages(expiryBytes, 0, 5, 1); // Also write into card

		// Page 5(bytes 20-23) will contain number of allowed uses in Big Endian Byte order (Java default)
		byte[] useBytes = ByteBuffer.allocate(4).putInt(uses).array(); // Number of uses into its own ByteBuffer
		data.put(useBytes);
		utils.writePages(useBytes, 0, 6, 1); // Also write useBytes into card

		// Calculate MAC and write 8 first bytes from it into pages 6 and 7
		dataOnCard = data.array();
		byte[] mac = macAlgorithm.generateMac(dataOnCard);
		utils.writePages(mac, 0, 7, usedMacLength);
		remainingUses = uses - currentUses;
		
		return true;
	}
	
	public boolean lock() throws CardException,
	GeneralSecurityException {
		byte[] lockBytes = new byte[4];
		utils.readPages(2, 1, lockBytes, 0);
		lockBytes[2] = (byte) (lockBytes[2] | (byte)0xF0); // 1111000 to first lock byte (pages 7-4)
		lockBytes[3] = (byte)0xFF; // 11111111 to second lock byte (pages 8-15)
		utils.writePages(lockBytes, 0, 2, 1);
		return true;
	}
	
	private Boolean isValid = false;
	private int remainingUses = 0;
	private int expiryTime = 0;

	// After validation, get ticket status: was it valid or not?
	public boolean isValid() {
		return isValid;
	}

	// After validation, get the number of remaining uses.
	public int getRemainingUses() {
		return remainingUses;
	}

	// After validation, get the expiry time.
	public int getExpiryTime() {
		return expiryTime;
	}

}
