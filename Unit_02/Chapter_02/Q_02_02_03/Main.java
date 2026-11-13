package Unit_02.Chapter_02.Q_02_02_03;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int health = scanner.nextInt();

        if (health >= 80 && health <= 300) {
            System.out.println("healthy");
        } else if (health >= 60 && health <= 79) {
            System.out.println("bloody nose");
        } else if (health >= 40 && health <= 59) {
            System.out.println("face swollen");
        } else if (health >= 20 && health <= 39) {
            System.out.println("eyes bloodshot, face bleeding");
        } else if (health >= 1 && health <= 19) {
            System.out.println("even more blood");
        } else if (health == 0) {
            System.out.println("eyes closed. Game Over");
        }

        scanner.close();
    }
}