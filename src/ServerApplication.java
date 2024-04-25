import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ServerApplication {
    private static final int PORT = 1249;
    private static final String DB_URL = "jdbc:mysql://localhost:3306/class_scheduler";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "";
    private static final String TERMINATE_KEYWORD = "TERMINATE";

    private Connection connection;

    public ServerApplication() {
        try {
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            System.out.println("Database connected successfully");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        try {
            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress().getHostAddress());

                // Create a new thread for each client connection
                Thread clientThread = new Thread(new ClientHandler(clientSocket, connection));
                clientThread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        ServerApplication server = new ServerApplication();
        server.start();
    }
}
