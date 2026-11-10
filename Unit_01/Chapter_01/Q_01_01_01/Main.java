package Unit_01.Chapter_01.Q_01_01_01;



import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        String name = scanner.nextLine();
        
        System.out.println("Good evening, " + name + "!");
        
        scanner.close();
    }
}