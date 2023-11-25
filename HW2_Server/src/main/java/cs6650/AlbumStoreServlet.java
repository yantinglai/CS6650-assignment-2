package cs6650;

import javax.servlet.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.sql.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.google.gson.Gson;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

@WebServlet(name = "AlbumStoreServlet", value = "/albums/*")
public class AlbumStoreServlet extends HttpServlet {
    private static final Logger logger = Logger.getLogger(AlbumStoreServlet.class.getName());
    private final Gson gson = new Gson();
    private static final String SQL_INSERT_ALBUM = "INSERT INTO albums (artist, title, year, image) VALUES (?, ?, ?, ?)";
    private static final String SQL_SELECT_ALBUM = "SELECT artist, title, year FROM albums WHERE id = ?";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("Missing albumID");
            return;
        }

        String[] urlParts = pathInfo.split("/");
        if (!isUrlValid(urlParts)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write(gson.toJson(new ErrorMsg("Invalid album ID")));
            return;
        }

        String albumId = urlParts[1];
        try {
            AlbumInfo album = getAlbumInfoFromDatabase(albumId);
            if (album != null) {
                response.getWriter().write(gson.toJson(album));
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write(gson.toJson(new ErrorMsg("Album not found")));
            }
        } catch (SQLException | ClassNotFoundException e) {
            logger.log(Level.SEVERE, "Database access error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");

        try {
            List<FileItem> items = new ServletFileUpload(new DiskFileItemFactory()).parseRequest(req);
            AlbumInfo albumData = null;
            byte[] imageBytes = null;

            for (FileItem item : items) {
                if ("albumData".equals(item.getFieldName())) {
                    albumData = gson.fromJson(item.getString(), AlbumInfo.class);
                } else if ("image".equals(item.getFieldName())) {
                    imageBytes = item.get();
                }
            }

            if (albumData != null && imageBytes != null) {
                int generatedKey = storeAlbumData(albumData, imageBytes);
                ImageMetaData imageData = new ImageMetaData(String.valueOf(generatedKey), String.valueOf(imageBytes.length));
                response.getWriter().write(gson.toJson(imageData));
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write(gson.toJson(new ErrorMsg("Incomplete data: Album information or image is missing.")));
            }
        } catch (FileUploadException e) {
            throw new ServletException("Failed to parse multipart request", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Server error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private int storeAlbumData(AlbumInfo albumData, byte[] imageBytes)
            throws SQLException, ClassNotFoundException {
        try (Connection connection = DatabaseUtil.connectionToDb();
             PreparedStatement stmt = connection.prepareStatement(SQL_INSERT_ALBUM, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, albumData.artist);
            stmt.setString(2, albumData.title);
            stmt.setString(3, albumData.year);
            stmt.setBytes(4, imageBytes);
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating album failed, no rows affected.");
            }
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating album failed, no ID obtained.");
                }
            }
        }
    }

    private AlbumInfo getAlbumInfoFromDatabase(String albumId)
            throws SQLException, ClassNotFoundException {
        try (Connection connection = DatabaseUtil.connectionToDb();
             PreparedStatement stmt = connection.prepareStatement(SQL_SELECT_ALBUM)) {
            stmt.setInt(1, Integer.parseInt(albumId));
            try (ResultSet resultSet = stmt.executeQuery()) {
                if (resultSet.next()) {
                    return new AlbumInfo(resultSet.getString("artist"), resultSet.getString("title"), resultSet.getString("year"));
                }
            }
        }
        return null;
    }

    private boolean isUrlValid(String[] urlParts) {
        return urlParts.length == 2 && !urlParts[1].isEmpty();
    }
}
