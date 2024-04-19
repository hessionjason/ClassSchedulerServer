import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.concurrent.RecursiveAction;

public class EarlyLectureTask extends RecursiveAction {
    private String day;
    private Connection connection;

    public EarlyLectureTask(String day, Connection connection) {
        this.day = day;
        this.connection = connection;
    }

    @Override
    protected void compute() {
        try {
            connection.setAutoCommit(false);  // Start transaction

            if (shiftClassesToEarlyMorning(day)) {
                connection.commit();
                System.out.println("Classes shifted successfully for day: " + day);
            } else {
                connection.rollback();
                System.out.println("No shifts were made for day: " + day);
            }
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ex) {
                System.out.println("Error during rollback: " + ex.getMessage());
            }
            System.out.println("Error shifting classes: " + e.getMessage());
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                System.out.println("Error resetting auto-commit: " + e.getMessage());
            }
        }
    }

    private boolean shiftClassesToEarlyMorning(String day) throws SQLException {
        String fetchSql = "SELECT class_id, time, room FROM classes WHERE day = ? AND time >= '13:00' AND time <= '17:00'";
        try (PreparedStatement fetchStmt = connection.prepareStatement(fetchSql)) {
            fetchStmt.setString(1, day);
            ResultSet rs = fetchStmt.executeQuery();

            while (rs.next()) {
                int classId = rs.getInt("class_id");
                String originalTime = rs.getString("time");
                String room = rs.getString("room");
                String newTime = calculateNewTime(originalTime);

                if (newTime != null && !isTimeSlotOccupied(day, newTime, room)) {
                    if (!updateClassTime(classId, newTime)) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    /*private String calculateNewTime(String originalTime) {
        if (originalTime.compareTo("15:00") >= 0) {
            return "11:00";
        } else if (originalTime.compareTo("13:00") >= 0) {
            return "09:00";
        }
        return null;
    }*/

    private String calculateNewTime(String originalTime) {
        switch (originalTime) {
            case "13:00":
                return "09:00";
            case "14:00":
                return "10:00";
            case "15:00":
                return "11:00";
            case "16:00":
                return "12:00";
            case "17:00":
                return "13:00";
            default:
                return null; // Return null if the time does not need changing or is out of scope
        }
    }


    private boolean isTimeSlotOccupied(String day, String time, String room) throws SQLException {
        String checkSql = "SELECT COUNT(*) AS count FROM classes WHERE day = ? AND time = ? AND room = ?";
        try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
            checkStmt.setString(1, day);
            checkStmt.setString(2, time);
            checkStmt.setString(3, room);
            ResultSet rs = checkStmt.executeQuery();
            rs.next();
            return rs.getInt("count") > 0;
        }
    }

    private boolean updateClassTime(int classId, String newTime) throws SQLException {
        String updateSql = "UPDATE classes SET time = ? WHERE class_id = ?";
        try (PreparedStatement updateStmt = connection.prepareStatement(updateSql)) {
            updateStmt.setString(1, newTime);
            updateStmt.setInt(2, classId);
            int rowsUpdated = updateStmt.executeUpdate();
            return rowsUpdated > 0;
        }
    }
}
