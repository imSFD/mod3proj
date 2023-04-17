import client.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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
//TODO: packet loss
public class MyProtocol {

    // The host to connect to. Set this to localhost when using the audio interface tool.
    private static String SERVER_IP = "netsys.ewi.utwente.nl"; //"127.0.0.1";
    // The port to connect to. 8954 for the simulation server.
    private static int SERVER_PORT = 8954;
    // The frequency to use.
    private static int frequency = 4803;
    // View the simulator at https://netsys.ewi.utwente.nl/integrationproject/

    private BlockingQueue<Message> receivedQueue;
    private BlockingQueue<Message> sendingQueue;


    private Random rand;

    //indexare
    private ArrayList<String> possibleIndex;
    private String myIndex;

    private String restOfMessage = null;
    private String receivedMessage = "";
    private String destination = null;

//    private boolean messageIsFragmented = false;

    private String fragmentMessage(String message){
        if(message.length() < 26){
            restOfMessage = null;
            destination = null;
            return message;
        } else {
            String foo = message.substring(0,26);
            restOfMessage = message.substring(27);
            return foo;
        }
    }

    private String assignIndex() {
        myIndex = possibleIndex.get(0);
        possibleIndex.remove(0);
        distanceVector.put(myIndex, 0);
        if(Objects.equals(getIndex(), "a")){
            token = true;
            System.out.println("You have the token");
        }
        return myIndex;
    }

    public String getIndex() {
        return myIndex;
    }

    //distance vectors
    private HashMap<String, Integer> distanceVector;

    private MessageType state = MessageType.FREE;



    private String tempMsg = null; //Maybe rename?

    private boolean ack_bool = false;

    //Token passing
    private boolean token = false;
    private Message lastPacketRecieved = null;

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

//    private void sendMessage()

    //If input string is shorter than 32 bytes, add padding. We will use "@" as padding
    private byte[] addPadding(byte[] input){
        if((int) input[0] > 32){
            System.out.println("Packet is longer than 32 characters!");
            return null;
        } else {
            for(int i = (int) input[0]; i < 32; i++){
                input[i] = 64;
            }
            return input;
        }
    }


    //Token passing methods
    //Get list of neighbours
    private Map<String,Integer> getNeighbours() {
        return distanceVector.entrySet().stream().
            filter(map -> map.getValue() == 1).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    //returns a random neighbour
    private String getRandomNeighbour() {
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

    //Token is a DATA_SHORT type, format: t sourceaddr destaddr (example: tb)
    private void passToken() throws InterruptedException {

        String destination = getRandomNeighbour();
        String source = getIndex();
        String finalMsg = "t" + encodeToken(source, destination);

        sendMessage(finalMsg);
        token = false;
    }

    private void passToken(String destination) throws InterruptedException {

        String source = getIndex();
        String finalMsg = "t" + encodeToken(source, destination);

        sendMessage(finalMsg);
        token = false;
    }

    //TODO: 1 byte = 8 bits, REDO!
    private String encodeToken(String sourceAddr, String destinationAddr){
        byte result = 0;
        switch (sourceAddr) {
            case "a":
                result = 0;
                break;
            case "b":
                result = 1 << 2;
                break;
            case "c":
                result = 2 << 2;
                break;
            case "d":
                result = 3 << 2;
                break;
        }

        switch (destinationAddr){
            case"a":
                break;
            case"b":
                result = (byte) (result + 1);
                break;
            case"c":
                result = (byte)  (result + 2);
                break;
            case"d":
                result = (byte)  (result + 3);
                break;
        }

        return String.valueOf((char) result);
    }

    public static String decodeToken(byte input){
        String res = "";
        byte input2 = (byte) (input >> 2);
        switch (input2) {
            case 0:
                res = res + "a";
                break;
            case 1:
                res = res + "b";
                input = (byte) (input ^ 4);
                break;
            case 2:
                res = res + "c";
                input = (byte) (input ^ 8);
                break;
            case 3:
                res = res + "d";
                input = (byte) (input ^ 12);
                break;
        }
        switch (input) {
            case 0:
                res = res + "a";
                break;
            case 1:
                res = res + "b";
                break;
            case 2:
                res = res + "c";
                break;
            case 3:
                res = res + "d";
                break;
        }
        return res;
    }

    private String constructAck(byte[] header){
        String res;
        res = String.valueOf(header[2]) + encodeToken(String.valueOf ((char) header[5]), String.valueOf((char)header[4]));
        return res;
    }

    private void menu(){
        System.out.println("token - PASS YOUR TOKEN TO THE NEXT NODE");
        System.out.println("exit -  EXIT THE PROGRAM");
        System.out.println("myVector - SHOW THE DISTANCE VECTOR OF THE CURRENT NODE");
        System.out.println("myIndex - GET AN INDEX IF YOU STILL DON'T HAVE ONE, FROM THE REMAINING ONES");
        System.out.println("menu - display this screen again");
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
                if(command.equals("devToken")){
                    System.out.println("You have the token");

                    token = true;
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

                if(command.equals("token") && token && distanceVector.size() > 0){//TODO: implement error message if indexing hasnt been done yet
                    if(!token)
                        System.out.println("You don't have the token");
                    else
                        passToken();
                }
                //Command syntax: send node "message"
                //Example: send c "hello world"
                if(command.startsWith("send")){
                    if(!token)
                        System.out.println("You don't have the token");
                    else {
                        String[] splitCommand = command.split(" ", 3);
                        //Packet header is 6 bytes total
                        //(temporary) Packet header format: LENGTH=0 TTL=3 SEQ_NUM=0 SOURCEADDR DESTADDR PAYLOAD

                        ByteBuffer packetBuffer; //Packet to send

                        byte[] header = new byte[6]; //Header
                        header[0] = 6; //length
                        header[1] = 3; //ttl
                        header[2] = 0; //Sequence number
                        header[3] = 1; //Sequence number
                        header[4] = myIndex.getBytes()[0]; //Source address
                        header[5] = splitCommand[1].getBytes()[0]; //Destination addrress
                        destination = splitCommand[1];

//                        System.out.println(header);

                        String message = splitCommand[2].substring(1, splitCommand[2].length() - 1); //the message without the quotations
                        message = fragmentMessage(message);
                        byte[] payload = message.getBytes(); //Payload
                        int plength = payload.length;

                        header[0] = (byte) (6+plength);

                        if(restOfMessage != null){
                            header[3] = 0;
                        }

                        //Put the whole packet in the buffer
                        packetBuffer = ByteBuffer.allocate(32);
                        packetBuffer.put(header);
                        packetBuffer.put(payload);



                        byte[] packetWithPadding = new byte[32];
                        packetWithPadding = addPadding(packetBuffer.array());
                        packetBuffer.clear();
                        packetBuffer.put(packetWithPadding);
                        //Create the message to send and send it
                        Message msg = new Message(MessageType.DATA, packetBuffer);
                        sendingQueue.put(msg);
//                        ack = false;

                        System.out.println("Sent packet: " + packetBuffer.toString() + " of length " + packetBuffer.array().length);
                        System.out.println("Header: " + header.toString());

                        if(getNeighbours().containsKey(splitCommand[1])) {
                            passToken(splitCommand[1]);
                        } else {
                            passToken();
                        }
                        continue;
                    }
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
            int timeToWait = 1;
            while(true) {
                try{
                    Message m = receivedQueue.take();
                    // assume sender sends a packet
                    if (m.getType() == MessageType.BUSY){
                        System.out.println("BUSY");
                        // wait for packet  arrival

                        timeToWait = timeToWait + 2;
//                        Thread.sleep(2^timeToWait);
//                        if(tempMsg2 != null){ //Whenever the channel is free, check if we have a message to send.
//                            if(rand.nextInt()<25  || timeToWait==6){
//                                sendMessage(tempMsg2);
//                                tempMsg2 = null;
//                                timeToWait=1;
//                            } else {
//                                // increase the time unless it send back
//                                timeToWait=timeToWait+1;
//                            }
//                        }

                        //System.out.println("BUSY");
                    } else if (m.getType() == MessageType.FREE){
                        System.out.println("FREE");
                        if(tempMsg != null){ //Whenever the channel is free, check if we have a message to send.
                            for(int i = 0; i < 10; i++){
                                Thread.sleep(rand.nextInt(2^timeToWait));
                                if(rand.nextInt(100) < 40){
                                    sendMessage(tempMsg);
                                }
                            }
                            tempMsg = null; //After sending the message, reset the message buffer to null.
                        }
                    } else if (m.getType() == MessageType.DATA){
                        lastPacketRecieved = m;
                        System.out.print("DATA: ");
                        String s = StandardCharsets.UTF_8.decode(m.getData()).toString(); //Decode the data recieved
                        System.out.println(s); //Print the data
                        byte[] header =  Arrays.copyOfRange(m.getData().array(), 0, 6);
                        if(header[5] == myIndex.getBytes()[0]){ //If this check is true, message is for this node

                            byte[] payload = Arrays.copyOfRange(m.getData().array(),6, header[0]);
                            String message = new String(payload);

                            receivedMessage += message;
                            if(header[3] == 1) {
                                System.out.println("Message recieved: " + receivedMessage);
                                receivedMessage = "";
                            }
                            String ack = constructAck(header);
                            byte[] ack_b = ack.getBytes();
                            ByteBuffer toSend = ByteBuffer.allocate(ack.length());
                            toSend.put(ack_b, 0, ack_b.length);
                            Message ack_m = new Message(MessageType.DATA_SHORT, toSend);
                            lastPacketRecieved = ack_m;
                            ack_bool = true;
                        }
                        Map<String,Integer> neighbours = getNeighbours();
                        if(neighbours.containsKey(String.valueOf((char) header[5]))){
                            System.out.println("neighbour");
                            lastPacketRecieved = m; //Save the packet in memory to send it further when the token is acquired
                        }
                    } else if (m.getType() == MessageType.DATA_SHORT){

                        System.out.print("DATA_SHORT: ");
                        String s = StandardCharsets.UTF_8.decode(m.getData()).toString(); //Decode the data recieved
                        System.out.println(s); //Print the data
                        if(s.substring(0,1).equals("t")) { //If the first byte is a T, that means its a token that has been passed
                            if (decodeToken(m.getData().array()[1]).substring(1, 2).equals(myIndex)) { //Get the packet data, transform it to an array of bytes, decode the token and see if the destination address equals to this node's address
                                token = true;
                                System.out.println("You have the token");
                                if (lastPacketRecieved != null) { //for retransmission, if the packet is destined for another node
                                    byte[] header = Arrays.copyOfRange(lastPacketRecieved.getData().array(), 0, 6);
                                    if (getNeighbours().containsKey(String.valueOf((char) header[5]))) { //If the destination of the packet is a neighbor
                                        sendingQueue.put(lastPacketRecieved);
                                        lastPacketRecieved = null;
                                        passToken(String.valueOf((char) header[5]));
                                    } else { //If the destination is not a neighbor, pass the token to a neighbor that is NOT the one that sent it TODO:implement
                                        Map<String, Integer> neighbors = getNeighbours();
                                        if(!ack_bool) {
                                            neighbors.remove(decodeToken(m.getData().array()[1]).substring(0, 1));
                                        }
                                        ArrayList<String> neighbor_indexes = new ArrayList<>(neighbors.keySet());
                                        String neighbor = neighbor_indexes.get(0);

                                        sendingQueue.put(lastPacketRecieved);
                                        lastPacketRecieved = null;
                                        passToken(neighbor);
                                        ack_bool = false;
                                    }
                                }
                            } else {
                                lastPacketRecieved = null;
                            }
                            if(restOfMessage != null){ //For fragmentation
                                ByteBuffer packetBuffer;
                                String dest = destination;

                                String message = fragmentMessage(restOfMessage);

                                byte[] header = new byte[6];
                                header[0] = 6; //length
                                header[1] = 3; //ttl
                                header[2] = 0; //Sequence number
                                if(restOfMessage != null) {
                                    header[3] = 0; //Sequence number
                                } else {
                                    header[3] = 1;
                                }
                                header[4] = myIndex.getBytes()[0]; //Source address
                                header[5] = dest.getBytes()[0]; //Destination addrress



                                byte[] payload = message.getBytes();
                                int plength = payload.length;

                                header[0] = (byte) (6+plength);

                                packetBuffer = ByteBuffer.allocate(32);
                                packetBuffer.put(header);
                                packetBuffer.put(payload);

                                byte[] packetWithPadding = addPadding(packetBuffer.array());
                                packetBuffer.clear();
                                packetBuffer.put(packetWithPadding);
                                //Create the message to send and send it
                                Message msg = new Message(MessageType.DATA, packetBuffer);
                                sendingQueue.put(msg);
//                        ack = false;

                                System.out.println("Sent packet: " + packetBuffer.toString() + " of length " + packetBuffer.array().length);
                                System.out.println("Header: " + header.toString());

                                if(getNeighbours().containsKey(dest)) {
                                    passToken(dest);
                                } else {
                                    passToken();
                                }
                            }

                        } else if (s.substring(0,1).equals("0") || s.substring(0,1).equals("1")){
                            if (decodeToken(m.getData().array()[1]).substring(1, 2).equals(myIndex)) {
                                System.out.println("Ack recieved!");
//                                ack = true;
                            } else {
                                lastPacketRecieved = m;
                            }
                        } else { //Else, its from initial flooding
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
                        timeToWait = 1;
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

