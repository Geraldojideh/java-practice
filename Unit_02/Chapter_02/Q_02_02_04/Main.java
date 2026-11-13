package Unit_02.Chapter_02.Q_02_02_04;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        int balance = 500;
        int dailyLimit = 250;

        // Step 1: Set new PIN
        String newPin = scanner.nextLine();
        if (newPin.length() != 4) {
            System.out.println("Invalid PIN length entered. Goodbye.");
            scanner.close();
            return;
        }
        System.out.println("New PIN stored.");

        // Step 2: Enter PIN to access account
        String enteredPin = scanner.nextLine();
        if (enteredPin.length() != 4) {
            System.out.println("Invalid PIN length entered. Goodbye.");
            scanner.close();
            return;
        }
        if (!enteredPin.equals(newPin)) {
            System.out.println("Invalid PIN entered. Goodbye.");
            scanner.close();
            return;
        }

        // Step 3: Enter withdrawal amount
        int amount = scanner.nextInt();
        if (amount > balance) {
            System.out.println("Insufficient funds. Goodbye.");
        } else if (amount > dailyLimit) {
            System.out.println("Exceeds daily limit. Goodbye.");
        } else {
            balance -= amount;
            System.out.println("Withdrawal approved.");
            System.out.println("Balance: $" + balance);
        }

        scanner.close();
    }
}