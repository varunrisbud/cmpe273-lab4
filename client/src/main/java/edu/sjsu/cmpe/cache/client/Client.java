package edu.sjsu.cmpe.cache.client;

public class Client {

    public static void main(String[] args) throws Exception {

        System.out.println("Starting Client");

        CRDTClient crdtClient = new CRDTClient();
        System.out.println("Putting Key 1 with Value 'a'");
        boolean isResponseSuccessful = crdtClient.put(1, "a");
        if (isResponseSuccessful) {
            System.out.println("Value 'a' is successfully written on all three servers");
        } else {
            System.out.println("Operation failed");
        }
        Thread.sleep(15000);

        System.out.println("Putting Key 1 with Value 'a'");
        isResponseSuccessful = crdtClient.put(1, "b");
        if (isResponseSuccessful) {
            System.out.println("Value 'a' is successfully written on all three servers");
        } else {
            System.out.println("Operation failed");
        }
        Thread.sleep(15000);

        System.out.println("Getting key: 1 value");
        String cachedValue = crdtClient.get(1);
        System.out.println("Value of key:1 is : "+ cachedValue);

        System.out.println("Closing Client");
    }
}
