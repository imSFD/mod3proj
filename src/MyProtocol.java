import client.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
* This is just some example code to show you how to interact 
* with the server using the provided client and two queues.
* Feel free to modify this code in any way you like!
*/

public class MyProtocol{

    // The host to connect to. Set this to localhost when using the audio interface tool.
    private static String SERVER_IP = "netsys.ewi.utwente.nl"; //"127.0.0.1";
    // The port to connect to. 8954 for the simulation server.
    private static int SERVER_PORT = 8954;
    // The frequency to use.
    private static int frequency = 4800; //TODO: Set this to your group frequency!
    // View the simulator at https://netsys.ewi.utwente.nl/integrationproject/

    private BlockingQueue<Message> receivedQueue;
    private BlockingQueue<Message> sendingQueue;

    //indexare
    private ArrayList<String> possibleIndex;
    private String myIndex;

    private String assignIndex(){
        myIndex = possibleIndex.get(0);
        possibleIndex.remove(0);
        distanceVector.put(myIndex, 0);
        return myIndex;
    }

    //distance vectors
    private HashMap<String,Integer> distanceVector;

    private String tempMsg = null; //Maybe rename?



    public MyProtocol(String server_ip, int server_port, int frequency){
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

                    byte[] index_b = (myIndex+0).getBytes(); //myIndex + distance to index (distance is 0 in this case)
                    ByteBuffer toSend = ByteBuffer.allocate(index_b.length); // copy data without newline / returns
                    toSend.put( index_b, 0, index_b.length); // enter data without newline / returns
                    Message msg;

                    msg = new Message(MessageType.DATA_SHORT, toSend);
                    sendingQueue.put(msg);

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
                if(command.equals("menu"){
                    continue; //TODO: implement
                }
//                read = System.in.read(temp.array()); // Get data from stdin, hit enter to send!
//                if(read > 0){
//                    if (temp.get(read-1) == '\n' || temp.get(read-1) == '\r' ) new_line_offset = 1; //Check if last char is a return or newline so we can strip it
//                    if (read > 1 && (temp.get(read-2) == '\n' || temp.get(read-2) == '\r') ) new_line_offset = 2; //Check if second to last char is a return or newline so we can strip it

////                    if( (read-new_line_offset) == )
//                    if( (read-new_line_offset) > 2 ){
//                        msg = new Message(MessageType.DATA, toSend);
//                    } else {
//                        msg = new Message(MessageType.DATA_SHORT, toSend);
//                    }
//                    sendingQueue.put(msg);
//                }
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
                        if(tempMsg != null){
                            byte[] msg = tempMsg.getBytes(); //myIndex + distance to index (distance is 0 in this case)
                            ByteBuffer toSend = ByteBuffer.allocate(msg.length); // copy data without newline / returns
                            toSend.put( msg, 0, msg.length); // enter data without newline / returns
                            Message message;

                            message = new Message(MessageType.DATA_SHORT, toSend);
                            sendingQueue.put(message);
                            tempMsg = null;
                        }
                    } else if (m.getType() == MessageType.DATA){
                        System.out.print("DATA: ");
                        printByteBuffer( m.getData(), m.getData().capacity() ); //Just print the data
                    } else if (m.getType() == MessageType.DATA_SHORT){
                        System.out.print("DATA_SHORT: ");
                        String s = StandardCharsets.UTF_8.decode(m.getData()).toString(); //Decode the data recieved
                        System.out.println(s); //Print the data
                        if(distanceVector.containsKey(s.substring(0,1))){//If index is not this node's index, add the route to distanceVector
                            continue;
                        } else {
                            String index = s.substring(0,1);
                            Integer distance = Integer.valueOf(s.substring(1,2));
                            possibleIndex.remove(index);
                            distanceVector.put(index, distance + 1);
                            tempMsg = index + (distance+1);
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

