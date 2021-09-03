import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;
import VDMS.protobufs.QueryMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

class SubscriberServiceThread extends Thread
{
    VdmsConnection connection; /**< VDMS connection that includes socket and additional socket information */
    boolean m_bRunThread = true; /**< flag that keeps the thread inside a while loop while it is running  */
    int id; /**< thread id assigigned when all threads are created */
    int type; /**< flag indicating whether this thread is a consumer or producer */
    int messageId; /**< messageId  indicating how many messages have come through this consumer*/
    int passListId; /**<  list of messages that can be forwaded to this consumer */
    Plugin manager; /**< manager that is the source for data */
    BlockingQueue<VdmsTransaction> responseQueue; /**< qeuee holding newly arrived data that should be sent to consumers */
    VdmsTransaction initSequence; /**< sequence to be sent to manager to indicate plugin type */
    PassList passList; /**< list of types of messages that should be communicated to consumers  */

    public SubscriberServiceThread()
    {
        super();
        responseQueue = null;
        initSequence = null;
        passList = null;
        passListId = -1;
        connection = null;
    }

    public SubscriberServiceThread(Plugin nManager, VdmsConnection nConnection, int nThreadId)
    {
        responseQueue = new ArrayBlockingQueue<VdmsTransaction>(128);
        manager = nManager;
        connection = nConnection;
        id = nThreadId;
        messageId = 0;
        passListId = nConnection.GetPassListId();
        passList = null;
    }

    public void SetPassList(PassList nPassList)
    {
        passList = nPassList;
    }

    public PassList GetPassList()
    {
        return passList;
    }

    public int GetPassListId()
    {
        return passListId;
    }

    public void Publish(VdmsTransaction newMessage)
    {
        boolean passMessage = false;
        //if there is no passList all values should be forwarded
        if(passList == null)
        {
            responseQueue.add(newMessage);
        }
        else // perform this task if message filtering is enabled - filtering data between two consumers
        {
            try
            {
                /// \todo need to add a switch statement based on the query type
                /// enum 0 is match the field type // enum 1 is match the autoquery id
                QueryMessage.queryMessage newTmpMessage = QueryMessage.queryMessage.parseFrom(newMessage.GetBuffer());
                JSONParser jsonString = new JSONParser();
                JSONArray jsonArray = (JSONArray) jsonString.parse(newTmpMessage.getJson());
                JSONObject jsonObject = (JSONObject) jsonArray.get(0);
                //Iterate through the keys in this message and check against the keys that should be published to this node
                for (Object key : jsonObject.keySet())
                {
                    for(int i = 0; i < passList.GetCriteriaSize(); i++)
                    {
                        String checkString = passList.GetCriteriaValue(i);
                        if(checkString.equals(key))
                        {
                            passMessage = true;
                        }
                    }
                    System.out.println(key.toString());
                }
                if(passMessage)
                {
                    responseQueue.add(newMessage);
                }
            }
            catch(InvalidProtocolBufferException e)
            {
                System.exit(-1);
            }
            catch(ParseException e)
            {
                System.exit(-1);
            }
        }
    }

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
                    if(!connection.GetInitSequence().GetEmptyTransactionFlag())
                    {
                        connection.WriteInitMessage();
                    }
                    threadInitFlag = true;
                }

                returnedMessage = responseQueue.take();
                //need to check to see if there is a message id - needs to have message id here for pass back up
                String tmpString = new String(returnedMessage.GetBuffer(), StandardCharsets.UTF_8);
                //System.out.println(tmpString);
                manager.AddOutgoingMessageRegistry(returnedMessage.GetMessageId(), id);
                connection.Write(returnedMessage);
                ++messageId;
                newTransaction = connection.Read();
                newTransaction.SetMessageId(returnedMessage.GetMessageId());
                newTransaction.SetThreadId(returnedMessage.GetThreadId());
                newTransaction.SetTimestamp(System.currentTimeMillis() - returnedMessage.GetTimestamp());
                manager.AddToProducerQueue(newTransaction);
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
