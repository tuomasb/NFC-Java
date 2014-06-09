import java.util.Date;
import Ticket.*;
import Ultralight.*;
import java.io.Console;

/**
 * @author Tuomas Aura
 * 
 */
public class Main {

	public static void main(String[] args) throws Exception {
		boolean status;

		Console c = System.console();
		// (Step 1) Create a reader object.
		// Since this is a command line main program, we print informative
		// user messages System.out. The second argument can be set to
		// System.out if you also want to print the APDU hex data.
		CardReader reader = new CardReader(System.out, null);

		// (Step 2) Initialize the smart card reader and card.
		// If there many readers, it tries to select the right one
		// automatically. Here we bail out on error. A better UI would
		// not do so.
		status = reader.initReader();
		if (!status) return;
		status = reader.initCard();
		if (!status) return;

		// (Step 3) Initialize Ultralight commands object.
		UltralightCommands ul = new UltralightCommands(reader);
		//ul.safe = false;
		if (ul.safe)
			System.out.println("You are currently working in safe mode (practice mode).");
		else
			System.out.println("You are currently working in UNSAFE mode. It may not be possible to erase or reuse the card after this.");

		// (Step 4) Finally, read and write data.

		UltralightUtilities utils = new UltralightUtilities(ul, System.out);
		utils.printMemory(System.out);

		Ticket ticket = new Ticket(ul, System.out);

		if (args.length == 0) {
			System.out.println("You did not specify what to do.");
			System.out.println("Use command-line argument: dump|erase|format|issue|use");
			System.out.println("\nTypically, first format the card, then issue tickets, and finally use them.");
			System.out.println("Erase works only in the safe mode. In real life, used cards cannot be erased.");
			System.out.println();
			System.exit(0);
		}

		else if (args[0].equals("dump"))
			System.exit(0);

		else if (args[0].equals("erase")) {
			boolean isSafe = ul.safe;
			if (isSafe) {
				System.out.println("Turning safe mode off.");
				ul.safe = false;
			}
			System.out.println("Erasing whatever can still be erased...");
			status = utils.eraseMemory();
			if (isSafe) {
				System.out.println("Turning safe mode back on.");
				ul.safe = true;
			}
			if (status)
				System.out.println("Erasing completed (except maybe any OTP and lock bits that cannot be reset).");
			else
				System.out.println("Erasing FAILED.");
		}

		else if (args[0].equals("format")) {
			System.out.println("Formating the card to be used as ticket...");

			// You need to implement this method:
			status = ticket.format();

			if (status)
				System.out.println("Formating completed.");
			else
				System.out.println("Formating FAILED.");
		}

		else if (args[0].equals("issue")) {
			int days = 30;
			int uses = 10;
			System.out.println("Issuing new ticket for " + days + "days, "
					+ uses + " uses...");
			// Time expressed as MINUTES since January 1, 1970.
			int currentTime = (int) ((new Date()).getTime() / 1000 / 60);
			int expiryTime = currentTime + days * 24 * 60;
			System.out.println("Current time: "
					+ new Date((long) currentTime * 60 * 1000));
			System.out.println("Expiry time: "
					+ new Date((long) expiryTime * 60 * 1000));
			System.out.println("Remaining uses: " + uses);

			// You need to implement this method:
			status = ticket.issue(expiryTime, uses);

			if (status)
				System.out.println("Ticket issuing completed.");
			else
				System.out.println("Ticket issuing FAILED. Probably you did not format the card correctly.");
		}

		else if (args[0].equals("use")) {
			System.out.println("Using ticket...");
			// Time expressed as MINUTES since January 1, 1970.
			int currentTime = (int) ((new Date()).getTime() / 1000 / 60);

			// You need to implement these methods:
			ticket.use(currentTime);
			boolean valid = ticket.isValid();
			int uses = ticket.getRemainingUses();
			int expiryTime = ticket.getExpiryTime();

			if (valid)
				System.out.println("Used ticket successfully. The ticket was valid.");
			else
				System.out
						.println("Ticket use FAILED. The following data may be INVALID.");

			System.out.println("Current time: "
					+ new Date((long) currentTime * 60 * 1000));
			System.out.println("Expiry time: "
					+ new Date((long) expiryTime * 60 * 1000));
			System.out.println("Remaining uses: " + uses);
		}
		
		else if (args[0].equals("lock")) {
			System.out.println("Locking a ticket (all pages)");
			ticket.lock();
		}
		
		else if (args[0].equals("reissue")) {
			System.out.println("Reissuing a ticket (overwriting an already formatted ticket unless it is locked)");
			System.out.print("Validity in days: ");
			String input1 = c.readLine();
			System.out.print("Number of uses: ");
			String input2 = c.readLine();
			
			int days;
			int uses;
			try {
				days = Integer.parseInt(input1);
				uses = Integer.parseInt(input2);
			} catch(NumberFormatException e) {
				System.out.println("Could not parse inputted numbers");
				return;
			}
			
			System.out.println("Issuing new ticket for " + days + "days, "
					+ uses + " uses...");
			// Time expressed as MINUTES since January 1, 1970.
			int currentTime = (int) ((new Date()).getTime() / 1000 / 60);
			int expiryTime = currentTime + days * 24 * 60;
			System.out.println("Current time: "
					+ new Date((long) currentTime * 60 * 1000));
			System.out.println("Expiry time: "
					+ new Date((long) expiryTime * 60 * 1000));
			
			status = ticket.reissue(expiryTime, uses);
			System.out.println("Remaining uses: " + ticket.getRemainingUses());
			
			
			if (status)
				System.out.println("Ticket issuing completed.");
			else
				System.out.println("Ticket issuing FAILED. Probably you did not format the card correctly.");
			
		}
		
		else {
			System.out.println("Unknown function: " + args[0]);
		}

		utils.printMemory(System.out);
	}

}
