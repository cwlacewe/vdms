import java.io.File;
import java.io.IOException;
import java.lang.System;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
* A constructor for TestPlugin class. This class contains a main function - thus is an application. By default, this application will perform replication where all of the data that is received from a producr (generally a  client) is transmitted to all of the connected consumers (data stores such as a VDMS server)
*/
public class TestPlugin extends Plugin
{

    /**
    * A constructor for TestPlugin class.
    * @param configFileName - file name that contains the file name so the producer and consumer configuration files. The path used for the configFileName is also used for the source_config_file and destination_config_file
    */
    public TestPlugin(String configFileName)
    {
        try
        {
            Path configFilePath = Paths.get(configFileName);
            String configData = Files.readString(configFilePath);
            String configFileDirectory = configFilePath.getParent().toString();
            JSONParser configParser = new JSONParser();
            JSONObject config = (JSONObject) configParser.parse(configData);
            String sourceConfigFileName = (String) config.get("source_config_file");
            AddPublishersFromFile(configFileDirectory + File.separator + sourceConfigFileName);
            String destinationConfigFileName = (String) config.get("destination_config_file");
            AddSubscribersFromFile(configFileDirectory + File.separator + destinationConfigFileName);
        }
        catch(ParseException e)
        {
            e.printStackTrace();
            System.exit(-1);
        }
        catch(IOException e)
        {
            e.printStackTrace();
            System.exit(-1);
        }
        InitThreads();
    }

    public static void main (String[] args)
    {
        new TestPlugin(args[0]);
    }

}

