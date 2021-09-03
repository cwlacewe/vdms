import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;

class PublisherServiceThread extends Thread
{
    protected VdmsConnection connection; /**< VDMS connection that includes socket and additional socket information */
    protected boolean m_bRunThread = true; /**< flag that keeps the thread inside a while loop while it is running  */
    protected int id; /**< thread id assigigned when all threads are created */
    protected int type; /**< flag indicating whether this thread is a consumer or producer */
    protected int messageId; /**< messageId  indicating how many messages have come through this consumer*/
    protected Plugin manager; /**< manager that is the source for data */
    protected BlockingQueue<VdmsTransaction> responseQueue; /**< qeuee holding newly arrived data that should be sent to consumers */
    protected VdmsTransaction initSequence; /**< sequence to be sent to manager to indicate plugin type */

    /**
    * A constructor for a thread to handle incoming connections from clients. This empty constructor is only used when the object is initialized after it is created
    */
    public PublisherServiceThread()
    {
        super();
        responseQueue = null;
        initSequence = null;
    }

    /**
    * a constructor that creates a thread to handle incoming connections from clients
    * @param nManager pointer to the manager that is handling the incoming manager
    * @param nConnection  socket that connectes the publisher to a subscriber
    * @param nThreadId Id of the thread - this value is assigned by application code and not the automatic id created by OS
    */
    PublisherServiceThread(Plugin nManager, VdmsConnection nConnection, int nThreadId)
    {
        responseQueue = new ArrayBlockingQueue<VdmsTransaction>(128);
        manager = nManager;
        id = nThreadId;
        messageId = 0;
        connection = nConnection;
    }

    /**
    * publish message to appropriate recipients
    * @param nMessage message that should be passed to connecting socket
    * @return void()
    */
    public void Publish(VdmsTransaction newMessage)
    {
        responseQueue.add(newMessage);
    }

    /**
    * a loop to handle the client connections. This function is repsonsible for handling connections for both producers (managers) and consumers (clients). This function will wait for a message in the service queue. Once that message arrives, it will be placed into the appropriate queue and then published to the proper recipients. Additional metadata is transmitted using the ReadExtended() and WriteExtended() functions that support transmission of metadata in addition to basic VDMS transaction
    */
    public void run()
    {
        VdmsTransaction returnedMessage;
        Boolean threadInitFlag = false;
        VdmsTransaction newTransaction = null;
        try
        {
            while(m_bRunThread)
            {
                if(threadInitFlag == false)
                {
                    //only write the value if there is a valid init sequence
                    if(connection.GetInitSequence() != null)
                    {
                        connection.WriteInitMessage();
                    }
                    threadInitFlag = true;
                }
                newTransaction = connection.ReadExtended();
                manager.AddToConsumerQueue(newTransaction);
                returnedMessage = responseQueue.take();
                connection.WriteExtended(returnedMessage);
                ++messageId;
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            connection.Close();
        }
    }
}
