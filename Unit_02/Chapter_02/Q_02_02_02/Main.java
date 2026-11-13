package Unit_02.Chapter_02.Q_02_02_02;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        String country = scanner.nextLine();

        if (country.equals("Argentina")) {
            System.out.println("Lionel Messi");
        } else if (country.equals("Portugal")) {
            System.out.println("Cristiano Ronaldo");
        } else {
            System.out.println("no preference");
        }

        scanner.close();
    }
}