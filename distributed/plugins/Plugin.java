import java.io.IOException;
import java.lang.System;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.List;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Plugin
{
    protected BlockingQueue<VdmsTransaction> publisherDataQueue; /**< BlockingQue of vdms transactions that should be transmitted to producers */
    protected BlockingQueue<VdmsTransaction> subscriberDataQueue; /**< BlockingQue of vdms transactions that should be transmitted to subscribers / consumers */
    protected QueueServiceThread publisherService; /**<  thread for handling producers producers. This thread is responsible for taking data from the publisherDataQueue and sending this data to the producers. It also takes data from client and puts it into the subscriberDataQueue */
    protected QueueServiceThread  subscriberService; /**<  thread for handling producers consumer. This thread is responsible for taking data from the subscriberDataQueue and sending this data to the producers. It also takes data from client and puts it into the publisherDataQueue */
    protected List<PublisherServiceThread> publisherList; /**< list of threads that produce data that should be directed to consumers */
    protected List<SubscriberServiceThread> subscriberList; /**< list of threads that will receive data from producers */
    protected int threadId; /**<  keeps track of tne nid of the next thread that needs to be created. This is an id that can be used to identify the connection that is associated with a connection */
    protected int newMessageId; /**< message id of the next message to be created. Ths is used to keep track of what messages have received a response */
    protected ArrayList<Integer>[] outgoingMessageRegistry; /**< this structure registers whether a repsonse has been provided for each message. This prevents multiple consumer threads from responding to a database transaction from a produer. We use a circular buffer to keep track of these responses*/
    protected int outgoingMessageRegistrySize; /**< size  of the circular buffer OutgoingMessageRegistry */
    protected ArrayList<VdmsTransaction>[] outgoingMessageBuffer; /**<  buffer of messages that should be transmitted back to a producer. This buffer can be used to compare responses returning to ensure they match*/
    protected ArrayList<PassList> allFilterFields; /**<  ArrayList holding all of the PassLists that have been created. This is only neeed when we are perfoming filtering and not needed when performing replication*/

    public Plugin()
    {
        //Create a queue for each direction
        publisherDataQueue = new ArrayBlockingQueue<VdmsTransaction>(256);
        publisherService = new QueueServiceThread(publisherDataQueue, this, 1);
        publisherService.start();
        subscriberDataQueue = new ArrayBlockingQueue<VdmsTransaction>(256);
        subscriberService = new QueueServiceThread(subscriberDataQueue, this, 0);
        subscriberService.start();
        allFilterFields = null;
        publisherList = new ArrayList<PublisherServiceThread>();
        subscriberList = new ArrayList<SubscriberServiceThread>();
        threadId = 0;
        newMessageId = 0;
        //initialize the outgoign queue registry that stores information abou tmessages that have been sent
        outgoingMessageRegistrySize = 256;
        outgoingMessageRegistry = (ArrayList<Integer>[]) new ArrayList[outgoingMessageRegistrySize];
        outgoingMessageBuffer = (ArrayList<VdmsTransaction>[]) new ArrayList[outgoingMessageRegistrySize];
        for(int i = 0; i < outgoingMessageRegistrySize; i++)
        {
            outgoingMessageRegistry[i] = new ArrayList<Integer>();
            outgoingMessageBuffer[i] = new ArrayList<VdmsTransaction>();
        }
    }

    public void AddPublishersFromFile(String fileName)
    {
        try{
            JSONParser configParser = new JSONParser();
            Path configFilePath = Paths.get(fileName);
            String configData = Files.readString(configFilePath);
            JSONArray config = (JSONArray) configParser.parse(configData);
            for(int i = 0 ; i < config.size(); i++)
            {
                JSONObject connection = (JSONObject) config.get(i);
                TcpVdmsConnection tmpConnection = new TcpVdmsConnection(connection.toString());
                PublisherServiceThread thisServerServivceThread = new PublisherServiceThread(this, tmpConnection, threadId);
                threadId++;
                AddNewPublisher(thisServerServivceThread);
            }
        }
        catch(IOException e)
        {
            e.printStackTrace();
            System.exit(-1);
        }
        catch(ParseException e)
        {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public void AddSubscribersFromFile(String fileName)
    {
        try{
            JSONParser configParser = new JSONParser();
            Path configFilePath = Paths.get(fileName);
            String configData = Files.readString(configFilePath);
            JSONArray config = (JSONArray) configParser.parse(configData);
            for(int i = 0 ; i < config.size(); i++)
            {
                JSONObject connection = (JSONObject) config.get(i);
                TcpVdmsConnection tmpConnection = new TcpVdmsConnection(connection.toString());
                SubscriberServiceThread thisServerServivceThread = new SubscriberServiceThread(this, tmpConnection, threadId);
                threadId++;
                AddNewSubscriber(thisServerServivceThread);
            }
        }
        catch(IOException e)
        {
            e.printStackTrace();
            System.exit(-1);
        }
        catch(ParseException e)
        {
            e.printStackTrace();
            System.exit(-1);
        }
    }


    /**
    * function to send data back to the producer. If we are doing replication, this function must ensure that multiple reponses for the same message are not returned to the producer. This is doen using a message registry - a circular buffer that documents when a message is sent to a consumer and prevents further responses for the same message.
    * @param message - this is the response from a consumer that should be sent to a producer
    * @see AddToConsumerQueue()
    * @return void()
    */
    public void AddToProducerQueue(VdmsTransaction message )
    {
        try
        {
            //For now check to see how many
            outgoingMessageBuffer[message.GetMessageId() % outgoingMessageRegistrySize].add(message);
            //If this is the first message recived for this outgoing message then send it back as the response
            if(outgoingMessageBuffer[message.GetMessageId() % outgoingMessageRegistrySize].size() == 1)
            {
                publisherDataQueue.put(message);
            }

            //we remove messages that are index - 1/2 (buffer size) for MessageBuffer and the MessageRegistry
            if(message.GetMessageId() - (int) (outgoingMessageRegistrySize / 2) >=0 )
            {
                outgoingMessageBuffer[(message.GetMessageId() - (int) (outgoingMessageRegistrySize / 2))%outgoingMessageRegistrySize].clear();
                outgoingMessageRegistry[(message.GetMessageId() - (int) (outgoingMessageRegistrySize / 2))%outgoingMessageRegistrySize].clear();
            }
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
            message.SetMessageId(newMessageId);
            message.SetTimestamp(System.currentTimeMillis());
            newMessageId++;
            subscriberDataQueue.put(message);
        }
        catch(InterruptedException e)
        {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    //Add this entry to list of consumers
    public void AddNewSubscriber(SubscriberServiceThread nThread)
    {
        subscriberList.add(nThread);
    }

    public void AddNewPublisher(PublisherServiceThread nThread)
    {
        publisherList.add(nThread);
    }

    public List<SubscriberServiceThread> GetSubscriberList()
    {
        return subscriberList;
    }

    public List<PublisherServiceThread> GetPublisherList()
    {
        return publisherList;
    }

    public void AddOutgoingMessageRegistry(int messageId, int threadId)
    {
        outgoingMessageRegistry[messageId % outgoingMessageRegistrySize].add(threadId);
    }

    protected void InitThreads()
    {
        //Start all of the threads. We start the destination nodes first so that no data from the sources is acceptd
        for(int i = 0; i < publisherList.size(); i++)
        {
            publisherList.get(i).start();
        }
        for(int i = 0; i < subscriberList.size(); i++)
        {
            subscriberList.get(i).start();
        }
    }

    //function is used when filtering data to different consumers but not for replication
    protected void LoadFilterFields(String filterConfigFileName)
    {
        try
        {
            allFilterFields = new ArrayList<PassList>();
            Path configFilePath = Paths.get(filterConfigFileName);
            String configData = Files.readString(configFilePath);
            JSONParser configParser = new JSONParser();
            JSONArray config = (JSONArray) configParser.parse(configData);
            for(int i = 0; i < config.size(); i++)
            {
                PassList tmpPassList = new PassList(config.get(i).toString());
                allFilterFields.add(tmpPassList);
            }
        }
        catch(IOException e)
        {
            e.printStackTrace();
            System.exit(-1);
        }
        catch(ParseException e)
        {
            e.printStackTrace();
            System.exit(-1);
        }
    }

}