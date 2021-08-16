import java.io.IOException;
import java.lang.System;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.Calendar;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

/**
* Server class that conatins the main function. This application is capable of handing producers that generate VDMS queries and consumers that take further process the data and eventually store the data into a data store. Currently both producers and consumers connect to the same port. A consumer is aware that this server is not a true VDMS server and will send 4 bytes to inform the server it is a consumer. A producer will send data as if this server is a normal VDMS server. Once the connection is initialized all dat acoming from a producer is directed to all of the consumers. When a response comes back from the producer, the response is then sent back to the originating producer.
*/

public class TestServer 
{
    ServerSocket myServerSocket;
    private final BlockingQueue<VdmsTransaction> producerDataQueue; /**< data queue holding maessage from a producer to go to a consumer (data store) */
    private final BlockingQueue<VdmsTransaction> consumerDataQueue; /**< data queue holding maessage from a consumer to go to a producer (client ingesting data) */
    private QueueServiceThread producerService;  /**< service thread to handle consumer connections*/
    private QueueServiceThread  consumerService;  /**< service thread to handle producer connection*/
    List<Integer> threadTypeArray;  /**< array with the flag of whether each thread is a producer or consumer */
    List<ClientServiceThread> threadArray;  /**< array of pointers to threads */
    int newThreadId;  /**< Id for the next thread to be created */
    private List<ClientServiceThread> producerList;  /**< array with all of the producers of data coming into server  - generally client */
    private List<ClientServiceThread> consumerList; /**< array with all of the consumers of data leaving from the server -generrally a plugin that directs data to a data store*/
    boolean ServerOn; /**< flag indicating whether the server is initialized */
    
       /**
   * constructor to create a TestServer object. This constructor needsno parameters becasue the NETWORK_PORT variable is pulled from the environment variables. When a client connects to the server, a ClientServiceThread is created that can handle both consumers and producers that connect.
   */
    public TestServer() { 
        //Create a queue for each direction 
        threadTypeArray = new ArrayList();
        producerDataQueue = new ArrayBlockingQueue<VdmsTransaction>(256);
        producerService = new QueueServiceThread(producerDataQueue, this, 1);
        producerService.start();
        consumerDataQueue = new ArrayBlockingQueue<VdmsTransaction>(256);
        consumerService = new QueueServiceThread(consumerDataQueue, this, 0);
        consumerService.start();
        threadArray = new ArrayList();
        newThreadId = 0;
        producerList = new ArrayList();
        consumerList = new ArrayList();
        
        System.out.println(System.getenv("NETWORK_PORT"));
        try 
        {
            myServerSocket = new ServerSocket(Integer.parseInt(System.getenv("NETWORK_PORT")));
            ServerOn = true;
        } 
        catch(IOException ioe) 
        { 
            System.out.println("Could not create server socket");
            System.exit(-1);
        } 
        
        Calendar now = Calendar.getInstance();
        SimpleDateFormat formatter = new SimpleDateFormat(
        "E yyyy.MM.dd 'at' hh:mm:ss a zzz");
        System.out.println("It is now : " + formatter.format(now.getTime()));
        
        while(ServerOn) { 
            try { 
                
                Socket clientSocket = myServerSocket.accept();
                ClientServiceThread cliThread = new ClientServiceThread(this, clientSocket, newThreadId);
                cliThread.start();
                newThreadId++;
                threadArray.add(cliThread);
            } 
            catch(IOException ioe)
            { 
                System.out.println("Exception found on accept. Ignoring. Stack Trace :"); 
                ioe.printStackTrace(); 
            }  
        } 
        try { 
            myServerSocket.close(); 
            System.out.println("Server Stopped"); 
        } 
        catch(Exception ioe) { 
            System.out.println("Error Found stopping server socket"); 
            System.exit(-1); 
        } 
    }
    
    public Boolean GetServerOn()
    {
        return ServerOn;
    }
    
    public void SetServerOn(Boolean nValue)
    {
        ServerOn = nValue;
    }
    
    public void SetThreadType(int threadId, int threadType )
    {
        threadTypeArray.set(threadId, threadType);
    }
    
    public void AddToProducerQueue(VdmsTransaction message )
    {
        try
        {
            producerDataQueue.put(message);
        }
        catch(InterruptedException e)
        {
            e.printStackTrace();
            System.exit(-1);
        }
    }
    
    public void AddToConsumerQueue(VdmsTransaction message )
    {
        try
        {
            consumerDataQueue.put(message);
        }
        catch(InterruptedException e)
        {
            e.printStackTrace();
            System.exit(-1);
        }
    }
    
    
    public void AddNewConsumer(ClientServiceThread nThread)
    {
        consumerList.add(nThread);
    }
    
    
    public void AddNewProducer(ClientServiceThread nThread)
    {
        producerList.add(nThread);
    }
    
    public List<ClientServiceThread> GetConsumerList()
    {      
        return consumerList;
    }
    
    public List<ClientServiceThread> GetProducerList()
    {      
        return producerList;
    }

    public List<Integer> GetThreadTypeArray()
    {
        return threadTypeArray;
    }
    
    public List<ClientServiceThread> GetThreadArray()
    { 
        return threadArray;
    }

    public static void main (String[] args)
    {
        new TestServer();
    }
}

