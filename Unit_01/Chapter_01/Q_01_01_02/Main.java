package Unit_01.Chapter_01.Q_01_01_02;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        // Read height and width as floating-point numbers
        double height = scanner.nextDouble();
        double width = scanner.nextDouble();
        
        // Calculate area
        double area = height * width;
        
        // Output the result
        System.out.println(area);
        
        scanner.close();
    }
}