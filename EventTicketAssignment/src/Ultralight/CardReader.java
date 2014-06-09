package Ultralight;

import java.util.List;
import javax.smartcardio.CardException;

/**
 * Class for card reader commands that send APDUs to the card.
 * Written for SLC011/SLC010 readers, but tested to work also on 
 * several other PCSC readers.
 * 
 * @author Tuomas Aura
 */
public class CardReader {

	protected java.io.PrintStream msgOut = null;
	protected java.io.PrintStream apduOut = null;

	protected javax.smartcardio.CardTerminal terminal = null;
	protected javax.smartcardio.Card card = null;
	protected javax.smartcardio.CardChannel channel = null;

	/**
	 * Constructor for the CardReader class.
	 * 
	 * @param msgOut
	 *            PrintStream for printing informative user messages. You can
	 *            use System.out.
	 * @param apduOut
	 *            PrintStream for printing the APDU data. Typically null, but
	 *            can also be System.out.
	 */
	public CardReader(java.io.PrintStream msgOut, java.io.PrintStream apduOut) {
		this.msgOut = msgOut;
		this.apduOut = apduOut;
	}

	protected void userMessage(String msg) {
		if (msgOut != null) msgOut.println(msg);
	}

	protected void printApdu(String prefix, byte[] data) {
		if (apduOut != null) {
			if (prefix != null) apduOut.print(prefix);
			for (byte b : data)
				apduOut.printf("%02X ", b);
			apduOut.println();
		}
	}

	/**
	 * Initialize the smart card reader. Currently, only SCL01x readers are
	 * supported.
	 * 
	 * @return Returns true if a reader was found.
	 */
	public boolean initReader() {
		terminal = null;
		javax.smartcardio.TerminalFactory factory = javax.smartcardio.TerminalFactory
				.getDefault();
		List<javax.smartcardio.CardTerminal> terminalList;
		try {
			terminalList = factory.terminals().list();
		} catch (CardException e) {
			userMessage("No smart card reader found.");
			return false;
		}
		if (terminalList.size() == 0) {
			userMessage("No smart card reader found.");
			return false;
		}
		if (terminalList.size() > 1)
			userMessage("Warning: Multiple smart card readers. Selecting the first one.");
		terminal = terminalList.get(0);
		userMessage("Reader name: " + terminal.getName());
		return true;
	}

	protected byte[] ultralightAtr = new byte[] { (byte) 0x3B, (byte) 0x8F,
			(byte) 0x80, (byte) 0x01, (byte) 0x80, (byte) 0x4F, (byte) 0x0C,
			(byte) 0xA0, (byte) 0x00, (byte) 0x00, (byte) 0x03, (byte) 0x06,
			(byte) 0x03, (byte) 0x00, (byte) 0x03, (byte) 0x00, (byte) 0x00,
			(byte) 0x00, (byte) 0x00, (byte) 0x68 };

	/**
	 * Initialize connection to the smart card.
	 * 
	 * @return Returns true if successful.
	 */
	public boolean initCard() throws CardException {
		if (terminal == null)
			throw new CardException("Bug: must initialize reader before card.");
		card = null;
		channel = null;
		try {
			userMessage("Waiting for MIFARE Ultralight card... ");
			terminal.waitForCardPresent(10000);
			if (!terminal.isCardPresent()) {
				userMessage("(if you always get stuck here, maybe the PCSC driver for your card reader is not working right)");
				terminal.waitForCardPresent(0);
			}
			userMessage("Found a card.");
			card = terminal.connect("T=1");
			channel = card.getBasicChannel();
		} catch (Exception e) {
			userMessage("Unable to connect to the card: " + e.toString());
			return false;
		}

		byte[] atr = card.getATR().getBytes();
		boolean goodAtr = true;
		if (atr.length != ultralightAtr.length)
			goodAtr = false;
		else
			for (int i = 0; i < atr.length; i++)
				if (atr[i] != ultralightAtr[i]) goodAtr = false;
		if (goodAtr) {
			userMessage("It is an Ultralight card.");
			return true;
		} else {
			userMessage("Unrecognized ATR. Not an Ultralight card.");
			return false;
		}
	}

	protected boolean readCommand(int adr, byte[] dstBuffer, int dstPos)
			throws CardException {
		if (channel == null)
			throw new CardException(
					"Bug: must initialize card before sending commands.");

		// Translate Ultralight command to card reader command for storage token
		// read. The card reader will translate it to the actual Ultralight
		// command (0x30).
		byte[] cmdApdu = new byte[5];
		cmdApdu[0] = (byte) 0xFF;
		cmdApdu[1] = (byte) 0xB0;
		cmdApdu[2] = (byte) 0;
		cmdApdu[3] = (byte) adr;
		cmdApdu[4] = (byte) 4; // Always read or write 4 bytes.

		// Now, really send the APDU to the reader and card.
		byte[] resApdu = sendApdu(cmdApdu);

		if (checkResponse(resApdu, 6)) {
			System.arraycopy(resApdu, 0, dstBuffer, dstPos, 4);
			return true;
		} else
			return false;
	}

	protected boolean writeCommand(int adr, byte[] srcBuffer, int srcPos)
			throws CardException {
		if (channel == null)
			throw new CardException(
					"Bug: must initialize card before sending commands.");

		// Translate Ultralight command to card reader command for storage token
		// write. The card reader will translate it to the actual Ultralight
		// command (0xA2).
		byte[] cmdApdu = new byte[9];
		cmdApdu[0] = (byte) 0xFF;
		cmdApdu[1] = (byte) 0xD6;
		cmdApdu[2] = (byte) 0;
		cmdApdu[3] = (byte) adr;
		cmdApdu[4] = (byte) 4; // Always read or write 4 bytes.
		if (srcBuffer != null)
			System.arraycopy(srcBuffer, srcPos, cmdApdu, 5, 4);

		// Now, really send the APDU to the reader and card.
		byte[] resApdu = sendApdu(cmdApdu);

		return checkResponse(resApdu, 2);
	}

	protected byte[] sendApdu(byte[] cmdApdu) throws CardException {
		printApdu("==> ", cmdApdu);
		byte[] resApdu;
		try {
			// Actually send the APDU to the card.
			javax.smartcardio.CommandAPDU cmdApduObject = new javax.smartcardio.CommandAPDU(
					cmdApdu);
			javax.smartcardio.ResponseAPDU resApduObject = channel
					.transmit(cmdApduObject);
			resApdu = resApduObject.getBytes();
		} catch (Exception e) {
			userMessage("Sending command to the card failed: " + e.toString());
			return null;
		}
		printApdu("<== ", resApdu);
		return resApdu;
	}

	protected boolean checkResponse(byte[] resApdu, int expectedLength) {
		// Card reader response has two status bytes at the end (sw1=0x90,
		// sw2=0x00 for success). The error message are different in 
		// different readers. Here we assume the reader is SLC011.
		if (resApdu == null) return false;
		if (resApdu.length >= 2) {
			byte sw1 = resApdu[resApdu.length - 2];
			byte sw2 = resApdu[resApdu.length - 1];
			if (sw1 != (byte) 0x90 || sw2 != (byte) 0x00) {
				userMessage(String
						.format("Card returned error status (sw1=0x%02X, sw2=0x%02X). ",
								sw1, sw2)
						+ getErrorMessage(sw1, sw2));
				return false;
			}
		}
		if (resApdu.length != expectedLength) {
			userMessage("Ultralight response length " + resApdu.length
					+ " is not normal.");
			return false;
		}
		return true;
	}

	// These strings are from the SCL01x Manual version 2.1. sections 6.3.1.4
	// and 6.3.2.4. Probably the reader will never return most of them as they
	// are for MIFARE Classic. Other readers have different error codes, so 
	// the output from this function may be wrong for them.
	protected String getErrorMessage(byte sw1, byte sw2) {
		// Should use a HashMap container but that would create extra code in
		// Java.
		int key = ((int) sw1 << 8) + ((int) sw2 & 0xff);
		switch (key) {
		case (((int) 0x90 << 8) + ((int) 0x00 & 0xff)):
			return "NO ERROR";
		case (((int) 0x62 << 8) + ((int) 0x81 & 0xff)):
			return "WARNING: part of the returned data may be corrupted";
		case (((int) 0x62 << 8) + ((int) 0x82 & 0xff)):
			return "WARNING: end of file reached before Le bytes where read";
		case (((int) 0x64 << 8) + ((int) 0x00 & 0xff)):
			return "State of the non-volatile memory unchanged";
		case (((int) 0x67 << 8) + ((int) 0x00 & 0xff)):
			return "Length incorrect";
		case (((int) 0x68 << 8) + ((int) 0x00 & 0xff)):
			return "CLA byte incorrect";
		case (((int) 0x69 << 8) + ((int) 0x81 & 0xff)):
			return "Command not supported";
		case (((int) 0x69 << 8) + ((int) 0x82 & 0xff)):
			return "Security status not satisfied";
		case (((int) 0x69 << 8) + ((int) 0x86 & 0xff)):
			return "Command not allowed";
		case (((int) 0x6A << 8) + ((int) 0x81 & 0xff)):
			return "Function not supported";
		case (((int) 0x6A << 8) + ((int) 0x82 & 0xff)):
			return "File not found, addressed blocks or bytes do not exist";
		case (((int) 0x6B << 8) + ((int) 0x00 & 0xff)):
			return "Wrong P1, P2 parameters";
		}
		if (sw1 == 0x6C)
			return String.format("Wrong Le, 0x%02X is the correct value", sw2);
		return String.format("Undefined error code.", sw1, sw2);
	}

}
