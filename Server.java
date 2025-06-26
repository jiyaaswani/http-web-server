import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;



public class Server {
    private final ExecutorService threadPool;

    public Server(int poolSize){
        this.threadPool = Executors.newFixedThreadPool(poolSize);
    }

    public void handleClient(Socket clientSocket){
        try(BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            OutputStream out = clientSocket.getOutputStream();
        ){
            try{
                String requestLine = in.readLine();
                System.out.println("Received " + requestLine);

                Map<String,String> headers = new HashMap<>();
                String line;
                while(!(line = in.readLine()).isEmpty()){
                    String[] header = line.split(":", 2);
                    if(header.length == 2){
                        headers.put(header[0].trim(), header[1].trim());
                    }
                }

                String[] tokens = requestLine.split(" ");
                String method = tokens[0];
                String path = tokens[1];

                System.out.println("[" + getTimestamp() + "] Request: " + method + " " + path);

                System.out.println("Client: " + clientSocket.getInetAddress().getHostAddress());

                if(method.equals("POST")){
                    int contentLength = Integer.parseInt(headers.getOrDefault("Content-Length","0"));
                    char[] bodyChars = new char[contentLength];
                    in.read(bodyChars,0,contentLength);
                    String body = new String(bodyChars);

                    System.out.println("Received POST body: " + body);

                    String responseBody = "<h1>POST received</h1><p>" + body +"</p>";
                    PrintWriter pw = new PrintWriter(out);
                    pw.print("HTTP/1.1 200 OK\r\n");
                    pw.print("Content-Type: text/html\r\n");
                    pw.print("Content-Length: " + responseBody.length() + "\r\n");
                    pw.print("Connection: close\r\n");
                    pw.print("\r\n");
                    pw.print(responseBody);
                    pw.flush();

                    return;

                }
                else if(method.equals("GET")){
                    if(path.equals("/")){
                    path = "/index.html";
                    }

                    File file = new File("public" + path);

                    if(file.exists() && !file.isDirectory){
                        byte[] content = Files.readAllBytes(file.toPath());

                        String contentType = guessContentType(file.getName());

                        PrintWriter pw = new PrintWriter(out);

                        pw.print("HTTP/1.1 200 OK\r\n");
                        pw.print("Content-Type: " + contentType + "\r\n");
                        pw.print("Content-Length: " + content.length + "\r\n");
                        pw.print("Connection: close\r\n");
                        pw.print("\r\n");
                        pw.flush();

                        out.write(content);
                        out.flush();
                    }
                    else{
                        String notFoundMessage = "<h1>404 Not Found</h1>";
                        PrintWriter pw = new PrintWriter(out);
                        pw.print("HTTP/1.1 404 Not Found\r\n");
                        pw.print("Content-Type: text/html\r\n");
                        pw.print("Content-Length: " + notFoundMessage.length() + "\r\n");
                        pw.print("Connection: close\r\n");
                        pw.print("\r\n");
                        pw.print(notFoundMessage);
                        pw.flush();
                    }

                    return;
                    
            }

            }catch(Exception e){
                String errorMessage = "<h1>500 Internal Server Error</h1>";
                PrintWriter pw = new PrintWriter(out);
                pw.print("HTTP/1.1 500 Internal Server Error\r\n");
                pw.print("Content-Type: text/html\r\n");
                pw.print("Content-Length: " + errorMessage.length() + "\r\n");
                pw.print("Connection: close\r\n");
                pw.print("\r\n");
                pw.print(errorMessage);
                pw.flush();
                System.err.println("[" + getTimestamp() + "] ERROR while processing request:");
                e.printStackTrace();
            }
            
                
        }catch(IOException ex){
            System.err.println("[" + getTimestamp() + "] IO ERROR in socket handling:");
            ex.printStackTrace();
        }
    }

    private String guessContentType(String filename){
        if (filename.endsWith(".html")) return "text/html";
        if (filename.endsWith(".css")) return "text/css";
        if (filename.endsWith(".js")) return "application/javascript";
        if (filename.endsWith(".png")) return "image/png";
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    private String getTimestamp(){
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public static void main(String[] args){
    int port = 8010;
    int poolSize = 10;
    Server server = new Server(poolSize);

    try{
        ServerSocket serverSocket = new ServerSocket(port);
        serverSocket.setSoTimeout(70000);
        System.out.println("Server is listening on port " + port);

        while(true){
            Socket clientSocket = serverSocket.accept();

            //use thread pool to handle client
            server.threadPool.execute(() -> server.handleClient(clientSocket));
        }
    }catch(IOException ex){
        ex.printStackTrace();
    }finally{
        //shutdown thread pool when server exits
        server.threadPool.shutdown();
    }
}
}


