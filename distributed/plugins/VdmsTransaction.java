import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
* A class holding transactions between a VDMS client and a VDMS server. A basic transaction includes a 4-byte array holding the size and a buffer holding a protobuf with the actual transaction. The transactions between plugins also have additional metadata used for capturing teelemtry information such as timestamps, the message infomration, and thread originating the transaction.
*/
class VdmsTransaction
{
    byte size[];  /**< buffer that contains the size of data that will be transmitted with VDMS protobuf */
    byte buffer[];  /**< buffer holding the VDMS protobuf */
    int messageId;  /**< id assigned to message  */
    int threadId;   /**< threadId corresponding with sender of this message */
    long timestamp;  /**< time at which this message was created */
    Boolean emptyTransactionFlag;  /**< flag indicating whether this message contains valid information */

    /**
    * a constructor to create a new VDMS transaction. The messageId and timestamp receive non-valid numbers and will be set by other functions
    * @param nSize size of protobuf transaction as a byte array. This is same format that is sent by client code
    * @param nBuffer buffer holding protobuf with VDMS Transaction
    */
    public VdmsTransaction(byte[] nSize, byte[] nBuffer)
    {
        emptyTransactionFlag = true;
        if(nBuffer.length > 0)
        {
            emptyTransactionFlag = false;
        }
        size = nSize;
        buffer = nBuffer;
        messageId = -1;
        timestamp = 0;
    }

    /**
    * a constructor to create a new VDMS transaction. The messageId and timestamp receive non-valid numbers and will be set by other functions
    * @param nSize size of protobuf transaction as a byte array. Size is sent as an integer and must be converted to buffer format used by client
    * @param nBuffer buffer holding protobuf with VDMS Transaction
    */
    public VdmsTransaction(int nSize, byte[] nBuffer)
    {
        emptyTransactionFlag = true;
        if(nBuffer.length > 0)
        {
            emptyTransactionFlag = false;
        }
        size = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(nSize).array();
        buffer = nBuffer;
        messageId = -1;
        timestamp = 0;
    }

    /**
    * function to return whether the transaction has valid data
    * @return Boolean flag that indicates if this transaction contains valid data
    */
    public Boolean GetEmptyTransactionFlag()
    {
        return emptyTransactionFlag;
    }

    /**
    * function to get size of the VDMS transaction protobuf
    * @return byte array that holds the size of the VDMS database transaction
    */
    public byte[] GetSize()
    {
        return size;
    }

    /**
    * function to get the contents of the protobuf containing the VDMS database transaction
    * @return byte array that holds the protobuf content of VDMS transaction
    */
    public byte[] GetBuffer()
    {
        return buffer;
    }

    /**
    * function to get the id of the message
    * @see SetMessageId()
    * @return ID of the current message - incremental value that the plugin has received
    */
    public int GetMessageId()
    {
        return messageId;
    }

    /**
    * function to set the id of the current transaction
    * @param nId new ID  corresponding with thread that created this transaction
    * @see GetMessageId()
    * @return void()
    */
    public void SetMessageId(int nId)
    {
        messageId = nId;
    }

    /**
    * function to get the id of the thread that originating this transaction
    * @see SetThreadId()
    * @return timestamp corresponding to time at which this tranaction was created
    */
    public int GetThreadId()
    {
        return threadId;
    }

    /**
    * function to set the timestamp of new transaction with a new value
    * @param nId new ID  corresponding with thread that created this transaction
    * @see GetThreadId()
    * @return void()
    */
    public void SetThreadId(int nId)
    {
        threadId = nId;
    }

    /**
    * function to get the timestamp of a transaction
    * @see SetTimestamp()
    * @return timestamp  corresponding to time at which this tranaction was created
    */
    public long GetTimestamp()
    {
        return timestamp;
    }

    /**
    * function to set the timestamp of new transaction with a new value
    * @param nTimestamp new timestamp corresponding with message creation time
    * @see GetTimestamp()
    * @return void()
    */
    public void SetTimestamp(long nTimestamp)
    {
        timestamp = nTimestamp;
    }

}
