package driver;

public class Main {
    public static void main(String[] args) {
        System.out.println("Starting LEIA:\n");
        try {
            LEIA target = new LEIA();
        } catch (Exception e) {
            System.out.println("Caught exception:");
            System.out.println(e.getMessage());
        }
        System.out.println("\nDone");
    }
}