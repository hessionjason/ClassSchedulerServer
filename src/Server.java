import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Server {
    private static final int PORT = 5678; // Port on which the server will listen for client connections
    private static final Map<String, String> schedule = new HashMap<>();

    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("Server started. Waiting for clients to connect...");

            while (true) {
                // Accept incoming client connections
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket);

                // Handle client connection in a separate thread
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class ClientHandler extends Thread {
        private final Socket clientSocket;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try {
                // Setup input and output streams for communication with the client
                DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

                // Receive messages from the client and send responses
                String message;
                while ((message = in.readUTF()) != null) {
                    // Handle the received message
                    System.out.println("Message from client: " + message);

                    //check for STOP message
                    if(message.equals("STOP")){
                        out.writeUTF("TERMINATE");
                        break; //exit loop and close connection
                    }

                    // Parse the received message and perform corresponding actions
                    String[] parts = message.split(":", 2);
                    String action = parts[0];
                    String classInfo = parts.length > 1 ? parts[1] : "";

                    // Perform actions based on the received message
                    String response = "";
                    switch (action) {
                        case "ADD_CLASS":
                            response = addClass(classInfo);
                            break;
                        case "REMOVE_CLASS":
                            response = removeClass(classInfo);
                            break;
                        case "DISPLAY_SCHEDULE":
                            response = displaySchedule();
                            break;
                        default:
                            response = "Invalid action: " + action;
                            break;
                    }

                    // Send response back to the client
                    out.writeUTF(response);
                }
            } catch (EOFException e) {
                System.out.println("Client disconnected: " + clientSocket);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    // Close the client socket when done
                    clientSocket.close();
                    System.out.println("Client disconnected: " + clientSocket);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private String addClass(String classInfo){
            //Trim the class info to remove leading or trailing whitespace
            classInfo = classInfo.trim();

            // Split the class information using ":"
            String[] classInfoParts = classInfo.split(":");
            if (classInfoParts.length != 4){
                return "Invalid class Info format";
            }

            String className = classInfoParts[0].trim();
            String room = classInfoParts[1].trim();
            String day = classInfoParts[2].trim();
            String time = classInfoParts[3].trim();

            //Check if class details already exist
            if(schedule.containsKey(className)){
                return "Class " + className + " already exists in the schedule";
            }

            // Add the class to the schedule
            schedule.put(className, room + ":" + day + ":" + time);
            return "Class " + className + " added to the schedule";

        }

        private String removeClass(String className){//UPDATE SO TAKES CLASS-INFO AS A PARAM
            // Check if the class exists in the schedule
            if(!schedule.containsKey(className)){
                return "Class " + className + " does not exist in the schedule";
            }

            // Remove the class from the schedule
            schedule.remove(className);
            return "Class " + className + " removed from the schedule";
        }

        private String displaySchedule(){
            // Check if the schedule is empty
            if(schedule.isEmpty()){
                return "Schedule is empty";
            }

            StringBuilder scheduleStr = new StringBuilder("Schedule:\n");
            for(Map.Entry<String, String> entry : schedule.entrySet()){
                scheduleStr.append(entry.getKey()).append(":").append(entry.getValue()).append("\n");
            }
            return scheduleStr.toString();
        }
    }
}
