import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ServerApplication {

    private static final int PORT = 1236;
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

                // Handle client requests in a new thread
                Thread thread = new Thread(() -> handleClient(clientSocket));
                thread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleClient(Socket clientSocket) {
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            String message;
            while ((message = reader.readLine()) != null) {
                System.out.println("Received from client: " + message);
                String[] parts = message.split("\\s+");
                String action = parts[0];

                // Handle different actions
                switch (action) {
                    case "ADD_CLASS":
                        handleAddClass(parts, writer);
                        break;
                    case "REMOVE_CLASS":
                        handleRemoveClass(parts, writer);
                        break;
                    case "DISPLAY_SCHEDULE":
                        handleDisplaySchedule(parts, writer);
                        break;
                    case "STOP":
                        writer.println(TERMINATE_KEYWORD);
                        clientSocket.close();
                        return;
                    default:
                        writer.println("IncorrectActionException: Invalid action format");
                }
            }
        } catch (IOException | SQLException e) {
            e.printStackTrace();
        }
    }

    private void handleAddClass(String[] parts, PrintWriter writer) throws SQLException {
        String className = parts[1];
        String room = parts[2];
        String day = parts[3];
        String time = parts[4];

        String sql = "INSERT INTO classes (class_name, room, day, time) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, className);
            statement.setString(2, room);
            statement.setString(3, day);
            statement.setString(4, time);
            statement.executeUpdate();
        }

        writer.println("Class added successfully");
    }

    private void handleRemoveClass(String[] parts, PrintWriter writer) throws SQLException {
        String className = parts[1].trim();
        String room = parts[2].trim();
        String day = parts[3].trim();
        String time = parts[4].trim();

        String sql = "DELETE FROM classes WHERE class_name = ? AND room = ? AND day = ? AND time = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, className);
            statement.setString(2, room);
            statement.setString(3, day);
            statement.setString(4, time);

            int rowsAffected = statement.executeUpdate();
            if (rowsAffected > 0) {
                writer.println("Class removed successfully");
            } else {
                writer.println("No class found with the specified details");
            }
        } catch (SQLException e) {
            // Handle SQL exception
            e.printStackTrace();
            writer.println("Error occurred while removing class: " + e.getMessage());
        }
    }


    private void handleDisplaySchedule(String[] parts, PrintWriter writer) {
        try {
            String sql = "SELECT * FROM classes";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                ResultSet resultSet = statement.executeQuery();
                StringBuilder schedule = new StringBuilder();

                while (resultSet.next()) {
                    int classId = resultSet.getInt("class_id");
                    String className = resultSet.getString("class_name");
                    String room = resultSet.getString("room");
                    String day = resultSet.getString("day");
                    String time = resultSet.getString("time");
                    String classInfo = "Class ID: " + classId + ", Class Name: " + className + ", Room: " + room + ", Day: " + day + ", Time: " + time;
                    schedule.append(classInfo).append("\n"); // Append each class information with newline
                }

                writer.println(schedule.toString()); // Send the entire schedule as a single response

            } catch (SQLException e) {
                e.printStackTrace();
                writer.println("Error: Failed to fetch schedule from the database");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }




    public static void main(String[] args) {
        ServerApplication server = new ServerApplication();
        server.start();
    }
}
