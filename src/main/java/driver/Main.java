package driver;

public class Main {
    public static void main(String[] args) {
        System.out.println("Starting LEIA:\n");
        LEIA target = null;
        try {
            target = new LEIA();
            target.open();
            target.close();
        } catch (Exception e) {
            if (target != null)
                target.close();
            System.out.println("Caught exception:");
            System.out.println(e.getMessage());
        }
        System.out.println("\nDone");
    }
}