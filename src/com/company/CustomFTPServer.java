package com.company;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class CustomFTPServer extends Thread {

    // Commands
    private final String PORT = "PORT";
    private final String GPRT = "GPRT";
    private final String NLST = "NLST";
    private final String CWD = "CWD";
    private final String CDUP = "CDUP";
    private final String PUT = "PUT";
    private final String MKDR = "MKDR";
    private final String RETR = "RETR";
    private final String DELE = "DELE";
    private final String DDIR = "DDIR";
    private final String QUIT = "QUIT";

    private String rootDir;
    private String binding_address;
    private int control_port;
    private int data_port;
    private int clientCount;
    private ServerSocket serverSocket;
    private ServerSocket dataServerSocket;
    private Socket control_connection;
    private Socket data_connection;
    private BufferedWriter bw;
    private BufferedReader br;

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Enter Valid Arguments!!!");
            System.exit(-1);
        }

        try {
            CustomFTPServer server = new CustomFTPServer(args[0], Integer.parseInt(args[1]));

            server.initializeServer();
            int clients = 0;
            Socket clientSocket;


            while ((clientSocket = server.acceptClientSocket()) != null) {
                clients++;
                CustomFTPServer client = new CustomFTPServer(clientSocket, clients);
                client.start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public CustomFTPServer(String binding_address, int control_port) {
        this.binding_address = binding_address;
        this.control_port = control_port;
        this.rootDir = System.getProperty("user.dir");
    }

    public CustomFTPServer(Socket clientSocket, int clientCount) {
        this.control_connection = clientSocket;
        this.clientCount = clientCount;
        System.out.println("Client " + clientCount + " Connected!!!");
        this.rootDir = System.getProperty("user.dir");
    }

    public void run() {
        try {
            bw = new BufferedWriter(new OutputStreamWriter(control_connection.getOutputStream()));
            br = new BufferedReader(new InputStreamReader(control_connection.getInputStream()));
            listen2Client();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void initializeServer() throws IOException {
        serverSocket = new ServerSocket(control_port);
    }

    public boolean acceptClient() throws IOException {
        if ((control_connection = serverSocket.accept()) != null) {
            bw = new BufferedWriter(new OutputStreamWriter(control_connection.getOutputStream()));
            br = new BufferedReader(new InputStreamReader(control_connection.getInputStream()));
            return true;
        }
        return false;
    }

    public Socket acceptClientSocket() throws IOException {
        return serverSocket.accept();
    }

    public void listen2Client() throws IOException {
        boolean quit = false;
        String currentDir = rootDir;
        while (!quit) {
            String request = getClientResponse();
            if (request.indexOf(PORT) != -1) {
                data_port = PORTCommand(request);
                sendSuccess();
                System.out.println("Data Port Acquired!!!");
            } else if (request.indexOf(GPRT) != -1) {
                System.out.println(GPRT);
                sendSuccess();
                GPRTCommand();
            } else if (request.indexOf(NLST) != -1) {
                System.out.println(NLST);
                String data = NLSTCommand(currentDir);
                sendSuccess();
                sendDataThroughDataConnection(data);
            } else if (request.indexOf(CWD) != -1) {
                System.out.println(request);
                String fileName = readArgument(request);
                if (CWDCommand(currentDir, fileName)) {
                    currentDir += "\\" + fileName;
                    sendSuccess();
                } else {
                    System.out.println("Folder Does Not Exist!!!");
                    sendFailure();
                }
            } else if (request.indexOf(CDUP) != -1) {
                System.out.println(CDUP);
                if (currentDir.equals(rootDir)) {
                    System.out.println("You Are Already in the Root File!!!");
                    sendFailure();
                } else {
                    currentDir = CDUPCommand(currentDir);
                    sendSuccess();
                }
            } else if (request.indexOf(PUT) != -1) {
                System.out.println(request);
                String fileName = readArgument(request);
                sendSuccess();
                if (PUTCommand(currentDir, fileName))
                    sendSuccess();
                else {
                    System.out.println("File Already Exists!!!");
                    sendFailure();
                }
            } else if (request.indexOf(MKDR) != -1) {
                System.out.println(request);
                if (MKDRCommand(currentDir, readArgument(request)))
                    sendSuccess();
                else {
                    System.out.println("Folder Already Exists!!!");
                    sendFailure();
                }
            } else if (request.indexOf(RETR) != -1) {
                System.out.println(request);
                String fileName = readArgument(request);
                byte[] data = RETRCommand(currentDir + "\\" + fileName);
                sendSuccess();
                sendFile(data);
            } else if (request.indexOf(DELE) != -1) {
                System.out.println(DELE);
                String fileName = readArgument(request);
                if(DELECommand(currentDir, fileName))
                    sendSuccess();
                else
                {
                    System.out.println("There is no Such File!!!");
                    sendFailure();
                }
            } else if (request.indexOf(DDIR) != -1) {
                System.out.println(request);
                if (DDIRCommand(currentDir, readArgument(request)))
                    sendSuccess();
                else {
                    System.out.println("There is no Such Folder!!!");
                    sendFailure();
                }
            } else if (request.indexOf(QUIT) != -1) {
                sendSuccess();
                System.out.println("Client " + clientCount + " Quit!!!");
                quit = QUITCommand();
            }
        }
    }

    public String readArgument(String request) {
        return request.split(" ")[1].split("\r\n")[0];
    }

    public String getClientResponse() throws IOException {
        String response = "";
        String str = "";
        while ((str = br.readLine()) != null) {
            response += str;
            if (response.length() != 0)
                break;
        }

        return response;
    }

    public int findAvailablePort() throws IOException {
        int port = -1;
        ServerSocket tempSocket = checkForAvailablePorts();
        if (tempSocket != null) {
            port = tempSocket.getLocalPort();
        }
        tempSocket.close();
        return port;
    }

    public ServerSocket checkForAvailablePorts() {
        int startingPort = 49152;
        int endingPort = 65535;
        for (int i = startingPort; i < endingPort + 1; i++) {
            try {
                return new ServerSocket(i);
            } catch (IOException ex) {
                continue;
            }
        }
        return null;
    }

    public String NLSTCommand(String currentDir) {
        String data = "";
        File folder = new File(currentDir);
        File[] fileList = folder.listFiles();
        for (int i = 0; i < fileList.length; i++)
            if (i != fileList.length - 1) {
                if (fileList[i].getName().indexOf(".") != -1) {
                    data += fileList[i].getName() + ":f\r\n";
                } else {
                    data += fileList[i].getName() + ":d\r\n";
                }
            } else {
                if (fileList[i].getName().indexOf(".") != -1) {
                    data += fileList[i].getName() + ":f";
                } else {
                    data += fileList[i].getName() + ":d";
                }
            }

        return data;
    }

    public boolean DELECommand(String currentDir, String fileName) {
        File currentFile = new File(currentDir);
        for (File f : currentFile.listFiles())
            if (f.getName().indexOf(fileName) != -1) {
                File deletedFile = new File(currentDir + "\\" + fileName);
                deletedFile.delete();
                return true;
            }
        return false;
    }

    public boolean MKDRCommand(String currentDir, String fileName) {
        File currentFolder = new File(currentDir);
        for (File f : currentFolder.listFiles())
            if (f.getName().indexOf(fileName) != -1)
                return false;
        File newFolder = new File(currentDir + "\\" + fileName);
        newFolder.mkdirs();
        return true;
    }

    public boolean DDIRCommand(String currentDir, String fileName) {
        File currentFile = new File(currentDir);
        for (File f : currentFile.listFiles())
            if (f.getName().indexOf(fileName) != -1) {
                File deletedFile = new File(currentDir + "\\" + fileName);
                deletedFile.delete();
                return true;
            }
        return false;
    }

    public int PORTCommand(String request) {
        String argument = request.split("\r\n")[0];
        argument = argument.split(" ")[1];
        return Integer.parseInt(argument);
    }

    public boolean CWDCommand(String currentDir, String fileName) {
        File currentFile = new File(currentDir);
        for (File f : currentFile.listFiles())
            if (f.getName().indexOf(fileName) != -1)
                return true;
        return false;
    }

    public String CDUPCommand(String currentDir) {
        int indexOfLastSlash = 0;
        for (int i = currentDir.length() - 1; i >= 0; i--)
            if (currentDir.charAt(i) == '\\') {
                indexOfLastSlash = i;
                break;
            }
        currentDir = currentDir.substring(0, indexOfLastSlash);
        return currentDir;
    }

    public void GPRTCommand() throws UnknownHostException, IOException {
        this.dataServerSocket = checkForAvailablePorts();
        sendDataThroughDataConnection("" + dataServerSocket.getLocalPort());
    }

    public byte[] RETRCommand(String fileDir) throws IOException {
        File target_file = new File(fileDir);
        int fileSize = (int) target_file.length();
        InputStream is = new FileInputStream(target_file);
        byte[] data = new byte[fileSize];
        is.read(data);
        is.close();
        return data;
    }

    public boolean PUTCommand(String currentDir, String fileName) throws UnknownHostException, IOException {
        File currentFile = new File(currentDir);
        for (File f : currentFile.listFiles())
            if (f.getName().indexOf(fileName) != -1)
                return false;
        byte[] data = listen2DataConnection();
        saveFile(data, currentDir + "\\" + fileName);
        return true;
    }

    public boolean QUITCommand() throws IOException {
        bw.close();
        br.close();
        control_connection.close();
        return true;
    }

    public void saveFile(byte[] data, String fileDir) throws IOException {
        File file = new File(fileDir);
        OutputStream os = null;
        os = new FileOutputStream(file);
        os.write(data);
        os.close();
        System.out.println("File Saved!!!");
    }

    // Listens to Data Connection
    public byte[] listen2DataConnection() throws UnknownHostException, IOException {
        data_connection = dataServerSocket.accept();
        DataInputStream in = new DataInputStream(new BufferedInputStream(data_connection.getInputStream()));
        byte[] data_length = new byte[2];
        in.read(data_length);
        int val = ((data_length[0] & 0xff) << 8) | (data_length[1] & 0xff);
        byte[] data = new byte[val];
        in.read(data);
        in.close();
        data_connection.close();
        return data;
    }

    public void sendDataThroughDataConnection(String data) throws UnknownHostException, IOException {
        data_connection = new Socket(this.binding_address, data_port);
        DataOutputStream dos = new DataOutputStream(data_connection.getOutputStream());
        ByteBuffer b = ByteBuffer.allocate(4);
        b.putInt(data.length());
        byte[] results = b.array();
        byte[] header = new byte[2];
        header[0] = results[2];
        header[1] = results[3];
        byte[] dataUSASCII = new byte[data.length()];
        for (int i = 0; i < data.length(); i++)
            dataUSASCII[i] = (byte) data.charAt(i);
        dos.write(header);
        dos.write(dataUSASCII);
        dos.close();
        data_connection.close();
    }

    public void sendFile(byte[] data) throws IOException {
        data_connection = new Socket(this.binding_address, data_port);
        DataOutputStream dos = new DataOutputStream(data_connection.getOutputStream());
        ByteBuffer b = ByteBuffer.allocate(4);
        b.putInt(data.length);
        byte[] results = b.array();
        byte[] header = new byte[2];
        header[0] = results[2];
        header[1] = results[3];
        dos.write(header);
        dos.write(data);
        dos.close();
        data_connection.close();
    }

    // Sends Success Response through the Control Connection
    public void sendSuccess() throws IOException {
        bw.write("200\r\n");
        bw.flush();
    }

    // Sends Failure Response through the Control Connection
    public void sendFailure() throws IOException {
        bw.write("400\r\n");
        bw.flush();
    }

}
