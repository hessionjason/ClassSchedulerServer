import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.RecursiveAction;
import java.sql.ResultSet;


public class EarlyLectureTask extends RecursiveAction {
    private String day;
    private Connection connection;

    public EarlyLectureTask(String day, Connection connection) {
        this.day = day;
        this.connection = connection;
    }

    @Override
    protected void compute() {
        // Start database transaction
        try {
            connection.setAutoCommit(false);  // Ensure transaction block starts

            // Implementing actual shifting logic here
            if (shiftClassesToEarlyMorning(day)) {
                // If shifts are successful, commit changes
                connection.commit();
                System.out.println("Classes shifted successfully for day: " + day);
            } else {
                // If no shifts were possible, rollback any partial changes
                connection.rollback();
                System.out.println("No shifts were made for day: " + day);
            }
        } catch (SQLException e) {
            try {
                // Attempt to rollback on error
                connection.rollback();
            } catch (SQLException ex) {
                System.out.println("Error during rollback: " + ex.getMessage());
            }
            System.out.println("Error shifting classes: " + e.getMessage());
        } finally {
            try {
                // Reset default commit behavior
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                System.out.println("Error resetting auto-commit: " + e.getMessage());
            }
        }
    }

    private boolean shiftClassesToEarlyMorning(String day) throws SQLException {
        // Check if early morning slots are free
        String checkSql = "SELECT COUNT(*) AS count FROM classes WHERE day = ? AND time BETWEEN '09:00' AND '13:00'";
        try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
            checkStmt.setString(1, day);
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next() && rs.getInt("count") == 0) {  // Check if the morning slots are empty
                // All morning slots are free, attempt to reschedule classes
                String updateSql = "UPDATE classes SET time = CASE " +
                        "WHEN time BETWEEN '13:00' AND '15:00' THEN '09:00' " +
                        "WHEN time BETWEEN '15:00' AND '17:00' THEN '11:00' " +
                        "ELSE time END " +
                        "WHERE day = ? AND time > '09:00'";
                try (PreparedStatement updateStmt = connection.prepareStatement(updateSql)) {
                    updateStmt.setString(1, day);
                    int updatedRows = updateStmt.executeUpdate();
                    return updatedRows > 0;  // Return true if any rows were updated
                }
            } else {
                // Morning slots are not empty, do not perform any updates
                return false;
            }
        }
    }
}
