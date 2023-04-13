import client.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

/**
* This is just some example code to show you how to interact 
* with the server using the provided client and two queues.
* Feel free to modify this code in any way you like!
*/

public class MyProtocol {

    // The host to connect to. Set this to localhost when using the audio interface tool.
    private static String SERVER_IP = "netsys.ewi.utwente.nl"; //"127.0.0.1";
    // The port to connect to. 8954 for the simulation server.
    private static int SERVER_PORT = 8954;
    // The frequency to use.
    private static int frequency = 4800; //TODO: Set this to your group frequency!
    // View the simulator at https://netsys.ewi.utwente.nl/integrationproject/

    private BlockingQueue<Message> receivedQueue;
    private BlockingQueue<Message> sendingQueue;

    private Random rand;

    //indexare
    private ArrayList<String> possibleIndex;
    private String myIndex;

    private String assignIndex() {
        myIndex = possibleIndex.get(0);
        possibleIndex.remove(0);
        distanceVector.put(myIndex, 0);
        if(Objects.equals(getIndex(), "a")){
            token = true;
        }
        return myIndex;
    }

    public String getIndex() {
        return myIndex;
    }

    //distance vectors
    private HashMap<String, Integer> distanceVector;

    private String tempMsg = null; //Maybe rename?

    //Token passing
    private boolean token = false;
    //Function for sending a packet. Automatically detects if DATA or DATA_SHORT should be used
    private void sendMessage(String input) throws InterruptedException {
        byte[] input_b = input.getBytes();
        ByteBuffer toSend = ByteBuffer.allocate(input.length());
        toSend.put(input_b, 0, input_b.length);

        if(input.length() > 2){
            Message msg = new Message(MessageType.DATA, toSend);
            sendingQueue.put(msg);
        } else {
            Message msg = new Message(MessageType.DATA_SHORT, toSend);
            sendingQueue.put(msg);
        }
    }


    private Map<String,Integer> getNeighbours() {
        return distanceVector.entrySet().stream().
            filter(map -> map.getValue() == 1).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
}
    private String passToken() {
        if(getNeighbours().size()==1) {
            ArrayList<String> neighbors = new ArrayList<>(getNeighbours().keySet());
            return neighbors.get(0);
        } else if(getNeighbours().size()>1){
            ArrayList<String> neighbors = new ArrayList<>(getNeighbours().keySet());
            Random rand = new Random();
            int randIndex = rand.nextInt(neighbors.size());
            return neighbors.get(randIndex);
        }
        return "a";
    }

    private void menu(){
        System.out.println("TOKEN - PASS YOUR TOKEN TO THE NEXT NODE");
        System.out.println("EXIT -  EXIT THE PROGRAM");
        System.out.println("MyVector - SHOW THE DISTANCE VECTOR OF THE CURRENT NODE");
        System.out.println("myIndex - GET AN INDEX IF YOU STILL DON'T HAVE ONE, FROM THE REMAINING ONES");
        System.out.println("MENU - display this screen again");
    }
    public MyProtocol(String server_ip, int server_port, int frequency){
        rand = new Random();
        receivedQueue = new LinkedBlockingQueue<Message>();
        sendingQueue = new LinkedBlockingQueue<Message>();

        distanceVector = new HashMap<String, Integer>();

        possibleIndex = new ArrayList<String>() {
            {
                add("a");
                add("b");
                add("c");
                add("d");
            }
        };





        new Client(SERVER_IP, SERVER_PORT, frequency, receivedQueue, sendingQueue); // Give the client the Queues to use

        new receiveThread(receivedQueue).start(); // Start thread to handle received messages!

        // handle sending from stdin from this thread.
        try{
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

            while(true){
                String command = reader.readLine();
                if(command.equals("index")){ //Starts up the indexing proccess, selecting this node as node "a"
                    System.out.println("This node's index is: " + assignIndex());

                    sendMessage(myIndex+0);

                    continue;
                }
                if(command.equals("myIndex")){
                    System.out.println(myIndex);
                    continue;
                }
                if(command.equals("myVector")){
                    System.out.println(distanceVector.toString());
                    continue;
                }
                if(command.equals("menu")){
                    menu();
                }

                if(command.equals("exit")){
                    System.exit(0);
                }

                if(command.equals("TOKEN") && token && distanceVector.size() > 0){
                    String destination = passToken();
                    String finalMsg = destination + "~";


                }
                //Command syntax: send node "message"
                //Example: send c "hello world"
                if(command.startsWith("send")){
                    //TODO: packet sending
                    String[] splitCommand = command.split(" ");
                    //Packet header is 6 bytes total
                    //(temporary) Packet header format: LENGTH=0 TTL=3 SEQ_NUM=0 SOURCEADDR DESTADDR PAYLOAD
                    byte[] header = new byte[6];
                    header[0] = 0; //length
                    header[1] = 3; //ttl
                    header[2] = 0; //Sequence number
                    header[3] = 0; //Sequence number
                    header[4] = myIndex.getBytes()[0]; //Source address
                    header[5] = splitCommand[1].getBytes()[0]; //Destination addrress
//                    byte length = 0;
//                    byte ttl = 3;
//                    byte[] seq_num = new byte[2];
//                    byte sourceaddr = myIndex.getBytes()[0];
//                    byte destaddr = splitCommand[1].getBytes()[0];
                    byte[] payload = splitCommand[2].getBytes();

                    String packet = Base64.getEncoder().encodeToString(header) +
                                    Base64.getEncoder().encodeToString(payload);

                    System.out.println("Sent packet: " + packet);
                    sendMessage(packet);
                    continue;
                }
            }
        } catch (InterruptedException e){
            System.exit(2);
        } catch (IOException e){
            System.exit(2);
        }        
    }

    public static void main(String args[]) {
        if(args.length > 0){
            frequency = Integer.parseInt(args[0]);
        }
        new MyProtocol(SERVER_IP, SERVER_PORT, frequency);        
    }

    private class receiveThread extends Thread {
        private BlockingQueue<Message> receivedQueue;

        public receiveThread(BlockingQueue<Message> receivedQueue){
            super();
            this.receivedQueue = receivedQueue;
        }

        public void printByteBuffer(ByteBuffer bytes, int bytesLength){
            for(int i=0; i<bytesLength; i++){
                System.out.print( Byte.toString( bytes.get(i) )+" " );
            }
            System.out.println();
        }

        public void run(){
            while(true) {
                try{
                    Message m = receivedQueue.take();
                    if (m.getType() == MessageType.BUSY){
                        System.out.println("BUSY");
                    } else if (m.getType() == MessageType.FREE){
                        System.out.println("FREE");
                        if(tempMsg != null){ //Whenever the channel is free, check if we have a message to send.
                            sendMessage(tempMsg);
                            tempMsg = null; //After sending the message, reset the message buffer to null.
                        }
                    } else if (m.getType() == MessageType.DATA){
                        System.out.print("DATA: ");
//                        printByteBuffer( m.getData(), m.getData().capacity() ); //Just print the data
//
//                        byte[] test = m.getData().array();
//                        byte[] payload = Arrays.copyOfRange(test, 6, test.length-1);
//                        String s =  Base64.getEncoder().encodeToString(payload);; //Decode the data recieved
                        String s = StandardCharsets.UTF_8.decode(m.getData()).toString(); //Decode the data recieved
                        System.out.println(s); //Print the data
                    } else if (m.getType() == MessageType.DATA_SHORT){
                        System.out.print("DATA_SHORT: ");
                        String s = StandardCharsets.UTF_8.decode(m.getData()).toString(); //Decode the data recieved
                        System.out.println(s); //Print the data
                        if(s.substring(0,1).equals("t")){

                        } else {
                            if (distanceVector.containsKey(s.substring(0, 1))) {//If index is not this node's index, add the route to distanceVector
                                continue;
                            } else {
                                String index = s.substring(0, 1);
                                Integer distance = Integer.valueOf(s.substring(1, 2));
                                possibleIndex.remove(index);
                                distanceVector.put(index, distance + 1);
                                tempMsg = index + (distance + 1);
                            }
                        }
                    } else if (m.getType() == MessageType.DONE_SENDING){
                        System.out.println("DONE_SENDING");
                    } else if (m.getType() == MessageType.HELLO){
                        System.out.println("HELLO");
                    } else if (m.getType() == MessageType.SENDING){
                        System.out.println("SENDING");
                    } else if (m.getType() == MessageType.END){
                        System.out.println("END");
                        System.exit(0);
                    }
                } catch (InterruptedException e){
                    System.err.println("Failed to take from queue: "+e);
                }                
            }
        }
    }
}

