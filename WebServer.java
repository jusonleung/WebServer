import java.io.*;
import java.net.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public final class WebServer {

    public static void main(String argv[]) throws Exception{
        // set a port number
        int port_number = 8080;

        // Create welcoming socket
        ServerSocket welcomeSocket = new ServerSocket(port_number);

        while (true){
            // wait for a request
            Socket connectionSocket = welcomeSocket.accept();

            // Construct an object to process the HTTP request message.
            HttpRequest request = new HttpRequest(connectionSocket);

            // Create a new thread to process the request.
            Thread thread = new Thread(request);

            // Start the thread.
            thread.start();
        }
    }
}

final class HttpRequest implements Runnable{
    Socket socket;

    public HttpRequest(Socket socket) throws Exception  {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            processRequest();
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    private void processRequest() throws Exception{
        // get the current time
        Date now = new Date();

        String response_type;
        String fileName = null;

        // Create input stream, attached to socket
        InputStream InputStream = socket.getInputStream();
        BufferedReader inFromClient = new BufferedReader(new InputStreamReader(InputStream));

        // Create output stream, attached to socket
        DataOutputStream outToClient = new DataOutputStream(socket.getOutputStream());

        // Read in line from socket
        String requestMessageLine = inFromClient.readLine();
        System.out.println(requestMessageLine);

        // Extract the filename from the request line.
        StringTokenizer tokenizedLine = new StringTokenizer(requestMessageLine);
        String requestMethods = tokenizedLine.nextToken();

        // Get the filename
        fileName = tokenizedLine.nextToken();
        if (fileName.startsWith("/") == true )
            fileName = fileName.substring(1);

        // Get the client host name
        System.out.println(socket.getInetAddress());

        // Get the If-Modified-Since header date
        String headerLine = null;
        Date ifModifiedSince = null;
        DateFormat format = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy",Locale.ENGLISH);
        while ((headerLine = inFromClient.readLine()).length() != 0) {

            if (headerLine.startsWith("If-Modified-Since")){
                // Get the string of date and parse into Date type
                String temp = headerLine.substring(19);
                ifModifiedSince = format.parse(temp);
            }
        }
        // When the request methods is GET or HEAD
        if (requestMethods.equals("GET") || requestMethods.equals("HEAD")){

            // try to open the requested file
            boolean fileExists = true;
            File file = new File(fileName);
            int numOfBytes = (int) file.length();
            FileInputStream inFile = null;
            try{
                inFile = new FileInputStream(fileName);
            } catch (FileNotFoundException e){
                fileExists = false;
            }

            //Send the response message
            if(fileExists){ //if the requested file was found
                // Get the requested file's lastModified date
                Date lastModified = new Date(file.lastModified());

                // Response "304 Not Modified" if the if-Modified-Since date is equal or after the file's lastModified date
                if(ifModifiedSince != null && (ifModifiedSince.toString().equals(lastModified.toString()) || ifModifiedSince.after(lastModified))){
                    response_type = "304 Not Modified";
                    System.out.println(response_type);

                    // Status line
                    outToClient.writeBytes("HTTP/1.0 304 Not Modified\r\n");

                    // Header lines
                    outToClient.writeBytes("Server: Dioses/1.0\r\n");
                    outToClient.writeBytes("Date: "+ now+ "\r\n");
                    outToClient.writeBytes("Last-Modified: " + lastModified + "\r\n");
                    outToClient.writeBytes("Content-Type: " + getContentType(fileName)+ "\r\n");
                    outToClient.writeBytes("Content-Length: " + numOfBytes+ "\r\n");
                }else { // Response of "200 OK"
                    response_type = "200 OK";
                    System.out.println(response_type);
                    byte[] fileInBytes = new byte[numOfBytes];
                    inFile.read(fileInBytes);

                    // Status line
                    outToClient.writeBytes("HTTP/1.0 200 Document Follows\r\n");

                    // Header lines
                    outToClient.writeBytes("Server: Dioses/1.0\r\n");
                    outToClient.writeBytes("Date: "+ now+ "\r\n");
                    outToClient.writeBytes("Last-Modified: " + lastModified + "\r\n");
                    outToClient.writeBytes("Content-Type: " + getContentType(fileName)+ "\r\n");
                    outToClient.writeBytes("Content-Length: " + numOfBytes+ "\r\n");
                    outToClient.writeBytes("\r\n");

                    // if request methods is GET, include the bytes of file in data
                    if(requestMethods.equals("GET"))
                        outToClient.write(fileInBytes, 0, numOfBytes);
                }
            }else {// if the requested file was not found
                // Response of "404 File Not Found"
                response_type = "404 File Not Found";
                System.out.println(response_type);

                // Status line
                outToClient.writeBytes("HTTP/1.0 404 File Not Found\r\n");

                // Header lines
                outToClient.writeBytes("Server: Dioses/1.0\r\n");
                outToClient.writeBytes("Date: "+ new Date()+ "\r\n");
                outToClient.writeBytes("\r\n");

                // Data
                outToClient.writeBytes("<HTML>" + "<HEAD><TITLE>Not Found</TITLE></HEAD>" + "<BODY>404 File Not Found</BODY></HTML>");
            }

        } else{// if the request method was invalid
            // Response of "400 Bad Request"
            response_type = "400 Bad Request";
            System.out.println(response_type);

            // Status line
            outToClient.writeBytes("HTTP/1.0 400 Bad Request\r\n");

            // Header lines
            outToClient.writeBytes("Server: Dioses/1.0\r\n");
            outToClient.writeBytes("Date: "+ now+ "\r\n");
            outToClient.writeBytes("\r\n");

            // Data
            outToClient.writeBytes("<HTML>" + "<HEAD><TITLE>Bad Request</TITLE></HEAD>" + "<BODY>Bad Request</BODY></HTML>");
        }

        // Close streams and socket.
        outToClient.close();
        inFromClient.close();
        socket.close();

        // write the log file
        try{
            BufferedWriter log = new BufferedWriter (new FileWriter("log.txt", true));
            log.append(socket.getInetAddress().toString().substring(1) + " " + now + " " + fileName + " " + response_type);
            log.newLine();
            log.flush();
            log.close();
            System.out.println("Log file registered\n");
        } catch (IOException e) {
            System.out.println("An error occurred in writing log file");
        }
    }

    //function getting content type from file name
    private static String getContentType(String fileName) {
        if(fileName.endsWith(".htm") || fileName.endsWith(".html")) {
            return "text/html";
        }if (fileName.endsWith(".jpg"))
            return "image/jpeg";
        if (fileName.endsWith(".gif"))
            return "image/gif";
        if (fileName.endsWith(".png"))
            return "image/png";
        return "application/octet-stream";
    }
}