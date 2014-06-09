package Ultralight;

import javax.smartcardio.CardException;

/**
 * Class for atomic MIFARE Ultralight read and write commands.
 * 
 * @author Tuomas Aura
 */
public class UltralightCommands {

	protected CardReader reader;

	// Set true to ignore writes to page 2 and to emulate page 3 with page 15:
	// Set false to really write the OTP and lock bits (cannot be reset).
	public boolean safe = true;

	/**
	 * Constructor for the UltralightCommands class.
	 * 
	 * @param reader
	 *            Initialized object of the class Scl01Reader.
	 */
	public UltralightCommands(CardReader reader) {
		this.reader = reader;
	}

	protected void checkArgs(int adr, byte[] buffer, int pos)
			throws CardException {

		if (adr < 0 || adr > 15)
			throw new CardException("Bug: Memory page must be 0...15. It was "
					+ adr + ".");
		if (buffer == null)
			throw new CardException("Bug: read or write buffer is null.");
		if (buffer.length < pos + 4)
			throw new CardException(
					"Bug: Buffer too short. Ultralight is read and written 4 bytes at a time.");
	}

	/**
	 * Read a page of binary data from the smart card.
	 * 
	 * @param adr
	 *            Number of the smart card memory page to be read. (page in
	 *            MIFARE Ultralight 4 bytes.)
	 * @param dstBuffer
	 *            Destination buffer to which the data will be read from the
	 *            smart card.
	 * @param dstPos
	 *            Byte index in the destination buffer to which the data will be
	 *            written. The buffer must have enough space (4 or 16 bytes) for
	 *            the data.
	 * @return Returns true of the read was successful.
	 * @throws CardException
	 *             Thrown only on unexpected errors. Normal errors are reported
	 *             as false return value.
	 */
	public boolean readBinary(int adr, byte[] dstBuffer, int dstPos)
			throws CardException {
		checkArgs(adr, dstBuffer, dstPos);

		if (!safe || adr != 3)
			// Normal read
			return reader.readCommand(adr, dstBuffer, dstPos);

		// SAFE MODE: page 3 has been mapped to page 15.
		return reader.readCommand(15, dstBuffer, dstPos);
	}

	/**
	 * Write 4 bytes from source buffer to a card memory page.
	 * 
	 * @param adr
	 *            Number of the smart card memory page to be written. (Page in
	 *            MIFARE Ultralight is 4 bytes.)
	 * @param srcBuffer
	 *            Source buffer from which data will be written to the card.
	 * @param srcPos
	 *            Byte position in the source buffer from which data is read.
	 * @return Returns true if the write was successful.
	 * @throws CardException
	 *             Throws exception only on unexpected errors. Normal errors are
	 *             reported as false return value.
	 */
	public boolean writeBinary(int adr, byte[] srcBuffer, int srcPos)
			throws CardException {
		checkArgs(adr, srcBuffer, srcPos);

		if (!safe || (adr != 2 && adr != 3 && adr != 15))
			// Normal write
			return reader.writeCommand(adr, srcBuffer, srcPos);

		// SAFE MODE: prevents setting of locks, maps OTP writes to page 15
		if (adr == 2 || adr == 15)
			// Page 2: silently ignore writes to the locks and to page 15.
			return true;
		else {
			// Page 3: emulate the one time programmable page with page 15.
			byte[] page15 = new byte[4];
			boolean status = reader.readCommand(15, page15, 0);
			for (int i = 0; i < 4; i++)
				page15[i] |= srcBuffer[srcPos + i];
			status |= reader.writeCommand(15, page15, 0);
			return status;
		}
	}
		
}
