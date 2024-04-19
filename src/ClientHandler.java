import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool;



public class ClientHandler implements Runnable {
    private static int clientCount = 0;
    private int clientID;
    private static final String TERMINATE_KEYWORD = "TERMINATE";

    private static final String EARLY_LECTURE_REQUEST = "EARLY_LECTURE_REQUEST";
    private static final ForkJoinPool pool = new ForkJoinPool(); // Shared among all instances

    private Socket clientSocket;
    private Connection connection;

    public ClientHandler(Socket clientSocket, Connection connection) {
        this.clientSocket = clientSocket;
        this.connection = connection;
        this.clientID = ++clientCount;//INCREMENT CLIENT ID AND ASSIGN
    }

    @Override
    public void run() {
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            //PRINT CLIENT CONNECTION INFO
            System.out.println("Client " + clientID + " connected " + clientSocket.getInetAddress().getHostAddress());

            String message;
            while ((message = reader.readLine()) != null) {
                System.out.println("Received from client: " + clientID + ": " + message);
                String[] parts = message.split("\\s+");
                String action = parts[0];

                // handle different actions
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
                    case "DISPLAY_MODULE_SCHEDULE":
                        handleDisplayModuleSchedule(parts, writer);
                        break;
                    case "EARLY_LECTURE_REQUEST":
                        handleEarlyLectureRequest(parts, writer);
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

    private void handleEarlyLectureRequest(String[] parts, PrintWriter writer) {
        String day = parts[1]; // Assuming the day is passed as the second part of the message

        try {
            EarlyLectureTask task = new EarlyLectureTask(day, connection);
            pool.execute(task); // Submit the task to the fork-join pool

            writer.println("Early lecture request processing initiated for " + day);
        } catch (Exception e) {
            writer.println("Failed to process early lecture request for " + day);
            e.printStackTrace();
        }
    }


    private synchronized void handleAddClass(String[] parts, PrintWriter writer) throws SQLException {
        String className = parts[1];
        String room = parts[2];
        String day = parts[3];
        String time = parts[4];

        // check for clash before adding the class
        if (isClash(className, room, day, time)) {
            writer.println("Clash detected: Another class already scheduled at the same time.");
            return;
        }
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

    private boolean isClash(String className, String room, String day, String time) throws SQLException {
        String sql = "SELECT * FROM classes WHERE room = ? AND day = ? AND time = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, room);
            statement.setString(2, day);
            statement.setString(3, time);
            ResultSet resultSet = statement.executeQuery();
            return resultSet.next(); // Returns true if clash detected, false otherwise
        }
    }

    private synchronized void handleRemoveClass(String[] parts, PrintWriter writer) throws SQLException {
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
                writer.println("Class removed successfully - " + room + " is now free at " + time + " " + day);
            } else {
                writer.println("No class found with the specified details");
            }
        } catch (SQLException e) {
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

                // print the response being sent to the client
                System.out.println("Response to client: " + schedule.toString());

                writer.println(schedule.toString()); // Send the entire schedule as a single response

            } catch (SQLException e) {
                e.printStackTrace();
                writer.println("Error: Failed to fetch schedule from the database");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleDisplayModuleSchedule(String[] parts, PrintWriter writer) {
        try {
            // get the module name from the message parts
            String moduleName = parts[1];

            // Prepare the SQL query to fetch the schedule for the specified module
            String sql = "SELECT * FROM classes WHERE class_name = ?";

            // Use a PreparedStatement to prevent SQL injection
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                // Set the module name as a parameter in the query
                statement.setString(1, moduleName);

                // Execute the query and process the results
                ResultSet resultSet = statement.executeQuery();
                StringBuilder schedule = new StringBuilder();

                // iterate over the results and append each class info to the schedule
                while (resultSet.next()) {
                    int classId = resultSet.getInt("class_id");
                    String className = resultSet.getString("class_name");
                    String room = resultSet.getString("room");
                    String day = resultSet.getString("day");
                    String time = resultSet.getString("time");
                    String classInfo = "Class ID: " + classId + ", Class Name: " + className + ", Room: " + room + ", Day: " + day + ", Time: " + time;
                    schedule.append(classInfo).append("\n"); // Append each class information with newline
                }
                // print the response being sent to the client
                System.out.println("Response to client: " + schedule.toString());

                // Send the entire schedule
                writer.println(schedule.toString());
            }
        } catch (SQLException e) {
            e.printStackTrace();
            writer.println("Error: Failed to fetch module schedule from the database");
        }
    }
}
